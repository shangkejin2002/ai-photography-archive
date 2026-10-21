# AI Photography Archive — Stage 5 实施说明

## 1. 阶段目标

Stage 5 为每张照片建立一次性的“视觉语义索引”，并与 Stage 4 的 EXIF / GPS 数据共同成为 Stage 6 自然语言创建 Portfolio 的数据基础。

核心原则：

- Stage 4 保存客观数据：相机、镜头、ISO、快门、光圈、拍摄时间、GPS。
- Stage 5 保存视觉语义：场景、主体、视觉元素、颜色、光线、天气、构图、视觉季节、通用标签和中文描述。
- Stage 6 只让语言模型把用户语句转换为结构化筛选条件；照片匹配由 Spring Boot 查询 MySQL 完成，不重复发送整批图片。
- 精确地点来自 GPS / 用户确认；精确季节优先由 EXIF 拍摄时间计算。AI 的地点与季节判断不得覆盖客观数据。

## 2. 数据流

```text
照片上传
  ├─ 同步：保存原图与缩略图、提取 EXIF/GPS、返回 Library
  └─ 异步：缩略图 → DeepSeek Flash → 结构化 JSON
                                  ↓
                    ai_description + tag + photo_tag
                                  ↓
                    Photo Detail 展示 / Library 搜索
                                  ↓
                    Stage 6 Portfolio 条件检索
```

AI 调用只发送缩略图，不发送 GPS、用户身份和原图。API Key 只从后端环境变量读取，前端和数据库均不保存密钥。

## 3. 标签分类

| tag_type | 内容 | 示例 |
| --- | --- | --- |
| `scene` | 场景类型 | `temple`, `landscape` |
| `subject` | 画面主体 | `golden pavilion` |
| `visual_element` | 视觉元素 | `maple leaves`, `reflection` |
| `color` | 主色彩 | `gold`, `blue`, `green` |
| `lighting` | 光线 | `daylight`, `soft light` |
| `weather` | 可见天气 | `clear`, `cloudy` |
| `composition` | 构图 | `natural framing`, `symmetry` |
| `season_visual` | 仅视觉推断的季节 | `autumn` |
| `general` | 其他可搜索标签 | `architecture`, `travel` |

模型同时生成一条简短中文 `ai_description`。标签统一为小写英文，便于查询和去重。

## 4. 状态闭环

- `PENDING`：已入队。
- `ANALYZING`：后台正在调用 DeepSeek。
- `COMPLETED`：描述与标签已保存。
- `FAILED`：未配置 Key、余额不足、鉴权失败、限流或网络/响应异常。

Photo Detail 在 `PENDING / ANALYZING` 时每 3 秒刷新；`FAILED` 时显示可读错误并提供 Retry。Library 的 Analyze archive 可补分析旧照片。

每张照片额外保存：`analysis_model`、`analysis_version`、`analysis_attempts`、`analyzed_at`，用于审计、升级模型和控制重复调用。

## 5. 后端接口

- `POST /api/photo/upload`：上传完成后自动提交异步分析（已配置 Key 时）。
- `POST /api/photo/{photoId}/analysis`：当前用户手动重试单张照片。
- `POST /api/photo/analysis/pending`：提交当前用户所有 `PENDING / FAILED` 照片。
- `GET /api/photo/{photoId}`：返回分析状态、错误、模型信息、描述与分组标签。
- `GET /api/photo`：Library 数据同样包含上述 AI 索引。

所有查询和重试都先校验当前登录用户的照片所有权。

## 6. DeepSeek 配置

DeepSeek 是云端 API，不需要在本机安装模型。后端启动前设置：

```bash
export DEEPSEEK_API_KEY='在 DeepSeek 官方控制台创建的密钥'
export DEEPSEEK_MODEL='deepseek-flash'
```

本地开发也可以运行 `backend/configure-deepseek-local.sh`，以隐藏输入方式将配置保存到项目根目录的 `.env.local`；该文件已被 Git 忽略。之后通过 `backend/run-local.sh` 启动后端即可自动载入配置。

不要把真实密钥写入 Git、聊天记录或前端代码。`.env.example` 只保留变量名和占位值。

## 7. Stage 6 接口边界

Stage 6 接收自然语言，例如“今年秋天在金阁寺拍的 ISO 200 照片”，语言模型只输出类似：

```json
{
  "year": 2026,
  "season": "autumn",
  "location": "金阁寺",
  "iso": 200,
  "visualTags": ["temple", "autumn"],
  "limit": 20
}
```

Spring Boot 再组合 `photo` 的 EXIF/GPS 字段与 `tag / photo_tag` 查询，返回不超过 20 张候选照片。用户确认、删除和排序后才创建 Portfolio。

## 8. 验收标准

- 上传接口不等待 AI 完成即可返回。
- 配置有效 Key 后，状态可从 `PENDING → ANALYZING → COMPLETED`。
- 描述和标签刷新页面后仍存在，不会重复分析已完成照片。
- 单张失败可重试，旧照片可批量补分析。
- 用户只能查看与分析自己的照片。
- Library 可通过 AI 描述或标签搜索当前已加载照片。
- 未配置密钥时，上传仍可使用，页面明确显示配置提示。
