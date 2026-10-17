[English](README.md) | 简体中文

# Omni Com RTS 引擎

`projects:oc:oc-rts-engine` —— Omni Com 即时战略游戏的无头引擎：世界、单位、经济与生产，不包含渲染、窗口和输入处理。GUI 构建在下面描述的只读快照 API 之上。

## 文档

面向任务的指南（中文撰写，API 名称为英文）位于 [`help/`](help/README.md)：

| 指南 | 主题 |
| --- | --- |
| [01 入门](help/01-getting-started.md) | 构建、自检、最小示例、门面参考 |
| [02 内容与物品](help/02-content-and-items.md) | 目录 DSL、物品包、库存、仓库 |
| [03 地图与寻路](help/03-map-and-pathfinding.md) | 区块流式加载、地形生成、A*、寻路预算 |
| [04 单位、命令与移动](help/04-units-orders-and-movement.md) | 实体、`Motion` 契约、命令、转向 |
| [05 阵型](help/05-formations.md) | 阵型形状、槽位分配、间距 |
| [06 经济、物流与生产](help/06-economy-logistics-and-production.md) | 库存规则、预留、搬运、队列 |
| [07 精灵与动画](help/07-sprites-and-animation.md) | 图集、剪辑、动画集、动画器 |
| [08 快照与渲染](help/08-snapshots-and-rendering.md) | 只读快照、渲染循环、相机/流式加载 |
| [09 架构与 tick 流水线](help/09-architecture-and-tick-pipeline.md) | 包、阶段、事件总线、确定性、扩展点 |
| [10 食谱](help/10-cookbook.md) | 19 个常见任务的做法 |
| [11 限制与常见问题](help/11-limits-and-faq.md) | 未实现的内容、取舍、按症状查找的答案 |
| [12 数值与 googology](help/12-numbers-and-googology.md) | `Natural` 数量类型：精确范围、箭头记法、近似、`BEYOND` |
| [13 维度](help/13-dimensions.md) | 一个世界中的多张地图（主世界、月球、火星）：按维度的空间索引、环境配置、流式加载预算、事件、网关与旅行 |
| [附录：API 地图](help/appendix-api-map.md) | 所有公共类型、常量、门面方法表 |

## 坐标

| | |
| --- | --- |
| Gradle 路径 | `:projects:oc:oc-rts-engine` |
| Java 模块 | `oc.projects.oc.rts.engine` |
| 基础包 | `io.github.sob1234509876.oc.rts` |
| 组 / 版本 | `io.github.sob1234509876.oc` / `2.0a` |
| 构建 | `java-library`、Java 17 工具链、JPMS、**无第三方依赖** |
| 依赖 | `api project(':projects:oc:oc-googology')` —— 仓库内的大数库，提供用于数量的 `Natural` 类型 |

## 试一试

```shell
./gradlew :projects:oc:oc-rts-engine:scenario
```

该场景是一个无头自检（无测试框架、无网络），会打印它观察到的内容：地形按区块流入和流出、一条绕开水域的路线、一个小队集结、仓库对物品的准入控制、一座兵营训练士兵、动画帧推进、三个维度并排模拟，以及一幅世界的 ASCII 图。
成功运行以以下内容结束：

```text
RTS engine scenario: all 153 checks passed.
```

自检是一个程序，而不是基于框架的测试套件，因此它位于自己的 `selfCheck` 源集（`src/selfcheck/java`）中；`src/test/java` 保持为空，留给以后真正的单元测试。

该场景逐节实际证明了什么：

