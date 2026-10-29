# 微信小程序支付设计

> 状态：2026-10-29 五项问答已确认，代码及自动化验证完成；商户与 HTTPS 环境暂未就绪，真实收款及真机联调未执行。
> 日期：2026-10-29
> 功能名称：wechat_payment
> 依据：GUIDE.md、doc/CONTEXT.md、doc/STYLE_GUIDE.md、doc/DESIGN_SUMMARY.md、docs/开发设计文档.md、design/miniprogram_user_order_sign.md。
> 目标：接通用户确认报价后的微信 JSAPI 预支付、可信支付结果处理、库存正式扣减和小程序支付交互。

## 1. 本次范围

- 新增本人订单预支付 API，接入微信支付 APIv3 JSAPI。
- 新增公开的微信支付通知入口：验签、解密、业务核验、幂等入账。
- 可信支付成功后，同事务更新支付记录、订单、库存流水及状态日志。
- 小程序支持“确认价格并支付”“立即支付”，支付取消/失败后可重试，最终结果以服务端为准。
- 纳入与支付直接相关的微信查单、关单和现有用户/商家未支付取消流程协调，避免收款与库存释放竞争。
- 提供隔离的支付 Port 与测试 Fake，覆盖真实签名/解密、MySQL 事务与并发、前端支付行为测试。

不包含：微信自动退款、部分退款、财务对账、优惠/会员、订阅消息发送、完整订单超时自动关闭任务、后台支付管理页面。已有人工退款登记流程保留。

本阶段不把开发或模拟支付当作真实收款验收。真实联调需要商户资质、配置和外网 HTTPS 回调地址。

## 2. 当前实现与必须明确的规则

### 2.1 开发前现状

- 用户确认报价已使订单从 QUOTED 进入 PENDING_PAYMENT，并固定金额与报价版本。
- 用户订单详情目前提示支付暂未开放；没有 prepay、微信回调、支付写入或正式扣库存实现。
- payment_record 已建表，有 out_trade_no/transaction_id 唯一约束及 INIT/SUCCESS/FAILED 状态，目前仅支持后台读取。
- 订单项同时保存 estimatedQuantity 和 actualQuantity。创建订单只预占 estimatedQuantity；当前用户/商家未支付取消也按 estimatedQuantity 释放。
- 商家可在报价时录入大于预估的实称数量，当前报价流程未追加预占。
- finalAmountCent 当前允许为 0；用户和商家取消尚未协调微信交易状态。

### 2.2 库存口径（已确认并实现）

事实源 3.6 库存表写支付扣“下单数量”，但验收第 1 项明确：预订 1.5kg、实称 1.2kg，支付后实物库存减少 1.2kg。已有人工退款回补也使用 actualQuantity。

推荐：正式扣减按 actualQuantity，释放该订单全部预占。为允许实称大于预估且避免“已收款后才发现缺货”，预支付前补足差额预占，不修改原始预估快照。

- 订单项新增 payment_extra_reserved_qty，初始 0。
- 预支付准备事务预占 `max(actualQuantity - estimatedQuantity, 0)` 的差额，写 RESERVE 流水，使用独立幂等键 `PAY_EXTRA_RESERVE:<orderId>:<specId>`。
- 总预占量为 `estimatedQuantity + paymentExtraReservedQty`。相同订单、报价重复预支付不重复预占。
- actualQuantity 小于预估时保留原预占至支付或取消，不在预支付时提前释放。
- 支付时 physical_stock_qty 减 actualQuantity，reserved_qty 减总预占量。例：预估 1.5、实称 1.2，则分别减 1.2 和 1.5；预估 1.5、实称 1.8，则先补预占 0.3，支付分别减 1.8 和 1.8。
- 用户/商家未支付取消统一释放总预占量；已有订单 extra 默认 0，保持原行为。
- 差额库存不足时，在调用微信之前返回 STOCK_INSUFFICIENT，不生成可付款的微信订单。已确认报价保持原样，用户可以取消；本次不新增支付后改价或自动退款。
- 扣减用条件 UPDATE，要求 physical_stock_qty >= actualQuantity、reserved_qty >= 总预占量，并保证更新后 physical_stock_qty >= reserved_qty。
- 库存流水 quantity 表示实物扣减量，physical_before/after、reserved_before/after 分别记录两种实际变化，不能假设二者扣减数量相同。

