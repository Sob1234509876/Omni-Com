[English](oc-protocol.md) | 简体中文

# OC/1 协议

> 这是浏览器客户端与 Java 服务器之间唯一的线上约定。两边的实现都必须服从本文；本文不记录实现历史，也不描述某个具体客户端。

## 这一篇讲什么

- 传输：WebSocket 路径、一个文本帧一条命令、`HELLO 1` 版本握手，以及空闲超时与服务端心跳。
- 命令表，以及每条命令的确切回答。
- `REQ` 的登录前提、base64 约定与错误词汇。
- 游戏命令：`WHOAMI`、`ORDER`、`BUILD` 与 `TRAIN`，账号名身份规则、登录与所有权规则，以及世界步边界。
- `oc-debug` 的第二层信封：字节布局、密钥、60 秒防重放窗口。
- 插件资源：`/plugin-assets` 上的普通 HTTP 内容，不属于命令协议。
- 世界快照：`/world/snapshot` 上的普通 HTTP 内容，同样不属于命令协议。
- 版本兼容规则，以及怎么用 pnpm 起浏览器客户端。

## 传输

| 项 | 约定 |
| --- | --- |
| WebSocket 地址 | `ws://<host>:25567/ws` |
| HTTP 地址 | `http://<host>:25567/`，提供同一个 `web-client/dist/` 的静态文件、`/plugin-assets` 下的插件资源，以及 `/world/snapshot` 的世界快照 |
| 帧类型 | 只使用 **TEXT** 帧；二进制帧不是命令，服务端回 ERR 并保持连接，客户端忽略并提示 |
| 帧内容 | 每个 TEXT 帧**恰好一条命令行**，UTF-8，不含 CR/LF |
| 回答 | 每条命令**恰好一行**回答；客户端不得把多条命令塞进一个帧 |
| 关闭 | 正常路径上只有 `QUIT` 关闭连接；握手失败会关闭；超时或违反帧规则时实现可以关闭，原因可读 |

客户端连接后不能立刻发命令：**第一个帧必须是版本握手**（见下一节）。服务器在握手成功之前不接受其它命令。命令行里的命令字（`PING`、`REQ` 等）**大小写不敏感**，参数按原样传递，不做大小写折叠。

### 空闲超时与心跳

服务器负责让打开的 WebSocket 保持存活。它每 **25 秒**给每条打开的连接发一个空的 WebSocket **ping** 控制帧；
按 RFC 6455，合规客户端会自动用 pong 回答，这些 pong 就是让连接的读超时不断刷新的入站流量。这是传输层
分帧，不是 OC/1 命令：不会出现任何文本行，客户端也不得把它显示出来。

- 空闲超时默认 **300 秒**。服务器上的 `--ws-idle <seconds>` 可以改它；必须是正整数秒。连接在这段时间内
  没有任何入站帧，就以 `1001` 关闭。
- ping 间隔是 25 秒，不是命令行选项。它比默认超时短得多，所以只靠服务器保活的页面也会在超时之前回 pong。
- 不回 pong 的客户端仍然按普通空闲超时关闭；回答 pong 的客户端无需用户操作就能一直保持连接。
- 文本命令 `PING` -> `PONG` 与这个心跳是两回事；客户端发来的 ping 控制帧仍然用同一个 payload 的 pong 控制帧回答。
- `--ws-idle 60` 会复现旧的 60 秒关闭，它是诊断手段，不是默认值。

这个心跳不增加命令、回答、文本帧或版本号：它只增加标准 WebSocket 控制帧，所以仍然属于 OC/1。

## 版本握手

升级成 WebSocket 之后，客户端发送的第一个帧必须是：

```text
HELLO 1
```

服务器回答：

```text
OK OC/1
```

- `HELLO` 后面的数字是协议版本，当前为 `1`；`OC/1` 是同一个版本的完整名字。
- 第一帧不是 `HELLO 1`（包括 `HELLO 2`、`HELLO`、直接发命令）时，服务器**关闭连接**并给出明确的错误信息，不执行任何命令。
- 客户端收到的东西不是 `OK OC/1` 时，必须把连接当作不可用，不得继续发送命令。

