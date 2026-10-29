# 微信小程序用户订单功能设计

> 状态：2026-10-29 用户确认后已实现，自动化验证通过；微信开发者工具/真机手测待执行，详见 doc/FEATURE_LIST.md。
> 日期：2026-10-29
> 功能名称：miniprogram_user_order
> 依据：GUIDE.md、doc/CONTEXT.md、doc/STYLE_GUIDE.md、doc/DESIGN_SUMMARY.md、docs/开发设计文档.md、design/miniprogram_cart_order_sign.md。
> 目标：在创建预订单的基础上，完成用户订单列表、详情、确认报价、未支付取消及小程序入口。微信支付另行开发。

## 1. 范围与已确认决策

### 1.1 已确认范围

- 用户只可查询和操作本人订单。
- 新增订单列表、订单详情页面及对应 API。
- 确认报价校验归属、状态、报价版本和有效期，成功进入 PENDING_PAYMENT。
- 用户可取消 CREATED、QUOTED、PENDING_PAYMENT 订单，取消与释放库存同事务、幂等。
- 小程序增加“我的订单”入口，下单成功跳转订单详情。
- 微信支付后续开发，本次按钮使用“确认价格”，不展示“确认价格并支付”。
- CREATED 用户取消按原设计接口表处理，补齐原状态表遗漏。

### 1.2 本次不包含

- 微信预支付、支付回调、正式扣库存、退款接口及订阅消息。
- 超时自动关闭任务，以及读取订单时自动关闭的隐式写入。
- 管理后台订单页面、个人中心、订单删除、再次购买、用户确认收货。
- 修改商家现有报价、配送、完成、取消和人工退款业务规则。

### 1.3 本阶段交付边界

用户可完成“预下单 → 查看订单 → 商家报价后确认价格”，确认后停留在待支付状态。详情显示“价格已确认，支付功能暂未开放”，不提供支付按钮，不将其标记为已支付。

本次不自动关闭过期订单、不自动释放其预占。过期订单保持数据库真实状态，用户仍可取消，商家仍可按既有规则处理。此限制在验收中明确记录，完整超时处理后续补齐。

## 2. 当前实现与接入点

- 用户 OrderController 目前只有 POST /api/v1/orders，OrderCreateService 已完成预订单、库存预占、快照和创建幂等。
- services/order.js 目前仅封装 createOrder；结算页成功后回首页。
- 小程序已注册首页、商品详情、购物车、结算和地址页面，tabBar 目前为首页、购物车。
- UserJwtAuthenticationFilter 与 Spring Security 已保护 /api/v1/orders 及其子路径，用户身份取自 UserPrincipal。
- SalesOrderMapper 已有管理端查询、订单行锁、版本条件更新；其管理端分页查询没有用户隔离条件，不可直接用于用户列表。
- OrderStateMachine 已有 QUOTE、DELIVER、COMPLETE、CANCEL；CANCEL 包含商家可取消的 PAID_WAIT_DELIVERY，不可直接作为用户取消权限依据。
- AdminOrderService 已有库存释放实现，但绑定 AdminPrincipal、管理审计及后台响应；用户服务不得伪造管理员身份调用。
- V4/V6 已具备订单、商品快照、改价历史、状态日志、库存流水、用户与幂等列，本次预计不新增数据库字段或迁移。

## 3. 用户 API

### 3.1 通用约定

- 统一响应 ApiResponse，字段为 code、message、data、traceId；分页复用 PageResponse。
- 全部接口要求 USER Token，用户 ID 仅从 Token 获取，不接收客户端 userId。
- 订单 id 为正整数。无此订单和非本人订单统一 ORDER_NOT_FOUND（404），不得返回其他用户信息。
- 查询及锁定订单均带 wx_user_id 条件；订单项、改价历史等从已经校验归属的订单加载。
- 参数绑定、Bean Validation 在 Controller，状态、期限和事务规则在 Service/Domain。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | /api/v1/orders | 本人订单分页列表 |
| GET | /api/v1/orders/{id} | 本人订单详情 |
| POST | /api/v1/orders/{id}/confirm-quote | 确认当前报价 |
| POST | /api/v1/orders/{id}/cancel | 取消本人未支付订单 |

现有 POST /api/v1/orders 请求和响应保持兼容。

### 3.2 列表

查询参数：page 默认 1，pageSize 默认 20、范围 1～100；status 可省略，或传一个/多个以逗号分隔的 OrderStatus 枚举值。非法值或空分项返回 VALIDATION_ERROR；去重后最多 8 个状态。排序固定 created_at DESC、id DESC，不接收客户端排序 SQL。

列表与 count 必须使用完全相同的用户和状态条件。计算 offset 时使用不会整数溢出的方式，越界返回参数错误。

