# 阿里云 ECS 部署运行手册

本文档以仓库当前 Docker Compose、Session 账号隔离、私有图片访问和安全 Nginx 覆盖为准。它覆盖新 ECS 首次部署、已有 ECS 的普通迭代部署、带数据库升级的增量部署，以及回滚边界。

不要在生产环境执行 `docker compose down -v`、`docker-compose down -v`、删除 `mysql_data`，或让 MySQL 8.0 接管现有 MySQL 8.4 数据卷。

相关文档：

- [安全访问保护](SECURE_DEPLOY.md)
- [用户账号与数据隔离](USER_ISOLATION.md)
- [备份脚本](../scripts/backup-production.sh)

## 1. 部署结构与边界

```text
Internet :80/:443
        |
     frontend (Nginx, only public container)
        |-- /api/     -> backend:8080
        |-- /uploads/ -> backend:8080 (authenticated ownership check)
        |
 backend (private Docker network) -> mysql:3306 (private Docker network)

mysql_data                  Docker volume, database persistence
${UPLOADS_DIR:-./uploads}  host directory, uploaded image persistence
```

- 只允许 `frontend` 映射 HTTP 端口；不要给 `backend` 或 `mysql` 增加 `ports`。
- `/uploads/**` 经过 backend 的登录和所属用户校验，不应恢复为 Nginx 或 Spring 静态目录公开读取。
- Basic Auth 是公网迁移期的第二层访问保护；它不替代应用账号隔离，也不加密 HTTP。
- HTTPS 启用后才设置 `APP_SESSION_COOKIE_SECURE=true`。

## 2. ECS 准备

建议使用 Ubuntu 22.04 LTS 或 Alibaba Cloud Linux 3，并安装 Docker Engine、Docker Compose v2、Git、OpenSSL 和基础校验工具。以受限管理员账号登录，避免长期使用 root。

安全组建议：

- `22`：仅可信管理 IP。
- `80`：公网 HTTP；启用 HTTPS 后仅用于跳转。
- `443`：完成域名和证书配置后再开放。
- 不开放 `3306`、`8080`，也不开放 Docker 内部网段。

先确认工具可用：

```bash
docker --version
docker compose version
git --version
openssl version
sha256sum --version
```

如果服务器只有旧版 Compose，后续命令中的 `docker compose` 可以替换为 `docker-compose`。

## 3. 获取指定代码版本

首次部署示例：

```bash
sudo mkdir -p /opt/travel-memory
sudo chown "$USER":"$USER" /opt/travel-memory
git clone --branch feature/travel-memory-app https://github.com/823889637/travel-memory.git /opt/travel-memory
cd /opt/travel-memory
git rev-parse HEAD
```

部署前记录 commit SHA。ECS 只能部署已经推送到 GitHub 的 commit，或能明确证明使用相同 SHA 的离线产物。

已有服务器更新前先确认目录干净且目标分支正确：

```bash
cd /opt/travel-memory
git status --short
git fetch origin
git log --oneline HEAD..origin/feature/travel-memory-app
git pull --ff-only origin feature/travel-memory-app
git rev-parse HEAD
```

`.env`、`uploads/`、`backups/` 和 `deploy/secrets/.htpasswd` 已被 Git 忽略，不能用 Git 覆盖它们。

## 4. 生产环境变量

首次部署时从仓库中的临时测试模板创建服务器专用 `.env`。它只保留在 ECS，权限设为 `600`，绝不能提交或复制到聊天、工单和截图中。临时测试部署可直接使用模板值；公开生产环境必须替换数据库密码、邀请码和高德凭据为独立值。

```bash
cp .env.example .env
chmod 600 .env
```

编辑 `.env`，至少明确设置以下变量：