用户已确认该方案及两种取消流程调整；事实源已同步实称扣减口径。

### 2.3 零金额订单（已确认并实现）

推荐微信预支付仅接受 `1 <= finalAmountCent <= 微信当前接口允许上限`，适配层在实施时按官方协议校验范围。0 元订单返回 PAY_AMOUNT_INVALID，提示“订单金额无法发起微信支付，请联系商家或取消订单”，不伪造微信收款、不擅自新增免费订单完成流程。

不在本次追改历史报价或改变现有商家报价的非负金额约束。将来若需要免费订单，应另行定义状态、库存和审计行为。

## 3. 接入方式与配置

### 3.1 Port/Adapter

使用微信支付官方 Java SDK 0.2.17 处理 APIv3 请求/响应签名、通知验签与 AES-GCM 解密，业务层只依赖 WeChatPayClient Port 及项目 DTO。版本已通过 Maven Central 元数据及官方 v0.2.17 源码核实，排除与 Spring 日志桥冲突的 commons-logging，不使用动态版本。

Port 能力：createJsapiPrepay、queryByOutTradeNo、closeByOutTradeNo、parseAndVerifyNotification、签发小程序支付参数。SDK 类型不传播到 Service/Controller。

选官方 SDK 的成本是新增 Maven 依赖、证书配置和更新管理，收益是减少自行维护签名协议。业务层保持可替换；测试使用 Fake，未来替换 SDK 适配器不改变订单和库存规则。

生产适配器必须验证微信 HTTP 响应签名，设置连接/读取超时；请求结果不确定不能当成未创建。仅同一 out_trade_no 的幂等调用可重试，次数有上限。

### 3.2 配置项

- WECHAT_PAY_ENABLED：显式开关，默认关闭。关闭时预支付返回 PAY_NOT_CONFIGURED，不自动降级为 Fake。
- WECHAT_APPID：沿用小程序 AppID；收款 AppID 必须与真实登录所属小程序一致。
- WECHAT_PAY_MCH_ID：商户号。
- WECHAT_PAY_MERCHANT_SERIAL_NO：商户 API 证书序列号。
- WECHAT_PAY_PRIVATE_KEY_PATH：商户 API 私钥挂载路径。
- WECHAT_PAY_API_V3_KEY：APIv3 解密密钥。
- WECHAT_PAY_PUBLIC_KEY_ID、WECHAT_PAY_PUBLIC_KEY_PATH：用户已选择微信支付公钥模式，使用 PUB_KEY_ID_ 开头的微信公钥标识及其 PEM 文件验签。商户证书序列号用于商户请求签名身份，与微信公钥 ID 分开配置。更换公钥配置后重启服务，真实联调前核对商户平台当前公钥。
- WECHAT_PAY_NOTIFY_URL：公网 HTTPS 通知地址，不能来自客户端传参。
- 网络超时和最大重试次数通过受控配置设置，不接受客户端覆盖。

启用真实支付而配置缺失时启动失败；关闭支付允许已有业务正常启动。私钥和密钥只从环境变量/挂载读取，不入库、不打印，不要求用户在聊天中粘贴。

测试 Fake 仅通过测试配置注入，不提供生产模拟支付成功入口。未配置真实微信登录的 Fake openid 不用于真实付款。

## 4. 数据模型与幂等

拟新增 Flyway V7，保留 V1～V6：

- sales_order_item：新增 payment_extra_reserved_qty DECIMAL(12,3) NOT NULL DEFAULT 0，非负约束。
- payment_record：在原有字段上补充 quote_version、appid、mchid、payer_openid（内部核验字段）、prepay_id、expires_at、phase、operation_token、operation_until、sync_after；错误类别保存在独立 payment_event_log。
- 每个业务订单只保留一个当前微信商户订单号，新增 order_id 唯一约束；迁移前检测历史重复记录，发现重复应报错并人工核对，不能擅自丢弃。当前尚无真实支付写入，预计无历史交易需要合并。
- out_trade_no 使用 FM + 订单数据库 ID 的大写 36 进制表示，长度不超过 15 字符；同订单重试复用，与业务 orderNo 通过 order_id 关联，响应 orderNo 仍返回原业务订单号。
- INIT/SUCCESS/FAILED 记录业务支付状态；外部操作阶段单独表示未提交、创建中、可支付、结果不确定、关闭中、已关闭，不用 FAILED 表示网络超时。
- 新增 payment_event_log 保存可检索的支付异常/通知处理记录：事件 ID、订单号/支付单号、事件类型、处理结果、脱敏原因和时间；事件幂等键唯一。不把完整敏感解密报文作为普通日志。

