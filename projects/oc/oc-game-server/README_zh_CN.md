[English](README.md) | 简体中文

# Omni Com 游戏服务器

`projects:oc:oc-game-server` —— Omni Com 的游戏服务器：一个零第三方依赖的 Java 17 程序（JPMS），只拥有插件不该拥有的三样东西：监听 socket、用户记录，以及把记录写盘的定时器。服务器自己提供 `25566` 上的加密 TCP 信道、`25567` 上可选的浏览器端点，以及一条让插件往里发布处理器的只读请求缝。浏览器端点旁边还托管一个真实的 RTS 世界（`server.world.WorldHost`）并发布它的实时快照；游戏内容与贴图仍由 `plugins/*.jar` 里的插件提供。

## 坐标

| | |
| --- | --- |
| Gradle 路径 | `:projects:oc:oc-game-server` |
| Java 模块 | `oc.projects.oc.game.server` |
| 基础包 | `io.github.sob1234509876.oc.server`，其下是 `.auth`、`.net`、`.plugin`、`.ws`、`.world` |
| 组 / 版本 | `io.github.sob1234509876.oc` / `2.0a` |
| 构建 | `java-library`、Java 17 工具链、JPMS、**无第三方依赖**（托管的 RTS 世界来自仓库内的引擎模块） |
| 自检 | `./gradlew --offline :projects:oc:oc-game-server:selfcheck`（513 项检查，无测试框架，无网络） |
| TCP 信道 | 加密，默认 `25566`（`--port`） |
| 浏览器端点 | HTTP/WebSocket，默认 `25567`（`--http-port`；`0` 关闭它） |
| 帮助 | [`help/README.md`](help/README.md) —— 五章，中文 |

## 构建、自检、运行

```shell
./gradlew --offline :projects:oc:oc-game-server:selfcheck
./gradlew --offline :projects:oc:oc-game-server:server --args="--port 25566 --plugins plugins --data data"
```

自检末尾打印一行：

```text
game server self check: all 513 checks passed.
```

513 条是 `ServerCheck` 依次调用的八个检查类之和：`AuthCheck` 65 条（账号、本地存储、转义、并发、PBKDF2 参数）、`NetworkCheck` 57 条（真实回环 socket 上的握手、命令、并发与恶意客户端）、`PluginCheck` 76 条（运行时编译并真正加载插件 jar）、`RequestCheck` 43 条（`REQ` 的鉴权、透明转发与失败处理）、`WebSocketCheck` 63 条（升级、版本握手、帧规则与来源规则）、`HttpCheck` 47 条（静态文件、路径穿越、缓存、缺失的静态根）、`PluginAssetsCheck` 80 条（真实插件 jar 上的清单合并与资源路由）、`WorldHostCheck` 82 条（daemon tick 线程、快照 JSON、种子确定性、`/world/snapshot` 的 200/503/405）。自检是独立 `selfCheck` 源集（`src/selfcheck/java`）里的普通程序，所以标准 `test` 任务保持没有源码。

`server` 任务用 `JavaExec` 运行 `OcGameServer`，标准输入接到进程，工作目录是仓库根，所以 `Ctrl+C` 能走到 shutdown hook，默认的 `--static-root web-client/dist` 也能解析到构建好的浏览器客户端。

### 命令行选项

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `--port N` | `25566` | TCP 端口；`0` 让操作系统挑一个空闲端口，并打印真实端口 |
| `--bind ADDRESS` | `0.0.0.0` | 绑定的接口，同时作用于 TCP 信道与浏览器端点 |
| `--plugins DIRECTORY` | `plugins` | 插件 jar 所在目录，同时是它们的依赖搜索目录 |
| `--data DIRECTORY` | `data` | 服务器自己的状态目录，今天只有用户记录 |
| `--save-seconds N` | `60` | 用户记录写盘的间隔；必须是正数 |
| `--http-port N` | `25567` | 浏览器端点端口；`0` 关闭这个端点，而不是让它随机挑端口 |
| `--static-root DIRECTORY` | `web-client/dist` | 浏览器端点提供的目录，相对**进程工作目录** |
| `--world on\|off` | `on` | 是否启动服务器世界；`off` 让 `/world/snapshot` 回答 503；`--http-port 0` 时强制关闭 |
| `--seed N` | `1` | 服务器世界的种子；同一种子复现同一张地图与起始布局 |
| `--help` | — | 打印帮助后退出 |

