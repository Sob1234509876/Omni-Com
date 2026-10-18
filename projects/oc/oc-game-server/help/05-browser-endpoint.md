# 05 · 浏览器端点

> `--http-port` 上的一个监听器：既把构建好的 web client 当静态文件发出去，又把 `/ws` 升级成 WebSocket，
> 让浏览器用和加密 TCP 完全相同的命令集说话。

## 这一篇讲什么

- 这个端点是什么、为什么存在，以及它和加密 TCP 的关系。
- 两套版本机制：TCP 握手里的版本字节，和浏览器信道上的 `HELLO 1` / `OK OC/1`。
- 这条端点上的命令集，以及为什么它和 TCP 是同一套语法。
- 来源（`Origin`）规则：同源、白名单、空白名单的含义。
- 静态文件路由：方法、路径穿越、SPA 回退、缓存与缺失的静态根。
- 插件资源路由：`/plugin-assets/...` 与静态根的关系（细节在协议文档）。
- 世界快照路由：`/world/snapshot` 的 JSON 形状、503 规则与 `--world` / `--seed`。
- WebSocket 帧规则、关闭码、空闲超时（`--ws-idle`）、服务端 ping 心跳，以及二进制帧的处理。
- 常见坑：工作目录、反向代理、`--http-port 0`、关闭世界。

## 它是什么

`OcGameServer` 在 `--http-port`（默认 `25567`）上启动一个 `HttpWebServer`，它做四件事，前两件是主线：

| 路由 | 做什么 |
| --- | --- |
| `GET` / `HEAD` 其它路径 | 从 `--static-root`（默认 `web-client/dist`）发静态文件，也就是构建好的浏览器客户端 |
| `/ws` 的 WebSocket 升级 | 升级成 RFC 6455 WebSocket，之后一个文本帧就是一条命令行 |
| `/plugin-assets/...`（端点持有 `PluginLoader` 时） | 从插件 jar 的 `assets/` 发资源，见下面的「插件资源路由」 |
| `GET /world/snapshot` | 把 `--world` 启动的服务器世界当前 tick 的 JSON 快照发给浏览器，见下面的「世界快照」 |

它存在的原因很直接：**浏览器不会说那条加密 TCP 信道**。TCP 信道在握手阶段自己实现 X25519、HKDF-SHA256 与
AES-256-GCM（见 [02](02-accounts-and-encryption.md)），浏览器没有对应的 API；WebSocket 则是浏览器原生就有的。
于是服务器把同一条命令语法搬到文本帧上，页面既能由这个服务器自己发出来，也能连回同一个服务器。

这不是第二个游戏服务器，而是一个适配器：它不自己保存用户记录，也不解释命令里的游戏内容。`REGISTER` / `LOGIN`
走的是同一个 `AuthService`，`REQ` 走的是同一个 `RequestRegistry`，命令语法两边都委托给 `CommandDispatcher`。
唯一的游戏状态读取是 `/world/snapshot`，它直接转交 `server.world.WorldHost`，HTTP 层只负责加响应头。

一个必须说清楚的前提：**这条端点是明文 HTTP/WS，不是加密 TCP 的替代品**。它默认绑 `0.0.0.0`，适合本机开发；
把它暴露到不可信网络、或在上面输入真实密码之前，先套一层 HTTPS 反向代理或 SSH 端口转发。`oc-debug` 的
`engine-info` 还依赖 `crypto.subtle`，浏览器只在安全上下文里提供它（见协议文档的“浏览器实现提示”）。

端点是**可选**的：`--http-port 0` 让 `OcGameServer` 根本不创建 `HttpWebServer`，一个只跑 TCP 的服务器行为与
这个端点出现之前完全一样。注意 `0` 在这里是“关闭”，不是 `--port 0` 的“让操作系统挑一个空闲端口”；
`HttpWebServer` 自己把 `0` 解释为后者，是 `OcGameServer` 给了它第二个含义。WebSocket 的空闲超时默认
`300` 秒，`--ws-idle N` 可以改成任意正整数秒；端点同时每 25 秒发一次 ping，浏览器自动回 pong，所以
纯空闲页面不会被旧的 60 秒读超时误杀。

