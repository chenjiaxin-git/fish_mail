# 后台管理系统商品模块设计

> 状态：待确认  
> 功能名称：product_module  
> 依赖：admin_login_sign.md、doc/CONTEXT.md、doc/STYLE_GUIDE.md、doc/DESIGN_SUMMARY.md  
> 本次目标：后台管理系统商品和分类的增删改查；商品图片源文件暂存本地

## 1. 任务范围

### 1.1 包含

- 商品分类管理：查询、新增、编辑、删除。
- 商品管理：列表查询、新增、编辑、详情、软删除。
- 商品规格管理：编辑商品时维护规格名称、计价方式、单位、单价、最小购买量、步进值、库存、预警量和上下架状态。
- 商品库存调整：修改规格库存，记录库存变更流水。
- 商品图片：上传到本地目录，返回可访问 URL；商品保存相对 URL。
- 管理端页面：分类页面、商品列表页面、商品新增/编辑弹窗、图片上传和规格编辑表格。
- 后端 API、Flyway 数据库迁移、基础自动化测试。

### 1.2 不包含

- 用户端小程序商品浏览和搜索接口。
- 购物车、订单、支付、库存预占和订单快照。
- 对象存储、CDN、图片裁剪、缩略图生成和图片水印。
- 商品多图、视频、品牌、标签、SKU 组合和营销活动。
- 多门店、多角色、批量导入导出、商品审核。

商品和分类是后续用户端商品浏览、购物车和下单的基础数据模块；订单、库存预占和支付仍由后续任务实现。

## 2. 现状与接入点