握手失败时服务器先回一行错误再关闭；客户端只需判断它不是 `OK OC/1`，不必依赖具体文案：

| 错误行 | 触发 |
| --- | --- |
| `ERR HELLO first` | 第一帧不是 `HELLO` |
| `ERR malformed HELLO` | `HELLO` 缺版本号、多参数或版本号不是十进制数字 |
| `ERR unsupported version, this server speaks OC/1` | 版本号不是 `1` |

握手的版本号与本文的版本号是同一个东西：本文改变已有线上约定时，版本号就变（见“版本兼容”）。增加一条旧客户端永远不会发送的命令不改变已有约定；下面的游戏命令就是这样的增量。

## 命令表

握手成功后，每条命令一行，每条命令一行回答：

| 命令 | 回答 | 说明 |
| --- | --- | --- |
| `PING` | `PONG` | 连通性探测，不改变任何东西 |
| `REGISTER <name> <password>` | `OK <message>` 或 `ERR <message>` | 注册账号；成功与失败的文案由服务器给出 |
| `LOGIN <name> <password>` | `OK <message>` 或 `ERR <message>` | 认证**这条连接**；成功后这条连接才能发 `REQ` 与游戏命令 |
| `COUNT` | `OK <n>` | 已注册账号数，`n` 是十进制非负整数 |
| `REQ <name> <base64-payload>` | `OK <base64-payload>` 或 `ERR <reason>` | 调用一个已注册的只读请求；需要同一条连接先 `LOGIN` |
| `WHOAMI` | `OK player <id>` 或 `ERR <reason>` | 这条连接所用账号绑定的引擎玩家 id；需要 `LOGIN` |
| `ORDER ...` | `OK` 或 `ERR <reason>` | 移动与战斗命令；需要 `LOGIN` 与所有权；见“游戏命令” |
| `BUILD ...` | `OK <buildingId>` 或 `ERR <reason>` | 放置一个要付费的建筑工地；需要 `LOGIN` 与所有权；见“游戏命令” |
| `TRAIN ...` | `OK` 或 `ERR <reason>` | 在属于自己的生产建筑里排入一个单位；需要 `LOGIN` 与所有权；见“游戏命令” |
| `QUIT` | `OK bye` | 服务器先回这一行，然后关闭连接 |

`REGISTER` 与 `LOGIN` 的 `<message>` 是给人看的文本，客户端只能依赖开头的 `OK` / `ERR` 判断成败，
不要解析或比较整句文案。`COUNT` 的 `n` 是唯一的载荷；`QUIT` 的回答精确为 `OK bye`。

### `REQ` 的规则

`REQ` 要求**同一条连接上成功 `LOGIN` 过**。`REQ` 的 `<name>` 是注册名，按原样精确匹配；`engine-info`
全小写，`ENGINE-INFO` 不会被折叠成它。失败答案是固定短句，按下面的优先级：

| 情况 | 回答 |
| --- | --- |
| 这条连接还没有成功登录 | `ERR login first` |
| 名字没有注册（或名字不合法、载荷缺失、载荷不是合法 base64） | `ERR unknown request` |
| 处理器抛出异常，或返回 `null` | `ERR request failed` |
| 其它由处理器决定的拒绝 | `ERR <reason>` |

成功时回答 `OK <base64-payload>`。服务器只把请求载荷的 base64 解码成**不透明字节**交给处理器，
再把处理器返回的字节 base64 编码回送；它不解析载荷、不记录载荷内容，也不认识 `oc-debug` 的信封。

`REQ` 和每一条游戏命令（`WHOAMI`、`ORDER`、`BUILD`、`TRAIN`）都要求同一条连接成功 `LOGIN` 过。`PING`、`COUNT`、`REGISTER`、`LOGIN`、`QUIT` 都不要求登录。

