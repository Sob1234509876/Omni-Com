# OSC Server（Omni Spring Chat Server）

`projects:osc:osc-server` —— OSA 的伴生聊天服务器：人对人对话（chat）、群聊（room，含称号/等级/权限/打卡）、好友与加群请求、消息（可带附件）、WebSocket 事件总线。

## 构建运行

```bash
./gradlew :projects:osc:osc-server:bootRun
```

- 端口 `8081`（osa-server 占 8080，两者需**同时运行**）
- **硬依赖 osa-server**：启动时立即拉取 OSA 的 `/.well-known/jwks.json`，OSA 不可达则 OSC 拒绝启动；OSA 运行中换钥会经未知 kid 自动重拉，无需重启 OSC
- MongoDB：`mongodb://localhost:27017/chat`
- 附件存储走 osftp-library（FTP），启动时自动创建 `osc-file/` 目录；FTP 连接默认 localhost:21 / osftp / 123456
- OSC 不签发 token、无本地登录、无密钥文件——只验 OSA 发的 JWT

## 身份模型

OSC 用户是 OSA 用户的**懒创建扩展**：

- JWT Filter 用 OSA JWKS 公钥（Ed25519）验签，token 取 `Authorization: Bearer` 头或 query 参数 `token`（后者供 WebSocket 握手用，浏览器原生 WS 无法带头）
- 验出 OSA 用户 id 后查 OSC 用户表，查无则自动创建（OSC 用户 id = OSA 用户 id）
- OSC 用户不重复存 OSA 已有字段（用户名、头像均经 OSA 查询——头像走 OSA `/public/avatar?id=`），自有字段仅 `bio`

## 端点

### /public（公开读，无需认证）

| 端点 | 说明 |
|---|---|
| `GET /public/chat-user?id=` | 按 ID 查 OSC 用户 |
| `GET /public/chat?id=` | 按 ID 查对话 |
| `GET /public/room?id=` | 按 ID 查群聊 |
| `GET /public/room-user?roomId=&userId=` | 查群成员（称号/等级/活跃分/加入时间/禁言） |
| `GET /public/room-users?roomId=` | 列举某群全部成员（群不存在 404 `Room not found`） |
| `GET /public/search/chat-user?username=` | 经 OSA 用户名搜索（先查 OSA 再过滤出有 OSC 账号的，保持 OSA 结果顺序） |
| `GET /public/search/room?name=` | 群名部分串搜索（大小写不敏感，上限 50） |
| `GET /public/message?chatId=｜roomId=&before=&limit=` | 消息分页，新→旧；`before` 为消息 ID 游标；默认/上限 64 条（`osc.server.message-page-size` 可配） |
| `GET /public/resource/file?id=` | 读回上传的文件（按记录的 contentType 返回） |

### /private（OSA JWT 认证后）

| 端点 | 方法 | 说明 |
|---|---|---|
| `/private/update-chat-user` | POST | 改自己的 `bio`（缺省=不改，传空串=清空 bio） |
| `/private/upload-message` | POST | 发消息：`userId`（对人，无对话则自动建对话）/ `chatId` / `roomId` 恰传一个 + `type` + `content` 或 `fileId` |
| `/private/upload-file` | POST | multipart 字段 `file`，存 FTP 后返回文件记录 |
| `/private/chat/delete-chat` | DELETE | 删除对话（双方均可），级联删消息；之后仍可重新发起对话 |
| `/private/room/create-room` | POST | 建群，创建者自动成为成员 |
| `/private/room/update-room` | POST | 改群名/群信息（**仅群主**） |
| `/private/room/update-user` | POST | 改成员 `title` / `admin`（**仅群主**；不能改群主本人） |
| `/private/room/check-in` | POST | 每日打卡（成员即可；每 UTC 日一次，+2 活跃分） |
| `/private/room/mute` | POST | 禁言 `minutes` 分钟（群主/admin；0=解除；不能禁言群主） |
| `/private/room/give-nickname` | POST | 授予称号（**仅群主**） |
| `/private/room/remove-user` | DELETE | 群主/admin 踢人，或传自己的 userId 自愿退群（群主不可退） |
| `/private/room/delete-room` | DELETE | 解散群聊（**仅群主**），级联删成员+消息 |
| `/private/request?requestId=` | GET | 按 ID 查请求（发起人、好友目标、或群主/admin 可见） |
| `/private/request/requests` | GET | 与自己相关的所有请求 ID（发出的、收到的好友请求、所在群的加群请求） |
| `/private/request/accept-request` | POST | 接受请求（好友=双方互加好友链接；加群=群主/admin 处理，拉人进群） |
| `/private/request/reject-request` | POST | 拒绝请求（同 accept 的权限） |
| `/private/request/operation/request-be-friends` | POST | 向 `userId` 发好友请求（重复 pending 400） |
| `/private/request/operation/request-join-room` | POST | 向 `roomId` 发加群请求（重复 pending 400） |
| `/private/friends` | GET | 自己的好友列表（返回 ID） |
| `/private/chats` | GET | 自己参与的对话列表（返回 ID） |
| `/private/rooms` | GET | 自己加入的群聊列表（返回 ID） |

### /private/event-bus（WebSocket）

- 握手走与 REST 相同的安全链：`ws://host:8081/private/event-bus?token=<OSA JWT>`，无 token 403
- 服务器推送 JSON 事件：`{"event": "...", ...载荷}`
- 事件类型：`message`（新消息，推给对话对方/房间除发送者外的全部在线成员，载荷含完整消息）、`request`（请求建立/被接受/被拒绝，推给对方）、`room-update` / `room-user-update` / `room-mute` / `room-remove` / `room-delete`（群变更，推给全体在线成员）、`chat-delete`（推给对方）

## 等级系统（QQ 式）

- 活跃分按群独立累计（`OscApiRoomUser.points`）：每日打卡 +2、房间内发消息 +1（每群每日消息积分上限 2）
- 等级 = 满足 `n² + 4n ≤ points` 的最大 n（QQ 账号活跃天数公式的移植），公式在契约模块 `OscApiRoomUser.levelOf(points)`，客户端可自行复算

## 消息类型

当前为占位集 `TEXT / IMAGE / AUDIO / FILE`（`OscApiMessageType`）：TEXT 必须带 `content`，其余必须带 `fileId`。待业主定稿后替换。

## 响应约定

- 错误 = HTTP 状态码 + 自定义 `description` 响应头 + 空 body（与 osa-server 一致）
- 成功无 body 的写操作返回 200 空 body；实体操作返回实体 JSON
- 配置项（application.yml / `osc.server.*` 前缀）：`message-page-size`（默认 64）、`osa-base-uri`（默认 `http://localhost:8080`）、`file-ftp-path-prefix`（默认 `osc-file`）