异常事件与失败交易使用独立短事务保存，避免随入账事务回滚而丢失。保留微信通知原始加密资源及必要头部的方案在实施时落实到受限字段，禁止明文保留 payer openid 等完整敏感报文到业务日志；现有 notify_raw 仅用于经过筛选的受限审计数据。

核心唯一键：payment_record.order_id、out_trade_no、transaction_id；PAY_DEDUCT 与追加预占/释放流水使用业务唯一键。相同 transaction_id 不能绑定其他订单。

## 5. 预支付接口

POST /api/v1/orders/{id}/prepay，需要 USER Token；请求 `{"quoteVersion":1}`。用户 ID、金额、商户号、AppID、openid、回调地址不接受客户端决定。

响应仍为 ApiResponse，data 包括 orderId、orderNo、payDeadlineAt 与 `paymentParams:{timeStamp,nonceStr,package,signType,paySign}`；signType=RSA，package 为 prepay_id 形式。支付签名、预支付 ID 不写普通日志或长期客户端存储。

### 5.1 准备事务

1. 按订单 ID+Token 用户锁订单，越权统一 ORDER_NOT_FOUND。
2. 仅 PENDING_PAYMENT 可发起；核验 quoteVersion、币种、金额、实称数量及服务端当前时间严格早于 payDeadlineAt。
3. 从持久化微信用户读取 openid，检查可用于真实支付；不能使用前端提交或临时 Fake openid。
4. 同事务补足额外预占、创建/复用 INIT 支付记录，固定金额、报价、付款人和截止时间，领取本次外部调用占用标记。
5. 已支付返回已支付状态错误并提示刷新；其他调用正在创建或关闭时返回 PAYMENT_PROCESSING，不并发调用微信。

### 5.2 外部调用与结果落库

- 事务外调用微信 JSAPI，time_expire 使用既有 payDeadlineAt，重试不能延长报价期限。
- 有有效 prepay_id 时重新生成客户端签名复用；缺少时用同一 out_trade_no 查询并重试创建。
- 短事务再次按订单→支付记录顺序锁定，校验占用标记/版本，保存 prepay_id、阶段和结果。不在数据库长事务中等待微信网络。
- 预支付成功只表示可付款，不变更业务订单为已支付、不正式扣库存。
- 调用超时/响应验签失败/进程中断属于结果不确定，保留支付记录和全部预占，不创建新 out_trade_no；后续查单恢复。
- 不确定状态的外部占用不能因本地超时就当作从未提交，恢复程序先查微信状态再处理；版本过期的调用结果不能覆盖关闭/成功状态。

## 6. 微信通知与支付成功事务

POST /api/v1/payments/wechat/notify 对微信开放，不要求用户 JWT，但只接受通过验签的通知。返回微信要求的 HTTP 状态/JSON，而非项目 ApiResponse。

### 6.1 信任建立

- 获取原始请求体与 Wechatpay 头部，校验平台证书/公钥序列标识、签名、时间戳和随机数后解密资源，不用客户端支付回调代替。
- 校验事件类型、trade_state=SUCCESS、appid、mchid、out_trade_no、transaction_id、amount.total、currency=CNY、付款人和支付记录一致，success_time 合法。
- amount.total 按商户订单原始总额核验，不能把优惠后的 payer_total 当作订单金额。
- 未知支付单、金额/商户/付款人不一致时不改业务订单，保存脱敏异常并返回失败；业务瞬时失败返回可重试响应。
- 可信但非成功的消息不把已有 SUCCESS 回退为 FAILED。每次重放仍须验签和业务身份核验。

### 6.2 入账

回调及服务端可信查单结果共用一个支付成功 Application Service：