| 小节 | 它打印的证据 |
| --- | --- |
| 1 分块地形 | 视距为 2 个区块时在内存中保留 5x5 个区块，跳转到 (4000, 4000) 后它们又被卸载，从未访问过的地块按需生成，64x64 的区块包含六种地形 |
| 2 寻路 | 存在一条 60 多个地块的路线，每个路点都可通行，相邻路点之间视线通畅，深水中的目标要么干净地失败，要么解析到岸边 |
| 3 阵型 | 十二名士兵被命令前往同一地块，最终分散开来，没有任何一对重叠，每个单位都走完了距离，五种形状产生五种不同的分布（line 0.85x9.87、column 7.27x2.53、box 2.02x3.28、wedge 3.48x3.64、circle 2.73x4.04） |
| 4 物品与仓库 | 46 种物品；已入库的货物对物流可见，而工厂缓冲区或背包中的货物不可见；预留控制支出；槽位限制和存储过滤器生效 |
| 5 生产 | 兵营用库存材料训练士兵并消耗它们，被阻塞的任务会等待而不是消失，工坊冶炼钢铁并制造步枪 |
| 5b 搬运 | 启用搬运后，工人真的会走到仓库，装载矿石和木板，并在兵营能够训练之前把它们送达 |
| 6 精灵与动画 | 生成的单位播放 `idle`，接到命令后切换到 `move`，推进帧，面向其移动方向，快照会解析出一个具体的精灵帧，包含图集和像素矩形 |
| 7 确定性 | 相同种子和相同命令的两次运行产生逐位相同的单位位置 |
| 10 巨大数值 | 一个物品堆叠为 `10^5000`，一个单位拥有 `10↑↑10` 生命值：1000 点伤害伤不到它，`Natural.BEYOND` 伤害能杀死它，一个宝库报告 `BEYOND` 个槽位，而无上限的宝库仍然未满，同样结构数量的四槽宝库则已满 |
| 11 维度 | `moonAndMars()` 给出三个维度；在主世界和月球同一地块生成的两个单位保持各自的 id，每个空间索引只有一个实体，且没有跨地图邻居；把焦点放在遥远的火星只加载火星区块；移动命令让单位留在月球；销毁它后主世界的单位仍然存活；两个网关通过月球把主世界连到火星，路线为两跳，网关是单向的，旅行的单位保持其 id，并在每一跳触发一次 `EntityTransferred` |

生成世界的真实预览，仅地形，每地块一个字符
（`~` 水、`:` 沙、`.` 草、`f` 森林、`^` 丘陵、`#` 岩石；单位绘制为 `C`、`W`、`S`）：

```text
ffffffffff:::::::ffffffffffffff::::ffffffffffffff
ffffffff::::::::::fffffffffffff:::::fffffffffffff
ffffffff::::~~~~~::fffffffffff:::~:::ffffffffffff
fffffff::::~~~~~~::fffffffffff::~~~::ffffffffffff
fffffff::::~~~~~~::ffffffffff::~~~~:::fffffffffff
fffffff::::::::::::ffffffffff::~~~~~::::::::fffff
fffffff::::::::::::fffffffff:::~~~~~~~::::::::fff
ffffffff:::::::::ffffffffff:::~~~~~~~~~~~~~::::ff
ffffffff::::::fffffff::::::::~~~~~~~~~~~~~~~:::ff
^ffffffff:::ffffffff::::::::~~~~~~~~~~~~~~~~~:::f
```

## 引擎的功能

| # | 功能 | 所在位置 |
| --- | --- | --- |
| 1 | 分块、流式加载的地形地图 | `map.ChunkedMap`、`map.ChunkStreamer`、`map.ChunkGenerator`、`map.NoiseChunkGenerator`、`map.ChunkStore` |
| 1 | 在该地图上寻路 | `map.AStarPathFinder`、`movement.PathService`（按 tick 的预算） |
| 2 | 会集结的群体移动 | `formation.FormationPlanner`、`formation.FormationPlan`、`movement.MovementSystem`、`order.FormationMoveOrder` |
| 3 | 能生产的工厂 | `production.ProductionSystem`、`production.ProductionQueue`、`production.ConstructionSystem` |
| 4 | 任意物品，只能从仓库使用 | `content.ItemType`、`economy.Inventory`、`economy.WarehouseLogistics`、`logistics.HaulingSystem` |
| 5 | 精灵与动画（仅数据） | `visual.VisualLibrary`、`visual.AnimationSet`、`visual.UnitAnimator`、`visual.AnimationSystem` |
| 6 | 一个世界中的多张地图（维度） | `dimension.DimensionId`、`dimension.DimensionProfile`、`dimension.Dimension`、`dimension.Gateway`、`World` 的维度访问器、`map.ChunkStreamer` |
| 7 | 通过网关在地图之间旅行 | `World.addGateway`、`World.route`、`order.TransferOrder`、`order.UnitDirector.travel`、`event.Events.EntityTransferred` |

