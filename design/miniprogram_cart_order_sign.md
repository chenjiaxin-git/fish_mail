# 微信小程序购物车与下单设计

> 状态：已确认（2026-10-28，6 项决策均按推荐方案执行）
> 功能名称：miniprogram_cart_order
> 依赖：doc/CONTEXT.md、doc/STYLE_GUIDE.md、doc/DESIGN_SUMMARY.md、docs/开发设计文档.md、design/miniprogram_product_detail_sign.md
> 本次目标：完成小程序购物车页面与用户选择商品下单（创建预订单）闭环，并补齐其后端前置能力（微信登录、用户 Token、地址、购物车、创建订单）。

## 1. 任务范围

### 1.1 包含

- Flyway V6 迁移：`wx_user`、`user_address`、`cart_item` 三张表；`sales_order` 补充 `idempotency_key` 幂等列。
- 用户微信登录 `POST /api/v1/auth/wechat`：code2Session 换 openid，落库 `wx_user`，签发用户 JWT（7 天，与管理员 Token 互相隔离）。
- 用户鉴权链路：用户 Token 解析与 `/api/v1/addresses/**`、`/api/v1/cart/**`、`/api/v1/orders/**` 强制鉴权；用户接口不信任请求体中的 userId。
- 地址管理 API：本人地址列表、新增、编辑、删除，上限 20 个，支持默认地址。
- 购物车 API：列表（实时回填价格、可用库存、可购状态）、加入（同规格合并累加）、修改数量、删除。
- 创建预订单 API `POST /api/v1/orders`：服务端重校验地址归属、商品可售、数量约束和库存，条件更新预占 `reserved_qty`，写 `RESERVE` 库存流水、订单项快照、状态日志，按 `idempotencyKey` 幂等。
- 小程序页面：
  - 购物车页：勾选、数量步进、删除、全选、合计、去结算。
  - 结算页：地址选择、商品快照、收货备注、预估金额、防重复提交下单。
  - 地址管理页：列表、新增、编辑、删除（结算页选择地址依赖）。
  - 商品详情页改造：新增“加入购物车”，“立即下单”改为立即购买直达结算页。
- 下单成功后同事务删除本次结算的购物车项（见 8.1）。
- 新增 `MoneyCalculator`（金额乘法/合计统一入口）与 `OrderAction.CREATE` 状态机动作。
- 自动化测试：登录、地址、购物车、下单成功/失败、幂等重放、库存并发预占。

### 1.2 不包含

- 订单列表、订单详情、改价确认、微信支付、用户取消、订阅消息（后续任务）。
- CREATED 超时自动关闭定时任务（后续任务）。
- 用户资料页、微信头像昵称授权（登录仅落 openid）。
- 购物车角标、失效购物车项自动清理任务。
- 后台管理相关改动。

## 2. 现状与接入点

- 小程序已有 `services/request.js`（`{code,message,data,traceId}` 解析 + Bearer Token）、`services/auth.js`（已按约定调用 `POST /api/v1/auth/wechat`，读取 `data.accessToken || data.token`，后端目前无对应实现）、`services/catalog.js`、`utils/format.js`（`formatMoneyCent`、`formatQuantity`、`resolveImageUrl` 等）。
- 小程序商品详情页已有规格选择与数量边界逻辑（`minQuantity`/`stepQuantity`/`availableQty`），可复用到加购；“立即下单”当前提示“下单功能暂未开放”。
- 后端已有：`ApiResponse`、`ErrorCode`、管理端 JWT（`AdminJwtTokenService`、`AdminJwtAuthenticationFilter`）、公开目录接口、管理端订单模块（改价/配送/完成/取消/退款登记）、`OrderStateMachine`（无 CREATE 动作）、`ProductSpecMapper.releaseReserved/restorePhysical`（无预占方法）、`ProductStockLedgerMapper.insert`。
- `sales_order`、`sales_order_item`、`order_status_log`、`product_stock_ledger`（含 `order_id`、`biz_key` 唯一）已建表；订单目前无法创建（`SalesOrderMapper` 无 insert）。
- 缺口：`wx_user`/`user_address`/`cart_item` 表、用户 Token、地址/购物车/创建订单接口均不存在；`MoneyCalculator` 尚未创建。

## 3. 后端 API 设计

### 3.1 用户登录与鉴权

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | /api/v1/auth/wechat | 公开 | code 换 openid，签发用户 Token |

请求：`{"code": "wx-login-code"}`（`code` 必填，1-128 字符）。

响应 `data`：`{"accessToken": "<jwt>", "expiresIn": 604800}`（`auth.js` 已按 `accessToken || token` 兼容读取）。

