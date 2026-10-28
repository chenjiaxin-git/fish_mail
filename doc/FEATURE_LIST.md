## 2026-10-22

### admin_login_sign

- 新增 Spring Boot 管理端 API 工程骨架、Flyway 管理员表和登录审计表。
- 实现唯一管理员登录：只接受 ADMIN_USERNAME 配置账号，其他账号、错误密码、停用账号统一返回 AUTH_INVALID_CREDENTIALS。
- 实现 JWT HS256 Token、token_version 校验、/api/v1/admin/** 鉴权拦截和 /api/v1/admin/me。
- 实现修改密码接口：校验当前密码、新密码确认和长度，成功后递增 token_version、返回新 Token，旧 Token 立即失效。
- 新增管理端登录页、Axios Token 拦截、登录后布局、修改密码弹窗和路由守卫。
- 新增 AdminAuthService、AdminJwtTokenService 单元测试，覆盖越权账号、错误密码、停用账号、Token 篡改和密码修改。

### product_module_sign

- 新增 V3 商品模块迁移：商品分类、商品、商品规格、库存流水和管理操作审计表。
- 实现商品分类查询、新增、编辑和软删除；分类存在有效商品时返回 CATEGORY_HAS_PRODUCTS。
- 实现商品列表分页/筛选、详情、创建、编辑和软删除；商品删除前校验库存预占。
- 实现商品规格创建和维护；已有预占规格禁止删除，且禁止通过商品编辑修改计价属性和物理库存。
- 实现管理员手工库存调整：条件更新物理库存，写入 INITIAL/ADMIN_ADJUST 库存流水，返回并展示最近流水，检测 STOCK_VERSION_CONFLICT。
- 实现本地商品图片上传：校验 jpg/jpeg/png/webp 扩展名、MIME、文件魔数和 5 MB 上限，使用 UUID 文件名并提供本地静态读取 URL。
- 新增管理端分类页面、商品页面、规格编辑表格、图片上传控件、库存调整弹窗和商品/分类 API 封装。
- 新增分类、商品、库存和图片校验单元测试，后端测试共 15 个通过；前端 TypeScript/Vite 构建通过。

## 2026-10-23

### order_module_sign

- 新增 V4 订单模块迁移：订单、订单项、改价记录、支付记录、订单状态日志和消息日志，并为商品库存流水补充订单追踪字段。
- 实现管理端订单列表分页与状态、日期、订单号、收货手机号筛选，订单详情返回商品快照、改价记录、支付记录和状态日志。
- 实现商家称重改价：校验完整订单项、件数数量、订单项金额合计和 `quoteVersion` 乐观锁，生成递增改价记录并锁定支付截止时间。
- 实现订单统一状态机和管理端标记配送、标记完成、商家取消操作；重复状态请求不重复写状态流水。
- 实现未支付取消释放库存预占；已支付取消仅标记人工退款待登记，退款确认后按实称数量回补实物库存并写 `REFUND_RELEASE` 流水。
- 实现订单 Excel 导出：Apache POI SXSSF 生成 `.xlsx`，固定白名单列，日期范围按闭区间最大 31 天校验。
- 新增订单状态机、改价版本冲突、金额校验、取消库存释放、人工退款回补和导出范围回归测试；后端测试共 26 个通过。

### miniprogram_home_sign

- 新建微信原生小程序工程骨架，包含 app 全局配置、页面注册、sitemap、本地调试 API 配置和项目配置。
- 新增统一 wx.request 封装，按 `{code, message, data, traceId}` 解析响应，支持可选 `Authorization: Bearer <token>`、超时和统一中文网络错误。
- 新增微信登录 Token 管理服务，封装微信登录、Token 保存、读取、清理和按需登录，首页公开接口 Token 为空时不强制登录。
- 新增首页目录 API 封装，接入 `/api/v1/banners`、`/api/v1/categories` 和 `/api/v1/products`，支持关键词、分类和分页参数。
- 新增金额分转元、数量三位小数格式化和日期格式化工具，首页价格和库存统一走格式化函数。
- 完成首页轮播图、分类横向筛选、搜索框、商品分页列表、下拉刷新和触底加载；搜索词限制 100 字符并截断提示，分页固定 20 条。
- 完成加载中、空态、加载失败、图片加载失败、网络错误和重试状态，使用加载锁防止重复请求。
- 完成轮播图跳转约定：分类切换首页筛选，商品提示“商品详情暂未开放”，未知类型不跳转。

## 2026-10-26

### miniprogram_home_sign backend APIs

- 新增 `shop_banner` Flyway 迁移、轮播图领域模型和启用轮播图查询，公开接口仅返回 ACTIVE 且未删除记录。
- 新增小程序公开目录接口 `GET /api/v1/banners`、`GET /api/v1/categories`、`GET /api/v1/products`，统一返回 `ApiResponse`，商品列表返回 `total/page/pageSize/items`。
- 公开商品接口支持关键词、分类和分页筛选，批量加载启用规格并返回价格区间、可用库存和安全规格字段，不暴露物理库存、预占库存等后台字段。
- 修正小程序轮播图字段兼容，支持标准 `jumpType/jumpTargetId`，并补充公开目录 Service/Controller 回归测试和参数类型错误处理。

## 2026-10-27

### miniprogram_product_detail_sign

- 新增公开商品详情接口 `GET /api/v1/products/{id}`，返回简介、启用规格、单价区间和实时可用库存（`physical_stock_qty - reserved_qty`），下架与软删除商品统一返回 `PRODUCT_NOT_FOUND`。
- 公开详情响应复用安全规格字段，不暴露物理库存、预占库存和预警量；无启用规格时返回空 `specs` 与空价格区间。
- 新增小程序商品详情页：主图、名称、副标题、分类、简介、规格列表（计价方式、单位、单价、实时可用库存）和售罄标记。
- 详情页支持规格选择与数量步进，数量按 `minQuantity`/`stepQuantity` 约束，PIECE 取整、WEIGHT 三位小数，越界回退并提示。
- 落实库存为 0 禁止下单：所选规格无库存或不足起购量时禁用“立即下单”并显示中文提示；有库存时点击提示“下单功能暂未开放”（下单为后续任务）。
- 实时可用库存通过进入页面、返回页面静默刷新和下拉刷新重新查询实现，刷新失败保留旧数据并提示。
- 首页商品卡片和轮播图 `PRODUCT` 跳转接入商品详情页；小程序统一使用 `resolveImageUrl` 补全商品与轮播图相对图片 URL。
- 新增公开详情 Service/Controller 回归测试（成功、不存在、下架、非法 id、空规格），后端测试共 39 个通过；小程序 JS 语法与格式化/数量边界校验通过。

## 2026-10-28

### miniprogram_cart_order_sign

- 新增 V6 用户域迁移：`wx_user`、`user_address`、`cart_item` 三表，`sales_order` 补充 `idempotency_key` 幂等列（`(wx_user_id, idempotency_key)` 唯一，手工单为 NULL）。
- 实现微信登录 `POST /api/v1/auth/wechat`：`WeChatAuthClient` Port 提供真实 code2Session 与 Fake 双实现（未配置 `WECHAT_APPID/SECRET` 时 Fake 仅限本地），按 openid upsert `wx_user`，签发 7 天用户 JWT，与管理员 Token 按 `tokenType` 互相隔离。
- 实现用户鉴权过滤器保护 `/api/v1/addresses/**`、`/api/v1/cart/**`、`/api/v1/orders/**`、`/api/v1/users/**`，`wxUserId` 只从 Token 获取；新增 `GET /api/v1/users/me` 供探测登录态。
- 实现地址管理 API：列表（默认在前）、新增、编辑、软删除，每用户上限 20 条（`ADDRESS_LIMIT_EXCEEDED`），`isDefault` 同事务互斥，越权统一 `ADDRESS_NOT_FOUND`。
- 实现购物车 API：列表实时回填价格/库存/可购状态（含 `minQuantity`/`stepQuantity` 步进约束），加购同规格合并累加，修改数量、删除；数量走 `QuantityRules` 网格校验，超库存 `STOCK_INSUFFICIENT`，下架 `PRODUCT_NOT_AVAILABLE`。
- 实现创建预订单 `POST /api/v1/orders`：单事务校验地址归属、商品可售、数量网格，条件更新预占 `reserved_qty` 并写 `RESERVE` 流水（`biz_key` 唯一兜底），订单项与地址快照落库，写 CREATED 状态日志，同事务按 `cartItemIds` 清理购物车；`(wx_user_id, idempotency_key)` 幂等重放返回首次订单；事务隔离级别 READ_COMMITTED，消除幂等查询间隙锁并发插入死锁。
- 新增 `MoneyCalculator` 金额乘法/合计统一入口与 `OrderAction.CREATE` 状态机入口动作；订单号 `FM+yyyyMMdd+6 位序列`，唯一冲突自动重试。
- 新增登录、Token 隔离、地址、购物车、下单成功/失败/幂等重放/库存并发抢购（Testcontainers MySQL）回归测试，后端测试共 75 个通过。
- 小程序新增 `services/cart.js`、`services/address.js`、`services/order.js`，新增 tabBar（首页、购物车）常驻底部导航。
- 新增购物车页：勾选/数量步进（与详情页同网格约束）/删除/全选/合计/去结算，失效条目置灰不可勾选并显示中文原因，空态、失败重试与防重复请求。
- 新增结算页：地址选择（默认优先）、只读商品清单、“预估金额以商家称重为准”提示、备注 500 字计数截断、进入页面生成 uuid 幂等键防重复提交，成功 toast“下单成功，等待商家确认”后回首页；`STOCK_INSUFFICIENT`/`PRODUCT_NOT_AVAILABLE`/`ADDRESS_NOT_FOUND` 中文提示并刷新对应区域。
- 新增地址管理页（列表/新增/编辑/删除、上限 20 置灰提示、默认地址开关、删除确认、结算页选择模式回填）。
- 商品详情页底部栏改为“加入购物车”+“立即下单”（立即购买直达结算页、`cartItemIds=[]`），售罄/不足起购量统一禁用；首页新增购物车入口；未登录操作先 `ensureLogin()`。
- 小程序全部 JS 通过 Node 语法检查、JSON 配置校验；微信开发者工具手测清单覆盖加购合并、步进边界、失效置灰、勾选合计、选地址、备注截断、防重复提交与失败恢复。
