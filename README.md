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
- Map 坐标空间视图
- 收藏和搜索

旅行修改的后端接口已完成，但前端旅行编辑页面尚未完成。

### V1.1 已完成

- 图片 EXIF 时间和 GPS
- 浏览器当前位置和 WGS84 到 GCJ-02 转换
- 高德逆地理编码与地点名称候选推荐
- 用户确认后使用地点名称
- Docker Compose 和阿里云 ECS 部署
- Basic Auth 临时访问保护、安全 Nginx 覆盖与 `/healthz`
- MySQL 与 uploads 备份脚本

`Map` 当前是基于经纬度的坐标空间视图，不是集成真实地图 SDK 的地图或导航功能。

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

ECS 公网部署必须使用安全覆盖配置，详见 [安全部署说明](docs/SECURE_DEPLOY.md) 和 [ECS 部署说明](docs/ALIYUN_ECS_DEPLOY.md)。Basic Auth 只是临时访问保护，不等同于 HTTPS；生产公网使用前仍需要域名、HTTPS 和 HTTP 跳转 HTTPS。

- 生产 MySQL 保持 `mysql:8.4`；不得让 MySQL 8.0 使用现有 `mysql_data` volume。
- MySQL 使用 Docker volume `mysql_data`；上传图片使用 `${UPLOADS_DIR:-./uploads}` 挂载到 backend `/app/uploads`。
- 上线前运行 `./scripts/backup-production.sh`，在 ECS 与受控本地副本分别校验 `SHA256SUMS`。
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

优先处理域名、HTTPS、HTTP 跳转 HTTPS、旅行编辑前端页面、旅行封面、孤儿图片清理和真实地图 SDK。正式登录、多用户数据隔离和对象存储备份留待独立任务。
