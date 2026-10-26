# 后台管理系统订单模块设计

> 状态：按本次任务执行
> 功能名称：order_module
> 依赖：admin_login_sign.md、doc/CONTEXT.md、doc/STYLE_GUIDE.md、doc/DESIGN_SUMMARY.md、docs/开发设计文档.md
> 本次目标：完成后端管理系统订单查询、称重改价、订单状态操作、人工退款登记、订单 Excel 导出及订单域自动化测试。

## 1. 任务范围

### 1.1 包含

- 管理端订单列表分页、状态/日期/订单号/收货手机号筛选。
- 订单详情，返回订单项快照、改价记录、支付记录和状态日志。
- 商家称重改价：修改实称数量和订单项最终金额，使用 `quoteVersion` 乐观锁。
- 标记配送中、标记完成、商家取消。
- 已支付取消后登记人工退款凭证，确认退款后释放库存并写库存流水。
- 按日期和状态导出订单 `.xlsx`，日期范围最大 31 天。
- 订单数据库结构、统一订单状态机、订单审计和管理操作审计。

### 1.2 不包含

- 用户端登录、地址、购物车、用户下单、用户确认报价和取消接口。
- 微信 JSAPI 预支付、支付回调验签、支付幂等处理和支付退款接口。
- 定时关闭订单和订阅消息发送。
- 管理端 Vue 订单页面。
- 会员、优惠、营销、多门店、多角色、骑手和配送轨迹。

用户下单和支付是订单数据来源，将在后续模块补充；本次订单管理模块按 `docs/开发设计文档.md` 的订单状态和数据结构实现，不改变其业务边界。

## 2. 现状与接入点

- 当前已有 Spring Boot 3、MyBatis、Flyway、管理员 JWT 鉴权、商品和库存模块。
- 当前迁移为 V1/V2/V3，本次新增 V4 订单结构，不修改已发布脚本。
- 复用 `AdminPrincipal`、`AdminOperationLogMapper`、`ApiResponse`、`BusinessException` 和 `GlobalExceptionHandler`。
- 订单和库存操作通过 Application Service 事务完成，Controller 只做参数绑定、鉴权上下文和响应封装。

## 3. 数据模型

### 3.1 sales_order

字段按设计事实源保存订单号、用户标识、状态、地址 JSON 快照、预估/最终金额、报价版本、支付/配送/完成/取消时间、退款状态和乐观锁版本。`order_no` 唯一，列表按订单状态、创建时间、支付截止时间和用户建立索引。

### 3.2 sales_order_item

保存商品、规格、名称、单位、计价方式、单价和图片快照；保存 `estimated_quantity`、`actual_quantity`、预估金额和最终金额。数量为 `DECIMAL(12,3)`，金额为非负 `BIGINT`。

### 3.3 order_price_revision

每次改价生成递增 `revision_no`，保存改价前后总额、原因、订单项 JSON 快照和操作者，唯一约束 `(order_id, revision_no)`。`revision_no=0` 留给创建订单时的预估单，本次管理端改价从 1 开始。

### 3.4 payment_record、order_status_log、message_log

- `payment_record` 预留微信支付幂等，`out_trade_no` 和 `transaction_id` 唯一。
- `order_status_log` 记录状态前后值、操作者、原因、来源和时间。
- `message_log` 预留通知审计，本次不发送外部通知。
- 既有 `product_stock_ledger` 增加 `order_id`，统一记录 RESERVE、PAY_DEDUCT、RELEASE、REFUND_RELEASE 流水，`biz_key` 保持唯一。

## 4. 状态机和事务

允许的管理端动作：

| 动作 | 当前状态 | 目标状态 | 事务内副作用 |
| --- | --- | --- | --- |
| quote | CREATED、QUOTED | QUOTED | 条件更新 quoteVersion，写价格修订和状态日志 |
| deliver | PAID_WAIT_DELIVERY | DELIVERING | 写状态日志 |
| complete | DELIVERING | COMPLETED | 写状态日志 |
| cancel | CREATED、QUOTED、PENDING_PAYMENT | CANCELLED | 释放预占库存，写状态日志 |
| cancel | PAID_WAIT_DELIVERY | CANCELLED | `refund_status=REQUIRED`，不自动释放库存 |
| refund-confirmed | CANCELLED 且 REQUIRED | CANCELLED 且 MANUAL_REFUNDED | 补回实物库存，写 REFUND_RELEASE 流水 |

所有状态操作调用 `OrderStateMachine`，使用 `sales_order.version` 条件更新。重复的 deliver、complete、cancel 或 refund-confirmed 请求直接返回当前详情，不重复写流水。改价请求必须提交完整的订单项集合，最终总额必须等于订单项最终金额之和。

## 5. API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /api/v1/admin/orders | 分页筛选订单 |
| GET | /api/v1/admin/orders/{id} | 订单详情 |
| POST | /api/v1/admin/orders/{id}/quote | 称重改价 |
| POST | /api/v1/admin/orders/{id}/deliver | 标记配送中 |
| POST | /api/v1/admin/orders/{id}/complete | 标记完成 |
| POST | /api/v1/admin/orders/{id}/cancel | 商家取消 |
| POST | /api/v1/admin/orders/{id}/refund-confirmed | 登记人工退款完成 |
| GET | /api/v1/admin/orders/export | 导出 Excel，最大 31 天 |

所有接口要求管理员 Token，并写 `admin_operation_log`。分页 `page >= 1`、`1 <= pageSize <= 100`；导出日期范围必须闭区间且不超过 31 天。

## 6. 导出

使用 Apache POI SXSSF 生成 `.xlsx`，列为订单号、创建时间、状态、联系人、手机号、收货地址、预估金额、最终金额、实称明细、退款状态、退款凭证。导出只查询授权管理端数据，不接收排序 SQL 或任意列名。

## 7. 错误码

- `ORDER_NOT_FOUND`
- `ORDER_STATE_INVALID`
- `QUOTE_VERSION_CONFLICT`
- `ORDER_QUOTE_ITEMS_INVALID`
- `ORDER_QUOTE_AMOUNT_MISMATCH`
- `REFUND_NOT_REQUIRED`
- `REFUND_REFERENCE_REQUIRED`
- `STOCK_INSUFFICIENT`
- `STOCK_VERSION_CONFLICT`
- `VALIDATION_ERROR`

## 8. 测试方案

- 状态机覆盖全部允许和拒绝状态。
- 改价成功、版本冲突、订单项缺失/重复、总额不匹配、支付后改价拒绝。
- 取消未支付释放预占；已支付取消只标记 REQUIRED；退款确认补回库存。
- 配送、完成和取消重复请求不重复写状态日志。
- 列表分页上限、导出日期范围上限、查询筛选和错误码。

## 9. 需确认信息和冲突点

1. 本次按“后端管理系统订单模块”理解，不包含用户端下单和微信支付接口；若需要一并完成支付闭环，请另行确认。
2. 设计事实源同时提到 `stock_ledger` 和既有 `product_stock_ledger`，本次沿用已发布表名 `product_stock_ledger` 并增加 `order_id`，避免破坏商品库存模块。
3. 已支付取消后的人工退款确认按设计事实源“退款确认后释放”执行，补回实物库存并保留 `REFUND_RELEASE` 流水；如门店要求退款后不回补实物库存，需改为人工盘点调整。
4. 订单关闭/超时定时器不在本次范围，`pay_deadline_at` 和 `created_at` 已预留后续任务使用。
