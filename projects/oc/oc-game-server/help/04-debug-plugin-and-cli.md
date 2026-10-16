# 04 · 调试插件与命令行

> `oc-debug` 是这个仓库里唯一一个真的插件（自校验里那些 `Plugin` 实现是运行时现场编译的替身）：它注册一个只读请求，用第二层加密把服务器的运行事实交给手里有 `debug.key` 的人。

## 这一篇讲什么

- 为什么"远程看一眼服务器"是**插件**而不是服务器功能，以及它注册的那个唯一的请求。
- 两层加密：`OCDBG1` 信封的字节布局、60 秒防重放窗口，以及信道已经加密了为什么还要再加一层。
- `debug.key`：位置、形状、怎么创建、权限、怎么复制。
- `INFO` 与 `INFO json` 两条请求行，以及其它任何输入得到的固定拒绝。
- 报告里的全部字段，以及每个字段从哪里来。
- `DebugCli` 的每个开关、它执行的顺序、失败时的退出码与文案。
- 只读的安全属性，以及"还没有实现"的清单。

## 它是什么，不是什么

`oc-debug` 是一个**普通插件**：`DebugPlugin` 实现 `Plugin` 接口，jar 丢进服务器的 `plugins/` 目录就生效，删掉就消失（生命周期见 [03-plugins.md](03-plugins.md)）。服务器里没有一行代码知道它存在。

| 是 | 不是 |
| --- | --- |
| `:projects:oc:oc-game-server-debug` 里的 `DebugPlugin`，走 `PluginLoader` 加载 | 不是服务器内置的调试端点；服务器没有 `--debug` 这样的开关 |
| 注册**恰好一个**请求：`engine-info`（`DebugPlugin.REQUEST_NAME`），在 `onLoad` 里注册、在 `onDisable` 里撤回 | 没有第二个请求，没有命令通道，没有后门 |
| 只**返回信息**：报告由常量与临时构造的对象拼出 | 不改引擎状态、不写用户记录、不踢人、不关服 |

它注册的名字要满足 `RequestRegistry` 的规则 `[a-z0-9-]{1,32}`（见 [03-plugins.md](03-plugins.md)）；`engine-info` 是这个名字，注册表里也永远只有这一个来自本插件。

## 一次请求的全程

```text
CLI                                  服务器                             插件
 |  --- LOGIN <名字> <密码> --------->  认证这条连接
 |  --- REQ engine-info <base64 信封> ->  base64 解码，原样交给处理器  --->  开信封
 |                                                                         按行精确比较
 |                                                                         拼报告、封回信封
 |  <-- OK <base64 信封> --------------  base64 编码，原样回送        <---  返回字节
 |  开信封、打印
```

两个前提值得单独说：

1. **必须先在**同一条连接上**成功 `LOGIN`**，否则 `REQ` 直接回 `ERR login first`，请求根本不会到达处理器（`REQ` 是唯一强制登录的命令，见 [02-accounts-and-encryption.md](02-accounts-and-encryption.md)）；
2. 服务器**只转发不透明字节**：base64 解码 → 交给处理器 → base64 编码回送，既不解析信封、也不记录载荷；载荷不是合法 base64 时**从未**交给处理器。

## 两层加密

信道本身已经是 AES-256-GCM 了（见 [02](02-accounts-and-encryption.md)），插件在它上面又套了一层自己的 AES-256-GCM 信封。`DebugEnvelope` 是这一层的全部实现，两端（插件与 CLI）用的是同一个类，所以格式不可能各自漂移。

### 信封格式

```text
"OCDBG1"     6 字节   ASCII magic；magic 不对在跑密码学之前就失败
timestamp    8 字节   大端 epoch 秒，明文发送
nonce       12 字节   每次都新取的随机字节，绝不与同一把密钥重复
ciphertext   n 字节   AES-256-GCM(明文)，尾部带 16 字节（128 位）认证标签
```

