# Travel Memory

Travel Memory 是一个私人旅行记忆产品。它不是旅游攻略、不是社交平台、不是相册瀑布流，也不是 AI 游记生成器。

它的目标是帮助用户在多年以后重新体验一次真实发生过的旅行：看到照片、原话、时间和地点时，能重新想起那一天怎么走过来。

## 项目简介

Travel Memory V1 已完成一条完整的旅行记忆主链路：

1. 创建一次旅行。
2. 上传旅行照片。
3. 写下一句当时想记住的话。
4. 记录时间、地点名称和经纬度。
5. 在 Timeline 查看原始记忆。
6. 在 Journey 按 Day / Stop 重新浏览旅行。
7. 在 Map 查看记忆发生的位置。

图片 V1 保存到本地 `uploads` 目录。上传图片后，后端会尝试解析 EXIF 信息，用于辅助填入拍摄时间和 GPS 坐标。

## 产品定位

Travel Memory 的定位是：

> 保存真实旅行记忆，让未来的自己重新走一遍旅行。

产品不追求公开分享、攻略生产或内容创作，而是保留用户自己留下的真实照片、原话、时间和地点。

## 核心理念

- 真实优先：用户原话不被改写，不用 AI 创造新记忆。
- 记忆优先：保存的是旅行中的记忆，不只是照片文件。
- 照片优先：照片是触发回忆的主要载体。
- 低成本记录：用户只需要上传照片、补一句话、确认时间和地点。
- Journey 是回放，不是地图，也不是时间线。
- Timeline 是原始记录页，可以查看、搜索、编辑、删除 memory。
- Map 是空间视图，用来回答“这些记忆发生在哪里”。

## V1 已完成功能

### 旅行模块

- 创建旅行
- 查询旅行列表
- 查询旅行详情
- 修改旅行
- 删除旅行
- 首页旅行卡片展示封面、标题、目的地、日期和入口

### 记忆模块

- 新增记忆
- 上传照片
- 保存一句话
- 保存记录时间
- 保存地点名称
- 保存经纬度
- 编辑记忆
- 删除记忆
- 收藏 / 取消收藏记忆
- 按旅行 ID 查询 Timeline
- 按内容和地点搜索记忆

### 图片与 EXIF

- 图片保存到本地 `uploads`
- 上传限制：50MB
- 支持常见图片格式，包括 `jpg`、`jpeg`、`png`、`gif`、`webp`、`heic`、`heif`
- 后端尝试解析 EXIF `DateTimeOriginal`
- 后端尝试解析 EXIF GPS
- 前端在用户未手动填写时，自动辅助填入：
  - `recordTime`
  - `latitude`
  - `longitude`
- EXIF 解析失败不影响上传和保存

### Journey V1

- 路由：`/trips/:id/journey`
- 按 `recordTime` 自动分 Day
- Day 内按时间升序排列
- 按相邻 `locationName` 生成 Stop
- 相邻相同地点合并为同一个 Stop
- 地点中断后再次出现，会生成新的 Stop
- 空地点归为“途中留下的记忆”
- Stop 展示地点、时间范围、照片、用户原话和记忆数量
- 多图 Stop 支持主图和缩略图切换
- 支持横图、竖图、方图的自然展示
- 竖图使用同图模糊背景承托
- Journey 页面不提供编辑、删除、新增、搜索等管理操作

### Map V1

- 路由：`/trips/:id/map`
- 使用现有 `getTrip` 和 `getTimeline`
- 展示带经纬度的 memory 点位
- 点击点位展示照片、原话、地点和时间
- 无坐标 memory 不报错，并给出温和提示
- 不包含地图 SDK、路线规划、导航或逆地理编码

## 页面说明

### `/trips`

旅行列表首页。定位为“我的旅行记忆入口”，展示用户曾经走过的旅行。

主要能力：

- 查看旅行列表
- 进入 Journey
- 查看 Timeline
- 进入 Map
- 新建一次旅行
- 删除旅行

### `/trips/new`

创建旅行页。用于创建一次新的旅行。

主要字段：

- 旅行标题
- 目的地
- 开始日期
- 结束日期
- 描述
- 封面地址

开始日期和结束日期有基础校验关系。

### `/trips/:id`

TripTimeline 页面，定位为“这次旅行的原始记忆记录页”。

主要能力：

- 按 Day 查看原始 memory
- 搜索一句话或地点
- 编辑 memory
- 删除 memory
- 收藏 memory
- 进入 Journey
- 进入 Map
- 新增记忆

### `/trips/:id/memories/new`

MemoryCreate 页面，定位为“留下这一刻”。

主要能力：

- 上传照片并预览
- 接收后端返回的图片 metadata
- 自动辅助填入拍摄时间和经纬度
- 写一句真实原话
- 确认记录时间
- 填写地点名称
- 在更多位置信息中填写经纬度

### `/trips/:tripId/memories/:memoryId/edit`

MemoryEdit 页面，定位为“修正这段记忆”。

主要能力：

- 读取 memory 详情
- 修改照片
- 修改一句话
- 修改记录时间
- 修改地点名称
- 修改经纬度
- 保存后返回旅行 Timeline

### `/trips/:id/journey`

Journey 页面，定位为“重新走一遍旅行”。

主要能力：

- 按 Day 浏览
- 按 Stop 重新经过地点
- 展示照片和用户原话
- 不提供管理操作

### `/trips/:id/map`

