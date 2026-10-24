[English](README.md) | 简体中文

# Omni Com Classic 世界插件

`projects:oc:oc-game-server-classic` —— 世界内容插件 `oc-classic`。服务器从自己的 `plugins/` 目录里捡插件；这个 jar 注册一场游戏：五级近战、一个工程师、两级兵营、三级矿场、三级伐木场和一座仓库，外加替它运转的收益钩子与按种子生成的机器人玩家系统。它不注册请求、不打开文件、不起线程。jar 在目录里，服务器就服务这个世界而不是引擎的示例目录；拿走它，服务器就回退到示例。

## 坐标

| | |
| --- | --- |
| Gradle 路径 | `:projects:oc:oc-game-server-classic` |
| Java 模块 | `oc.projects.oc.game.server.classic` |
| 基础包 | `io.github.sob1234509876.oc.server.classic` |
| 组 / 版本 | `io.github.sob1234509876.oc` / `2.0a` |
| 构建 | `java-library`、Java 17 工具链、JPMS |
| 依赖 | `oc-game-server`（插件 API）与 `oc-rts-engine`（目录类型）；引擎会带上 `oc-googology` |
| 自检 | `./gradlew --offline :projects:oc:oc-game-server-classic:selfcheck`（458 项检查） |
| 世界内容 | `oc-classic` |
| 素材 | `assets/manifest.json` 与 96x128 的 `assets/atlas.png` |
| 帮助 | [03 插件](../oc-game-server/help/03-plugins.md) 与 [05 浏览器端点](../oc-game-server/help/05-browser-endpoint.md)，中文 |

## 五级近战

五个士兵同属一个角色，只有数字不同。每一级的射程都是 0.9 格、冷却都是 30 tick；血量、伤害、护甲、成本与生产时间都严格递增。

| 路径 | 名字 | 血量 | 伤害 | 护甲 | 成本（金币） | tick | 外观 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `unit.melee.t1` | 普通 | 100 | 10 | 1 | 20 | 60 | 皮革 + 木材 |
| `unit.melee.t2` | 强化 | 150 | 18 | 2 | 40 | 90 | 皮革 + 石头 |
| `unit.melee.t3` | 精英 | 220 | 30 | 3 | 80 | 120 | 铁 |
| `unit.melee.t4` | 将领 | 320 | 48 | 4 | 160 | 180 | 金 |
| `unit.melee.t5` | 战王 | 460 | 75 | 5 | 320 | 240 | 钻石 |

外观的递进是这条阶梯最好读的部分：皮革 + 木材、皮革 + 石头、铁、金、钻石。护甲是**真正的引擎字段**，不是显示约定：`UnitType.armour()` 就是平摊减伤，这里用 `.armour(n)` 写进去，战斗层把它变成每次命中 `max(1, 伤害 - 护甲)`。`armour_<n>` 标签与字段并存，只给显示和内容工具看；没有任何系统读这个标签。

### 工程师

| 路径 | 名字 | 血量 | 伤害 | 护甲 | 射程 | 成本（金币） | tick | 载货量 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `unit.engineer` | 工程师 | 60 | 2 | 0 | 0.6 | 10 | 45 | 60 |

工程师是带 `builder` 标签、带载货仓的 `SUPPORT` 单位：它是把工地盖起来的那只手，也是经济系统以后指望去搬运的那只手。它不是战斗单位，但也不是无害的——它用与别人完全相同的规则攻击。

## 两级兵营

两座兵营都带生产者标签 `armory`，所以配方先按标签选中，再按引擎从 `BuildingType.level()` 读到的等级过滤。

| 路径 | 名字 | 等级 | 占地 | 血量 | 成本 | 建造 tick | 仓储格 | 可训练 | 升级到 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `building.armory.t1` | 兵营·普通 | 1 | 3x3 | 600 | 100 金币 + 20 木材 | 150 | 6 | `t1`..`t3`、工程师 | `building.armory.t2`，**140 金币 + 40 木材** |
| `building.armory.t2` | 兵营·强化 | 2 | 3x3 | 900 | 240 金币 + 60 木材 | 240 | 8 | 全部五级、工程师 | -（阶梯顶端） |

规则是 **t1 产出 t1..t3，t2 产出全部五级**。这不是插件约定：下面三级与工程师要求建筑等级 1，上面两级要求等级 2，而 `ProductionSystem.enqueue` 会拒绝建筑等级够不着的配方。

