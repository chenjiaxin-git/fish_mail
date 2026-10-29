# 订单超时自动关闭设计

> 状态：2026-10-29 用户已确认默认启用，代码完成，全量后端 185 项及小程序 13 项测试通过；实际环境边界见 doc/FEATURE_LIST.md。尚未启动或重启用户业务服务。
> 日期：2026-10-29
> 功能名称：order_timeout_close
> 依据：GUIDE.md、doc/CONTEXT.md、doc/STYLE_GUIDE.md、doc/DESIGN_SUMMARY.md、docs/开发设计文档.md、design/wechat_payment_sign.md。
> 目标：自动关闭到期的未支付订单，幂等释放全部库存预占，同时复用支付查单/关单机制，防止已经收款的订单被误关。

## 1. 范围

- 后端新增可配置的定时扫描任务，处理 CREATED、QUOTED、PENDING_PAYMENT。
- 新增统一状态机 TIMEOUT_CLOSE 动作及 CLOSED 条件更新，保存 closed_at 和 SYSTEM 状态日志。
- 关闭事务释放 estimatedQuantity + paymentExtraReservedQty，写 RELEASE 流水，实物库存不变。
- 对曾提交微信的订单先查单/关单；已支付则复用可信入账，不自动退款、不关闭已付订单。
- 持久化失败重试时间和次数，保证重启后可继续，异常订单不阻塞后续正常订单。
- 覆盖期限边界、重复执行、状态并发、微信不确定结果、库存事务回滚和任务扫描测试。

不包含：订阅消息、后台任务管理页面、手动强制关闭接口、额外调度中间件、微信自动退款、历史订单批量修复和自动开启微信支付。继续使用现有 Spring Boot/MySQL，不新增框架依赖。

本功能是仓库内的后端业务任务，不创建 Codex 定时自动化。

## 2. 已有规则与当前代码

| 当前状态 | 超时依据 | 默认规则 |
| --- | --- | --- |
| CREATED | created_at + confirmTimeoutMinutes | 创建后 1440 分钟 |
| QUOTED | pay_deadline_at | 报价时固定，默认 quoted_at + 60 分钟 |
| PENDING_PAYMENT | pay_deadline_at | 与报价使用同一截止时间，确认/重试不延长 |

- 到达截止时间即视为超时，使用 `now >= deadline`，与现有确认报价/预支付必须严格早于截止时间的规则衔接。
- 对报价订单以持久化 pay_deadline_at 为准，不能因后续调整支付超时配置重新计算旧订单期限。
- CREATED 的确认时限由当前配置计算；配置变更后也适用于尚未报价的旧订单，部署说明应明确这一点。
- 所有时间使用 Asia/Shanghai 语义；数据库连接会话已固定 +08:00，新关闭逻辑通过可注入 Clock 获取当前时间，便于精确边界测试。
- 当前 OrderProperties 只有 paymentTimeoutMinutes；尚无调度启用、到期候选查询、TIMEOUT_CLOSE 动作或 CLOSED 更新实现。
- 现有 PaymentService.prepareCancellation、PaymentTransactions.claimExisting 已实现查单/关单及网络结果不确定时保留预占，但没有“必须仍超时”的保护，不能不加修改直接用于定时关闭。
- 原事实源示例使用 order.confirm.timeout-minutes、order.payment.timeout-minutes，实际代码采用 order.payment-timeout-minutes。本次沿用代码的扁平配置风格，新增 order.confirm-timeout-minutes，并同步文档，避免出现两套不生效的配置。

## 3. 默认调度配置（随本文确认）

推荐实现后默认启用；已有到期订单会在应用启动后的扫描中被处理。支付开关维持现状，不随本功能开启。

| 配置 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| order.timeout-close-enabled | ORDER_TIMEOUT_CLOSE_ENABLED | true | 可随时通过部署配置关闭定时扫描 |
| order.confirm-timeout-minutes | ORDER_CONFIRM_TIMEOUT_MINUTES | 1440 | CREATED 确认时限，1～525600 分钟 |
| order.timeout-close-delay-ms | ORDER_TIMEOUT_CLOSE_DELAY_MS | 30000 | 上轮扫描结束后再等待 30 秒，fixedDelay |
| order.timeout-close-batch-size | ORDER_TIMEOUT_CLOSE_BATCH_SIZE | 50 | 每批候选上限，范围 1～100 |
| order.timeout-close-max-batches | ORDER_TIMEOUT_CLOSE_MAX_BATCHES | 10 | 每轮最多处理的批次数 |
| order.timeout-close-budget-ms | ORDER_TIMEOUT_CLOSE_BUDGET_MS | 20000 | 达预算后不再开始新的订单处理 |