```dotenv
TZ=Asia/Shanghai
HTTP_PORT=80

MYSQL_DATABASE=travel_memory
MYSQL_USER=travel_user
MYSQL_PASSWORD=<strong-unique-application-password>
MYSQL_ROOT_PASSWORD=<different-strong-root-password>

UPLOADS_DIR=/opt/travel-memory/uploads

# HTTP 阶段必须为 false；HTTPS 生效后改为 true 并重建 backend。
APP_SESSION_COOKIE_SECURE=false

# 临时测试模板默认允许用以下邀请码注册第一个管理员。
# 完成首次注册后立即改为 false 并清空邀请码，再重建 backend。
APP_REGISTRATION_ENABLED=true
APP_REGISTRATION_INVITE_CODE=travel-memory-test-invite-2026

# 后端 Web Service Key，只由 backend 使用。
AMAP_WEB_SERVICE_KEY=
AMAP_REVERSE_GEOCODE_ENABLED=false

# 前端地图变量在 docker build 时写进浏览器产物。
VITE_AMAP_JS_API_KEY=
VITE_AMAP_SECURITY_JS_CODE=
VITE_AMAP_SERVICE_HOST=

# 孤儿图片清理默认关闭；生产首次仅允许 dry-run。
APP_UPLOAD_CLEANUP_ENABLED=false
APP_UPLOAD_CLEANUP_DRY_RUN=true
APP_UPLOAD_CLEANUP_RETENTION_HOURS=24
APP_UPLOAD_CLEANUP_CRON="0 30 3 * * ?"
```

变量说明：

| 变量 | 使用位置 | 生效时机 |
| --- | --- | --- |
| `MYSQL_*` | MySQL 与 backend | 容器创建时；已有数据卷不要随意修改 |
| `UPLOADS_DIR` | 宿主机到 backend `/app/uploads` | 容器重建时；必须保持同一目录 |
| `AMAP_WEB_SERVICE_KEY` | backend 地点搜索和逆地理编码 | 重建 backend |
| `VITE_AMAP_*` | 前端高德地图 | 必须重建 frontend 镜像 |
| `APP_SESSION_COOKIE_SECURE` | Session Cookie | HTTPS 生效后重建 backend |
| `APP_REGISTRATION_*` | 受控初始化和邀请码注册 | 重建 backend |
| `APP_UPLOAD_CLEANUP_*` | 定时孤儿图片清理 | 重建 backend |

`VITE_AMAP_JS_API_KEY` 是前端地图 Key，和 `AMAP_WEB_SERVICE_KEY` 不是同一个 Key。Vite 会把所有 `VITE_` 变量写入浏览器产物，因此前端 Key 必须在高德控制台限制可用域名；后端 Web Service Key 不得写入任何 `VITE_` 变量。变更任意 `VITE_AMAP_*` 后，必须使用 `--build` 重建 frontend，单纯重启容器不会更新地图配置。

## 5. 首次部署

### 5.1 创建持久化目录与 Basic Auth

```bash
# 与 .env 的 UPLOADS_DIR 保持一致；默认使用项目下的 uploads。
mkdir -p uploads
chmod 750 uploads

chmod +x scripts/create-basic-auth.sh scripts/backup-production.sh
./scripts/create-basic-auth.sh
```

`create-basic-auth.sh` 交互创建唯一的 Basic Auth 用户，并在覆盖已有 `.htpasswd` 前确认。密码修改后必须强制重建 frontend：

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.secure.yml \
  up -d --no-deps --force-recreate frontend