升级由引擎的 `CommandService.upgradeBuilding` 执行：t1 建筑在同一个 3x3 原点上被替换，所有者与血量比例保持不变，旧生产队列被取消。升级费恰好等于两级目录价格的差额（100/20 + 140/40 = 240/60），所以升级与直接造一座 t2 花掉的总材料完全相同。t2 兵营是阶梯顶端，不再提供升级。

## 三级矿场

| 路径 | 名字 | 等级 | 收益（金币 / 分钟） | 成本（金币） | 建造 tick | 升级（金币） |
| --- | --- | --- | --- | --- | --- | --- |
| `building.mine.t1` | 普通 | 1 | 30 | 80 | 120 | 80 → `building.mine.t2` |
| `building.mine.t2` | 强化 | 2 | 60 | 160 | 180 | 160 → `building.mine.t3` |
| `building.mine.t3` | 精英 | 3 | 120 | 320 | 240 | -（阶梯顶端） |

每一级升级费就是源等级的造价，所以 t1 + 两次升级 = 320 = 直接造一座 t3。升级不手工改收益：新类型的 `income_<n>` 标签从下一次发放起自然接管。

每座矿场都是 3x3、500 血、六个仓储格。收益是**内容数据，不是引擎机制**：引擎没有被动收入 tick，所以每座矿场把自己的速率发布成 `income_<n>` 标签，由插件的 `ClassicEconomy` 钩子付出去。

## 三级伐木场

| 路径 | 名字 | 等级 | 收益（木材 / 分钟） | 成本（金币） | 建造 tick | 升级（金币） |
| --- | --- | --- | --- | --- | --- | --- |
| `building.lumbercamp.t1` | 普通 | 1 | 20 | 80 | 120 | 80 → `building.lumbercamp.t2` |
| `building.lumbercamp.t2` | 强化 | 2 | 40 | 160 | 180 | 160 → `building.lumbercamp.t3` |
| `building.lumbercamp.t3` | 精英 | 3 | 80 | 320 | 240 | -（阶梯顶端） |

升级阶梯与矿场相同：每一级升级费就是源等级的造价，顶端没有后继，而且升级同样不花木材。

每座伐木场都是 3x3、500 血、六个仓储格，而且**只花金币**——一座要木材的伐木场永远解不开开局的死结。它把速率发布成 `wood_income_<n>` 标签；这个前缀与矿场的 `income_<n>` 刻意不同，所以伐木场永远不会被当成金矿。`ClassicEconomy` 会把玩家已建成、还活着的伐木场收益求和，并走与金币相同的仓库投放路径存进去。

## 经济规则

每 **60 tick** —— 经典游戏的一分钟，在宿主每秒 20 tick 下就是三秒 —— 每个玩家都会收到以金币计的**自己所有矿场收益之和**，以及以木材计的**自己所有伐木场收益之和**。三座一级矿的玩家在那一个 tick 收到 90 金币；三种等级各一座的玩家收到 210。两座一级伐木场的玩家收到 40 木材。只有**既已建成、又还活着**的建筑才付钱：还在施工的工地与已被摧毁的建筑都不付。

这些和通过引擎的后勤网支付，作为整堆存进拥有者的仓库。**没有仓库就一分不付**：金币和木材都不会掉在地上，也不会排队等着，所以丢光了仓库的玩家也就停止收入。钩子记得自己上一次付钱的 tick，所以同一个 tick 被调用两次也不会付两次——金币和木材都不会。

## 仓库

`building.warehouse` 就是引擎的下货点。引擎把后勤网定义成“所有建成的、类型是仓库的建筑”，所以没有专门的 drop-off 类型：目录里放的是一座与引擎示例同类的 3x3 仓库 —— 500 血、24 个仓储格、**100 金币 + 40 木材**、120 tick。

它是目录的一部分，而不是这个模块的发明：没有它，开局资源无处安放、生产无法供料、矿场收益也无法支付。

## 维修、继续建造与回收