| 事实 | 值 | 在哪 |
| --- | --- | --- |
| magic | `"OCDBG1"`（`DebugEnvelope.MAGIC`） | 常量 |
| 固定头长度 | 26 字节 = 6 + 8 + 12（`DebugEnvelope.HEADER_BYTES`） | 常量 |
| 密钥长度 | 32 字节 = AES-256（`DebugEnvelope.KEY_BYTES`） | 常量 |
| 最短信封 | 42 字节（26 + 16 字节标签）；明文 `INFO` 是 46 字节 | 由上面两个常量推出，自校验断言 |
| nonce | 随机而不是计数器，因为两端没有共享计数器 | `DebugEnvelope.seal(byte[], byte[])` |
| 密钥来源 | `SecureRandom`（`DebugEnvelope.newKey()`）；模块里**没有** `java.util.Random` | `newKey()` |

两边对称：插件用 `DebugEnvelope.seal(byte[], byte[])` 封答复，CLI 用它封请求，双方都用 `DebugEnvelope.open(byte[], byte[], long)` 打开。`seal` 与 `open` 都要求密钥正好 32 字节，短一点长一点都直接拒绝，而不是悄悄降级成别的算法。

### 60 秒防重放窗口

时间戳是信封里唯一"防重放"的东西：`DebugEnvelope.open` 拒绝与读方时钟相差超过 `DebugEnvelope.MAX_CLOCK_SKEW_SECONDS`（60 秒）的信封，**两个方向都拒**。

| 情况 | 消息（英文，带具体秒数） |
| --- | --- |
| 信封太旧 | `envelope is N seconds old, which is beyond the 60 second window; it is a replayed or very stale request` |
| 信封来自未来 | `envelope is timestamped N seconds in the future, which is beyond the 60 second window; check the clock of this machine` |
| 正好在边界上 | **可以用**（自校验专门断言 60 秒整仍然能打开） |

这是个**有界的**防御，而且只能是有界的：时钟会漂移，窗口不能是零。也正因为窗口有界，插件能回答的东西必须是**无状态报告**而不是命令——窗口内重放一次也无害，因为没有任何可重复执行的动作。

### 认证标签覆盖什么

GCM 认证的是密文与传给它的 nonce，**不覆盖** magic，也**不覆盖**时间戳字段——那两个字段在密码学运行之前就被解析了。所以：

- 改 magic → magic 不符，先拒绝；
- 改时间戳 → 要么出窗被拒绝，要么仍是原值（改回原值等于没改）；
- 攻击者能做的只剩延迟或丢弃一个信封，而这已经被时间窗口限定了。

密码学失败只出一条消息：`authentication tag mismatch: the envelope was altered, or this machine holds a different debug.key`。**故意不区分**"密钥不对"与"内容被改"——区分开来等于告诉猜密钥的人"你猜得很接近"。

### 为什么还要第二层

信道的密钥是**服务器自己**协商出来的（匿名 X25519，没有证书，见 [02](02-accounts-and-encryption.md)）。所以一个能碰到服务器 socket 的人、或者将来某个把信道终止在别处的组件，看到的报告就是明文。

信封的密钥只存在 `debug.key` 里，而这个文件由操作员**手工**复制到客户端机器——和 SSH 主机密钥是同一个思路：信任来自带外传输，不来自网络。于是"谁能读报告"这件事与"谁持有信道密钥"完全解耦，只由那把 32 字节的密钥决定。

## `debug.key`

