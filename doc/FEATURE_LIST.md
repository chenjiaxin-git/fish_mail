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