启动顺序是：插件先加载 → 构造 `WorldHost`（不启动线程）→ TCP socket 绑定 → 启动世界的 daemon 线程 →
（如果端口不是 `0`）浏览器端点绑定 → 定时器。浏览器端点绑定失败时，`OcGameServer.start()` 会把世界线程、已经绑好的
TCP socket 和已经加载的插件回滚掉，再抛出异常，避免留下一个“半个服务器”。关闭时 TCP socket 先关，浏览器端点
紧随其后，世界线程在端点关闭后停止，最后才写用户记录；细节见 [01](01-getting-started.md) 的关闭顺序。

## 两套版本机制

两个传输都有版本号，但放的位置不同，而且是有意不同：

| 项 | 加密 TCP | 浏览器 WebSocket |
| --- | --- | --- |
| 版本在哪 | 加密握手里的一个二进制版本字节（当前 `1`） | 升级成功后的第一个文本帧 |
| 客户端说 | 37 字节 client hello 里的版本字节 | `HELLO 1` |
| 服务器答 | 69 字节 server hello 里的版本字节 | `OK OC/1` |
| 版本不对 | 握手失败，连接断开 | 先回错误行，再以 `1008` 关闭 |
| 先发命令再握手 | 不可能：没有握手就没有命令 | 回 `ERR HELLO first`，以 `1008` 关闭 |

浏览器端不能把版本字节塞进握手：RFC 6455 的 `Sec-WebSocket-Version: 13` 协商的是 **WebSocket 分帧协议**，
不是游戏协议；升级完成后每个字节都属于某个 WebSocket 帧，没有“命令之前的裸字节”这个位置。所以游戏版本
只能占用第一个文本帧。没有这条规则，一个过期的浏览器 bundle 会被当成当前语法来解释——这正是版本握手要
避免的静默错误。

```text
第一帧 HELLO 1   -> OK OC/1
第一帧 HELLO 2   -> ERR unsupported version, this server speaks OC/1   (1008)
第一帧 PING      -> ERR HELLO first                                      (1008)
第一帧 HELLO     -> ERR malformed HELLO                                  (1002)
```

三条错误行与关闭码：

| 情况 | 回答 | 关闭码 |
| --- | --- | --- |
| 第一帧不是 `HELLO`（空帧、`PING` 等） | `ERR HELLO first` | `1008` |
| `HELLO` 缺版本号、带多余参数、版本号不是十进制数字 | `ERR malformed HELLO` | `1002` |
| 版本号是合法的十进制数但不是 `1` | `ERR unsupported version, this server speaks OC/1` | `1008` |

`HELLO` 的命令字大小写不敏感（`hello 1` 也行），版本号必须恰好是 `1`；行尾的 `\r\n`、`\n` 或 `\r` 会先被
去掉。`1008` 是“策略不允许继续说话”，`1002` 是“这一行的形状破坏了握手语法”，两者故意分开，客户端作者
能一眼看出自己犯的是哪一种错。

**TCP 没有被这件事改动**：它继续在握手字节里读版本号，永远看不到 `HELLO`，命令表与回答也没有变化。

## 命令集：和 TCP 是同一套

`CommandWebSocketHandler` 把每个文本帧交给 `CommandDispatcher.execute(String, Session)`，与 `GameServer` 的
TCP 命令循环完全相同。因此这条端点上的命令、参数规则、回答与鉴权规则都不是“另一套兼容实现”，而是同一份
代码：

