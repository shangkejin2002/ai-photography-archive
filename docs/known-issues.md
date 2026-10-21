# 已知限制

- 管理员搜索接口一次返回全部匹配用户，后端尚未实现分页；前端表格分页只是在浏览器中分页。
- 前端是 Umi 4 Simple App，通过 Umi 插件组合实现 request、initial-state、model 和 access；目录和导入方式与教程中的完整 `@umijs/max` 脚手架不同。
- 当前仓库只提供 Nginx HTTP 示例骨架，未接入真实域名、TLS 证书、HSTS 或生产秘密管理服务。
- 注册和登录接口尚未实现服务端限流、验证码、失败锁定或完整 CSRF Token 方案；生产上线前应按 `SecurityChecklist.md` 补齐。
- 当前自动化测试覆盖照片上传、非法文件、20MB 限制、EXIF/GPS、用户数据隔离、Portfolio 权限、Insights 隔离、AI Create 条件解析与多样化匹配；浏览器端视觉仍需在目标 Safari/Chrome 版本进行发布前人工复核。
- 数据库结构已经改为 Flyway 版本化迁移；生产发布仍应先备份数据库并在预发布环境验证迁移。
- Story 使用 CSS 3D 而不是 WebGL，现代 Safari、Chrome 和 Edge 均支持；非常旧的浏览器不会纳入支持范围。
- DeepSeek 服务不可用时，已经完成的 EXIF、Library、Portfolio、Story 和 Insights 仍可使用，但新的 Stage 5/6 AI 请求会显示失败或重试状态。
