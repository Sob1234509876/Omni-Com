# 09 · 架构与 tick 管线

> 引擎由什么组成、一个 tick 里发生了什么事、以及你可以在哪里插手。

## 这一篇讲什么

- 六条设计原则，以及它们各自带来的约束。
- 16 个包的职责与依赖方向。
- 相位 100 → 800 的完整管线：每个系统读什么、写什么、为什么排在这个位置。
- 一 tick 内实体的增删生命周期，以及空间索引的维护责任。
- 事件总线：谁发布什么、订阅语义。
- 确定性规则清单（写自定义系统时必须遵守）。
- 扩展点清单：想换地形生成、想存档、想加系统、想加订单分别实现哪个接口。

## 六条设计原则

| 原则 | 具体表现 | 由此产生的约束 |
| --- | --- | --- |
| **无头（headless）** | 引擎不引用任何图形/输入 API；贴图只是数据 | 想画出来必须走[快照](08-snapshots-and-rendering.md)，引擎不会替你渲染 |
| **数据驱动** | 地形、物品、单位、建筑、配方全部来自 `ContentRegistry` | 引擎代码里不出现任何具体物品名；加内容不改引擎 |
| **确定性** | 固定步长、`DeterministicRandom`、稳定迭代顺序、无时钟无线程 | 不要用 `java.util.Random`、系统时间、并行流（见下面清单） |
| **单线程** | 所有系统在调用 `tick()` 的线程上顺序执行 | 引擎对象不可跨线程共享；渲染可以在另一个线程用快照 |
| **显式相位** | 系统按 `SimulationSystem.order()` 排序执行 | 一个系统不该依赖"某个同类系统已经跑过"之外的隐式顺序 |
| **零第三方依赖** | 模块只 `requires oc.projects.oc.googology`，而那个模块只 `requires java.base`（同一仓库里的兄弟模块，不是外部坐标） | 想用 JSON/数学库得自己加；`oc-googology` 里的东西不算"外部依赖"，但也别把它当成通用数学库 |

## 包结构

| 包 | 职责 | 主要类型 |
| --- | --- | --- |
| `io.github.sob1234509876.oc.rts`（根） | 门面、世界、玩家 | `OcRtsEngine`、`OcRtsEngineBuilder`、`World`、`Player` |
| `core` | 纯值类型：向量、格坐标、区块坐标、矩形、tick、确定性随机 | `Vec2`、`GridPos`、`ChunkPos`、`TileRect`、`Tick`、`EntityId`、`Direction`、`DeterministicRandom` |
| `content` | 内容目录与定义 | `ContentRegistry`、`ItemType`、`ItemBundle`、`TerrainType`、`UnitType`、`BuildingType`、`Recipe`、`ResourceKey` |
| `dimension` | 一个世界里的多张地图：身份、环境档案、一张图上的实体、地图之间的门 | `DimensionId`、`DimensionProfile`、`Dimension`、`Gateway` |
| `map` | 区块地形、流式加载、寻路 | `TerrainMap`、`ChunkedMap`、`ChunkStreamer`、`NoiseChunkGenerator`、`AStarPathFinder`、`PathOptions` |
| `entity` | 实体与空间索引 | `Entity`、`Unit`、`Building`、`Health`、`SpatialIndex`、`GridSpatialIndex` |
| `movement` | 移动意图、转向、路径请求服务 | `Motion`、`MovementSystem`、`SteeringSystem`、`SteeringSettings`、`PathService` |
| `combat` | 自动索敌、伤害与护甲、击杀结算 | `CombatStats`、`CombatSystem` |
| `formation` | 阵型几何与阵位分配 | `FormationShape`、`FormationPlanner`、`FormationPlan` |
| `order` | 订单模型与命令入口 | `Order`、`OrderQueue`、`OrderSystem`、`UnitDirector`、`CommandService`、`MoveOrder` |
| `economy` | 库存与玩家仓库网络 | `Inventory`、`Logistics`、`LogisticsReservation`、`WarehouseLogistics` |
| `logistics` | 搬运任务的实际执行 | `DeliveryJob`、`HaulingSystem`、`HaulOrder` |
| `production` | 建造与生产 | `ProductionState`、`ProductionJob`、`ProductionQueue`、`ProductionSystem`、`ConstructionSystem` |
| `visual` | 贴图/动画数据与只读快照 | `SpriteSheet`、`AnimationClip`、`VisualLibrary`、`UnitAnimator`、`AnimationSystem`、`WorldSnapshot`、`AsciiMapRenderer` |
| `sim` | 管线本身 | `SimulationSystem`、`SystemPipeline` |
| `event` | 事件总线与事件定义 | `EventBus`、`SimpleEventBus`、`Events` |

