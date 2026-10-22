# STYLE_GUIDE.md

## 1. 通用原则

- 正确性优先于抽象复用；订单、金额、库存、支付相关代码必须保守、显式、可测试。
- 业务规则放在 Service/Domain 层；Controller、页面、Mapper 不承载状态机和金额规则。
- 新增代码必须与现有目录、命名、响应格式和错误码保持一致。
- 不引入项目未约定的框架、中间件和外部服务；需要新增依赖时先说明用途、成本和回退方案。
- 代码、注释和提交信息使用中文或清晰英文均可，但用户可见文案必须是中文。
- 不提交密钥、真实商户号、生产数据库密码、上传图片原件和临时测试数据。

## 2. 命名和文件规范

### Java

- 包名：com.fishmall.<module>，例如 com.fishmall.order、com.fishmall.stock。
- 类名使用 UpperCamelCase；Service、Controller、Mapper、Request、Response 使用明确后缀。
- 方法名使用 lowerCamelCase；布尔变量使用 is/has/can 前缀。
- 常量使用 UPPER_SNAKE_CASE；枚举值使用英文大写，如 PENDING_PAYMENT。
- DTO 不直接暴露数据库实体；数据库字段 snake_case，Java 字段 camelCase。
- 一个 Service 方法只表达一个业务动作；事务边界方法不得从 Controller 或 Mapper 直接复用。

### Vue 和小程序

- Vue 组件文件使用 PascalCase，例如 OrderDetail.vue；目录按业务模块组织。
- Pinia store 使用 useXxxStore；API 函数使用 getXxx/createXxx/updateXxx。
- 请求类型以 Request 结尾，响应类型以 Response 或 Vo 结尾；金额类型显式使用 amountCent。
- 微信小程序页面文件使用小写短横线或连续小写单词，页面配置必须显式声明标题。
- 日期、金额、重量的格式化必须集中封装，禁止在页面中散落字符串拼接。

## 3. 后端代码规范

### 分层职责

~~~text
Controller -> Application Service -> Domain/Repository -> Mapper -> MySQL
                         |
                         +-> Port -> WeChatPayClient / ObjectStorageClient / MessageClient
~~~

- Controller：参数绑定、Bean Validation、鉴权上下文、响应包装。
- Application Service：事务、业务编排、幂等检查、状态机调用。
- Domain：状态转换、金额和数量规则；不依赖 Controller 和 HTTP。
- Mapper：SQL 和映射，不包含订单状态决策。
- Port/Adapter：封装微信、对象存储和通知，测试使用 Fake 实现。

### Spring 约束

- 构造器注入，禁止字段注入；服务类优先使用 final 字段。
- 事务注解只放在 Application Service 的公开方法；禁止事务方法内部自调用导致注解失效。
- 捕获异常时保留 traceId 和业务上下文，禁止吞掉异常后返回成功。
- 外部调用设置连接和读取超时；重试只用于幂等操作，并记录次数。
- Controller 返回统一 ApiResponse；业务错误使用 BusinessException 和稳定错误码。

### 金额、数量和时间

- 金额字段命名为 xxxAmountCent，类型 long 或 Long；禁止 BigDecimal 直接承载人民币元。
- 重量和数量字段命名为 quantity、actualQuantity，类型 BigDecimal，scale 为 3。
- 时间字段命名为 xxxAt，使用 Instant 或项目统一 LocalDateTime + Asia/Shanghai 约定。
- 任何乘法、合计和改价金额都通过 MoneyCalculator；禁止直接用 double、float 或四舍五入散落逻辑。
- 展示元金额时由前端 money.ts 转换，服务端始终存储和传输整数分。

### 订单、库存和支付

- 订单状态切换只能调用 OrderStateMachine，输入当前状态、动作和操作者。
- 每一次状态转换写 order_status_log；重复动作不能重复写流水。
- 库存操作必须使用条件 UPDATE，受影响行数为 0 时抛出 STOCK_INSUFFICIENT。
- 支付回调先验签再解析，先幂等后业务；不在 Controller 中扣库存。
- out_trade_no、transaction_id、stock_ledger.biz_key 必须有数据库唯一索引。
- 已支付订单的金额字段禁止被改价接口更新；人工退款只写 refund_status 和 refund_reference。

### MyBatis 和 SQL

- 所有用户输入使用参数绑定，禁止拼接 SQL；排序字段和导出列使用白名单。
- Flyway 脚本命名 V1__create_schema.sql、V2__init_admin.sql；已发布脚本只新增 V3+。
- 唯一键、外键和非空约束优先在数据库层保证，Service 仍需返回业务错误。
- 查询列表统一返回分页结构；禁止无条件 SELECT 全表。
- 时间索引、状态索引和用户索引按照 DESIGN_SUMMARY.md 的数据模型建立。

## 4. API 和错误规范

