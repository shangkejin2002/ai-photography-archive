# 用户中心 API 契约

## 通用规则

- 基础路径：`/api`（由 Spring Boot `server.servlet.context-path` 提供）。
- JSON 业务响应：`BaseResponse<T>`，结构为 `{ code, data, message, description }`；`code = 0` 表示成功。
- 登录态：服务端 HttpSession。浏览器保存并在后续同源请求中携带 Session Cookie。
- `UserVO` 字段：`id`、`username`、`userAccount`、`avatarUrl`、`gender`、`userStatus`、`userRole`。不返回 `userPassword`、`phone`、`email`。

## 接口一览

| 功能 | 方法与路径 | 请求 | `data` 类型 | 权限 |
| --- | --- | --- | --- | --- |
| 注册 | `POST /api/user/register` | JSON `RegisterRequest` | `Long`（新用户 ID） | 公开 |
| 登录 | `POST /api/user/login` | JSON `LoginRequest` | `UserVO` | 公开 |
| 当前用户 | `GET /api/user/current` | 无 | `UserVO` | 已登录 |
| 注销 | `POST /api/user/logout` | 无 | `Boolean` | 可匿名调用；使当前 Session 失效 |
| 搜索用户 | `GET /api/user/search` | 可选查询参数 `username` | `UserVO[]` | 管理员 |
| 删除用户 | `POST /api/user/delete` | JSON 纯数字 `Long` | `Boolean` | 管理员 |
| 上传照片 | `POST /api/photo/upload` | `multipart/form-data`，字段 `file` | `PhotoVO` | 已登录 |
| 照片列表 | `GET /api/photo` | 可选 `favorite=true/false` | `PhotoVO[]` | 已登录，仅本人 |
| 照片详情 | `GET /api/photo/{id}` | 路径参数 | `PhotoVO` | 已登录，仅本人 |
| 照片内容 | `GET /api/photo/{id}/content` | `variant=thumbnail/original` | 图片二进制 | 已登录，仅本人 |
| 更新收藏 | `PATCH /api/photo/{id}/favorite` | `{ "favorite": true }` | `PhotoVO` | 已登录，仅本人 |
| 删除照片 | `DELETE /api/photo/{id}` | 无 | `Boolean` | 已登录，仅本人 |
| 补录照片元数据 | `POST /api/photo/metadata/reprocess` | 无 | `Integer`（本次处理数量） | 已登录，仅本人 |
| 确认照片地点 | `PATCH /api/photo/{id}/location` | `{ "locationName": "金阁寺，京都" }` | `PhotoVO` | 已登录，仅本人 |
| AI 条件解析与候选预览 | `POST /api/ai-create/preview` | `{ "prompt": "今年秋天在金阁寺拍摄的照片" }` | `AICreatePreviewVO` | 已登录，仅本人 |
| 创建作品集 | `POST /api/portfolios` | `PortfolioCreateRequest` | `PortfolioVO` | 已登录，仅本人 |
| 作品集列表 | `GET /api/portfolios` | 无 | `PortfolioVO[]` | 已登录，仅本人 |
| 作品集详情 | `GET /api/portfolios/{id}` | 路径参数 | `PortfolioVO` | 已登录，仅本人 |
| 导出作品集 PDF | `GET /api/portfolios/{id}/pdf` | 路径参数 | `application/pdf` | 已登录，仅本人 |
| 更新作品集 | `PATCH /api/portfolios/{id}` | `PortfolioUpdateRequest` | `PortfolioVO` | 已登录，仅本人 |
| 删除作品集 | `DELETE /api/portfolios/{id}` | 无 | `Boolean` | 已登录，仅本人 |
| 摄影习惯报告 | `GET /api/insights` | 无 | `ShootingInsightsVO` | 已登录，仅本人 |

## 请求体

### RegisterRequest

```json
{
  "userAccount": "temporary_user",
  "userPassword": "replace_with_test_password",
  "checkPassword": "replace_with_test_password"
}
```

约束：账号 4～32 位；密码 8～72 位；两次密码一致；账号不可重复。

### LoginRequest

```json
{
  "userAccount": "temporary_user",
  "userPassword": "replace_with_test_password"
}
```

### Delete 请求体

删除接口的 body 是 JSON 数字本身，不是对象：

```json
123
```

不要发送 `{ "id": 123 }`，否则与当前后端 `@RequestBody Long id` 契约不匹配。删除使用 MyBatis-Plus 逻辑删除。

## 状态与错误

HTTP 请求成功到达应用时，业务错误仍通过 `BaseResponse` 的非零 `code` 表示。前端响应拦截器会把非零业务码转换为错误，由页面展示 `description` 或 `message`。管理员接口还会分别检查未登录与无权限场景。

## Photography Library

上传接口当前只接受 JPEG 和 PNG，单文件最大 20MB，并限制解码后的总像素数。服务端检查真实图片内容，不依赖浏览器提供的扩展名或 MIME。

