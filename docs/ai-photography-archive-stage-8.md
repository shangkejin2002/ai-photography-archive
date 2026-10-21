# AI Photography Archive — Stage 8

## 阶段产物

Shooting Insights 是与 Library、AI Create 和 Portfolios 同级的个人摄影习惯报告。桌面端在 `1280 × 720` 及以上采用无需纵向滚动的单屏六区块布局；窄屏为了保持可读性自动改为两列或单列并允许滚动。

## 数据来源

- Stage 4：拍摄时间、相机、镜头、焦段、光圈、ISO、GPS 与用户确认地点。
- Stage 5：已经保存的 subject、scene 与 general 标签。
- 用户数据：照片总数和收藏状态。

打开 Insights 不会上传照片，不会调用 DeepSeek，也不会产生 AI 费用。档案观察由现有统计结果在本地按规则生成。

## 统计规则

- EXIF 覆盖率：至少存在有效元数据状态或核心摄影参数。
- AI 覆盖率：`analysisStatus = COMPLETED`。
- GPS 覆盖率：经纬度同时存在。
- 月度拍摄：以最新一张有 `takenAt` 的照片为结束月，展示连续 12 个月。
- 焦段：优先 35mm 等效焦距，缺失时使用实际焦距；区间为 `<24`、`24–35`、`36–50`、`51–85`、`86–135`、`135+`。
- 热门地点：只统计非空 `locationName`。
- 热门题材：统计当前用户、未删除照片关联的 subject、scene 与 general 标签。

## 页面组成

1. 顶部总照片、EXIF 覆盖率和 AI 覆盖率。
2. Shooting rhythm：最近 12 个月折线与浅灰渐变面积。
3. Visual language：灰阶 AI 题材圆盘。
4. Archive observation：本地规则生成的一句话总结。
5. Focal language：六档圆角柱状分布。
6. Equipment：常用相机、镜头、光圈和 ISO。
7. Places & archive health：地点与 EXIF/GPS/AI Tags 覆盖率。

## 状态处理

- 空图库显示进入 Library 的入口。
- 没有拍摄时间时保留图表区域并说明不可用。
- 缺失相机、镜头、光圈或 ISO 时分别显示 unavailable。
- 无地点时不显示虚构地点。
- API 失败显示原因和 Retry。
- Loading 使用与最终布局相同的六区块骨架，避免页面跳动。

## 验收

- `1280 × 720` 下 `scrollHeight` 等于视口高度。
- 所有图表只使用黑白灰，卡片具有细边框、柔和圆角和克制阴影。
- 当前用户无法从报告中看到其他用户的照片、地点或设备。
- 空数据、无 EXIF、无 GPS、AI Failed 不造成接口或页面错误。
- 打开 Insights 不产生 DeepSeek 请求。