实现结构：

- 新增 Port `WeChatAuthClient`：`code2Session(code) -> openid`；适配器 `HttpWeChatAuthClient` 读取环境变量 `WECHAT_APPID`、`WECHAT_SECRET`（不入库、不打日志）；测试与本地用 `FakeWeChatAuthClient`。微信侧失败统一返回 `AUTH_LOGIN_FAILED`。
- 新增 `wx_user` 表（见 3.6），登录按 openid upsert 并刷新 `last_login_at`；不存储 session_key。
- 新增 `UserJwtTokenService`：密钥环境变量 `JWT_USER_SECRET`，有效期 7 天，claims 含 `wxUserId`、`tokenType=USER`；管理员 Token 含 `tokenType=ADMIN`，两类 Token 互不可用（用户接口拒绝 ADMIN Token，反之亦然）。
- 新增用户鉴权过滤器（解析 `Authorization: Bearer`），保护 `/api/v1/addresses/**`、`/api/v1/cart/**`、`/api/v1/orders/**`、`/api/v1/users/**`；公开目录接口保持匿名。`wxUserId` 只从 Token 获取。
- 新增 `GET /api/v1/users/me`（登录用户资料）供小程序探测登录态（可选实现，供 auth 联调）。

### 3.2 地址管理

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | /api/v1/addresses | 用户 | 本人地址列表（默认在前） |
| POST | /api/v1/addresses | 用户 | 新增地址 |
| PUT | /api/v1/addresses/{id} | 用户 | 编辑本人地址 |
| DELETE | /api/v1/addresses/{id} | 用户 | 软删除本人地址 |

请求字段：`contactName`（1-50）、`phone`（11 位手机号）、`detailAddress`（1-200）、`isDefault`。

规则：

- 每用户上限 20 条（软删不计），超限返回 `ADDRESS_LIMIT_EXCEEDED`。
- `isDefault=true` 时同事务取消该用户其他默认标记；更新/删除本人地址按 `id + wxUserId` 归属过滤，非本人统一返回 `ADDRESS_NOT_FOUND`（不泄露存在性）。
- 新增错误码：`ADDRESS_NOT_FOUND`、`ADDRESS_LIMIT_EXCEEDED`。

### 3.3 购物车

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | /api/v1/cart | 用户 | 购物车列表（实时价格/库存/可购状态） |
| POST | /api/v1/cart | 用户 | 加入购物车（同规格合并累加） |
| PUT | /api/v1/cart/{id} | 用户 | 修改数量 |
| DELETE | /api/v1/cart/{id} | 用户 | 删除条目 |

`GET /api/v1/cart` 响应 `data[]`（数组直接返回，实时联查商品/规格，不存冗余快照；`minQuantity`/`stepQuantity` 为实施补充字段，供购物车步进器与详情页保持一致约束）：

~~~json
{
  "id": 5,
  "productSpecId": 101,
  "productId": 10,
  "productName": "草鱼",
  "specName": "按斤",
  "quantityKind": "WEIGHT",
  "unit": "kg",
  "priceCent": 3000,
  "quantity": 1.500,
  "mainImageUrl": "/uploads/products/uuid.jpg",
  "availableQty": 3.500,
  "minQuantity": 0.500,
  "stepQuantity": 0.500,
  "purchasable": true,
  "unavailableReason": null
}
~~~

- `purchasable=false` 场景：规格/商品下架或软删除（`unavailableReason="商品已下架"`）、可用库存不足当前数量（`"可用库存不足"`）；结算只允许 `purchasable=true` 的条目。
- `POST /api/v1/cart`：`{"productSpecId":101,"quantity":1.5}`；条目不存在则插入，存在则累加数量（唯一约束 `(wx_user_id, product_spec_id)`）。
- 数量校验（加购/改数量一致）：`quantity > 0`，满足规格 `minQuantity`/`stepQuantity` 网格（PIECE 取整、WEIGHT 三位小数），合并/修改后总量不得超过实时可用库存（`physical_stock_qty - reserved_qty`），不足返回 `STOCK_INSUFFICIENT`；规格不可售返回 `PRODUCT_NOT_AVAILABLE`。
- 非本人条目统一返回 `CART_ITEM_NOT_FOUND`。

### 3.4 创建预订单

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | /api/v1/orders | 用户 | 创建预订单（状态 CREATED，库存预占） |

请求：

~~~json
{
  "addressId": 12,
  "remark": "去鳞，切块",
  "items": [
    {"productSpecId": 101, "quantity": 1.5},
    {"productSpecId": 102, "quantity": 2}
  ],
  "cartItemIds": [5, 6],
  "idempotencyKey": "9f36c0f0-..."
}
~~~