```

### 5.2 检查最终 Compose 并启动

```bash
docker compose -f docker-compose.yml -f docker-compose.secure.yml config
docker compose -f docker-compose.yml -f docker-compose.secure.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.secure.yml ps
```

新建 `mysql_data` volume 时，MySQL 只会执行一次 `sql/init.sql`。它用于全新环境，会创建当前全部表；不要在已有数据库执行该文件。

### 5.3 首次管理员初始化

临时测试模板已开启受控邀请码注册。完成首次管理员初始化后应立即将 `.env` 的 `APP_REGISTRATION_ENABLED=false`、`APP_REGISTRATION_INVITE_CODE=`，然后只重建 backend。也可以使用交互式脚本完成初始化：

1. 受控邀请码注册：在受保护的 `/register` 使用模板邀请码完成第一个账号注册。第一位成功注册的账号会成为管理员。
2. 交互式脚本：执行 `./scripts/activate-initial-admin.sh`。该脚本要求 backend 已启动，会隐藏输入密码，不提供公网初始化接口。

邀请码方式的 backend 重建命令：

```bash
docker compose -f docker-compose.yml -f docker-compose.secure.yml \
  up -d --no-deps --force-recreate backend
```

第一个成功注册的账号会激活预留管理员；后续账号应由管理员在账号管理页面创建。不要长期开放注册。

### 5.4 首次验证

```bash
curl -i http://localhost/healthz
curl -i http://localhost/
docker compose -f docker-compose.yml -f docker-compose.secure.yml logs --tail=100 frontend
docker compose -f docker-compose.yml -f docker-compose.secure.yml logs --tail=100 backend
docker compose -f docker-compose.yml -f docker-compose.secure.yml logs --tail=100 mysql
```

- `/healthz` 必须返回 `200`，且不返回业务数据。
- 未认证的首页、`/api/` 与 `/uploads/` 应返回 `401`。
- Basic Auth 登录后，再登录应用账号，验证旅行列表、上传图片、地图和图片访问。
- `mysql` 必须 healthy；backend 与 mysql 不得有公网端口映射。

## 6. 上线前备份与异地校验

任何更新前先执行完整备份：

```bash
./scripts/backup-production.sh
sha256sum -c backups/<timestamp>/SHA256SUMS
```

只有目录中不存在 `INCOMPLETE` 且校验成功时，才能继续。将整个时间戳目录下载到受控、非公开的管理员电脑，再次校验：

```bash
scp -r <ecs-user>@<ecs-host>:/opt/travel-memory/backups/<timestamp> <local-encrypted-backup-root>
cd <local-encrypted-backup-root>/<timestamp>
sha256sum -c SHA256SUMS
```

本地副本建议放在磁盘加密目录或加密压缩包中。ECS 不自动删除历史备份，`backups/` 不得提交 Git。

## 7. 普通迭代部署（无数据库结构变更）

适用于纯前端、后端逻辑、Nginx、文档或环境变量调整，且目标 commit 不包含新的升级 SQL。

```bash
cd /opt/travel-memory
./scripts/backup-production.sh
sha256sum -c backups/<timestamp>/SHA256SUMS

git fetch origin
git pull --ff-only origin feature/travel-memory-app
git rev-parse HEAD

docker compose -f docker-compose.yml -f docker-compose.secure.yml config
docker compose -f docker-compose.yml -f docker-compose.secure.yml \
  up -d --build --no-deps backend frontend
docker compose -f docker-compose.yml -f docker-compose.secure.yml ps
```

这个命令只重建 backend 和 frontend，不重启 MySQL、不删除 `mysql_data`、不改动 `UPLOADS_DIR`。更新 `.env` 后也使用这条命令；只有改动 `VITE_AMAP_*` 时 frontend 必须包含 `--build`。

## 8. 增量部署（包含数据库升级 SQL）

适用于本仓库新增 `sql/upgrade_*.sql` 的版本。升级 SQL 应按日期顺序执行；当前已有数据库依次使用：

```text
sql/upgrade_20260712_add_memory_photo.sql
sql/upgrade_20260713_add_user_ownership.sql
sql/upgrade_20260714_add_trip_companion.sql
```

执行前先阅读 SQL、核对目标数据库当前状态，并完成第 6 节的异地验证备份。建议在短维护窗口执行：

```bash
cd /opt/travel-memory
./scripts/backup-production.sh
sha256sum -c backups/<timestamp>/SHA256SUMS

git fetch origin
git pull --ff-only origin feature/travel-memory-app

