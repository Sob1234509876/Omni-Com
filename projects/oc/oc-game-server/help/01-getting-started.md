# 01 · 快速上手

> 从零到"一个加密 TCP 服务器跑起来：读到了用户记录、加载了插件目录里的 jar，并且能干净地关掉"。

## 这一篇讲什么

- 怎么构建、怎么跑自校验，以及 938 条断言分别落在哪十一组。
- 启动器的全部命令行选项、默认值，以及解析器的脾气。
- 插件目录、数据目录与浏览器静态根的默认位置，谁往里写什么。
- 一次启动打印什么、一次关闭按什么顺序发生。
- 一张表说清根包与 `auth`/`net`/`plugin`/`ws`/`world` 六个包各自负责什么。
- 浏览器端点是否存在、默认端口与静态根，详见 [05](05-browser-endpoint.md)。

## 前置条件

| 需要 | 说明 |
| --- | --- |
| JDK 17 | 两个模块都用 `java.toolchain` 固定为 17；`JAVA_HOME` 指向 17 即可 |
| Gradle | 仓库自带 wrapper（`gradlew` / `gradlew.bat`），无需本机安装 |
| 网络 | **不需要**。服务器零第三方依赖，`build` 与 `selfcheck` 都可离线执行 |
| 源码编码 | 构建脚本已设 `options.encoding = 'UTF-8'`；手工 `javac` 时记得 `-encoding UTF-8` |

## 构建与自校验

```shell
./gradlew :projects:oc:oc-game-server:build            # 编译、打 jar
./gradlew :projects:oc:oc-game-server:selfcheck        # 无框架自校验
./gradlew :projects:oc:oc-game-server-debug:selfcheck  # 调试插件自校验（含真实 socket 往返）
```

两个任务的末尾各打印一行结论：

```text
game server self check: all 938 checks passed.
debug plugin self check: all 85 checks passed.
```

938 条来自 `ServerCheck` 依次调用的十一个检查类：`AuthCheck` 65 条（账号、本地存储、转义、并发、PBKDF2 参数）、`NetworkCheck` 57 条（真实回环 socket 上的握手、命令、并发、异构客户端）、`PluginCheck` 76 条（运行时生成真 jar 并加载）、`RequestCheck` 43 条（`REQ` 的鉴权、透明转发与失败处理）、`WebSocketCheck` 88 条（真实 socket 上的升级、版本握手、帧规则、来源规则、可配置空闲超时与服务端心跳）、`HttpCheck` 47 条（静态文件、穿越、缓存、缺失的静态根）、`PluginAssetsCheck` 80 条（真实插件 jar 上的清单合并与资源路由）、`WorldHostCheck` 306 条（daemon tick 线程、无界 `InfiniteTerrainMap` 与 4096 区块预算、从 `(0,0)` 起 64 格内的出生锚点、带 `cx` / `cy` / `r` 的窗口快照（含远中心与负原点）、无地图边缘的启动、种子确定性、`/world/snapshot` 的 200/503/405，以及收入速率与逐任务 `queueItems`）、`WorldTickHookCheck` 23 条（插件 tick 钩子的契约与顺序）、`PlayerCommandCheck` 93 条（`WHOAMI`、账号身份、世界线程命令队列、移动命令、所有权、建造成本、生产等级门槛与建造可达）、`JoinBaseCheck` 60 条（加入世界时的仓库、工程师与开局资源，以及重连后原样保留）。85 条来自 `DebugPluginCheck` 的四节：信封 15 条、密钥文件与 `engine-info` 报告 37 条、请求注册表 14 条、真实 socket 端到端 19 条。

## 启动服务器

```shell
./gradlew :projects:oc:oc-game-server:server --args="--port 25566 --plugins plugins --data data"
```

这个任务用 `JavaExec` 跑 `OcGameServer` 的 `main`，标准输入接到进程，所以 `Ctrl+C` 能触发关闭。也可以直接跑 `io.github.sob1234509876.oc.server.OcGameServer`。默认还会在 `25567` 上开一个浏览器端点；只想跑加密 TCP 就加 `--http-port 0`，细节见 [05](05-browser-endpoint.md)。

