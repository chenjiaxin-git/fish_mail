# 微信小程序商品详情页设计

> 状态：按本次任务执行
> 功能名称：miniprogram_product_detail
> 依赖：doc/CONTEXT.md、doc/STYLE_GUIDE.md、doc/DESIGN_SUMMARY.md、docs/开发设计文档.md、design/miniprogram_home_sign.md
> 本次目标：小程序商品详情页展示图片、简介、规格、单价、实时可用库存；库存为 0 禁止下单；并补齐后端公开商品详情接口 `GET /api/v1/products/{id}`。

## 1. 任务范围

### 1.1 包含

- 后端公开商品详情接口 `GET /api/v1/products/{id}`：返回商品主信息（含简介）、启用规格、单价和实时可用库存。
- 小程序商品详情页 `pages/product-detail/product-detail`：商品图片、名称、副标题、简介、规格列表（名称、计价方式、单位、单价、实时可用库存）。
- 规格选择与数量选择：按规格的 `minQuantity`、`stepQuantity` 约束数量；PIECE 取整数，WEIGHT 保留三位小数。
- 下单入口（底部栏“立即下单”按钮）：所选规格可用库存为 0 时禁用按钮并提示售罄，库存大于 0 时可点击；本次不实现真实下单。
- 实时库存刷新：进入页面、下拉刷新、从后台返回页面（onShow）时重新拉取详情，库存始终以服务端查询时刻 `physical_stock_qty - reserved_qty` 为准。
- 首页接入：首页商品卡片点击、轮播图 `PRODUCT` 类型跳转改为进入商品详情页（替换当前“商品详情暂未开放”提示）。
- 小程序图片 URL 补全：后端保存相对 URL（`/uploads/products/...`），小程序端统一补全 `apiBaseUrl` 后展示。
- 后端 Service/Controller 回归测试与小程序 JS 语法检查。

### 1.2 不包含

- 购物车、结算、订单提交、改价确认、微信支付页面和用户端订单接口。
- 商品多图、视频、相册预览、分享海报（一期商品仅 `main_image_url` 单主图）。
- 规格库存预警展示、到货提醒、收藏和足迹。
- 后台商品管理相关改动。

## 2. 现状与接入点

- 后端已有 `PublicCatalogController`：`GET /api/v1/banners`、`/api/v1/categories`、`/api/v1/products`（列表），尚无 `GET /api/v1/products/{id}`。
- 后端已有 `ProductMapper.findActiveById`、`ProductSpecMapper.findActiveByProductIds`、公开列表 VO `PublicProductSpecResponse`，可复用；公开接口不暴露 `physical_stock_qty`、`reserved_qty` 等后台字段。
- `ErrorCode.PRODUCT_NOT_FOUND`（“商品不存在”）已存在，直接复用。
- 小程序已有工程骨架、`services/request.js`、`services/catalog.js`、`utils/format.js` 和首页；首页商品点击目前提示“商品详情暂未开放”。
- 商品图片在库中保存相对 URL（如 `/uploads/products/uuid.jpg`），小程序 `<image>` 直接使用会加载失败，需补全为绝对地址。

## 3. 后端 API 设计

### 3.1 接口定义

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | /api/v1/products/{id} | 公开，无 Token | 商品详情和启用规格；下架、软删除商品不可见 |

路径参数 `id` 必须为正整数，非法时返回 `VALIDATION_ERROR`。

响应（`ApiResponse` 包装，`code=0`）：

~~~json
{
  "code": "0",
  "message": "ok",
  "data": {
    "id": 10,
    "categoryId": 1,
    "categoryName": "淡水鱼",
    "name": "草鱼",
    "subtitle": "鲜活淡水鱼",
    "description": "适合清蒸、红烧",
    "mainImageUrl": "/uploads/products/uuid.jpg",
    "minPriceCent": 3000,
    "maxPriceCent": 4500,
    "totalAvailableQty": 5.500,
    "specs": [
      {
        "id": 20,
        "productId": 10,
        "name": "按斤",
        "quantityKind": "WEIGHT",
        "unit": "kg",
        "priceCent": 3000,
        "minQuantity": 1.000,
        "stepQuantity": 0.500,
        "availableQty": 3.500
      }
    ]
  },
  "traceId": "trace-id"
}
~~~

