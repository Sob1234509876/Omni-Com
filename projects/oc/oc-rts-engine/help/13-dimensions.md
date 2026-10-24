# 13 · 维度：一个世界里的多张地图

> 一个 `World` 能装下多张彼此独立的地图（默认叫 `overworld`，可以再加 `moon`、`mars`）：坐标是**每张地图自己的**，空间索引、寻路器与焦点都按维度分开；`Gateway` + `World.route(...)` + `UnitDirector.travel(...)` 让单位通过传送门走过去，跨维度的货则按"每个门一份直送延迟"发货。

## 这一篇讲什么

- 维度是什么、为什么坐标必须是局部的、为什么空间索引不能共享。
- `DimensionId` / `DimensionProfile` / `Dimension` 三个新类型与 `World` 的维度 API。
- 怎么搭一个"地球 + 月球 + 火星"的世界：`moonAndMars()` 与逐条 `.dimension(...)`。
- 一张"哪些东西按维度分开、哪些是世界级"的对照表，以及系统作者该用哪个访问器。
- 环境档案里**已经生效的**只有移动速度倍率，其余字段是数据（含 `hazardDamagePerTick`）。
- 传送门与旅行：`Gateway` 数据、`World.route(...)` 的 BFS、`UnitDirector.travel(...)`、`TransferOrder` 的半径与超时、转移保留什么、`Events.EntityTransferred`。
- 跨维度送货：搬运工是"每张图"的，跨图的货走传送门链路，等待时间 = 直送延迟 × 跳数；**预约层本身可以给出别的维度的仓库（自校验断言了这一点），但生产端的请求链路还没跨图，所以这条链路端到端还没打通**（第 11 节把它记为待办）。
- 按维度的快照：`SnapshotFactory.capture(DimensionId, ...)`、`WorldSnapshot.dimension()` / `dimensionName()`、文本地图的图例行。
- 跨维度的区块流式：预算共享、余数怎么分、焦点互不影响。
- 事件现在带着维度；自校验第 11 节的 28 条断言；以及一份诚实的"还没有实现"清单（只剩环境危害）。

想把地形、区块与 A* 本身先看懂，见 [03-map-and-pathfinding.md](03-map-and-pathfinding.md)；想理解相位与系统约定，见 [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md)。

## 一、维度是什么

一个**维度**（`Dimension`）= 一张地图 + 站在这张地图上的一切：

| 组成 | 类型 | 说明 |
| --- | --- | --- |
| 身份 | `DimensionId` | 稳定的小写键，例如 `overworld`、`moon`、`mars`；也是引擎遍历维度的排序顺序 |
| 环境 | `DimensionProfile` | 展示名、重力、是否可呼吸、移动速度倍率、每 tick 危害伤害（全部是数据） |
| 地形 | `TerrainMap` | 每张图各有自己的生成器与区块存储 |
| 实体 | `Unit` / `Building` / `Entity` | 归属某一张图；id 仍然全世界唯一 |
| 空间索引 | `SpatialIndex` | **每张图一份**，只回答"这张图上谁在我附近" |
| 寻路器 | `PathFinder` | **每张图一份**，搜索永远不会走出这张图 |
| 焦点 | `Vec2` + 半径 | **每张图一个**，所以火星在跑图时月球可以静置不动 |

地图之间的"门"不属于任何一张图，它挂在 `World` 上：`Gateway` 记录"从哪张图的门、到哪张图的哪一格"，`World.route(...)` 把它们串成一条路线（见第十节）。

`World` 里第一张注册的维度是**默认维度**：`World` 上那些老方法（`map()`、`spatial()`、`units()`、`spawnUnit(...)`、`canPlace(...)`……）全部转发给它。所以维度功能对旧代码是**纯增量**的——只有一个维度时，行为与引入维度之前完全一样。

## 二、坐标是局部的，空间索引必须按图分开

每个维度的坐标都从自己的原点算起，所以"月球上的 `(0, 0)"` 与"地球上的 `(0, 0)"` 是两个毫不相干的位置。这正是空间索引放在 `Dimension` 而不是 `World` 的原因：如果全世界只有一个索引，`inRadius(...)` 会把另一张图上恰好站在同一坐标的单位报成"邻居"，于是分离避让、队形占位、`nearest(...)`、物流选址都会看到幻影。

实测（RtsScenario 第 11 节：同一玩家在同一格 `(-3, -3)` 的地球与月球上各生成一个单位）：

| 观察 | 结果 |
| --- | --- |
| 两个单位的 id | 不同（id 由 `World` 发放，全世界唯一） |
| 全世界实体数 | `2`（`world.entityCount()`） |
| 地球索引里的实体数 | `1`（`world.spatial(overworld).size()`） |
| 月球索引里的实体数 | `1`（`world.spatial(moon).size()`） |
| 地球索引 `inRadius(...)` 半径 3 | 看不到那个月球单位 |

两条推论：

- **"谁在我附近"要问对索引**：位置相关的代码用 `world.spatial(unit.dimensionId())`，不要用 `world.spatial()`（那是默认维度的索引）。
- **id 仍然是全局句柄**：`world.findEntity(id)` 会遍历所有维度，`world.entity(id)` 也是一样。所以一个 id 永远指向唯一一个实体，不管它站在哪张图上。

## 三、`DimensionId` / `DimensionProfile` / `Dimension`

```java
// 身份：record DimensionId(String key) implements Comparable<DimensionId>
DimensionId overworld = DimensionId.overworld();          // "overworld"
DimensionId moon      = DimensionId.of("moon");
moon.key();                                               // "moon"
moon.compareTo(DimensionId.of("mars"));                   // 字典序，键相同时才谈相等
```