### 命令行选项

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `--port N` | `25566` | 监听端口；`0` 让操作系统挑一个空闲端口（打印出来的是实际端口） |
| `--bind ADDRESS` | `0.0.0.0` | 绑定的接口；只想本机访问就写 `127.0.0.1` |
| `--plugins DIRECTORY` | `plugins` | 插件 jar 所在目录，同时是它们的依赖搜索目录 |
| `--data DIRECTORY` | `data` | 服务器自己的状态目录；今天只有用户记录 |
| `--save-seconds N` | `60` | 定时把用户记录写盘的间隔；必须是正数，`0` 会直接抛 |
| `--http-port N` | `25567` | 浏览器端点的 HTTP/WebSocket 端口；`0` **关闭**这个端点（不是让系统挑端口） |
| `--static-root DIRECTORY` | `web-client/dist` | 浏览器端点提供的静态目录；相对**进程工作目录**，`server` 任务从仓库根运行 |
| `--ws-idle N` | `300` | WebSocket 空闲超时，整数秒；必须是正数；静默连接以 `1001` 关闭 |
| `--world on\|off` | `on` | 是否启动服务器世界；`off` 让 `/world/snapshot` 回答 503；`--http-port 0` 时强制关闭 |
| `--seed N` | `1` | 服务器世界的种子；同一种子生成同一份地形与同一套起始布局 |
| `--chunks N` | `4096` | 无界世界的已加载区块预算（内存上限，不是地图大小）；必须是正数 |
| `--help` | — | 打印帮助后退出 |

`--help` 打印的就是下面这段（取自源码里的文本块，原文如此）：

```text
Omni Com game server

  --port N            listening port, 0 picks a free one (default 25566)
  --bind ADDRESS      interface to bind (default 0.0.0.0)
  --plugins DIRECTORY where plugin jars live (default plugins)
  --data DIRECTORY    server owned state, user records today (default data)
  --save-seconds N    how often user records are written (default 60)
  --http-port N       browser facing HTTP/WebSocket port, 0 disables it (default 25567)
  --static-root DIR   directory the HTTP route serves (default web-client/dist)
  --ws-idle N         WebSocket idle timeout in seconds (default 300, must be positive)
  --world on|off      start the server side world (default on; off answers 503)
  --seed N            seed of the server side world (default 1)
  --chunks N          loaded chunk budget of the unbounded world (default 4096)
  --help              this text
```

解析器的规则来自 `OcGameServer` 的 `parseArguments`：参数必须以 `--` 开头，后面可以跟一个不以 `--` 开头的值；**不跟值就是 `true`**。所以 `--save-seconds` 后面忘了写数字时，值会变成 `true`，`Integer.parseInt` 抛 `NumberFormatException`；`--http-port` 也走同样的 `Integer.parseInt`。`--ws-idle` 走 `OcGameServer.parseWsIdleMillis`：它要求正整数秒，`0`、负数、非数字都会抛 `IllegalArgumentException`，再把秒换算成毫秒交给 `WebSocketConfig`。一个不认识的裸参数（比如 `plugins`）会抛 `IllegalArgumentException`。

`--port`、`--bind`、`--plugins`、`--data`、`--save-seconds`、`--http-port`、`--static-root`、`--ws-idle`、`--world`、`--seed`、`--chunks` 之外没有别的开关：没有配置文件，没有环境变量。`--world` 只接受 `on` / `off`（也接受 `true` / `false`），写别的值会直接抛异常。

## 目录与文件

两个默认目录由 `OcGameServer.DEFAULT_PLUGINS_DIRECTORY` 与 `OcGameServer.DEFAULT_DATA_DIRECTORY` 给出，都是相对**进程工作目录**的路径；构造服务器时会被转成绝对路径并 `normalize()`。浏览器端点的静态根（默认 `web-client/dist`，`WebSocketConfig.DEFAULT_STATIC_ROOT`）同样相对**进程工作目录**，由 `StaticFiles` 构造时转成绝对路径并 `normalize()`；它只读，服务器不往里写。

| 路径 | 谁写 | 内容 |
| --- | --- | --- |
| `plugins/` | 操作员 | 插件 jar 与它们需要的依赖 jar；服务器只读不写 |
| `data/` | 服务器 | 服务器自己拥有的状态 |
| `data/users.txt` | `LocalUserStore` | 制表符分隔的账号表，格式见 [02](02-accounts-and-encryption.md) |
| `data/<插件名>/` | 插件 | 每个插件自己的目录（`PluginContext.dataDirectoryOf`） |
| `data/oc-debug/debug.key` | `oc-debug` | 调试密钥，见 [04](04-debug-plugin-and-cli.md) |
| `web-client/dist/` | pnpm / 操作员 | 构建好的浏览器客户端；静态路由只读，见 [05](05-browser-endpoint.md) |

一条硬约定：**插件的状态绝不写进 `plugins/`**。`plugins/` 是操作员的拖放区，drops 进去的 jar 随时会被替换或删除；`OcGameServer` 用的是四参数构造的 `PluginLoader`（多出来的那个参数是服务器自己的请求注册表），把数据根指向 `--data`。只有 `PluginLoader` 的两参数构造会把数据根放到 `plugins/data`（那是给自包含的目录或自校验用的，见 [03](03-plugins.md)）。