`hello` 是 `oc-hello` 插件提供的示例请求：载荷是普通 UTF-8 名字（不是 `oc-debug` 那样的信封），回答也是普通 UTF-8 文本。

## 游戏命令

成功 `LOGIN` 之后，连接可以用下面的命令操作单位、建筑与游戏经济。它们属于 OC/1：命令字、回答与帧规则
在 WebSocket 通道和加密 TCP 通道上完全一致，因为两个传输执行的是同一个命令分发器。

### 语法

```text
WHOAMI                                      -> OK player <id>            (需要 LOGIN)
ORDER MOVE <entityId> <x> <y>               -> OK                        (需要 LOGIN，实体必须属于该玩家)
ORDER ATTACK <entityId> <targetId>          -> OK                        (需要 LOGIN，实体必须属于该玩家)
ORDER ATTACKMOVE <entityId> <x> <y>         -> OK                        (需要 LOGIN，实体必须属于该玩家)
ORDER STOP <entityId>                       -> OK                        (需要 LOGIN，实体必须属于该玩家)
BUILD <engineerId> <buildingPath> <x> <y>   -> OK <buildingId> | ERR ...  (需要 LOGIN，工程师必须属于该玩家)
TRAIN <buildingId> <unitPath>               -> OK | ERR ...              (需要 LOGIN，建筑必须属于该玩家)
```

- `<entityId>`、`<targetId>` 与 `<buildingId>` 是 `/world/snapshot` 里的十进制实体 id。
- `<x>` 与 `<y>` 是十进制地图格坐标。
- `<buildingPath>` 与 `<unitPath>` 是不带命名空间的内容路径，例如 `building.armory.t1` 或
  `unit.melee.t4`。
- 命令字与其它命令一样大小写不敏感；`ORDER` 的子命令也大小写不敏感（`MOVE` 与 `move` 等价）。
  内容路径与参数不做大小写折叠。

### 登录与身份

本节每一条命令都要求**同一条连接成功 `LOGIN` 过**。没有登录时服务器精确回答 `ERR login first`，
不触碰任何东西，和 `REQ` 完全一样。成功登录之后，**账号名就是身份**——不是 socket，也不是连接。
世界在第一条游戏命令（包括 `WHOAMI`）时惰性创建这个身份：

- 玩家 `1` 拥有服务器搭建的样例场景；玩家 `2` 保留给经典插件的敌方 AI。因此**新的账号名**拿到的是从
  `3` 开始的最小未占用 id。主机的世界上限是 `8` 个玩家，所以默认世界提供 `3` 到 `8` 的真人 id：
  最多六个不同的账号名。
- 这个 id 就是引擎 `Player` 的 id，也是 `/world/snapshot` 实体 `player` 字段里的同一个数字。
- `WHOAMI` 回答 `OK player <id>`，并在首次使用时创建玩家。id 在世界的整个生命周期里都绑定这个账号名，
  所以断开不会释放它；之后用同一个账号——关闭后重连、刷新页面或新开一个标签页——得到的是**同一个 id、
  同一座基地、同一批实体与同一份资源**，不会多出第二座基地，也不会重复发一份开局资源。
- **不同的**账号名总是得到不同的 id 与自己的基地。六个名额都用完时，`WHOAMI`（以及任何会创建身份的
  游戏命令）回答 `ERR world full`。
- 连接已经消失的玩家仍然存在。**本版本不会自动回收空闲账号**：回收名额要么让该玩家的实体失去归属，
  要么把它们交给一个新账号，所以账号会留在世界里，直到世界本身被重建。
- 同一条连接重新登录只会重置这条连接缓存的 id；下一条游戏命令会重新向世界询问。同名登录复用同一个玩家，
  换名登录得到那个名字自己的玩家，前一个玩家留在世界里。

### 所有权、存在性与内容

每条命令在世界应用它时校验，拒绝会说明违反了哪条规则：

