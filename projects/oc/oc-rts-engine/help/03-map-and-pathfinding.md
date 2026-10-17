# 区块地图与寻路

> 世界按 16×16 区块惰性生成、按焦点流式加载，内存里只留焦点周围那一片；寻路在 A* 里按 `TerrainType.moveCost()` 付代价，并把结果当作航点写回单位的 `Motion`。

## 这一篇讲什么

这一篇面向要实现"无限地图 + 大量单位寻路"的人，或者要接一层磁盘存档、一台专用服务器调度的人。
内容按数据流自下而上：`Chunk` / `TerrainMap` 的读取语义 → 生成器（含 `NoiseSettings.defaults()` 的全部实测默认值与"原点混合"保证）→ `ChunkStore` 持久化接缝 → `ChunkedMap` 的惰性生成与驱逐 → `ChunkStreamer` 的每 tick 预算 → `Path` / `PathFinder` / `AStarPathFinder` → `PathService` 的请求队列与相位。
物品与目录见 [02-content-and-items.md](02-content-and-items.md)；编队、命令与移动见 [04-units-orders-and-movement.md](04-units-orders-and-movement.md)。

## 1. `TerrainMap` 与 `Chunk`

<!-- 示意：接口总览，方法体省略，签名与源码一致 -->
```java
public interface TerrainMap {
    int chunkSize();
    TerrainType terrainAt(int x, int y);                 // 可能触发生成
    void setTerrain(GridPos tile, TerrainType terrain);
    boolean isChunkLoaded(ChunkPos chunk);
    Set<ChunkPos> loadedChunks();
    Optional<TileRect> loadedBounds();                   // 空 map 返回 Optional.empty()
    default TerrainType terrainAt(GridPos tile) { ... }
    default boolean isPassable(GridPos tile) { ... }
    default boolean isPassable(int x, int y) { ... }
    default boolean isBuildable(GridPos tile) { ... }    // 只有 GridPos 重载
    default float moveCost(GridPos tile) { ... }
    default int loadedChunkCount() { return loadedChunks().size(); }
}
```

语义要点：

- **查询会自动生成/加载**。接口注释写得很直白："每一次查询都可能触发生成或加载一个区块"。`terrainAt(9999, -9999)` 会凭空造出一块地形并留在内存里，直到流式系统把它卸掉。实测（半径 3、`chunkSize = 16`）：`tick(12)` 后内存里 48 个区块，查询远方一格后变 49；下一 tick 那个远方区块已经不在内存里了（同时流式系统补上了本该加载的第 49 块），计数回到 49。
- `isBuildable` = `passable && buildable`，`isPassable` 只看 `TerrainType.passable()`，`moveCost` 是"进入这一格"的相对代价（1 是开阔地）。

`Chunk` 把地形放在一个扁平数组里（行主序 `localY * size + localX`），区块一存在，每格就已经实体化了，所以之后的查询是一次数组访问：

| 成员 | 说明 |
| --- | --- |
| `Chunk(ChunkPos, int size)` | 全 null 的区块，只有 store 和 generator 用；交给 map 前必须填满 |
| `Chunk.filled(ChunkPos, int size, TerrainType)` | 整块同一种地形 |
| `terrainAt(int localX, int localY)` | 本地坐标，越界抛 `IndexOutOfBoundsException`（故意不 clamp） |
| `terrainAt(GridPos)` / `contains(GridPos)` | 世界坐标；用 `Math.floorMod` / `Math.floorDiv`，负数坐标也成立 |
| `setTerrain(...)` / `fill(...)` | 同时把 `dirty` 置位 |
| `isDirty()` / `markClean()` / `markDirty()` | 脏标记：编辑只在内存里，落盘由 store 决定 |
| `rawTiles()` / `count(TerrainType)` | 底层数组（不拷贝，批量消费者用）/ 统计某种地形的格数 |

## 2. `ChunkGenerator`：坐标的纯函数

```java
public interface ChunkGenerator {
    Chunk generate(ChunkPos pos);
    int chunkSize();
}
```

契约是"同坐标必须产出同地形"：生成器不允许使用顺序随机流，随机性必须由坐标 hash 出来，否则区块被驱逐后重新生成就会变样，回放与存档立刻失真。

