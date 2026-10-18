# Omni Com 游戏服务器 · 使用文档

> 面向使用者的分主题文档：服务器怎么启动、账号与加密怎么工作、插件怎么加载、浏览器端点怎么工作、调试插件怎么用。

这套文档对应仓库里的两个模块。

| 模块 | Gradle 路径 | Java 模块 / 包 | 是什么 |
| --- | --- | --- | --- |
| 游戏服务器 | `:projects:oc:oc-game-server` | `oc.projects.oc.game.server` / `io.github.sob1234509876.oc.server` 及 `.auth`、`.net`、`.plugin`、`.ws`、`.world` | 纯 Java 17、**零第三方依赖**的 TCP 服务器，外加可选的浏览器 HTTP/WebSocket 端点与服务器世界：用户记录、加密信道、插件加载器、定时保存、只读请求缝、`/world/snapshot` |
| 调试插件 | `:projects:oc:oc-game-server-debug` | `oc.projects.oc.game.server.debug` / `io.github.sob1234509876.oc.server.debug` 及 `.cli` | 只读调试插件 `oc-debug`，以及它的命令行客户端 |

服务器刻意保持"什么都不懂"：它只拥有插件不该拥有的三样东西——监听 socket、用户记录、把记录写盘的定时器。浏览器端点旁边还有一个由 `server.world.WorldHost` 托管的 256×256 噪声世界（可建造出生锚点、窗口快照），只通过 `/world/snapshot` 公开只读快照（见 [05-browser-endpoint.md](05-browser-endpoint.md)）；插件系统本身仍然不知道引擎、socket 或用户记录的存在（见 [03-plugins.md](03-plugins.md)）。调试插件与服务器现在都依赖 RTS 引擎，前者报告引擎的版本与构件，后者托管世界（见 [04-debug-plugin-and-cli.md](04-debug-plugin-and-cli.md)）。

文中所有类型名、方法名、默认值、字节数与断言数都取自这两个模块的源码与它们的自校验输出，不是示意；每篇末尾给出「相关源码」的相对路径。

## 三条阅读路线

**A. 先把服务器跑起来（约 10 分钟）**

1. [01-getting-started.md](01-getting-started.md) —— 构建、跑自校验、命令行选项、启动与关闭顺序、六个包的分工
2. [04-debug-plugin-and-cli.md](04-debug-plugin-and-cli.md) —— 装一个真插件，看它返回什么
3. [05-browser-endpoint.md](05-browser-endpoint.md) —— 用浏览器打开同一套命令

**B. 要写插件**

- 生命周期、发现顺序、**依赖规则**、请求缝、最小可编译例子 → [03-plugins.md](03-plugins.md)

**C. 要动账号、信道，或排查连接问题**

- 用户文件格式、PBKDF2 参数、`AuthResult` 的中文文案 → [02-accounts-and-encryption.md](02-accounts-and-encryption.md)
- 握手字节、帧格式、命令表、加固措施 → [02-accounts-and-encryption.md](02-accounts-and-encryption.md)
- 浏览器端点、静态文件、来源规则、WebSocket 帧规则 → [05-browser-endpoint.md](05-browser-endpoint.md)
- 信封、`debug.key`、报告字段、CLI 退出码 → [04-debug-plugin-and-cli.md](04-debug-plugin-and-cli.md)

## 全部文档

| 文件 | 讲什么 | 什么时候读 |
| --- | --- | --- |
| [01-getting-started.md](01-getting-started.md) | `OcGameServer` 的选项与默认值、插件与数据目录、启动输出、关闭顺序、包结构表 | 第一次接触这个服务器 |
| [02-accounts-and-encryption.md](02-accounts-and-encryption.md) | `UserStore` / `LocalUserStore`、PBKDF2、`AuthService` 的锁、`AuthResult` 的文案、握手与帧格式、命令表、加固 | 想知道账号存在哪，或要看懂线上的字节 |
| [03-plugins.md](03-plugins.md) | `Plugin` 生命周期、`PluginContext`、描述文件、发现顺序、依赖规则、为什么不关类加载器、请求缝、最小插件 | 想写插件，或插件加载行为不符合预期 |
| [04-debug-plugin-and-cli.md](04-debug-plugin-and-cli.md) | `oc-debug` 注册的请求、两层加密、`debug.key`、报告字段、CLI 的全部开关与失败退出、只读的安全属性 | 想远程看一眼服务器 |
| [05-browser-endpoint.md](05-browser-endpoint.md) | `--http-port` / `--static-root` / `--ws-idle` / `--world` / `--seed`、两套版本机制、同一套命令语法、来源规则、静态路由、插件资源路由、世界快照路由、WebSocket 帧规则与心跳 | 想用浏览器打开，或排查静态文件/来源/帧/快照问题 |