| 情况 | 回答 |
| --- | --- |
| 实体或建筑不存在、已死亡、或种类不对（例如把建筑 id 放进 `ORDER MOVE`） | `ERR unknown entity` |
| 实体或建筑属于另一个玩家 | `ERR not yours` |
| 参数个数不对，或 id/坐标不是十进制数字 | `ERR bad arguments` |
| `BUILD` 的建筑路径不在服务器提供的内容里 | `ERR unknown building` |
| `TRAIN` 的单位路径不在服务器提供的内容里 | `ERR unknown unit` |
| `TRAIN` 指向一个尚未完工的工地 | `ERR building not finished` |
| `TRAIN` 指向一个没有生产标签的建筑 | `ERR building cannot produce` |
| `TRAIN` 的单位在该建筑里没有任何配方 | `ERR no recipe for <unit> in <building>` |
| `BUILD` 无法从玩家的仓库付款 | `ERR cannot afford <building> (needs <cost>)` |

### 成本、生产与世界步

- `BUILD` 在**放置工地时一次性**从玩家的仓库网络扣除建筑类型的成本。预留与扣款和放置发生在同一个世界步里，
  生产任务无法在中间花掉同一批物资。玩家付不起时什么也不放置、什么也不扣；放置失败会释放预留。
- `TRAIN` 在属于自己的、已完工的生产建筑里排入一个单位。建筑等级够不够由引擎的生产系统决定，而不是由命令
  解析器决定：在 t1 兵营里训练 t4 单位会得到带生产系统理由的 `ERR ...`，队列保持不变。
- **世界步边界。** 客户端命令在 socket 线程到达，但世界在自己的线程上 tick。每条游戏命令都进入一个队列，
  并在**下一个固定步的起点**、引擎推进模拟之前被应用。队列是 FIFO：命令按提交顺序逐条应用，回答在命令
  被应用时产生（而不是在解析时）。因此命令永远不会在世界 tick 时改动世界，相同的命令顺序总是产生相同的
  模拟。如果世界在服务器的命令超时内没有应用命令，客户端得到 `ERR world busy`；没有世界在托管、或世界没有
  在 tick 时，回答是 `ERR world not available` 或 `ERR world not running`。

### 兼容性

游戏命令是增量扩展。它们不改变任何已有命令、回答、帧规则或握手：`PING`、`REGISTER`、`LOGIN`、`COUNT`、
`REQ` 与 `QUIT` 的回答和以前完全一样，协议仍然是 `OC/1`。从不发送这些命令的旧客户端不受影响。

## base64 约定

- 使用 RFC 4648 第 4 节的标准字母表（`A`–`Z`、`a`–`z`、`0`–`9`、`+`、`/`），带 `=` 补齐。
- 不使用 base64url（没有 `-` 和 `_`），不插入空白、换行或前缀。
- 命令里的 `<base64-payload>` 必须能成功解码，否则服务器在到达处理器之前就回 `ERR unknown request`。
- 回答里的 `<base64-payload>` 是**原始字节**的编码；客户端必须先解码，再按请求自己的格式解释。`oc-debug` 的信封是二进制，因此对浏览器来说，base64 只是为了让信封能穿过一行文本。

## `engine-info` 信封

`oc-debug` 插件注册的请求名是 `engine-info`。它要求登录，按 `REQ` 的常规规则调用；它额外把请求和回答
封在第二层 AES-256-GCM 信封里。服务器把这层信封当成**不透明的 base64 字节**；只有持钥的插件与客户端
按下面的格式打开它。

### 密钥

- 密钥是 32 字节（AES-256），来自服务器的 `data/oc-debug/debug.key`。
- `debug.key` 的内容是 64 个十六进制字符（大小写都收），可以带一个结尾换行；浏览器把它解析成 `Uint8Array(32)`。
- 这个文件由服务器首次启用插件时生成，**永不覆盖**；操作员像复制 SSH 私钥一样**手工**把它复制到客户端机器。密钥不经过网络传输，也不由浏览器生成。
- 密钥长度不是 32 字节、或文本不是 64 个十六进制字符时，客户端必须在本地拒绝，不能悄悄补齐或哈希。