依赖方向大体是"值类型 ← 定义 ← 世界 ← 系统"：`core` 不依赖任何人，`content` 只依赖 `core`，`map` 依赖 `core`/`content`，系统包依赖根包与上面这些。允许出现包之间的相互引用（例如 `entity` 与 `order` 互相引用），Java 模块内不构成循环编译错误。

在这张表之外还有一个**仓库内的兄弟模块**：

| 模块 | Gradle 路径 | Java 模块 / 包 | 提供什么 |
| --- | --- | --- | --- |
| 大数库 | `:projects:oc:oc-googology` | `oc.projects.oc.googology` / `io.github.sob1234509876.oc.googology` | `Natural`（数量类型）、`HyperOperation`、`NaturalFormat`，以及序数层 `Ordinal` / `OrdinalNotation` / `CantorNormalForm` / `CantorOrdinal` / `EpsilonZero` / `TransfiniteHyperoperation`（引擎的模拟代码并不使用序数，它只为库的用户准备） |

引擎的 `module-info.java` 写的是 `requires oc.projects.oc.googology`，`build.gradle` 写的是 `api project(':projects:oc:oc-googology')`——`api` 而不是 `implementation`，因为 `Natural` 出现在 `ItemType.maxStack()`、`ItemBundle.amount()`、`Inventory.insert()`、`Health.damage()` 这些导出包的公开签名上，下游代码必须能看到它。方向是单向的：`oc-googology` 不认识引擎，也不认识任何别的东西（它只 `requires java.base`，零第三方依赖），所以"离线可构建"和"引擎层可替换"都没有被破坏。

## 数值类型约定

这是写引擎代码或自定义系统时必须先记住的一条：**离散的数量用 `Natural`，时间用 `int`/`long` tick，空间用 `float`**。

| 类别 | 类型 | 为什么 |
| --- | --- | --- |
| 物品数量、容量、槽位、血量、伤害（`ItemType.maxStack()`、`ItemBundle.amount()/totalUnits()`、`Inventory` 的上下限与所有读写口、`UnitType.maxHealth()/attackDamage()/cargoCapacity()`、`BuildingType.maxHealth()/storageSlots()`、`Health.max()/current()/damage()/heal()`、`Events.DamageDealt.amount()`） | `Natural` | 数量可以挂机增长、可以被内容目录声明成任意大；`int` 会在 21 亿处回绕，`long` 会溢出，`double` 无法精确表示整数 |
| 位置、速度、半径、射程、`EntitySnapshot.healthFraction` | `float` | 它们是**空间量或比例**，不是件数；渲染要的也是比例。降级成大数只会让每 tick 的分离/转向变慢 |
| 区块与格坐标（`GridPos`、`ChunkPos`、`TileRect`） | `int` | 索引与算术，世界无界但单点坐标不需要大数 |
| 一切 tick 时长：`buildTimeTicks`、`Recipe.durationTicks`、攻击冷却、`HaulOrder.TIMEOUT_TICKS`、各系统的超时与冷却 | `int` / `long` | 时间步数参与每 tick 的减法，用大数没有意义、还会拖慢模拟 |

`Natural` 的语义（精确阈值、结构形算术、`BEYOND`）见 [12-numbers-and-googology.md](12-numbers-and-googology.md)，这里只保留与确定性有关的两点：

- **确定性与数值类型的关系**：`Natural` 的所有运算都是纯函数——同样输入永远得到同样结果，没有随机、没有时钟、没有浮点舍入路径上的平台差异（`BigInteger` 与整数比较都是严格定义的）。所以引入它**不会**削弱下面的确定性规则；
- 反过来，`float` 仍然是位置与速度的类型，而 Java 17 的 `float`/`double` 基本算术在所有平台上严格一致（见下面规则 5），这才让"同种子逐位一致"成立。两者各守一段，别互相替换：把坐标换成 `Natural` 不会提高精度（只是把 24 位有效位换成无界），把数量换成 `float` 则会直接丢件数。

