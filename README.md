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
- 收藏筛选：可在当前旅行 Timeline 中只浏览已收藏的 Memory；旧收藏回看地址继续兼容并跳转到该筛选状态

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

`Map` 已接入高德真实地图：有坐标的 Memory 按时间顺序显示为编号图钉，并使用虚线表达记忆先后；该连线不是导航路线或真实轨迹。地图页只保留“恢复完整路线”一个自定义控件，缩放与拖动使用地图原生手势。点击图钉或横向日期标签时不会改变缩放级别或把页面滚回顶部；地图先将选中图钉平移到 Memory 卡片之外，再显示对应记忆。高德 Logo、版权和审图信息始终保留。Memory 坐标在数据库统一保存为 WGS84，前端仅在高德地图展示时转换为 GCJ-02。

地点搜索、地图选点、照片 EXIF 和浏览器定位都只帮助用户确定坐标与推荐名称；用户始终可以保留或修改自己的地点描述。

### V1.2 本地开发完成，待浏览器与上线验收

- 单趟旅行回顾：使用真实 Trip、Memory、照片、收藏、时间和地点生成摘要，不改写用户原话。
- 同行的人：当前登录账号作为“我”固定显示在首位；其他同行人是旅行内的记忆称呼，不是系统账号，也不获得旅行访问权限。
- 相关记忆：进入同行者页面时不自动查询；选择“我”后浏览当前旅行全部 Memory，选择其他同行人后再查询与其关联的 Memory。
- 同行者管理：只添加“我”以外的人；先记录名字，之后可在名单中添加或更换头像、改名、停用，历史 Memory 关联继续保留。
- 移动端旅行内导航统一为时间线、旅程回放、地图、旅行回顾和同行的人 5 个入口；收藏回看合并为 Timeline 的收藏筛选。

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

仓库中的 `.env.example` 是当前可运行的临时测试配置：复制后可用于本机和全新的 ECS Docker Compose 部署。`.env` 仍被 Git 忽略；线上已有 `.env` 不会被 `git pull` 覆盖。公开部署前必须替换数据库密码、注册邀请码和高德凭据，并在首次管理员注册后关闭注册。

主要环境变量包括：

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

直接运行 Vite 时，复制前端临时模板：

```bash
cp travel-memory-web/.env.local.example travel-memory-web/.env.local
```

前端真实地图使用单独申请的高德 Web 端（JS API）Key：

```text
VITE_AMAP_JS_API_KEY=
VITE_AMAP_SECURITY_JS_CODE=
VITE_AMAP_SERVICE_HOST=
```

`AMAP_WEB_SERVICE_KEY` 仅在后端使用，用于逆地理编码和地点 POI 搜索；`VITE_AMAP_JS_API_KEY` 不能复用它。Vite 的 `VITE_` 变量会在构建时注入浏览器产物；生产环境应优先配置安全代理地址 `VITE_AMAP_SERVICE_HOST`，`VITE_AMAP_SECURITY_JS_CODE` 仅适合本地开发或临时测试。Docker Compose 会在构建 frontend 镜像时从根目录 `.env` 注入这些前端变量；变更后必须使用 `--build` 重建 frontend。仓库模板中的凭据仅限临时测试，生产凭据和证书不得提交到仓库。

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
