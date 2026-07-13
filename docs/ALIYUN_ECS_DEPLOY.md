# 阿里云 ECS 部署说明

本项目通过 Docker Compose 部署。公网只暴露 frontend 的 HTTP 端口；backend 和 MySQL 不映射公网端口。公网部署必须结合 [安全部署说明](SECURE_DEPLOY.md) 使用。

## ECS 准备

- 推荐 Ubuntu 22.04 LTS 或 Alibaba Cloud Linux 3。
- 安全组开放 `80`；配置域名和 HTTPS 后再开放 `443`。
- `22` 仅允许可信管理 IP；不要开放 `3306` 或 `8080`。
- 创建 `.env` 后设置 `600` 权限，不提交该文件。
- 账号隔离上线前先完成 `sql/upgrade_20260713_add_user_ownership.sql`，并保持 `APP_REGISTRATION_ENABLED=false`；仅在受控首次初始化或邀请期间短暂开启邀请码注册，完成后立即关闭。

## MySQL 与持久化

- 生产环境当前使用 MySQL 8.4，Compose 保持 `mysql:8.4`，与现有 `mysql_data` 数据卷一致。
- 固定具体 `8.4.x` 小版本前，必须先在 ECS 核对：

  ```bash
  docker compose exec -T mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT VERSION();"'
  docker inspect travel-memory-mysql
  ```

- 不允许把现有 MySQL 8.4 数据卷交给 MySQL 8.0 使用，也不允许原地降级。
- 如确实需要降级，只能新建独立的 8.0 数据卷，通过逻辑备份和恢复迁移，不能复用 `mysql_data`。
- 不删除或重建 `mysql_data`，不执行 `docker compose down -v`。
- uploads 保存于宿主机 `UPLOADS_DIR`，挂载到 backend `/app/uploads`；容器重建不应删除上传图片。

## 部署来源

先完成本地验证、审查 `git diff` 与 `git status`、确认不存在错误的 MySQL 8.0 镜像标签，再提交并推送 `feature/travel-memory-app`。ECS 只能部署已推送 commit 的 GitHub 拉取结果，或具有相同 commit SHA 的打包产物。

推送后核对远端分支 SHA 与本地 HEAD 一致；推送完成前，不能认为 GitHub 上的部署配置已经修正。

## 更新前备份

上线前运行：

```bash
./scripts/backup-production.sh
sha256sum -c backups/<timestamp>/SHA256SUMS
```

备份包含 `mysql.sql`、`uploads.tar.gz` 和 `SHA256SUMS`。仅当目录不存在 `INCOMPLETE`、ECS 校验通过，并已将整个时间戳目录下载到受控且非公开的本地管理员设备后才可更新：

```bash
scp -r <ecs-user>@<ecs-host>:<remote-backup-dir> <local-encrypted-backup-root>
```

在本地再次执行 `sha256sum -c SHA256SUMS` 并核对文件大小。ECS 本地备份不自动删除，备份不得提交 Git。

## HTTPS 后续工作

HTTPS 需要正式域名解析到 ECS 公网 IP，并在安全组开放 `443`。可使用 Certbot 或其他成熟证书方案；证书和私钥绝不能提交 Git。配置 HTTPS 后应将 HTTP 重定向到 HTTPS。在此之前，HTTP 传输未加密。