应用启动后延迟一个扫描间隔开始首轮，避免启动过程抢占资源。采用 Spring 调度与独立后台线程，定时方法只调用编排服务，不直接改订单状态。任务禁用时不扫描、不访问微信。

预算不强制中断正在进行的支付网络请求；当前订单须等待其配置的连接/读写超时结束，随后停止本轮。固定延迟不是“精确到秒关闭”的承诺，实际关闭受扫描、网络及重试影响。

同一实例不重叠执行本任务；即使误启动多个实例，数据库订单行锁、支付操作占用和状态条件更新仍保证不重复释放。暂不增加分布式锁服务。

测试默认关闭真实定时扫描，由测试显式触发任务，防止后台线程干扰固定夹具与并发断言。

## 4. 候选扫描与重试

### 4.1 查询

只选同时满足以下条件的记录：

- 状态属于 CREATED、QUOTED、PENDING_PAYMENT，且按第 2 节规则已到期。
- timeout_next_attempt_at 为空或不晚于本轮查询时间。
- 使用 ID 游标分页：id > lastSeenId，固定 id ASC，每批最多配置条数，不使用 OFFSET，不无界 SELECT。

扫描不预先锁住整批订单；候选只代表“可能到期”，逐单处理时必须重新锁定、检查当前状态、版本和截止时间。

同一轮中不重复访问已经尝试的 ID；失败后继续处理后面的候选。轮次达到批数或时间预算即结束，下轮从头扫描；持久化退避时间将暂时隔离反复失败的旧单，避免其长期占满最前面的批次。

QUOTED/PENDING_PAYMENT 缺少 pay_deadline_at 的异常数据不得臆测期限后关闭，不被正常到期查询选中；执行入口遇到时记录数据异常并跳过，由人工核对。正常商家报价已经保证写入截止时间。

### 4.2 持久化重试

拟新增 Flyway V8：

- sales_order.timeout_next_attempt_at TIMESTAMP(3) NULL。
- sales_order.timeout_attempt_count INT NOT NULL DEFAULT 0，非负；累计计数设安全上限防溢出，达到上限仍继续按封顶间隔重试。
- 增加与状态、下次重试时间、ID 对应的扫描索引；实施时用 MySQL 查询计划核对，并复用已有状态/created_at、状态/pay_deadline_at 索引，避免重复索引。

单次失败以独立短事务更新下一次尝试时间：30 秒起，按次数指数退避，最长 30 分钟；不得因为达到某个重试次数就强制关单或释放库存。

重试元数据更新须限定订单仍为到期未支付状态且匹配此次观察到的业务版本/期限，不能覆盖已经支付、取消、关闭或重新报价的订单。重试字段不自行递增业务 version。

重新报价时主动清空旧 timeout_next_attempt_at，保留累计次数，防止历史失败延迟新报价的到期扫描。

支付处理异常沿用 payment_event_log；任务记录 traceId、orderNo、动作、结果、重试次数和下次时间，不打印敏感支付数据。库存失败等本地异常也记录安全原因，持续失败应在服务日志中明确提示人工检查。

数据库整体不可用时，重试时间也可能无法写入；记录错误，退出或结束当前轮次，下一轮重新扫描，不能声称已经持久化成功。

## 5. 逐单编排及支付保护

流程概要：

```text
Scan due candidates
        |
        v
Lock order + recheck deadline/state
        |
        +--> Not due / paid / terminal --> Skip
        |
        +--> No payment / confirmed CLOSED --> Local close transaction
        |
        v
Claim payment operation with due guard
        |
        v
Query / close WeChat outside transaction
        |
        +--> SUCCESS --> Existing trusted settlement --> Keep paid order
        |
        +--> CLOSED / close confirmed --> Recheck + local close transaction
        |
        +--> Unknown / busy / error --> Keep reservation + retry later
```

### 5.1 超时专用支付入口