| 项 | 事实 |
| --- | --- |
| 位置 | `<服务器数据目录>/oc-debug/debug.key`，即 `PluginContext.dataDirectory()` 下按插件名建的目录 |
| 文件名 | `DebugKeyFile.FILE_NAME` = `debug.key` |
| 内容 | 64 个十六进制字符（正好 32 字节，小写写出、大小写都收），外加一个可选的结尾换行 |
| 生成 | 文件不存在时用 `SecureRandom` 取 32 字节（`DebugEnvelope.newKey()`）再写成十六进制 |
| 时机 | 第一次 `onEnable()` 的日志行要报出密钥路径，所以启用时就会创建它；`onLoad` 不碰它 |
| 并发 | 用 `CREATE_NEW` 打开；两个首启进程只有一个赢，输的那个读赢家的密钥而不是覆盖它 |
| 覆盖 | **永不覆盖**。插件不会在操作员背后重新生成密钥（那会让所有已分发的副本失效） |
| 权限 | 支持 POSIX 的文件系统上收成 `rw-------`（只属主可读写）；Windows 上没有 POSIX 视图，文件继承插件数据目录的 ACL，共享机器上该限制的是那个目录 |
| 日志 | 路径可以进日志与报告（`onEnable` 的日志与报告的 `plugin-key-file` 都是路径），**内容永远不进**；密钥文件相关的异常消息只由路径与固定文本拼出 |

怎么用：把服务器上生成的 `debug.key` **像 SSH 私钥一样手工复制**到跑 CLI 的机器。谁读得到这个文件，谁就能读全部报告；谁读不到，就什么都读不到——哪怕他能看 socket。

形状不对的文件会被**明确拒绝**，不会被补齐、也不会被哈希成一把密钥：

- 文件缺失 → `no debug key at <绝对路径>; copy the debug.key the server created into this machine`；
- 内容不是 64 个十六进制字符 → `is not 64 hexadecimal characters; delete it and let the server create a new one, then copy that file to the client`。

## 请求行

信封里的明文必须是一行 UTF-8 文本，插件按**精确比较**处理：

| 请求行 | 回答（都在一个有效信封里） |
| --- | --- |
| `INFO` | 文本报告，一行一个 `key: value` |
| `INFO json` | 同一个报告，一个 JSON 对象 |
| 其它任何东西 | `ERR unsupported request` |
| 信封打不开（magic / 长度 / 标签 / 时钟） | `ERR bad request`（原因只写进服务器的 WARNING 日志，不进答复） |
| 明文里含 `\r` 或 `\n` | `ERR bad request`（一次请求只能是一行） |

规则细节：

- 明文先 `strip()`（去首尾空白）再比较，所以 ` INFO ` 仍然可用；
- 比较是**大小写敏感**的：`info`、`Info`、`INFO JSON` 都是 `ERR unsupported request`（信封里那两层与 `REQ` 的命令名不同，命令名不敏感，请求行敏感）；
- 被拒绝的那两条**不增加** `plugin-requests-served` 计数，自校验专门断言了这一点；
- 两条拒绝语句短、固定、只对持钥者可见：能开出信封的人本来就拿着密钥，所以这句话不是秘密。

## 报告里有什么

文本报告与 JSON 报告共用同一份 `facts()`，一个 `LinkedHashMap`——**顺序是报告的一部分**，而且每次请求都重新构造，所以"上次留下的过期数字"不可能出现（自校验里唯一有状态的四项——运行时间、堆、线程数、已服务请求数——都是回答那一刻读的）。