| 命令 | 回答 | 浏览器端的注意点 |
| --- | --- | --- |
| `PING` | `PONG` | — |
| `REGISTER <名字> <密码>` | `OK <文案>` / `ERR <文案>` | 成功与失败的文案由 `AuthResult` 给出 |
| `LOGIN <名字> <密码>` | `OK <文案>` / `ERR <文案>` | 成功后把**这条连接**标成已认证 |
| `COUNT` | `OK <账号数>` | 跨连接共享 |
| `REQ <名字> <base64 载荷>` | `OK <base64 载荷>` / `ERR <文案>` | 需要同一条连接上成功 `LOGIN` |
| `WHOAMI` / `ORDER` / `BUILD` / `TRAIN` | 见协议文档 | 需要同一条连接上成功 `LOGIN`；由 `WorldHost` 在下一步起点应用；账号名就是身份，同名重连复用同一个玩家与基地 |
| `QUIT` | 先 `OK bye`，再关闭连接 | 关闭码是 `1000` |
| 其它任何东西 | `ERR unknown command` | 空行也是这一条 |

- 一个文本帧**恰好一条**命令行，一条命令**恰好一行**回答；分片会在传输层先拼成完整消息。
- 命令字大小写不敏感；名字与密码逐字节精确，只切掉分隔用的空白——和 TCP 一样。
- `REQ` 的失败短句也相同：没登录是 `ERR login first`，名字没注册或载荷不是合法 base64 是
  `ERR unknown request`，处理器抛异常是 `ERR request failed`。登录前提由 `CommandDispatcher` 实现，
  浏览器拿不到任何 TCP 客户端拿不到的东西；插件的请求缝见 [03](03-plugins.md)。
- [`protocol/oc-protocol_zh_CN.md`](../../../../protocol/oc-protocol_zh_CN.md) 是线上约定的**唯一**来源：命令表、base64
  约定、`engine-info` 信封与版本兼容规则都以它为准，这一篇只解释服务器这一侧怎么实现。

二进制帧不是命令，也到不了 `CommandDispatcher`：传输层自己回一行 `ERR binary frames are not commands` 并
保持连接（见下面的帧规则）。

## 来源规则：同源优先，白名单兜底

WebSocket 握手不受浏览器的同源策略保护，所以服务器必须自己判断“这个页面有没有资格连”。`HttpWebServer`
按固定顺序做两件事：

1. **同源**：`Origin` 头里的 authority（`host[:port]`）与 `Host` 头相等，就接受。比较忽略大小写，也忽略
   scheme（`http://` 与 `https://` 不比），因为 authority 才是来源的身份。
2. **白名单**：不同源时，`Origin` 必须匹配 `WebSocketConfig` 的 `allowedOrigins` 里的某个 glob 模式
   （`*` 匹配任意一串字符，忽略大小写，整串匹配）。默认名单是 `http://localhost:*`、
   `http://127.0.0.1:*`、`https://localhost:*`、`https://127.0.0.1:*`。

同源为什么安全：浏览器把 `Origin` 设成打开 socket 的页面，把 `Host` 设成 socket 连到的服务器，页面自己
伪造不了这两个值。两者相等说明这个页面就是**本服务器发出去的**，浏览器已经对它执行过我们自己的来源策略；
接受它并没有信任一个外来页面。这也正是局域网地址能直接工作的原因：`http://192.168.1.7:25567/` 打开页面时，
`Origin` 与 `Host` 都是 `192.168.1.7:25567`，不需要把每个局域网 IP 都写进白名单。

白名单为什么还留着：同源策略管不到 WebSocket 握手，别的站点上的页面仍然可以对本服务器发起升级请求。
白名单是专门为这一种情况存在的第二条规则，默认只放行本机开发客户端；`http://evil.example` 因此被拒绝，
除非操作员明确放行。

| 情况 | 结果 |
| --- | --- |
| `Origin` 的 authority 等于 `Host` | 接受（同源） |
| 不同源，但匹配白名单模式 | 接受 |
| 不同源，且不匹配白名单 | `403` |
| 配了白名单，但请求没有 `Origin` | `403` |
| 没有 `Host` | 不算同源；继续看白名单 |
| `Origin` 结构不合法 | `400` |
| 白名单为空 | 接受（开发默认，见下） |