为 PaymentService/PaymentTransactions 增加超时关闭协调用途，复用现有查单、关单及入账实现，而不是复制微信 HTTP 或验签代码。

- 在领取支付操作占用的同一事务、同一订单行锁下校验当前订单仍到期，并记录本次观察的 version、状态和截止时间。
- 候选查询后若商家已重新报价，或用户已确认成尚未到期的订单，必须跳过，不能先关掉其有效微信交易再发现条件变化。
- 对于没有支付记录的候选，不调用微信，直接进入本地关闭事务；若中间产生了支付记录，最终关闭事务必须发现并拒绝跳过协调。新预支付仍受原有截止时间校验限制。
- 支付记录 SUCCESS 或可信查单 SUCCESS：走现有 PAYMENT_SUCCESS 原子入账，订单进入待配送，库存按实称扣减；本轮不关单、不写 RELEASE。回调晚到不改变可信付款时间判断规则。
- NOTPAY：确认微信关单后才允许本地关闭。微信已为 CLOSED 可直接进入最终关闭事务。
- 操作占用未过期、USERPAYING、UNKNOWN、查单/关单超时、验签失败，均不关闭、不释放，按退避重试。
- 曾提交过支付而查询为 NOT_EXISTS，仍沿用当前保守规则：查不到不证明没有迟到创建，不直接释放，不自动创建新的微信预支付单；重试和异常提示保留人工核查路径。
- 支付目前关闭配置，但订单已有历史支付记录时，仍不能以“现在支付开关关闭”为理由当作未提交。无法查单则保留库存、记录错误并重试。没有支付记录的订单可照常本地关闭。
- 领取、完成和恢复支付操作时沿用现有 token/有效期保护，过期结果不能覆盖新的支付成功或关闭阶段。

### 5.2 竞态处理

- 扫描与报价：最终事务重新核对状态和截止时间，较新的报价/版本使旧关闭尝试失效；不会把 CREATED 的 24 小时期限套用到新 QUOTED。
- 扫描与确认报价/预支付：到期后现有接口禁止首次确认/预支付；已进入网络阶段的支付操作通过记录与占用协调，不能绕过。
- 扫描与用户/商家取消：同一订单锁串行化；谁先成功，另一方视终态跳过，不重复释放或改写原因。
- 扫描与支付成功：可信已付优先按既有规则入账；微信已明确关单才允许释放。若 CLOSED 后仍收到矛盾的成功结果，沿用异常留痕人工处理，不自动复活或退款。

## 6. 本地关闭事务

单独的公开 Application Service 事务方法执行以下动作，避免内部自调用导致事务失效：

1. 按 ID 加订单行锁，以本次 Clock 时间重新检查实际状态、期限和预期 version/截止时间。
2. 对已付、配送中、完成、已取消、已关闭或仍未到期订单返回跳过，不抛成库存失败、不写任何 RELEASE。
3. 锁定支付记录。存在支付记录时，必须为业务未成功且远端阶段 CLOSED；否则退出等待协调，不能仅凭外部方法“已执行过”就释放。
4. 通过统一状态机 TIMEOUT_CLOSE 验证，允许 CREATED/QUOTED/PENDING_PAYMENT 转为 CLOSED；条件 UPDATE 同时限定当前状态、version 和到期条件。
5. 设置 closed_at，递增业务 version，保留原报价与金额；不填 cancelled_at，不伪造用户取消原因，不改已支付退款状态。
6. 按 product_spec_id 升序锁规格，释放每项 estimatedQuantity + paymentExtraReservedQty。下架/软删除规格仍可释放；physical_stock_qty 保持不变，reserved_qty 不得为负。
7. 使用与既有取消兼容的 `RELEASE:<orderId>:<specId>` 唯一业务键写库存流水，保存前后库存和订单归属，operator_type=SYSTEM、operator_id 为空。不能换独立关闭业务键使同一预占被取消和关闭各释放一次。
8. 写一条 from_status→CLOSED 的 SYSTEM 状态日志，source=TIMEOUT_JOB，原因按“商家确认超时自动关闭”或“报价/支付超时自动关闭”区分。清理下次重试时间，保留累计尝试次数供诊断。

订单、库存、流水和状态日志同事务提交，任一步失败全部回滚。日志唯一冲突不得捕获后当成功，库存也不能通过跳过流水补救。