| 字段 | 内容 | 来源 |
| --- | --- | --- |
| `schema` | `1` | `DebugPlugin` 的私有常量 `SCHEMA` |
| `server-version` | 例如 `2.0a` | `PluginContext.serverVersion()` |
| `plugin` | `oc-debug` | `Plugin.name()` |
| `plugin-version` | `1.0` | `Plugin.version()` |
| `plugin-description` | 一行英文说明 | `Plugin.description()` |
| `plugin-read-only` | `true` | 字面量 |
| `plugin-data-directory` | 插件自己的数据目录，绝对路径、已 `normalize()` | `PluginContext.dataDirectory()` 加插件名 |
| `plugin-key-file` | `debug.key` 的绝对路径 | `DebugKeyFile.file()` |
| `plugin-requests-registered` | 服务器注册表里的请求总数 | `RequestRegistry.size()` |
| `plugin-request-names` | 已注册名字，逗号分隔、按字母排序 | `RequestRegistry.names()` |
| `plugin-requests-served` | 本插件成功回答过的请求数 | 插件的 `AtomicLong` |
| `jvm-vendor` | `java.vendor` | 系统属性 |
| `jvm-name` | `java.vm.name` | 系统属性 |
| `jvm-version` | `java.version` | 系统属性 |
| `os-name` | `os.name` | 系统属性 |
| `os-version` | `os.version` | 系统属性 |
| `os-arch` | `os.arch` | 系统属性 |
| `available-processors` | 可用处理器数 | `Runtime.getRuntime().availableProcessors()` |
| `jvm-uptime-seconds` | JVM 已运行的整秒数，永不为负 | `ManagementFactory.getRuntimeMXBean().getUptime()` |
| `heap-used-bytes` | 已用堆字节 | `MemoryMXBean` 的 `getHeapMemoryUsage().getUsed()` |
| `heap-max-bytes` | 堆上限字节 | 同一个 `MemoryMXBean`，`getHeapMemoryUsage().getMax()` |
| `live-thread-count` | 存活线程数 | `ThreadMXBean.getThreadCount()` |
| `engine-hyperoperations` | 9 个超运算等级，`rank:label notation`，用 `;` 连接 | `HyperOperation.values()` 的 `rank()` / `label()` / `notation()` |
| `engine-natural-value` | 字面量 `10^^10` | 常量字符串 |
| `engine-natural-rendered` | 科学计数法渲染 | `NaturalFormat.scientific(...)` |
| `engine-natural-description` | 一段描述 | `NaturalFormat.describe(...)` |
| `engine-natural-hyperoperation` | 该数落在哪个等级的名字 | `NaturalFormat.operationOf(...)`，取不到时是 `none (the level is not a classic hyperoperation)` |
| `engine-world-dimensions` | 临时世界的维度数 | `World.dimensionCount()` |
| `engine-world-entities` | 实体数（临时世界恒为 0） | `World.entityCount()` |
| `engine-world-tick` | tick 号（临时世界恒为 0） | `World.tickNumber()` |
| `engine-world-default-dimension` | `overworld` | `World.defaultDimensionId()` |
| `engine-world-terrain` | `debug:grass` | `world.map().terrainAt(0, 0).key()` |
| `engine-chunk-size` | 16 | `ChunkedMap.chunkSize()` |
| `engine-chunk-terrain-count` | `N of 256`（命中格数 + `" of "` + 区块格数平方） | `Chunk.count(TerrainType)` 与 `Chunk.size()` |
| `engine-chunks-loaded` | 已加载区块数 | `TerrainMap.loadedChunkCount()` |
| `engine-chunks-generated` | 已生成区块数（自校验断言为 1） | `ChunkedMap.generatedChunkCount()` |

一共 **36 个字段**，全部由代码里数得出来：`facts()` 是唯一往报告里放东西的地方。

### 引擎那一段数字

三组引擎事实都不是从"一局正在跑的游戏"里读的，而是插件**自己搭出来的**：

- **超运算表**：`HyperOperation.values()` 走一遍，拼出来的完整字符串是
  ```text
  0:succession a + 1;1:addition a + b;2:multiplication a x b;3:exponentiation a^b;4:tetration a↑↑b;5:pentation a↑↑↑b;6:hexation a↑↑↑↑b;7:heptation a↑↑↑↑↑b;8:octation a↑↑↑↑↑↑b
  ```
  它证明的是"服务器类路径上这个库能做什么"，与任何一局对局无关（超运算见引擎的 [12-numbers-and-googology.md](../../oc-rts-engine/help/12-numbers-and-googology.md)）。