列表项字段：id、orderNo、status、estimatedAmountCent、finalAmountCent、quoteVersion、payDeadlineAt、createdAt、updatedAt、items。items 为当前页订单的商品摘要：productName、specName、productImageUrl、unit、estimatedQuantity、actualQuantity。批量查询当前页订单项，避免每条订单分别查库。

不返回用户 ID、支付交易标识、退款凭证、后台审计信息和收货地址；金额为整数分，未报价 finalAmountCent 为 null。

### 3.3 详情

新增用户专用响应，不直接复用后台 OrderDetailResponse，也不返回数据库实体。

- 主信息：id、orderNo、status、remark、currency、quoteVersion、estimatedAmountCent、finalAmountCent。
- 地址：addressSnapshot（contactName、phone、detailAddress），来自下单快照；后续编辑或删除地址不影响历史订单。
- 时间：createdAt、updatedAt、quotedAt、payDeadlineAt、paidAt、deliveredAt、completedAt、cancelledAt、closedAt。
- 取消/退款展示：cancelReason、refundStatus；不暴露 refundReference。
- items：商品及规格 ID、名称、图片、计价方式、单位、快照单价、预估/实称数量、预估/最终金额。商品下架、软删除后仍按订单快照展示，不依赖公开商品详情接口成功。
- priceRevisions：revisionNo、previousAmountCent、finalAmountCent、reason、createdAt，以及结构化的改价商品项（orderItemId、actualQuantity、finalAmountCent）。对现有 items_snapshot 做白名单转换，不把原始 JSON、操作人 ID 直接透传。
- 状态进度使用订单状态及上述时间，不暴露后台 statusLogs、payments、source、version 或 idempotencyKey。
- canConfirmQuote、canCancel 由后端按状态与有效期计算，仅辅助页面；写接口始终重新校验。

订单与明细、改价记录在只读一致性事务内读取，防止并发商家改价导致金额、版本和商品项混用。

### 3.4 确认报价

请求：`{"quoteVersion": 1}`，quoteVersion 必填且为正整数。不接收客户端最终金额作为更新值。成功返回最新用户订单详情。

事务处理顺序：

1. 按订单 ID + 当前用户 ID 加订单行锁，不存在统一 ORDER_NOT_FOUND。
2. 状态为 PENDING_PAYMENT 且 quoteVersion 相同，视为成功重放，直接返回详情，不重复写状态日志、不更改截止时间；此分支只确认此前已完成的操作，不执行支付。
3. 首次确认仅允许 QUOTED；其他状态返回 ORDER_STATE_INVALID。
4. 报价版本不匹配返回 QUOTE_VERSION_CONFLICT；前端必须刷新后重新展示金额，禁止自动确认新报价。
5. 校验最终金额已生成、payDeadlineAt 有值且服务端当前时间严格小于 payDeadlineAt；失效返回 ORDER_STATE_INVALID，中文提示“报价已过期，无法确认价格”。不在失败分支修改状态或库存。
6. 经统一状态机执行 USER_CONFIRM_QUOTE，使用用户归属、当前状态、quote_version、version 和有效期条件更新为 PENDING_PAYMENT。
7. 状态更新及一条 USER/MINI_PROGRAM 的状态日志在同一事务中提交，返回最新详情。

确认不修改 finalAmountCent、订单项、quoteVersion、quotedAt 或 payDeadlineAt，不延长原报价有效期，不创建支付记录、不改变库存。

商家改价和用户确认使用同一订单行锁串行化：先改价则旧版本确认失败；先确认则商家后续改价被既有状态规则拒绝。并发取消后不能再确认。

### 3.5 用户取消

请求：`{"reason":"用户取消订单"}`。复用 CancelOrderRequest 的非空、最多 500 字校验；去除首尾空白后仍须非空。页面二次确认后提交固定原因“用户取消订单”，不新增取消原因选项或表单。

事务处理顺序：

1. 按订单 ID + 当前用户 ID 加订单行锁。
2. 已为 CANCELLED，返回成功，不覆盖原取消原因、操作者和时间，不重复释放库存或写日志。
3. 仅 CREATED、QUOTED、PENDING_PAYMENT 可执行 USER_CANCEL；已支付、配送中、已完成和已关闭均 ORDER_STATE_INVALID。
4. 按用户归属、上述状态白名单及 version 条件更新 CANCELLED、cancelled_at、cancel_reason，refund_status 保持 NONE。
5. 按 product_spec_id 升序锁定相关规格，释放每个订单项的 estimatedQuantity；商家称重修改 actualQuantity 不改变原始预占量，故不能按实称数量释放。
6. 写 RELEASE 流水，biz_key 兼容现有 `RELEASE:<orderId>:<specId>`，保存订单 ID、前后库存、USER 操作者及原因。商品下架或软删除不得阻止已有预占的释放，锁定查询不能仅查 ACTIVE 商品。
7. 写 USER/MINI_PROGRAM 状态日志；全部步骤同事务提交，任一步失败完整回滚。

