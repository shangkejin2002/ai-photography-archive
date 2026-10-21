# AI Photography Archive — Stage 9

## 完成范围

Stage 9 统一了最终视觉和边界状态，并把 Story 从平面卡牌扇形重构为 CSS 3D 圆柱画廊。核心功能不依赖 WebGL，不增加 AI 调用，也不修改现有照片数据。

## Story 3D 圆柱画廊

- 照片按 `360° / 照片数` 固定在不可见圆柱表面。
- 圆柱使用累计旋转角度，跨越第一张/最后一张以及连续快速操作时不会反向跳跃。
- 当前照片保持正面、完整比例和最高亮度；相邻照片沿圆周进入景深。
- 3 秒自动播放，支持暂停、前后按钮、点击照片、左右方向键、空格、Esc 和手机左右滑动。
- 1 张照片隐藏导航并停止自动播放；2、3 张和最多 20 张使用同一套稳定几何逻辑。
- 横图、竖图、方图根据真实宽高比计算卡片尺寸，使用 `object-fit: contain` 避免裁切主体。
- 图片加载失败显示占位卡；接口失败和空 Portfolio 显示可返回的完整状态页。
- `prefers-reduced-motion` 下取消 3D 位移，退化为当前照片淡入淡出。
- 使用 `-webkit-backface-visibility`、`-webkit-backdrop-filter` 等兼容声明支持现代 Safari。

## 统一状态与边界

- Library：空图库、空搜索、上传队列、非法文件、超过 20MB、上传失败与重试。
- Photo Detail：无 EXIF、无镜头、无 GPS、AI Pending / Analyzing / Completed / Failed 与重试。
- AI Create：解析中、无候选、失败提示、标签词表对齐、排除条件、构图条件与多样化选片。
- Portfolios / Story：空列表、空作品集、资源失败、单张照片和混合比例照片。
- Insights：加载骨架、空图库、接口失败重试、无拍摄时间、无地点和无器材数据。
- 所有后端照片、Portfolio、Insights 查询均限定当前 Session 用户。

## 自动与浏览器验收

- 后端 `mvn test`：照片完整流程、所有权隔离、非法图片、超过 20MB、EXIF/GPS、AI 条件与多样化匹配。
- 前端 `npm run build`：TypeScript 与生产构建通过。
- 1280×720：Story 与 Insights 无页面溢出。
- 390×844：Story 无横向/纵向溢出，竖图完整显示。
- 1 / 2 / 3 / 5 张照片：始终只有一张 `aria-current`，导航和计时状态正确。
- 连续快速点击：累计旋转角度保持为单步角度的整数倍，不发生回弹。

## 发布前人工复核

在目标版本 Safari 与 Chrome 各执行一次 README 的 Demo 验收流程；重点确认系统字体差异、音频首次播放权限、触控滑动手感和减少动态效果设置。
