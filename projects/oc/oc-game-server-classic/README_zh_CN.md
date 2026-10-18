[English](README.md) | 简体中文

# Omni Com Classic 世界插件

`projects:oc:oc-game-server-classic` —— 世界内容插件 `oc-classic`。服务器从自己的 `plugins/` 目录里捡插件；这个 jar 注册一场游戏：五级近战、一个工程师、两级兵营、三级矿场和一座仓库，外加替它运转的矿场收益钩子与敌人 AI。它不注册请求、不打开文件、不起线程。jar 在目录里，服务器就服务这个世界而不是引擎的示例目录；拿走它，服务器就回退到示例。

## 坐标

| | |
| --- | --- |
| Gradle 路径 | `:projects:oc:oc-game-server-classic` |
| Java 模块 | `oc.projects.oc.game.server.classic` |
| 基础包 | `io.github.sob1234509876.oc.server.classic` |
| 组 / 版本 | `io.github.sob1234509876.oc` / `2.0a` |
| 构建 | `java-library`、Java 17 工具链、JPMS |
| 依赖 | `oc-game-server`（插件 API）与 `oc-rts-engine`（目录类型）；引擎会带上 `oc-googology` |
| 自检 | `./gradlew --offline :projects:oc:oc-game-server-classic:selfcheck`（288 项检查） |
| 世界内容 | `oc-classic` |
| 素材 | `assets/manifest.json` 与 96x96 的 `assets/atlas.png` |
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

| 路径 | 名字 | 等级 | 占地 | 血量 | 成本 | 建造 tick | 仓储格 | 可训练 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `building.armory.t1` | 兵营·普通 | 1 | 3x3 | 600 | 100 金币 + 20 木材 | 150 | 6 | `t1`..`t3`、工程师 |
| `building.armory.t2` | 兵营·强化 | 2 | 3x3 | 900 | 240 金币 + 60 木材 | 240 | 8 | 全部五级、工程师 |

规则是 **t1 产出 t1..t3，t2 产出全部五级**。这不是插件约定：下面三级与工程师要求建筑等级 1，上面两级要求等级 2，而 `ProductionSystem.enqueue` 会拒绝建筑等级够不着的配方。

## 三级矿场

| 路径 | 名字 | 等级 | 收益（金币 / 分钟） | 成本（金币） | 建造 tick |
| --- | --- | --- | --- | --- | --- |
| `building.mine.t1` | 普通 | 1 | 30 | 80 | 120 |
| `building.mine.t2` | 强化 | 2 | 60 | 160 | 180 |
| `building.mine.t3` | 精英 | 3 | 120 | 320 | 240 |

每座矿场都是 3x3、500 血、六个仓储格。收益是**内容数据，不是引擎机制**：引擎没有被动收入 tick，所以每座矿场把自己的速率发布成 `income_<n>` 标签，由插件的 `ClassicEconomy` 钩子付出去。

## 经济规则

每 **60 tick** —— 经典游戏的一分钟，在宿主每秒 20 tick 下就是三秒 —— 每个玩家都会收到以金币计的**自己所有矿场收益之和**。三座一级矿的玩家在那一个 tick 收到 90 金币；三种等级各一座的玩家收到 210。只有**既已建成、又还活着**的矿场才付钱：还在施工的工地与已被摧毁的矿场都不付。

这笔和通过引擎的后勤网支付，作为一个整堆存进拥有者的仓库。**没有仓库就一分不付**：金币不会掉在地上，也不会排队等着，所以丢光了仓库的玩家也就停止收入。钩子记得自己上一次付钱的 tick，所以同一个 tick 被调用两次也不会付两次。

## 仓库

`building.warehouse` 就是引擎的下货点。引擎把后勤网定义成“所有建成的、类型是仓库的建筑”，所以没有专门的 drop-off 类型：目录里放的是一座与引擎示例同类的 3x3 仓库 —— 500 血、24 个仓储格、60 木材、120 tick。

它是目录的一部分，而不是这个模块的发明：没有它，开局资源无处安放、生产无法供料、矿场收益也无法支付。

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

`ClassicAi` 扮演**玩家 2**，名字是 `Enemy`，红色。它每 **20 tick** —— 模拟的一秒 —— 做一次决定，并按下面这个固定优先级落入一种模式：