字段规则：

- `description`：商品简介，可空；本次新增到公开响应（列表接口不返回简介，保持不变）。
- `specs`：仅返回 `status=ACTIVE` 且未删除的规格，字段复用 `PublicProductSpecResponse`。
- `availableQty = physical_stock_qty - reserved_qty`，查询时计算，不落库、不缓存；`totalAvailableQty` 为各规格 `availableQty` 之和。
- `minPriceCent`、`maxPriceCent` 为启用规格单价区间，与列表接口口径一致。
- 响应不包含 `physicalStockQty`、`reservedQty`、`warningQty`、`status` 等后台字段。

### 3.2 可见性与错误

- 仅 `status=ACTIVE` 且 `deleted=0` 的商品可见；不存在、已软删除、已下架统一返回 `PRODUCT_NOT_FOUND`（“商品不存在”），不区分提示，避免探测下架商品。
- 商品存在但无启用规格时正常返回，`specs` 为空数组、价格区间为 `null`、`totalAvailableQty` 为 0。

### 3.3 实现结构

- `PublicCatalogController` 新增 `GET /products/{id}` 方法。
- `PublicCatalogService` 新增只读方法 `productDetail(Long id)`；复用 `toProductResponse` 的规格聚合逻辑，新增详情 VO 组装（补 `description`）。
- 新增 VO `PublicProductDetailResponse`（record），字段见 3.1。
- `ProductMapper`、`ProductSpecMapper` 现有查询可满足，不新增 SQL。

## 4. 小程序页面设计

### 4.1 工程结构

~~~text
miniprogram/
├─ app.json                        # 注册 pages/product-detail/product-detail
├─ pages/
│  ├─ home/                        # 商品卡片与轮播图商品跳转接入详情页
│  └─ product-detail/
│     ├─ product-detail.js
│     ├─ product-detail.json
│     ├─ product-detail.wxml
│     └─ product-detail.wxss
├─ services/
│  └─ catalog.js                   # 新增 getProductDetail(id)
└─ utils/
   └─ format.js                    # 新增 resolveImageUrl（相对 URL 补全）
~~~

### 4.2 页面布局

- 顶部商品主图：固定宽高比（如 4:3），图片加载失败显示占位块（与首页一致）；无图显示“暂无图片”。
- 商品信息区：名称、副标题、分类名、简介（多行文本）；简介为空时隐藏该行。
- 规格区：规格卡片列表，每项展示规格名称、计价方式（PIECE 显示“按件”，WEIGHT 显示“按重量”）、单位、单价（`¥x.xx / 单位`）、实时可用库存（`formatQuantity`）；库存为 0 显示“已售罄”标记。
- 规格选择：点选规格卡片高亮；默认选中第一个有库存的规格，若全部无库存则选中第一个规格。
- 底部操作栏：数量步进器（减/输入/加）+“立即下单”按钮。
- 加载中、加载失败重试、商品不存在（`PRODUCT_NOT_FOUND`）展示“商品不存在或已下架”空态，均使用中文文案。

### 4.3 数量与下单约束

- 数量下限为所选规格 `minQuantity`，步进为 `stepQuantity`，上限为该规格 `availableQty`（库存实时值）。
- PIECE 规格数量取整（步进值按服务端返回的 `stepQuantity` 取整数步进）；WEIGHT 规格数量最多三位小数，按 `stepQuantity` 步进（默认 0.5）。
- 手动输入数量越界时回退到合法边界并 toast 提示。
- 下单按钮状态：
  - 无规格、或所选规格 `availableQty <= 0`：按钮禁用，文案“已售罄，无法下单”。
  - 所选规格 `availableQty > 0`：按钮可用，点击后提示“下单功能暂未开放”（购物车/下单为后续任务，本次只落实“库存为 0 禁止下单”的禁用约束）。
- 重新选择规格时，数量重置为新规格的 `minQuantity`。

### 4.4 实时库存

