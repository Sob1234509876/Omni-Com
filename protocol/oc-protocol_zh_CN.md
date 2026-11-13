[English](oc-protocol.md) | 简体中文

# OC/1 协议

> 这是浏览器客户端与 Java 服务器之间唯一的线上约定。两边的实现都必须服从本文；本文不记录实现历史，也不描述某个具体客户端。

## 这一篇讲什么

- 传输：WebSocket 路径、一个文本帧一条命令、`HELLO 1` 版本握手、空闲超时与服务端心跳，以及服务端主动发起的 `NOTIFY` 推送帧。
- 命令表，以及每条命令的确切回答。
- `REQ` 的登录前提、base64 约定与错误词汇。
- 游戏命令：`WHOAMI`、`ORDER`、`BUILD`、`TRAIN`、`UPGRADE`、`UPGRADEUNIT`、`REPAIR`、`RECLAIM`、`TRANSFER`、
  `ASSIST`、`TRAVEL` 与 `BATCH`，账号名身份规则、登录与所有权规则，以及世界步边界。
- `ORDER MOVE` / `ORDER ATTACK` / `ORDER ATTACKMOVE` / `BUILD` / `REPAIR` 的 `queue` 追加形式，以及
  `BATCH` 合并订单：客户端合成的一串订单按顺序应用，只回一条回答。
- 维修、继续建造与回收：每点血量的费用、维修速率、一半返还，以及各种拒绝。
- 手动仓库调拨：`TRANSFER` 语法、单仓库预留、空闲搬运工规则、`all`，以及数量夹取。右键协助：`ASSIST`
  取代旧的强制搬运，由服务器按建筑状态决定单位是建造、修理还是去取缺料。
- 维度旅行：`TRAVEL` 语法——自己的单位走向传送门（一座门建筑或一个维度名）并跨入所链接的维度。
- 快照里的单位路径，以及客户端的路径点交互（`Shift`+右键，`路径` 开关）。
- `oc-debug` 的第二层信封：字节布局、密钥、60 秒防重放窗口。
- 插件资源：`/plugin-assets` 上的普通 HTTP 内容，不属于命令协议。
- 世界快照：`/world/snapshot` 上的普通 HTTP 内容，同样不属于命令协议；另有一次性下发的世界引导文档 `/world/bootstrap`，承载静态的世界数据。
- 版本兼容规则，以及怎么用 pnpm 起浏览器客户端。

## 传输

| 项 | 约定 |
| --- | --- |
| WebSocket 地址 | `ws://<host>:25567/ws` |
| HTTP 地址 | `http://<host>:25567/`，提供同一个 `web-client/dist/` 的静态文件、`/plugin-assets` 下的插件资源、`/world/snapshot` 的世界快照，以及 `/world/bootstrap` 的一次性世界引导文档 |
| 帧类型 | 只使用 **TEXT** 帧；二进制帧不是命令，服务端回 ERR 并保持连接，客户端忽略并提示 |
| 帧内容 | 每个 TEXT 帧**恰好一条命令行**，UTF-8，不含 CR/LF |
| 回答 | 每条命令**恰好一行**回答；客户端不得把多条命令塞进一个帧。服务器也可能主动发送一条**并非回答**的 TEXT 帧——`NOTIFY` 推送帧，见下文"服务端主动推送的 NOTIFY 帧" |
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
| `UPGRADEUNIT ...` | `OK <newUnitId>` 或 `ERR <reason>` | 把属于自己的单位替换成它阶梯的下一级；需要 `LOGIN` 与所有权；见“游戏命令” |
| `REPAIR ...` | `OK` 或 `ERR <reason>` | 派自己的工程师修理自己的受损完工建筑；需要 `LOGIN` 与所有权 |
| `RECLAIM ...` | `OK <refund>` 或 `ERR <reason>` | 取消自己的未完工工地并返还一半放置成本；需要 `LOGIN` 与所有权 |
| `TRANSFER ...` | `OK <moved> <item>` 或 `ERR <reason>` | 在玩家自己的两座已完工仓库之间搬运库存；需要 `LOGIN` 与所有权；见“游戏命令” |
| `ASSIST ...` | `OK build` / `OK repair` / `OK haul <amount> <item>` 或 `ERR <reason>` | 派一名有建造能力且有货舱的己方单位前往一座己方建筑，服务器按建筑状态决定它做什么；需要 `LOGIN` 与所有权；见“游戏命令” |
| `TRAVEL ...` | `OK <count>` 或 `ERR <reason>` | 派己方单位经传送门进入所链接的维度；需要 `LOGIN` 与所有权；见“游戏命令” |
| `BATCH ...` | `OK <count>` 或 `ERR <index>/<count> <reason>` | 按顺序应用一串用 `;` 分隔的订单，只回一条回答；需要 `LOGIN`；见“游戏命令” |
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

`REQ` 和每一条游戏命令（`WHOAMI`、`ORDER`、`BUILD`、`TRAIN`、`UPGRADE`、`UPGRADEUNIT`、`REPAIR`、`RECLAIM`、`TRANSFER`、`ASSIST`、`TRAVEL`、`BATCH`）都要求同一条连接成功 `LOGIN` 过。`PING`、`COUNT`、`REGISTER`、`LOGIN`、`QUIT` 都不要求登录。
- 快照路由多接受一个查询参数：`dim=<key>` 画该维度而不是默认维度，世界未声明的键回答
  `400 unknown dimension: <key>`。窗口本身（`cx`/`cy`/`r`）是维度本地的，文档会标注画的是哪个维度——
  见下面的快照字段。
`hello` 是 `oc-hello` 插件提供的示例请求：载荷是普通 UTF-8 名字（不是 `oc-debug` 那样的信封），回答也是普通 UTF-8 文本。

## 游戏命令

成功 `LOGIN` 之后，连接可以用下面的命令操作单位、建筑与游戏经济。它们属于 OC/1：命令字、回答与帧规则
在 WebSocket 通道和加密 TCP 通道上完全一致，因为两个传输执行的是同一个命令分发器。

### 语法