- **一个 `Natural`**：`Natural.tetration(Natural.TEN, Natural.of(10))` 就是十的十层幂塔。精确求值会贵到离谱，所以引擎把它保持成结构性数值，报告只显示 `10^^10` 这个符号值加两条 `NaturalFormat` 渲染——这正是值得在调试报告里展示的行为。
- **一个一次性的 `World`**：`flatWorld()` 在**处理器内部**构造：`ContentRegistry.builder("debug")` 现场建一个只有 `grass` 的内容目录，`new World(0L, content)` 建世界，`DimensionId.overworld()` + `DimensionProfile.earth()` + `new ChunkedMap(new FlatChunkGenerator(...))` 加一个平坦维度。它**从不 tick、从不存进字段、从不交给任何人**，请求结束就丢。用平坦生成器是为了让这个世界只花两张表，而不是一片噪声场。

自校验在本机量到的文本报告是 **1526 字节**（断言上限是 3072，协议行上限是 4096）；路径更长的机器上这个数会更大一些。所以报告离"答复超长导致断连"还有一倍余量（见 [02](02-accounts-and-encryption.md) 的常见坑第 5 条）。

## 命令行客户端

```shell
./gradlew :projects:oc:oc-game-server-debug:debugCli --args="--host 127.0.0.1 --port 25566 --user admin --password secret --key data/oc-debug/debug.key"
```

任务 `debugCli` 是一个 `JavaExec`，用本模块的主运行时类路径跑 `io.github.sob1234509876.oc.server.debug.cli.DebugCli`。也可以把它当模块跑：

```shell
java -m oc.projects.oc.game.server.debug/io.github.sob1234509876.oc.server.debug.cli.DebugCli \
        --host 127.0.0.1 --port 25566 --user admin --password secret --key C:\keys\oc-debug.key
```

### 全部开关

| 开关 | 默认值 | 作用 |
| --- | --- | --- |
| `--host ADDRESS` | `127.0.0.1` | 要问的服务器 |
| `--port N` | `25566` | 服务器端口；必须是 1..65535，非数字直接被拒 |
| `--user NAME` | — | 登录用的账号，**必填**且不能空白 |
| `--password TEXT` | — | 该账号的密码，**必填**且不能为空 |
| `--key FILE` | — | 从服务器复制来的 `debug.key`，**必填** |
| `--json` | 关 | 请求 `INFO json`，打印一个 JSON 对象 |
| `--help` | — | 打印帮助后退出（退出码 0） |

解析器很严：`--help` 与 `--json` 是**开关**（不取值），其余每个开关都要一个取值；不认识的选项（`unknown option '--nope'; see --help`）与缺取值的选项（`option '--host' needs a value`）都直接抛，而不是悄悄用默认值。`--help` 打印的就是：

```text
Debug CLI of the Omni Com server's oc-debug plugin

  --host ADDRESS   server to ask (default 127.0.0.1)
  --port N         server port (default 25566)
  --user NAME      account to log in with (required)
  --password TEXT  password of that account (required)
  --key FILE       the debug.key copied from the server (required)
  --json           print the report as one JSON object
  --help           this text

The plugin answers only read-only information; it cannot change the server or the game.
```

### 它按什么顺序做事

1. 读 `debug.key`（`DebugKeyFile.read(Path)`，本机自己再查一遍 64 个十六进制字符的格式，因为**这一份最容易坏**）；
2. 按 `--json` 选请求行，用信封封好（`DebugEnvelope.seal`）；
3. `GameClient.connect(host, port)` 连上去，走的是玩家用的同一条加密信道；
4. `LOGIN <名字> <密码>`，回答不以 `OK` 开头就是失败；
5. `REQ engine-info <base64 信封>`，回答不以 `OK ` 开头就是失败；
6. 把答复 base64 解回信封、用**同一把密钥**打开、`stripTrailing()` 后打印。

也就是说：CLI 除了拿了一个普通账号登录之外，**没有用任何服务器不知道的协议**——服务器不需要第二个端口、第二个监听器、也不需要为调试工具开特例。

`DebugCli.fetchReport(Map)` 是"不打印、不 `System.exit`"的整个 CLI，自校验就是靠它断言真实往返的；`main` 只负责打印与退出码。

### 失败退出

