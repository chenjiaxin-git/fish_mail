# 微信小程序基础框架与首页设计

> 状态：按本次任务执行
> 功能名称：miniprogram_home
> 依赖：doc/CONTEXT.md、doc/STYLE_GUIDE.md、doc/DESIGN_SUMMARY.md、docs/开发设计文档.md
> 本次目标：搭建微信原生小程序基础工程，并完成首页轮播图、分类筛选、商品搜索和分页商品列表。

## 1. 任务范围

### 1.1 包含

- 微信原生小程序工程骨架：`app.json`、`app.js`、`app.wxss`、`project.config.json`、`sitemap.json`。
- `services` 目录封装统一请求、微信登录 Token 管理和首页商品目录 API。
- `utils` 目录集中处理金额分转元、数量格式化和日期格式化。
- 首页：轮播图、分类横向筛选、搜索框、商品分页列表、加载中/空态/加载失败/图片加载失败状态。
- 搜索词长度限制 100 字符、分页 `pageSize=20`、下拉刷新和触底加载更多。
- 首页请求失败时展示中文提示，不暴露异常堆栈；重复加载使用加载锁。

### 1.2 不包含

- 商品详情、购物车、结算、地址、订单列表、订单详情和支付页面。
- 微信授权弹窗、订阅消息和支付流程。
- 后端用户认证、轮播图、分类和商品公开接口的新增实现。
- 营销、会员、优惠券、推荐算法和多门店能力。

## 2. 现状与接入点

- 当前仓库已有 `backend` Spring Boot API、`admin` Vue 管理端和订单模块。
- 当前尚无 `miniprogram` 目录，本次新建原生小程序目录，不使用跨端框架。
- 首页按设计事实源调用公开 API：`GET /api/v1/banners`、`GET /api/v1/categories`、`GET /api/v1/products`。
- 统一响应格式为 `{code, message, data, traceId}`；商品列表使用 `total, page, pageSize, items` 分页结构。

## 3. 工程结构

~~~text
miniprogram/
├─ app.js
├─ app.json
├─ app.wxss
├─ project.config.json
├─ sitemap.json
├─ pages/
│  └─ home/
│     ├─ home.js
│     ├─ home.json
│     ├─ home.wxml
│     └─ home.wxss
├─ services/
│  ├─ request.js
│  ├─ auth.js
│  └─ catalog.js
└─ utils/
   └─ format.js
~~~

## 4. 首页数据和交互

### 4.1 数据获取

- 进入首页并下拉刷新时并行获取轮播图和分类，再加载第一页商品。
- 分类和搜索变化会重置页码并重新加载商品。
- 触底加载下一页；没有下一页时展示“没有更多了”。
- 商品列表字段兼容 `mainImageUrl`、`name`、`subtitle`、`minPriceCent`、`maxPriceCent`、`totalAvailableQty`、`specs` 等公开商品响应字段。

### 4.2 异常状态

- 图片加载失败使用占位块，不显示空白图片。
- 轮播图为空隐藏轮播区域；分类为空展示“暂无分类”。
- 商品为空展示“暂无符合条件的商品”。
- 网络失败展示“网络异常，请稍后重试”，保留重试入口。
- 搜索词最多 100 个字符，超出时裁剪并提示。

### 4.3 轮播图跳转

- `CATEGORY`：切换首页分类并刷新商品。
- `PRODUCT`：本次不创建商品详情页，点击后提示“商品详情暂未开放”。
- `NONE` 或未知类型：不跳转。

## 5. API 约定

| 方法 | 路径 | 参数 | 返回 |
| --- | --- | --- | --- |
| GET | /api/v1/banners | 无 | `data` 为启用轮播图数组 |
| GET | /api/v1/categories | 无 | `data` 为启用分类数组 |
| GET | /api/v1/products | `keyword`、`categoryId`、`page`、`pageSize` | `data` 为 `{total,page,pageSize,items}` |

请求服务封装 `Authorization: Bearer <token>`，Token 由 `services/auth.js` 在微信登录后保存；首页公开接口 Token 为空时不强制登录。

## 6. 金额、数量和样式约束

- 后端金额始终使用整数分，页面统一通过 `formatMoneyCent` 显示为元。
- 数量通过 `formatQuantity` 显示最多三位小数并去掉无意义 0。
- 页面文案全部中文；错误响应只展示服务端中文 message 或统一网络文案。
- 页面使用简约商用风格，主色为深海蓝，辅以珊瑚橙和绿色状态色；图片区域固定比例，避免图片加载导致布局跳动。

## 7. 测试和验证

- 所有 JS 文件执行 Node 语法检查。
- 检查 `app.json` 页面路径和首页配置存在。
- 检查请求、格式化、分类筛选、搜索清空、分页加载锁和图片失败处理代码覆盖。
- 使用微信开发者工具导入 `miniprogram` 目录进行页面视觉和接口手测；需要配置后端域名或本地调试地址。

## 8. 冲突点和待补充信息

1. 后端当前尚未提供 `/api/v1/banners`、`/api/v1/categories`、`/api/v1/products` 用户公开接口；本次小程序按设计文档约定先行实现，联调前需补充对应后端接口。
2. 设计事实源包含商品详情页，但本次任务仅要求首页；首页点击商品暂不跳转，避免创建范围外页面。
3. 小程序 AppID、正式 API 域名和合法 request 域名属于部署配置，不提交真实值；`project.config.json` 使用占位 AppID，`config.js` 使用本地调试地址。