键的规则：非空，且只允许 `a-z`、`0-9`、`_`、`-`、`/`，例如 `oc:moon` 这种写法是**不允许的**（大写与冒号都会被拒绝并抛 `IllegalArgumentException`）。`DimensionId` 实现 `Comparable`，比较就是 `key` 的字典序，因此它天然是 `TreeMap`/排序输出的稳定键。

环境档案是纯数据 record：

```java
public record DimensionProfile(String displayName, float gravity, boolean breathable,
        float movementSpeedFactor, long hazardDamagePerTick) { ... }
```

| 工厂 | `displayName` | `gravity` | `breathable` | `movementSpeedFactor` | `hazardDamagePerTick` | `hazardous()` | `requiresSealedUnits()` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `DimensionProfile.earth()` | `Earth` | `1f` | `true` | `1f` | `0L` | `false` | `false` |
| `DimensionProfile.moon()` | `Moon` | `0.166f` | `false` | `1.25f` | `1L` | `true` | `true` |
| `DimensionProfile.mars()` | `Mars` | `0.377f` | `false` | `1.1f` | `1L` | `true` | `true` |
| `DimensionProfile.of(name)` | 你给的名字 | `1f` | `true` | `1f` | `0L` | `false` | `false` |

构造器会校验：`displayName` 非空、`gravity >= 0`、`movementSpeedFactor > 0`、`hazardDamagePerTick >= 0`。`requiresSealedUnits()` 的定义是 `!breathable || hazardous()`，也就是"不密闭就得死的环境"。

`Dimension` 自己是一张地图加一层实体管理：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `id()` / `profile()` / `setProfile(...)` | 身份与环境 | `setProfile` 可以在场景里把某张图改成战区 |
| `map()` / `spatial()` | 地形与这张图的空间索引 | 与其他维度完全隔离 |
| `pathFinder()` / `setPathFinder(...)` | 这张图的寻路器 | 未设置时 `pathFinder()` 抛 `IllegalStateException` |
| `focus(Vec2, int)` / `focus()` | 转发/读取这张图的流式焦点 | 地图不支持流式（非 `MapStreaming`）时 `focus(...)` 返回 `false`，`focus()` 返回 `Vec2.ZERO` |
| `canPlace(BuildingType, GridPos)` | 地形可建且不与本图已有建筑重叠 | 只看这张图的索引 |
| `entities()` / `units()` / `buildings()` / `entityCount()` | 这张图的实体视图 | `entities()` 返回拷贝，`units()`/`buildings()` 是活列表 |
| `findEntity(EntityId)` / `entity(EntityId)` / `contains(Entity)` | 本图内按 id 查 | `entity(...)` 查不到抛 `IllegalArgumentException` |
| `unitsOf(Player)` / `unitsOf(PlayerId)` / `buildingsOf(Player)` / `unitsIn(TileRect)` | 本图内的玩家与范围查询 | `unitsIn(...)` 走本图索引 |
| `spawnUnit(...)` / `spawnBuilding(...)` / `placeBuilding(...)` | 直接在本图生成 | 与 `World` 的同名方法同义 |
| `register(Entity)` / `markDestroyed(Entity)` / `flush()` / `detach(Entity)` | 实体进出本图 | `register` 会调用 `entity.setDimension(id)` 并发布 `Events.EntitySpawned`；`detach` 是维间转移用的立即移除 |

## 四、`World` 的维度 API

| 方法 | 作用 |
| --- | --- |
| `defaultDimensionId()` / `setDefaultDimension(DimensionId)` | 读取/切换"单地图 API"指向哪张图（目标必须已存在，否则抛 `IllegalArgumentException`） |
| `defaultDimension()` | 默认维度本体；没有任何地图时抛 `IllegalArgumentException` |
| `addDimension(DimensionId, DimensionProfile, TerrainMap)` | 注册一张新图；**第一张注册的自动成为默认维度**；重复 id 抛 `IllegalArgumentException` |
| `findDimension(DimensionId)` / `hasDimension(DimensionId)` / `dimension(DimensionId)` | 查找；`dimension(...)` 找不到时抛 `IllegalArgumentException` 并列出已知 id |
| `dimensions()` / `dimensionCount()` | 全部维度（**创建顺序**）与数量 |
| `dimensionOf(Entity)` | 实体所属的维度；实体还没登记时（`entity.dimensionId()` 抛异常）同样抛异常 |
| `spatial(DimensionId)` / `map(DimensionId)` / `pathFinder(DimensionId)` / `setPathFinder(DimensionId, ...)` | 按维度取地形、索引与寻路器 |
| `entities(DimensionId)` / `units(DimensionId)` / `buildings(DimensionId)` | 单张图的实体 |
| `unitsOf(Player, DimensionId)` / `buildingsOf(Player, DimensionId)` | 单张图里某个玩家的实体 |
| `spawnUnit(DimensionId, ...)` / `spawnBuilding(DimensionId, ...)` / `placeBuilding(DimensionId, ...)` / `canPlace(DimensionId, ...)` | 在指定维度生成或检查 |
| `setMap(TerrainMap)` | **仍然只改默认维度**，而且只能在世界还没实体时用；默认维度已经装了实体就抛 `IllegalStateException` |
| `flush()` | 清理**每一个**维度里被标记死亡的实体（引擎每 tick 末调用一次） |
| `destroy(Entity)` | 在实体**自己所属的**维度里标记死亡并发布 `Events.EntityDestroyed` |

