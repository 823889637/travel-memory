# 阿里云 ECS 部署说明

本项目使用 Docker Compose 部署。ECS 对外只开放 frontend 的 HTTP 端口；backend 和 MySQL 不映射公网端口。

公网部署前请阅读并按 [安全部署说明](SECURE_DEPLOY.md) 使用 Basic Auth 覆盖配置。该保护适合单用户或小范围试用，仍需要在后续配置域名和 HTTPS。

## ECS 准备

- 推荐 Ubuntu 22.04 LTS 或 Alibaba Cloud Linux 3，2 vCPU、4 GB 内存、40 GB 以上磁盘。
- 安全组开放 `80`；有域名并配置 HTTPS 后再开放 `443`。
- `22` 仅允许可信管理 IP。
- 不要开放 `3306` 或 `8080`。

安装 Docker Engine 和 Docker Compose Plugin 后，拉取项目并准备环境文件：

```bash
git clone <repository-url> travel-memory
cd travel-memory
cp .env.example .env
chmod 600 .env
```

在 `.env` 中设置 `MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`，以及需要时设置 `AMAP_WEB_SERVICE_KEY` 和 `AMAP_REVERSE_GEOCODE_ENABLED=true`。不要提交 `.env`、上传图片、备份或 `deploy/secrets/`。

## 数据与版本安全

- Compose 固定使用 `mysql:8.0.42`，避免不确定的标签。
- 修改 MySQL 镜像版本前必须完成备份并核对当前运行版本；不要随意跨大版本切换。
- 不要删除 `mysql_data` volume，也不要使用 `docker compose down -v`。
- MySQL 数据保存在 `mysql_data` volume；上传图片保存在宿主机 `UPLOADS_DIR`（默认 `./uploads`），挂载到 backend 的 `/app/uploads`。

更新部署时先备份，再拉取代码并按安全部署文档重新构建：

```bash
./scripts/backup-production.sh
git pull
docker compose -f docker-compose.yml -f docker-compose.secure.yml up -d --build
```

## HTTPS 后续工作

HTTPS 需要正式域名解析到 ECS 公网 IP，并在安全组开放 `443`。可使用 Certbot 或其他成熟证书方案；证书和私钥绝不能提交到 Git。配置成功后，应把 HTTP 重定向到 HTTPS。在此之前，HTTP 传输并未加密。