```text
WHOAMI                                      -> OK player <id>            (需要 LOGIN)
ORDER MOVE <entityId> <x> <y> [<x> <y> ...] [queue] -> OK                (需要 LOGIN，实体必须属于该玩家，1..8 个点)
ORDER ATTACK <entityId> <targetId> [queue]   -> OK                        (需要 LOGIN，实体必须属于该玩家)
ORDER ATTACKMOVE <entityId> <x> <y> [queue]  -> OK                        (需要 LOGIN，实体必须属于该玩家)
ORDER STOP <entityId>                       -> OK                        (需要 LOGIN，实体必须属于该玩家)
BUILD <engineerId> <buildingPath> <x> <y> [queue] -> OK <buildingId> | ERR ...  (需要 LOGIN，工程师必须属于该玩家)
TRAIN <buildingId> <unitPath>               -> OK | ERR ...              (需要 LOGIN，建筑必须属于该玩家)
UPGRADE <buildingId> [variantPath]          -> OK <newBuildingId> | ERR ... (需要 LOGIN，建筑必须属于该玩家)
UPGRADEUNIT <unitId> [tierPath]             -> OK <newUnitId> | ERR ...   (需要 LOGIN，单位必须属于该玩家)
REPAIR <engineerId> <buildingId> [queue]    -> OK | ERR ...              (需要 LOGIN，工程师与建筑都必须属于该玩家)
RECLAIM <buildingId>                        -> OK <refund> | ERR ...     (需要 LOGIN，建筑必须属于该玩家)
TRANSFER <sourceId> <targetId> <item> <amount|all> -> OK <moved> <item> | ERR ... (需要 LOGIN，两座建筑都必须属于该玩家)
ASSIST <unitId> <buildingId>               -> OK build | OK repair | OK haul <amount> <item> | ERR ... (需要 LOGIN，单位与建筑都必须属于该玩家)
TRAVEL <unitId> [<unitId> ...] <gatewayBuildingId|dimension> -> OK <count> | ERR ... (需要 LOGIN，每个单位都必须属于该玩家)
BATCH <command>; <command>; ...             -> OK <count> | ERR <index>/<count> <reason>  (需要 LOGIN，1..16 条子命令)
```

- `<entityId>`、`<targetId>` 与 `<buildingId>` 是 `/world/snapshot` 里的十进制实体 id。`UPGRADE` 与
  `UPGRADEUNIT` 成功回答里的 id 都是一个**新**实体——替换出来的实体有新的 id，旧的已经消失。
- `<x>` 与 `<y>` 是十进制地图格坐标。
- 可选的 `queue` token（大小写不敏感，放在最后）是 `ORDER MOVE`、`ORDER ATTACK`、`ORDER ATTACKMOVE`、
  `BUILD` 与 `REPAIR` 的**追加**形式：命令的校验与普通形式完全相同，生成的订单被压到该单位已有订单的
  后面，而不是替换它们。三条 `BUILD ... queue` 会在同一个工程师的队列里留下三个工地与三个订单，按到达
  顺序执行。普通形式与历史命令逐字节相同，保持替换语义；任何其它多余 token 都是 `ERR bad arguments`。
- `BATCH` 是**合并订单**（Q16）：客户端把一串命令合成一行，用 `;`（分号，前后可有空格）分隔，服务器按给定
  顺序应用。它接受 `ORDER`、`BUILD`、`TRAIN`、`UPGRADE`、`UPGRADEUNIT`、`REPAIR`、`RECLAIM`、`TRANSFER`、
  `ASSIST` 与 `TRAVEL` 子命令；其它任何子命令——包括嵌套 `BATCH`、`WHOAMI` 或 `QUIT`——在应用任何东西之前就回
  `ERR bad arguments`。
  一条命令可带 1 到 16 条子命令；超过 16 条回 `ERR bad arguments`，不会截断。批次是**顺序，不是事务**：
  每条子命令按顺序应用，第一条失败以 `ERR <index>/<count> <reason>` 报告（index 从 1 开始），后面的
  子命令仍然按顺序应用。全部成功回答 `OK <count>`。解析失败的子命令只是少一条订单，不会中断其余命令，
  与单位订单队列里一条订单失败不取消其余订单的规则一致。
- `ORDER MOVE` 在实体 id 之后接受 **1 到 8 组坐标**，按顺序走过，最后一组是最终目标。单组形式
  `<entityId> <x> <y>` 与旧的单点移动**逐字节相同**。空路线、坐标 token 数为奇数、或超过 8 组都会回
  `ERR bad arguments`。某一段不可达会让整条命令失败，引擎的 `OrderFinished` 事件会带上说明是哪一段的理由
  （`waypoint k of n`）。
- `<buildingPath>` 与 `<unitPath>` 是不带命名空间的内容路径，例如 `building.armory.t1` 或
  `unit.melee.t4`。
- `TRANSFER` 的 `<item>` 是不带命名空间的物品路径（`gold`、`wood`），`<amount>` 是正整数或字面量
  `all`（大小写不敏感）。负数数量回 `ERR bad arguments`；`0` 会交给引擎并以
  `ERR the amount must be positive` 拒绝。`all` 表示“来源还能给出的全部”：服务器把它解析成来源当前
  未被预留的库存，引擎再夹一次。数量最终被夹到来源未预留库存与目标当前剩余空间，所以填大了不算错误，
  回答里的数字就是真正会移动的数量。
- `ASSIST <unitId> <buildingId>` 是**右键协助**：一名带有 `builder` 标记*且*有货舱的己方单位被派往一座
  己方建筑，**服务器按建筑状态决定它做什么**，优先级如下：未完工工地就地加入建造（免费的继续建造加入）；
  已完工且受损的建筑就地修理；已完工、完好、但第一个排队配方仍缺料的生产建筑由它去取料——缺料先在
  **能整批覆盖的最近己方仓库**预留（没有单座仓库能覆盖时退回普通的全网预留），登记一条普通送货任务并把
  协助单位指派给它，**先清空它原有的订单**。其它任何状态都以可读句子拒绝，例如
  `ERR nothing to assist at ...: <building> is finished, undamaged and no queued recipe is waiting for
  materials`。回答是 `OK build`、`OK repair`，或带预留物资的 `OK haul <amount> <item>`；拒绝不预留任何
  东西、不改动任何订单。（本命令取代旧的强制 `HAUL`——那只是本命令由客户端替服务器做决定的搬运分支；
  旧动词已删除。）
