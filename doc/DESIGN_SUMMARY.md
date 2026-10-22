# DESIGN_SUMMARY.md

## 一页摘要

系统由微信小程序、PC 管理后台和 Spring Boot API 组成。客户提交的是预订单，商家确认实际称重后生成最终报价，用户确认报价并完成微信支付，商家再标记配送和完成。下单时预占库存，支付成功正式扣减；取消、关闭和超时释放预占库存。

核心正确性顺序：

~~~text
商品/规格可售 -> 库存预占 -> 商家称重改价 -> 用户确认
-> 微信预支付 -> 支付回调验签 -> 库存扣减 -> 配送 -> 完成
~~~

## 业务模块摘要

### 用户端

- 首页：轮播图、分类、商品搜索、商品列表。
- 商品：图片、简介、规格、单价、实时可用库存；库存为 0 禁止下单。
- 购物车：按规格保存数量，选中项结算，服务端重新校验价格和库存。
- 结算：地址、商品快照、收货备注、预估金额；提交后等待商家确认。
- 支付：商家报价后用户确认最终金额，再调用微信 JSAPI 支付。
- 订单：全部、待支付、待配送、已完成、已取消；详情展示改价前后、备注和状态。
- 地址和登录：微信 code2Session 登录，地址限本人操作。
- 通知：订阅消息覆盖提交、商家改价、配送完成；失败不阻塞订单。

### 商家端

- 登录：一期仅单管理员账号，BCrypt 密码，JWT 访问。
- 看板：今日订单数、今日销售额、待处理订单数量。
- 分类：新增、编辑、删除；有商品时禁止删除。
- 商品：新增/编辑鱼品，上传图片，维护规格、单价、单位、库存、预警量、上下架。
- 订单：筛选、详情、改价、标记配送中、标记完成、取消、登记人工退款。
- 轮播图：上传、排序、启停。
- 导出：按日期和状态导出订单 Excel，最大范围 31 天。

## 订单状态

| 状态 | 进入条件 | 主要操作 |
| --- | --- | --- |
| CREATED | 用户创建预订单，库存已预占 | 商家改价、取消、超时关闭 |
| QUOTED | 商家提交实称数量和最终金额 | 用户确认、取消、超时关闭 |
| PENDING_PAYMENT | 用户确认报价 | 微信支付、取消、超时关闭 |
| PAID_WAIT_DELIVERY | 支付回调成功 | 标记配送中、退款取消 |
| DELIVERING | 商家标记配送中 | 标记完成 |
| COMPLETED | 商家标记完成 | 无 |
| CANCELLED | 用户或商家取消 | 已支付需人工退款登记 |
| CLOSED | CREATED 或报价/支付超时 | 自动释放预占库存 |

关键时间默认值：CREATED 1440 分钟关闭；QUOTED 和 PENDING_PAYMENT 从 quoted_at 起 60 分钟关闭。

## 金额和改价

- 价格以 integer cent 存储，API 字段名统一使用 amountCent 或 finalAmountCent。
- 订单项保存 product_id、product_spec_id、名称、单位、计价方式、单价和图片快照。
- 重量规格使用 DECIMAL(12,3) kg；件数规格使用正整数。
- 商家可修改订单项 actualQuantity 和 finalAmountCent，并填写 reason。
- 每次改价写 order_price_revision，revision_no 单调递增，支持 quote_version 乐观锁。
- 支付后禁止改价、补差和自动退款；人工退款需要 refund_reference。

## 数据实体摘要

| 实体 | 关键字段 | 作用 |
| --- | --- | --- |
| admin_user | username、password_hash、status | 单管理员 |
| wx_user | openid、nickname、status | 微信用户 |
| user_address | wx_user_id、contact_name、phone、detail_address | 收货地址 |
| product_category | name、sort、status | 商品分类 |
| product | category_id、name、description、main_image_url、status | 商品主信息 |
| product_spec | quantity_kind、unit、price_cent、physical_stock_qty、reserved_qty、warning_qty | 规格、价格、库存 |
| shop_banner | image_url、jump_type、jump_target_id、sort、status | 首页轮播 |
| cart_item | wx_user_id、product_spec_id、quantity | 购物车 |
| sales_order | order_no、status、address_snapshot、estimated_amount_cent、final_amount_cent、quote_version | 订单主表 |
| sales_order_item | spec 快照、estimated_quantity、actual_quantity、amount_cent | 订单商品 |
| order_price_revision | revision_no、previous/final amount、reason | 改价审计 |
| payment_record | out_trade_no、transaction_id、state、amount_cent | 支付幂等 |
| stock_ledger | change_type、quantity、before/after、biz_key | 库存流水 |
| order_status_log | from/to_status、operator、reason | 状态审计 |
| message_log | template_type、state、error_message | 通知审计 |