而下面这些是**跨全部维度**的聚合视图：

| 方法 | 语义 |
| --- | --- |
| `entities()` / `entityCount()` | 所有维度的实体 |
| `units()` / `buildings()` | 所有维度的单位/建筑（**只有一个维度时返回的就是那张图的活列表**，所以旧行为不变） |
| `unitsOf(Player)` / `buildingsOf(Player)` | 该玩家在**所有**维度上的实体 |
| `findEntity(EntityId)` / `entity(EntityId)` | 按 id 在**所有**维度里找 |

`Entity` 侧新增了三个成员：

```java
unit.dimensionId();        // 登记后才有；否则抛 IllegalStateException
unit.dimensionIfAny();     // Optional<DimensionId>，未登记时为空
unit.setDimension(id);     // 由 Dimension.register 调用，业务代码不该自己调
```

## 五、怎么搭一个月球 + 火星的世界

最短的写法是一个方法：

```java
OcRtsEngine engine = OcRtsEngine.builder(content)
        .seed(90210L)
        .viewRadiusChunks(2)
        .moonAndMars()          // 注册 moon 与 mars：各自的档案 + 各自的噪声生成器
        .build();
```

`moonAndMars()` 等价于：

```java
.dimension(DimensionId.of("moon"), DimensionProfile.moon())
.dimension(DimensionId.of("mars"), DimensionProfile.mars())
```

要更细的控制就用三档 `dimension(...)`：

```java
// 1) 只给档案：地形用从"世界种子 + 维度键"派生的 NoiseChunkGenerator
OcRtsEngineBuilder a = OcRtsEngine.builder(content).dimension(DimensionId.of("moon"), DimensionProfile.moon());

// 2) 自己给生成器
ChunkGenerator moonTerrain = new NoiseChunkGenerator(7L, content);
OcRtsEngineBuilder b = OcRtsEngine.builder(content)
        .dimension(DimensionId.of("moon"), DimensionProfile.moon(), moonTerrain);

// 3) 生成器与区块存储都自己给
ChunkStore moonStore = new InMemoryChunkStore();
OcRtsEngineBuilder c = OcRtsEngine.builder(content)
        .dimension(DimensionId.of("moon"), DimensionProfile.moon(), moonTerrain, moonStore);
```

默认维度的环境由 `.profile(DimensionProfile)` 设置（不写就是 `DimensionProfile.earth()`）。`build()` 里的接线是：

| 事项 | 行为 |
| --- | --- |
| 默认维度的键 | 固定是 `DimensionId.overworld()` |
| 每个维度的地图 | 一张自己的 `ChunkedMap`，区块边长统一取 `.chunkSize(...)` |
| 每个维度的区块存储 | **各自一个**。省略时新建 `InMemoryChunkStore`；显式 `.chunkStore(...)` 只作用于默认维度 |
| 每个维度的生成器 | 默认维度用 `.generator(...)` 或 `new NoiseChunkGenerator(seed, content)`；额外维度用 `new NoiseChunkGenerator(seedFor(id), content)` |
| 每个维度的寻路器 | 各自的 `new AStarPathFinder(map, AStarPathFinder.minimumMoveCost(content))` |
| 每个维度的焦点 | 建好后都指向原点、半径取 `.viewRadiusChunks(...)` |

> **为什么区块存储不能共享**：`ChunkStore` 只按 `ChunkPos` 索引（接口就是 `load(ChunkPos)` / `save(Chunk)` / `exists(ChunkPos)`），共用一份会让两张图在同一个 `ChunkPos` 上互相覆盖地形。所以引擎给每个维度各发一份存储，包括内存实现。

`seedFor(id)` 是"世界种子与维度键的混合"：`seed` 先异或 `0x9E3779B97F4A7C15L`，再逐字符 `mixed = (mixed ^ c) * 1099511628211L`。所以同一颗世界种子下，月球与火星各自有稳定的、不同的地貌，且完全可从世界种子复现。

## 六、环境档案：什么是真的，什么只是数据

`DimensionProfile` 的五个分量里，**引擎目前只读 `movementSpeedFactor` 一个**（record 分量由编译器生成同名的访问器方法）：

| 字段 | 现状 | 具体在哪生效 |
| --- | --- | --- |
| `movementSpeedFactor` | **已生效** | `MovementSystem` 用它乘单位的最大速度 |
| `displayName` | 数据 | 只出现在日志/调试与 `Dimension.toString()` 里，引擎不据此分支 |
| `gravity` | 数据 | 无代码读取 |
| `breathable` | 数据 | 无代码读取（`requiresSealedUnits()` 也不被引擎调用） |
| `hazardDamagePerTick` | 数据 | 无代码读取；**危害目前不会真的掉血** |

所以想让"月球更快"成为真的，什么都不用做；想让"不穿宇航服会死"成为真的，得自己写系统（订阅 `Events.EntitySpawned` 记下维度，再按 tick 扣血或调用 `Health.damage(...)`），引擎现在只把答案准备好了。

`Dimension.setProfile(...)` 可以在运行中替换档案；下一次 `MovementSystem` 读到新倍率就生效（如果系统自己缓存了档案，记得跟着更新）。

## 七、已经按维度工作的系统

