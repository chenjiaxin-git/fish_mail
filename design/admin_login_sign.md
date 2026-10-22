# 后端管理系统登录功能设计

> 状态：已确认并开始实现  
> 功能名称：admin_login  
> 对应文档：doc/CONTEXT.md、doc/STYLE_GUIDE.md、doc/DESIGN_SUMMARY.md、GUIDE.md  
> 交付范围：管理员登录、Token 鉴权、管理端登录页面和登录后的基础路由

## 1. 任务目标

完成 PC 管理后台登录闭环：

1. 用户在管理端登录页输入用户名和密码，登录成功后进入系统。
2. 仅允许唯一的管理员账号登录。
3. 未知账号、错误密码、停用账号、伪造 Token、过期 Token 和未登录访问受保护接口都必须被拦截。
4. 本次不提供注册、找回密码、多账号、角色权限和用户端微信登录；支持当前管理员修改密码。

## 2. 当前项目状态与设计前提

- 当前目录没有 backend、admin、miniprogram 代码，只有 GUIDE.md、doc/ 和 docs/ 设计文档。
- 本次需要创建最小可运行的 backend Spring Boot 工程和 admin Vue 工程，但只实现登录所需模块，不提前实现商品、订单等业务接口。
- 后端技术栈遵循 CONTEXT.md：Java 17、Spring Boot 3.x、Spring MVC、Spring Security、MyBatis-3、MySQL 8、Flyway。
- 管理端技术栈遵循 CONTEXT.md：Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios。
- 一期单管理员账号：数据库只维护一条 admin_user 记录，不提供任何创建管理员接口。
- 管理员初始用户名和密码由部署环境变量提供，不能把真实密码写入源码、设计文档或提交记录。

## 3. 功能范围

### 3.1 后端

