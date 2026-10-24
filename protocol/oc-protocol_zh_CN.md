[English](oc-protocol.md) | 简体中文

# OC/1 协议

> 这是浏览器客户端与 Java 服务器之间唯一的线上约定。两边的实现都必须服从本文；本文不记录实现历史，也不描述某个具体客户端。

## 这一篇讲什么

- 传输：WebSocket 路径、一个文本帧一条命令、`HELLO 1` 版本握手，以及空闲超时与服务端心跳。
- 命令表，以及每条命令的确切回答。
- `REQ` 的登录前提、base64 约定与错误词汇。
- 游戏命令：`WHOAMI`、`ORDER`、`BUILD`、`TRAIN`、`UPGRADE`、`REPAIR` 与 `RECLAIM`，账号名身份规则、登录与所有权规则，以及世界步边界。
- 维修、继续建造与回收：每点血量的费用、维修速率、一半返还，以及各种拒绝。
- 快照里的单位路径，以及客户端的路径点交互（`Shift`+右键，`路径` 开关）。
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
| `UPGRADE ...` | `OK <newBuildingId>` 或 `ERR <reason>` | 把属于自己的已完工建筑替换成下一级；需要 `LOGIN` 与所有权；见“游戏命令” |
| `REPAIR ...` | `OK` 或 `ERR <reason>` | 派自己的工程师修理自己的受损完工建筑；需要 `LOGIN` 与所有权 |
| `RECLAIM ...` | `OK <refund>` 或 `ERR <reason>` | 取消自己的未完工工地并返还一半放置成本；需要 `LOGIN` 与所有权 |
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

`REQ` 和每一条游戏命令（`WHOAMI`、`ORDER`、`BUILD`、`TRAIN`、`UPGRADE`、`REPAIR`、`RECLAIM`）都要求同一条连接成功 `LOGIN` 过。`PING`、`COUNT`、`REGISTER`、`LOGIN`、`QUIT` 都不要求登录。

`hello` 是 `oc-hello` 插件提供的示例请求：载荷是普通 UTF-8 名字（不是 `oc-debug` 那样的信封），回答也是普通 UTF-8 文本。

## 游戏命令

成功 `LOGIN` 之后，连接可以用下面的命令操作单位、建筑与游戏经济。它们属于 OC/1：命令字、回答与帧规则
在 WebSocket 通道和加密 TCP 通道上完全一致，因为两个传输执行的是同一个命令分发器。

### 语法

```text
WHOAMI                                      -> OK player <id>            (需要 LOGIN)
ORDER MOVE <entityId> <x> <y> [<x> <y> ...] -> OK                        (需要 LOGIN，实体必须属于该玩家，1..8 个点)
ORDER ATTACK <entityId> <targetId>          -> OK                        (需要 LOGIN，实体必须属于该玩家)
ORDER ATTACKMOVE <entityId> <x> <y>         -> OK                        (需要 LOGIN，实体必须属于该玩家)
ORDER STOP <entityId>                       -> OK                        (需要 LOGIN，实体必须属于该玩家)
BUILD <engineerId> <buildingPath> <x> <y>   -> OK <buildingId> | ERR ...  (需要 LOGIN，工程师必须属于该玩家)
TRAIN <buildingId> <unitPath>               -> OK | ERR ...              (需要 LOGIN，建筑必须属于该玩家)
UPGRADE <buildingId>                        -> OK <newBuildingId> | ERR ... (需要 LOGIN，建筑必须属于该玩家)
REPAIR <engineerId> <buildingId>            -> OK | ERR ...              (需要 LOGIN，工程师与建筑都必须属于该玩家)
RECLAIM <buildingId>                        -> OK <refund> | ERR ...     (需要 LOGIN，建筑必须属于该玩家)
```

- `<entityId>`、`<targetId>` 与 `<buildingId>` 是 `/world/snapshot` 里的十进制实体 id。`UPGRADE` 成功回答里的
  id 是一个**新**实体——替换出来的建筑有新的 id，旧建筑已经消失。