- `TRAVEL <unitId> [<unitId> ...] <gatewayBuildingId|dimension>` 派 1..8 个己方单位穿过传送门。最后一个
  token 是目的地：既可以是数字建筑 id——必须存在、存活且带 `gateway` 标记，并解析为其所在维度第一条
  开放路线的目的维度——也可以是维度键（例如 `hell`）。目的地必须**已链接**（世界网关图存在路线），否则回答
  `ERR dimension not linked` 且不触碰任何单位；未声明的维度键回答 `ERR unknown dimension`，非门建筑回答
  `ERR building is not a gateway`。每个单位都按普通的存在性/所有权规则校验，且必须存在从它自己维度出发
  的路线。成功时每个单位获得一条旅行订单，走向路线上第一扇门并在到达时跨入（与魔物入侵同一机制），回答
  `OK <count>` 标明派出多少个单位。移动本身是异步的：单位在之后的 tick 里陆续到达。
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
| `UPGRADE` 指向阶梯顶端，或目标不在服务器内容里 | `ERR ... top of its ladder / does not exist in the content registry` |
| `UPGRADE` 在提供多个变体的升级步上不带 `variantPath` | `ERR ... without choosing one of its N variants: <a>, <b>` |
| `UPGRADE` 指定源类型没有提供的变体 | `ERR ... does not offer '<path>' as an upgrade; it offers <a>, <b>` |
| `UPGRADE` 的变体令牌根本不是内容路径 | `ERR <token> is not a content path` |
| `UPGRADE` 选中的变体被内容限定在另一个维度（`dimensionsOnly`） | `ERR ... in the <dimension> dimension: the content restricts it to <allowed>` |
| `UPGRADE` 无法从玩家的仓库付款 | `ERR cannot afford the upgrade of <building> to <target> (needs <cost>)` |
| `UPGRADE` 的目标占地不同，或替换位置被占用 | `ERR ... footprint / site ... is not clear` |
| `UPGRADEUNIT` 的阶梯令牌根本不是内容路径 | `ERR <token> is not a content path` |
| `UPGRADEUNIT` 的单位类型没有后继，或目标不在阶梯上 | `ERR ... top of its ladder / does not offer '<path>' as an upgrade; it offers <a>` |
| `UPGRADEUNIT` 无法从玩家的仓库付款 | `ERR cannot afford the upgrade of <unit> to <target> (needs <cost>)` |
| `REPAIR` 指向的建筑未完工、满血，或内容没有维修价 | `ERR ... under construction / full health / no repair cost` |
| `REPAIR` 无法从玩家仓库付款 | `ERR cannot afford the repair (needs <cost>)` |
| `RECLAIM` 指向已完工建筑 | `ERR ... already finished, and a finished building cannot be reclaimed` |
| `RECLAIM` 指向死亡/不存在的建筑，或别人的建筑 | 按常规 `ERR unknown entity` / `ERR not yours` |
| `TRANSFER` 的物品路径不在服务器提供的内容里 | `ERR unknown item` |
| `TRANSFER` 的来源与目标是同一座建筑 | `ERR cannot transfer within the same building: <id>` |
| `TRANSFER` 的来源或目标不是已完工仓库 | `ERR ... it is not a warehouse` / `ERR ... it is still under construction` |
| `TRANSFER` 的两座仓库属于不同玩家、在不同维度，或之间没有可走路线 | `ERR ... different players / different dimensions / ... unreachable` |
| `TRANSFER` 的来源没有该物品的未预留库存，或目标无法存放 | `ERR no stock of <item> in ...` / `ERR the target warehouse cannot store <item> ...` |
| `TRANSFER` 下达时没有空闲搬运工 | `ERR no free engineer is available to haul <amount> <item>` |
| `ASSIST` 的单位没有 `builder` 标记或没有货舱，或建筑无活可干 | `ERR <unit> is not a builder / cannot carry goods / nothing to assist at ...` |
| `ASSIST` 的加入建造超出射程，或修理买不起 | 按普通 `BUILD` / `REPAIR` 的拒绝文案 |
| `TRAVEL` 的目的维度未声明 | `ERR unknown dimension` |
| `TRAVEL` 指向非门建筑，或目的地没有开放路线 | `ERR building is not a gateway` / `ERR dimension not linked` |

### 成本、生产与世界步

- `BUILD` 在**放置工地时一次性**从玩家的仓库网络扣除建筑类型的成本。预留与扣款和放置发生在同一个世界步里，
  生产任务无法在中间花掉同一批物资。玩家付不起时什么也不放置、什么也不扣；放置失败会释放预留。
- `TRAIN` 在属于自己的、已完工的生产建筑里排入一个单位。建筑等级够不够由引擎的生产系统决定，而不是由命令
  解析器决定：在 t1 兵营里训练 t4 单位会得到带生产系统理由的 `ERR ...`，队列保持不变。
- `UPGRADE` 把一座属于自己的已完工建筑替换成内容声明的后继之一。一个升级步可以提供**多个变体**（classic 的
  t1 兵营可以长成更高的兵营，也可以长成炼狱工厂）：每个变体的价格来自它自己的选项，快照把全部变体列在
  `upgrade.options` 数组里。省略 `<variantPath>` 时，单选项阶梯直接选中它唯一的后继，而选择有歧义时会被
  `ERR ... N variants: ...` 拒绝，因此客户端应当始终回发自己选中的 `to` 令牌。变体还可以被内容限定维度
  （`dimensionsOnly`）——classic 的炼狱工厂只能立在地狱——在别处选中它会在任何扣费之前被拒绝。
- 快照文档标注它画的是哪个世界：`dimension` 是内容键，`dimensionName` 是 profile 的显示名，
  `loadedChunks` 是该维度已流送的区块集合——客户端给区块
  缺席的瓦片画一层阴影，并随相机持续移动、区块陆续加载让阴影散开。静态的 `dimensions` 名册（切换器
  的条目）已移到一次性的引导文档，见世界快照一节。带 `gateway` 标签的建筑行携带
  `gateway:{"to":"<维度键>","arrival":[x,y]}`（与 `TRAVEL` 会走的同一跳），客户端把它变成一次快速的
  视角切换；路由未打开时省略该字段。
- `UPGRADEUNIT <unitId> [tierPath]` 是 `UPGRADE` 的单位版本：把一座属于自己的单位替换成其内容阶梯的
  后继（`UnitType.upgradeTo()` / `upgradeCost()`）。费用是**源**类型的打包物资，走同一套仓库预留路径，
  并且只在替换单位存在之后**扣一次**。替换单位在旧单位的原坐标出生，保持所有者、朝向与**血量比例**，
  旧单位的背包随行（按新容量截断，装不下的部分存回所有者的仓库网络），成功回答 `OK <newUnitId>`——
  替换出来的单位有新的 id，旧的已经消失，并从所有单位的战斗目标与空间索引里清掉。旧单位的订单队列走
  普通取消路径，所以被占用的工地会被释放、搬运的预留与货物会在实体消失前交还。每个拒绝都免费且可读：
  单位无法被指挥、类型没有阶梯（`top of its ladder`）、目标不存在或不在阶梯上、以及付不起。可选的
  `<tierPath>` 是单位行 `upgrade.options[0]` 条目里的 `to` 令牌；省略时选择阶梯的唯一一步。
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

### 手动仓库调拨

`TRANSFER <sourceId> <targetId> <item> <amount|all>` 是物流命令：把库存从自己的一座仓库搬到另一座，走的是
**普通搬运路径**。两端建筑必须存在、属于该玩家、是**已完工的仓库**，并且位于**同一个维度**；物品必须是
来源持有的；数量为正，并被夹到“来源未预留库存”与“目标当前剩余空间”中较小者。命令不瞬移任何东西：
它只在**点名的来源仓库**上预留（不同于生产的多仓库预留），要求下达命令时存在**空闲搬运工**——自己的、
能搬运、同维度、空闲且没有别的任务，最近者优先、距离并列按实体 id——然后派出与生产完全相同的搬运任务。
货物在工人装货时离开来源、在卸货时进入目标，所以成功回答 `OK <moved> <item>`（例如 `OK 120 gold`）说的是
**正在路上**的数量，不是目标已经持有的数量。