## 一个 tick 做了什么

`OcRtsEngine.tick()` 的顺序是：**推进 tick 号 → 按相位跑完全部系统 → `world.flush()` 清理死亡实体**。

| 相位 | 系统 | 读 | 写 |
| --- | --- | --- | --- |
| 100 | `chunk-streamer`（`ChunkStreamer`） | 每个维度各自的 `map.focus()`、`map.loadedChunks()` | 加载/卸载区块（预算跨维度共享）；发布带维度的 `Events.ChunkLoaded` / `ChunkUnloaded` |
| 200 | `orders`（`OrderSystem`） | 每个单位的 `OrderQueue` | 启动/推进/结束订单；发布 `Events.OrderIssued` / `OrderFinished` |
| 300 | `paths`（`PathService`） | 路径请求队列 | 调用 `PathFinder` 并把结果写回 `Motion.setPath(...)` |
| 400 | `movement`（`MovementSystem`） | `Motion` 的目标与路径、`SpatialIndex` 邻居 | 请求新路径、更新位置/速度/朝向、`spatial().update(...)`、`markArrived()` |
| 450 | `combat`（`CombatSystem`） | 每个单位的 `CombatStats`、攻击冷却、本维度空间索引、生命 | 目标 id、朝向、冷却、伤害；发布 `Events.DamageDealt` / `EntityKilled`，标记死亡 |
| 500 | `construction`（`ConstructionSystem`） | 未建成建筑、`Building.builderCount()` | 按 `min(附着的建造者数, MAX_BUILDERS_PER_SITE=4)` 推进建造进度；完工 tick 结束所有附着 `BuildOrder`；发布 `Events.ConstructionCompleted` |
| 600 | `production`（`ProductionSystem`） | 建筑、`ProductionQueue`、玩家 `Logistics` | 预订材料、消耗材料、生成单位/物品；发布 `Events.UnitProduced` / `ItemProduced` / `ProductionStateChanged` |
| 700 | `hauling`（`HaulingSystem`） | `DeliveryJob`、空闲搬运工、`world.route(...)` | 给**同维度**的搬运工派单（把 `HaulOrder` 推入其订单队列）；跨维度的货先走 `tickShipped`（按门链路跳数计延迟）；发布交付相关事件 |
| 800 | `animation`（`AnimationSystem`） | 单位/建筑状态、`Motion` | 切换动画片段、设置朝向、推进帧 |
| — | `world.flush()` | 死亡实体列表 | 从实体集合与空间索引中真正移除 |

为什么是这个顺序：

- **区块最先**：任何地形查询都会触发生成，先让焦点内的区块在内存里，后面的寻路与建造查询更便宜。
- **订单在寻路之前**：订单只设置目标，随后同一 tick 的寻路与移动就能用上这个目标，减少一 tick 延迟。
- **寻路在移动之前**：新算好的路径在同一 tick 就能被消费。
- **生产与搬运在移动之后**：它们可能生成单位或下达搬运命令，此时本 tick 的移动已经跑完，新单位从下一 tick 开始动，不会边遍历边插入。
- **战斗紧跟移动**：命中要落在单位这一 tick 真正到达的位置上；订单（200）先决定去哪，移动（400）把人带过去，战斗（450）才按新位置判断射程。
- **动画最后**：它只读取本 tick 结束时的状态，所以显示的永远是最终姿态。

`SystemPipeline` 在 `add()` 时按 `order()` 排序；`order()` 相同则保持加入顺序。想打印当前管线用 `engine.pipeline().describe()` 或 `engine.describe()`。

## 一 tick 内实体的增删

| 动作 | 立即发生 | 延后发生 |
| --- | --- | --- |
| `world.spawnUnit(...)` / `spawnBuilding(...)` / `placeBuilding(...)` | 分配 `EntityId`、加入该维度的 `entities/units/buildings`、插入**那个维度**的空间索引、发布 `Events.EntitySpawned` | — |
| `world.destroy(entity)` | `kill()`（血量归零）、从**它所属维度**的空间索引移除、发布 `Events.EntityDestroyed` | 从集合中移除：tick 末的 `world.flush()`（每个维度各刷一遍） |
| `world.transfer(entity, to, position)` | `detach`（源维度）→ 改位置 → `register`（目标维度，发布 `EntitySpawned`）→ 发布 `Events.EntityTransferred`；**id / 血量 / 背包 / 订单全部保留** | — |
| 移动实体 | `entity.setPosition(...)` | 空间索引只有在调用 `world.spatial(unit.dimensionId()).update(entity)` 后才知道新位置 |