## 架构

```
core      向量、地块、区块、id、tick、确定性随机
content   定义目录：地形、物品、单位、建筑、配方
dimension 维度标识、环境配置、网关、一张地图及其实体
map       分块地形、流式加载、A* 寻路
entity    实体、空间索引、单位与建筑状态
movement  转向、移动系统、带预算的寻路请求
formation 阵型形状与槽位分配
order     命令、命令队列、单位指挥器（命令 API）
economy   库存、玩家的物流网络
logistics 由工人单位执行的搬运任务
production 建造与建筑的生产队列
visual    精灵图集、动画剪辑、动画器、世界快照
sim       模拟系统接口与 tick 流水线
event     同步事件总线以及系统发布的事实
```

引擎模块（`oc.projects.oc.rts.engine`）需要一个同级模块 `oc.projects.oc.googology`（`projects/oc/oc-googology`）：`Natural` 是物品、容量、生命值和伤害的数量类型，`NaturalFormat` 负责把它渲染到日志和控制栏中。

### tick 流水线

`OcRtsEngine.tick()` 让模拟前进一个固定时间步，按阶段顺序运行每个已注册的系统，然后移除在本次 tick 中死亡的实体：

| 阶段 | 系统 | 职责 |
| --- | --- | --- |
| 100 | `ChunkStreamer` | 加载每个维度关注点周围的区块，卸载其余区块，共用一个预算 |
| 200 | `OrderSystem` | 推进每个单位的命令队列 |
| 300 | `PathService` | 在节点预算内处理排队的寻路请求 |
| 400 | `MovementSystem` | 转向、分离、地形约束、到达 |
| 500 | `ConstructionSystem` | 为建造中的建筑倒计时 |
| 600 | `ProductionSystem` | 材料、生产进度、生成单位 |
| 700 | `HaulingSystem` | 在目标自己所在的地图上挑选搬运工，通过网关链运送跨维度货物 |
| 800 | `AnimationSystem` | 选择动画剪辑并推进帧 |

## 快速开始

```java
ContentRegistry content = ContentRegistry.builder("oc")
        .terrain("grass").name("Grass").buildable()
        .terrain("water").name("Water").impassable()
        .item("iron_ingot").name("Iron Ingot").maxStack(60)
        .unit("soldier").name("Soldier").role(UnitRole.COMBAT).health(120).speed(2.4f).radius(0.35f)
                .cost("iron_ingot", 50).buildTime(120).spriteSet("soldier")
        .building("warehouse").name("Warehouse").size(3, 3).storage(24).warehouse()
        .building("barracks").name("Barracks").size(3, 3).storage(6).producer("barracks")
        .recipe("train_soldier").producer("barracks").input("iron_ingot", 50)
                .unitOutput("soldier").duration(120)
        .build();

OcRtsEngine engine = OcRtsEngine.builder(content)
        .seed(42L)
        .chunkSize(16)
        .viewRadiusChunks(4)
        .directDelivery(true)   // 交付在延迟后完成，而不需要搬运单位
        .build();

Player player = engine.players().get(0);
Building warehouse = engine.spawnBuilding("warehouse", player, new GridPos(10, 10));
warehouse.inventory().insert(content.item("iron_ingot"), 400);

Building barracks = engine.spawnBuilding("barracks", player, new GridPos(20, 10));
engine.production().enqueue(barracks, content.recipe("train_soldier"), 5);

engine.tick(600);
engine.commands().moveGroup(engine.world().unitsOf(player), new GridPos(60, 40), FormationShape.WEDGE);
engine.tick(400);

System.out.println(engine.render(new GridPos(60, 40), 20));
```