- 当前已有 backend Spring Boot 工程、MyBatis、Flyway、JWT 管理员鉴权和 ApiResponse。
- 当前仅有 V1/V2 管理员迁移，本次新增 V3 商品结构迁移。
- 当前 admin Vue 管理端已有登录、路由守卫、Axios Token 拦截和登录后布局。
- 本次在 admin 中新增 /categories、/products 页面，使用现有 useAuthStore 和 http 模块。
- 管理接口统一走 /api/v1/admin/**，由现有 AdminJwtAuthenticationFilter 拦截。

## 3. 数据模型

### 3.1 product_category

字段：

- id BIGINT 主键。
- name VARCHAR(50) 必填，唯一。
- sort INT 默认 0，数值越大越靠前。
- status VARCHAR(20)：ACTIVE、DISABLED。
- deleted TINYINT(1)：0 未删除、1 已删除。
- created_at、updated_at。

约束和索引：

- uk_product_category_name(name)。
- 查询索引 (deleted, status, sort)。
- 删除分类前必须确认不存在有效商品，否则返回 CATEGORY_HAS_PRODUCTS。

### 3.2 product

字段：

- id BIGINT 主键。
- category_id BIGINT 必填，关联有效分类。
- name VARCHAR(100) 必填。
- subtitle VARCHAR(150) 可空。
- description TEXT 可空。
- main_image_url VARCHAR(500) 可空，保存相对 URL。
- status VARCHAR(20)：ACTIVE、OFF_SHELF。
- deleted TINYINT(1)。
- created_at、updated_at。

约束和索引：

- idx_product_category (category_id, deleted, status)。
- idx_product_name (deleted, name)。
- 商品删除为软删除；历史订单快照不依赖当前商品记录。
- 有 reserved_qty > 0 的规格时禁止删除商品，返回 PRODUCT_HAS_RESERVATION。

### 3.3 product_spec

字段：

- id BIGINT 主键。
- product_id BIGINT 必填。
- name VARCHAR(50) 必填，例如“整条”“按斤”。
- quantity_kind VARCHAR(20)：PIECE、WEIGHT。
- unit VARCHAR(20) 必填，例如“条”“kg”。
- price_cent BIGINT 必填，单位分，必须大于 0。
- min_quantity DECIMAL(12,3) 默认 1，必须大于 0。
- step_quantity DECIMAL(12,3) 默认 1，必须大于 0。
- physical_stock_qty DECIMAL(12,3) 默认 0，不能小于 0。
- reserved_qty DECIMAL(12,3) 默认 0，不能小于 physical_stock_qty 的历史预占约束。
- warning_qty DECIMAL(12,3) 默认 0，不能小于 0。
- status VARCHAR(20)：ACTIVE、DISABLED。
- deleted TINYINT(1)。
- created_at、updated_at。

约束和索引：

- uk_product_spec_name(product_id, name)。
- idx_product_spec_product(product_id, deleted, status)。
- 可用库存字段由查询计算：available_qty = physical_stock_qty - reserved_qty。
- PIECE 的数量必须为正整数；WEIGHT 数量允许三位小数。
- 价格和库存由服务端校验，禁止前端传入 available_qty 或 reserved_qty。

### 3.4 product_stock_ledger

本次仅支持后台手工调整库存，后续订单模块复用同一张表：

- id BIGINT 主键。
- product_spec_id BIGINT 必填。
- change_type VARCHAR(30)：INITIAL、ADMIN_ADJUST、RESERVE、PAY_DEDUCT、RELEASE、REFUND_RELEASE。
- quantity DECIMAL(12,3) 必填，正数表示增加，负数表示减少。
- physical_before、physical_after、reserved_before、reserved_after。
- reason VARCHAR(200) 必填。
- operator_type、operator_id、biz_key、created_at。
- biz_key UNIQUE，手工调整使用 ADMIN_ADJUST:{specId}:{uuid}。

索引：idx_product_stock_ledger_spec(product_spec_id, created_at)。

## 4. 本地图片存储

### 4.1 配置

新增配置：

~~~text
PRODUCT_IMAGE_DIR=./data/product-images
PRODUCT_IMAGE_BASE_URL=/uploads/products
PRODUCT_IMAGE_MAX_BYTES=5242880
~~~

生产环境可通过环境变量覆盖目录；真实图片不提交仓库。

### 4.2 上传规则

- 接口只允许管理员 Token 访问。
- multipart 字段名固定为 file。
- 只接受 jpg、jpeg、png、webp，最大 5 MB。
- 服务端同时校验扩展名、Content-Type 和文件魔数，拒绝伪装文件。
- 文件名使用 UUID + 白名单扩展名，禁止使用客户端文件名和路径。
- 文件写入 PRODUCT_IMAGE_DIR，成功后返回相对 URL，例如 /uploads/products/550e8400-e29b-41d4-a716-446655440000.jpg。
- 商品表只保存相对 URL，避免环境迁移后域名失效。
- 本地文件读取通过 Spring 静态资源映射 /uploads/products/**，仅开放读取，不开放目录遍历。

### 4.3 删除策略

- 商品软删除时不立即删除图片文件，保留历史和恢复可能。
- 图片文件清理不纳入本次任务，后续可增加安全的孤儿文件清理任务。

## 5. 后端 API 设计

所有接口遵循 ApiResponse，错误码保持稳定。管理员鉴权由现有 SecurityConfig 处理。

### 5.1 分类接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /api/v1/admin/categories | 分页/全部查询，支持 status |
| POST | /api/v1/admin/categories | 新增分类 |
| PUT | /api/v1/admin/categories/{id} | 编辑分类 |
| DELETE | /api/v1/admin/categories/{id} | 软删除分类 |

新增/编辑请求字段：name、sort、status。name 必填 1 到 50 字符，sort 为 0 到 9999。

### 5.2 商品接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /api/v1/admin/products | 列表查询 |
| GET | /api/v1/admin/products/{id} | 商品和规格详情 |
| POST | /api/v1/admin/products | 新增商品和规格 |
| PUT | /api/v1/admin/products/{id} | 编辑商品和规格 |
| DELETE | /api/v1/admin/products/{id} | 软删除商品 |
| POST | /api/v1/admin/products/{id}/specs/{specId}/stock | 手工调整库存 |

列表查询参数：

- page、pageSize，pageSize 最大 100。
- keyword：匹配商品名称，最大 100 字符。
- categoryId、status。

创建/编辑商品请求：

~~~json
{
  "categoryId": 1,
  "name": "草鱼",
  "subtitle": "鲜活淡水鱼",
  "description": "适合清蒸、红烧",
  "mainImageUrl": "/uploads/products/uuid.jpg",
  "status": "ACTIVE",
  "specs": [
    {
      "id": null,
      "name": "按斤",
      "quantityKind": "WEIGHT",
      "unit": "kg",
      "priceCent": 3000,
      "minQuantity": 1.000,
      "stepQuantity": 0.500,
      "physicalStockQty": 100.000,
      "warningQty": 10.000,
      "status": "ACTIVE"
    }
  ]
}
~~~

规则：

- specs 至少 1 条，最多 20 条。
- 创建时 id 必须为空；编辑时 id 为空表示新增规格，id 存在表示更新已有规格。
- 已有 reserved_qty 的规格不能删除、不能修改 quantity_kind、unit、min_quantity、step_quantity，避免影响既有订单。
- 更新规格库存不得通过创建/编辑接口覆盖 reserved_qty；手工库存调整必须调用 stock 接口。
- 删除商品或分类返回稳定错误码，不返回数据库异常。

### 5.3 图片上传

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/v1/admin/files | multipart file 上传商品图片 |

响应：

~~~json
{
  "code": "0",
  "message": "ok",
  "data": {
    "url": "/uploads/products/uuid.jpg",
    "fileName": "uuid.jpg",
    "sizeBytes": 102400
  }
}
~~~

## 6. 管理端页面

### 6.1 路由

- /categories：分类管理。
- /products：商品管理。

### 6.2 分类页面

- 表格列：名称、排序、状态、创建时间、操作。
- 支持新增、编辑、删除、状态筛选。
- 删除失败时展示 CATEGORY_HAS_PRODUCTS 对应中文提示。

### 6.3 商品页面

- 搜索栏：商品名称、分类、状态。
- 表格列：主图、名称、分类、规格摘要、价格范围、库存摘要、状态、更新时间、操作。
- 新增/编辑使用抽屉或弹窗，字段包含商品基础信息和规格表格。
- 规格表格支持新增/删除规格行；删除已有规格前提示服务端约束。
- 图片上传控件上传成功后保存相对 URL，展示本地预览。
- 库存调整单独操作，必须填写调整原因，提交后刷新可用库存和流水。
- 删除商品需要二次确认，并处理“有库存预占不能删除”的错误。

### 6.4 前端类型

admin/src/types/product.ts 定义 Category、Product、ProductSpec、ProductCreateRequest、ProductUpdateRequest、StockAdjustRequest。金额字段使用 priceCent，数量字段使用 number/Decimal 字符串但由后端最终校验。

## 7. 后端模块结构

建议包名 com.fishmall.catalog，保持 Controller、Application Service、Repository、Domain 分层：

~~~text
backend/src/main/java/com/fishmall/catalog/
├─ api/controller/AdminCategoryController.java
├─ api/controller/AdminProductController.java
├─ api/controller/AdminFileController.java
├─ api/param/...
├─ api/vo/...
├─ application/service/...
├─ domain/...
├─ repository/...
└─ infrastructure/LocalFileStorageService.java
~~~

复用现有 ApiResponse、BusinessException、GlobalExceptionHandler、AdminPrincipal、AdminJwtAuthenticationFilter。

## 8. 事务和并发

- 分类删除和商品删除在同一 Service 事务中完成检查和软删除。
- 商品创建、编辑和规格更新在事务中完成，规格删除采用软删除。
- 库存调整使用条件更新：

~~~sql
UPDATE product_spec
SET physical_stock_qty = #{targetQuantity},
    updated_at = CURRENT_TIMESTAMP
WHERE id = #{specId}
  AND deleted = 0
  AND physical_stock_qty = #{beforeQuantity};
~~~

- 受影响行数为 0 时返回 STOCK_VERSION_CONFLICT，不覆盖并发调整。
- 每次库存调整写 product_stock_ledger，写流水和更新库存放在同一事务。
- 文件写入成功但商品保存失败时允许产生孤儿文件，不回滚数据库之外的文件写入；文件清理不在本次范围。

## 9. 错误码

- PRODUCT_NOT_FOUND
- PRODUCT_HAS_RESERVATION
- PRODUCT_SPEC_NOT_FOUND
- PRODUCT_SPEC_IN_USE
- CATEGORY_NOT_FOUND
- CATEGORY_HAS_PRODUCTS
- CATEGORY_NAME_DUPLICATE
- FILE_TYPE_INVALID
- FILE_TOO_LARGE
- FILE_WRITE_FAILED
- STOCK_VERSION_CONFLICT
- VALIDATION_ERROR

## 10. 测试方案

### 10.1 后端单元测试

- 分类名称重复、分类删除存在商品时失败。
- 商品创建成功、至少一个规格、规格字段非法时失败。
- 商品编辑软删除/新增规格、已预占规格不可删除。
- 商品软删除和有 reserved_qty 时删除被拒绝。
- 库存调整成功、并发版本冲突、流水记录正确。
- 图片扩展名、MIME、魔数和 5 MB 上限校验。

### 10.2 API 测试

- 未登录访问商品/分类/上传接口返回 401。
- 商品列表分页、关键词、分类和状态筛选。
- 创建、详情、编辑、删除商品的响应结构和错误码。
- 上传图片后 URL 可访问，非法文件被拒绝。

### 10.3 前端验收

- 登录后可进入分类和商品页面。
- 可新增、编辑、删除分类。
- 可新增、编辑、删除商品，维护多规格。
- 图片上传后在表单和商品列表正确显示。
- 删除有预占库存商品显示明确错误，不丢失表单数据。
- 列表搜索、分页、状态筛选和金额/数量展示正确。

## 11. 实施顺序

1. 新增 V3__create_product_schema.sql。
2. 实现 catalog domain、Mapper 和 Service。
3. 实现本地文件存储和上传接口。
4. 实现分类、商品、规格和库存 API。
5. 新增管理端商品/分类 API 封装、类型和页面。
6. 补后端单元/API 测试。
7. 执行后端测试和前端构建。
8. 将功能点追加到 doc/FEATURE_LIST.md。

## 12. 需确认信息

1. 商品“删除”采用软删除且历史订单快照保留，是否接受？
2. 有库存预占的商品禁止删除，是否接受？
3. 商品图片暂存路径使用 ./data/product-images，生产环境通过 PRODUCT_IMAGE_DIR 覆盖，是否接受？
4. 商品模块是否同时包含分类管理页面和接口？本设计按商品依赖分类纳入本次范围。

## 13. 冲突点

- 设计文档中商品图片原定对象存储；本次按你的要求暂存本地，已抽象为 FileStorage 接口，后续可替换为 COS/OSS。
- 设计文档未定义商品删除与订单引用的具体策略，本设计补充为软删除并禁止删除仍有库存预占的商品。

