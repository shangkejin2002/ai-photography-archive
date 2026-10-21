# 生产部署说明

本项目采用同源部署：Nginx 提供前端静态文件，并把 `/api/` 原样转发到本机 `127.0.0.1:8080` 的 Spring Boot。这样页面、接口和 Session Cookie 属于同一站点，不需要额外 CORS 配置。

## 1. 准备环境变量

在服务器的进程管理器或秘密管理服务中设置以下变量，不要把真实值写进仓库：

```bash
export DB_URL='jdbc:mysql://数据库地址:3306/user_center?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Tokyo'
export DB_USERNAME='生产数据库账号'
export DB_PASSWORD='生产数据库密码'
export COOKIE_SECURE='true'
```

生产环境应通过 HTTPS 对外服务。只有本地纯 HTTP 验证时，才临时使用 `COOKIE_SECURE=false`。

## 2. 构建后端

```bash
cd backend
mvn clean package
```

构建产物位于 `backend/target/user-center-backend-0.0.1-SNAPSHOT.jar`。启动生产配置：

```bash
java -jar backend/target/user-center-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

正式部署建议用 systemd 等进程管理器托管，并把环境变量放入仅服务账号可读的环境文件。

## 3. 构建前端

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm build
```

把生成的 `frontend/dist` 同步到服务器的 `/var/www/user-center/frontend/dist`，或同步修改 `nginx.conf` 中的 `root`。

## 4. 配置 Nginx

1. 把 `deploy/nginx.conf` 复制到 Nginx 的站点配置目录。
2. 把 `server_name example.com` 替换为真实域名。
3. 配置证书并让 HTTPS server 使用相同的 `root` 和 location 规则。
4. 检查配置后平滑重载 Nginx。
5. 访问 `/api/hello`、页面登录与管理员页面完成验收。

`proxy_pass http://127.0.0.1:8080;` 末尾没有 URI，因此 `/api/...` 会原样传给后端；不要改成会剥离 `/api` 的写法。

## 5. 推荐发布顺序

1. 备份数据库并记录当前后端 JAR、前端静态目录和 Nginx 配置版本。
2. 构建并校验新产物。
3. 如有数据库迁移，先按迁移方案执行并验证。
4. 停止旧后端，启动新后端并验证 `/api/hello`。
5. 原子切换前端静态目录。
6. 检查并平滑重载 Nginx，完成 `docs/acceptance.md`。

## 6. 回滚

1. 将 Nginx 的静态目录切回上一版并平滑重载。
2. 停止新后端，用相同生产环境变量启动上一版 JAR。
3. 若发布包含数据库变更，使用事先验证过的回滚迁移或备份恢复方案；不要临时手工改生产数据。
4. 重新执行健康检查和关键登录链路，并记录回滚原因。