TripMap 页面，定位为“记忆发生在哪里”。

主要能力：

- 展示有坐标的记忆点
- 点击点位查看记忆摘要
- 提示无坐标 memory 暂不显示

## 技术栈

### 前端

- Vue 3
- Vite
- Vue Router
- Axios

### 后端

- Spring Boot 3
- Java 17
- MyBatis Plus
- MySQL
- metadata-extractor

### 存储

- MySQL 保存旅行和记忆数据
- 本地 `uploads` 保存图片文件

## 项目结构

```text
TravelMemory/
  docs/                         产品、愿景、UI、规则与路线文档
  scripts/                      本地启动脚本
  sql/                          数据库初始化和升级 SQL
  uploads/                      本地图片上传目录
  travel-memory-server/         Spring Boot 后端
    src/main/java/com/travelmemory/
      common/                   Result、文件对象等公共类
      config/                   Web MVC 静态资源配置
      controller/               Trip / Memory 接口
      dto/                      上传结果 DTO
      entity/                   TravelTrip / TravelMemory
      exception/                统一异常处理
      mapper/                   MyBatis Plus Mapper
      service/                  业务服务
      util/                     图片 EXIF 解析工具
      vo/                       列表展示 VO
    src/main/resources/
      application.yml           后端配置
  travel-memory-web/            Vue 3 前端
    src/api/                    Axios 请求封装
    src/router/                 前端路由
    src/styles/                 全局样式
    src/views/                  页面组件
```

## 启动方式

### 1. 准备数据库

默认数据库配置见：

```text
travel-memory-server/src/main/resources/application.yml
```

默认连接信息：

```yaml
database: travel_memory
username: travel_user
password: Travel@123456
```

初始化 SQL：

```powershell
mysql -u travel_user -p travel_memory < sql/init.sql
mysql -u travel_user -p travel_memory < sql/upgrade_20260703_add_memory_favorite.sql
```

如果本机使用项目内脚本启动 MySQL，可参考：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-mysql.ps1
```

### 2. 启动后端

```powershell
cd travel-memory-server
mvn spring-boot:run
```

默认端口：

```text
http://localhost:8080
```

### 3. 启动前端

```powershell
cd travel-memory-web
npm install
npm run dev
```

默认端口：

```text
http://localhost:5173
```

### 4. 使用脚本重启

项目提供了 PowerShell 脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/restart-backend.ps1
powershell -ExecutionPolicy Bypass -File scripts/restart-frontend.ps1
powershell -ExecutionPolicy Bypass -File scripts/restart-all.ps1
```

## 配置说明

### 后端配置

核心配置文件：

```text
travel-memory-server/src/main/resources/application.yml
```

重要配置：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/travel_memory
    username: travel_user
    password: Travel@123456
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 55MB

app:
  upload:
    dir: ../uploads
```

### 图片访问

后端通过 `WebMvcConfig` 暴露本地上传目录。图片第一版保存在：

```text
uploads/
```

### 前端请求

前端 API 位于：

```text
travel-memory-web/src/api/
```

主要接口前缀：

- `/api/trips`
- `/api/memories`

## 后续计划

V1 优先保证核心链路稳定。后续可考虑：

- 更完整的地图能力，但不做复杂导航
- 更好的地点名称补全或逆地理编码
- 最佳回忆页面
- 去年今天
- 年度旅行回顾
- 更完整的移动端体验
- 云端图片存储
- 仅在必要时引入 AI，且 AI 只能帮助组织记忆，不能替用户创造记忆

暂不做：

- 社交
- 点赞评论
- 排行榜
- 攻略推荐
- AI 聊天
- 短视频
- 直播
- 复杂推荐算法

## Codex 开发注意事项

1. 开发前先阅读：
   - `docs/PRODUCT.md`
   - `docs/VISION.md`
   - `docs/UI_GUIDELINE.md`
   - `docs/RULES.md`
   - `docs/ROADMAP.md`

2. 不要偏离产品定位：
   - Travel Memory 不只是帮助用户记录旅行。
   - Travel Memory 更重要的是帮助用户多年以后重新体验一次真实发生过的旅行。

3. 不要随意改写用户原始内容：
   - `content` 是用户原话。
   - Journey 和 Timeline 都必须展示真实内容。
   - AI 可以帮助组织，不能替用户创造记忆。

4. 优先保持 V1 稳定：
   - 不随意修改数据库结构。
   - 不随意修改接口返回结构。
   - 不随意新增依赖。
   - 不重构整个项目。

5. 页面设计原则：
   - 温暖、克制、私人记忆感。
   - 照片和原话优先。
   - 管理操作弱化。
   - 不要做成后台管理系统。
   - 移动端优先。

6. 开发验证：
   - 前端修改后运行 `npm run build`。
   - 后端修改后运行 `mvn test` 或至少完成编译。
   - 上传、Timeline、Journey、Map 是主链路，必须回归检查。

7. 文件边界：
   - 前端页面在 `travel-memory-web/src/views/`
   - 前端 API 在 `travel-memory-web/src/api/`
   - 后端接口在 `travel-memory-server/src/main/java/com/travelmemory/controller/`
   - 数据库脚本在 `sql/`

## V1 状态

Travel Memory V1 已具备可运行的核心体验：

从创建旅行，到留下记忆，再到 Timeline 查看、Journey 回放、Map 空间查看，已经形成完整闭环。

下一阶段开发应以稳定 V1 为前提，小步扩展，而不是大范围重构。