细节：

- **缺失 `Host` 或 `Origin` 都不算同源**：没有两个值就没有可比的东西。缺 `Origin` 时，白名单为空则放行、
  非空则 `403`。
- **格式错误的 `Origin` 是 `400`**，不是 `403`：它不是“另一个站点”，而是一个连来源都拼不出来的坏请求。
  没有 scheme、带路径/查询/片段/用户信息、含空白或非法字符、端口不是 `1..65535`，都算格式错误。
- 字面量 `Origin: null` 不参与同源比较，会落到白名单；这是浏览器在沙箱或 `file://` 场景可能发的东西。
- **空白名单是“允许任何来源”**，这是 `WebSocketConfig` 注释里写明的开发默认，而**不是**出厂默认
  （`WebSocketConfig.defaults()` 给的是上面那四个回环模式）。它必须只用于开发：任何网站都能打开一个到
  这个端点的 WebSocket，替操作员发命令或消耗连接线程；在生产或任何能被别人访问的机器上请给出明确的来源列表。

## 静态文件路由

静态路由由 `StaticFiles` 实现，根目录在构造时解析成绝对、`normalize()` 过的路径：`--static-root` 相对
**进程工作目录**，不是相对 jar 或 Gradle 项目目录。

### 方法与状态

| 情况 | 结果 |
| --- | --- |
| `GET` 一个存在的文件（没有命中 `/ws` 或插件资源路由） | `200`，带 `Content-Type` 与 `Content-Length` |
| `HEAD` 同一个文件 | `200`，`Content-Length` 与 `GET` 相同，但没有响应体 |
| 其它方法（`POST`、`OPTIONS`…） | `405`，带 `Allow: GET, HEAD` |
| 无扩展名的未知路径、目录、`/` | 回退到 `index.html`（SPA 路由） |
| 有扩展名的未知路径 | `404`，正文 `no such file: <path>` |
| 静态根不存在 | `404`，正文 `the web client is not built: nothing to serve at <root>` |

`Content-Length` **总是**有：`HttpResponse.writeTo` 无条件写它，`HEAD` 只是不写 body。每个普通 HTTP 响应
都带 `Connection: close`，一个连接只服务一个请求，没有 keep-alive 状态机。

### 路径穿越

请求目标必须是 origin-form（以 `/` 开头）；绝对形式、`//host/share`、以及第一段像 `C:` 的驱动器名都直接
`400`。解码与检查的顺序是：

1. 百分号解码，拒绝截断的转义、非十六进制转义与非 UTF-8 字节；
2. 拒绝原始或解码后的反斜杠与 NUL 字节；
3. 拒绝任何 `..` 段，包括 Windows 会忽略尾部空格/点的写法（`.. `）；
4. 把相对路径拼到 root 上再 `normalize()` 后，必须仍以 root 开头，否则 `403`；
5. 真的打开文件时再用 `toRealPath()` 比一次，符号链接指到 root 外也是 `403`。

前四步只看字符串，不要求文件存在；所有拒绝都发生在读一个字节之前。自校验用 `/../secret.txt`、
`/%2e%2e/secret.txt`、`/assets/../../secret.txt`、`/..%5csecret.txt`、`/..%20/secret.txt`、
`/C:/Windows/win.ini` 这些形状逐一验证“拒绝且不泄露 root 外的文件”。

### 缓存与 MIME

| 文件 | `Cache-Control` |
| --- | --- |
| `*.html` | `no-cache` |
| `/assets/` 下的文件 | `public, max-age=31536000, immutable` |
| 其它 | 不发送 |

HTML 用 `no-cache` 是为了让重新构建的客户端立刻生效；`/assets/` 下的文件名带内容哈希，所以可以按一年不变
缓存。MIME 表只覆盖浏览器客户端真正会用到的类型：