任何失败都是**退出码 1**，外加 stderr 上的一行 `debug-cli: <原因>`；成功（含 `--help`）是 0。

| 情况 | stderr 上的原因 |
| --- | --- |
| 密钥文件不存在 | `no key file at <绝对路径>; copy the debug.key the server created into this machine` |
| 密钥文件不是 64 个十六进制字符 | `the key file <路径> is not 64 hexadecimal characters; copy the server's debug.key unchanged` |
| 缺 `--user` / `--password` / `--key` | `--user is required` / `--password is required` / `--key is required: copy debug.key from the server (see the plugin's log line for its path)` |
| 端口不是数字或越界 | `--port must be a number, was '...'` / `--port must be 1..65535, was N` |
| 登录被拒 | `login as '<名字>' was refused by <host>:<端口>: <服务器的回答>` |
| 请求名没注册 | `the server refused REQ engine-info: <服务器的回答> (is the debug plugin loaded and are you logged in?)` |
| 标签不匹配 / 密钥不对 | `envelope failed to open: authentication tag mismatch: the envelope was altered, or this machine holds a different debug.key` |
| 时间戳出窗 | `envelope is N seconds old, ...` / `envelope is timestamped N seconds in the future, ...` |

五种失败在外行看来一模一样，所以每一条都**自己报出自己是哪一种**，而不是丢一句"失败了"。

## 安全属性

这一条要说得非常直白：

- **插件拿不到活游戏。** `PluginContext` 只给一个目录、一个版本字符串、一个 logger 和那张请求注册表；里面**没有世界、没有引擎、没有用户记录、没有 socket**。报告里的引擎事实全部由插件自己构造（一个临时 `World`、几张常量表），描述的是服务器类路径上的**引擎版本与构件**，不是任何一局正在跑的对局。
- **服务器只转发不透明字节。** `REQ` 只做 base64 解码/编码与转发，不解析信封、不记录载荷、不把它喂给任何游戏对象；`RequestHandler` 的合同就是"字节进、字节出"。
- **`REQ` 要求同一条连接上成功 `LOGIN` 过。** 没登录的连接拿到 `ERR login first`，处理器根本不会被调用。
- **所以这个插件能做的只有"报告"，不能"控制"。** 它没有可以写的东西：处理器不持有任何可写引用，一次请求唯一的输入是"要文本还是要 JSON"这个二选一，没有任何请求能命名一个文件、一个设置或一个游戏对象。

换句话说：**第二层信封决定"谁能读"，只读的处理器决定"能做什么"；两件事分开，所以就算密钥泄露，泄露的也只是信息面，不是控制面。**

## 常见坑

1. **`INFO` 是大小写敏感的。** 与 `REQ` 的命令名不同，信封里的请求行要逐字节精确比较；`info` 只会得到 `ERR unsupported request`。
2. **`debug.key` 不会自动重建。** 删掉它以后要重启（或重新启用）插件才会生成新的一份，而**所有旧的客户端副本同时失效**——这是故意的，静默换钥会让每个已经拿到副本的人莫名其妙地打不开信封，所以换钥永远是操作员主动做的事。
3. **两台机器的时钟差超过 60 秒就会失败**，而且报的是"replayed or very stale"或"in the future"。信封的 60 秒窗口比信道的计数器防重放更严格，先撞上的通常是它。
4. **`--json` 与 `--help` 不取值。** 写成 `--json true` 时，`true` 会被当成下一个选项而抛 `unexpected argument 'true'`。
5. **报告的字节数随路径长度增长。** 本机实测 1526 字节，但 `plugin-data-directory` 与 `plugin-key-file` 两条里带着完整绝对路径；数据目录特别深的机器上要留意 4096 字节的协议行上限（超了不是报错，是断连）。
6. **`plugin-requests-registered` 数的是整个服务器注册表，不是"这个插件注册了几个"。** 装了别的插件时它会更大；本插件自己贡献的名字看 `plugin-request-names`（正常的单插件服务器就是 `engine-info`）。
7. **`ERR bad request` 与 `ERR unsupported request` 是两句不同的话。** 前者是"信封没打开"（密钥/时钟/标签问题），后者是"信封打开了但你要的东西不存在"；调试时先分清自己拿到的是哪一句。
8. **插件停用后同一个请求名立刻不可用。** `onDisable()` 会 `unregister`，于是 `REQ engine-info` 马上变回 `ERR unknown request`，而不是"插件收到了但不回答"。

