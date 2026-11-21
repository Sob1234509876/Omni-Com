# OSA — Omni Spring Authorization Server（2.1a）

基于 Spring Boot 3.5.6（Java 17、JPMS 模块 `osl.projects.osa.server`）的授权服务器：用户注册 / JWT 登录 / 资料与头像管理。契约与实体来自 [`:projects:osa:osa-api`](../osa-api)，头像存储构建在 [`:projects:osftp:osftp-library`](../../osftp/osftp-library) 之上。

## 构建与运行

```bash
./gradlew :projects:osa:osa-server:build
./gradlew :projects:osa:osa-server:bootRun
```

- 默认端口 **8080**；MongoDB 地址走 `spring.data.mongodb.uri`（默认 `mongodb://localhost:27017/user`）。
- **SMTP 未配置时**（默认），注册验证码打印到服务器日志（dev 模式）；配置 `spring.mail.*` 后改为真实发信。
- 首次运行会在工作目录生成 Ed25519 密钥文件 `osa_generated_key.txt`（已被 `.gitignore` 覆盖，勿提交勿泄露）。

## 端点一览

### 匿名（`/anonymous/**`）

| Method | Path | 参数 | 成功返回 |
|--------|------|------|----------|
| POST | `/anonymous/register/email` | `email` | 发送验证码（200） |
| POST | `/anonymous/register` | `email`、`code`、`username`、`password`、`authorities` | 新用户 **id**（Long） |
| POST | `/anonymous/login` | `id`、`password` | **JWT** 字符串 |

### 公开（`/public/**` 与 `/.well-known/**`）

| Method | Path | 参数 | 返回 |
|--------|------|------|------|
| GET | `/public/profile` | `?id=` | 脱敏用户 JSON |
| GET | `/public/avatar` | `?id=` | 头像二进制（PNG） |
| GET | `/public/user/search` | `?username=` | 脱敏用户数组（用户名包含式模糊匹配，最多 50 条） |
| GET | `/.well-known/jwks.json` | — | JWT 验签公钥（标准 JWKS，`Cache-Control: max-age=3600`） |

### 认证（`/private/**`，请求头 `Authorization: Bearer <JWT>`）

| Method | Path | 参数 | 说明 |
|--------|------|------|------|
| GET | `/private/whoami` | — | 当前令牌身份 + 自己的脱敏资料 |
| POST | `/private/profile/update` | `body.username`、`body.authorities`；`changeSensitiveField=true` 时另需 `body.password`、`body.email`（可选）与 `password`（旧密码） | `changeSensitiveField` 缺省 `false`：只改非敏感字段、不验密码；`true`：可改密码/邮箱，必须通过旧密码校验 |
| POST | `/private/avatar/upload` | multipart 字段 `avatar` | 上传头像 |
| POST | `/private/avatar/update` | multipart 字段 `avatar` | 更换头像 |
| DELETE | `/private/profile/delete` | `password` | 注销账号（需旧密码） |
| DELETE | `/private/avatar/delete` | — | 删除头像 |

其他：`OPTIONS /**` 与 `/error` 放行；`/admin/**` 预留 `ROLE_ADMIN`（暂无 controller）。

## 认证机制

- JWT 由 **Ed25519** 签名（jjwt），头部携带 `kid`；公钥经 `/.well-known/jwks.json` 发布，其他服务只需公钥即可验签、无法伪造。
- 登录凭据是 **id + password**（id 即身份），username 类似 QQ 昵称、**不唯一**，仅作展示与搜索。
- 默认有效期 24 小时（`osa.server.jwt-token-expiration`）。

## 注册流程（两阶段）

```bash
# 1. 提交邮箱，服务器发送验证码（dev 模式下码在服务器日志里）
curl -X POST http://localhost:8080/anonymous/register/email -F "email=you@example.com"

# 2. 带验证码完成注册，返回新用户 id（登录与后续操作都用这个 id）
curl -X POST http://localhost:8080/anonymous/register \
  -F "email=you@example.com" -F "code=150959" \
  -F "username=myname" -F "password=secret" -F "authorities="
```

## 响应约定

- **错误**走 HTTP 状态码 + `description` 响应头（如 `400` + `description: Wrong verification code`），不在响应体里。
- 公开返回的用户对象一律脱敏：`password` 置空、`email` 遮盖为 `m***@example.com` 形式、`authorities` 清空。
- 验证码 10 分钟过期、错 5 次作废、按用途（`purpose`）绑定——注册码不能用于其他用途。

## 安全规则

`/anonymous/**`、`/public/**`、`/.well-known/**`、`/error` 匿名可访问；`OPTIONS /**` 全放行（CORS 预检，来源/方法/头全通配、不带凭据）；其余路径需有效 JWT。