- 创建 admin_user 表和唯一初始管理员初始化流程。
- 提供管理员登录接口。
- 提供当前管理员信息校验接口。
- 提供当前管理员修改密码接口，校验原密码并使旧 Token 失效。
- 提供 JWT 生成、校验和 Spring Security 过滤器。
- 对 /api/v1/admin/** 下受保护资源实施统一鉴权拦截。
- 登录失败使用统一的通用错误，不暴露用户名是否存在。
- 记录管理员登录成功和失败审计日志；不记录密码。

### 3.2 管理端

- 创建 admin Vue + TypeScript + Vite 基础工程。
- 创建登录页面、表单校验、错误提示和防重复提交。
- 创建 Axios 请求封装、Token 保存和请求拦截器。
- 创建登录后基础布局和 dashboard 占位页面，证明已成功进入系统。
- 创建路由守卫，未登录访问受保护页面跳转登录页。

### 3.3 不包含

- 商品、分类、订单、轮播图、文件上传、看板真实数据。
- 用户注册、密码重置、管理员新增/删除、角色和多账号。
- 微信小程序登录、微信支付和用户 Token。
- OAuth2、Redis、记住我、刷新 Token、单点登录和第三方登录。

## 4. 账号和凭据策略

### 4.1 初始账号

新增配置：

~~~text
ADMIN_USERNAME=admin
ADMIN_INITIAL_PASSWORD=<部署时必填，禁止提交>
JWT_SECRET=<至少 32 字节的随机密钥，禁止提交>
~~~

应用启动时由 AdminBootstrapRunner 创建或校验唯一管理员：

1. 读取 admin.username 和 admin.initial-password。
2. 若 admin_user 表为空，创建 id=1 的管理员，使用 BCrypt 保存初始密码。
3. 若 admin_user 已存在，只校验用户名配置，不覆盖已有密码。
4. 若生产环境缺少任一必需配置，启动失败并输出配置项名称，不使用默认密码。
5. 初始化过程只允许创建一条管理员记录；禁止注册接口和管理员创建接口。

### 4.2 登录校验

- 用户名、密码均通过 Bean Validation 做长度和非空校验。
- 登录服务按配置的 ADMIN_USERNAME 查询唯一管理员，再使用 BCrypt matches 校验密码。
- 用户名不存在、密码错误、账号非 ACTIVE 返回同一个 AUTH_INVALID_CREDENTIALS。
- 不返回用户是否存在、密码是否正确、账号是否停用的差异信息。
- 管理员表只允许一条记录；即使数据库被手工加入其他账号，也不会作为有效管理员登录。

### 4.3 修改密码

- 只有已登录的唯一管理员可以调用修改密码接口。
- 请求字段：currentPassword、newPassword、confirmNewPassword。
- currentPassword 必须与当前 BCrypt 密码匹配。
- newPassword 长度 8 到 64 个字符，不能与 currentPassword 相同，且必须与确认密码一致。
- 修改成功后更新 password_hash、递增 token_version，写审计日志，并返回新 Token。
- 旧 Token 在 token_version 校验中失效；前端收到新 Token 后替换本地 Token。
- 修改失败不得改变密码、token_version 或签发新 Token。

## 5. 数据库设计

### 5.1 admin_user 表

~~~sql
CREATE TABLE admin_user (
    id BIGINT UNSIGNED PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    token_version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_admin_user_username (username),
    CONSTRAINT ck_admin_user_status CHECK (status IN ('ACTIVE','DISABLED'))
);
~~~

本次 Flyway 文件：

- V1__create_admin_schema.sql：创建 admin_user。
- V2__init_admin_user.sql：创建登录审计所需的基础表结构，或明确由应用日志承载登录审计。

管理员密码只存 password_hash，禁止在数据库中存明文、可逆加密或临时密码。

### 5.2 登录审计

本次最小实现使用 admin_auth_log：

~~~sql
CREATE TABLE admin_auth_log (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    action VARCHAR(20) NOT NULL,
    result VARCHAR(20) NOT NULL,
    failure_code VARCHAR(50) NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_admin_auth_log_created (created_at),
    KEY idx_admin_auth_log_result (result)
);
~~~

action 只允许 LOGIN、LOGIN_FAILED、PASSWORD_CHANGED、PASSWORD_CHANGE_FAILED、TOKEN_REJECTED；不得写入密码、Token、JWT 原文。

## 6. 后端接口设计

### 6.1 通用响应

成功：

~~~json
{
  "code": "0",
  "message": "ok",
  "data": {},
  "traceId": "trace-id"
}
~~~

登录失败：

~~~json
{
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "用户名或密码错误",
  "traceId": "trace-id"
}
~~~

### 6.2 POST /api/v1/admin/login

请求：

~~~json
{
  "username": "admin",
  "password": "至少8位密码"
}
~~~

字段校验：

- username：必填，3 到 32 个字符。
- password：必填，8 到 64 个字符。

成功响应：

~~~json
{
  "code": "0",
  "message": "ok",
  "data": {
    "accessToken": "jwt-token",
    "tokenType": "Bearer",
    "expiresIn": 28800,
    "admin": {
      "id": 1,
      "username": "admin",
      "status": "ACTIVE"
    }
  },
  "traceId": "trace-id"
}
~~~

行为：

1. Controller 只做请求校验并调用 AdminAuthService.login。
2. Service 校验用户名、BCrypt 密码和 ACTIVE 状态。
3. 成功后生成 JWT，更新 last_login_at，写 ADMIN_LOGIN 审计。
4. 失败不创建 Token，不更新最后登录时间，写 ADMIN_LOGIN_FAILED 审计。
5. HTTP 状态：成功 200；未知账号、错误密码、停用账号统一 401；参数格式错误 400。

### 6.3 GET /api/v1/admin/me

- 请求头：Authorization: Bearer <accessToken>。
- 成功返回当前管理员的 id、username、status。
- 无 Token、签名错误、过期 Token、非管理员 Token 均返回 401。
- 该接口用于前端刷新页面后恢复登录态和验证 Token。

### 6.4 POST /api/v1/admin/change-password

请求：

~~~json
{
  "currentPassword": "原密码",
  "newPassword": "新密码至少8位",
  "confirmNewPassword": "新密码至少8位"
}
~~~

行为：

1. 仅接受已登录管理员 Token，从 Token 获取 adminId，禁止传入其他用户。
2. Service 校验原密码、新密码规则和确认密码。
3. 使用 BCrypt 保存新密码，递增 admin_user.token_version。
4. 写 PASSWORD_CHANGED 审计并返回新 accessToken。
5. 原 Token 下次请求被拒绝并返回 401，前端自动切换到新 Token。

### 6.5 鉴权拦截

- 公开路径仅允许：
  - POST /api/v1/admin/login
  - GET /actuator/health
- 其他 /api/v1/admin/** 请求必须通过 AdminJwtAuthenticationFilter。
- /api/v1/admin/** 不允许使用用户 Token；Token 必须包含 claim role=ADMIN、adminId、username。
- 过滤器校验失败使用 401，不返回堆栈、Token 内容和数据库信息。
- Spring Security 的权限规则必须显式写在 SecurityConfig 中，禁止使用 permitAll 通配整个 /api/v1/admin。

## 7. JWT 设计

- 算法：HS256。
- secret：JWT_SECRET 环境变量，至少 32 字节随机值；开发环境使用本地非真实密钥。
- issuer：fishmall-admin。
- audience：fishmall-admin-web。
- token 有效期：8 小时。
- claims：adminId、username、role=ADMIN、tokenVersion、iat、exp、iss、aud。
- 前端只保存 accessToken，不保存密码和完整 JWT payload。
- 不提供刷新 Token；过期后清空登录态并跳转登录页。
- Token 解析失败、签名错误、过期、role 不是 ADMIN 都按 401 处理。
- 过滤器校验 tokenVersion 与 admin_user.token_version 一致，避免修改密码后旧 Token 继续使用。

## 8. 后端模块和文件

新增 backend 目录，建议包名 com.fishmall.admin：

~~~text
backend/src/main/java/com/fishmall/
├─ FishMallApplication.java
├─ common/
│  ├─ ApiResponse.java
│  ├─ BusinessException.java
│  ├─ ErrorCode.java
│  ├─ GlobalExceptionHandler.java
│  ├─ TraceIdFilter.java
├─ admin/
│  ├─ api/AdminAuthController.java
│  ├─ application/AdminAuthService.java
│  ├─ application/AdminBootstrapRunner.java
│  ├─ domain/AdminUser.java
│  ├─ repository/AdminUserMapper.java
│  ├─ repository/AdminAuthLogMapper.java
│  ├─ security/AdminJwtAuthenticationFilter.java
│  ├─ security/AdminJwtTokenService.java
│  ├─ security/SecurityConfig.java
│  └─ config/AdminProperties.java
~~~

Mapper 使用 XML 或注解均可，但查询和审计写入必须使用参数绑定。禁止在 Controller 中直接调用 Mapper。

## 9. 管理端模块和文件

新增 admin 目录：

~~~text
admin/src/
├─ api/http.ts
├─ api/auth.ts
├─ stores/auth.ts
├─ router/index.ts
├─ layouts/AdminLayout.vue
├─ views/LoginView.vue
├─ views/DashboardView.vue
├─ App.vue
├─ main.ts
└─ types/auth.ts
~~~

### 9.1 登录页

- 页面标题：生鲜水产管理后台。
- 表单字段：用户名、密码。
- 用户名必填 3 到 32 个字符；密码必填 8 到 64 个字符。
- 提交期间禁用按钮，防止重复登录。
- 登录成功保存 accessToken、admin 到 useAuthStore，并跳转 /dashboard。
- 登录失败显示“用户名或密码错误”，不显示后端堆栈。
- 已登录访问 /login 时跳转 /dashboard。

### 9.2 Axios 和路由

- 所有请求统一前缀 /api/v1。
- 请求拦截器附加 Authorization: Bearer。
- 响应拦截器处理 code=0；401 清空登录态并跳转 /login。
- 路由守卫：未登录访问 /dashboard 或其他受保护路由跳转 /login。
- /api/v1/admin/me 用于刷新页面后的登录态恢复；接口失败时按未登录处理。
- 本次 dashboard 只做登录成功后的占位页，不伪造业务数据。

## 10. 安全和异常处理

- 密码校验使用 BCryptPasswordEncoder；禁止明文比对、MD5、SHA-1。
- 登录接口使用通用 401，避免账号枚举。
- 修改密码成功后递增 token_version，并返回新 Token；旧 Token 立即失效。
- JWT_SECRET、ADMIN_INITIAL_PASSWORD 只能通过环境变量注入。
- 日志不得打印密码、JWT、Authorization 请求头和 password_hash。
- AdminAuthLog 记录 IP、User-Agent、traceId 和结果，敏感字段裁剪后保存。
- 登录接口应限制请求体大小；可在后续统一添加限流，本次不在文档外新增限流系统。
- 对所有请求使用 TraceIdFilter 生成或透传 traceId，并在响应中返回。

## 11. 测试方案

### 11.1 后端单元测试

- AdminAuthService：正确账号登录成功并返回 Token。
- AdminAuthService：未知用户名登录失败并返回 AUTH_INVALID_CREDENTIALS。
- AdminAuthService：错误密码登录失败且不区分账号是否存在。
- AdminAuthService：DISABLED 账号登录失败。
- AdminAuthService：修改密码原密码错误、新密码不一致、规则不合法均失败。
- AdminAuthService：修改密码成功递增 token_version 并返回新 Token。
- AdminJwtTokenService：正确 Token 可解析，role=ADMIN。
- AdminJwtTokenService：错误签名、过期 Token、role=USER Token 被拒绝。
- AdminBootstrapRunner：空表创建唯一管理员；重复启动不重复创建；缺配置启动失败。

### 11.2 API 集成测试

- POST /api/v1/admin/login 使用正确账号返回 200、accessToken 和 admin。
- 使用未知账号、错误密码、停用账号均返回 401 和相同错误码。
- 无 Token 访问 GET /api/v1/admin/me 返回 401。
- 伪造 Token、过期 Token、用户 Token 访问管理接口返回 401。
- 仅 /api/v1/admin/login 和 health 可匿名访问，其他管理接口匿名访问返回 401。
- 登录审计表记录成功、失败、traceId 和请求 IP，但不包含密码或 Token。
- POST /api/v1/admin/change-password 使用正确原密码成功后旧 Token 访问返回 401。
- 修改密码接口未登录、伪造 Token、用户 Token 访问返回 401。

### 11.3 前端验收

- 正确账号登录后进入 /dashboard。
- 修改密码成功后使用新 Token 继续访问，旧 Token 请求被拦截。
- 未知账号、错误密码显示统一错误文案，页面停留在登录页。
- 空用户名、短密码、空密码触发表单校验。
- 提交时按钮禁用，失败后恢复可用。
- 手工清除 Token 后访问 /dashboard 被重定向到 /login。
- 刷新页面后可通过 /api/v1/admin/me 恢复登录态。

## 12. 实施顺序

1. 创建 backend Maven 工程、配置、ApiResponse、异常和 TraceIdFilter。
2. 创建 Flyway V1/V2、AdminUserMapper、AdminAuthLogMapper。
3. 创建 AdminBootstrapRunner、BCrypt 密码初始化和配置校验。
4. 创建 AdminAuthService、AdminJwtTokenService、SecurityConfig 和登录过滤器。
5. 创建 AdminAuthController，并完成后端测试。
6. 创建 admin Vue + TS + Vite 工程、登录页、Axios、Pinia 和路由守卫。
7. 完成前端构建和登录流程手测。
8. 将本次功能点追加到 doc/FEATURE_LIST.md。

## 13. 完成定义

- 后端可编译，登录和鉴权测试通过。
- 管理端可从前端页面使用唯一管理员账号登录。
- 其他账号、错误密码、停用账号和无 Token 请求均被拦截。
- 不提供注册、创建管理员或绕过登录的接口。
- 密码只存 BCrypt，密钥不入仓库，日志不泄露密码和 Token。
- FEATURE_LIST.md 已追加本功能记录。

## 14. 待确认信息

以下信息当前文档未给出，代码开发前需要确认：

1. 已按默认管理员用户名 admin 实现；如需其他用户名可通过 ADMIN_USERNAME 配置。
2. 已按环境变量 ADMIN_INITIAL_PASSWORD 提供初始密码；代码不保存默认密码。
3. 已按创建 backend/admin 两个新工程实现。
4. 已按一期仅 8 小时有效、过期重新登录处理，不实现刷新 Token。
5. 已实现修改密码；修改成功返回新 Token，旧 Token 失效。

## 15. 冲突点说明

- 产品文档曾描述“管理员账号登录”，但没有定义账号初始化方式、Token 类型、过期策略和登录失败细节，本设计补充了环境变量、BCrypt、JWT 和统一错误策略。
- DESIGN_SUMMARY.md 将管理员登录列为一期能力，但没有要求注册、修改密码或刷新 Token；本次严格只做登录和拦截。