## 一次启动做了什么

`OcGameServer.start()` 的顺序是刻意的：

1. **先加载插件**（`PluginLoader.loadAll()`），把每个 jar 的问题写进日志——此时 socket **还没开**，所以一个起不来的插件不会留下半个服务器让客户端连上；
2. 如果 `--world` 是 `on` 且 `--http-port` 不是 `0`，构造 `WorldHost`（此时只建世界与样例场景，**不启动线程**）；目录或内容有问题就在绑定任何端口之前失败；
3. 绑定监听 socket（`GameServer.start()`）：失败就以异常抛给调用方，而不是在后台线程里消失；
4. 启动世界的 daemon tick 线程，按固定 20 ticks/s 推进引擎；
5. 如果 `--http-port` 不是 `0`，启动浏览器端点（`HttpWebServer.start()`）：绑定失败时先把世界线程、已经绑好的 TCP socket 与已经加载的插件回滚掉，再把异常抛给调用方，不留下半个服务器；
6. 起一个名为 `oc-autosave` 的 daemon 单线程定时器，首次延迟与间隔都是 `--save-seconds`；
7. 打印一行汇总，写入服务器的 logger。

`main` 随后再打印四行并**用 `CountDownLatch` 把进程挂住**——acceptor 与客户端线程都是 daemon，所以让进程活着的是这个 latch，不是线程：

```text
Omni Com 2.0a listening on 0.0.0.0:25566
plugins: 1 from C:\srv\plugins
accounts: 3, records in C:\srv\data
browser endpoint: http://0.0.0.0:25567/ (static root web-client/dist)
```

`--http-port 0` 时最后一行改成 `browser endpoint: disabled (--http-port 0)`，并且根本不会绑定 HTTP 端口。

同时注册一个名为 `oc-shutdown` 的 JVM shutdown hook，所以 `Ctrl+C` 会走和代码里调用 `stop()` 一样的路径。

## 关闭顺序

`OcGameServer.stop()` 只在已经启动过时做事，顺序是**先停表、再停插件、再关两个 socket、最后写盘**：

| 步骤 | 做了什么 | 为什么是这个位置 |
| --- | --- | --- |
| 1 | `saveIntervalSeconds` 的定时器 `shutdownNow()` | 先停掉后台写盘，后面几步的写盘时机才是确定的 |
| 2 | `PluginLoader.unloadAll()` 按加载顺序调每个插件的 `onDisable()` | **socket 还开着**，插件可以在这里给自己的连接收尾；一次失败只记一条日志，不停下别的插件 |
| 3 | `GameServer.stop()` | 关监听 socket（唤醒阻塞的 `accept`）→ 关每个客户端 socket → `shutdownNow()` 线程池并等最多 2 秒 |
| 4 | `HttpWebServer.stop()` | 关 HTTP 监听 socket → 给每个活着的 WebSocket 发 `1001 server stopping` → 关 socket → 停线程池与 acceptor；浏览器因此看到一次正常关闭，HTTP 端口在这里释放 |
| 5 | `WorldHost.stop()` | 端点已经不再收请求，此时停 tick 线程并等它离开循环；之后不会再有快照读取一个正在关闭的世界 |
| 6 | 再写一次用户记录 | 定时器已经停了，这是最后一次机会；写失败只记日志，不阻止退出 |

第 3 至 5 步是同一个关闭阶段：TCP socket 先关，浏览器端点紧随其后，世界线程在端点关闭后停止，三者都在最后写盘之前；`--http-port 0` 时第 4、5 步都是空操作。第 6 步之所以存在：注册和登录成功时各自已经 `flush()` 过一次（见 [02](02-accounts-and-encryption.md)），`flush()` 在没有改动时是空操作，所以定时器和关闭时的这次写盘都是**兜底**——真正保证"注册的账号不会丢"的是注册路径里那次立即写盘。

## 架构：六个包各管什么