- `<x>` 与 `<y>` 是十进制地图格坐标。
- `ORDER MOVE` 在实体 id 之后接受 **1 到 8 组坐标**，按顺序走过，最后一组是最终目标。单组形式
  `<entityId> <x> <y>` 与旧的单点移动**逐字节相同**。空路线、坐标 token 数为奇数、或超过 8 组都会回
  `ERR bad arguments`。某一段不可达会让整条命令失败，引擎的 `OrderFinished` 事件会带上说明是哪一段的理由
  （`waypoint k of n`）。
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
| `BUILD` 的工地超出建造者射程（从建造者到工地占地外一圈的路径不存在或超过 16 tile） | `ERR ... out of reach ...` |
| `TRAIN` 的单位路径不在服务器提供的内容里 | `ERR unknown unit` |
| `TRAIN` 指向一个尚未完工的工地 | `ERR building not finished` |
| `TRAIN` 指向一个没有生产标签的建筑 | `ERR building cannot produce` |
| `TRAIN` 的单位在该建筑里没有任何配方 | `ERR no recipe for <unit> in <building>` |
| `BUILD` 无法从玩家的仓库付款 | `ERR cannot afford <building> (needs <cost>)` |
| `UPGRADE` 指向尚未完工的工地 | `ERR ... still under construction` |
| `UPGRADE` 指向阶梯顶端、没有升级链接，或目标不在服务器内容里 | `ERR ... no upgrade / top of its ladder / does not exist in the content registry` |
| `UPGRADE` 无法从玩家的仓库付款 | `ERR cannot afford the upgrade of <building> to <target> (needs <cost>)` |
| `UPGRADE` 的目标占地不同，或替换位置被占用 | `ERR ... footprint / site ... is not clear` |
| `REPAIR` 指向的建筑未完工、满血，或内容没有维修价 | `ERR ... under construction / full health / no repair cost` |
| `REPAIR` 无法从玩家仓库付款 | `ERR cannot afford the repair (needs <cost>)` |
| `RECLAIM` 指向已完工建筑 | `ERR ... already finished, and a finished building cannot be reclaimed` |
| `RECLAIM` 指向死亡/不存在的建筑，或别人的建筑 | 按常规 `ERR unknown entity` / `ERR not yours` |

### 成本、生产与世界步

- `BUILD` 在**放置工地时一次性**从玩家的仓库网络扣除建筑类型的成本。预留与扣款和放置发生在同一个世界步里，
  生产任务无法在中间花掉同一批物资。玩家付不起时什么也不放置、什么也不扣；放置失败会释放预留。
- `TRAIN` 在属于自己的、已完工的生产建筑里排入一个单位。建筑等级够不够由引擎的生产系统决定，而不是由命令
  解析器决定：在 t1 兵营里训练 t4 单位会得到带生产系统理由的 `ERR ...`，队列保持不变。
- `UPGRADE` 把一座**已完工、属于自己**的建筑替换成内容指定的下一级。费用是源类型的 `upgradeCost()`，走同一套
  仓库预留路径，并且只在替换建筑存在之后**扣一次**；任何拒绝（未完工、没有目标、目标不存在、占地不同、位置
  不干净、付不起）都不扣费，并留下旧建筑。成功时旧建筑被销毁，新类型的已完工建筑站在**同一原点**，拥有
  **同一个所有者**与**同样的血量比例**，id 是新的，回答里带着它。旧建筑上排队的生产任务会**被取消**（销毁
  事件放弃它的队列），指向它的搬运任务失败，旧 id 会从所有单位的战斗目标里清掉并离开空间索引。血量不要求
  满：保留比例意味着升级既不会免费回血，也不会让受损建筑变得不能升级；修复是下面单独的 `REPAIR` 命令。