1. 通过支付号定位订单，事务中统一按订单→支付记录→规格 ID 升序加锁，避免与预支付/取消锁顺序相反。
2. 重查支付记录并核验全部业务字段。同一交易已 SUCCESS 且身份一致，直接确认处理成功，不重复写流水。
3. 业务订单须为 PENDING_PAYMENT。当前已取消/关闭而收到成功结果时，不擅自复活订单或再扣其他订单预占；保存异常交由商家处理，不自动退款。
4. 经状态机 PAYMENT_SUCCESS 将订单变为 PAID_WAIT_DELIVERY，设置可信 paid_at；支付记录写 SUCCESS、transaction_id、trade_state。
5. 按每项 actualQuantity 正式扣实物库存，并释放 estimatedQuantity+paymentExtraReservedQty，写 `PAY_DEDUCT:<orderId>:<specId>` 流水。
6. 写 PAYMENT 来源的状态日志。同一事务全部提交后才能向微信返回成功；任何库存/唯一键/数据库失败完整回滚并要求重试。

有效付款可能在截止前发生但回调晚到：使用可信 success_time 和微信交易结果，不能仅因接收时已过 payDeadlineAt 而拒绝入账。微信显示已收款但付款时间/本地状态异常的情况单独留痕处理，不按普通“未支付”释放库存。

## 7. 支付与取消协调（已确认并实现）

直接沿用当前取消接口先释放库存会产生风险：用户可能已经付款，回调尚未到达。本次需要调整用户取消与商家“未支付取消”分支：

- 从未向微信提交预支付且没有不确定调用时，保留本地直接取消，释放全部预占。
- 已生成或可能生成微信订单时，先以短事务进入关闭操作阶段，使新预支付不能开始；网络查单/关单在事务外执行。
- 微信确认 SUCCESS：先走统一支付成功入账；用户取消返回状态变化错误，商家按已有已支付取消/人工退款登记规则处理，不再执行未支付库存释放。
- 微信明确 CLOSED 或确认关单成功：再次锁订单与支付记录确认版本，在本地取消事务中释放全部预占。
- 微信 NOTPAY：关单后才能取消。USERPAYING、网络超时、验签失败等返回 PAYMENT_PROCESSING/PAYMENT_REMOTE_ERROR，不改 CANCELLED、不释放库存。
- 曾提交但结果不确定，查单 ORDERNOTEXIST 也不能直接证明没有迟到创建请求；保留不确定状态，重试恢复并确证终止后再释放，不能以占用超时或单次查不到为依据。
- 取消和回调竞争时，同一订单行锁保证只能走一次终态处理；事务中再次检查状态。微信关单后不会继续提供可支付参数。
- 操作标记必须可在进程重启后恢复；重试由相同预支付/取消入口执行，不新增生产“强制成功/忽略微信直接取消”接口。

这种不确定状态可能暂时保留库存并要求稍后重试/人工核查，优先保证已经收款时不误释放。完整自动超时关闭后续必须复用该协调流程，不能绕过关单直接改 CLOSED。

## 8. 小程序交互

- QUOTED 且有效：显示“确认价格并支付”，弹窗展示最终金额。先调用现有 confirm-quote，再调用 prepay；若预支付失败，保持 PENDING_PAYMENT，可再次点“立即支付”，不重复改价确认。
- PENDING_PAYMENT 且未过期：显示“立即支付”。按钮与确认/取消共用提交锁，避免重复调起收银台。
- services/payment.js 封装预支付和 wx.requestPayment；页面只负责交互。openid、商户私钥、APIv3 key 不下发。
- wx.requestPayment 的 success 仅触发刷新，不直接显示“支付成功”。只有服务端订单进入 PAID_WAIT_DELIVERY 或后续已支付状态才显示成功。
- 支付返回后使用有限次数的订单状态刷新；需要补偿时通过受鉴权、限频的 `POST /api/v1/orders/{id}/sync-payment` 查单入口执行，服务端核验微信响应并复用入账服务。不得把普通 GET 订单详情改成隐式外部写操作。
- cancel/失败/结果等待均显示中文提示；支付取消只取消收银台，不等于取消业务订单，用户仍可重试或主动取消。
- onShow 重新查询订单状态，页面隐藏/销毁停止轮询，不无限重试。回调暂未到达显示“支付结果确认中”，提供刷新入口，不能擅自切回未支付或跳已支付页。
- 报价已过期禁用新预支付；但仍允许查单以确认截止前完成的付款。订单取消失败时刷新真实状态。
- Fake 模式仅验证 Service 和页面行为，不向真实 wx.requestPayment 提供伪造签名参数。

## 9. 错误、异常与可观测性

