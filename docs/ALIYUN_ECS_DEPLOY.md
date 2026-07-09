# Travel Memory 阿里云 ECS 部署说明

本文档只说明手动部署流程，不使用阿里云 CLI，不需要 AccessKey。

## 1. 推荐 ECS 配置

基础可用配置：

- 2 vCPU
- 4 GB 内存
- 40 GB 以上 ESSD 云盘
- Ubuntu 22.04 LTS 或 Alibaba Cloud Linux 3
- 带宽按访问量选择，个人使用 3-5 Mbps 通常足够

如果图片较多，建议把云盘提升到 80 GB 或更高，并定期备份 `uploads`。

## 2. 安全组

开放：

- `80`：对公网开放，用于访问前端
- `443`：可选，后续配置 HTTPS 时开放
- `22`：只允许你的个人公网 IP 访问

不要开放：

- `3306`：MySQL 只在 Docker 网络内部访问
- `8080`：后端只由 Nginx 通过 Docker 网络访问

## 3. 安装 Docker

Ubuntu 示例：

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg git
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
docker version
docker compose version
```

如果使用 Alibaba Cloud Linux，请按 Docker 官方或阿里云文档安装 Docker Engine 和 Docker Compose Plugin。

## 4. 拉取项目

```bash
git clone <your-repository-url> travel-memory
cd travel-memory
```

## 5. 配置环境变量

```bash
cp .env.example .env
vi .env
```

必须修改：

```bash
MYSQL_PASSWORD=你的数据库用户密码
MYSQL_ROOT_PASSWORD=你的数据库root密码
```

如需启用高德地点名称推荐：

```bash
AMAP_WEB_SERVICE_KEY=你的高德WebServiceKey
AMAP_REVERSE_GEOCODE_ENABLED=true
```

不启用时保持：

```bash
AMAP_WEB_SERVICE_KEY=
AMAP_REVERSE_GEOCODE_ENABLED=false
```

不要提交 `.env`。

## 6. 启动服务

```bash
docker compose up -d --build
```

启动后访问：

```text
http://你的服务器公网IP/
```

## 7. 查看日志

全部服务：

```bash
docker compose logs -f
```

后端：

```bash
docker compose logs -f backend
```

前端 Nginx：

```bash
docker compose logs -f frontend
```

MySQL：

```bash
docker compose logs -f mysql
```

## 8. 更新部署

```bash
git pull
docker compose up -d --build
```

如果只更新前端或后端，也可以单独构建：

```bash
docker compose up -d --build frontend
docker compose up -d --build backend
```

## 9. 数据持久化

MySQL 数据：

- 使用 Docker volume：`mysql_data`
- 容器重建不会删除数据库数据

上传图片：

- 默认挂载到宿主机：`./uploads`
- 可在 `.env` 中通过 `UPLOADS_DIR` 修改
- 容器重建不会删除上传图片

## 10. 备份

备份 MySQL：

```bash
mkdir -p backups
docker compose exec mysql mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" > backups/travel_memory_$(date +%F).sql
```

备份上传图片：

```bash
tar -czf backups/uploads_$(date +%F).tar.gz uploads
```

建议把 `backups/` 定期下载到本地或同步到对象存储。

## 11. 恢复

恢复 MySQL：

```bash
docker compose exec -T mysql mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < backups/travel_memory_YYYY-MM-DD.sql
```

恢复上传图片：

```bash
tar -xzf backups/uploads_YYYY-MM-DD.tar.gz
```

## 12. 网络和访问路径

Nginx 对外暴露 `80`：

- `/`：Vue 前端
- `/api/`：反向代理到 `backend:8080`
- `/uploads/`：反向代理到 `backend:8080/uploads/`

MySQL 和后端不暴露公网端口。

## 13. 常用排查

查看容器状态：

```bash
docker compose ps
```

后端无法连接数据库：

```bash
docker compose logs backend
docker compose logs mysql
```

图片无法访问：

```bash
ls -lah uploads
docker compose logs backend
```

前端刷新 404：

- 检查 `travel-memory-web/nginx.conf` 是否包含：

```nginx
try_files $uri $uri/ /index.html;
```

## 14. 安全注意事项

- 不要开放 `3306`
- 不要开放 `8080`
- 不要提交 `.env`
- 不要提交真实数据库密码
- 不要提交高德 Key
- SSH `22` 只允许个人 IP
- 生产环境建议后续增加 HTTPS