`FlatChunkGenerator(TerrainType)`（可再加 `chunkSize`）产出整块同一种地形，`DEFAULT_CHUNK_SIZE = 16`；自校验场景用它把地形变量从移动测试里剔掉。

`NoiseChunkGenerator` 是默认生成器（`OcRtsEngineBuilder` 未指定 `generator(...)` 时用 `new NoiseChunkGenerator(seed, content)`）。构造器有三档：

```java
new NoiseChunkGenerator(long seed, ContentRegistry content);                          // defaults + 16
new NoiseChunkGenerator(long seed, ContentRegistry content, NoiseSettings settings);  // settings + 16
new NoiseChunkGenerator(long seed, ContentRegistry content, NoiseSettings settings, int chunkSize);
```

### 2.1 `NoiseSettings.defaults()` 的实测数值

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `frequency` | `1f / 32f`（0.03125） | 高程场基础频率，越小大陆越大 |
| `octaves` | `3` | 叠加的噪声层数 |
| `lacunarity` | `2f` | 层间频率倍率 |
| `gain` | `0.5f` | 层间振幅倍率 |
| `seaThreshold` | `0.38f` | 低于它是水 |
| `sandThreshold` | `0.42f` | 海面之上、它之下是沙滩 |
| `forestThreshold` | `0.52f` | 湿度高于它，陆地变森林 |
| `hillThreshold` | `0.56f` | 高程高于它变丘陵 |
| `rockThreshold` | `0.74f` | 高程高于它变岩石 |
| `moistureFrequency` | `1f / 24f`（≈0.041667） | 湿度场频率 |
| `originElevation` | `0.44f`（`DEFAULT_ORIGIN_ELEVATION`） | 原点被混合到的高程 |
| `originRadius` | `36f`（`DEFAULT_ORIGIN_RADIUS`） | 混合完全消退的距离（格） |
| 内容路径 | `oc:water`、`oc:sand`、`oc:grass`、`oc:forest`、`oc:hill`、`oc:rock` | 都是 `ResourceKey`，可用 `withPaths(...)` 整体替换 |
| `DEFAULT_CHUNK_SIZE` | `16` | `defaults()` 假定的区块边长 |
| `amplitudeSum()` | `1.75`（实测） | 用于把叠加后的场归一化到 `[0,1]` |

记录构造器会校验：`frequency > 0`、`octaves >= 1`、`lacunarity > 1`、`0 < gain < 1`、`moistureFrequency > 0`、`originRadius > 0`、四个高程阈值严格递增、`originElevation` 必须**高于海平面且低于岩石线**。`withSeaThreshold(...)` / `withOrigin(elevation, radius)` / `withPaths(...)` 提供拷贝式修改。

### 2.2 原点混合：出生地为什么可建造

高程是 `octaves` 层双线性值噪声之和除以 `amplitudeSum`，再向 `originElevation` 做一次**混合**（不是加偏移）：

```java
// 源码中的形状，与 NoiseChunkGenerator.elevation() 一致
float raw   = sum / amplitudeSum;
float blend = originBlend(x, y);                        // 原点是 1，originRadius 之外是 0
return raw + (originElevation - raw) * blend;
```

`originBlend(x, y)` 在 `x² + y² >= originRadius²` 时返回 0，否则返回 `(1 - t)²`，其中 `t = (x² + y²) / originRadius²`。加偏移没法保证"在岸上"（本来就低于海面的噪声加一点还是低于海面），混合才能把原点钉在指定高度上。实测（默认设置，`d` 为 +x 方向距离）：

| `d` | `originBlendAt(d, 0)` | `elevation(d, 0)` | `terrainFor(d, 0)` |
| --- | --- | --- | --- |
| 0 | 1.0 | 0.44 | grass |
| 9 | 0.879 | 0.448 | grass |
| 12 | 0.790 | 0.461 | grass |
| 18 | 0.5625 | 0.519 | grass |
| 24 | 0.309 | 0.652 | hill |
| 36 | 0.0 | 0.745 | rock |
| 48 | 0.0 | 0.549 | grass |

同一套默认值下实测：`guaranteedLandRadius() == 9`，`landFraction(9) == 1.0`、`landFraction(16) == 1.0`、`landFraction(32) == 0.918`、`landFraction(64) == 0.868`、`landFraction(128) == 0.831`。