拟新增：PAY_NOT_CONFIGURED、PAY_AMOUNT_INVALID、PAYMENT_PROCESSING、PAYMENT_REMOTE_ERROR、PAYMENT_NOTIFY_INVALID、PAY_AMOUNT_MISMATCH；复用订单归属/状态、报价冲突、库存不足错误。

用户接口返回中文安全信息；微信通知失败只返回协议需要的信息，不泄露证书、报文、SQL 或堆栈。支付异常可按订单号/支付单号检索，日志保留 traceId、动作、结果、重试次数，禁止打印支付签名、密钥、Token 和完整 openid。

## 10. 验证与完成标准

### 10.1 自动化

- 预支付：本人成功、越权、错误 Token 类型、非待支付、报价版本错误、截止时间、金额零值/超上限、无真实配置、无有效付款人、重复请求复用和额外预占不足。
- 协议适配：测试生成 RSA 签名和 AES-GCM 通知样本，覆盖验签/解密成功及失败、未知证书、头部/报文篡改、无效响应签名；不只测试“Fake 返回成功”。
- 支付成功：金额/商户/AppID/付款人/币种不一致、未知订单、transaction_id 跨单冲突、重复与乱序通知、成功时间和到达时间差异。
- MySQL：支付记录/订单/库存/状态日志同事务；1.5→1.2 与 1.5→1.8 两种库存口径、重复回调只扣一次、取消/回调竞争、网络不确定时保留库存、流水失败回滚、进程恢复标记。
- 取消：未提交微信可本地取消；NOTPAY 关单后释放；SUCCESS 先入账；USERPAYING/超时/未知创建状态不释放；商家已支付退款流程回归。
- 前端：确认→预支付→调起收银台；用户取消、网络失败、重复点击、回调延迟、回前台刷新、轮询停止，客户端 success 不直接宣告收款成功。
- 全量后端与现有订单/库存回归，小程序 JS/JSON 检查及行为测试。

### 10.2 真实联调

需用户在自有环境准备：已开通小程序支付的商户号、与小程序绑定的 AppID、APIv3 密钥、商户私钥/证书序列号、平台证书或公钥模式、合法 request 域名与公网 HTTPS 回调地址。只确认是否具备及配置方式，不在聊天中索取密钥正文。

使用用户明确授权的小额真实订单验证支付和通知；自动化测试阶段不自动产生真实扣款。尚未联调时必须明确记录，不以测试 Fake 宣称微信收款已验证。

## 11. 实施顺序

1. 用户确认本设计，尤其第 2、7 节的业务口径与范围。
2. 核实官方 SDK 接入模式/版本，锁定依赖；增加配置校验、Port、生产适配器和测试 Fake。
3. V7 迁移及支付记录/事件/额外预占 Mapper，状态机支付成功动作。
4. 预支付、可信入账、通知、查单恢复与两端取消协调；补充核心测试。
5. 小程序支付服务、详情交互和有限状态刷新。
6. 回归测试、真实配置及联调条件检查；需要真实扣款时单独由用户授权具体测试订单。
7. 同步设计事实源和上下文，完成后追加 FEATURE_LIST，注明自动化与真实联调各自结果。

以上顺序已按用户确认执行至代码与自动化验证；真实收款联调待环境就绪。

## 12. 问答确认结果及实现补充

1. 已确认：采用实称扣减，付款前补足预占，库存不足阻止支付。
2. 已确认：纳入查单、关单与用户/商家取消协调，不确定状态不释放库存。
3. 已确认：0 元订单阻止支付，提示联系商家或取消，不新增免费订单流程。
4. 已确认：官方 Java SDK + 微信支付公钥模式。
5. 已确认：真实联调环境暂未就绪，先完成代码与自动化测试，不发起真实扣款。

实现补充：操作占用标记有效期 120 秒，过期后通过原入口查询恢复；同步查单间隔至少 3 秒。SDK 网络超时默认 5 秒、最大 15 秒，不做隐式网络重试；不确定创建可由用户以相同支付号重试恢复。客户端支付结果刷新最多 3 次，间隔 3.5 秒，隐藏页面后停止。

配置清单见 backend/payment.env.example，环境变量由进程/容器注入，该文件不会自动加载。支付开关默认关闭；启用时要求真实登录配置且 AppID 一致，配置缺失或无效时启动失败，不回退到测试 Fake。

自动化结果和未执行的真实联调项目见 doc/FEATURE_LIST.md。本节确认后已按本文完成代码和数据库迁移，未创建 OpenSpec 文件。