| 系统 / 相位 | 用到的维度访问器 | 效果 |
| --- | --- | --- |
| `PathService`（300） | `world.map(unit.dimensionId())`、`world.pathFinder(unit.dimensionId())` | 搜索永远在单位自己那张图上，另一张图的同坐标目标走不过去 |
| `MovementSystem`（400） | `world.spatial(unit.dimensionId())`、`world.map(unit.dimensionId())`、`world.dimension(unit.dimensionId()).profile().movementSpeedFactor()` | 邻居、地形约束与最大速度都来自本图 |
| `CombatSystem`（450） | `world.spatial(unit.dimensionId())` | 目标查询只看本图；坐标局部的敌人不会被另一张图的同坐标单位误伤 |
| `ProductionSystem`（600） | `building.dimensionId()` | 单位产出到建筑**所在**的维度，不会掉到默认维度 |
| `ChunkStreamer`（100） | 每个维度一份地图与焦点 | 见第九节 |
| `HaulingSystem`（700） | `unit.dimensionId()` 与 `job.target().dimensionId()`，以及 `world.route(...)` | 挑人只看本图（见第十一节）；跨图的货走门"发运"，不会给走路单位规划跨图路线 |

**仍然整世界遍历**（暂时无害）的系统：`OrderSystem`（200）、`ConstructionSystem`（500）、`AnimationSystem`（800），以及仓库网络相关代码。它们要么只读单个实体的状态，要么把"建筑 → 库存/队列"这类非位置信息排成队列，所以跨维度遍历不会出错；但它们**还没有按维度分区**（物流的搬运部分已经按维度处理，见第十一节）。

### 自己写系统时的取用规则

| 你的代码在做什么 | 该用 |
| --- | --- |
| 位置相关（邻居、半径、矩形、避让、队形占位、A*） | `world.spatial(entity.dimensionId())`、`world.map(entity.dimensionId())`、`world.pathFinder(entity.dimensionId())` |
| 想只处理某一张图 | `world.dimensions()` 逐个迭代，再 `world.units(id)` / `world.buildings(id)` |
| 与位置无关（生产、建造计时、动画、统计、存档） | 可以用 `world.entities()` / `world.units()` / `world.buildings()` |
| 生成实体 | `world.spawnUnit(id, ...)` / `world.spawnBuilding(id, ...)`；用错重载会造到默认维度去 |
| 判断"该不该生成/该不该扣血" | 先 `entity.dimensionIfAny()`（可能还没登记），再 `world.dimension(id).profile()` |

如果必须给出一个默认值，请优先用 `entity.dimensionId()` 而不是 `world.defaultDimensionId()`：前者说"实体在哪"，后者说"老 API 指向哪"，两者在多维度下会分叉。

## 八、给系统作者的三个硬性约定

1. **永远不要把坐标跨维度比较**。`a.position().equals(b.position())` 在两个不同维度上永远是巧合，不是一个事实。要比就先比 `dimensionId()`。
2. **遍历维度用创建顺序**。`world.dimensions()` 返回的就是创建顺序（内部是 `LinkedHashMap`），而且 `ChunkStreamer` 的预算余数也按这个顺序发放——所以**先注册的维度先拿到资源**，这是刻意的确定性设计。想换优先级就换注册顺序，别在系统里自己排序。
3. **不要在迭代 `world.units()` 时生成/销毁实体**（老的约定仍然成立）；如果你在处理某一张图，用 `world.units(id)` 可以少踩一半的坑。

## 九、跨维度的区块流式与共享预算

`ChunkStreamer` 现在一次照看所有维度：

```java
public ChunkStreamer(ChunkedMap map, int radiusChunks);                    // 旧的单图构造器：注册在 overworld 下
public ChunkStreamer(Map<DimensionId, ChunkedMap> maps, int radiusChunks); // 每维度一张图

streamer.dimensions();                              // 服务的维度，按注册顺序
streamer.streamDimension(DimensionId, ChunkedMap);  // 给 build() 之后才 addDimension 的图补上流式
```

关键语义：

- **每个维度有自己的焦点**，所以"看月球"与"看火星"互不干扰：焦点不动的维度就一直停在原处。
- **每 tick 预算是共享的，不是相乘的**。`setChunksPerTick(n)` 的 `n` 是所有维度加起来的上限：实现先把 `n` 平均分给各维度（`max(1, n / 维度数)`），余数按注册顺序补给靠前的维度，用不完的份额再往下传。
- 每个维度的这一份里，**先卸载后加载**（与单图时代一致），加载顺序是两个嵌套的 `for` 循环（先 y 后 x），所以同一份种子的加载顺序完全确定。
- `loadNow(World, Tick)` 仍然无视预算，把**每个**维度的整片半径一次装满。

实测（自校验第 11 节：`viewRadiusChunks(2)` = 5×5 = 25 块/图，`seed=90210`，火星焦点被移到 `(20000, -20000)`，然后 tick 12 次）：

| 观察 | 结果 |
| --- | --- |
| 火星已加载区块 | `12` 块（`world.map(mars).loadedChunkCount()`，断言 `>= 1`） |
| 地球 / 月球已加载区块 | 各 `12` 块（同一份预算下各自推进的结果） |
| 结论 | 火星拿到自己的区块，另外两张图**没有**被火星的焦点带走 |

预算的算术：默认 `chunksPerTick = 4`，三个维度 → 每维度每 tick 一块（`4 / 3`），剩下 1 块补给**注册顺序最靠前**的维度（这里是 `overworld`）。所以在 `build()` 之后（引擎把三个焦点都设到原点，但还没 tick）：地球每 tick 拿 2 块、月球与火星各 1 块；跑满 12 个 tick 后地球正好装满自己的 25 块，月球与火星各 12 块。

聚焦某个维度有三种写法：

