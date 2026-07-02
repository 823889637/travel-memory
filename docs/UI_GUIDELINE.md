Travel Memory



不是帮助用户记录旅行。



而是帮助用户重新体验一次旅行。请先阅读 PRODUCT.md、VISION.md、UI\_GUIDELINE.md，然后设计新的页面，不要直接修改代码# UI Guideline



Version: 1.0



\---



\# Design Goal



Travel Memory 不是一个旅游攻略应用。



不是相册管理软件。



不是后台管理系统。



Travel Memory 是一本旅行回忆录。



用户打开页面以后，



应该感觉：



> 我正在重新体验一次旅行。



而不是：



> 我正在管理旅行数据。



所有 UI 设计都必须围绕：



\*\*旅行\*\*

\*\*照片\*\*

\*\*故事\*\*

\*\*回忆\*\*



展开。



\---



\# Design Keywords



整个产品应体现：



\- 简洁（Simple）

\- 温暖（Warm）

\- 高级（Elegant）

\- 沉浸（Immersive）

\- 回忆（Memory）

\- 旅行（Travel）

\- 照片优先（Photo First）

\- 留白（Whitespace）



避免：



\- 后台管理系统

\- ERP

\- Admin Dashboard

\- 复杂科技风

\- 花哨动画



\---



\# Design Principles



所有页面必须遵循：



\## 1.



照片优先



不是按钮优先。



不是表格优先。



不是文字优先。



照片是旅行记忆最重要的载体。



\---



\## 2.



减少操作。



增加浏览。



用户应该：



看



滑



回忆



而不是：



点



配置



设置



\---



\## 3.



少按钮。



一个页面不要出现大量按钮。



删除、编辑等操作尽量放入：



More Menu（···）



不要成为视觉重点。



\---



\## 4.



时间线比列表更重要。



Travel Memory 的核心：



Timeline



不是：



List



所有记忆应尽量以：



旅行时间线



进行展示。



\---



\## 5.



移动端优先。



页面首先适配：



手机。



其次：



桌面浏览器。



不要为了 PC 做复杂布局。



\---



\# Visual Style



\## Color



Primary



\#3B82F6



Secondary



\#64748B



Background



\#F8FAFC



Card



\#FFFFFF



Border



\#E5E7EB



Danger



\#EF4444



不要超过：



3 个主题颜色。



不要高饱和度。



\---



\# Typography



Title



24px



Bold



Subtitle



18px



SemiBold



Body



16px



Regular



Description



14px



Regular



Time



13px



Medium



Caption



12px



Regular



字体层级必须明显。



\---



\# Radius



Card



16px



Button



10px



Input



10px



Image



16px



整个产品保持统一。



\---



\# Shadow



仅使用轻阴影。



不要使用：



重阴影。



不要使用：



玻璃拟态。



不要使用：



复杂渐变。



\---



\# Spacing



页面：



24px



卡片：



20px



组件：



16px



元素：



8px



保持统一节奏。



\---



\# Layout



页面结构统一：



Header



↓



Summary



↓



Timeline



↓



Floating Action Button



不要每个页面都有不同布局。



\---



\# Header



Header 应包括：



旅行封面



旅行标题



旅行日期



旅行地点



旅行统计



例如：



照片数量



旅行天数



记忆数量



Header 应具有：



旅行海报感。



不要像后台详情页。



\---



\# Timeline



Timeline 是产品核心。



左侧：



时间



中间：



时间线



右侧：



Memory Card



例如：



09:20



●────────



东京塔



第一次来到东京。



📷



↓



13:40



●────────



寿司店



终于吃到了。



📷



Timeline 应具有连续性。



\---



\# Memory Card



每张卡片建议包含：



照片



时间



地点



一句话



不要展示大量字段。



不要展示数据库信息。



不要展示 ID。



不要展示经纬度。



经纬度属于地图功能。



\---



\# Image



图片：



统一比例。



建议：



4:3



或



3:2



图片优先展示。



不要缩略得太小。



\---



\# Button



Primary



蓝色



Secondary



灰色



Danger



红色



页面按钮数量：



越少越好。



删除：



不要直接展示。



放入：



More Menu



\---



\# Empty State



不要出现：



暂无数据



建议：



加入插画。



例如：



📷



还没有留下旅行记忆。



去记录今天的故事吧。



\---



\# Loading



使用：



Skeleton



不要：



Loading...



文字。



\---



\# Animation



仅允许：



Fade



Slide



不要：



复杂动画。



不要：



炫酷特效。



\---



\# Responsive



设计宽度：



Mobile First



375px



兼容：



768px



Desktop



仅作适配。



\---



\# Accessibility



颜色对比符合 WCAG。



按钮高度：



至少



44px。



图片必须支持：



Alt。



\---



\# Icon



建议：



Lucide Icons



或



Heroicons。



保持统一。



\---



\# Design Inspiration



参考：



Apple Photos



Google Photos



Airbnb



Instagram



Notion



不要参考：



AdminLTE



Element 后台



ERP



OA



CRM



\---



\# Before Every UI Change



Codex 修改任何页面之前必须检查：



1\.



是否更像旅行回忆？



2\.



是否减少了操作？



3\.



是否突出照片？



4\.



是否突出时间线？



5\.



是否符合 Mobile First？



6\.



是否符合产品定位？



如果答案不是：



YES。



请重新设计。



\---



\# Never Do



不要：



增加复杂动画。



不要：



增加后台管理元素。



不要：



使用大量表格。



不要：



为了美观增加复杂交互。



不要：



让按钮比照片更醒目。



不要：



让功能比故事更重要。



\---



\# Final Principle



Travel Memory



不是帮助用户记录旅行。



而是帮助用户多年以后重新体验一次旅行。



保存的是：



旅行中的记忆。



不是：



旅行中的数据。

# Emotion First

任何页面设计之前，

请思考：

用户看到这个页面以后，

第一感觉应该是：

"我想起了那次旅行。"

而不是：

"我在管理旅行数据。"

如果页面更像管理系统，

请重新设计。