| 扩展名 | `Content-Type` |
| --- | --- |
| `.html` | `text/html; charset=utf-8` |
| `.js` / `.mjs` | `text/javascript; charset=utf-8` |
| `.css` | `text/css; charset=utf-8` |
| `.svg` / `.png` / `.jpg` / `.ico` | `image/svg+xml` / `image/png` / `image/jpeg` / `image/x-icon` |
| `.json` / `.map` | `application/json` |
| `.woff2` | `font/woff2` |
| `.wasm` | `application/wasm` |
| 其它 | `application/octet-stream` |

未知类型落到 `application/octet-stream` 是有意的：浏览器会下载而不是执行它，新资源类型不会意外当脚本跑。
目录永远不列出，只回退到 `index.html`；如果连 `index.html` 都没有，和静态根缺失一样是“web client is not
built”的 `404`。这条日志只写一次，避免每个请求刷一行。

## 插件资源路由

`OcGameServer` 用三参数构造 `HttpWebServer`，把 `PluginLoader` 一起传进去，于是端点还提供
`/plugin-assets/...`。`manifest.json` 是所有已加载插件图集清单的合并数组，`/plugin-assets/<插件 id>/<文件>`
是插件 jar 内 `assets/<文件>` 的原始字节。它只允许 `GET` / `HEAD`，成功的清单与文件响应一律 `no-cache`，
并且**只通过插件自己的类加载器读资源，不碰磁盘上的插件目录**。完整契约在协议文档的“插件资源”一节，
这一篇不重复；没有插件加载器的端点没有这条路由，`/plugin-assets/...` 会落到静态路由。

## 世界快照路由

`/world/snapshot` 是浏览器画面读取真实引擎世界的唯一入口。它是**普通 HTTP**，和插件资源一样不需要登录、不经过
WebSocket。`--world`（默认 `on`，`off` 关闭）决定是否启动 `WorldHost`，`--seed`（默认 `1`）决定世界种子。
服务器用一个 daemon 线程按固定 20 ticks/s 推进 `OcRtsEngine`，快照在同一个锁下读取，所以请求不会看到半个 tick。

### 成功响应

`GET /world/snapshot`（`HEAD` 同样支持）在 host 运行时返回：

| 项 | 值 |
| --- | --- |
| 状态 | `200` |
| `Content-Type` | `application/json; charset=utf-8` |
| `Cache-Control` | `no-store` |
| 正文 | 下面这个 JSON |

```json
{
  "tick": 42,
  "width": 64,
  "height": 64,
  "tilePixels": 16,
  "tiles": [["grass", "grass", "..."], ["...", "...", "..."]],
  "entities": [
    {"id": 1, "kind": "warehouse", "x": 19.5, "y": 19.5, "player": 1, "health": 1.00},
    {"id": 4, "kind": "worker", "x": 16.8, "y": 24.3, "player": 1, "health": 1.00}
  ],
  "players": [
    {"id": 1, "name": "Player 1", "gold": 0, "wood": 0},
    {"id": 3, "name": "alice", "gold": 300, "wood": 60}
  ]
}
```

- `tiles` 是 row major，恰好 `width * height` 个条目；每个字符串是地形的内容路径（`grass`、`sand`、...），和
  `TerrainType.key().path()` 一致。
- `entities` 按 id 排序；`kind` 是引擎的内容路径（`worker`、`soldier`、`medic`、`warehouse`、`barracks`、
  `workshop`、`ore_silo`），建筑与单位形状相同。`x` / `y` 是实体中心的地图坐标，保留一位小数。
- `players` 按 id 排序，世界里存在的每个玩家各一行（样例场景、AI 与每个真人账号），字段是 `id`、显示名
  `name`，以及扁平的金币/木材数量 `gold` / `wood`。这两个键就是经典内容目录注册的物品路径；快照不重复
  它们的显示名，其它物品类型暂时不进快照。金额来自玩家仓库的可用数量（引擎的投放点规则，与建造/训练
  成本检查同源），所以没有仓库的玩家两项都是 `0`。旧服务器可以不带这个字段，客户端必须容错。