- `REPAIR` 按内容每点血量的维修价，只对**真正恢复的血量**扣费，并且每个 tick 的扣费与该 tick 的治疗在同一个
  世界步里完成；命令应用时会先对整笔剩余维修费做一次仓库预留探测，然后立即释放，所以拒绝不会占用物资。
  `RECLAIM` 返还工地放置时的一半成本，自身不扣任何费用。两者都由内容驱动：维修价为空的目录不会出现
  `REPAIR` 入口，免费建筑的回收返还为零。
- **世界步边界。** 客户端命令在 socket 线程到达，但世界在自己的线程上 tick。每条游戏命令都进入一个队列，
  并在**下一个固定步的起点**、引擎推进模拟之前被应用。队列是 FIFO：命令按提交顺序逐条应用，回答在命令
  被应用时产生（而不是在解析时）。因此命令永远不会在世界 tick 时改动世界，相同的命令顺序总是产生相同的
  模拟。如果世界在服务器的命令超时内没有应用命令，客户端得到 `ERR world busy`；没有世界在托管、或世界没有
  在 tick 时，回答是 `ERR world not available` 或 `ERR world not running`。

### 维修、继续建造与回收

**维修。** `REPAIR <engineerId> <buildingId>` 派一个自己的工程师去修理自己的一座**已完工**且**受损**的建筑。
工程师走到建筑解析后的入口，站定后每 tick 恢复 **4 点血量**；多座工程师可以同时修同一座建筑。价格是内容的
`BuildingType.repairCostPerHealth()` 乘以当前缺失的血量，并且**按 tick 分批**预留与扣款：每个 tick 只为该 tick
真正恢复的血量扣费。因此被取消或中断的维修不会为没有做的工收费，血量永远不会超过上限，失败的尝试不扣费。
经典目录把所有建筑定为**每点缺失血量 1 金币**（半血的 600 血兵营修满要 300 金币）。

`REPAIR` 在以下情况回可读的 `ERR <reason>`：工程师不是你的、建筑不是你的、已死亡、仍在施工、已经满血、内容
没有维修价、或仓库付不起整笔剩余维修费。命令应用时会对整笔费用做一次预留探测再立即释放，所以工程师走过去的
过程中不会占用生产物资。

**继续建造。** 没有单独的命令。对属于自己、尚未完工的工地，发送普通的
`BUILD <engineerId> <buildingPath> <x> <y>`（用该工地的类型与占地原点）。引擎会认出同类型的现有未完工工地，
把工程师附着上去，而不是再放一座新工地；附着不收费，多个自己的工程师可以加入同一工地（合成速度由施工规则
封顶，而不是由协议封顶）。客户端的 `继续建造` 入口发送的就是这一行。