### 字节布局

一个信封是下面这些字节依次拼接：

| 偏移 | 长度 | 字段 | 内容 |
| --- | --- | --- | --- |
| 0 | 6 | magic | ASCII `OCDBG1`，固定不变 |
| 6 | 8 | timestamp | 无符号大端 epoch 秒（UTC） |
| 14 | 12 | nonce | 每次封包新取的随机字节，不得与同一把密钥重复 |
| 26 | n | ciphertext | AES-256-GCM 密文，尾部带 16 字节（128 位）认证标签 |

- 固定头是 26 字节；最短信封是 42 字节（26 + 16 字节标签）。
- 密钥 32 字节，算法固定为 AES-256-GCM，不使用 AAD（附加认证数据为空）。
- GCM 认证的是密文与 nonce；magic 与 timestamp 在密码学运行之前单独检查（magic 不符直接拒绝，timestamp 出窗直接拒绝）。
- nonce 是随机的而不是计数器：两端没有共享计数器，服务器可以重启，客户端可以换机器。

### 明文

信封里的明文是一行 UTF-8 文本，`oc-debug` 按**精确比较**处理：

| 明文请求 | 明文回答 |
| --- | --- |
| `INFO` | 文本报告，一行一个 `key: value` |
| `INFO json` | 同一个报告的 JSON 对象 |
| 其它任何内容 | `ERR unsupported request` |
| 信封打不开（magic、长度、标签、时钟） | `ERR bad request` |

- 明文先去掉首尾空白再比较；比较是大小写敏感的，`info` 与 `INFO JSON` 都不被接受。
- 信封打不开时，插件仍然用**同一把密钥**封一个明文为 `ERR bad request` 的信封回来。所以传输层看到的仍是 `OK <base64>`，错误在信封里面。
- 报告字段属于 `oc-debug` 的报告格式，不属于传输协议；客户端应容忍未知字段，只显示自己认识的或原样显示文本。

### 60 秒防重放

timestamp 是信封里唯一防重放的东西，读方必须检查**两个方向**：

- 信封时间戳比本机时间早超过 60 秒：拒绝，视为重放的旧请求。
- 信封时间戳比本机时间晚超过 60 秒：拒绝，视为时钟错误。
- 正好相差 60 秒：**接受**（边界属于窗口内）。

这就是“60 秒窗口”的全部含义。它是有界的防御：时钟会漂移，所以窗口不能是零；也正因为有界，`engine-info` 只能是无状态报告，不能是有副作用的命令。

### 浏览器实现提示

`sealEnvelope` 与 `openEnvelope` 用 WebCrypto 的 `crypto.subtle` 实现 AES-256-GCM。
浏览器只在**安全上下文**里提供 `crypto.subtle`：

- `http://127.0.0.1:25567/` 与 `http://localhost:5173/` 是安全上下文，可以用。
- 直接用 `http://<局域网 IP>:25567/` 打开页面时，浏览器通常**不提供** `crypto.subtle`，此时
  `engine-info` 无法工作；请用 localhost、SSH 端口转发或 HTTPS 反向代理。

## 错误词汇

传输层的错误回答都以 `ERR ` 开头，一条命令只得到一行：

| 回答 | 含义 |
| --- | --- |
| `ERR login first` | 这条连接没有成功 `LOGIN`，`REQ` 或游戏命令被拒绝 |
| `ERR unknown request` | `REQ` 的名字没有注册，或请求形状/base64 不合法 |
| `ERR request failed` | 处理器抛异常或返回了 `null` |
| `ERR bad arguments` | 游戏命令参数个数不对，或 id/坐标不是十进制数字 |
| `ERR unknown entity` | 游戏命令指向不存在、已死亡或种类不对的实体/建筑 |
| `ERR not yours` | 游戏命令指向另一个玩家的实体/建筑 |
| `ERR unknown building` / `ERR unknown unit` | `BUILD`/`TRAIN` 的内容路径不在服务器提供的目录里 |
| `ERR world not available` / `ERR world not running` / `ERR world stopped` / `ERR world full` / `ERR world busy` | 没有托管世界、世界没有在 tick、世界在应用命令前停止、没有空余玩家位、或世界没有及时应用命令 |
| `ERR <message>` / `ERR <reason>` | `REGISTER`、`LOGIN`、`REQ` 与游戏命令的其它拒绝，文案由服务器、引擎或处理器给出 |