校验与流程（单事务，Application Service）：

1. `addressId` 必须属于当前用户且未删除，否则 `ADDRESS_NOT_FOUND`；`remark` ≤ 500 字。
2. `items` 1-50 条、`productSpecId` 不重复；每项 `quantity` 满足 `minQuantity`/`stepQuantity` 网格。
3. 逐项重读规格与商品：必须 `ACTIVE` 且未删除，否则 `PRODUCT_NOT_AVAILABLE`；单价取当前 `price_cent` 生成快照。
4. 逐项条件更新预占：`UPDATE product_spec SET reserved_qty = reserved_qty + #{qty} WHERE id=#{specId} AND status='ACTIVE' AND physical_stock_qty - reserved_qty >= #{qty}`，受影响行数为 0 抛 `STOCK_INSUFFICIENT`，整体回滚。
5. 金额：`estimated_amount_cent = Σ MoneyCalculator.itemAmount(unitPriceCent, quantity)`（HALF_UP 到整数分）。
6. 落库：
   - `sales_order`：`status=CREATED`、`order_no`、`wx_user_id`、`address_snapshot`（`contact_name/phone/detail_address`）、`remark`、`estimated_amount_cent`、`source=MINI_PROGRAM`、`idempotency_key`。
   - `sales_order_item`：商品/规格名、`quantity_kind`、`unit`、`unit_price_cent`、`estimated_quantity=quantity`、`estimated_amount_cent`、`product_image_url` 快照。
   - `product_stock_ledger`：`change_type=RESERVE`，`order_id`、before/after、`biz_key="RESERVE:{orderNo}:{specId}"`（唯一兜底）。
   - `order_status_log`：`from_status=null`、`to_status=CREATED`、`operator_type=USER`。
   - `cartItemIds`：按 `id + wx_user_id` 同事务删除；立即购买传 `[]`。
7. 幂等：`idempotencyKey` 必填（1-64 字符），`(wx_user_id, idempotency_key)` 唯一；重复请求不重复预占、不重复写流水，直接返回首次创建的订单信息（同 `orderNo`）。
8. 响应 `data`：`{"id": 1, "orderNo": "FM20261028000001", "status": "CREATED", "estimatedAmountCent": 7500}`。

`OrderStateMachine` 新增 `OrderAction.CREATE`（入口动作，创建即合法），状态机与 `docs/开发设计文档.md` 同步。订单号生成：`FM + yyyyMMdd + 6 位序列`，唯一索引冲突时重试。

新增错误码：`AUTH_LOGIN_FAILED`、`ADDRESS_NOT_FOUND`、`ADDRESS_LIMIT_EXCEEDED`、`CART_ITEM_NOT_FOUND`、`PRODUCT_NOT_AVAILABLE`；复用 `AUTH_REQUIRED`、`STOCK_INSUFFICIENT`、`VALIDATION_ERROR`、`ORDER_NOT_FOUND`。

### 3.5 实现结构

~~~text
com.fishmall.user/            # 新模块：登录、用户、地址
├─ api/WechatAuthController、AddressController、UserController
├─ application/WxUserAuthService、UserAddressService
├─ domain/WxUser、UserAddress
├─ infrastructure/WeChatAuthClient(Http)、WxUserMapper、UserAddressMapper
└─ security/UserJwtTokenService、UserAuthFilter、UserPrincipal
com.fishmall.cart/
├─ api/CartController
├─ application/CartService
└─ infrastructure/CartItemMapper
com.fishmall.order/
├─ api/OrderController                 # 用户端
├─ application/OrderCreateService      # 事务：预占+快照+幂等
└─ ...（既有管理端保持不变）
com.fishmall.common/MoneyCalculator    # 金额乘法/合计唯一入口
~~~

- 事务注解只放在 Application Service 公开方法；Mapper 负责条件 UPDATE 与唯一键冲突映射。
- `SalesOrderMapper` 新增 insert / 按 `(wx_user_id, idempotency_key)` 查询 / `SalesOrderItemMapper.insert`。
- `ProductSpecMapper` 新增 `reserve(specId, qty)` 条件更新；`writeStockLedger` 逻辑抽为可复用封装（`RESERVE` 类型）。

### 3.6 V6 迁移要点

