# AI Photography Archive - Stage 4 设计与实现说明

> 文档状态：已实现首版  
> 更新日期：2026-10-02  
> 前置基线：`docs/ai-photography-archive-stage-2-3.md`

---

## 1. 阶段目标

让 Photography Archive 在照片上传时直接读取文件自身携带的 EXIF 与 GPS 元数据，并在 Photo Detail 中以摄影作品档案的方式呈现。

核心流程：

```text
上传 JPEG / PNG
  → 校验真实图片内容
  → 读取 EXIF / GPS
  → 按 EXIF Orientation 生成方向正确的缩略图
  → 保存结构化摄影参数
  → Photo Detail 展示参数与地点
```

EXIF 读取不会阻断上传：没有 EXIF 的图片仍可正常进入 Library，并明确显示“无可读取的摄影参数”。

---

## 2. 已实现范围

### 2.1 摄影参数提取

后端使用 `metadata-extractor` 在上传阶段读取原始文件，支持保存：

- 相机厂商与型号；
- 镜头型号；
- 实际焦距与 35mm 等效焦距；
- 光圈；
- 快门；
- ISO；
- 原始拍摄时间；
- 曝光补偿；
- 测光模式；
- 白平衡；
- 闪光灯状态；
- EXIF Orientation；
- GPS 纬度、经度。

相机和镜头序列号不提取、不保存，避免把没有展示价值的设备唯一标识写入档案。

### 2.2 图片方向处理

原图文件保持不变，缩略图生成时处理 EXIF Orientation 1～8，包括旋转和镜像场景。数据库中的 `width`、`height` 保存用户实际看到的方向尺寸。

### 2.3 元数据状态

每张照片记录独立的读取状态：

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 旧数据尚未执行 Stage 4 提取 |
| `EXTRACTED` | 至少提取到一项主要 EXIF 或 GPS 信息 |
| `MISSING` | 文件有效，但不含可用的 EXIF/GPS |
| `FAILED` | 文件元数据读取失败 |

读取失败只影响元数据，不影响已经成功保存的照片本身。

### 2.4 旧照片补录

- 进入旧照片的 Photo Detail 时，如果状态为 `PENDING`，后端自动从受保护的原图重新提取一次，并重建方向正确的缩略图；
- `POST /api/photo/metadata/reprocess` 可为当前用户批量重试 `PENDING` 和 `FAILED` 的照片；
- 补录查询和更新始终限定当前登录用户。

### 2.5 Photo Detail 展示

页面包含：

- 焦距、光圈、快门、ISO 四项主要曝光数据；
- 相机、镜头、35mm 等效焦距、拍摄时间；
- 曝光补偿、测光、白平衡、闪光灯；
- GPS 坐标；
- 由用户主动选择的 Apple Maps、Google Maps 与 OpenStreetMap 地图入口；
- 缺失或失败时的清晰降级提示。

---

## 3. 数据库变更

Stage 4 新增 Flyway migration：

```text
V4__add_photo_exif_metadata.sql
```

在既有 `photo` 表上新增：

| 字段 | 用途 |
| --- | --- |
| `orientation` | EXIF 方向值 1～8 |
| `focal_length_35mm` | 35mm 等效焦距 |
| `exposure_bias` | 曝光补偿 EV |
| `metering_mode` | 测光模式描述 |
| `white_balance` | 白平衡描述 |
| `flash_status` | 闪光灯状态描述 |
| `metadata_status` | 元数据处理状态 |
| `metadata_error` | 不含内部堆栈的用户可读错误 |

Stage 2 已预留并在本阶段正式写入的字段包括 `camera_make`、`camera_model`、`lens_model`、`focal_length`、`aperture`、`shutter_speed`、`iso_value`、`taken_at`、`gps_latitude` 与 `gps_longitude`。

---

## 4. GPS 与地点名称边界

照片文件通常只保存经纬度，不保存“金阁寺”或“京都”这样的可读地点名称。将坐标转换为地点名称需要反向地理编码服务。

当前版本采取隐私优先策略：

1. GPS 坐标只保存在本项目数据库中；
2. Photo Detail 直接显示坐标；
3. 只有用户点击 `View on map` 并选择地图服务后，浏览器才会把坐标发送给 Apple、Google 或 OpenStreetMap；
4. 后端不会在上传时自动把每张照片的精确位置发送给第三方；
5. `location_name` 继续保留，等确认地图供应商、隐私提示、调用额度与缓存策略后再写入。

因此，本阶段已经完成 GPS 的读取、保存和可用展示；自动显示“Kyoto / Kinkaku-ji nearby”属于需要单独确认的联网地点解析增强项。

---

## 5. 本次真实样本验证

使用用户提供的金阁寺 JPEG 作为回归样本，成功读取：

| 项目 | 读取结果 |
| --- | --- |
| 相机 | OLYMPUS CORPORATION E-P7 |
| 镜头 | LUMIX G VARIO 35-100/F4.0-5.6 |
| 焦距 | 35mm |
| 光圈 | f/5.6 |
| 快门 | 1/640s |
| ISO | 200 |
| 拍摄时间 | 2026-09-22 10:47:30 |
| 纬度 | 35.0395139 N |
| 经度 | 135.7284417 E |
| Orientation | 1 |

坐标与京都金阁寺区域一致。自动化测试内保存的是缩小后的 JPEG 测试夹具，但保留了这张样本的 EXIF/GPS，用于防止后续代码修改破坏真实相机元数据解析。

---

## 6. 用户隔离与安全

- 元数据随所属照片一起受 `user_id` 限制；
- 原图仍通过需要 Session 的内容接口读取；
- 补录接口只能处理当前用户的照片；
- 不信任扩展名或浏览器 MIME，仍先验证实际图片内容；
- 元数据异常不会把解析器内部错误或文件系统路径返回给前端；
- 不提取设备序列号；
- 不自动调用第三方地理编码服务；地图链接不使用嵌入式地图 SDK，也不需要项目配置地图 API key。

---

## 7. 验收标准

- 带 EXIF 的 JPEG 上传后，详情页显示相机、镜头和曝光参数；
- 带 GPS 的照片显示精确坐标和地图入口；
- 无 EXIF 的 PNG/JPEG 仍可上传并显示缺失提示；
- EXIF Orientation 不为 1 时，缩略图方向正确；
- 一个用户不能读取或补录另一个用户的照片；
- 旧照片可在详情页自动补录，或由批量接口重试；
- Flyway 可从空数据库依次迁移到 V4；
- 后端测试、TypeScript 检查和前端生产构建全部通过。

---

## 8. Stage 5 交接点

Stage 5 可在当前照片记录之上异步增加 AI 图片理解：

- 上传成功后立即可用，不等待 AI；
- `analysis_status` 进入 `PENDING → ANALYZING → COMPLETED / FAILED`；
- 生成 scene、season、subjects、colors、tags、description；
- 分析结果持久化，搜索时不重复分析同一照片；
- EXIF/GPS 属于客观数据，AI 结果属于推断数据，两者在 UI 与数据库层保持区分。