- `tilePixels` 固定 `16`，只是给渲染器的建议值；浏览器仍按自己的相机缩放。
- 64×64 的快照只有几 KB，服务器每次请求重建，不缓存。

### 失败与关闭

| 情况 | 结果 |
| --- | --- |
| `--world off`，或 host 未启动 / 已停止 | `503 Service Unavailable`，短文本 `the world is not running` |
| `--http-port 0` | 根本没有 HTTP 监听器，也就没有这条路由 |
| `GET` / `HEAD` 之外的方法 | `405`，带 `Allow: GET, HEAD` |

路由在端点存在时**总是**注册：即使 `--world off`，`/world/snapshot` 也回答 503，而不是掉进静态路由的 SPA 回退
（那会错误地返回 `index.html`）。这和插件资源路由的方法规则一致。

### 浏览器侧

`web-client/src/render/view.ts` 的 `fetchWorldSnapshot(baseUrl)` 把这份 JSON 转换成渲染器已有的 `WorldView`
（地形字符串 -> `TerrainId`，`health` -> `healthRatio`，`player` -> `playerId`，`players` -> `PlayerView[]`），
结构不合法或实体越界时返回 `null`，页面继续使用确定性的 `demoWorld()` 占位地图。`players` 可以缺失：旧服务器
没有这个字段时客户端照常工作，只是 HUD 的资源行显示破折号。`main.ts` 在画面可见时每 500ms 拉一次快照（上一次
请求未完成就跳过），HUD 显示 `世界：server · tick N` 或 `世界：demo`；`WHOAMI` 回答玩家 id 后，资源行显示该玩家
的名字与 `金 300 · 木 60`，每次快照和成功的 `BUILD` / `TRAIN` 之后都会刷新。Vite dev proxy 把 `/world` 转发到
同一个 Java 服务器。

## 帧规则

`WebSocketConnection` 是模块里唯一解释 WebSocket 字节的类。它只把**完整的文本消息**交给 handler，分片、
mask 位、控制帧都在传输层处理。默认上限来自 `WebSocketConfig`；命令行只暴露端口、静态根与空闲超时
（`--ws-idle`），不暴露帧大小、消息上限或 25 秒的 ping 间隔。

| 规则 | 行为 |
| --- | --- |
| 长度编码 | `0..125` 用 7 位；`126` 后面跟 16 位；`127` 后面跟 64 位。64 位长度最高位为 1 是 `1002` |
| 客户端帧 | 必须设置 mask 位；没有 mask 是 `1002`，因为 RFC 6455 要求客户端掩码 |
| 服务端帧 | **永不**掩码；写帧加锁，handler 线程与读线程不会交错半个帧 |
| 帧上限 | 默认 64 KiB；声明长度超过上限在分配 payload 之前就 `1009` |
| 分片 | 数据帧可以分片，逐片拼成一条消息；重组后的消息上限默认 1 MiB，超了 `1009` |
| 分片形状 | continuation 没有未完成的消息、分片中间又开一个新的数据帧，都是 `1002` |
| 控制帧 | 不能分片，payload 最多 125 字节；违反是 `1002` |
| 保留位 / 未知 opcode | `1002` |
| 文本 | 重组后按 UTF-8 严格校验；非法 UTF-8 是 `1007` |
| 关闭 | 对端 close 回一个 close；close reason 也按 UTF-8 校验，非法是 `1007` |
| 关闭码 | `1002` 协议错误、`1007` 非法载荷、`1009` 消息过大、`1001` 空闲/服务器停止 |
| `PING` / `PONG` | 控制帧 ping 用同一份 payload 回 pong；pong 忽略（但会刷新读超时） |
| 空闲超时 | socket 读超时兼作 idle timeout，默认 300 秒，可用 `--ws-idle N` 改；超时以 `1001` 关闭 |
| 服务端心跳 | 端点每 25 秒给每条打开的连接发一个空 ping；浏览器自动回 pong，读超时因此不会在纯空闲页面上到期 |
| 二进制帧 | 回文本 `ERR binary frames are not commands`，**连接保持打开** |