- `wx_user`：`id`、`openid`（唯一）、`nickname`、`avatar_url`、`status`、`last_login_at`、时间戳。
- `user_address`：`id`、`wx_user_id`、`contact_name`、`phone`、`detail_address`、`is_default`、`deleted`、时间戳；索引 `(wx_user_id, deleted, is_default)`。
- `cart_item`：`id`、`wx_user_id`、`product_spec_id`、`quantity DECIMAL(12,3)`、时间戳；唯一约束 `(wx_user_id, product_spec_id)`；`quantity > 0`。
- `sales_order` 增列：`idempotency_key VARCHAR(64) NULL`，唯一约束 `(wx_user_id, idempotency_key)`（管理端手工单为 NULL，MySQL 唯一索引允许多个 NULL）。
- 字段与 `docs/开发设计文档.md` 4.3 对齐；`idempotency_key` 为文档未列的补充字段，实施时同步回写设计文档（见 8.7）。

## 4. 小程序页面设计

### 4.1 工程结构

~~~text
miniprogram/
├─ app.json                        # 注册 cart/checkout/address 页面，新增 tabBar（见 8.2）
├─ pages/
│  ├─ home/                        # 新增购物车入口图标
│  ├─ product-detail/              # 底部栏改“加入购物车”+“立即下单”
│  ├─ cart/                        # 新增
│  ├─ checkout/                    # 新增（结算/确认订单）
│  └─ address/
│     ├─ list/                     # 新增
│     └─ edit/                     # 新增（新增/编辑共用，id 可空）
├─ services/
│  ├─ cart.js                      # 新增 getCart/addCart/updateCartItem/deleteCartItem
│  ├─ address.js                   # 新增 getAddresses/createAddress/updateAddress/deleteAddress
│  └─ order.js                     # 新增 createOrder（携带 idempotencyKey）
└─ utils/
   └─ format.js                    # 复用，不新增金额逻辑
~~~

### 4.2 购物车页 `pages/cart`

- 进入时 `GET /api/v1/cart` 拉取实时价格/库存；勾选状态只存前端内存，刷新后重置为全选可购项。
- 条目卡片：勾选框、图片（`resolveImageUrl`，失败占位）、名称、规格名、单价（`¥x.xx / 单位`）、数量步进器、小计、删除按钮。
- 步进约束与详情页一致（`minQuantity`/`stepQuantity`、上限 `availableQty`，越界回退 + toast）；修改成功后以服务端返回为准。
- 不可购条目（下架/库存不足）：置灰不可勾选，显示中文原因，可删除。
- 底部栏：全选、合计（选中项 Σ，前端按分计算 HALF_UP 后 `formatMoneyCent` 显示）、“去结算”（选中可购数 ≥1 才可点）。
- 空态“购物车空空如也，去逛逛”（跳首页）、加载失败重试、网络错误中文文案；请求带加载锁防重复。
- 点击条目跳商品详情页。

### 4.3 结算页 `pages/checkout`

- 入参两种来源：购物车结算（选中项的 `productSpecId/quantity/cartItemIds`）或详情页立即购买（单商品，`cartItemIds=[]`）。
- 地址栏：默认地址优先，否则取列表第一条；无地址时提示“请先添加收货地址”并可跳转地址新增；点击可进入地址列表选择（选中回填）。
- 商品清单（只读）：图片、名称、规格、单价、数量、小计；顶部提示“预估金额，以商家实际称重后为准”（预订单语义）。
- 备注输入框：≤500 字，实时计数截断提示。
- 合计行：预估总金额（分计算后展示元）。
- “提交订单”按钮：进入页面生成一次 `idempotencyKey`（uuid），点击后 loading 锁防重复，请求失败恢复按钮；成功后 toast“下单成功，等待商家确认”并 `reLaunch` 到首页（订单列表为后续任务）。
- 服务端返回 `STOCK_INSUFFICIENT`/`PRODUCT_NOT_AVAILABLE`/`ADDRESS_NOT_FOUND` 时给出对应中文提示并刷新商品区/地址区。

### 4.4 地址管理页

- 列表页：联系人、手机号、详细地址、“默认”标记；编辑/删除（删除需 `wx.showModal` 确认）；“新增地址”按钮；从结算页进入时带选择模式，点击条目回填结算页。
- 编辑页：联系人（≤50）、手机号（11 位，前端+服务端校验）、详细地址（≤200）、设为默认开关；保存防重复；达 20 条上限时新增按钮置灰并提示。

### 4.5 商品详情页改造

- 底部栏改为两个按钮：“加入购物车”（`POST /api/v1/cart`，成功 toast“已加入购物车”）与“立即下单”（立即购买 → 结算页单商品）。
- 两按钮沿用既有库存禁用约束：无规格或所选规格 `availableQty <= 0` 禁用并提示售罄；数量约束复用现有逻辑。
- 未登录点击时 `ensureLogin()` 后继续。

## 5. 金额、数量和约束

