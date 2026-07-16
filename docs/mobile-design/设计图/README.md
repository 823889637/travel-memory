# Travel Memory 移动端视觉基准

本目录中的 10 张 PNG 是 Travel Memory 后续移动端页面优化的主要视觉基准，基准确认日期为 2026-07-15。

## 使用规则

- 真实业务、当前代码、数据安全和权限边界始终优先。
- 对应页面的布局、视觉层级、留白、卡片比例和移动端交互优先参考本目录 PNG。
- 设计图中不存在于当前项目的字段或功能仅代表视觉占位，不得据此直接新增业务。
- 不写死图片中的旅行、照片数量、头像、地点、时间或统计数据。
- 桌面端继续遵循现有响应式设计，不把移动端画面机械放大。

## 文件索引

| 编号 | 文件 | 中文名称 | 对应页面 |
| --- | --- | --- | --- |
| 01 | `01-trip-list.png` | 我的旅行 | `/trips` |
| 02 | `02-trip-create.png` | 创建旅行 | `/trips/new` |
| 03 | `03-trip-timeline.png` | 旅行时间线 | `/trips/:id` |
| 04 | `04-trip-favorites.png` | 收藏回看 | `/trips/:id/favorites` |
| 05 | `05-trip-journey.png` | 旅程回放 | `/trips/:id/journey` |
| 06 | `06-trip-map.png` | 旅行地图 | `/trips/:id/map` |
| 07 | `07-memory-create.png` | 新增记忆 | `/trips/:id/memories/new` |
| 08 | `08-memory-detail.png` | Memory 独立详情 | `/trips/:tripId/memories/:memoryId` |
| 09 | `09-trip-recap.png` | 单趟旅行回顾 | `/trips/:id/recap` |
| 10 | `10-trip-companions.png` | 同行的人 | `/trips/:id/companions` |

## 业务边界

- `01-trip-list`：统计和旅行内容必须来自真实接口，不写死示例数据。
- `02-trip-create`：当前没有的旅行草稿或创建时封面上传不能仅因设计图出现而新增。
- `03-trip-timeline`：不增加天气等当前数据模型不存在的信息；旅行内导航保留真实页面。
- `04-trip-favorites`：收藏回看仍属于当前 Trip，不扩展为全局收藏中心。
- `05-trip-journey`：只展示用户原话，不自动生成或改写旅行故事。
- `06-trip-map`：Marker 按 Memory 时间顺序编号；连线表示记忆顺序，不代表真实导航路线。
- `07-memory-create`：继续遵守最多 6 张照片、EXIF、地点和同行者的现有规则。
- `08-memory-detail`：分享等尚未实现的入口只作构图参考，不自动开发。
- `09-trip-recap`：只使用真实统计和 Memory 数据，不生成虚假回顾内容。
- `10-trip-companions`：同行者是旅行内称呼标签，不是账号，也不产生内容访问权限。