未来新增自动关闭入口也必须调用该服务，禁止定时方法或 Mapper 单独批量更新状态。

## 7. 前端及接口边界

- 本次不新增公开 API，定时执行不使用用户 Token，不提供强制关闭/忽略微信结果的接口。
- 现有用户详情已有 closedAt、CLOSED 中文状态和只读控制，列表“已取消”标签已包含 CLOSED，优先复用。
- 报价倒计时到 0 仅显示“报价已过期”，在后端实际关闭前保留真实数据库状态；不能前端自行宣告 CLOSED。
- 实际 CLOSED 后，详情展示“已关闭”和关闭时间，不显示确认价格、付款、取消按钮；确认/支付/取消服务端接口也应拒绝不合法状态。
- 如果库存因支付不确定仍保留，页面按现有真实状态展示，用户可刷新或按现有取消流程重试，不添加虚假关闭提示。
- 视实际页面兼容验证结果，只调整与上述状态展示直接有关的代码，不扩展后台页面或营销功能。

## 8. 验证计划

### 8.1 单元与调度

- 固定 Clock 校验 CREATED 的期限前、刚好到期、期限后；报价/待支付使用持久化 deadline，不被确认或配置变化延长。
- 无截止时间、不支持状态、重新报价、新版本、已支付/已取消/已关闭均跳过。
- 默认值、配置边界、禁用不执行、fixedDelay/初始延迟接线正确，逐单异常不终止整批。
- 游标分页不重复、不跳过正常候选，反复失败的旧单不会占满后续批次；达到预算/最大批次停止，下一轮可继续。
- 退避持久化、封顶间隔、重启后到期重试、计数边界，以及失败重试不覆盖并发业务状态。

### 8.2 Testcontainers MySQL 与支付 Fake

- 三种到期状态成功关闭，正确设置 closed_at、SYSTEM 日志，实物不变且预估+额外预占全部释放。
- 同一订单重复执行、两个任务实例并发、自动关闭与用户/商家取消竞争，均只有一次 RELEASE 和一次终态日志。
- 同订单多规格中途库存失败/流水冲突整体回滚，没有部分释放。
- CREATED 候选被报价、QUOTED 被重新报价、PENDING_PAYMENT 收到回调等竞态，不错误关闭新状态。
- 无支付记录不调用微信；NOTPAY 关单后关闭；远端 CLOSED 后关闭；SUCCESS 先入账且不写关闭日志。
- USERPAYING、网络异常、未知创建、NOT_EXISTS、占用中、支付配置关闭但有历史支付单：保持订单和预占，写重试信息。
- 关单成功但本地事务失败后再次执行，能够复用远端关闭状态完成本地幂等关闭。
- 微信真实网络调用不发生在数据库事务中；沿用订单→支付→规格升序锁序。
- 验证空库 V1～V8 与既有 V7 升级迁移；执行完整订单/支付/库存回归。

### 8.3 小程序与实际环境

- 复用或补充 CLOSED 状态、关闭时间和按钮禁用行为测试，JS/JSON 检查。
- 微信开发者工具/真机验证执行情况单独记录，不把静态检查当作手测通过。
- 真实商户及 HTTPS 环境仍未就绪，不进行真实扣款或微信关单联调；自动化使用测试 Fake，生产保持原支付开关配置。

## 9. 实施顺序与交付

1. 用户确认本文方案，尤其“默认启用并处理已有到期订单”。
2. 新增 Clock、超时配置校验、V8 重试字段与索引、到期候选和条件关闭 Mapper。
3. 扩展状态机，完成关闭事务、支付超时专用协调和持久化重试。
4. 接入 Spring 调度，设置测试关闭开关，完成边界、MySQL 并发及失败恢复验证。
5. 检查小程序 CLOSED 展示，补充必要兼容改动及验证。
6. 同步 docs/开发设计文档.md、doc/CONTEXT.md、doc/DESIGN_SUMMARY.md，完成功能后追加 doc/FEATURE_LIST.md，记录真实联调未执行的边界。

本文已获用户确认，按现有流程实现了代码、V8 迁移和默认开启配置，未初始化 OpenSpec。运行环境需启动新代码后任务才生效，本次自动化仅运行隔离的测试 MySQL，不直接操作用户业务数据库。