```java
world.dimension(DimensionId.of("mars")).focus(new Vec2(20_000f, -20_000f), 2);  // 只改火星
engine.focus(new GridPos(60, 40));                          // 门面：只改默认维度
engine.focus(DimensionId.of("mars"), new Vec2(0f, 0f), 2);  // 门面：直接点名某张图与半径
engine.streaming().focus(new GridPos(60, 40), 2);           // 默认维度的 MapStreaming 视图
```

## 十、传送门与旅行：`Gateway`、`route`、`travel`

门是**普通地图数据**，不是内容目录：场景可以随便把月球接到火星，不用改 `ContentRegistry`。

```java
// record Gateway(DimensionId from, GridPos at, DimensionId to, GridPos arrival, String name)
Gateway lift = Gateway.of(overworld, new GridPos(10, 10), moon, new GridPos(0, 0));  // name 默认 "overworld-to-moon"
Gateway back = lift.reversed();                                                       // 双向 = 两条记录
world.addGateway(lift);
```

| 成员 | 语义 |
| --- | --- |
| `from()` / `at()` | 出发维度，以及必须站上去的那一格（`at().center()` 就是判定中心） |
| `to()` / `arrival()` | 目的维度，以及单位落地的那一格（`World.transfer(...)` 用 `arrival().center()`） |
| `name()` | 日志/调试用的短标签，`Gateway.of(...)` 默认给 `from + "-to-" + to`；非空校验在这里 |
| `Gateway.of(from, at, to, arrival)` | 便捷构造器（补默认名字） |
| `reversed()` | 反向的那条门；**门是单向的**，回程是另一条记录 |
| 紧凑构造器 | 任何分量为 `null`、`name` 空白、或 `from.equals(to)` 都抛 `IllegalArgumentException`（"门必须通向另一个维度"） |

`World` 侧的门与旅行 API：

| 方法 | 作用 |
| --- | --- |
| `addGateway(Gateway)` / `gateways()` | 注册（**按注册顺序**保存）/ 全部门的快照 |
| `gatewaysFrom(DimensionId)` | 从某张图出发的门，按注册顺序 |
| `gatewayAt(DimensionId, Vec2, float)` | 某个位置半径内踩着的那条门；命中多条时取注册顺序最靠前的，返回 `Optional` |
| `route(DimensionId from, DimensionId to)` | 门图上的**广度优先**搜索：同维度返回 `Optional.of(List.of())`（空路线），不可达返回 `Optional.empty()`，可达返回按顺序的门列表 |
| `transfer(Entity, DimensionId, Vec2)` | 立刻把实体挪过去：从源索引 `detach`、改位置、进目标索引 `register`；**保留 id、血量、背包与订单**；同维度时只更新位置与索引 |

`route` 是确定性的：BFS 按注册顺序出队，同一套门布局永远给出同一条路线（这正是回放一致的前提之一）。`transfer` 会发布 `Events.EntitySpawned`（实体进入新维度）**再**发布 `Events.EntityTransferred(entity, from, to, tick)`——前者让"按事件维护索引"的订阅者照旧工作，后者才是"还是同一个实体，只是换了张图"这个事实。

让单位自己走过去只需要一句：

```java
world.director().travel(unit, DimensionId.of("mars"));   // UnitDirector.travel(Unit, DimensionId)
```

它把 `unit` 的订单**替换**成 `order.TransferOrder`（`name()` 返回 `"travel"`）：

| `TransferOrder` 成员 | 值 / 行为 |
| --- | --- |
| `GATEWAY_RADIUS` | `1.5f`：单位到门心的距离不超过它就算"踩上去了" |
| `TIMEOUT_TICKS` | `20 * 120`（两分钟，即 2400 tick） |
| `TransferOrder(DimensionId target)` / `target()` | 目的地维度 |
| 每 tick 的行为 | 已经到达 → `COMPLETED`；`route(...)` 为空 → `FAILED`（清掉目标，不会把人送出场）；走到第一个门内 → `world.transfer(...)`，还没到终点就继续朝下一跳；超时 → `FAILED` |
| 多跳 | 每次转移后都**从当前位置重新算路线**，所以"地球 → 月球 → 火星"不需要任何额外状态 |

两个要点：**没有任何瞬移是订单自己做的**，位移全部发生在 `World.transfer(...)`；**没放门就是失败**，而不是把人硬塞到另一张图上。`CommandService.travel(...)` 是 `UnitDirector` 的默认实现（与 `move`/`stop` 一样先过 `isCommandable(unit)` 检查）。

实测（自校验第 11 节：`Gateway.of(overworld, earthPad, moon, moonPad)` + `Gateway.of(moon, moonPad, mars, marsPad)`）：`world.route(overworld, mars)` 是 2 跳，`world.route(mars, overworld)` 为空（单向），单位在 400 个 tick 内走完两跳、`dimensionId()` 变成 `mars`，id 没变（`world.findEntity(id)` 还是同一个对象），两次转移各发一次 `EntityTransferred`，火星多了它、地球少了他但保留了原来那个单位。

## 十一、跨维度送货：搬运工是"每张图"的

搬运是**二维交叉**的问题：搬运工是站在某一张图上的单位，而 `DeliveryJob` 的目标可能在一张图上、供应的仓库在另一张图上。引擎现在的处理是两条规则：

- **挑人只看同一张图**。`HaulingSystem.findHauler(job)` 跳过所有 `unit.dimensionId()` 与 `job.target().dimensionId()` 不同的单位——否则一张图上"坐标恰好接近提货点"的单位会被选中，然后被派去一组毫无意义的坐标。搬运工因此永远是**本图内**的短驳。
- **跨图的货走门，不走路**。当供货仓库与目标不同图时，`HaulingSystem` 不进 `tickHaulers` 那条路，而是在它之前跑 `tickShipped(now)`：货按 **`max(1, directDeliveryTicks) * 跳数`** 的等待时间"发运"，跳数由 `world.route(仓库维度, 目标维度)` 给出。