九种经典建筑都用 `.repairCostPerHealth(GOLD, 1)` 声明了维修价：**每点缺失血量 1 金币**。
`REPAIR <engineerId> <buildingId>` 派一个自己的工程师去修自己的受损完工建筑；工程师站在入口时每 tick 恢复
**4 点血量**，每个 tick 从仓库扣掉该 tick 实际恢复血量的费用。所以半血的 600 血兵营修满恰好 **300 金币**，
满血后立即停止、不会多扣。满血建筑、未完工工地、被摧毁建筑、以及付不起整笔剩余维修费的玩家都会被可读理由
拒绝，且不扣任何费用。

自己的未完工工地可以用普通的 `BUILD <engineerId> <buildingPath> <x> <y>`（用它的类型与原点）**继续建造**：
引擎会把工程师附着到已有工地，而不是再放一座；加入不收费。客户端在选中的半成品工地上显示 `继续建造`。

`RECLAIM <buildingId>` 取消自己的一座**未完工**工地。所有已附着的建造者以
`the construction site was reclaimed by its owner` 为理由失败，工地被移除，并且**恰好一半放置成本**存回仓库
——例如仓库工地返还 50 金币 + 20 木材。**已完工建筑不能回收**，拒绝文案会说明。命令本身不扣费。

## 开局资源与开局

新玩家收到 **300 金币 + 60 木材**，由 `ClassicContent.STARTING_RESOURCES` 声明一次。世界创建时由宿主把这笔库存存进玩家的仓库。

文档里的开局是一座一级兵营、一座一级矿场和两个一级近战：

| 项目 | 金币 | 木材 |
| --- | --- | --- |
| `building.armory.t1` | 100 | 20 |
| `building.mine.t1` | 80 | 0 |
| 2 x `unit.melee.t1` | 40 | 0 |
| **合计** | **220** | **20** |

剩下 80 金币和 40 木材的余量，够第一个工程师（10 金币）或第二座矿场。

## AI

`ClassicAi` 扮演一个机器人；在默认的单机器人世界里，它就是**玩家 2**，名字是 `Enemy`，红色。它每 **20 tick** —— 模拟的一秒 —— 做一次决定，并按下面这个固定优先级落入一种模式：

| 模式 | 何时 | 做什么 |
| --- | --- | --- |
| `DEFEND` | 有任何敌方单位站在 AI 建筑 **8 格**以内 | 每个空闲 AI 单位攻击最近的入侵者（按平方距离最近，平局按实体 id）；其它什么都不做 |
| `ATTACK` | 活着的战斗单位达到 6 个 | 每个**没有订单**的战斗单位向最近的敌方建筑 attack-move；已经有订单的单位不碰 |
| `ARMY` | 基地已经齐备、军队不足 8 个 | 训练部队 |
| `ECON` | 基地还没齐备 | 训练工程师、盖建筑 |

无论哪种模式（防守之外），常驻经济规则都会跑：先**升级基地**，再保持至少 **三个工程师**活着、**三座矿场**和**一座伐木场**站着，并把军队养到八个。AI 只维持**一座**兵营，并且升级它而不是再造第二座：当 t1 已完工、不是顶端、仓库又付得起 140 金币 + 40 木材时，`ClassicAi` 会在考虑任何工地或训练任务之前调用引擎的 `upgradeBuilding`。升级在同一次决定的工程师/军队训练之前执行，而且只升级**空闲**兵营，所以不会取消 AI 自己的排队任务。每次决定最多开一个生产任务、一个工地或一次升级，所以 AI 不会在一个 tick 里把自己的库存或工兵掏空。伐木场给 AI 提供可再生的木材；它和别的建筑一样走同一套有界选址搜索。

军队训练看的是**最好的空闲兵营** —— 等级最高，平局按实体 id —— 从最高级往下走五级，取第一个兵营等级允许、仓库又还付得起的等级。所以二级兵营只要金币够，就会训练 `unit.melee.t5`。它只给空闲单位下 attack-move，所以从不重发订单，也从不替换一次还在走的行军。

在它的第一个 tick，AI 自己创建玩家 2，接上引擎的仓库后勤，放下仓库、一级兵营和一级矿场作为开局基地，生成一个工程师，并存入同样的 300 金币 + 60 木材。它的兵营在下一次决定（tick 20）升到二级，自检在固定种子上锁定了这一点。它的第一座伐木场来自常驻维护，而不是开局基地。它在自己建筑的八格内防守。这里没有任何随机：单位、建筑与入侵者都按实体 id 排序，所有距离平局都按 id 打破，所以同样的世界状态与 tick 永远产生同样的命令。

