# Travel Memory

Travel Memory 是一个私人旅行记忆产品，不是旅行攻略、社交平台或相册信息流。

Travel Memory 不只是帮助用户记录旅行。
Travel Memory 更重要的是帮助用户多年以后重新体验一次真实发生过的旅行。

照片、当时留下的话、时间和地点共同构成记忆；产品不会用 AI 改写用户的原始内容。

## 当前状态

### V1 已完成

- 旅行管理基础能力
- Memory 新增、编辑、删除
- Timeline
- Journey
- Map 高德真实地图
- 收藏和搜索
- 收藏回看：只浏览当前旅行中已收藏的 Memory

旅行编辑前端页面已完成。

### V1.1 已完成

- 图片 EXIF 时间和 GPS
- 浏览器当前位置和 WGS84 到 GCJ-02 转换
- 高德逆地理编码与地点名称候选推荐
- 用户确认后使用地点名称
- 地点搜索与地图选点：搜索高德 POI、在地图点击选点，并保留用户自定义地点名称
- 同一段 Memory 最多 6 张照片：支持排序、主图同步和统一图库浏览
- 旅行封面管理：可从当前旅行已有的 Memory 照片中选择封面
- Docker Compose 和阿里云 ECS 部署
- Basic Auth 临时访问保护、安全 Nginx 覆盖与 `/healthz`
- MySQL 与 uploads 备份脚本

`Map` 已接入高德真实地图：有坐标的 Memory 可显示为点位，点击点位可查看对应记忆；不包含导航、路线规划或轨迹回放。Memory 坐标在数据库统一保存为 WGS84，前端仅在高德地图展示时转换为 GCJ-02。

地点搜索、地图选点、照片 EXIF 和浏览器定位都只帮助用户确定坐标与推荐名称；用户始终可以保留或修改自己的地点描述。

## 技术栈

- 前端：Vue 3、Vite、Vue Router、Axios
- 后端：Spring Boot 3、Java 17、MyBatis Plus
- 数据库：MySQL 8.4
- 部署：Nginx、Docker Compose
- 图片：宿主机 `uploads/` 目录挂载到后端容器

## 本地开发

```bash
cp .env.example .env
```

在 `.env` 中设置强且唯一的数据库密码；不要提交该文件。主要环境变量包括：

```text
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
MYSQL_DATABASE
MYSQL_USER
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
UPLOADS_DIR
AMAP_WEB_SERVICE_KEY
AMAP_REVERSE_GEOCODE_ENABLED
```

前端真实地图需要在 `travel-memory-web/.env.local` 配置单独申请的高德 Web 端（JS API）Key：

```text
VITE_AMAP_JS_API_KEY=
VITE_AMAP_SECURITY_JS_CODE=
VITE_AMAP_SERVICE_HOST=
```

`AMAP_WEB_SERVICE_KEY` 仅在后端使用，用于逆地理编码和地点 POI 搜索；`VITE_AMAP_JS_API_KEY` 不能复用它。Vite 的 `VITE_` 变量会在构建时注入浏览器产物；生产环境应优先配置安全代理地址 `VITE_AMAP_SERVICE_HOST`，`VITE_AMAP_SECURITY_JS_CODE` 仅适合本地开发或临时测试。Docker Compose 会在构建 frontend 镜像时从根目录 `.env` 注入这些前端变量；变更后必须使用 `--build` 重建 frontend，且不得把真实 Key 或安全密钥提交到仓库。

```bash
cd travel-memory-server
mvn spring-boot:run
```

```bash
cd travel-memory-web
npm ci
npm run dev
```

基础 Compose 适用于本地开发：

```bash
docker compose up -d --build
```

## 生产部署与数据安全

ECS 公网部署必须使用安全覆盖配置，详见 [安全部署说明](docs/SECURE_DEPLOY.md) 和完整的 [ECS 部署运行手册](docs/ALIYUN_ECS_DEPLOY.md)。手册包含首次部署、无 SQL 的普通迭代、带升级 SQL 的增量部署、备份、校验和回滚边界。Basic Auth 只是临时访问保护，不等同于 HTTPS；生产公网使用前仍需要域名、HTTPS 和 HTTP 跳转 HTTPS。

- 生产 MySQL 保持 `mysql:8.4`；不得让 MySQL 8.0 使用现有 `mysql_data` volume。
- MySQL 使用 Docker volume `mysql_data`；上传图片使用 `${UPLOADS_DIR:-./uploads}` 挂载到 backend `/app/uploads`。
- 上线前运行 `./scripts/backup-production.sh`，在 ECS 与受控本地副本分别校验 `SHA256SUMS`。
- 孤儿图片清理默认关闭且默认 dry-run。生产验证时先设置 `APP_UPLOAD_CLEANUP_ENABLED=true`、`APP_UPLOAD_CLEANUP_DRY_RUN=true`、`APP_UPLOAD_CLEANUP_RETENTION_HOURS=24`，确认扫描结果后再单独评估真实删除。
- `.env`、`.htpasswd`、`uploads/`、`backups/`、证书和私钥均不得提交 Git。
- 不要删除 `mysql_data` volume，也不要在需要保留数据的环境执行 `docker compose down -v`。

## 项目结构

```text
docs/                       产品、工程和部署文档
deploy/nginx/               生产安全 Nginx 覆盖配置
scripts/                    认证与备份脚本
sql/                        MySQL 初始化 SQL
uploads/                    本地上传目录（不提交）
travel-memory-server/       Spring Boot 后端
travel-memory-web/          Vue 前端
```

## 后续方向

优先处理域名、HTTPS、HTTP 跳转 HTTPS，以及孤儿图片清理的生产 dry-run 验证。第一版账号与数据隔离已经实现：受控邀请码注册、管理员账号管理、Spring Security Session、Trip 归属用户和按用户隔离的图片路径。迁移与初始化步骤见 [用户账号与数据隔离](docs/USER_ISOLATION.md)。正式公网启用仍需先完成 HTTPS，并在过渡期保留 Basic Auth。