几个容易记混的点：

- `1008` 不在上表：它是 `CommandWebSocketHandler` 在**游戏版本握手**里用的（版本不对、先发命令），不是
  帧协议错误。
- 服务器自己关闭时用 `1001`（`HttpWebServer.stop()` 对每个活连接发 `close(1001, "server stopping")`），
  `QUIT` 用 `1000`，handler 抛异常用 `1011`；`1006` 只是“没有 close 帧就断了”的本地哨兵，不会出现在线上。
- close reason 最多 123 字节（控制帧 125 字节减 2 字节状态码），超长时按 UTF-8 边界截断，不会把一个字符
  劈成两半。
- 一次 WebSocket 连接独占一个 daemon 线程直到关闭；静态请求是短的，立即释放。连接线程上限 64、等待队列
  128，满了就记日志并关掉新 socket，监听器不受影响。
- 二进制帧由传输层回答，不经过 `CommandWebSocketHandler`，所以它既不算一条命令，也不会重置游戏版本握手
  的状态。

## 常见坑

1. **在子目录里跑 Gradle `server` 任务，`--static-root` 会解析到别处。** 静态根是相对**进程工作目录**解析的；
   从 `projects/oc` 之类的子目录启动时，`web-client/dist` 会变成 `projects/oc/web-client/dist`，于是每个静态
   请求都得到“web client is not built”。仓库的 `server` 任务已经把 `workingDir` 设成仓库根（`build.gradle`
   里那行注释就是为此），所以从任何目录调 `./gradlew :projects:oc:oc-game-server:server` 都能拿到默认值；
   自己用 `java -m` 或换工作目录时要显式给绝对路径。
2. **局域网页面被 403，通常是代理改了 `Host`。** 页面用 `http://192.168.1.7:25567/` 打开时，浏览器发
   `Origin: http://192.168.1.7:25567`；如果中间的反向代理把 `Host` 改成 `127.0.0.1:25567`，同源比较就失败，
   默认白名单又只有回环地址，于是 403。让代理保留原始 `Host`，或把页面来源加进白名单。
3. **反向代理改 `Host` 也会让同源失效，即使页面是“同一个站点”。** HTTPS 终止在代理上时，`Origin` 是公网名，
   代理转发给 Java 时 `Host` 变成后端地址；scheme 本来就不比较，但 authority 不同仍然不同源。要么用
   `proxy_set_header Host $host` 透传，要么配置 `allowedOrigins`。注意白名单是 glob 整串匹配，写
   `https://example.com` 不会顺带匹配端口。
4. **`--http-port 0` 是关闭，不是随机端口。** `--port 0` 会让操作系统挑一个空闲 TCP 端口并打印实际值；
   `--http-port 0` 则让 `OcGameServer` 根本不创建 HTTP 监听器。启动输出会明确写
   `browser endpoint: disabled (--http-port 0)`。一个只跑 TCP 的服务器因此与端点出现之前完全一样，连端口都不占。
5. **静态根缺失不是启动错误。** 它是第一次构建之前的正常状态：启动照常成功，静态请求回 404，并在正文与
   日志里说“web client is not built”。看到这句话先检查 `web-client/dist` 是否存在、`--static-root` 是否
   指对，而不是怀疑服务器没起来。
6. **这条端点是明文的。** HTTP 与 WebSocket 都没有 TLS；`REGISTER` / `LOGIN` 的密码在链路上是明文。只在
   本机或受控网络使用，或放到 HTTPS 反向代理后面。`engine-info` 还需要安全上下文里的 `crypto.subtle`，
   局域网 IP 直连通常拿不到它。
