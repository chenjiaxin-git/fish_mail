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