信封里的错误是另一层，不会变成传输层错误：

| 明文回答 | 含义 |
| --- | --- |
| `ERR unsupported request` | 信封打开了，但明文不是 `INFO` 或 `INFO json` |
| `ERR bad request` | 信封没打开：magic、长度、标签或时间窗口不合法 |

客户端必须分开报告这两层：`ERR bad request` 通常是 `debug.key` 不对或两台机器时钟差太多，`ERR unsupported request` 通常是请求行写错。

## 插件资源

贴图和其它插件资源是**普通 HTTP 内容**，不是 OC/1 消息：客户端用 `fetch` 读取，与 WebSocket 无关，也不需要登录。

| 路由 | 内容 |
| --- | --- |
| `GET /plugin-assets/manifest.json` | 所有已加载插件的图集清单合并成的 JSON 数组 |
| `GET /plugin-assets/<插件 id>/<文件>` | 该插件 jar 内 `assets/<文件>` 的原始字节 |

- 清单是数组，顺序就是插件加载顺序；每个元素是
  `{"plugin": "<插件 id>", "atlasUrl": "...", "tilePixels": ..., "tiles": {...}, "entities": {...}}`。
  `atlasUrl` 由服务器根据插件清单里的 `image` 改写，插件清单本身不含绝对 URL。
- 没有插件发布清单时，清单路由回 `200` 和 `[]`，不是 `404`。
- 清单的 `Content-Type` 是 `application/json; charset=utf-8`；文件按扩展名给 MIME 类型（`image/png`、
  `application/json` 等），未知扩展名是 `application/octet-stream`。两者都带 `Content-Length`，
  并且一律 `Cache-Control: no-cache`。
- 只允许 `GET` 和 `HEAD`；其它方法回 `405`，并带 `Allow: GET, HEAD`。
- 含 `..`、编码点（`%2e`）、反斜杠、NUL、盘符或空插件 id 的路径回 `400`；规范化后不在 `assets/` 下的路径回
  `403`；未知插件、缺失文件、没有 `assets/` 的插件都回 `404`。
- 服务器只通过插件自己的类加载器读取资源，从不为这个路由访问磁盘上的插件目录，所以插件无法借它读到服务器文件。
- 这条路由和静态页面共用 `--http-port` 的端口；`--http-port 0` 时两者都不存在。
- **它不改变本文的任何命令、回答、帧规则或版本号。** 客户端拿不到图集时退回程序化绘制，协议面板照常工作。
- 多个插件提供同一地形或实体时，客户端按数组顺序**后者覆盖前者**；清单格式见
  [`web-client/public/textures/README.md`](../web-client/public/textures/README.md)。

## 世界快照

服务器还可以为浏览器画面发布一个活的世界。快照是**普通 HTTP 内容**，和插件资源完全一样，不是 OC/1 消息：客户端用 `fetch` 读取，与 WebSocket 无关，也不需要登录。世界是**无界**的：服务器托管一张 `InfiniteTerrainMap`，唯一的上限是内存里的已加载区块预算（`--chunks N`，默认 `4096`），所以任意有符号 tile 坐标都有地形，而一份快照只带其中一个正方形窗口。

| 路由 | 内容 |
| --- | --- |
| `GET /world/snapshot` | 服务器世界某个窗口在当前 tick 的 JSON 快照 |
| `GET /world/snapshot?cx=<tileX>&cy=<tileY>&r=<radiusInTiles>` | 指定窗口的同一份文档 |