- `onLoad` 拉取详情；`onShow` 在已加载过的情况下静默刷新（覆盖从其他页面返回的场景）；下拉刷新重新拉取。
- 刷新后同步修正数量上限与按钮禁用状态；刷新失败保留旧数据并 toast 提示。
- 库存以接口返回为准，前端不做任何本地增减。

### 4.5 首页跳转接入

- 首页商品卡片点击：`wx.navigateTo('/pages/product-detail/product-detail?id=' + id)`，替换“商品详情暂未开放”提示。
- 轮播图 `PRODUCT` 类型：跳转商品详情页；`jumpTargetId` 为空时不跳转。
- 首页商品图片同步使用 `resolveImageUrl` 补全相对 URL（与详情页一致，修复相对路径图片无法加载的问题）。

## 5. 金额、数量和图片约束

- 金额始终使用后端整数分字段（`priceCent`、`minPriceCent`、`maxPriceCent`），页面用 `formatMoneyCent` 显示元。
- 数量展示用 `formatQuantity`（三位小数去尾 0）；单价展示为“¥金额 / 单位”，WEIGHT 规格展示单位 `kg`。
- `resolveImageUrl(url)` 规则：`http(s)://` 开头原样返回；以 `/` 开头拼接 `config.apiBaseUrl`；空值返回空字符串。
- 页面文案全部中文；错误只展示服务端中文 message 或统一网络文案，不暴露堆栈。
- 样式沿用首页简约商用风格（深海蓝主色、珊瑚橙价格、绿色状态），图片区域固定比例。

## 6. 测试和验证

### 6.1 后端测试

- `PublicCatalogServiceTest` 新增：详情查询成功（简介、规格单价、可用库存聚合正确）；商品不存在返回 `PRODUCT_NOT_FOUND`；已下架/已软删除返回 `PRODUCT_NOT_FOUND`；无启用规格时 `specs` 为空且价格区间为 `null`。
- `PublicCatalogControllerTest` 新增：`GET /api/v1/products/{id}` 成功响应结构、非法 id 返回 `VALIDATION_ERROR`、不存在返回 `PRODUCT_NOT_FOUND`。
- 执行后端全量测试，确认既有 26 个测试不回归。

### 6.2 小程序验证

- 所有 JS 文件执行 Node 语法检查。
- `app.json` 页面注册、首页跳转、`catalog.js` 详情 API、`resolveImageUrl` 逻辑检查。
- 微信开发者工具手测：详情展示、规格切换、数量步进边界、库存 0 禁用下单、下拉刷新与返回刷新库存、图片加载失败占位。

## 7. 实施顺序

1. 后端：新增 `PublicProductDetailResponse` 与 `PublicCatalogService.productDetail`、Controller 路由。
2. 后端：补充 Service/Controller 测试并执行全量测试。
3. 小程序：`catalog.js` 增加 `getProductDetail`，`format.js` 增加 `resolveImageUrl`。
4. 小程序：新建商品详情页四件套并注册页面。
5. 小程序：首页商品卡片与轮播图 `PRODUCT` 跳转接入详情页，图片 URL 补全。
6. 语法检查与手测验证。
7. 将功能点追加到 `doc/FEATURE_LIST.md`。

## 8. 冲突点和待确认信息

1. 下单按钮行为：本次任务描述含“库存为 0 禁止下单”，但购物车/下单功能属于后续任务。本设计按“保留下单入口 + 库存 0 禁用 + 有库存点击提示‘下单功能暂未开放’”处理。若希望本次不做下单按钮、仅展示库存，请确认。
2. 数量步进器取自设计事实源的商品详情页“数量”要素，纳入本次范围。若本次仅做纯展示页，请确认。
3. 首页商品图片当前直接使用相对 URL，小程序中会加载失败；本设计在 `resolveImageUrl` 中统一补全，并同步修正首页。是否接受一并修复？
4. “实时可用库存”按“每次进入/返回/下拉刷新时重新查询”实现，不做 WebSocket 或轮询。是否接受？
5. 商品仅有单主图（一期数据模型无商品多图），“图片”按主图理解，多图不在范围，是否接受？