几个对外方法各有用处：

- `terrainFor(int x, int y)` / `isLand(int x, int y)`：一个坐标的地形（纯查询，给预览工具与测试用）/ 该格是否在海平面之上；
- `originBlendAt(int x, int y)`：混合强度，用于诊断"出生地是否够平"与调参；
- `guaranteedLandRadius()`：**保证是陆地**的半径，按最坏情况（原始高程为 0）反解 `origin * fade >= seaThreshold`，即 `radius * sqrt(1 - sqrt(seaThreshold/origin))`，再向下取整、且不超过 `originRadius`。默认设置下是 9 格；
- `landFraction(int radius)`：原点周围正方形内"陆地"占比，采样每格，半径不能为负。

注意 `landFraction` 统计的是**陆地**（高于海平面），不是"可建造"；出生地是否可建造由构造器里的那次检查保证：生成 `terrainFor(0, 0)`，若它不 `passable()` 或不可 `buildable()` 就直接抛 `IllegalStateException`，把"目录里没有可建造地形"这种错误提前到建引擎的时候。

地形解析规则（源码 `resolveRock` / `passableOr` / `firstNonNull`）：

- `waterPath` / `sandPath` / `forestPath` / `hillPath`：目录里找不到就退化成 `fallbackTerrain()` / grass，**不会报错**；
- `grassPath`：若声明的草地不可通行，退回 `fallbackTerrain()`（保证出生地能站人）；
- `rockPath`：为 `null`、未定义、或声明的岩石**可通行**时，一律视为"没有岩石"——高地保持可通行，而不是用 void 把地图糊成墙。

## 3. `ChunkStore`：持久化接缝

```java
public interface ChunkStore {
    Optional<Chunk> load(ChunkPos pos);
    void save(Chunk chunk);        // 契约包含"标记为已保存"：InMemoryChunkStore 会调 chunk.markClean()
    boolean exists(ChunkPos pos);
}
```