## 机器人玩家

上面那个敌人现在只是**一个**机器人；游戏本身已经是一个**机器人玩家系统**，而不是写死的单个控制器。`ClassicBots` 是世界 tick 钩子，内部按机器人各持一个 `ClassicAi`；`--bots N`（默认 **1**，允许 **0..6**）决定数量。`0` 是合法世界：没有任何机器人玩家与机器人实体；`1` 就是历史上的玩家 2 `Enemy`；每多一个机器人，都是一个有自己基地、自己策略的完整 AI。0 号机器人玩玩家 2；额外的机器人玩玩家 **9、10、……**，在真人槽位 3..8 之外，所以机器人不会占掉真人的位置，真人也永远不会与机器人撞 id。

插件从 `PluginContext.options()` 的 `bots` 键读取数量；服务器把自己的 `--bots` 值放在那里，插件在缺省时使用同样的默认值。两个方向都不需要配置文件，也不需要插件与服务器互相引用对方的类。

### 出生规则

机器人 `i` 的阵营种子是

```text
factionSeed = splitmix64(worldSeed * 31 + i)
```

（SplitMix64 的 finalizer，即 `BotStrategy.mix64`）。这一个种子确定性地选出：

- **16 个罗盘方向**之一（整数单位向量表，按 256 缩放）；
- **5 个距离环**之一：离世界原点 **96、120、144、168 或 192 格**。

锚点只做整数运算——没有 `Random`、没有时钟、没有浮点三角函数——然后 AI 仍然在锚点周围按环搜索可建造地面（先 24 格，再用 64 格的有界回退），因为地形是噪声，固定格子可能是水或岩石。开局放置走引擎的 `canPlace` 规则，所以即使水域把搜索推向人类场景，机器人也不会与另一个机器人的基地或人类场景重叠。

**回放兼容：** 对历史组合（0 号机器人 + 默认种子 `1`），锚点仍是旧的固定 `(192, 192)`，策略仍是旧版。这是唯一一处刻意的例外；其它种子和每个额外的机器人都用派生锚点与派生策略。同一个种子与编号永远走同一条分支，所以规则仍是纯函数。

### 策略记录

每个机器人带一个由自己的阵营种子派生出的 `BotStrategy`。字段、闭区间与旧版取值如下：

| 字段 | 范围 | 旧版（0 号机器人，默认种子） | 作用 |
| --- | --- | --- | --- |
| `mineTarget` | **2..4** | 3 | 维持多少座矿场 |
| `engineerTarget` | **2..4** | 3 | 维持多少工程师 |
| `armyTarget` | **6..10** | 8 | 军队养到多大；**第一波**要集齐这么多才出征 |
| `attackArmySize`（后续波次阈值） | **4..8**，且不超过 `armyTarget` | 6 | 第一波之后的进攻阈值；第一波不会在集齐 `armyTarget` 之前出发 |
| `upgradeArmoryFirst` | `true` / `false` | `true` | `true`：付得起的兵营升级优先于缺的矿场/伐木场；`false`：先补经济建筑，再升级 |

所以同一世界的两个机器人不会表现得一模一样；如果派生结果恰好与旧版完全相同，会再确定性地挪开一格。AI 的其余部分（只维持一座兵营、升级阶梯、工程师/矿场/伐木场维护、最强的可负担等级、按实体 id 打破的最近目标平局）都不变。机器人之间不结盟：每个机器人都把其他机器人和真人当成敌人，和旧版单个敌人对待除自己以外所有人的方式一致。

### 确定性

机器人系统没有引入 `java.util.Random`，也没有时钟。种子混合只用整数，阵营列表按编号顺序构建，AI 读取的每个实体列表都按实体 id 排序，所以同样的世界种子与机器人数量会复现同样的锚点、策略、基地，以及逐字节相同的 600 tick 快照。不同种子会移动基地；自检对两个机器人断言了这一点。自检还断言：默认世界种子上的 `--bots 1` 与旧的单敌人运行逐字节相同。

## 安装

把插件 jar 与它运行期需要的两个 jar 一起构建：

```shell
./gradlew --offline :projects:oc:oc-game-server-classic:jar :projects:oc:oc-rts-engine:jar :projects:oc:oc-googology:jar
```