| 情况 | `shippingHops(job)` | 结果 |
| --- | --- | --- |
| 所有能供货的仓库都在目标同一张图 | `0` | 不算跨图，交给普通搬运工 |
| 仓库在别的图，且存在门链路 | 路线长度（多条仓库取**最大**跳数） | 等 `directDeliveryTicks * hops` tick 后把货"发运"进目标缓冲区 |
| 仓库在别的图，但**没有路线** | `-1` | job 保持 pending，直到 3000 tick 超时把它判失败 |
| 玩家没有物流网络 | `0` | 不走发运分支 |

发运本身复用直送模式的那套记账：等够时间、目标 `canAcceptAll(...)` 装得下才 `reservation().commit()` 取货、`insertInto` 进目标、塞不下的 `storeBack(...)` 兜底、最后 `complete`。所以**跨维度送货是"模拟货运"，不是"走路的人"**：没有任何逻辑会给一个走路单位规划跨图路线，`UnitDirector.travel(...)` 是给玩家/脚本用的显式命令。

对经济的含义：月球基地从地球领料，成本上就是"每个门多一份直送延迟"。这也解释了为什么第 11 节场景的结束备注是"cross dimension deliveries are shipped through gateways; hazards are still data only"。

## 十二、按维度的快照

快照现在明确说"我拍的是哪张图"：

```java
WorldSnapshot shot = engine.snapshot(DimensionId.of("moon"), new GridPos(0, 0), 24);
shot.dimension();        // moon
shot.dimensionName();    // Moon（档案的展示名，给图例/小地图/天幕用）
```

| API | 行为 |
| --- | --- |
| `SnapshotFactory.capture(DimensionId, GridPos, int)` | 地形取自**该维度**的地图，实体取自**该维度**的空间索引；维度不存在抛 `IllegalArgumentException` |
| `SnapshotFactory.capture(GridPos, int)` | 旧入口，转发到 `World.defaultDimensionId()` |
| `WorldSnapshot.dimension()` / `dimensionName()` | 该快照的维度键与档案展示名 |
| `WorldSnapshot` 的旧构造器 | 仍然保留，拼合成快照时按 `DimensionId.overworld()` + `DimensionProfile.earth()` 的展示名补齐 |
| `AsciiMapRenderer` | 图例行以维度打头：`dimension moon (Moon) | tick ... | entities ...`；否则同一坐标的两张图在终端里根本分不出来 |
| `OcRtsEngine.snapshot(DimensionId, GridPos, int)` | 门面入口；旧的 `snapshot(GridPos, int)` 仍然是默认维度 |
| `OcRtsEngine.focus(DimensionId, Vec2, int)` | 给某张图单独设焦点与半径；旧的 `focus(...)` 只动默认维度 |

注意 `OcRtsEngine.render(...)` 仍然只渲染默认维度（它没有维度参数），要看别的图就用 `snapshot(dimension, ...)` 自己交给 `AsciiMapRenderer.render(shot)`。

## 十三、事件现在带着维度

`Events.ChunkLoaded` 与 `Events.ChunkUnloaded` 都增加了一个**打头的** `DimensionId` 分量：

```java
engine.events().subscribe(Events.ChunkLoaded.class, event ->
        System.out.println(event.dimension() + " +" + event.pos() + " @tick " + event.tick()));
engine.events().subscribe(Events.ChunkUnloaded.class, event ->
        System.out.println(event.dimension() + " -" + event.pos() + " @tick " + event.tick()));
```

| 事件 | 现在的分量 | 谁发布 |
| --- | --- | --- |
| `Events.ChunkLoaded` | `dimension`、`pos`、`tick` | `ChunkStreamer`，每个维度各自发布 |
| `Events.ChunkUnloaded` | `dimension`、`pos`、`tick` | 同上 |
| `Events.EntityTransferred` | `entity`、`from`、`to`、`tick` | `World.transfer(...)`，**在**落地的 `EntitySpawned` **之后**发布 |
| `Events.EntitySpawned` / `Events.EntityDestroyed` | `entity`、`tick`（**没有**维度分量，需要时用 `entity.dimensionId()`） | `Dimension.register` / `World.destroy` |

`Events.EntityTransferred` 是"同一个实体、换了张图"这个事实：它不是 `EntityDestroyed` + `EntitySpawned` 的等价物，因为 id、血量、背包与订单都还在。**订阅方不要靠 Destroyed+Spawned 去重建状态**（比如"跟随选中的单位"），否则一次旅行会被当成死亡再出生，订单与货物都会丢。

注意 `ChunkLoaded` / `ChunkUnloaded` 是**破坏性变更**：以前写 `event.pos()` 的订阅者现在必须再加上维度判断（`event.dimension().equals(...)`），否则三张图的区块会混在一起统计。

## 十四、自校验里的维度场景

`./gradlew :projects:oc:oc-rts-engine:scenario` 的第 11 节有 **28 条断言**（`seed=90210`、`chunkSize=16`、`viewRadiusChunks=2`、`moonAndMars()`，再加上两条门），它逐条打印：