## 30 秒了解这个服务器

```text
> gradlew :projects:oc:oc-game-server:server --args="--port 25566 --plugins plugins --data data"
Omni Com 2.0a listening on 0.0.0.0:25566
plugins: 1 from C:\srv\plugins
accounts: 0, records in C:\srv\data
browser endpoint: http://0.0.0.0:25567/ (static root web-client/dist)
```

走加密 TCP 时，之后所有对话都是**一条加密信道上的、一行一条命令的文本**：

```text
PING                            -> PONG
REGISTER alice S3cret-Passw0rd! -> OK 登录成功。
LOGIN alice S3cret-Passw0rd!    -> OK 登录成功。
COUNT                           -> OK 1
REQ engine-info <base64 信封>    -> OK <base64 信封>     （需要先 LOGIN 成功）
QUIT                            -> OK bye（随后服务器关掉这条连接）
```

一句话概括设计：**服务器自己只做"端口 + 账号 + 插件目录 + 定时保存"；游戏内容是插件；插件能对客户端说的话只有它自己注册的只读请求。**

## 自校验

```shell
./gradlew :projects:oc:oc-game-server:selfcheck          # game server self check: all 789 checks passed.
./gradlew :projects:oc:oc-game-server-debug:selfcheck    # debug plugin self check: all 82 checks passed.
```

两个自校验都不需要网络、不需要测试框架。服务器那一个 789 条断言分十一组：账号与本地存储 65 条、加密信道与 TCP 协议 57 条、插件加载器 76 条、`REQ` 命令 43 条、WebSocket 端点 88 条、HTTP 静态路由 47 条、插件资源路由 80 条、世界主机与快照路由 163 条、插件 tick 钩子 23 条、游戏命令 87 条、玩家基地 60 条；调试插件那一个 82 条分四组：信封 15 条、密钥文件与 `engine-info` 报告 34 条、请求注册表 14 条、真实 socket 端到端 19 条。插件那一组会在运行时用 JDK 自带的编译器真的生成插件 jar，落到同一个临时目录里再加载它，所以它测的是真实的类加载器行为，不是替身。

自校验不是框架测试，所以它放在独立的 `selfCheck` 源集（`src/selfcheck/java`），由上面的任务以普通程序运行；`src/test/java` 留给以后的单元测试。调试插件的自校验复用服务器那一个的断言记录器，只在自己的 `build.gradle` 里把服务器的 selfCheck 输出挂上类路径。

## 还没有实现

- **浏览器端点没有 TLS**：它是明文 HTTP/WS，适合本机开发；要暴露到不可信网络，得自己套反向代理或 SSH 端口转发。
- **还没有原版（vanilla）插件 jar**：服务器的空壳已经就位，但"原版游戏"这个插件还不存在，所以现在开服只能靠第三方插件填内容。
- **没有大厅与匹配（lobby / matchmaking）**：没有排队、房间、对局分配。
- **除了用户记录没有持久化**：`data/users.txt` 是唯一落盘的游戏相关状态；世界、对局、插件内存里的进度在进程退出后全部消失。
- **没有远程修改命令**：`REQ` 只能走到插件自己注册的处理器，而服务器自带的命令里没有任何一条能改配置、踢人、关服或写文件。
- **没有会话令牌**："已登录"只挂在当前这条连接上；换个连接要重新 `LOGIN`。
- **没有热重载**：插件只在启动时加载、关闭时停用；再调一次加载只会再扫一遍目录并追加。
- **没有社交与管理功能**：没有好友、聊天、排行榜、管理员角色；调试插件的 `--user` 只是一个普通账号。

## 文档约定

- 代码块中的 API 与源码一致；标了 `示意` 的片段未编译验证，本文档中只有 [03](03-plugins.md) 末尾的最小插件是完整可编译的写法。
- 每个子系统只在一篇文档里详述，其它文档用链接引用。
- 所有相对路径以 `projects/oc/oc-game-server` 为基准（服务器源码在 `src/main/java`，自校验在 `src/selfcheck/java`）；调试模块的源码用 `../../oc-game-server-debug/...` 引用。
- 需要背景知识时看引擎的两篇：[09-architecture-and-tick-pipeline.md](../../oc-rts-engine/help/09-architecture-and-tick-pipeline.md)（包与管线）和 [12-numbers-and-googology.md](../../oc-rts-engine/help/12-numbers-and-googology.md)（`Natural` 与超运算）；序数层见 [`../../oc-googology/help/ordinals.md`](../../oc-googology/help/ordinals.md)。
