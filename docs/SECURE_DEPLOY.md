# Travel Memory 安全部署说明

本方案通过 Nginx Basic Auth 为单用户或小范围试用提供临时公网访问保护。它不等同于正式账号系统或数据隔离，也不等同于 HTTPS。

## 1. 准备环境变量和认证文件

在 ECS 项目根目录创建 `.env`，并设置强且唯一的数据库密码：

```bash
cp .env.example .env
chmod 600 .env
```

创建或更新 Basic Auth 用户。脚本会交互读取用户名和两次隐藏输入的密码，不会把明文密码写入脚本、Compose 文件或终端命令历史：

```bash
chmod +x scripts/create-basic-auth.sh scripts/backup-production.sh
./scripts/create-basic-auth.sh
```

生成的 `deploy/secrets/.htpasswd` 已被 Git 忽略，绝不能提交。修改 Basic Auth 密码时重新执行该脚本，并确认覆盖现有文件。

## 2. 安全启动

推荐新版 Docker Compose：

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.secure.yml \
  up -d --build
```

兼容旧版命令：

```bash
docker-compose \
  -f docker-compose.yml \
  -f docker-compose.secure.yml \
  up -d --build
```

停止服务但保留 MySQL volume 和上传文件：

```bash
docker compose -f docker-compose.yml -f docker-compose.secure.yml stop
```

不要执行 `docker compose down -v`，它会删除命名 volume。

## 3. 访问保护与验证

受保护路径为 `/`、`/trips`、`/api/` 和 `/uploads/`；它们使用同一个认证域，浏览器完成一次登录后，前端请求和图片访问会自动携带认证信息。`/healthz` 是唯一无需认证的健康检查地址，只返回 `200 OK` 和简单文本。

```bash
curl -i http://localhost/healthz
curl -i http://localhost/
```

第二个命令应返回 `401 Unauthorized`。在浏览器打开站点后输入 Basic Auth 凭据，确认页面、API 请求和图片均能加载。也可以使用会交互提示密码的 curl：

```bash
curl -i -u '<username>' http://localhost/api/trips
curl -I -u '<username>' http://localhost/uploads/<image-path>
```

查看运行状态和日志：

```bash
docker compose -f docker-compose.yml -f docker-compose.secure.yml ps
docker compose -f docker-compose.yml -f docker-compose.secure.yml logs -f frontend
docker compose -f docker-compose.yml -f docker-compose.secure.yml logs -f backend
docker compose -f docker-compose.yml -f docker-compose.secure.yml logs -f mysql
```

## 4. 备份与手工恢复

服务运行时执行：

```bash
./scripts/backup-production.sh
```

脚本会在 `backups/YYYY-MM-DD_HH-mm-ss/` 创建 `mysql.sql` 与 `uploads.tar.gz`，不会删除历史备份或上传到第三方服务。MySQL 密码只在 MySQL 容器内部环境变量中读取。

恢复必须由管理员确认后手工执行。先停止会写入数据的服务并确认目标环境，再导入数据库：

```bash
docker compose exec -T mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < backups/<timestamp>/mysql.sql
```

恢复图片前使用 `docker inspect` 确认 backend 容器 `/app/uploads` 对应的宿主机目录，并确认目标目录中没有需要保留的新文件，然后解压：

```bash
tar -xzf backups/<timestamp>/uploads.tar.gz -C <uploads-parent-directory>
```

不要把恢复自动化为未经确认的部署步骤。

## 5. HTTPS 后续工作

HTTPS 需要正式域名解析到 ECS 公网 IP，并在安全组开放 `443`。可使用 Certbot 或其他成熟的证书方案。证书文件和私钥不可提交到 Git；启用 HTTPS 后，应将 HTTP 重定向到 HTTPS。在启用前，不应宣称当前传输已加密。