```text
== 11) dimensions: independent maps for the overworld, the Moon and Mars ==
  [ok] three dimensions exist
  [ok] the default dimension is the overworld
  [ok] the overworld is Earth like
  [ok] the Moon keeps its own gravity
  [ok] the Moon is not breathable and forces sealed units
  [ok] the Moon moves units faster
  [ok] Mars has its own profile
  [ok] both units exist with different ids
  [ok] two entities in total across the world
  [ok] the overworld index holds one entity
  [ok] the Moon index holds its own one
  [ok] the overworld index cannot see the Moon unit
  [ok] each unit reports its own dimension
  [ok] an id finds its entity across dimensions
  [ok] the Mars focus loaded Mars chunks
  [ok] a moved unit stays in its dimension
  [ok] the unit actually travelled on the Moon
  [ok] the overworld unit was not dragged along by the Moon order
  [ok] the Moon is empty after the destroy
  [ok] the overworld unit survived
  [ok] the route from the overworld to Mars goes through the Moon
  [ok] gateways are one way unless reversed
  [ok] a unit standing on the pad is at the gateway
  [ok] the unit travelled to Mars through the Moon
  [ok] the transfer kept the entity id, so an id still finds it
  [ok] both gateways fired a transfer event
  [ok] Mars now holds the traveller
  [ok] the traveller left the overworld, which keeps its original unit
```

整份场景目前是 **515 项断言**（其中第 11 节维度本身 34 项；第 1b 节噪声地形、1c 节无界地形、第 12/12b 节战斗、第 13-16 节的建造扣费、可达、升级与合作建造等其余小节也计入同一总数）：

```text
RTS engine scenario: all 515 checks passed.
```

断言覆盖五件事：三张图确实存在且档案正确；同一格上的两个单位互不干扰（id 不同、全世界 2 个、各自索引各 1 个、地球索引看不到月球单位）；`findEntity` 能跨维度按 id 找到实体；给月球单位下移动命令后它留在月球、地球单位没被拖走，销毁月球单位也不影响地球的那个；**两跳路线 + 单向性 + 踩门判定 + 旅行后 id 不变 + 两次转移各一条事件 + 出发图少一个、火星多一个**。

## 十五、还没有实现

维度的主干功能——多张图、传送门与旅行、跨维度送货、按维度的快照——都已经实现并进了自校验。**还想加东西的人只需要看剩下的这些**：

| 缺口 | 现状 | 想做的话，大致要动什么 |
| --- | --- | --- |
| 环境危害真的掉血 | `hazardDamagePerTick` / `gravity` / `breathable` / `requiresSealedUnits()` **没有任何代码读取**；`DimensionProfile` 唯一被读的分量是 `movementSpeedFactor` | 写一个按 `dimensionId()` 分组的系统，按 tick 调 `Health.damage(...)`；"密闭单位"需要一个内容侧标记 |
| 按维度的寻路预算 | `PathService` 的请求数 / 节点预算仍然是**全世界**一份 | 想按维度限流就在 `PathService` 里先按 `dimensionId()` 分桶 |
| 维度进存档 | 没有整体存档格式；`ChunkStore` 只覆盖地形，且每个维度是独立实例，门列表与维度注册表没人序列化 | 存档格式自己带上维度键、各自的地形 store 与 `world.gateways()` |
| 走路单位的跨图寻路 | 不存在，也不打算有：跨图的货用 `tickShipped` 的定时发运，单位跨图用 `travel` 走门 | 需要"多图流场"才谈得上；目前刻意不做 |

注意"搬运工跨图"**不是缺口而是设计**：一张图上的单位不会被派去另一张图取货，跨图的货走门（第十一节）。同理，`travel` 也不会替你把单位瞬移过去——没有门就是 `FAILED`。

## 常见坑

1. **拿 `world.spatial()` / `world.map()` / `world.pathFinder()` 处理非默认维度的实体**。它们只指向默认维度，邻居与地形都会答错。用 `spatial(unit.dimensionId())` 这一族。
2. **以为 `world.units()` 是一张图**。多维度下它是所有维度的和；只有一个维度时它才是那张图的活列表（旧代码因此没坏）。要一张图就用 `world.units(id)`。
3. **以为 `ChunkStreamer` 的预算会按维度翻倍**。`setChunksPerTick(n)` 的 `n` 是**总数**：三张图各分到 `n / 3`，余数给先注册的。
4. **给每个维度传了同一个 `ChunkStore`**。`ChunkStore` 只按 `ChunkPos` 索引，共享会让几张图在同一个区块坐标上互相覆盖地形。要么让 builder 各发一份，要么自己保证键里带维度。
5. **在实体未登记时调用 `entity.dimensionId()`**。构造出来但还没 `register`/`spawn` 的实体会抛 `IllegalStateException`；需要"可能为空"就用 `dimensionIfAny()`，`World.destroy(...)` 内部就是这么写的。
6. **订阅了 `Events.ChunkLoaded` 却不看维度**。多维度下三张图的区块会混在一起；记得用 `event.dimension()`。
7. **`setMap(...)` 只改默认维度，而且只能在世界还空着的时候用**。默认维度已经有实体时会抛 `IllegalStateException`——要换图请注册一个新维度。
8. **`DimensionId` 不接受大写与冒号**。`DimensionId.of("Moon")`、`DimensionId.of("oc:moon")` 都会抛 `IllegalArgumentException`；键要么小写、要么带 `_`/`-`/`/`。
9. **别自己调 `setDimension(...)`**。登记一个新维度靠 `Dimension.register(...)`（它会同时更新集合、索引并发布 `EntitySpawned`）；只手写 `setDimension` 会让实体"名义上在月球、实际还在旧图"。
10. **门是单向的**。`Gateway.of(overworld, a, moon, b)` 只把人从地球送到月球；回程要么 `lift.reversed()`，要么另写一条。忘了反向门的表现是"去得了、回不来"，而 `route(mars, overworld)` 会返回 `Optional.empty()`、`travel` 直接 `FAILED`。
11. **`travel` 会替换订单队列**。`CommandService.travel(...)` 走的是 `orders().replace(...)`，所以正在进行的搬运/移动会被顶掉；反过来，一次长达两分钟的旅行也会占住这个单位。
12. **`TransferOrder` 的超时是"整段旅程"的**。`TIMEOUT_TICKS = 20 * 120`（2400 tick）从订单开始算，不是每跳重算；多跳长途要留够时间，否则会看到"走到一半被判失败"。
13. **`transfer` 不是"销毁 + 生成"**。它保留 id / 血量 / 背包 / 订单，并发布 `EntityTransferred`。用 `EntitySpawned` + `EntityDestroyed` 去推断"单位换图了"会把旅行当成死亡（见第十三节）。
14. **别把跨图的搬运 job 交给走路单位**。`findHauler` 已经只挑本图单位；你自己写派单逻辑时也要按 `dimensionId()` 过滤，否则单位会被派去一组"看起来很近、其实在另一张图"的坐标。