把这三个文件复制进服务器的 `plugins/` 目录：

```text
projects/oc/oc-game-server-classic/build/libs/oc-game-server-classic-2.0a.jar
projects/oc/oc-rts-engine/build/libs/oc-rts-engine-2.0a.jar
projects/oc/oc-googology/build/libs/oc-googology-2.0a.jar
```

重启服务器。安装就这些：没有配置文件，服务器服务按插件加载顺序注册的第一个世界内容。这些 jar 在 `plugins/` 里，它就跑 `oc-classic`；拿走经典 jar，它就回退到引擎的示例目录。除非你知道哪一个排在前面，否则不要往同一个目录放第二个世界内容插件。

## 自检

```shell
./gradlew --offline :projects:oc:oc-game-server-classic:selfcheck
```

自检末尾打印一行：

```text
classic plugin self check: all 458 checks passed.
```

458 条来自原来的十六节，外加这次按 owner 报告补的回归：默认单机器人世界必须在 600 tick 内至少训练出六个战斗单位并下达攻击命令，STOP 之后对真实 `CommandService` 下达的移动与建造都必须生效，九个经典建筑必须各自拥有不同的图集纹理格。门槛那一节是最严的部分：它对着真实的 `ProductionSystem` 证明一级兵营训不出 `t4` 与 `t5`，而不只是证明目录里这么写；升级那一节证明同一批阶梯在真实引擎里可执行，AI 那一节证明敌人确实通过命令到达二级，机器人那一节则证明默认单机器人世界与旧版逐字节相同，而两个机器人彼此不同、换种子会移动基地。

## 模块布局

| 路径 | 内容 |
| --- | --- |
| `src/main/java/.../classic/ClassicContent.java` | 目录：地形、物品、六个单位、九座建筑、六条配方 |
| `src/main/java/.../classic/ClassicEconomy.java` | 每 60 tick 支付矿场与伐木场收益的世界 tick 钩子 |
| `src/main/java/.../classic/ClassicAi.java` | 单个机器人玩家的确定性控制器（无参形式就是旧版的单个敌人） |
| `src/main/java/.../classic/ClassicBots.java` | 机器人玩家系统：数量、阵营种子、出生锚点与每个机器人一个控制器 |
| `src/main/java/.../classic/BotFaction.java` | 一个机器人的玩家 id、阵营种子、策略与出生锚点 |
| `src/main/java/.../classic/BotStrategy.java` | 每个机器人的策略记录、取值范围与确定性派生 |
| `src/main/java/.../classic/ClassicPlugin.java` | 插件本体：注册目录与两个钩子，从插件选项读取 `--bots` |
| `src/main/resources/omnicom-plugin.properties` | 描述文件：`id=oc-classic`、`version=1.0`、`main`、`description` |
| `src/main/resources/assets/manifest.json` | 服务器要合并的图集清单 |
| `src/main/resources/assets/atlas.png` | 96x128 的单位与建筑图集（每种建筑一个 32x32 格） |
| `src/selfcheck/java/.../classic/check/ClassicCheck.java` | 458 条检查的自检程序 |
| `build.gradle` | `selfcheck` 任务，以及挂在它类路径上的服务器检查报告器 |

## 相关

- [`../oc-game-server/help/03-plugins.md`](../oc-game-server/help/03-plugins.md) —— 生命周期、发现顺序、依赖规则与请求缝，中文。
- [`../oc-game-server/help/05-browser-endpoint.md`](../oc-game-server/help/05-browser-endpoint.md) —— `/ws`、`/plugin-assets` 路由与 WebSocket 帧规则，中文。
- [`../oc-rts-engine/help/04-units-orders-and-movement.md`](../oc-rts-engine/help/04-units-orders-and-movement.md) —— 单位、订单与移动，中文。
- [`../oc-rts-engine/help/06-economy-logistics-and-production.md`](../oc-rts-engine/help/06-economy-logistics-and-production.md) —— 仓库、存储、生产队列与建造，中文。
- [`../oc-rts-engine/help/14-combat.md`](../oc-rts-engine/help/14-combat.md) —— 这些兵种所处的战斗层，中文。
- [`../../../protocol/oc-protocol_zh_CN.md`](../../../protocol/oc-protocol_zh_CN.md) —— 服务器所说的线协议，中文。