由此得到两条硬性约定：

1. **不要在遍历 `world.units()` 的过程中生成单位**（会 `ConcurrentModificationException`）。系统跑在不同相位，所以"生产系统生成单位"是安全的；如果你在自己的系统里生成单位，请先遍历 `world.unitsOf(player)`（它返回新列表）或先把单位收集出来。
2. **改过位置必须 `spatial(dimensionId).update(...)`**，否则分离避让、`nearest(...)`、快照取实体都会用到旧位置。多维度下"哪个索引"必须跟着实体走：`world.spatial()` 是默认维度的，位置相关代码用 `world.spatial(unit.dimensionId())`（见 [13-dimensions.md](13-dimensions.md)）。

## 事件总线

`World.events()` 是一个同步的、按订阅顺序分发的总线（[`SimpleEventBus`](../src/main/java/io/github/sob1234509876/oc/rts/event/SimpleEventBus.java)）：

```java
EventBus.Subscription subscription = engine.events().subscribe(Events.ItemProduced.class,
        event -> System.out.println("产出 " + event.items() + " 于 " + event.building()));
// 不需要时
subscription.cancel();
```

订阅按 `Class.isInstance(event)` 匹配，所以订阅父类型能收到子类型（当前事件都是 record，没有继承关系）。监听器在发布线程上同步执行，抛异常会直接冒泡到 `tick()` 调用方——调试时很有用，上线前记得自己兜住。

已定义事件与它们的发布者：

| 事件 | 谁发布 | 何时 |
| --- | --- | --- |
| `Events.EntitySpawned` / `EntityDestroyed` | `Dimension` / `World` | 实体登记进某个维度的瞬间 / 标记销毁的瞬间 |
| `Events.EntityTransferred` | `World` | 实体换了维度（`from` → `to`），**在**落地那次 `EntitySpawned` **之后**发布 |
| `Events.ChunkLoaded` / `ChunkUnloaded` | `ChunkStreamer` | 某一张图的区块进出内存（事件带 `dimension` 分量） |
| `Events.OrderIssued` / `OrderFinished` | `OrderQueue` | 订单成为当前订单 / 离开当前订单（含 `CANCELLED`） |
| `Events.ConstructionCompleted` | `ConstructionSystem` | 建筑建造完成 |
| `Events.ItemProduced` / `UnitProduced` / `ProductionStateChanged` | `ProductionSystem` | 物品入库、单位出厂、任务状态变化 |
| `Events.DamageDealt` | `CombatSystem` | 每次命中；`amount` 是**实际扣掉**的血（可能被剩余血量截断） |
| `Events.EntityKilled` | `CombatSystem` | 目标的血第一次到 0，**在** `World.destroy` 之前发布 |

## 确定性规则清单

写自己的系统时逐条对照：

1. 随机数只用 `world.random()` 或它的 `fork(...)`（`DeterministicRandom`，splitmix64），**不要** `java.util.Random`、`Math.random()`。
2. 不要读系统时间（`System.currentTimeMillis()`、`nanoTime`）；时间只能来自传入的 `Tick`。
3. 不要开线程、线程池、`parallelStream()`。
4. 遍历顺序要固定：用 `List`/`LinkedHashMap`/`TreeMap`；避免依赖 `HashSet`/`HashMap` 的迭代顺序（要排序时用实体 `id()` 或坐标做平局判定）。
5. 浮点运算全用 `float`/`double` 基本算术；Java 17 的浮点是严格确定的，不要引入 `StrictMath` 与 `Math` 混用的差异写法。数量的算术用 `Natural`（它同样是严格确定的纯函数），不要把两者混着用：位置是 `float`，件数是 `Natural`。
6. 集合的"谁先谁后"会影响结果（比如同时到达的两个人抢同一个阵位），平局必须显式打破——引擎里统一用 `EntityId`/坐标比较。
7. 不要在系统里缓存跨越 tick 的派生状态而不跟着世界更新（例如自己缓存的仓库列表要订阅 `EntitySpawned`/`EntityDestroyed` 维护，像 `WarehouseLogistics` 那样）。

## 单线程与调用约定