每一次拒绝都是可读的 `ERR <reason>`，并且**不扣任何东西**：不移动库存、不留预留、不登记搬运任务。情况包括：
别人的建筑（`ERR not yours`）、死亡或不存在的 id（`ERR unknown entity`）、不是仓库、两端是同一座建筑、
未知物品路径、来源没有未预留库存、目标无法存放、两个维度、两个入口之间没有可走路线（`ERR ... unreachable`），
以及下达时没有空闲搬运工（`ERR no free engineer is available to haul <amount> <item>`）。最后一条正是那种
否则会让任务无限等待的情况，所以在预留之前就判定。

支持 `all`，因为“全部搬走”是最自然的物流动作，而夹取规则已经定义了它的含义；浏览器表单保留具体数字
（带 `全部` 快捷按钮），这样它自己的校验仍然能拒绝 0。

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
| `ERR unknown building` / `ERR unknown unit` / `ERR unknown item` | `BUILD`/`TRAIN`/`TRANSFER` 的内容路径不在服务器提供的目录里 |
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
| `GET /world/bootstrap` | 一次性的静态世界文档：内容目录、BUILD 数组、维度花名册与瓦片尺寸 |

| 参数 | 默认 | 允许范围 | 含义 |
| --- | --- | --- | --- |
| `cx` | 出生锚点（`spawnAnchor().x()`，在 `(0,0)` 附近 64 格内） | **任意有符号 `int`**，没有地图边界 | 窗口中心的 tile X |
| `cy` | 出生锚点（`spawnAnchor().y()`） | **任意有符号 `int`**，没有地图边界 | 窗口中心的 tile Y |
| `r` | `32` | 夹到 `16..64` | 窗口半径，单位 tile |

窗口是边长 `2 * r` 的正方形，所以默认 80×80，最大 128×128。**只有 `r` 会被夹紧。** `cx` / `cy` 原样使用，所以原点是 `cx - r` / `cy - r`：可以是负数（例如 `cx=0` 给出 `originX=-16`），也可以很大（例如 `cx=100000&cy=-50000` 给出 `99984,-50016`），没有边缘会把窗口往里推。不带参数就是出生锚点 + 默认半径，旧客户端因此仍然能拿到一份合法文档。数字格式错误的值回落到默认值。