除此之外没有别的开关：没有配置文件，也没有环境变量。

## 它做什么

**账号。** `LocalUserStore` 在 `data/users.txt` 里一行存一个账号：名字、十六进制哈希、十六进制盐、迭代次数、注册时间与最近登录时间，每个字段里的 `%`、制表符、CR 和 LF 都做了百分号转义。密码用 PBKDF2-HMAC-SHA256 哈希，派生密钥 256 位，盐是 `SecureRandom` 取的 16 字节，默认迭代 210000 次；迭代次数跟着记录一起存，所以调高默认值只影响新注册。`flush()` 先写同目录的 `users.txt.tmp`，再用原子移动覆盖整个文件，注册与登录成功时都当场落盘，因此崩溃后留下的要么是旧的完整文件，要么是新的完整文件。

**加密信道。** 一条普通 TCP socket 上走三步：临时 X25519 密钥交换、用 HKDF-SHA256 从共享秘密导出 32 字节会话密钥、然后 AES-256-GCM 分帧。客户端 hello 是 37 字节，服务器 hello 是 69 字节；每个方向各有 4 字节 nonce 前缀与 64 位计数器，一帧是 4 字节大端长度加上密文与它的 128 位认证标签，计数器让重放的帧通不过标签校验。帧之上是行协议：`PING`、`REGISTER`、`LOGIN`、`COUNT`、`REQ`、`QUIT`。

**浏览器端点。** `GET` 与 `HEAD` 从 `--static-root`（构建好的浏览器客户端）发出；`/ws` 升级成 RFC 6455 WebSocket，第一个文本帧必须是 `HELLO 1`，服务器答 `OK OC/1`；`/plugin-assets/...` 从插件 jar 里发 `assets/`，所有插件的 `assets/manifest.json` 在 `/plugin-assets/manifest.json` 合并成一个数组；`GET /world/snapshot` 用 `application/json; charset=utf-8` 与 `Cache-Control: no-store` 发布托管世界的快照，世界关闭时回答 `503 the world is not running`。它是明文 HTTP/WS 监听器、没有 TLS，适合本机或放在反向代理后面；`--http-port 0` 会把它（以及世界）整个去掉。

**插件加载器。** `PluginLoader` 按文件名顺序扫描 `plugins/*.jar`，给每个 jar 一个自己的 `URLClassLoader`。URL 列表先是这个 jar 自己，然后是目录里其他所有 jar（按文件名排序），所以把依赖 jar 丢进同一目录就算装好依赖，而插件自带的库副本又赢过目录里的那份。类加载器有意**永不关闭**：关掉的 `URLClassLoader` 再也不能定义它此前没碰过的类、也读不出没读过的资源，那正是按需加载的插件以后会撞上的 `NoClassDefFoundError`。插件由 `omnicom-plugin.properties`（`id`、`version`、`main`、`description`）描述，生命周期是 `onLoad` → `onEnable` → `onDisable`。

**只读请求缝。** 插件用匹配 `[a-z0-9-]{1,32}` 的名字把 `RequestHandler` 发布进 `RequestRegistry`；`byte[] handle(byte[] request)` 就是全部合同。客户端发 `REQ <名字> <base64 载荷>`，服务器 base64 解码、把确切字节交给处理器、再把答复 base64 编码，两边都不解析。`REQ` 是唯一要求同一条连接上成功 `LOGIN` 过的命令：没登录时直接回 `ERR login first`，处理器根本不会被调用。名字没注册、名字过长或载荷不是合法 base64 都是 `ERR unknown request`，处理器抛异常是 `ERR request failed`。

**定时保存与关闭顺序。** 名为 `oc-autosave` 的 daemon 定时器每 `--save-seconds` 秒（默认 60）把用户记录写盘一次，没有改动时是空操作。`stop()` 随后按固定顺序执行：先停定时器，在 socket 还开着时调每个插件的 `onDisable()`，关掉 TCP 监听与它的客户端，关掉浏览器端点（给活着的 WebSocket 发关闭码 `1001`），在没有任何请求能再读世界之后停掉 tick 线程，最后再写一次用户记录。

## 模块布局