| 参数 | 默认 | 允许范围 | 含义 |
| --- | --- | --- | --- |
| `cx` | 出生锚点（`spawnAnchor().x()`，在 `(0,0)` 附近 64 格内） | **任意有符号 `int`**，没有地图边界 | 窗口中心的 tile X |
| `cy` | 出生锚点（`spawnAnchor().y()`） | **任意有符号 `int`**，没有地图边界 | 窗口中心的 tile Y |
| `r` | `32` | 夹到 `16..64` | 窗口半径，单位 tile |

窗口是边长 `2 * r` 的正方形，所以默认 64×64，最大 128×128。**只有 `r` 会被夹紧。** `cx` / `cy` 原样使用，所以原点是 `cx - r` / `cy - r`：可以是负数（例如 `cx=0` 给出 `originX=-16`），也可以很大（例如 `cx=100000&cy=-50000` 给出 `99984,-50016`），没有边缘会把窗口往里推。不带参数就是出生锚点 + 默认半径，旧客户端因此仍然能拿到一份合法文档。数字格式错误的值回落到默认值。

```json
{
  "tick": 42,
  "originX": -32,
  "originY": -32,
  "width": 64,
  "height": 64,
  "tilePixels": 16,
  "tiles": [["grass", "sand", "..."], ["...", "...", "..."]],
  "entities": [
    {"id": 1, "kind": "warehouse", "x": -12.5, "y": -3.5, "player": 1, "health": 1.00},
    {"id": 4, "kind": "worker", "x": -10.2, "y": 12.3, "player": 1, "health": 1.00}
  ],
  "players": [
    {"id": 1, "name": "Player 1", "gold": 0, "wood": 0},
    {"id": 3, "name": "alice", "gold": 300, "wood": 60}
  ]
}
```

- 成功回答是 `200`，`Content-Type: application/json; charset=utf-8`，`Cache-Control: no-store`；只允许 `GET` 与 `HEAD`，其它方法回 `405` 并带 `Allow: GET, HEAD`。
- `originX` / `originY` 是窗口左上角的**世界** tile；`width` / `height` 是**窗口**的宽高（`2 * r`）。文档**不带** `mapWidth` / `mapHeight` / `mapSize`：世界没有地图大小，客户端必须把世界当成无界的。`--chunks` 限制的是内存中保留的区块数，不是可访问的坐标。
- `tiles` 是窗口内的 row major 数组，恰好 `width * height` 个条目；每个条目是一条地形内容路径（`water`、`sand`、`grass`、`forest`、`hill`、`rock`，取决于服务的内容目录）。地形来自确定性的种子噪声场：同一个 seed、同一份内容在任意坐标复现同一块地形，`100000,-50000` 的窗口与生成器该处逐格一致。
- `entities` 按 id 排序，并且**不按窗口裁剪**：它仍然是整个世界所有存活实体的列表，`x` / `y` 是**绝对世界坐标**（实体中心，单位 tile，保留一位小数），可以是负数。客户端用 `originX` / `originY` 把实体换算到窗口内；窗口外的实体也留在数组里。`kind` 是引擎的内容路径（`worker`、`soldier`、`medic`、`warehouse`、...），单位与建筑形状相同。
- `players` 按 id 排序，世界里存在的每个玩家各一行（样例场景、AI 与每个真人账号），带玩家 id、显示名和两种开局资源，金额都是非负整数。扁平键 `gold` 与 `wood` 就是经典内容目录注册的物品路径；快照不重复它们的显示名（金币 / 木材），其它物品类型暂时不进这份文档。金额是该玩家仓库当前可用的数量——也就是引擎的投放点规则、建造/训练成本检查读取的同一个来源——所以没有仓库的玩家两项都报 `0`。这个字段是增量字段：旧服务器可以不带它，客户端必须在没有它时照常工作。浏览器客户端用 `WHOAMI` 回答的 id 匹配对应行，在 HUD 显示名字、金币与木材；还没有这一行时显示破折号。
- 默认 64×64 窗口的 JSON 约 32 KB，最大 128×128 窗口约 130 KB。读取窗口也会把引擎焦点移到窗口中心（同一把锁内），让区块流式加载只保留相机附近的区块。
- 没有世界在运行时——`--world off`，或 host 还没启动——路由回 `503 Service Unavailable`，短文本 `the world is not running`；它不会掉进静态路由的 SPA 回退。
- `--world` 默认 `on`，`--seed` 默认 `1`，`--chunks` 默认 `4096`，`--http-port 0` 关闭整个浏览器端点，快照与插件资源都不存在。
- 快照**不改变本文的任何命令、回答、帧规则或版本号**。客户端在画面可见时每 500ms 轮询一次，在 HUD 里显示 `server` 或 `demo` 以及 tick；请求失败时保留确定性的占位地图。Vite dev proxy 把 `/world` 转发到 Java 服务器。把窗口映射到相机是这份契约之上的客户端改动；不发送参数的客户端仍然能拿到出生锚点窗口。仓库里当前的 `web-client/` 仍按 256 格的兜底地图大小校验，所以想渲染负原点或 `[0, 256)` 之外的坐标，客户端必须把世界当成无界的。