```json
{
  "tick": 42,
  "contentVersion": 1,
  "originX": -32,
  "originY": -32,
  "width": 64,
  "height": 64,
  "tiles": [["grass", "sand", "..."], ["...", "...", "..."]],
  "entities": [
    {"id": 1, "kind": "warehouse", "x": -12.5, "y": -3.5, "player": 1, "health": 1.00,
     "w": 3, "h": 3, "progress": 1.00, "queue": 0, "contents": {"gold": 120, "wood": 40},
     "storage": {"slots": 24, "used": 2, "free": 22}, "inbound": 1},
    {"id": 4, "kind": "worker", "x": -10.2, "y": 12.3, "player": 1, "health": 1.00,
     "w": 1, "h": 1, "progress": 1.00, "queue": 0, "role": "worker", "builder": true,
     "contents": {"gold": 6},
     "path": [[-10, 12], [-8, 12], [-6, 12], [-4, 12]], "target": [-4, 12],
     "task": "搬运中 → 兵工厂", "pop": {"kind": "pickup", "tick": 120}},
    {"id": 9, "kind": "barracks", "x": 6.5, "y": -4.5, "player": 1, "health": 1.00,
     "w": 3, "h": 3, "progress": 1.00, "queue": 2, "queueTicks": 87,
     "trains": [{"path": "unit.melee.t1", "name": "普通", "gold": 20, "level": 1},
                {"path": "unit.melee.t2", "name": "强化", "gold": 40, "level": 1}],
     "producingNow": {"kind": "unit.melee.t1", "position": 1, "length": 2},
     "demand": {"ready": true, "items": [{"item": "gold", "need": 50, "have": 50, "stock": 500}]},
     "queueItems": [{"kind": "unit.melee.t1", "ticks": 87, "total": 120},
                    {"kind": "unit.melee.t1", "ticks": 120, "total": 120}]},
    {"id": 10, "kind": "workshop", "x": 13.0, "y": -7.5, "player": 1, "health": 1.00,
     "w": 4, "h": 3, "progress": 0.42, "queue": 0,
     "reclaim": {"gold": 0, "wood": 0}, "invested": {"plank": 80, "brick": 40}},
    {"id": 12, "kind": "building.armory.t1", "x": 18.5, "y": 4.5, "player": 3, "health": 0.50,
     "w": 3, "h": 3, "progress": 1.00, "queue": 0,
     "repair": {"gold": 300, "wood": 0},
     "upgrade": {"options": [{"to": "building.armory.t2", "gold": 140, "wood": 40},
                             {"to": "building.purgatory", "gold": 260, "wood": 40}]}}
  ],
  "dimension": "overworld",
  "dimensionName": "Earth",
  "loadedChunks": [[0, 0], [1, 0]],
  "projectiles": [{"id": 1, "x": 10.2, "y": 9.1, "tx": 12.5, "ty": 8.3}],
  "players": [
    {"id": 1, "name": "Player 1", "gold": 0, "wood": 0, "goldPerMinute": 210, "woodPerMinute": 140,
     "anchor": [3, 2]},
    {"id": 3, "name": "alice", "gold": 300, "wood": 60, "goldPerMinute": 0, "woodPerMinute": 0,
     "anchor": [-21, -46]}
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
- `producingNow` 出现在有未完成生产任务的建筑行上：`{"kind": "<路径>", "position": <1 起>, "length": <未完成任务数>}`。`kind` 是产出的单位内容路径；只产出物品的配方用配方自己的路径——与对应 `queueItems` 行完全相同的值。`position` 从 1 开始，`length` 只数未完成任务，所以 HUD 不必遍历数组就能打印 `生产中 <position>/<length>`。单位行、从未有过队列的建筑以及没有任务在跑的空队列都省略它，客户端回退到 `queue` 的旧 `生产中 ×N` 标记。
- `demand` 在同样的建筑行上、任务尚未完成时出现：`{"ready": <布尔>, "items": [{"item": "<路径>", "need": <整数>, "have": <整数>, "stock": <整数>}]}`。`items` 恰好按配方顺序列出该配方的输入种类——绝不混入建筑里无关的库存——`have` 以 `need` 为上限。`stock` 是该物品在所有者仓库里未被预留的可调拨数量，也就是一次搬运还能送来的量（Q10 的“本地持有量旁边的可调拨仓库量”）；没有物流网络的玩家报告 `0`。只要有一种输入少于建筑自己持有的数量，`ready` 就是 false；引擎已经进入 `PRODUCING` 的任务已经消耗了输入，因此报告 ready。客户端在 `ready` 时给当前队列行画空心勾，否则画警告，并按 need/have 列出缺料；图标旁的小 `仓 <stock>` 数字回答一次搬运还能送来多少，悬停时显示带物品名的明细。没有生产队列的建筑省略该对象；旧客户端忽略它。
- `task` 出现在**工程师**单位行上，是一行从单位命令队列队首派生、绝不从移动推断的短文本：`搬运中 → <建筑>`、`建造中 → <建筑>`、`修理中 → <建筑>` 或 `空闲`。`<建筑>` 是执行中建筑的显示名。非工程师单位、建筑和旧服务器都省略该字段；旧客户端忽略它。客户端在实体提示框和选中 HUD 里显示它。
- `pop` 在引擎发出搬运事实后最多**一秒**内出现在单位行上：`{"kind": "pickup" | "dropoff", "tick": <模拟 tick>}`。`kind` 表示货物进入还是离开该单位的货舱，`tick` 是引擎事件的 tick，客户端用它让同一次事件即使被两次轮询都带上也只播放一次。事件超过 `WorldHost.POP_TTL_TICKS`（20 tick = 1 秒）后省略，旧服务器也不带；不认识它的客户端忽略。
- `queueItems` 出现在有生产队列的建筑行上，单位或从未有过队列的建筑省略该数组；`queue` 就是它的长度。它按队列顺序列出未完成任务，每项是 `{"kind": ..., "ticks": ..., "total": ...}`：`total` 是配方时长（`Recipe.durationTicks()`），`ticks` 是当前正在 `PRODUCING` 的任务的引擎剩余 tick（`ProductionJob.remainingTicks()`）。尚未开工的任务——等待材料、运输中、排在当前任务之后——报告 `ticks === total`；`WAITING_OUTPUT` 任务虽然仍未完成但已经不再生产，也按同样方式报告。`kind` 是配方产出的单位内容路径（`unit.melee.t1`）；只产出物品的配方回落到配方自己的路径。客户端用清单把 `kind` 解析成显示名；旧客户端可以完全忽略这个数组，继续用 `queue` 画 `生产中 ×N` 标记。
- `role` 出现在每一条单位行上（`combat`、`worker` 或 `support`），客户端的框选优先级靠它区分士兵与带小攻击的工程师。内容类型带 `builder` 标签的单位还会带 `"builder": true`：这是 BUILD 菜单应当依据的**能力标记**，所以升级后的工程师梯（内容路径不再是基础 `unit.engineer`）不需要客户端按类型名匹配也算建造者。`attackDamage` 与 `attackRange` 出现在内容声明了武器的单位或建筑行上；没有武器的建筑（仓库、矿场）与旧服务器省略。两个数字与战斗层实际开火用的是同一份引擎数据，选中面板与实际炮弹不会互相矛盾。
- `trains` 出现在**已完工**的生产建筑行上：内容驱动的 TRAIN 菜单，该建筑自身等级允许的每个配方一项，形状 `{"path": "<单位内容路径>", "name": "<显示名>", "gold": <整数>, "level": <整数>}`。`path` 是 `TRAIN <buildingId> <unitPath>` 的令牌，`name` 是受训单位的显示名，`gold` 是配方以货币物品（`gold`）计的输入成本，`level` 是配方要求的建筑等级——选中建筑低于它时客户端把该入口禁用。未完工工地或不能生产的建筑省略数组；不训练任何东西的生产建筑可以发空数组。
- `projectiles` 是自上一份快照以来射出的箭矢通道：每支在飞的箭一项 `{"id": <整数>, "x": <数字>, "y": <数字>, "tx": <数字>, "ty": <数字>}`——出发点与目标点。伤害已经在 tick 内结算完毕；箭矢纯属表现层。列表最多保留 128 支，超过 4 tick 的条目被丢弃，安静的世界发空数组。不认识它的客户端忽略。
- `tilePixels`、`dimensions` 花名册、`buildable` 数组与 `content` 目录已从快照中移出：它们**在向运行中的世界添加维度之前**是静态的（维度是可变的——插件钩子可以在运行中途注册一个），每轮轮询都重新发送它们是在为（几乎）不变的数据反复消耗带宽与序列化时间。它们现在位于一次性的引导文档（`GET /world/bootstrap`，形状与规则见下一节）里，字段形状与原先完全一致。快照轮询现在只携带动态数据（`tick`、`contentVersion`、窗口几何、`tiles`、`entities`、`dimension` / `dimensionName`、`loadedChunks`、`projectiles`、`players`）；仍然期待被移字段的旧客户端应改为一次性拉取引导文档，客户端团队会防御性地解析引导载荷并容忍其缺失（离线演示世界）。
- `contentVersion` 是一个顶层整数，重复引导文档里静态世界载荷的版本（见下一节）。它让缓存了引导文档的客户端能从下一次轮询开始就察觉运行中途的维度注册，从而把引导文档重新拉取一次，而不是对着一个悄悄变大的花名册继续轮询。这个字段只有几个字节；忽略它的客户端照常工作。
- `contents` 出现在**每一个库存非空的行**上，并且只列出数量非零的物品；空仓库与空货舱省略该对象。键是物品内容路径（`gold`、`wood`），值是非负整数，和 `players` 的金额一样会饱和。对仓库而言它是建筑自己的库存，与玩家行求和的是同一个来源，因此客户端可以在选中 HUD 里显示 `库存：金 120 · 木 40`；对载货单位而言它是 HUD `携带` 行背后的货舱；从内容驱动 HUD 起**生产建筑的缓冲**也会上报，选中的兵营能显示里面暂存的材料。生产缓冲仍然*不属于*物流网络——可花费的总量只在 `players` 行里——所以客户端不得把生产建筑的 `contents` 加进资源读数。
- `storage` 出现在**每一个**仓库行上（空仓库也有）：`{"slots": <整数>, "used": <整数>, "free": <整数>}`。`slots` 是内容的槽位容量（经典仓库是 `24`），`used` 是当前库存占用的槽位数——每 `ItemType.maxStack()` 个单位占一个槽，所以同一种物品超过一叠就会占多个槽——`free` 是剩余量。无上限库存按 `Long.MAX_VALUE` 饱和报告。数字来自引擎的 `Inventory`，因为只有服务器知道每种物品的最大堆叠；客户端不得自己推算容量。浏览器单仓库表头显示 `容量 24 · 已用 2 · 空闲 22`。
- `inbound` 在至少一个**未完成**搬运任务把它当作目标时出现在仓库行上：一个非负整数，表示在途任务数（手动 `TRANSFER` 是常见来源）。数量为零时省略，所以客户端把缺失读作“没有在途”；浏览器单仓库表头追加 `· 在途 N`。
- `upgrade` 只出现在**已完工**、且内容类型指定了至少一个*适用*后继的建筑行上，表示这座建筑可以用 `UPGRADE <id> <variantPath>` 升级。形状是 `{"options": [{"to": "<目标内容路径>", "gold": <整数>, "wood": <整数>}, ...]}`——每个变体一项。只有命令真正会接受的选项才会被列出：目标存在于服务目录、**占地相同**，并且**允许立在建筑所在的维度**。主世界的兵营因此只列出更高的兵营，而立在地狱的同一座兵营会同时列出两个变体——客户端永远不会画出一条会被命令以维度为由拒绝的升级入口。未完工工地、阶梯顶端、没有后继的类型和悬空的后继引用都省略整个对象。`gold` / `wood` 是该变体命令会扣的价格（源类型选项自己的成本）。可负担性**故意不编码**：客户端拿自己的 `players` 行比较成本，资源不足时把入口显示为禁用，而不是隐藏。旧客户端会忽略这个字段。
- `repair` 只出现在**已完工**、受损、且内容声明了维修价的建筑行上。形状是 `{"gold": <整数>, "wood": <整数>}`，金额是把**当前缺失血量**全部修满的总价（内容每点血量价 × 缺失血量，`Building.repairCost()`）。满血建筑、未完工工地和 `repairCostPerHealth()` 为空的内容都不带它，所以客户端不会显示命令必然拒绝的维修。可负担性不编码，客户端自己拿 `players` 行比较。旧客户端忽略。
- `reclaim` 只出现在**未完工**工地上。形状是 `{"gold": <整数>, "wood": <整数>}`，金额是 `RECLAIM <id>` 会存入仓库的返还：放置时记录成本的一半（向下取整，`CommandService.reclaimRefund`）。即使两项都是 `0` 也存在，因为动作仍然会移除工地；已完工建筑不带它。旧客户端忽略。
- `invested` 出现在同一批**未完工**工地行上，紧跟 `reclaim`：已支付的原材料束，形如 `{"gold": <整数>, "wood": <整数>}`（键是物品路径，与 `contents` 相同）。配合引导文档 `content` 目录里该类型的造价，客户端可以用原始数字自算建造进度，而不必信任四舍五入的 `progress`。已完工建筑不带它。旧客户端忽略。
- `path` / `target` 只出现在**移动中且已经有路线**的单位行上。`path` 是 `[x, y]` 绝对世界瓦片对数组，表示**剩余**折线（已经走过的点不发送），最多 **64 个点**；`target` 是当前移动命令的目标瓦片 `[x, y]`。空闲或停下的单位、还在等待第一条路线的单位、以及旧服务器都省略这两个字段。坐标与实体的 `x` / `y` 一样是绝对瓦片索引，可以为负，并且不按快照窗口裁剪。路线超过上限时，`path` 只带前 64 个点，`target` 仍然指向真正的目标，客户端据此画线并画目标小圆点。字段纯属表现层、可增量；旧客户端忽略。
- 这些字段都是增量字段：旧服务器可以不带它们，客户端必须在缺失时照常工作（不显示尺寸 HUD、不画进度条、不画生产标记、不显示逐项队列列表、不显示需求图标、不显示工程师任务、不显示角色与建造者标记、不播搬运弹跳、不显示库存行、不显示容量与在途数、不显示升级/维修/回收入口、不画路径、不显示 TRAIN 菜单、不显示箭矢通道）。浏览器客户端在 `src/render/view.ts` 里把它们读成 `footprint`、`progress`、`queue`、`queueTicks`、`queueItems`、`producingNow`、`demand`、`task`、`role`、`builder`、`pop`、`contents`、`storage`、`inbound`、`trains`、`upgrade`、`repairCost`、`reclaim`、`path`、`pathTarget`、`projectiles` 与 `gateway`；单个可选字段格式错误会被忽略，而不是让整份文档失效。
- `players` 按 id 排序，世界里存在的每个玩家各一行（样例场景、AI 与每个真人账号），带玩家 id、显示名、两种开局资源与两种收入速率，全部是非负整数。每行还可以带 `anchor`：可选的 `[x, y]` **绝对整数瓦片**对，表示该阵营的出生位置。玩家 1 报告样例场景铺开的出生锚点，加入的真人报告确定的基地槽位锚点（不是最终仓库原点），经典机器人报告它开局基地搜索的确定性锚点。值来自 `Player.anchor()`，没人发布时省略；它是**已知的出生位置**，不是实时单位位置，也不是战争迷雾揭示，所以客户端可以用它画方向/距离读数和地图边缘箭头，而不揭示地形或实体。扁平键 `gold` 与 `wood` 就是经典内容目录注册的物品路径；快照不重复它们的显示名（金币 / 木材），其它物品类型暂时不进这份文档。金额是该玩家仓库当前可用的数量——也就是引擎的投放点规则、建造/训练成本检查读取的同一个来源——所以没有仓库的玩家两项都报 `0`。`goldPerMinute` / `woodPerMinute` 是该玩家**存活且已完工**的建筑上内容标签 `income_<n>` / `wood_income_<n>` 之和，与 `ClassicEconomy` 发放时用的是同一套约定：在建工地、被摧毁的建筑和没有匹配标签的类型都不计入，所以不发布任何标签的内容两项都报 `0`。每座建筑第一枚匹配标签生效，与发放钩子一致。这些字段也是增量的：旧服务器可以不带，客户端必须在没有它们时照常工作。浏览器客户端用 `WHOAMI` 回答的 id 匹配对应行，在 HUD 显示名字、金币、木材与收入速率；还没有这一行时显示破折号。它把每个敌对行的 `anchor` 读进顶部敌方行和小地图边缘箭头，畸形对直接忽略。
- 默认 80×80 窗口的 JSON 约 50 KB（不含路径叠加），最大 128×128 窗口约 130 KB；每个移动中的单位最多增加 64 个瓦片对加一个目标点（约 0.7 KB）。过去每轮轮询都要随带的静态块（目录、BUILD 数组、花名册、瓦片尺寸，样例场景世界约 3 KB，经典目录约 9.5 KB）已完全不在这份文档里。读取窗口也会把引擎焦点移到窗口中心（同一把锁内），让区块流式加载只保留相机附近的区块。
- 没有世界在运行时——`--world off`，或 host 还没启动——路由回 `503 Service Unavailable`，短文本 `the world is not running`；它不会掉进静态路由的 SPA 回退。
- `--world` 默认 `on`，`--seed` 默认 `1`，`--chunks` 默认 `4096`，`--http-port 0` 关闭整个浏览器端点，快照与插件资源都不存在。
- 快照**不改变本文的任何命令、回答、帧规则或版本号**。客户端在画面可见时每 500ms 轮询一次，在 HUD 里显示 `server` 或 `demo` 以及 tick；请求失败时保留确定性的占位地图。Vite dev proxy 把 `/world` 转发到 Java 服务器。仓库里的 `web-client/` 把世界当成无界的，并用 `w` / `h` / `progress` / `queue` / `queueTicks` / `queueItems` 显示尺寸 HUD、建造进度条、`生产中 1/3` 标记（旧快照为 `生产中 ×N`）、需求图标和选中面板里的逐项队列，用 `contents` 画选中载货单位的库存标签，用 `storage` / `inbound` 画仓库面板的单仓库表头（容量、已用/空闲槽位与在途调拨数），用 `task` 显示工程师任务行，用 `pop` 播放 600 ms 搬运动画，用 `players.goldPerMinute` / `players.woodPerMinute` 显示 `(+30/分)` 后缀，用建筑行的 `trains` 渲染内容驱动的 TRAIN 菜单、用引导文档的 `buildable` 数组渲染 BUILD 菜单，用 `builder` 判定 BUILD 菜单的能力，用 `upgrade` 显示**升级**入口，用 `repair` / `reclaim` 显示**修理 / 回收**入口，用 `path` / `target` 画**选中己方单位**的虚线路径（顶部 `路径` 开关，默认开，本次会话内记住）。`Shift`+右键给当前选择追加一个待定路径点（用小叉和同款虚线显示），不带 Shift 的右键把整条路线作为一行 `ORDER MOVE` 提交，`Esc` 清除待定点；敌人永远不画路线。完全不读这些新字段的旧客户端仍然能处理同一份文档。

## 世界引导

引导文档是客户端**首次加入**一个服务器世界时恰好拉取一次的**静态世界载荷**，而不是在每次快照轮询里重复读取那些静态块。它是**普通 HTTP 内容**，与快照完全一样：客户端用 `fetch` 读取，与 WebSocket 无关，也不需要登录。动机是所有者的规则：键值对（内容目录）在第一次加入服务器时下发，凡是可以在刚加入时就下发的数据，就不要反复拉取。

| 路由 | 内容 |
| --- | --- |
| `GET /world/bootstrap` | 静态世界文档：瓦片尺寸、维度花名册、BUILD 数组、内容目录 |

文档**对每个宿主只构建一次**，之后逐字节地从宿主缓存中应答——**直到有人向运行中的世界添加维度为止**。目录与 BUILD 数组在世界运行期间都不可变，但维度花名册是*可变*状态：`World.addDimension(...)` 是公开的引擎 API，插件 tick 钩子完全可以在运行中途调用它。那一刻服务器丢弃缓存字节、把文档的 `contentVersion` 加一，并在下一个请求到达时按新花名册重建。确切的形状（以经典目录为例）：

```json
{
  "tilePixels": 16,
  "contentVersion": 1,
  "dimensions": [{"key": "overworld", "name": "地球"}, {"key": "hell", "name": "地狱"}],
  "buildable": ["building.armory.t1", "building.mine.t1", "building.lumbercamp.t1",
                "building.warehouse", "building.turret.t1", "building.hell_gate"],
  "content": {
    "dimensions": {"overworld": {"name": "地球"}, "hell": {"name": "地狱"}},
    "items": {"gold": {"name": "金币"}, "wood": {"name": "木材"}},
    "buildings": {"building.armory.t1": {"name": "兵营·普通", "size": [3, 3], "health": 600,
                 "cost": {"gold": 100, "wood": 20}, "buildTime": 150, "level": 1,
                 "upgrades": [{"to": "building.armory.t2", "gold": 140, "wood": 40}]}},
    "units": {"unit.melee.t1": {"name": "普通", "role": "combat", "health": 100, "speed": 2.2,
              "cost": {"gold": 20}, "buildTime": 60, "damage": 10, "range": 0.9}},
    "recipes": {"recipe.train.melee.t1": {"producers": ["armory"], "inputs": {"gold": 20},
                "output": "unit.melee.t1", "duration": 60, "level": 1}}
  }
}
```

- 五个字段正是快照过去每轮重复发送的块，形状不变，外加版本号：
  - `tilePixels` 是一个 tile 的像素尺寸（16）——客户端的缩放系数。
  - `contentVersion` 是静态世界载荷的版本号；语义见下文。
  - `dimensions` 是 `[{key, name}]` 花名册，按服务器声明的键序排列，就是客户端渲染成维度切换器的条目。
  - `buildable` 是**顶层的 BUILD 目录**：建造者可以用 `BUILD <engineerId> <path> <x> <y>` 直接放置的内容路径数组，顺序就是服务器声明的按钮顺序。只能通过升级到达的等级**不在**数组里，因为命令会拒绝直接放置；可建造的地狱门**在**数组里，因为它是通往地狱的唯一通道。
  - `content` 是**原始内容目录**（i18n 方案里"服务端提供的键值对"）：`"dimensions"`（各键的显示名）、`"items"`、`"buildings"`、`"units"` 与 `"recipes"`，各按内容路径为键，字段与"默认无信息的字段一律省略"的规则与迁移前快照所记载的完全一致。
- 成功回答是 `200`，`Content-Type: application/json; charset=utf-8`，`Cache-Control: public, no-cache`，并带对所发送字节计算的 `ETag`；`If-None-Match` 命中当前 ETag 的请求会得到没有正文的 `304 Not Modified`。只允许 `GET` 与 `HEAD`，其它方法回 `405` 并带 `Allow: GET, HEAD`。
- **为什么是 `no-cache` 而不是原来的 `max-age=31536000, immutable`：**文档在*添加维度之前*是静态的，而长达一年的 immutable 承诺会让浏览器一直端着一个悄悄过期的维度花名册、永远不再重新拉取。`public, no-cache` 保留了缓存的价值，同时放弃了不可变声明：浏览器仍然可以存储文档，但每次使用前必须用 `ETag` 重新验证——世界不变时每次页面加载只花一个小小的 `304`，而不是完整重下；版本变化会改变字节、字节改变 ETag，下一次重验证就变成带着新花名册的全新 `200`。下文的 `contentVersion` 握手是同一份契约的应用层，完全不依赖缓存时机。
- **`contentVersion` 语义。**宿主构建时版本从 `1` 开始；每当有人向*已经在运行*的世界注册一个维度，版本恰好加 `1`（引擎会在那一刻发布 `DimensionAdded` 事件；内容在服务器启动时声明的维度不会让版本加一，因为它们本来就属于文档的首次构建）。没有移除维度的 API，所以版本永远不会减少。文档里的其它一切——目录、BUILD 数组、`tilePixels`——都是注册表级不可变的，从不参与版本。版本提升还会以服务端主动的 `NOTIFY {"type":"contentVersion",...}` 推送帧告知已连接的 WebSocket 会话（见"服务端主动推送的 NOTIFY 帧"一节），但那一帧只是延迟提示；正确性所依赖的契约仍是这条基于快照的握手。
- **客户端握手（版本不一致时的预期行为）。**引导文档与每一次快照轮询都携带 `contentVersion`。客户端记录拉取引导文档时的版本，并在每次快照轮询时把记录值与快照的 `contentVersion` 比较：
  1. 相等（正常情况）：继续使用缓存的引导文档，不花任何请求。
  2. 不一致：**一次** `GET /world/bootstrap`（返回 `200`、新字节与新 `ETag`），重建所有从它派生的东西——维度切换器、按维度划分的渲染目标、一切以花名册条目为键的状态——并把记录的版本换成新值。
  3. 不要循环：每次察觉不一致重取一次就够，因为重取之后的下一份快照携带的正是刚拉到的版本。重取一次后仍然不一致是服务器出了 bug，而不是继续轮询引导路由的理由。
  4. 重取失败（世界停了、网络错误）：保留旧文档，留待下次察觉不一致时再试；客户端带着旧花名册照常可玩，正如加入时引导路由不可用时的处理一样。
  仓库浏览器客户端的快照轮询就是检测点，所以运行中途注册的维度会在一个轮询间隔（500 ms）内被发现，而不必依赖 HTTP 缓存头只能在下次重验证时才能发现。
- 没有世界在运行时——`--world off`，或 host 尚未启动或已停止——路由与快照一样回 `503 Service Unavailable`，短文本 `the world is not running`，并且绝不掉进静态路由的 SPA 回退。
- **何时拉取**：每次页面加载一次，在首次成功加入时（客户端可以在快照路由开始回答 `200` 时立即拉取——那证明世界在运行）。请求失败或世界不在线时，客户端用自己的静态演示数据继续工作——仓库客户端的离线演示世界自带目录，不得依赖这条路由存在。客户端应防御性解析载荷：把每个字段当成可选、容忍未知形状、回落到内置目录。
- 引导路由**不改变本文的任何命令、回答、帧规则或版本号**。把被移字段从快照中去掉属于同一次协同变更：快照是普通 HTTP 内容，仓库客户端同步更新，每个字段形状都在引导文档中原样保留。给两份文档都加上 `contentVersion` 也是同一种增量变更：从不读它的旧客户端照常工作，只是不会察觉运行中途的维度注册。Vite dev proxy 把 `/world` 转发到 Java 服务器，开发时无需额外配置即可访问这条路由。

## 服务端主动推送的 NOTIFY 帧

在一问一答的命令流之外，服务器可以主动发送一条内容以 `NOTIFY ` 前缀开头的**非请求触发的 TEXT 帧**：

```text
NOTIFY {"type":"contentVersion","value":2}
```

前缀之后是**一个紧凑 JSON 对象**：`type` 字段选择含义，对象里的其余字段携带该类型的负载。`NOTIFY` 是服务器会发送的唯一一种非请求触发的 TEXT 帧形状；其余的服务器出站文本要么是命令回答，要么是心跳一节里的传输层控制帧。

| 项目 | 约定 |
| --- | --- |
| 方向 | 只从服务器到客户端；客户端从不发送 `NOTIFY` 帧，客户端发来的这一帧只会被当作未知命令（`ERR unknown command`） |
| 路由 | 客户端对每一条入站 TEXT 帧**按前缀路由**：内容以 `NOTIFY ` 开头的是推送帧，其余都是命令回答 |
| 不是回答 | `NOTIFY` 帧不属于任何等待中的命令。它不得占用等待中的回答槽，不得交给命令解析器，收到它也绝不影响"一命令一回答"的配对 |
| 受众 | 每一条已完成版本握手（`HELLO 1` 得到 `OK OC/1`）的 WebSocket 会话，**无论是否登录**——`contentVersion` 并不敏感，同一个值就挂在无需登录的快照路由上。仍在握手之中的会话绝不会收到它，所以客户端看到的第一帧永远是自己的 `OK OC/1` |
| 可靠性 | 尽力而为。丢帧无害是设计前提；没有重放、没有排队、没有确认 |
| 可扩展 | 之后的版本可能增加新的 `type` 值（例如插件重载、世界重启）；不认识某个 `type` 的客户端**忽略这一帧**。目前定义的只有 `contentVersion` 这一种 |

### `contentVersion` 类型

```text
NOTIFY {"type":"contentVersion","value":2}
```

- `value` 是十进制整数，即新的 `contentVersion`——与下一次快照轮询、重新拉取的引导文档携带的是同一个数。版本从 `1` 开始，每向一个已经在运行的世界注册一个维度就恰好加 `1`（含义见"世界引导"一节）；一次提升向每条已握手会话**恰好推送一帧**。
- 这一帧是**唤醒提示，不是状态**。收到它的客户端执行的动作，与它在快照轮询里发现版本不匹配时要做的完全一致：拉取一次 `GET /world/bootstrap` 并重建一切由它派生的东西。引导文档自己的握手规则——每次不匹配只重拉一次、不循环、重拉失败就保留旧文档——原样适用；推送只是把"等下一次轮询"换成"现在就做"。
- 在提升**之后**才连接的会话不会收到重放：当前版本就在它的第一份快照和它拉取的第一份引导文档里，因此没有什么需要补发。
- 这一帧绝不影响正确性。完全忽略 `NOTIFY` 的客户端照常工作，只是晚一个轮询间隔（500 毫秒）才通过快照的 `contentVersion` 字段得知花名册变化——那条握手仍然是权威的兜底。推送只是这条握手之上的延迟优化，绝不是它的替代品。

## 版本兼容

**本文的版本号就是协议的版本号。** 当前版本是 `1`，线上写作 `HELLO 1` 与 `OK OC/1`。

- 本文改动已有帧规则、已有命令、任一已有回答、base64 约定、信封布局、密钥长度、窗口长度或 magic 时，版本号必须增加（`2`、`OK OC/2`，以此类推），并同步修改本文、服务器与客户端。
- 增加一条旧客户端永远不会发送的新命令是向后兼容的，本身不需要增加版本号。游戏命令——包括 `REPAIR`、`RECLAIM`、`TRANSFER`，以及 `ORDER MOVE` 的可选多路径点——就是这样的增量：它们保持 OC/1，并让每一条已有命令的字节保持不变。已有的 `ORDER MOVE <id> <x> <y>` 仍然精确是那一行，也仍然只表示一个目标。
- 服务端主动的 `NOTIFY` 推送帧在服务器到客户端方向上是同一类增量：没有任何已有回答行、已有命令被改动，旧客户端只会收到它本就能路由的帧（回答）加上必须按前缀路由、并且可以忽略的新 `NOTIFY` 帧。客户端一侧的唯一新义务是路由规则本身——入站 TEXT 帧按 `NOTIFY ` 前缀分发，而不是“把它当成上一条命令的回答”——这是与客户端同步的 coordinated change；对帧毫无反应的客户端损失的只是延迟（见上面的兜底规则）。
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