# 保留 MySQL 和数据卷，只暂停应用访问。
docker compose -f docker-compose.yml -f docker-compose.secure.yml stop frontend backend

apply_upgrade() {
  local sql_file="$1"
  docker compose exec -T mysql sh -c '
    set -eu
    umask 077
    option_file="$(mktemp /tmp/travel-memory-mysql.XXXXXX)"
    cleanup_option_file() { rm -f "$option_file"; }
    trap cleanup_option_file EXIT HUP INT TERM
    printf "[client]\\nuser=%s\\npassword=%s\\n" "$MYSQL_USER" "$MYSQL_PASSWORD" > "$option_file"
    chmod 600 "$option_file"
    mysql --defaults-extra-file="$option_file" "$MYSQL_DATABASE"
  ' < "$sql_file"
}

apply_upgrade sql/upgrade_20260712_add_memory_photo.sql
apply_upgrade sql/upgrade_20260713_add_user_ownership.sql
apply_upgrade sql/upgrade_20260714_add_trip_companion.sql

docker compose -f docker-compose.yml -f docker-compose.secure.yml \
  up -d --build --no-deps backend frontend
```

升级完成后执行第 9 节验证。不要通过重新挂载 `sql/init.sql` 迁移已有数据库；不要为了重试升级删除 volume。升级 SQL 设计为可重复执行，但仍应在备份后按一次变更、一轮验证的节奏执行。

## 9. 每次部署后的验证

```bash
docker compose -f docker-compose.yml -f docker-compose.secure.yml ps
docker compose -f docker-compose.yml -f docker-compose.secure.yml logs --tail=100 backend
docker compose -f docker-compose.yml -f docker-compose.secure.yml logs --tail=100 frontend
docker compose -f docker-compose.yml -f docker-compose.secure.yml logs --tail=100 mysql
curl -i http://localhost/healthz
```

浏览器验收至少覆盖：

- Basic Auth 未认证 `401`，认证后前端、API、已有图片均可访问。
- 应用账号登录、退出、修改密码；普通用户不可访问账号管理。
- 两个用户不能通过 Trip ID、Memory ID、封面或 `/uploads/` URL 访问对方数据。
- 新建和编辑旅行、Memory 多照片上传/排序、收藏、Journey、Map、地点搜索、旅行回顾和同行者。
- 现有数据、原上传图片和 MySQL health 都保持正常。

部署完成后再做一次备份，并完成 ECS 与本地副本的 SHA-256 校验。

## 10. 失败处理与回滚边界

- frontend 或 backend 异常：保留 MySQL 和 uploads，切回已记录的稳定 commit 后只重建 backend/frontend。
- MySQL 或迁移异常：停止继续操作，保留现场和日志。不要降级 MySQL、不要删除 volume、不要自动恢复。
- 数据恢复只在确认数据损坏后，由管理员手工使用已验证的 MySQL 与 uploads 备份执行。
- Basic Auth 文件更新后页面无法访问：恢复已确认的 `.htpasswd` 或 frontend 配置，然后使用 `--force-recreate frontend`；恢复无认证公网访问需要管理员明确决定。

记录每次部署的 commit SHA、镜像 ID、数据库版本、备份目录和验证结果，方便定位与回滚。

## 11. HTTPS 上线边界

HTTPS 需要正式域名解析到 ECS 公网 IP、安全组开放 `443`，以及成熟证书方案（例如 Certbot）。证书、私钥、AccessKey 和域名 DNS 凭据不得提交 Git。

启用 HTTPS 后：

1. 配置 Nginx 的证书与 `443` 监听。
2. 将 HTTP 重定向到 HTTPS。
3. 设置 `.env` 的 `APP_SESSION_COOKIE_SECURE=true`。
4. 只重建 backend 和 frontend，并重新验证登录 Cookie、图片和 API。

在 HTTPS 生效前，不要宣称传输已加密。