7. **`--world off` 不是“没有这条路由”。** 只要 `--http-port` 不是 `0`，`/world/snapshot` 就存在，只是回答
   `503 the world is not running`。看到 503 先看启动参数，而不是怀疑浏览器缓存；`--http-port 0` 才是连端点一起
   关掉。世界快照是公开只读数据，和插件资源一样不做鉴权。

## 相关文档

- 命令行选项、启动与关闭顺序、六个包的分工 → [01-getting-started.md](01-getting-started.md)
- 加密 TCP 的握手、帧格式与命令表 → [02-accounts-and-encryption.md](02-accounts-and-encryption.md)
- 插件、`RequestRegistry` 与 `REQ` 的请求缝 → [03-plugins.md](03-plugins.md)
- `engine-info` 的第二层信封与调试 CLI → [04-debug-plugin-and-cli.md](04-debug-plugin-and-cli.md)
- 浏览器与服务器之间的线上约定（命令表、版本兼容与插件资源以它为准） →
  [`protocol/oc-protocol_zh_CN.md`](../../../../protocol/oc-protocol_zh_CN.md)
- 浏览器客户端本身 → [`web-client/README.md`](../../../../web-client/README.md)
- 引擎的包与管线（`engine-info` 报告描述的那个模块） →
  [09-architecture-and-tick-pipeline.md](../../oc-rts-engine/help/09-architecture-and-tick-pipeline.md)

## 相关源码

| 内容 | 源码 |
| --- | --- |
| 端点、HTTP 请求/响应与静态文件 | [`HttpWebServer.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/HttpWebServer.java) |
| HTTP 头解析与错误状态 | [`HttpRequest.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/HttpRequest.java)、[`HttpResponse.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/HttpResponse.java) |
| 状态码与正文 | [`HttpError.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/HttpError.java) |
| 路径解析、穿越检查与缓存 | [`StaticFiles.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/StaticFiles.java) |
| 插件资源路由与清单合并 | [`PluginAssets.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/PluginAssets.java)、[`PluginManifest.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/PluginManifest.java) |
| 服务器世界主机与快照 JSON | [`WorldHost.java`](../src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java) |
| 扩展名到 `Content-Type` | [`MimeTypes.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/MimeTypes.java) |
| 端点配置与默认值 | [`WebSocketConfig.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/WebSocketConfig.java) |
| 分帧、分片与关闭 | [`WebSocketConnection.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/WebSocketConnection.java) |
| handler 与 session 接口 | [`WebSocketHandler.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/WebSocketHandler.java)、[`WebSocketSession.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/WebSocketSession.java) |
| 版本握手与命令适配 | [`CommandWebSocketHandler.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/CommandWebSocketHandler.java) |
| 两边共用的命令语法 | [`CommandDispatcher.java`](../src/main/java/io/github/sob1234509876/oc/server/net/CommandDispatcher.java) |
| TCP 侧的命令循环与配置 | [`GameServer.java`](../src/main/java/io/github/sob1234509876/oc/server/net/GameServer.java)、[`ServerConfig.java`](../src/main/java/io/github/sob1234509876/oc/server/net/ServerConfig.java) |
| 组装、选项与关闭顺序 | [`OcGameServer.java`](../src/main/java/io/github/sob1234509876/oc/server/OcGameServer.java) |
| `server` 任务与工作目录 | [`build.gradle`](../build.gradle) |
| 请求缝的另一端 | [`RequestHandler.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/RequestHandler.java)、[`RequestRegistry.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/RequestRegistry.java) |
| WebSocket 自校验 | [`WebSocketCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/WebSocketCheck.java) |
| HTTP 静态路由自校验 | [`HttpCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/HttpCheck.java) |
| 插件资源路由自校验 | [`PluginAssetsCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/PluginAssetsCheck.java) |
| 世界主机与快照路由自校验 | [`WorldHostCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/WorldHostCheck.java) |
| `REQ` 自校验与总入口 | [`RequestCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/RequestCheck.java)、[`ServerCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/ServerCheck.java) |