库存只减少 reserved_qty，physical_stock_qty 不变。SQL 条件保证 reserved_qty 不会为负。重复请求和用户/商家并发取消最终只有一次释放及一次状态转换；唯一键冲突不得捕获后忽略并继续提交。

### 3.6 错误与时间

- 复用 AUTH_REQUIRED、AUTH_FORBIDDEN、ORDER_NOT_FOUND、ORDER_STATE_INVALID、QUOTE_VERSION_CONFLICT、VALIDATION_ERROR、STOCK_INSUFFICIENT 等稳定错误码，按现有鉴权链处理无效或错误类型 Token。
- 报价冲突、状态变化后页面展示中文提示并刷新详情；无权限订单统一展示“订单不存在或无法访问”。
- 时间按项目 Asia/Shanghai 语义，新增用户订单响应统一为带 +08:00 偏移的 ISO 8601 字符串，避免小程序设备时区改变解释结果。
- 新增时间解析和倒计时工具，明确支持 ISO 8601；当前 formatDate 的统一替换短横线做法不适合直接用于带偏移时间，实施时须兼容现有调用并补回归验证。
- 倒计时只是展示，最终有效性由服务端判定。倒计时归零显示“报价已过期”，不将状态自行改成 CLOSED；前端时钟偏差导致失败时刷新服务端详情。

## 4. 状态机与数据访问

| 动作 | 允许当前状态 | 目标状态 | 库存变化 |
| --- | --- | --- | --- |
| USER_CONFIRM_QUOTE | QUOTED | PENDING_PAYMENT | 无 |
| USER_CANCEL | CREATED、QUOTED、PENDING_PAYMENT | CANCELLED | 释放预估数量的预占 |

新增用户专用动作，保留商家 CANCEL 行为，避免用户借用商家权限。重复成功请求在校验归属后识别，不再次调用写操作。状态机单测覆盖两类操作者的差异。

拟新增 UserOrderService、用户列表/详情/改价历史响应 DTO、ConfirmQuoteRequest；扩展现有 OrderController。查询与命令可在同一用户服务中分公开方法组织，事务均放在公开 Service 方法。

SalesOrderMapper 新增按用户分页/count、按用户查单及锁单、确认报价和用户取消条件更新；SalesOrderItemMapper 增加当前页批量查询。复用库存 Mapper 和流水写入能力，不从用户服务调用 AdminOrderService。

复用现有表、version、quote_version、唯一库存流水键及用户状态时间索引，不修改 V1～V6。若实施中发现必须新增结构，应先补充设计并确认，不能直接改已发布迁移。

## 5. 小程序页面

### 5.1 入口与工程结构

- 新增 pages/orders/orders（“我的订单”），作为第三个 tabBar；新增 pages/order-detail/order-detail（“订单详情”），按 id 打开。
- 结算成功读取创建接口返回的 id，提示下单成功后用 redirectTo 进入详情，避免返回结算页再次提交。详情提供“查看全部订单”入口，用 switchTab 返回订单列表。
- services/order.js 保留 createOrder，增加 getOrders、getOrderDetail、confirmQuote、cancelOrder。
- 新增 utils/order.js 集中维护状态中文名、筛选状态集合和页面展示派生逻辑；格式化仍由 utils/format.js 负责。
- 所有页面通过 ensureLogin 后请求；登录失败可重试，401 清理失效登录态，不循环自动重试。

### 5.2 订单列表

| 筛选标签 | 服务端 status 参数 |
| --- | --- |
| 全部 | 省略，包含全部 8 种状态 |
| 待支付 | QUOTED,PENDING_PAYMENT |
| 待配送 | PAID_WAIT_DELIVERY |
| 已完成 | COMPLETED |
| 已取消 | CANCELLED,CLOSED |

待商家确认和配送中在“全部”中可见；已取消筛选中的 CLOSED 卡片仍明确标为“已关闭”，不篡改状态。保持文档既有五个标签，不新增分类。

- 卡片显示订单号、真实状态中文名、商品摘要、创建时间和金额。未报价使用“预估金额”，已报价使用“最终金额”；最终金额为 0 时正确展示 0.00，不能用真值判断回退。
- QUOTED 显示“待确认价格”，PENDING_PAYMENT 显示“待支付”；有效报价显示剩余时间，过期显示文字提示。
- 点击卡片进入详情，取消和确认统一放在详情页，减少列表操作状态分支。
- 每页 20 条，支持下拉刷新、触底加载、首次加载、空态、错误重试、已到底；再次进入列表刷新第一页。
- 切换标签重置分页，使用请求序号丢弃旧响应，避免慢请求覆盖新标签；网络失败保留既有列表并恢复加载按钮。

### 5.3 订单详情