**回收。** `RECLAIM <buildingId>` 取消自己的一座**未完工**工地。所有已附着的建造者在同一个世界步里收到
`FAILED`，理由写明工地被回收；工地在同一命令里被移除；**一半放置成本**作为返还存入玩家仓库。一半是向下取整的
精确整数运算，基准是放置时记录的成本，而不是当前目录价。命令本身不扣任何其它费用。**已完工建筑不能回收**，
拒绝文案会明确说明。成功回答是 `OK <refund>`，`<refund>` 是可读的资源列表，例如 `OK 50 gold, 20 wood`
（没有返还时就是 `OK`）；未授权或非法请求回 `ERR <reason>`。

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
    {"id": 1, "kind": "warehouse", "x": -12.5, "y": -3.5, "player": 1, "health": 1.00,
     "w": 3, "h": 3, "progress": 1.00, "queue": 0, "contents": {"gold": 120, "wood": 40}},
    {"id": 4, "kind": "worker", "x": -10.2, "y": 12.3, "player": 1, "health": 1.00,
     "w": 1, "h": 1, "progress": 1.00, "queue": 0,
     "path": [[-10, 12], [-8, 12], [-6, 12], [-4, 12]], "target": [-4, 12]},
    {"id": 9, "kind": "barracks", "x": 6.5, "y": -4.5, "player": 1, "health": 1.00,
     "w": 3, "h": 3, "progress": 1.00, "queue": 2, "queueTicks": 87,
     "queueItems": [{"kind": "unit.melee.t1", "ticks": 87, "total": 120},
                    {"kind": "unit.melee.t1", "ticks": 120, "total": 120}]},
    {"id": 10, "kind": "workshop", "x": 13.0, "y": -7.5, "player": 1, "health": 1.00,
     "w": 4, "h": 3, "progress": 0.42, "queue": 0,
     "reclaim": {"gold": 0, "wood": 0}},
    {"id": 12, "kind": "building.armory.t1", "x": 18.5, "y": 4.5, "player": 3, "health": 0.50,
     "w": 3, "h": 3, "progress": 1.00, "queue": 0,
     "repair": {"gold": 300, "wood": 0},
     "upgrade": {"to": "building.armory.t2", "gold": 140, "wood": 40}}
  ],
  "players": [
    {"id": 1, "name": "Player 1", "gold": 0, "wood": 0, "goldPerMinute": 210, "woodPerMinute": 140},
    {"id": 3, "name": "alice", "gold": 300, "wood": 60, "goldPerMinute": 0, "woodPerMinute": 0}
  ]
}
```

- 成功回答是 `200`，`Content-Type: application/json; charset=utf-8`，`Cache-Control: no-store`；只允许 `GET` 与 `HEAD`，其它方法回 `405` 并带 `Allow: GET, HEAD`。
- `originX` / `originY` 是窗口左上角的**世界** tile；`width` / `height` 是**窗口**的宽高（`2 * r`）。文档**不带** `mapWidth` / `mapHeight` / `mapSize`：世界没有地图大小，客户端必须把世界当成无界的。`--chunks` 限制的是内存中保留的区块数，不是可访问的坐标。
- `tiles` 是窗口内的 row major 数组，恰好 `width * height` 个条目；每个条目是一条地形内容路径（`water`、`sand`、`grass`、`forest`、`hill`、`rock`，取决于服务的内容目录）。地形来自确定性的种子噪声场：同一个 seed、同一份内容在任意坐标复现同一块地形，`100000,-50000` 的窗口与生成器该处逐格一致。
- `entities` 按 id 排序，并且**不按窗口裁剪**：它仍然是整个世界所有存活实体的列表，`x` / `y` 是**绝对世界坐标**（实体中心，单位 tile，保留一位小数），可以是负数。客户端用 `originX` / `originY` 把实体换算到窗口内；窗口外的实体也留在数组里。`kind` 是引擎的内容路径（`worker`、`soldier`、`medic`、`warehouse`、...），单位与建筑形状相同。
- `w` / `h` 是实体的占地尺寸（单位 tile）：建筑取内容定义里的 footprint（仓库 `3×3`、车间 `4×3`），单位固定为 `1` / `1`，因为引擎给每个单位一个地面格子，与贴图大小无关。
- `progress` 是 `[0,1]` 的完成比例，保留两位小数。建筑在建时是 `constructionFraction()`，完工后恰好 `1.00`；未完工的工地会在线上夹到 `[0.01, 0.99]`，否则两位小数会把 99.6% 的工地四舍五入成已完工的 `1.00`。单位没有建造计数器——它只在生产完成后才进入世界——所以单位行报告自己的血量比例，这是引擎唯一提供的单位完成度；满血单位因此也是 `1.00`。
- `queue` 是建筑生产队列里尚未完成的任务数（单位或没有队列的建筑是 `0`）。`queueTicks` 只在队列当前任务真的处于 `PRODUCING` 时出现，值是 `ProductionJob.remainingTicks()`；其它状态下引擎的剩余 tick 只是配方时长，不代表进度，所以字段直接省略，不编造数字。
- `queueItems` 出现在有生产队列的建筑行上，单位或从未有过队列的建筑省略该数组；`queue` 就是它的长度。它按队列顺序列出未完成任务，每项是 `{"kind": ..., "ticks": ..., "total": ...}`：`total` 是配方时长（`Recipe.durationTicks()`），`ticks` 是当前正在 `PRODUCING` 的任务的引擎剩余 tick（`ProductionJob.remainingTicks()`）。尚未开工的任务——等待材料、运输中、排在当前任务之后——报告 `ticks === total`；`WAITING_OUTPUT` 任务虽然仍未完成但已经不再生产，也按同样方式报告。`kind` 是配方产出的单位内容路径（`unit.melee.t1`）；只产出物品的配方回落到配方自己的路径。客户端用清单把 `kind` 解析成显示名；旧客户端可以完全忽略这个数组，继续用 `queue` 画 `生产中 ×N` 标记。
- `contents` 只出现在内容类型被标记为仓库的建筑行上，并且只列出数量非零的物品；空仓库省略该对象。键是物品内容路径（`gold`、`wood`），值是非负整数，和 `players` 的金额一样会饱和。它是仓库自己的库存，与玩家行求和的是同一个来源，因此客户端可以在选中 HUD 里显示 `库存：金 120 · 木 40`。非仓库建筑永远不带它：生产建筑的私有缓冲不属于物流网络。
- `upgrade` 只出现在**已完工**、且内容类型指定了一个存在于服务目录里的后继类型的建筑行上，表示这座建筑可以用 `UPGRADE <id>` 升级。形状是 `{"to": "<目标内容路径>", "gold": <整数>, "wood": <整数>}`：`to` 是后继类型的内容路径，`gold` / `wood` 是命令会扣的两种资源（源类型的 `upgradeCost()`）。未完工工地、阶梯顶端、没有后继的类型和悬空的后继引用都不带它，所以客户端不会显示一个命令必然拒绝的升级入口。可负担性**故意不编码**：客户端拿自己的 `players` 行比较成本，资源不足时把入口显示为禁用，而不是隐藏。旧客户端会忽略这个字段。
- `repair` 只出现在**已完工**、受损、且内容声明了维修价的建筑行上。形状是 `{"gold": <整数>, "wood": <整数>}`，金额是把**当前缺失血量**全部修满的总价（内容每点血量价 × 缺失血量，`Building.repairCost()`）。满血建筑、未完工工地和 `repairCostPerHealth()` 为空的内容都不带它，所以客户端不会显示命令必然拒绝的维修。可负担性不编码，客户端自己拿 `players` 行比较。旧客户端忽略。
- `reclaim` 只出现在**未完工**工地上。形状是 `{"gold": <整数>, "wood": <整数>}`，金额是 `RECLAIM <id>` 会存入仓库的返还：放置时记录成本的一半（向下取整，`CommandService.reclaimRefund`）。即使两项都是 `0` 也存在，因为动作仍然会移除工地；已完工建筑不带它。旧客户端忽略。
- `path` / `target` 只出现在**移动中且已经有路线**的单位行上。`path` 是 `[x, y]` 绝对世界瓦片对数组，表示**剩余**折线（已经走过的点不发送），最多 **64 个点**；`target` 是当前移动命令的目标瓦片 `[x, y]`。空闲或停下的单位、还在等待第一条路线的单位、以及旧服务器都省略这两个字段。坐标与实体的 `x` / `y` 一样是绝对瓦片索引，可以为负，并且不按快照窗口裁剪。路线超过上限时，`path` 只带前 64 个点，`target` 仍然指向真正的目标，客户端据此画线并画目标小圆点。字段纯属表现层、可增量；旧客户端忽略。
- 这些字段都是增量字段：旧服务器可以不带它们，客户端必须在缺失时照常工作（不显示尺寸 HUD、不画进度条、不画生产标记、不显示逐项队列列表、不显示库存行、不显示升级/维修/回收入口、不画路径）。浏览器客户端在 `src/render/view.ts` 里把它们读成 `footprint`、`progress`、`queue`、`queueTicks`、`queueItems`、`contents`、`upgrade`、`repairCost`、`reclaim`、`path` 与 `pathTarget`；单个可选字段格式错误会被忽略，而不是让整份文档失效。
- `players` 按 id 排序，世界里存在的每个玩家各一行（样例场景、AI 与每个真人账号），带玩家 id、显示名、两种开局资源与两种收入速率，全部是非负整数。扁平键 `gold` 与 `wood` 就是经典内容目录注册的物品路径；快照不重复它们的显示名（金币 / 木材），其它物品类型暂时不进这份文档。金额是该玩家仓库当前可用的数量——也就是引擎的投放点规则、建造/训练成本检查读取的同一个来源——所以没有仓库的玩家两项都报 `0`。`goldPerMinute` / `woodPerMinute` 是该玩家**存活且已完工**的建筑上内容标签 `income_<n>` / `wood_income_<n>` 之和，与 `ClassicEconomy` 发放时用的是同一套约定：在建工地、被摧毁的建筑和没有匹配标签的类型都不计入，所以不发布任何标签的内容两项都报 `0`。每座建筑第一枚匹配标签生效，与发放钩子一致。这两个字段也是增量的：旧服务器可以不带，客户端必须在没有它们时照常工作。浏览器客户端用 `WHOAMI` 回答的 id 匹配对应行，在 HUD 显示名字、金币、木材与收入速率；还没有这一行时显示破折号。
- 默认 64×64 窗口的 JSON 约 32 KB（不含路径叠加），最大 128×128 窗口约 130 KB；每个移动中的单位最多增加 64 个瓦片对加一个目标点（约 0.7 KB）。读取窗口也会把引擎焦点移到窗口中心（同一把锁内），让区块流式加载只保留相机附近的区块。
- 没有世界在运行时——`--world off`，或 host 还没启动——路由回 `503 Service Unavailable`，短文本 `the world is not running`；它不会掉进静态路由的 SPA 回退。
- `--world` 默认 `on`，`--seed` 默认 `1`，`--chunks` 默认 `4096`，`--http-port 0` 关闭整个浏览器端点，快照与插件资源都不存在。
- 快照**不改变本文的任何命令、回答、帧规则或版本号**。客户端在画面可见时每 500ms 轮询一次，在 HUD 里显示 `server` 或 `demo` 以及 tick；请求失败时保留确定性的占位地图。Vite dev proxy 把 `/world` 转发到 Java 服务器。仓库里的 `web-client/` 把世界当成无界的，并用 `w` / `h` / `progress` / `queue` / `queueTicks` / `queueItems` 显示尺寸 HUD、建造进度条、`生产中 ×N` 标记和选中面板里的逐项队列，用 `players.goldPerMinute` / `players.woodPerMinute` 显示 `(+30/分)` 后缀，用 `upgrade` 显示**升级**入口，用 `repair` / `reclaim` 显示**修理 / 回收**入口，用 `path` / `target` 画**选中己方单位**的虚线路径（顶部 `路径` 开关，默认开，本次会话内记住）。`Shift`+右键给当前选择追加一个待定路径点（用小叉和同款虚线显示），不带 Shift 的右键把整条路线作为一行 `ORDER MOVE` 提交，`Esc` 清除待定点；敌人永远不画路线。完全不读这些新字段的旧客户端仍然能处理同一份文档。

## 版本兼容

**本文的版本号就是协议的版本号。** 当前版本是 `1`，线上写作 `HELLO 1` 与 `OK OC/1`。

- 本文改动已有帧规则、已有命令、任一已有回答、base64 约定、信封布局、密钥长度、窗口长度或 magic 时，版本号必须增加（`2`、`OK OC/2`，以此类推），并同步修改本文、服务器与客户端。
- 增加一条旧客户端永远不会发送的新命令是向后兼容的，本身不需要增加版本号。游戏命令——包括 `REPAIR`、`RECLAIM`，以及 `ORDER MOVE` 的可选多路径点——就是这样的增量：它们保持 OC/1，并让每一条已有命令的字节保持不变。已有的 `ORDER MOVE <id> <x> <y>` 仍然精确是那一行，也仍然只表示一个目标。
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
