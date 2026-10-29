# 生鲜水产线上经营系统

本项目提供客户微信小程序、商家管理后台和Java API，支持预下单、商家称重报价、用户确认支付及自配送。当前代码已接入订阅消息与MinIO存储，生产部署包位于deploy。真实微信模板、HTTPS域名、商户配置与实际服务器上线仍需在目标环境验证。

## 目录

- backend：Java17、Spring Boot、MyBatis、MySQL8、Flyway、微信支付与订阅消息。
- admin：Vue3、TypeScript、Element Plus商家后台。
- miniprogram：微信原生小程序。
- deploy：Compose、Nginx、镜像构建、配置示例及备份恢复脚本。
- design：经确认的功能设计和验收记录。
- docs：开发设计、部署文档和商家操作手册。

## 开发与测试

本地后端需要MySQL8、Java17与Maven；管理员/用户JWT密钥分别配置，设置首次管理员密码。配置变量见backend/src/main/resources/application.yml。本地默认图片存储为文件目录，生产profile要求MinIO与真实微信登录配置。真实支付与订阅发送默认关闭。

```sh
cd backend
mvn -B test
# 需先准备数据库与必要环境变量，再启动服务
mvn spring-boot:run
```

```sh
cd admin
npm ci
npm run dev
npm run build
```

后端集成测试需要Docker，MinIO测试默认使用本项目构建的fishmall-minio:2025-09-07。首次运行全部测试前先构建该镜像：

```sh
docker build -f deploy/minio.Dockerfile -t fishmall-minio:2025-09-07 deploy
node --test tests/admin-business.test.cjs tests/miniprogram-orders.test.js tests/miniprogram-subscription.test.cjs
```

小程序使用微信开发者工具打开miniprogram，真实联调需替换touristappid和config.js中的本地API地址。生产登录缺配置会启动失败，不能使用本地Fake登录上线。

## 生产交付入口

按[部署文档](docs/部署文档.md)执行环境准备、构建、HTTPS配置、迁移及备份恢复。商家日常使用见[商家操作手册](docs/商家操作手册.md)。

订阅消息只覆盖订单提交、商家报价和配送完成；用户拒绝订阅不影响下单，发送未知结果不自动重发。通知成功只表示微信接口接受，不表示用户已读。

MinIO服务端和mc按固定官方源码版本构建，镜像内保留LICENSE；Java客户端版本8.5.17。MinIO/mc采用AGPLv3，交付或分发镜像时应保留许可与对应源码获取方式，见部署文档。真实配置、密钥、图片原件和备份均不得提交代码仓库。

## 生成交付包

在仓库根目录运行`python tools/build_release.py`，输出dist下的源码部署ZIP和SHA-256校验文件。打包使用白名单，包含Docker构建所需源码、配置示例、脚本和文档，不包含.env、私钥、证书、备份、缓存或构建目录。实际镜像可在目标环境按部署文档构建并以唯一发布版本保存。

Word设计由`tools/export_design_docx.py`从开发设计Markdown生成，需python-docx；发布前应以LibreOffice或Word渲染核查中文与分页。