## 引擎强制执行的规则

* **物品只能从仓库使用。** `Logistics`（可通过 `player.logistics()` 访问）是所有被标记为 `warehouse()` 的建筑类型的库存之并。工厂缓冲区、搬运工货舱或地面上的货物都不算数；没有实际存放在库存中的材料时，生产拒绝开始。
* **没有东西凭空产生。** 生产任务会预留其输入，让它们被送进工厂，消耗它们，然后才生产；放不下的物品产出留在缓冲区中。
* **引擎从不硬编码物品名称。** 所有内容都是 `ContentRegistry` 中的数据，因此一个场景可以定义三十种或三百种物品，而无需改动引擎代码。
* **数量是任意精度的自然数。** 数量、堆叠大小、容量、生命值和伤害都是 `Natural` 值（`io.github.sob1234509876.oc.googology`）：当一个值最多需要 4096 位时是精确整数，超出后则是诸如 `10^5000` 或 `10↑↑10` 的超运算塔。那里的算术按设计是近似的，就像浮点数对极小分数是近似的一样；排序保持忠实，因此比较和排序仍然可靠。位置、速度和 tick 时长有意保持为 `float`/`int`。
* **模拟是确定性的。** 固定时间步、不依赖墙上时钟、无多线程、带种子的 `DeterministicRandom`、稳定的迭代顺序：相同的种子和相同的命令可以精确重放。
* **客户端逐 tick 驱动它。** `tick()`、`tick(n)` 和 `tickUntil(condition, maxTicks)`；没有内部循环，因此 GUI、AI 或测试掌控节奏。

## 面向未来 GUI 的接缝

* `engine.snapshot(center, radius)` 返回一个不可变的 `WorldSnapshot`：可见地块的地形，以及带有位置、所属方颜色、生命值比例、选中标志、动画剪辑、帧索引、朝向和解析后的 `SpriteFrame`（图集 id 加像素矩形和轴心）的实体。
* `engine.snapshot(dimension, center, radius)` 捕获一个指定维度，快照会报告它所绘制的维度 id 和显示名称，因此渲染器绝不会混淆两张共享坐标的地图。
* `engine.render(center, radius)` 把该快照渲染为文本；`AsciiMapRenderer` 是一个完全不使用图形库来消费快照的完整示例。
* 精灵集通过 id 从内容中引用（`UnitType.spriteSetId()`、`BuildingType.spriteSetId()`、`TerrainType.spriteId()`），因此渲染器通过 `VisualLibrary` 解析一切。
* `engine.streaming().focus(position, radiusChunks)` 是相机钩子：它决定默认维度哪些区块保留在内存中；有多个维度时，`engine.focus(id, position, radius)` 或 `world.dimension(id).focus(...)` 选择要跟随的地图（见 [13 维度](help/13-dimensions.md)）。

## 有意尚未实现的内容

渲染、输入、选择 UI、战争迷雾、战斗结算（`UnitType` 中的攻击数值是为它保留的数据）、AI、网络、存档和流场寻路。维度之间的旅行已经可用（网关、`UnitDirector.travel`、按维度的快照以及经网关运送的跨维度交付），但地图的环境只是数据：`DimensionProfile.hazardDamagePerTick`、`gravity`、`breathable` 和 `requiresSealedUnits()` 没有被任何代码读取，引擎唯一实际作用的是 `movementSpeedFactor`。感知维度的存档和按维度的寻路预算也还缺失。引擎核心有意保持不依赖第三方库；`map.ChunkStore` 是持久化接入的接缝。

## 目录结构

```text
src/main/java/module-info.java                    模块描述符
src/main/java/io/github/sob1234509876/oc/rts/     引擎源码（上面列出的包）
src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/   场景目录、检查、自检
```