库存关系：可用数量 = physical_stock_qty - reserved_qty。创建订单只增加 reserved_qty；支付成功同时减少 physical_stock_qty 和 reserved_qty；取消/关闭只减少 reserved_qty。

## 接口摘要

### 通用响应

~~~json
{"code":"0","message":"ok","data":{},"traceId":"trace-id"}
~~~

失败响应保留稳定 code，例如 STOCK_INSUFFICIENT、ORDER_STATE_INVALID、QUOTE_VERSION_CONFLICT、PAY_AMOUNT_MISMATCH。分页 page 从 1 开始，pageSize 最大 100。

### 用户 API

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | /api/v1/auth/wechat | code 换 openid，签发用户 Token |
| GET | /api/v1/banners | 启用轮播图 |
| GET | /api/v1/categories | 启用分类 |
| GET | /api/v1/products | 搜索和分类筛选 |
| GET | /api/v1/products/{id} | 商品和规格 |
| GET/POST/PUT/DELETE | /api/v1/addresses... | 本人地址 |
| GET/POST/PUT/DELETE | /api/v1/cart... | 本人购物车 |
| POST | /api/v1/orders | 创建预订单 |
| GET | /api/v1/orders | 订单列表 |
| GET | /api/v1/orders/{id} | 订单和改价历史 |
| POST | /api/v1/orders/{id}/confirm-quote | 确认最终金额 |
| POST | /api/v1/orders/{id}/prepay | 创建微信 JSAPI 预支付 |
| POST | /api/v1/orders/{id}/cancel | 取消未完成订单 |
| POST | /api/v1/payments/wechat/notify | 微信支付回调 |

### 管理 API

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | /api/v1/admin/login | 管理员登录 |
| GET | /api/v1/admin/dashboard | 经营看板 |
| GET/POST/PUT/DELETE | /api/v1/admin/categories... | 分类管理 |
| GET/POST/PUT | /api/v1/admin/products... | 商品和规格 |
| POST | /api/v1/admin/products/{id}/specs/{specId}/stock | 修改库存并写流水 |
| GET/POST/PUT/DELETE | /api/v1/admin/banners... | 轮播图 |
| GET | /api/v1/admin/orders | 订单筛选 |
| GET | /api/v1/admin/orders/{id} | 订单详情 |
| POST | /api/v1/admin/orders/{id}/quote | 称重改价 |
| POST | /api/v1/admin/orders/{id}/deliver | 标记配送中 |
| POST | /api/v1/admin/orders/{id}/complete | 标记完成 |
| POST | /api/v1/admin/orders/{id}/cancel | 商家取消 |
| POST | /api/v1/admin/orders/{id}/refund-confirmed | 登记人工退款 |
| GET | /api/v1/admin/orders/export | 导出 Excel |
| POST | /api/v1/admin/files | 商品图片上传 |

### 创建订单请求

~~~json
{
  "addressId": 12,
  "remark": "去鳞，切块",
  "items": [
    {"productSpecId": 101, "quantity": 1.5},
    {"productSpecId": 102, "quantity": 2}
  ],
  "idempotencyKey": "9f36c0f0-..."
}
~~~

### 商家改价请求

~~~json
{
  "quoteVersion": 1,
  "items": [
    {"orderItemId": 201, "actualQuantity": 1.2, "finalAmountCent": 3600},
    {"orderItemId": 202, "actualQuantity": 2, "finalAmountCent": 5000}
  ],
  "finalAmountCent": 8600,
  "reason": "实际称重1.2kg"
}
~~~

## 支付闭环

1. 用户确认报价后，后端创建 out_trade_no 并调用微信支付 JSAPI。
2. 回调使用 APIv3 验签，校验商户号、CNY、订单金额和 out_trade_no。
3. 以 out_trade_no、transaction_id 唯一约束和幂等逻辑处理重复通知。
4. 支付事务更新 payment_record、sales_order、stock_ledger 和 order_status_log。
5. 金额不一致时拒绝更新业务订单，只记录异常供人工处理。

## 关键验收摘要

- 并发抢最后一件库存只有一个订单成功。
- 同一支付回调多次调用只扣一次库存。
- 改价版本冲突不会覆盖较新金额。
- 支付前取消或超时会释放库存；支付成功会扣减库存。
- 支付后不能改价；已支付取消必须登记人工退款凭证。
- 下架商品不出现在用户端，但历史订单保留快照。
- 管理员和用户越权访问均被拒绝，后台导出只能查授权范围。