| 包 | 职责 | 主要类型 |
| --- | --- | --- |
| `io.github.sob1234509876.oc.server` | 组装与生命周期：把账号、socket、插件加载器、可选浏览器端点与定时器接起来，并解析命令行 | `OcGameServer` |
| `...server.auth` | 账号规则、密码哈希、本地文本存储 | `AuthService`、`UserStore`、`LocalUserStore`、`PasswordHasher`、`Pbkdf2PasswordHasher`、`UserRecord`、`AuthResult` |
| `...server.net` | 监听、加密信道、命令解析、协议客户端 | `ServerConfig`、`SecureChannel`、`GameServer`、`GameClient` |
| `...server.plugin` | 插件加载与只读请求缝 | `Plugin`、`PluginContext`、`PluginDescriptor`、`DiscoveryResult`、`PluginLoader`、`RequestHandler`、`RequestRegistry` |
| `...server.ws` | 可选的浏览器端点：HTTP 静态文件、插件资源、世界快照、WebSocket 分帧、版本握手 | `HttpWebServer`、`WebSocketConfig`、`WebSocketConnection`、`CommandWebSocketHandler`、`PluginAssets`、`PluginManifest` |
| `...server.world` | 托管的服务器世界：一个扁平 64×64 的 `OcRtsEngine`、daemon tick 线程与快照 JSON | `WorldHost` |

依赖方向是单向的：`auth` 与 `plugin` 互相不认识，`net` 认识这两个，`ws` 认识 `auth`、`net`、`plugin` 与 `world`，根包认识全部五个。`world` 是唯一认识 RTS 引擎的包；`plugin` 包里没有任何 `net`、`auth` 或 `world` 的引用，这是"插件永远看不到 socket、密码或活游戏"的第一层保证。

```text
src/main/java/module-info.java          导出六个包，requires RTS 引擎
src/main/java/.../server/               上面那六个包
src/selfcheck/java/.../server/check/    513 条检查的自检程序
help/                                   五章中文文档
```

## 帮助章节

| 章节 | 讲什么 |
| --- | --- |
| [01 快速上手](help/01-getting-started.md) | 构建、自检、全部选项、启动与关闭顺序、六个包的分工 |
| [02 账号与加密信道](help/02-accounts-and-encryption.md) | 用户文件、PBKDF2、`AuthService`、握手、帧格式、命令表 |
| [03 插件](help/03-plugins.md) | 生命周期、描述文件、发现顺序、依赖规则、请求缝、最小插件 |
| [04 调试插件与命令行](help/04-debug-plugin-and-cli.md) | `oc-debug`、第二层信封、`debug.key`、报告、CLI |
| [05 浏览器端点](help/05-browser-endpoint.md) | `/ws`、静态文件、来源规则、插件资源、`/world/snapshot`、WebSocket 帧规则 |
| [help/README.md](help/README.md) | 索引、三条阅读路线，以及还没有实现的东西 |

这些章节用中文写，API 名字保持英文。

## 运行时目录

`plugins/` 与 `data/` 是运行时状态，不是源码。`plugins/` 是操作员的投放区：插件 jar 与它们的依赖 jar 都放在这一层，服务器只读它，插件绝不能把自己的状态写进去。`data/` 属于服务器：`data/users.txt` 是账号表，`data/<插件名>/` 是每个插件拿到的自己的目录。这个目录本身作为文档被跟踪 —— 见 [`plugins/README.md`](../../../plugins/README.md) —— 但操作员丢进去的 jar 被 `.gitignore` 忽略（`plugins/*.jar`），`data/` 下的一切也都被忽略，所以 jar 与账号都不会进版本库。

## 相关

- [`../oc-game-server-debug/README.md`](../oc-game-server-debug/README.md) —— `oc-debug`，只读调试插件，以及向它要报告的命令行客户端。
- [`../oc-game-server-hello/README.md`](../oc-game-server-hello/README.md) —— `oc-hello`，最小插件，以及默认贴图图集。
- [`web-client/README.md`](../../../web-client/README.md) —— 可选端点提供的那个浏览器客户端。
- [`protocol/oc-protocol_zh_CN.md`](../../../protocol/oc-protocol_zh_CN.md) —— 两端共同实现的线上约定。
- [`../oc-rts-engine/README.md`](../oc-rts-engine/README.md) 与 [`../oc-googology/README.md`](../oc-googology/README.md) —— `oc-debug` 这类插件所依赖的两个库。