- `tick()` 只能在同一个线程里调用；引擎没有同步措施。
- 在系统里调用 `world.destroy(...)` 是安全的：它只做标记。
- 在系统里调用 `world.spawnUnit(...)` 是安全的，但请看上面的遍历约定。
- 长时间计算必须自我限流：`PathService` 用"每 tick 请求数 + 每 tick 节点预算"来限制 A* 的总开销，`ChunkStreamer` 用 `chunksPerTick` 限制区块生成。你自己的系统也应该有类似预算，否则一帧就会卡住。
- 渲染层可以拿 `engine.snapshot(...)` 的**不可变拷贝**去别的线程画；不要把 `World`、`Unit` 这些活对象传出去。

## 扩展点

| 想做什么 | 实现 / 使用 | 注册位置 | 详见 |
| --- | --- | --- | --- |
| 加一个每 tick 运行的系统 | `implements SimulationSystem`（`name()`/`tick()`/`order()`） | `engine.pipeline().add(system)` | 本文 + [10-cookbook](10-cookbook.md#9-加一个自己的系统) |
| 换地形生成算法 | `implements ChunkGenerator` | `OcRtsEngine.builder(...).generator(...)` | [03](03-map-and-pathfinding.md) |
| 把区块存到磁盘 | `implements ChunkStore` | `.chunkStore(...)` | [03](03-map-and-pathfinding.md) |
| 换寻路算法 | `implements PathFinder` | `world.setPathFinder(...)` | [03](03-map-and-pathfinding.md) |
| 加一种单位行为 | `implements Order` | `unit.orders().push(...)` | [04](04-units-orders-and-movement.md) |
| 加一种队形 | `FormationShape` 枚举 + `FormationPlanner` 几何 | 需要改源码 | [05](05-formations.md) |
| 加一种存档/统计 | 订阅事件 | `world.events().subscribe(...)` | 本文 |
| 换动画来源 | `implements VisualState` 或构建 `VisualLibrary` | `.visuals(library)` | [07](07-sprites-and-animation.md) |
| 限制某建筑只收某些物品 | `BuildingType.storageTags()`（目录 DSL 的 `.storageTag(...)`） | 目录定义 | [02](02-content-and-items.md) |
| 再加一张独立地图（维度） | `OcRtsEngineBuilder.dimension(...)` / `.moonAndMars()` | build 时接线；`build()` 之后新增的维度用 `ChunkStreamer.streamDimension(...)` 补流式 | [13](13-dimensions.md) |
| 把两张图连起来让人走过去 | `Gateway.of(...)`（单向；反向用 `reversed()`） | `world.addGateway(...)`，再用 `world.director().travel(unit, target)` | [13](13-dimensions.md) |

## 相关文档

- 想让引擎替你送货而不写搬运逻辑 → [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md)
- 想知道每个门面方法 → [01-getting-started.md](01-getting-started.md)、[appendix-api-map.md](appendix-api-map.md)
- 想知道数量为什么是 `Natural`、哪些字段仍然是 `float`/`int` → [12-numbers-and-googology.md](12-numbers-and-googology.md)
- 想知道一个世界怎么装下多张地图、哪些东西按维度分开、单位怎么走过去 → [13-dimensions.md](13-dimensions.md)
- 想按任务查做法 → [10-cookbook.md](10-cookbook.md)

## 相关源码

- 管线：[`SystemPipeline.java`](../src/main/java/io/github/sob1234509876/oc/rts/sim/SystemPipeline.java)、[`SimulationSystem.java`](../src/main/java/io/github/sob1234509876/oc/rts/sim/SimulationSystem.java)
- 模块与依赖：[`module-info.java`](../src/main/java/module-info.java)、[`build.gradle`](../build.gradle)
- 世界与实体生命周期：[`World.java`](../src/main/java/io/github/sob1234509876/oc/rts/World.java)
- 接线（相位在此确定）：[`OcRtsEngineBuilder.java`](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngineBuilder.java)
- 事件：[`Events.java`](../src/main/java/io/github/sob1234509876/oc/rts/event/Events.java)、[`SimpleEventBus.java`](../src/main/java/io/github/sob1234509876/oc/rts/event/SimpleEventBus.java)
- 数量类型：[`Natural.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/Natural.java)、[`NaturalFormat.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/NaturalFormat.java)、[`HyperOperation.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/HyperOperation.java)