`ChunkedMap` 取区块的顺序是 **store 优先、生成器兜底**：store 里可能存着生成器不知道的地形编辑（修路、砍林）。默认实现 `InMemoryChunkStore` 就是一个 `HashMap<ChunkPos, Chunk>`，另外提供 `size()` / `clear()`；它把 `Chunk` 原样存原样给（不拷贝），所以调用方想独占请自己复制砖块。换成磁盘存档只需要实现这三个方法并把实例交给 `OcRtsEngineBuilder.chunkStore(store)`；`chunk.pos()`、`chunk.size()`、`chunk.rawTiles()` 足以完整还原一块地形。完整的 `FileChunkStore` 配方见 [10-cookbook.md](10-cookbook.md#11-把区块存到磁盘)，这里只强调一条容易被漏掉的契约：

<!-- 示意：非编译验证片段，只展示 save 的义务 -->
```java
@Override
public void save(Chunk chunk) {
    write(fileOf(chunk.pos()), chunk.rawTiles(), chunk.size());
    chunk.markClean();   // store 的义务：不标记，区块永远是脏的，每次卸载/刷盘都会重写
}
```

## 4. `ChunkedMap`：惰性生成 + 按焦点驱逐

```java
new ChunkedMap(ChunkGenerator generator, ChunkStore store, int chunkSize);
new ChunkedMap(ChunkGenerator generator);   // 用 InMemoryChunkStore，区块边长取 generator.chunkSize()
```

它同时实现 `TerrainMap` 与 `MapStreaming`：

| 方法 | 说明 |
| --- | --- |
| `chunk(ChunkPos)` / `peekChunk(ChunkPos)` | 取区块（内存里没有就 store → generator，只有真走了生成器才给 `generatedChunkCount()` 加一）/ 只看内存，不生成不加载 |
| `chunks()` / `loadedChunks()` / `loadedOrder()` | 内存区块的副本 / 位置集合 / 插入顺序 |
| `unload(ChunkPos)` | 从内存移除；脏区块先 `store.save(...)` |
| `flushDirty()` | 把所有脏区块交给 store（靠 store 调 `markClean()`） |
| `unloadAll()` | 逐个卸载，用于场景结束 |
| `setMaxLoadedChunks(int)` / `maxLoadedChunks()` | 上限；`<= 0` 表示不限（**引擎默认就是 0**） |
| `focus(Vec2, int radiusChunks)` / `focus()` / `radiusChunks()` / `loadArea(...)` | 焦点与半径 / 无视预算立刻生成整片区域 |
| `loadedBounds()` | 所有已加载区块覆盖的瓦片矩形（闭区间），空时 `Optional.empty()` |
| `generatedChunkCount()` | 自 map 创建以来生成器真正产出的区块数（load 到的不计） |

超过上限时驱逐"离焦点最远"的区块（切比雪夫距离，距离相同则取 `ChunkPos.compareTo` 更小者，即 **y 优先、再比 x**），所以驱逐顺序与 map 的迭代顺序无关。默认上限为 0（不限），引擎靠 `ChunkStreamer` 定期卸载，而不是靠这个上限。

实测（`seed = 20240202`，`chunkSize = 16`，`viewRadiusChunks = 3`）：`tick(32)` 后 `loadedChunkCount() == 49`（7×7），`generatedChunkCount() == 49`，`loadedBounds() == [-48..63 x -48..63]`。

## 5. `ChunkStreamer`：相位 100、每 tick 预算

```java
public static final int ORDER = 100;                      // 管线相位
public static final int DEFAULT_CHUNKS_PER_TICK = 4;      // 默认每 tick 的区块转换次数
new ChunkStreamer(ChunkedMap map, int radiusChunks);
setChunksPerTick(int);   // 必须为正；loadNow(World, Tick) 无视预算一次装满
```

每个 tick 它做两件事，且**先卸载后加载**（卸载便宜，正好腾出内存）：把切比雪夫距离大于半径的区块卸掉，再从焦点区方形范围里补齐缺失的区块；两者共用同一个预算。加载与卸载分别发布 `Events.ChunkLoaded(ChunkPos pos, long tick)` 与 `Events.ChunkUnloaded(ChunkPos pos, long tick)`。

```java
engine.events().subscribe(Events.ChunkLoaded.class, event ->
        System.out.println("+" + event.pos() + " @tick " + event.tick()));
engine.events().subscribe(Events.ChunkUnloaded.class, event ->
        System.out.println("-" + event.pos() + " @tick " + event.tick()));
engine.focus(new GridPos(4000, 4000));   // 只改焦点；下一个 tick 起开始搬家
engine.tick(40);                          // 半径 3、预算 4 → 大约 25 个 tick 装满 7×7
```

实测（半径 3 = 49 个区块，`setChunksPerTick` 默认 4，焦点从原点跳到 (4000, 4000)）每 tick 之后的内存区块数是 `45 41 37 33 29 25 21 17 13 9 5 1 3 7`：前 12 个 tick 全在卸载（每 tick 恰好 4 个），第 13 个 tick 卸掉最后一块的同时开始加载，之后每 tick 净增 4 块，**25 个 tick** 装满 7×7。卸载顺序是 `ChunkPos` 的自然序（`dropping.sort(null)`，y 优先）。

## 6. `Path` / `PathFinder` / `PathOptions`

```java
Path route = ...;
route.start();  route.goal();  route.waypointCount();  route.waypoint(int);
route.waypoints();        // List<Vec2>，全是"瓦片中心"，不含起点
route.destination();      // 最后一个航点；没有航点时是 goal.center()
route.isEmpty();  route.length();   // 起点 == 终点 / 从 start.center() 沿航点累加（格）
```

```java
public interface PathFinder {
    default Optional<Path> findPath(GridPos start, GridPos goal);                  // 用 PathOptions.DEFAULT
    Optional<Path> findPath(GridPos start, GridPos goal, PathOptions options);
    boolean hasLineOfSight(Vec2 from, Vec2 to);
    default boolean isReachable(GridPos start, GridPos goal);                       // 用 PathOptions.FAST
}
```

`PathOptions` 是 record，只有四个分量：

| 常量 | `maxExpansions` | `allowDiagonal` | `allowCornerCutting` | `heuristicWeight` |
| --- | --- | --- | --- | --- |
| `PathOptions.DEFAULT` | `20_000` | `true` | `false` | `1.05f` |
| `PathOptions.EXHAUSTIVE` | `Integer.MAX_VALUE` | `true` | `false` | `1.0f` |
| `PathOptions.FAST` | `2_000` | `true` | `false` | `1.5f` |

构造时校验 `maxExpansions > 0` 且 `heuristicWeight >= 1`；`withMaxExpansions(int)` 与 `withDiagonals(boolean)` 返回拷贝。预算语义：搜索超出 `maxExpansions` 就**放弃并返回 `Optional.empty()`**（不会走半截），调用方之后可以重试或退化成直线走。`EXHAUSTIVE` 的注释写着"只给离线工具与测试用"——它每次搜索都会按 `MAX_NODES` 预分配堆数组（见下），别放进每 tick 的热路径。

## 7. `AStarPathFinder`

```java
new AStarPathFinder(TerrainMap map);                        // 启发式尺度：采样已加载区块
new AStarPathFinder(TerrainMap map, float minimumStep);     // 启发式尺度由调用方给出；0 表示"采样"
AStarPathFinder.minimumMoveCost(ContentRegistry content);   // 目录里最便宜的可通行步进
finder.findPath(start, goal, PathOptions.DEFAULT);
finder.hasLineOfSight(Vec2 from, Vec2 to);   // 接口方法
finder.hasLineOfSight(GridPos from, GridPos to); // 实现类上的重载
```

- **启发式尺度**：八向距离（对角 `√2`、直走 1）乘 `cheapestStep` 再乘 `heuristicWeight`。`cheapestStep` 用构造时给的 `minimumStep`（`> 0` 时直接采用）；单参构造器传 0，于是退化成"采样已加载区块里出现过的最低 `moveCost`"，那份采样会随流式历史变化。引擎走的是确定性的那条路：

```java
// OcRtsEngineBuilder.build()
AStarPathFinder pathFinder = new AStarPathFinder(map, AStarPathFinder.minimumMoveCost(content));
```

  `minimumMoveCost(ContentRegistry)` 遍历目录里所有 `passable() && moveCost() > 0` 的地形取最小值（本仓库自带目录实测为 `1.0`），与"内存里恰好加载了哪些区块"无关。
- **八向搜索**：`allowDiagonal` 为 true 时走对角，对角一步的代价是 `terrain.moveCost() * sqrt(2)`（`SQRT_TWO ≈ 1.41421`），直走就是 `moveCost()`（`moveCost <= 0` 时按 0.0001 处理）；**禁止切角**是默认行为，对角扩展前要求两个正交邻格都可通行。
- **不可通行的目标会被替换成最近可站格**：`bestNeighbor(goal)` 在八个邻居里取评分最小者（`chebyshev(候选, 目标) * cheapest + moveCost * 0.5`），所以"点进水里"会走到岸边；如果目标被彻底围死，返回空。
- **起点不可通行直接失败**；起点等于目标返回一个**没有航点**的 `Path`（`isEmpty() == true`，`destination()` 是目标格中心）。
- **航点拉直**：先重建瓦片路径，再用 **supercover DDA** 视线检查跳过不必要的拐点（线段**经过**的每一格都要可通行，不只命中格心的那些，否则贴着湖角拉直的路线看着是直线却走不过去）；生成航点时丢掉起点格（单位已经站在那儿），并把重建出来的最后一格只加一次——目标不可通行时它正是那个"最近可站格"，不会出现零长度的重复末段。
- **过期堆记录是设计的一部分**：开放集允许同一格存在多条记录。改进一个节点时会同步记录这次的代价（`node.queuedG = tentative`）再重新入堆，弹出时若"记录时的代价"与当前最优不一致就跳过这条记录，因此既不用在堆里查找移动，也不会把被改进过的节点（包括目标格）漏掉。
- **确定性**：节点比较先比 `f`，再比启发值，再比 y，最后比 x。所以同样的起点/终点/选项永远得到同一条路径，即使 map 的迭代顺序或哈希布局变了也一样；启发式尺度固定（由目录决定）进一步保证了这一点。
- **规模上限**：`MAX_NODES = 1 << 20`；堆数组长度是 `min(maxExpansions * 4 + 16, MAX_NODES)`，`EXHAUSTIVE` 时每次搜索约 4 MiB（`int[1048576]`）。

```java
PathFinder finder = engine.pathFinder();
Optional<Path> path = finder.findPath(new GridPos(0, 0), new GridPos(30, 20), PathOptions.DEFAULT);
path.ifPresent(route -> System.out.println(route));   // Path{(0, 0) -> (30, 20), 1 waypoints, 34.7 tiles}
finder.hasLineOfSight(Vec2.of(0.5f, 0.5f), Vec2.of(4.5f, 0.5f));   // true
```

上面那条路径终点落在 `(30, 20)` 的岸边而不是 `(30, 20)` 本身（该格不可通行），所以只有 1 个航点——正是"替换成最近可站格 + 只加一次末格"的表现。

实测可达性（同一片 90×90 噪声区，BFS 用完全相同的八向、禁止切角规则判定"真的可达"）：seed 1 的 411 对、seed 7 的 247 对、seed 20240202 的 370 对、seed 99 的 284 对可达格对，A*（`DEFAULT`）**一对都没有漏判**。

## 8. `PathService`：把搜索摊到 tick 上

```java
public static final int DEFAULT_MAX_REQUESTS_PER_TICK = 32;
public static final int DEFAULT_NODE_BUDGET_PER_TICK = 200_000;
public static final int MAX_EXPANSIONS_PER_SEARCH = PathOptions.DEFAULT.maxExpansions(); // 20000
public static final int FAILED_REPATH_COOLDOWN = 30;   // 失败后多少 tick 再试
public static final int GOAL_ADJUST_RING = 8;          // 目标不可通行时向外找可站格的最大环数
```

- 相位是 `order() == 300`，在移动系统（400）之前跑，这样移动系统本 tick 就能用上新航点。
- 队列是 `LinkedHashMap<EntityId, Request>`，**每个单位只保留最新请求**（重新下单会把它挪到队尾，不会因为反复点地饿死别人）；每个 tick 依次处理：单位已死 → 清掉 pending 标志跳过；`motion.goalOrNull()` 与请求目标不一致（搜索期间又被改单）→ 丢弃结果；否则算一次搜索。
- 预算：每次搜索先取 `allowance = min(剩余节点预算, MAX_EXPANSIONS_PER_SEARCH)` 并**从预算里扣掉**（`AStarPathFinder` 不汇报真实扩展数，所以这是上界），再用 `options.withMaxExpansions(allowance)` 搜索。
- 结果写回 `Motion`：有航点就 `setPath(waypoints)`（顺带清掉 pending 标志）；否则 `setPath(List.of())` + `requestRepath(FAILED_REPATH_COOLDOWN)`，`failedLastTick` 加一——**找不到路不等于停下**，单位会朝目标直线走并在 30 tick 后重试。
- 目标在水里时先经 `walkableTarget(map, goal)`：目标格可通行就直接用，否则按环（1..8）找最近可通行格。

实测默认预算下的吞吐：30 个单位同时下单，`processedLastTick()` 稳定为 **10**（`200000 / 20000`），`spentNodesLastTick()` 恰好 200000，`pendingRequests()` 每 tick 减少 10。请求是由移动系统在同 tick 的 400 相位排进去的，所以下单的那一 tick 服务数为 0，从下一 tick 开始才每 tick 10 个。也就是说 `DEFAULT_MAX_REQUESTS_PER_TICK = 32` 在默认节点预算下**根本用不满**；提高吞吐要同时看 `pathBudget(requests, nodes)`。

```java
engine.pathService().request(unit, new GridPos(40, 25).center());   // 只有 Vec2 重载
engine.pathService().request(unit, new GridPos(40, 25).center(), PathOptions.FAST);
engine.pathService().pendingRequests();     // 还有多少请求在排队
engine.pathService().isPending(unit, goal); // 队里是否还有一个"正好去这个目标"的请求
engine.pathService().processedLastTick();   // 上一 tick 服务了几次
engine.pathService().failedLastTick();      // 上一 tick 有几次没找到路
engine.pathService().spentNodesLastTick();  // 上一 tick 给出的节点额度总和（上界）
```

## 关键 API

| 类型/方法 | 作用 |
| --- | --- |
| `TerrainMap.terrainAt/isPassable/isBuildable/moveCost` | 逐格查询（默认方法全部可能触发生成） |
| `TerrainMap.loadedChunks/loadedBounds/loadedChunkCount/isChunkLoaded` | 内存区块视图 |
| `Chunk.filled/terrainAt/setTerrain/fill/rawTiles/count` / `isDirty/markClean/markDirty` | 一块地形砖的读写与脏标记 |
| `new FlatChunkGenerator(TerrainType[, int])` / `new NoiseChunkGenerator(seed, content[, settings[, chunkSize]])` | 单地形世界 / 默认噪声世界 |
| `NoiseSettings.defaults([namespace])` / `withOrigin` / `withSeaThreshold` / `withPaths` | 生成参数与拷贝式修改 |
| `NoiseChunkGenerator.terrainFor/isLand/elevation/moisture/originBlendAt/guaranteedLandRadius/landFraction/resolvedPaths` | 逐格诊断与出生地保证 |
| `ChunkStore.load/save/exists` + `InMemoryChunkStore` | 持久化接缝（`save` 要负责 `markClean()`） |
| `ChunkedMap.chunk/peekChunk/chunks/loadedOrder` / `unload/flushDirty/unloadAll` | 区块访问与卸载落盘 |
| `ChunkedMap.focus/radiusChunks/loadArea` / `setMaxLoadedChunks` / `generatedChunkCount/loadedChunkCount/loadedBounds` | 焦点、整片加载、上限与统计 |
| `new ChunkStreamer(map, radiusChunks)` / `setChunksPerTick` / `loadNow` / `ORDER` | 流式加载（相位 100） |
| `Events.ChunkLoaded/ChunkUnloaded` | 区块进出内存的事件 |
| `Path.start/goal/waypoints/waypoint/destination/isEmpty/length` / `PathFinder.findPath/hasLineOfSight/isReachable` | 路径结果与寻路入口 |
| `PathOptions.DEFAULT/EXHAUSTIVE/FAST` / `withMaxExpansions` / `withDiagonals` | 单次搜索预算与启发权重 |
| `new AStarPathFinder(map[, minimumStep])` / `minimumMoveCost(ContentRegistry)` / `map()` / `hasLineOfSight(GridPos,GridPos)` | 构造器、确定性的启发式尺度、实现类额外接口 |
| `PathService.request/cancel/pendingRequests/isPending` / `processedLastTick/failedLastTick/spentNodesLastTick` / `ORDER` | 请求队列、预算统计与相位（300） |
| `Motion.setPath/setPathPending/goalOrNull/requestRepath` | `PathService` 唯一写入的移动状态 |

## 常见坑

- **任何瓦片查询都可能造区块**：`map.terrainAt(x, y)`、`isPassable(...)`、路径搜索经过的每一格都会生成/加载区块。默认 `maxLoadedChunks` 是 0（不限），所以"离得很远的查询"靠 `ChunkStreamer` 在下一个 tick 收回来（每 tick 只收回 4 个）。
- **卸载只发生在焦点半径之外**，而且每 tick 有预算。焦点跳远之后，内存里的旧区块要过好几个 tick 才清完，期间 `loadedChunkCount()` 会先一路掉到 1 再涨回来（半径 3 实测 12 个 tick 全在卸载）。
- **`focus()` 只是改坐标**，真正搬家发生在这个 tick 的 `ChunkStreamer`（相位 100）：设置焦点后不 `tick()`，内存里什么都不会变；`loadArea(min, max)` 则无视预算立刻生成整片区域，会卡住这个 tick。另外 `ChunkedMap` 的焦点半径与 `ChunkStreamer` 的半径是两份数据（builder 把 `viewRadiusChunks` 同时交给两者），直接调 `map.focus(center, otherRadius)` 会被流式系统按自己的半径卸载掉，要改半径请给 `viewRadiusChunks(...)`。
- **`save` 的契约包含 `markClean()`**：`flushDirty()` 自己不标记，靠 store 标记；自定义 store 忘了这一步，区块会永远是脏的，每次卸载/刷盘都会重写。`InMemoryChunkStore` 也不拷贝区块，存进去的是同一个对象，之后的地形编辑会直接反映在"存档"里；想要快照语义请自己复制。
- **`EXHAUSTIVE` 每次搜索预分配约 4 MiB 堆数组**（`MAX_NODES = 1 << 20`），只适合离线工具与测试；每 tick 的热路径用 `DEFAULT`。
- **`findPath` 返回 `Optional.empty()` 有三种正常原因**：预算用尽（`maxExpansions`）、起点不可通行、目标被彻底围死。把 `empty` 当"这次没算出来"而不是"不可达"（`PathService` 就是这么处理的：清空航点、直线走、30 tick 后重试）；`isReachable` 用 `FAST`（2000 次扩展），长距离答"不可达"也可能只是预算不够。另外，自己改 A* 时别删掉"重新入堆前同步本次代价"那一行——删了就会让被改进过的节点（含目标格）整体被判为过期记录。
- **单参构造器的启发式尺度会"看内存"**：`new AStarPathFinder(map)` 只采样**已加载**区块里出现过的最低 `moveCost`，同一份种子在不同流式历史下可能得到不同的扩展顺序。引擎用的是 `new AStarPathFinder(map, AStarPathFinder.minimumMoveCost(content))`；自己建 finder 时也建议显式传尺度。
- **起点就是终点时没有航点**：返回的 `Path` 是 `isEmpty()`，`destination()` 等于目标格中心，`length()` 为 0；`PathService` 会把它当"没找到路"处理（走直线 + 冷却）。
- **`hasLineOfSight` 的坐标是格**：`Vec2` 重载会各自 `tile()` 取整，别把世界坐标当格号传。

## 相关文档

- [02-content-and-items.md](02-content-and-items.md)：`TerrainType`（`passable/buildable/moveCost`）如何声明，生成器按什么路径取地形，以及 `oc:void` 兜底地形。
- [04-units-orders-and-movement.md](04-units-orders-and-movement.md)：命令、编队与移动系统如何消费 `Motion` 里的航点。
- [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md)：相位 100→800 的管线、事件总线与确定性规则（本篇的 100 / 300 相位就落在那里）。
- [11-limits-and-faq.md](11-limits-and-faq.md)：区块预算、每单位一次寻路、`setMaxLoadedChunks` 等已知取舍与 FAQ。
- [../README.md](../README.md)：模块总览与 `./gradlew :projects:oc:oc-rts-engine:scenario` 的逐节证据（第 1、2 节正是本篇的实测来源）。

## 相关源码

- [TerrainMap.java](../src/main/java/io/github/sob1234509876/oc/rts/map/TerrainMap.java)、[Chunk.java](../src/main/java/io/github/sob1234509876/oc/rts/map/Chunk.java)、[MapStreaming.java](../src/main/java/io/github/sob1234509876/oc/rts/map/MapStreaming.java)
- [ChunkGenerator.java](../src/main/java/io/github/sob1234509876/oc/rts/map/ChunkGenerator.java)、[FlatChunkGenerator.java](../src/main/java/io/github/sob1234509876/oc/rts/map/FlatChunkGenerator.java)
- [NoiseChunkGenerator.java](../src/main/java/io/github/sob1234509876/oc/rts/map/NoiseChunkGenerator.java)、[NoiseSettings.java](../src/main/java/io/github/sob1234509876/oc/rts/map/NoiseSettings.java)
- [ChunkStore.java](../src/main/java/io/github/sob1234509876/oc/rts/map/ChunkStore.java)、[InMemoryChunkStore.java](../src/main/java/io/github/sob1234509876/oc/rts/map/InMemoryChunkStore.java)
- [ChunkedMap.java](../src/main/java/io/github/sob1234509876/oc/rts/map/ChunkedMap.java)、[ChunkStreamer.java](../src/main/java/io/github/sob1234509876/oc/rts/map/ChunkStreamer.java)
- [Path.java](../src/main/java/io/github/sob1234509876/oc/rts/map/Path.java)、[PathFinder.java](../src/main/java/io/github/sob1234509876/oc/rts/map/PathFinder.java)、[PathOptions.java](../src/main/java/io/github/sob1234509876/oc/rts/map/PathOptions.java)、[AStarPathFinder.java](../src/main/java/io/github/sob1234509876/oc/rts/map/AStarPathFinder.java)
- [PathService.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/PathService.java)、[Motion.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/Motion.java)
- [OcRtsEngineBuilder.java](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngineBuilder.java)、[RtsScenario.java](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java)（第 1、2 节的可运行证据）、[ScenarioContent.java](../src/main/java/io/github/sob1234509876/oc/rts/content/ScenarioContent.java)
