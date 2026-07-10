# Travel Memory 安全部署说明

本方案通过 Nginx Basic Auth 为单用户、小范围试用提供临时访问保护。它不是正式登录系统，也不提供多用户数据隔离，更不等同于 HTTPS。

## 创建单用户认证

在 ECS 项目根目录执行：

```bash
chmod +x scripts/create-basic-auth.sh scripts/backup-production.sh
./scripts/create-basic-auth.sh
```

脚本只支持单用户，并会在覆盖 `deploy/secrets/.htpasswd` 前要求确认。用户名和密码仅在 ECS 上交互输入；密码不会写入脚本、Compose、命令历史或 Git。`deploy/secrets/` 已被忽略，`.htpasswd` 不得提交。

由于脚本用临时文件替换 `.htpasswd`，修改密码后必须强制重新创建 frontend，使 bind mount 使用新文件：

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.secure.yml \
  up -d --no-deps --force-recreate frontend
```

旧版 Docker Compose：

```bash
docker-compose \
  -f docker-compose.yml \
  -f docker-compose.secure.yml \
  up -d --no-deps --force-recreate frontend
```

## 安全启动与验证

新部署使用：

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.secure.yml \
 up -d --build
```

旧版命令将 `docker compose` 替换为 `docker-compose`。不要执行 `down -v`。

已有 ECS 环境完成备份和 Compose 配置检查后，只更新 backend 与 frontend，避免触碰 MySQL：

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.secure.yml \
  up -d --build --no-deps backend frontend
```

`/`、`/trips`、`/api/` 和 `/uploads/` 使用同一 Basic Auth 域；浏览器认证一次后，前端 API 请求和图片访问会复用该认证。`/healthz` 不需要认证，只返回简单的 `200 OK`。

```bash
curl -i http://localhost/healthz
curl -i http://localhost/
curl -i -u '<username>' http://localhost/api/trips
curl -I -u '<username>' http://localhost/uploads/<image-path>
```

未认证的受保护路径应返回 `401 Unauthorized`。认证后确认页面、API、Vue Router 页面和已有图片均正常。

## 备份与恢复边界

运行 `./scripts/backup-production.sh` 会创建 `backups/YYYY-MM-DD_HH-mm-ss/`，包含数据库导出、uploads 归档和 SHA-256 清单。存在 `INCOMPLETE` 的目录不是可恢复备份，不能用于上线前校验。

备份必须在 ECS 校验后下载到受控、非公开的本地管理员设备，并在本地再次核对 SHA-256。脚本不会删除历史备份、上传第三方服务或自动恢复。

数据库和图片恢复必须由管理员手工确认执行；恢复不是部署失败时的首选回滚方式。先保留现场，再决定是否使用已验证备份。

## HTTPS 后续工作

Basic Auth 不会加密 HTTP 传输。后续仍需域名、HTTPS、`443` 安全组规则和 HTTP 到 HTTPS 跳转；证书和私钥不能提交 Git。