- 基础路径为 /api/v1；字段使用 camelCase；枚举值使用大写英文。
- 成功响应固定为 code=0、message=ok、data、traceId；错误响应不泄露 SQL、堆栈、Token。
- 错误码使用设计文档中的稳定值，例如 ORDER_STATE_INVALID、QUOTE_VERSION_CONFLICT。
- 创建订单、改价、支付、退款登记接口要求幂等键或版本号。
- 分页 page 从 1 开始，pageSize 最大 100；导出日期范围最大 31 天。
- 用户接口必须从 Token 获取 wxUserId，不能相信请求体中的 userId。
- 管理接口必须记录 operation_log；订单操作需要记录操作者和原因。

## 5. 安全规范

- 密码只存 BCrypt hash；禁止明文、MD5、可逆加密和日志打印密码。
- JWT 使用环境变量密钥；用户 Token 7 天，管理员 Token 8 小时；过期后重新登录。
- 微信支付证书、私钥、APIv3 key 只能从环境变量或密钥文件挂载读取。
- 图片上传校验扩展名、MIME、文件魔数和大小；文件名使用 UUID，不接受用户路径。
- 微信回调必须验证平台证书、签名、商户号、币种和金额。
- 日志不得记录完整手机号、openid、支付密钥、Token 和支付回调原文中的敏感字段。
- 管理后台必须启用 HTTPS、CORS 白名单、请求体限制和 401/403 处理。

## 6. 前端规范

### Vue 3 管理后台

- 页面只负责展示和交互；请求在 src/api，业务状态在 Pinia，纯函数在 utils。
- Axios 拦截器统一附加 Token、traceId、401 跳转和错误提示。
- Element Plus 表单必须有字段级校验；服务端校验失败要定位到具体字段。
- 按钮显示和禁用必须依据订单状态；不要只依赖前端隐藏按钮。
- 金额显示为元，提交使用 amountCent；重量显示保留三位小数并去除无意义 0。
- 大列表使用后端分页；导出由后端生成 Excel，不要在浏览器拼接 CSV。

### 微信小程序

- services 目录封装 request、登录和支付；页面不直接拼 URL。
- 点击支付、提交订单和保存地址必须防重复提交，请求失败恢复按钮状态。
- 支付结果仅用于刷新页面，等待后端订单状态确认后再展示成功。
- 图片加载失败显示占位；无库存、无地址、网络失败都有明确中文状态。
- 收货备注、地址和搜索词做长度限制；禁止把异常堆栈显示给用户。

## 7. 测试规范

- 每个 Service 业务方法至少有一个成功测试和一个失败测试。
- 订单状态、金额计算、数量校验、库存条件更新、支付幂等必须有单元或集成测试。
- MySQL 相关测试使用 Testcontainers；不要用与生产语义不同的内存数据库替代核心并发测试。
- 微信支付客户端使用 Fake/Stub，覆盖验签失败、金额不匹配、重复通知和乱序通知。
- API 测试覆盖鉴权、越权、参数校验、错误码和分页上限。
- 修改订单、支付或库存代码时，必须补充或更新回归测试后才能完成任务。

## 8. 日志和可观测性

- 关键日志字段：traceId、orderNo、out_trade_no、操作者、动作、结果。
- INFO 记录业务开始和完成；WARN 记录可恢复异常和重试；ERROR 记录需要排查的失败。
- 不使用 System.out；使用 SLF4J/Logback。
- /actuator/health 用于健康检查；不在公网暴露配置、环境变量和完整映射。
- 支付回调、库存流水、状态日志、通知日志不得从管理界面删除。

## 9. 提交和协作约定

- 一个提交只完成一个可验证行为，提交信息格式：<type>: <中文或英文简述>。
- type 可用 feat、fix、test、docs、refactor、chore；禁止一次提交混合重构和新功能。
- 生成的 Word 文档只由 docs/开发设计文档.md 转换，不手工编辑二进制文件。
- 修改设计时先更新 Markdown，再同步 DESIGN_SUMMARY.md、CONTEXT.md，最后重新生成 Word。
- 评审重点：状态机、金额精度、库存并发、支付幂等、权限边界、测试缺口。

## 10. 任务完成清单

- [ ] 阅读 CONTEXT.md 和 DESIGN_SUMMARY.md，确认任务未越过一期边界。
- [ ] 修改接口、数据模型或状态机时同步更新 docs/开发设计文档.md。
- [ ] 新增代码符合命名、分层、金额、数量、安全和日志规范。
- [ ] 新增接口有服务端校验、稳定错误码、鉴权和必要审计。
- [ ] 订单/库存/支付改动包含自动化回归测试。
- [ ] 后端、前端、数据库迁移和文档均可在干净环境验证。
- [ ] 未提交密钥、临时文件、生产配置和不相关内容。