- 顶部：状态、提示及报价截止时间；主体：商品和地址快照、预估/实称数量、预估/最终金额、备注、改价历史、订单号及进度时间。
- 未报价数据为空时显示“待商家称重”，不把空最终金额显示为 0；实称数量为 0 时按真实值展示。
- QUOTED 且有效：显示“确认价格”和“取消订单”；确认弹窗展示当前最终金额及“确认后等待支付，支付功能后续开放”，确认后携带页面 quoteVersion 提交。
- PENDING_PAYMENT：显示“价格已确认，支付功能暂未开放”，仅可取消；不调用 wx.requestPayment、不展示支付成功。
- CREATED：提示“等待商家确认”，可取消。过期 QUOTED：禁用确认、保留取消。已支付及终态只读。
- 取消使用二次确认，明确“取消后将释放商品预占库存”。同一页面的确认和取消共享提交锁，请求结束恢复；不能同时发起两个动作。
- onShow、下拉刷新重新读取服务端状态；刷新或操作失败保留已有数据并提示。页面离开清理倒计时，无后台轮询或推送。
- 图片使用 resolveImageUrl，失败显示占位。详情请求不存在或越权时展示不可访问状态和返回订单入口。

## 6. 测试与验收

### 6.1 后端自动化

- 列表：本人隔离、状态组合、排序分页、空列表、非法状态/page/pageSize、offset 边界、当前页商品摘要。
- 详情：成功、未报价空值、快照不随商品/地址修改改变、商品下架/软删除后可读、改价历史、订单不存在/越权、安全字段白名单。
- 确认：成功、重复确认、错误版本、非法状态、期限刚好到达和已过期、缺失金额/截止时间、确认后金额/版本/截止时间/库存均不变。
- 取消：三种合法状态、重复请求、已支付拒绝、过期报价可取消、按预估数量释放、下架/软删除规格释放、库存或流水失败整体回滚、越权不写库。
- MockMvc/鉴权：无 Token、失效 Token、管理员 Token 访问用户接口、非法 id、请求字段校验及错误码。
- Testcontainers MySQL：重复确认、用户/商家并发取消、确认与改价、确认与取消竞争；验证最终状态、金额版本、库存数量和日志/流水次数，而非只验证方法被调用。
- 回归现有订单创建、商家订单和库存测试，保持管理端行为不变。无法运行的测试明确记录原因，不以单元测试替代 MySQL 并发验证。

### 6.2 小程序验证

- JS 语法、JSON 页面及 tabBar 注册校验；金额空值/零值、ISO 时间解析、状态映射、倒计时边界验证。
- 微信开发者工具手测：下单直达详情、订单入口、标签分页、返回刷新、旧请求乱序、断网恢复、登录失效、图片失败、空态和不可访问状态。
- 商家改价后用户刷新可见新金额；旧报价确认失败必须重新确认，不自动提交新版本。
- 确认后处于待支付且无支付动作；重复点击不重复日志。取消后库存恢复，重复取消不重复释放。
- 截止时间归零不会虚报已关闭；用户仍能主动取消。已支付、配送中和终态均无用户取消入口，接口亦拒绝。
- 已支付等后续状态可由隔离测试数据验证展示，不通过新增生产接口伪造支付成功。

## 7. 实施顺序与文档同步

1. 用户确认本文详细设计后，再开始实现。
2. 用户 DTO、状态机动作、Mapper 查询/条件更新、UserOrderService、Controller。
3. 服务、接口、MySQL 并发回归测试及既有功能回归。
4. 小程序 services、格式化工具、订单列表、详情、tabBar、结算跳转。
5. 语法/配置检查及开发者工具验证，记录实际执行结果和环境限制。
6. 实施时同步 docs/开发设计文档.md 的用户取消状态表、列表参数、用户响应和分阶段确认报价交互；必要时同步 doc/DESIGN_SUMMARY.md、doc/CONTEXT.md。保留完整产品最终“确认价格并支付”目标，明确当前阶段暂未接入支付。
7. 功能实际完成后追加 doc/FEATURE_LIST.md，列出已验证能力和暂缺的支付、自动关闭；本次编写设计不登记为功能已完成。

## 8. 评审说明

- 已确认的业务边界：四项用户订单功能、CREATED 可取消、微信支付后续开发。
- 本文具体方案（响应白名单、状态筛选映射、第三个 tabBar、并发和期限处理等）已随本文获用户确认，沿用项目现有流程开发，不初始化 OpenSpec。
- 实施补充：数据库连接初始化会话时区为 +08:00，防止 TIMESTAMP 在 UTC 数据库中被当作上海时间比较；用户响应使用带偏移的 ISO 时间。
- 实施补充：库存流水 reason 列为 200 字，取消原因完整保存至订单和状态日志，流水仅保存前 200 字摘要，兼容 API 的 500 字上限。