## 还没有实现

- **没有 WebSocket / HTTP 传输**：CLI 用的是和玩家一样的加密 TCP 协议，浏览器不能直接连；没有第二个监听端口。
- **还没有原版（vanilla）插件 jar**：服务器的空壳已经就位，但"原版游戏"这个插件还不存在，所以现在开服只能靠第三方插件填内容。
- **没有大厅与匹配（lobby / matchmaking）**：没有排队、房间、对局分配，调试插件也没有"列出对局"这种东西可报。
- **除了用户记录没有持久化**：`data/users.txt` 是唯一落盘的游戏相关状态；世界、对局、插件内存里的进度在进程退出后全部消失，`plugin-requests-served` 也是这样，重启从 0 开始。
- **没有任何能远程改状态的东西**：`REQ` 只走到插件自己注册的处理器，而本插件只回答报告；服务器自带的命令里没有一条能改配置、踢人、关服或写文件（完整清单见 [README.md](README.md)）。

## 相关文档

- `REQ` 的鉴权、载荷的不透明转发与协议命令表 → [02-accounts-and-encryption.md](02-accounts-and-encryption.md)
- 插件生命周期、`PluginContext` 给什么、请求缝的合同 → [03-plugins.md](03-plugins.md)
- 数据目录、`--data` 与启动顺序 → [01-getting-started.md](01-getting-started.md)
- 超运算、`Natural` 与 `NaturalFormat` → [12-numbers-and-googology.md](../../oc-rts-engine/help/12-numbers-and-googology.md)
- 引擎侧的整体架构（这份报告描述的那个模块） → [09-architecture-and-tick-pipeline.md](../../oc-rts-engine/help/09-architecture-and-tick-pipeline.md)

## 相关源码

- 插件本体：[`DebugPlugin.java`](../../oc-game-server-debug/src/main/java/io/github/sob1234509876/oc/server/debug/DebugPlugin.java)
- 信封：[`DebugEnvelope.java`](../../oc-game-server-debug/src/main/java/io/github/sob1234509876/oc/server/debug/DebugEnvelope.java)
- 密钥文件：[`DebugKeyFile.java`](../../oc-game-server-debug/src/main/java/io/github/sob1234509876/oc/server/debug/DebugKeyFile.java)
- CLI：[`DebugCli.java`](../../oc-game-server-debug/src/main/java/io/github/sob1234509876/oc/server/debug/cli/DebugCli.java)
- 模块与构建（含 `debugCli` 任务）：[`module-info.java`](../../oc-game-server-debug/src/main/java/module-info.java)、[`build.gradle`](../../oc-game-server-debug/build.gradle)
- 端到端自校验：[`DebugPluginCheck.java`](../../oc-game-server-debug/src/selfcheck/java/io/github/sob1234509876/oc/server/debug/check/DebugPluginCheck.java)
- 服务器侧的两层缝：[`RequestHandler.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/RequestHandler.java)、[`RequestRegistry.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/RequestRegistry.java)、[`GameServer.java`](../src/main/java/io/github/sob1234509876/oc/server/net/GameServer.java)、[`GameClient.java`](../src/main/java/io/github/sob1234509876/oc/server/net/GameClient.java)
- 报告引用的引擎与库：[`FlatChunkGenerator.java`](../../oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/FlatChunkGenerator.java)、[`HyperOperation.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/HyperOperation.java)、[`NaturalFormat.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/NaturalFormat.java)