## 确定性提醒

- **不要用 `HashMap`/`HashSet` 装维度或门**。引擎用 `LinkedHashMap`/`List`：`world.dimensions()`、`world.gateways()` 与 `ChunkStreamer` 的预算余数都按**注册顺序**；自己另存一份顺序不同的集合，就会得到不同的资源分配、不同的 `route` 结果与不同的遍历顺序。
- **维度 id 的排序是字典序**（`DimensionId.compareTo`），`overworld`、`moon`、`mars` 按字典序是 `mars < moon < overworld`，而**创建顺序**是注册顺序——要区分这两件事：遍历实体用创建顺序，输出/排序用 `compareTo`。
- **`route` 是"注册顺序上的 BFS"**：同一张图若有多条等价路线，先注册的门先被考虑。改门的注册顺序会改路线，可能改到哪一跳——这是可复现的，但不是"最短跳数优先"以外的排序保证（BFS 保证跳数最少，同跳数之间按注册顺序）。
- **每张图的生成器种子是纯函数**（`seedFor(id)` 只依赖世界种子与维度键），所以"哪些维度、什么顺序注册"决定了地形，也决定了回放结果。让注册顺序在两次运行之间保持一致。
- **不要在系统里缓存"实体 → 维度"的映射**。`flush()` 之后实体可能已经消失；跨 tick 的缓存要像 `WarehouseLogistics` 那样订阅 `Events.EntitySpawned` / `Events.EntityDestroyed`（跟随旅行的视图再补一个 `Events.EntityTransferred`）维护。

## 相关文档

- [03-map-and-pathfinding.md](03-map-and-pathfinding.md) —— 区块、生成器、`ChunkStore`、A* 与 `PathService`（每个维度各一套）
- [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) —— 相位、事件总线、实体生命周期与确定性规则
- [04-units-orders-and-movement.md](04-units-orders-and-movement.md) —— 移动系统怎么消费路径与 `Motion`，以及 `UnitDirector` 的命令入口
- [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md) —— 生产与物流：`HaulingSystem` 的两种模式与跨维度发运的完整状态机
- [08-snapshots-and-rendering.md](08-snapshots-and-rendering.md) —— 按维度的快照、`WorldSnapshot.dimension()` 与渲染接入
- [11-limits-and-faq.md](11-limits-and-faq.md) —— 未实现清单与按症状排查
- [appendix-api-map.md](appendix-api-map.md) —— `Gateway` / `TransferOrder` / `EntityTransferred` 与 `World`/`SnapshotFactory`/`OcRtsEngine` 新签名的全量清单

## 相关源码

- [DimensionId.java](../src/main/java/io/github/sob1234509876/oc/rts/dimension/DimensionId.java)、[DimensionProfile.java](../src/main/java/io/github/sob1234509876/oc/rts/dimension/DimensionProfile.java)、[Dimension.java](../src/main/java/io/github/sob1234509876/oc/rts/dimension/Dimension.java)、[Gateway.java](../src/main/java/io/github/sob1234509876/oc/rts/dimension/Gateway.java)
- [World.java](../src/main/java/io/github/sob1234509876/oc/rts/World.java)、[Entity.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Entity.java)
- [OcRtsEngineBuilder.java](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngineBuilder.java)、[OcRtsEngine.java](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngine.java)、[module-info.java](../src/main/java/module-info.java)
- [TransferOrder.java](../src/main/java/io/github/sob1234509876/oc/rts/order/TransferOrder.java)、[UnitDirector.java](../src/main/java/io/github/sob1234509876/oc/rts/order/UnitDirector.java)、[CommandService.java](../src/main/java/io/github/sob1234509876/oc/rts/order/CommandService.java)
- [ChunkStreamer.java](../src/main/java/io/github/sob1234509876/oc/rts/map/ChunkStreamer.java)、[Events.java](../src/main/java/io/github/sob1234509876/oc/rts/event/Events.java)
- [HaulingSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/logistics/HaulingSystem.java)、[SnapshotFactory.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/SnapshotFactory.java)、[WorldSnapshot.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/WorldSnapshot.java)
- [PathService.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/PathService.java)、[MovementSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/MovementSystem.java)、[ProductionSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/production/ProductionSystem.java)
- [RtsScenario.java](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java)（第 11 节的可运行证据）