| 包 | 职责 | 主要类型 |
| --- | --- | --- |
| `io.github.sob1234509876.oc.server`（根） | 组装与生命周期：把认证、socket、插件加载器、可选浏览器端点、世界、定时器接起来，解析命令行 | `OcGameServer` |
| `...server.auth` | 账号规则、密码哈希、本地文本存储 | `AuthService`、`UserStore`、`LocalUserStore`、`PasswordHasher`、`Pbkdf2PasswordHasher`、`UserRecord`、`AuthResult` |
| `...server.net` | 监听、加密信道、命令解析、协议客户端 | `ServerConfig`、`SecureChannel`、`GameServer`、`GameClient`（外加一个包内测试缝 `ChannelTestSupport`） |
| `...server.plugin` | 插件加载与只读请求缝 | `Plugin`、`PluginContext`、`PluginDescriptor`、`DiscoveryResult`、`PluginLoader`、`RequestHandler`、`RequestRegistry` |
| `...server.ws` | 可选的浏览器端点：HTTP 静态文件、插件资源、世界快照、WebSocket 分帧、版本握手、命令适配 | `HttpWebServer`、`WebSocketConfig`、`WebSocketConnection`、`CommandWebSocketHandler`、`PluginAssets` |
| `...server.world` | 服务器世界：一个无界的 `OcRtsEngine`（`InfiniteTerrainMap` + 区块预算）与确定性噪声地形、从 `(0,0)` 附近找到的可建造出生锚点、daemon tick 线程与窗口快照 JSON | `WorldHost` |

依赖方向是单向的：`auth` 与 `plugin` 互相不认识，`net` 认识这两个（它要给 `AuthService` 送凭据、向 `RequestRegistry` 查处理器），`ws` 认识 `auth`、`net`、`plugin` 与 `world`（它用 `CommandDispatcher` 执行命令、查 `RequestRegistry`、读 `WorldHost`），根包认识全部。`world` 是服务器里唯一认识 RTS 引擎的包（`module-info` 里只有一个 `requires oc.projects.oc.rts.engine`）；`plugin` 包里没有任何 `net`、`auth` 或 `world` 的引用——插件 API 不知道 socket、密码或活游戏的存在，这是"插件拿不到活游戏"的第一层保证。

## 常见坑

1. **`--plugins` 目录不存在不是错误**。`PluginLoader.loadAll()` 把不存在的目录当成"没有插件"，返回空列表；只有目录存在但列不出来才会记一条问题。
2. **`--save-seconds` 不是"每 N 秒必然写盘"**。`flush()` 靠一个 dirty 标记，没有改动就什么都不做。它只是兜底，别指望它替你做增量备份。
3. **`--port 0` 的真实端口只在启动后才知道**。`GameServer.port()` 在绑定前返回配置值，绑定后返回操作系统挑的端口；脚本里要用它就得解析启动输出的第一行。
4. **重复 `start()` 的行为不一致**：`OcGameServer.start()` 已经启动时直接返回，而直接调 `GameServer.start()` 第二次会抛 `IllegalStateException`。
5. **默认绑定是 `0.0.0.0`**。开发机上想只让自己连，写 `--bind 127.0.0.1`；`--bind` 同时作用于 TCP 与浏览器端点。
6. **密码没有找回、没有改密命令**。账号一旦注册，密码只能靠直接编辑 `data/users.txt`（不推荐）或换名字。
7. **`--http-port 0` 是关闭浏览器端点，不是随机端口**。`--port 0` 才会让操作系统挑一个空闲 TCP 端口；`--http-port 0` 根本不创建 HTTP 监听器，启动输出会写 `browser endpoint: disabled (--http-port 0)`。
8. **`--static-root` 相对工作目录**。`server` 任务已经把工作目录设成仓库根，所以默认的 `web-client/dist` 能解析到仓库里的构建产物；自己用 `java -m` 启动时要显式给绝对路径，否则很容易落到“web client is not built”的 404。

## 接下来读什么

- 账号存在哪、信道上的字节长什么样 → [02-accounts-and-encryption.md](02-accounts-and-encryption.md)
- 想写一个插件 → [03-plugins.md](03-plugins.md)
- 想远程看一眼运行中的服务器 → [04-debug-plugin-and-cli.md](04-debug-plugin-and-cli.md)
- 想用浏览器打开同一套命令 → [05-browser-endpoint.md](05-browser-endpoint.md)
- 想理解服务器为什么这么分模块（引擎的同一套思路） → [09-architecture-and-tick-pipeline.md](../../oc-rts-engine/help/09-architecture-and-tick-pipeline.md)

## 相关源码

- 组装、选项、启动与关闭：[`OcGameServer.java`](../src/main/java/io/github/sob1234509876/oc/server/OcGameServer.java)
- 模块与构建：[`module-info.java`](../src/main/java/module-info.java)、[`build.gradle`](../build.gradle)
- 监听与线程模型：[`GameServer.java`](../src/main/java/io/github/sob1234509876/oc/server/net/GameServer.java)
- 浏览器端点：[`HttpWebServer.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/HttpWebServer.java)、[`CommandWebSocketHandler.java`](../src/main/java/io/github/sob1234509876/oc/server/ws/CommandWebSocketHandler.java)
- 自校验入口：[`ServerCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/ServerCheck.java)