照片文件不存入数据库。数据库保存受保护的内容 URL、storage key、文件信息及后续 EXIF/AI 字段。原图和缩略图均通过需要 Session 的 `/api/photo/{id}/content` 返回，不公开映射本地存储目录。

每个照片业务查询同时限定 `photo.id` 和当前登录用户 `user_id`。其他用户即使知道照片 ID，也只能得到数据不存在响应。

`PhotoVO` 当前主要字段：

- `id`、`originalFilename`、`originalUrl`、`thumbnailUrl`；
- `contentType`、`fileSize`、`width`、`height`；
- `favorite`；
- EXIF：`cameraMake`、`cameraModel`、`lensModel`、`focalLength`、`focalLength35mm`、`aperture`、`shutterSpeed`、`isoValue`、`takenAt`、`orientation`、`exposureBias`、`meteringMode`、`whiteBalance`、`flashStatus`；
- GPS：`gpsLatitude`、`gpsLongitude`，以及预留的 `locationName`；
- 元数据状态：`metadataStatus`、`metadataError`；
- 预留的 AI 分析字段；
- `analysisStatus`、`analysisError`；
- `createTime`、`updateTime`。

`metadataStatus` 取值为 `PENDING`、`EXTRACTED`、`MISSING` 或 `FAILED`。上传时同步提取 EXIF/GPS；旧照片在首次打开详情时自动补录，也可调用批量补录接口处理当前用户的 `PENDING` / `FAILED` 照片。

GPS 坐标不会在上传时自动发送给第三方反向地理编码服务。详情页显示原始坐标，并仅在用户主动选择地图服务后，通过普通坐标链接跳转到 Apple Maps、Google Maps 或 OpenStreetMap；当前没有嵌入地图 SDK，因此不需要配置地图 API key。
# Stage 5 AI 图片理解补充

照片响应在 Stage 4 字段之外新增：

- `aiDescription`: 中文视觉描述。
- `aiTags`: `{ name, type, confidence }[]`，Stage 5 视觉语义索引。
- `analysisStatus`: `PENDING | ANALYZING | COMPLETED | FAILED`。
- `analysisError`: 可供用户处理的失败原因。
- `analysisModel`, `analysisVersion`, `analysisAttempts`, `analyzedAt`: 分析审计信息。

新增接口：

- `POST /photo/{photoId}/analysis`：重试当前用户的一张照片。
- `POST /photo/analysis/pending`：提交当前用户所有待处理或失败照片，返回提交数量。

# Stage 6–7 AI Create 与 Portfolio

`POST /ai-create/preview` 只把用户输入的自然语言和当前图库已有的标签词表发送给 DeepSeek，转换为 `year`、`season`、`location`、`iso`、`favoriteOnly`、相机、镜头、视觉标签、排除标签、横竖构图、是否要求内容多样、排序和数量上限等结构化条件。照片文件、缩略图和完整照片描述不会在 Stage 6 再次发送给 AI。Spring Boot 使用已经保存的 Stage 4 EXIF/GPS/地点与 Stage 5 标签/描述在当前用户范围内查询，最多返回 20 张候选照片。

“多样、丰富、不同内容、diverse、varied”等词只控制候选照片之间的去同质化排序，不作为每张照片必须拥有的内容标签；“景色、风景、landscape”等宽泛条件会与 `nature`、`outdoor`、`garden`、`temple`、`architecture`、`mountain`、`water` 等相关标签进行语义映射。

创建和更新作品集时，`photoIds` 必须包含 1–20 个当前用户拥有的照片 ID；封面也必须来自这一列表。列表顺序即 Portfolio 和 Story 的展示顺序。

Stage 7 没有额外的 AI 调用。普通 Portfolio 详情页使用纵向摄影序列；Story 使用同一组照片和顺序，以 3 秒为周期在悬浮卡牌舞台中自动轮换。

Portfolio 详情页支持导出 A4 PDF。封面包含品牌名、标题、副标题、描述与封面照片；后续页面按作品集顺序展示每张照片，并在数据存在时附带相机、镜头、焦距、快门、光圈、ISO、拍摄时间、地点与 AI 描述。当前版本不写入本地 URL，也不生成二维码；正式域名上线后再补充推广信息。

# Stage 8 Shooting Insights

`GET /insights` 从当前用户的照片、EXIF、人工确认地点以及已经保存的 AI Tags 实时生成统计报告，不会重新调用 DeepSeek。报告包含总照片数、收藏数、EXIF/AI/GPS 覆盖率、最近 12 个拍摄月份、常用相机、镜头、光圈、ISO、焦段分布、热门地点、热门题材与一条本地规则生成的档案观察。

月度统计使用照片的 EXIF `takenAt`，没有拍摄时间的照片仍计入总数但不会进入月度折线。热门地点只使用用户确认的 `locationName`，不会通过 GPS 自动猜测地点。所有统计均限定当前 Session 用户。