| 模式 | 何时 | 做什么 |
| --- | --- | --- |
| `DEFEND` | 有任何敌方单位站在 AI 建筑 **8 格**以内 | 每个空闲 AI 单位攻击最近的入侵者（按平方距离最近，平局按实体 id）；其它什么都不做 |
| `ATTACK` | 活着的战斗单位达到 6 个 | 每个**没有订单**的战斗单位向最近的敌方建筑 attack-move；已经有订单的单位不碰 |
| `ARMY` | 基地已经齐备、军队不足 8 个 | 训练部队 |
| `ECON` | 基地还没齐备 | 训练工程师、盖建筑 |

无论哪种模式（防守之外），常驻经济规则都会跑：保持至少 **三个工程师**活着、至少**两座兵营**和**三座矿场**站着，并把军队养到八个。每次决定最多开一个生产任务、一个工地，所以 AI 不会在一个 tick 里把自己的库存或工兵掏空。

军队训练看的是**最好的空闲兵营** —— 等级最高，平局按实体 id —— 从最高级往下走五级，取第一个兵营等级允许、仓库又还付得起的等级。所以二级兵营只要金币够，就会训练 `unit.melee.t5`。它只给空闲单位下 attack-move，所以从不重发订单，也从不替换一次还在走的行军。

在它的第一个 tick，AI 自己创建玩家 2，接上引擎的仓库后勤，放下仓库、二级兵营和一级矿场作为开局基地，生成一个工程师，并存入同样的 300 金币 + 60 木材。它在自己建筑的八格内防守。这里没有任何随机：单位、建筑与入侵者都按实体 id 排序，所有距离平局都按 id 打破，所以同样的世界状态与 tick 永远产生同样的命令。

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
classic plugin self check: all 288 checks passed.
```

288 条来自十二节：内容目录 82、五级数值阶梯 40、兵营等级门槛 14、矿场收益阶梯 20、矿场收益 18、开局资源 10、素材清单与图集 17、世界主机与快照 27、敌人 AI 25、确定性 6、插件接口 17、插件加载器 12。门槛那一节是最严的部分：它对着真实的 `ProductionSystem` 证明一级兵营训不出 `t4` 与 `t5`，而不只是证明目录里这么写。

## 模块布局

| 路径 | 内容 |
| --- | --- |
| `src/main/java/.../classic/ClassicContent.java` | 目录：地形、物品、六个单位、六座建筑、六条配方 |
| `src/main/java/.../classic/ClassicEconomy.java` | 每 60 tick 支付矿场收益的世界 tick 钩子 |
| `src/main/java/.../classic/ClassicAi.java` | 扮演玩家 2 的确定性敌人 |
| `src/main/java/.../classic/ClassicPlugin.java` | 插件本体：注册目录与两个钩子 |
| `src/main/resources/omnicom-plugin.properties` | 描述文件：`id=oc-classic`、`version=1.0`、`main`、`description` |
| `src/main/resources/assets/manifest.json` | 服务器要合并的图集清单 |
| `src/main/resources/assets/atlas.png` | 96x96 的单位与建筑图集 |
| `src/selfcheck/java/.../classic/check/ClassicCheck.java` | 288 条检查的自检程序 |
| `build.gradle` | `selfcheck` 任务，以及挂在它类路径上的服务器检查报告器 |

## 相关

- [`../oc-game-server/help/03-plugins.md`](../oc-game-server/help/03-plugins.md) —— 生命周期、发现顺序、依赖规则与请求缝，中文。
- [`../oc-game-server/help/05-browser-endpoint.md`](../oc-game-server/help/05-browser-endpoint.md) —— `/ws`、`/plugin-assets` 路由与 WebSocket 帧规则，中文。
- [`../oc-rts-engine/help/04-units-orders-and-movement.md`](../oc-rts-engine/help/04-units-orders-and-movement.md) —— 单位、订单与移动，中文。
- [`../oc-rts-engine/help/06-economy-logistics-and-production.md`](../oc-rts-engine/help/06-economy-logistics-and-production.md) —— 仓库、存储、生产队列与建造，中文。
- [`../oc-rts-engine/help/14-combat.md`](../oc-rts-engine/help/14-combat.md) —— 这些兵种所处的战斗层，中文。
- [`../../../protocol/oc-protocol_zh_CN.md`](../../../protocol/oc-protocol_zh_CN.md) —— 服务器所说的线协议，中文。