- 金额：后端字段 `amountCent`/`estimatedAmountCent` 整数分；乘法与合计只走 `MoneyCalculator`（`priceCent × quantity` HALF_UP 到整数分）；前端只用 `formatMoneyCent` 展示，不拼字符串。
- 数量：`DECIMAL(12,3)`；PIECE 取整、WEIGHT 三位小数；网格校验 `(quantity - minQuantity) % stepQuantity == 0`（BigDecimal 精确比较）。
- 备注 ≤ 500 字；地址字段长度见 3.2；搜索词/输入统一截断提示。
- 幂等键：uuid 36 字符，进入结算页生成一次，重复提交复用。
- 所有用户可见文案中文；错误只展示服务端 message 或统一网络文案。

## 6. 测试和验证

### 6.1 后端测试

- `WxUserAuthService`：登录成功（新建用户）、重复登录（同一 openid 复用）、code2Session 失败返回 `AUTH_LOGIN_FAILED`。
- `UserJwtTokenService`/过滤器：Token 过期、篡改、ADMIN Token 访问用户接口被拒（反之亦然）、无 Token 401。
- `UserAddressService`：新增成功、超 20 条 `ADDRESS_LIMIT_EXCEEDED`、默认地址互斥切换、越权访问 `ADDRESS_NOT_FOUND`。
- `CartService`：加购成功、同规格合并累加、数量越网格 `VALIDATION_ERROR`、超库存 `STOCK_INSUFFICIENT`、下架规格 `PRODUCT_NOT_AVAILABLE`、列表 `purchasable` 标记正确。
- `OrderCreateService`：成功路径（快照金额、`RESERVE` 流水、状态日志、购物车清理）；幂等重放返回同 `orderNo` 且不重复预占；库存不足整体回滚（预占与流水均无残留）；越权地址、非法数量、下架商品各失败用例。
- 库存并发（Testcontainers MySQL）：并发创建订单抢最后一件库存，仅一个成功，另一个 `STOCK_INSUFFICIENT`，`reserved_qty` 一致。
- Controller 层：鉴权 401、参数校验 `VALIDATION_ERROR`、错误码稳定；执行全量回归。

### 6.2 小程序验证

- 所有 JS 文件 Node 语法检查；`app.json` 注册与 tabBar 配置检查。
- 微信开发者工具手测：加购合并、购物车步进边界、失效项置灰、勾选合计、结算选地址、备注截断、提交防重复、下单成功回首页、断网/失败恢复按钮、未登录触发登录。

## 7. 实施顺序

1. V6 Flyway 迁移（wx_user、user_address、cart_item、sales_order 幂等列）。
2. `WeChatAuthClient` + 微信登录 + `UserJwtTokenService`/过滤器 + `GET /users/me`。
3. 地址模块（表已迁移，Service/Controller/测试）。
4. 购物车模块。
5. `MoneyCalculator`、`OrderAction.CREATE`、创建订单 Service/Controller、库存预占与幂等测试。
6. 后端全量测试。
7. 小程序 services（cart/address/order）。
8. 地址页 → 购物车页 → 结算页 → 详情页/首页接入。
9. 语法检查与手测清单验证。
10. 功能点追加 `doc/FEATURE_LIST.md`；同步 `docs/开发设计文档.md`（idempotency_key、OrderAction.CREATE）。

## 8. 冲突点和待确认信息（2026-10-28 已全部确认）

1. **下单请求口径（已确认：两者并用）**：`items` 为下单权威清单，`cartItemIds` 仅用于下单成功后同事务清理购物车；立即购买 `cartItemIds=[]`。
2. **购物车入口（已确认：新增 tabBar）**：app.json 新增 tabBar（首页、购物车）常驻底部导航。
3. **下单范围（已确认：止于创建预订单）**：本次到 CREATED 为止，成功后回首页提示“等待商家确认”；订单列表、改价确认、支付为后续任务。
4. **微信登录本地调试（已确认：Fake 登录）**：未配置 `WECHAT_APPID`/`WECHAT_SECRET` 时使用 Fake 登录（仅本地/测试）；生产配置真实密钥走真实 code2Session。密钥不入库。
5. **地址管理（已确认：纳入本次）**：最小地址管理（列表/新增/编辑/删除、上限 20、默认地址）。
6. **“立即下单”行为（已确认：立即购买）**：详情页“立即下单”直达结算页单商品购买，另加“加入购物车”；两路径共用创建订单接口。
7. **设计文档同步**：`sales_order.idempotency_key` 与 `OrderAction.CREATE` 为设计事实源未明确列出的实现细节，实施时将回写 `docs/开发设计文档.md`。
