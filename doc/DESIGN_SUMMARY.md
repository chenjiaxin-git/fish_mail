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

当前阶段（2026-10-29）：用户订单、微信预支付、可信回调/查单入账、取消前关单协调和超时自动关闭已实现。超时任务默认启用，支付仍默认关闭；真实商户和 HTTPS 环境暂未就绪，未联调真实收款。订阅消息、MinIO及部署交付已接入，真实模板和生产上线尚未联调。

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

实现以 order.confirm-timeout-minutes 和报价时固定的 pay_deadline_at 判定，now>=截止时间即到期。扫描为启动后延迟 30 秒、每轮结束后再延迟 30 秒；候选使用 ID 游标分页，失败逐单持久化退避 30 秒～30 分钟，避免阻塞后续订单。关闭前重新校验版本、状态及期限，有微信交易须确认关单；已付入账、不确定保留预占。库存和状态日志使用 SYSTEM/TIMEOUT_JOB，重复执行不重复释放。新报价清除旧重试延迟，保留累计次数。

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

库存关系：可用数量 = physical_stock_qty - reserved_qty。创建时预占预估数量；支付前按需补足实称超出的数量，保存在订单项 payment_extra_reserved_qty。支付成功扣实物 actualQuantity、释放 estimatedQuantity+额外预占；未支付取消只释放全部预占，已提交微信的交易须先确认关单。结果不确定时不释放。

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
| POST | /api/v1/orders/{id}/sync-payment | 本人订单查单恢复，最少间隔 3 秒 |
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
2. 回调使用 APIv3 微信支付公钥验签、AES-GCM 解密，校验商户号、AppID、付款人、JSAPI、CNY、订单金额和 out_trade_no。
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


## 后台经营页面已实现（2026-10-29）

- 看板：当天创建的全部订单数、当天微信成功收款毛额（未减人工退款）；按上海日期统计，跨日期四类待办可跳转筛选。
- 订单管理：筛选分页/详情、实称报价与版本校验、配送/完成/取消、人工退款凭证登记、Bearer Token鉴权的Excel下载，退款筛选与导出一致。
- 轮播管理：上传、NONE/商品/分类跳转、排序、启停、软删除、目标失效提示；新增管理API，复用原轮播表和公开API。
- 看板和列表每30秒可见轮询并支持手动刷新；隐藏/离开暂停，详情表单不参与自动刷新。
- 迁移V9只增支付成功状态/收款时间索引；实施和验收见 design/admin_business_pages_sign.md。

## 订阅消息与部署交付

- 三类事件与订单变更同事务登记，事件唯一键防重复入队；实际微信请求在事务外，拒收/无额度记录失败，明确可恢复错误有限重试，未知结果不重发。
- 下单用户点击触发一次性订阅申请，拒绝/失败/不支持不阻止下单，页面离开后迟到回调不再提交。
- GET /api/v1/users/subscription-config读取已启用模板，复用用户鉴权；不提供任意内容/收件人的消息发送接口。
- MinIO单机部署、私有桶和专用前缀账号；商品上传API和图片URL不变，旧图按明确不存在回退，SHA-256校验迁移只复制不删原图。
- deploy包含MySQL/MinIO/后端/Nginx、HTTPS配置、源码构建及备份恢复；V10追加通知字段，生产禁Fake登录和示例/共享JWT。正式微信联调和服务器上线后续执行。