## 版本兼容

**本文的版本号就是协议的版本号。** 当前版本是 `1`，线上写作 `HELLO 1` 与 `OK OC/1`。

- 本文改动已有帧规则、已有命令、任一已有回答、base64 约定、信封布局、密钥长度、窗口长度或 magic 时，版本号必须增加（`2`、`OK OC/2`，以此类推），并同步修改本文、服务器与客户端。
- 增加一条旧客户端永远不会发送的新命令是向后兼容的，本身不需要增加版本号。游戏命令就是这样的增量：它们保持 OC/1，并让每一条已有命令的字节保持不变。
- 版本号是两端判断兼容性的**唯一**依据；不要用“字段看起来还在”“字符串能解析”来猜兼容。
- 只增加报告字段、或只改 `REGISTER`/`LOGIN` 的人读文案，不算协议变化；客户端本来就不应依赖这些内容。
- 旧客户端遇到新版本会收到非 `OK OC/1` 的握手回答，并按握手规则断开；新客户端遇到旧服务器同理。这是刻意的失败方式：拒绝比半兼容更安全。

## 怎么用 pnpm 起客户端

浏览器客户端在 `web-client/`，与 Gradle 完全并行：Gradle **从不**调用 npm/pnpm，pnpm 也**从不**调用 Gradle。

前置条件：

| 需要 | 版本/说明 |
| --- | --- |
| Node.js | 18+（Vite 6 的要求；建议 20+） |
| pnpm | 9+；`web-client/package.json` 里写的是 `packageManager: "pnpm@9"` |
| 网络 | **第一次 `pnpm install` 需要网络**；装好之后的 dev/build 可以离线 |
| Java 服务器 | 在 `25567` 上提供 `/ws` 与静态页面；先按服务器文档启动 |

依赖范围是 `typescript ^6.0.3` 与 `vite ^6.0.0`（解析到 Vite 6.x），见 `web-client/package.json`。

```shell
cd web-client
pnpm install
pnpm dev
```

打开 Vite 打印的地址。开发时把页面上的主机与端口**都留空**，客户端就连同源的 `/ws`，由 Vite 的代理转发到 `ws://127.0.0.1:25567`；填上主机/端口则直连。

构建：

```shell
pnpm build
```

产物在 `web-client/dist/`，由 Java 服务器在 `http://<host>:25567/` 提供。
构建失败不会影响 Java 侧：`./gradlew build --offline` 依旧只编译 Java，不需要 Node，也不会调用 pnpm。

`engine-info` 需要手工把服务器的 `data/oc-debug/debug.key` 复制到页面上的文本框；不要把密钥提交进仓库。

## 相关文档

- 浏览器客户端的使用说明：[`web-client/README.md`](../web-client/README.md)
- 仓库总览与并行工具链：[`README.md`](../README.md)
