# 用户账号与数据隔离

Travel Memory 的第一版账号能力使用 Spring Security Session。没有自由公开注册、JWT、Redis 或管理员跨账号查看旅行数据的能力；受邀请码和环境开关保护的注册仅用于受控邀请，默认关闭。

## 归属模型

- `app_user` 保存账号、BCrypt 密码哈希、角色和账号状态。
- `travel_trip.user_id` 是唯一的业务归属字段。
- Memory 与 `memory_photo` 通过所属 Trip 继承用户归属，不重复保存 `user_id`。
- 管理员只可管理账号；Trip、Memory、照片和封面仍只能由其拥有者访问。

跨账号访问 Trip、Memory、照片、封面或图片时统一按不存在处理。所有上传文件按 `/uploads/users/{userId}/yyyy/MM/{uuid}.ext` 保存；旧的 `/uploads/yyyy/MM/...` 文件仅归初始管理员使用。

## 升级现有数据库

上线前先完成已验证的 MySQL 与 `uploads` 备份。不要在已有数据的环境执行 `sql/init.sql`。

1. 若目标数据库仍是旧单图版本，先执行 `sql/upgrade_20260712_add_memory_photo.sql`；再执行 `sql/upgrade_20260713_add_user_ownership.sql`，最后执行 `sql/upgrade_20260714_add_trip_companion.sql`。每个 SQL 均应在备份完成后单独执行、查看结果并记录。
2. 该 SQL 新建一个禁用的保留管理员账号 `id=1`，并把现有 Trip 归属给它；不会删除或重建现有表。
3. 默认可以使用受控注册完成首次管理员初始化：设置 `APP_REGISTRATION_ENABLED=true` 和一段强随机 `APP_REGISTRATION_INVITE_CODE`，访问 `/register` 后使用该注册码注册。第一个成功注册的账号会激活预留 `id=1` 并成为管理员，后续注册账号均为普通用户。完成邀请后应立即将 `APP_REGISTRATION_ENABLED=false`。Docker Compose 会读取根目录 `.env`；本机直接运行 Spring Boot 时，需要在启动终端设置环境变量：

```powershell
$env:APP_REGISTRATION_ENABLED = 'true'
$env:APP_REGISTRATION_INVITE_CODE = 'replace_with_a_long_random_code'
```

也可以使用本地启动脚本，它会读取根目录 `.env` 的数据库和注册配置：

```powershell
.\scripts\run-local-backend.ps1
```

4. 脚本仍可作为不启用注册时的应急初始化方式。启动 backend 容器后，在 ECS 项目根目录交互执行：

```bash
chmod +x scripts/activate-initial-admin.sh
./scripts/activate-initial-admin.sh
```

脚本会隐藏输入用户名和密码，并二次确认密码。密码通过标准输入进入容器内的后端命令行，不会写入命令参数、仓库或脚本。初始管理员只能激活一次。

Windows 本地 Docker Desktop 环境使用 PowerShell 版本：

```powershell
.\scripts\activate-initial-admin.ps1
```

它同样隐藏密码输入，不会将密码写入 PowerShell 历史、命令参数或临时文件。执行前确认 `backend` 容器已经启动。

不使用 Docker 时，先确保本地 MySQL 已启动并已执行升级 SQL，然后构建 backend：

```powershell
cd travel-memory-server
mvn package -DskipTests
cd ..
.\scripts\activate-initial-admin.ps1 -Local
```

本地模式会隐藏输入数据库密码，并直接运行 `travel-memory-server/target/app.jar` 完成初始化；默认连接本机 `travel_memory` 数据库和 `travel_user`，可用 `-DatasourceUrl`、`-DatasourceUsername` 覆盖。

5. 管理员仍可从“账号管理”创建普通账号。该路径创建的账号首次登录必须修改临时密码，修改前后端都会限制其访问业务接口。

## 会话与生产边界

- Session Cookie 为 `HttpOnly`、`SameSite=Lax`；登录、注册和修改密码时都会更换 Session ID。
- 已启用 CSRF：前端先请求 `/api/auth/csrf`，Axios 自动回传 `X-XSRF-TOKEN`。
- 未登录响应为真实 HTTP `401`，管理员接口无权限为 `403`。
- 连续失败 5 次会锁定账号 15 分钟；登录失败文案不区分用户名、密码、禁用或锁定状态。
- 管理员禁用账号后，已有 Session 会在下一次请求失效；用户修改密码或管理员重置密码后，所有携带旧密码哈希的 Session 也会在下一次请求失效。
- `/uploads/**` 不使用静态目录映射：后端校验登录用户、用户目录、规范化路径、常规文件和每一层目录的符号链接。跨用户图片 URL 统一返回 `404`。

正式公网启用前必须完成 HTTPS。启用 HTTPS 后将 ECS 的 `APP_SESSION_COOKIE_SECURE=true`，并保留现有 Basic Auth 作为迁移期间的外层保护。HTTP 传输尚未加密时不能启用该变量，否则浏览器不会发送 Session Cookie。

完整的首次部署、普通迭代部署、带升级 SQL 的维护窗口和回滚边界见 [阿里云 ECS 部署运行手册](ALIYUN_ECS_DEPLOY.md)。

## 验收建议

使用管理员 A 和普通用户 B 分别创建旅行与照片，验证 A、B 不能通过对方的 Trip ID、Memory ID、照片 ID、封面 URL 或 `/uploads/` URL 访问数据，且跨用户访问统一得到 `404`。管理员账号管理权限不应改变这一结论。再验证：禁用 B 后其现有 Session 返回 `401`；B 修改密码或被重置密码后，另一浏览器中的旧 Session 返回 `401`。
