# 11 · 限制、取舍与 FAQ

> 这个引擎现在**不能**做什么、哪些字段是预留的、以及按症状排查的清单。

## 这一篇讲什么

- 能力清单：哪些已经实现、哪些完全没有（以及临时替代方案），例如存档、环境危害、尸体演出。
- 已经定义、但模拟逻辑没有真正接通的字段与事件——看到它们不要以为已经生效。
- 数量类型的近似边界：`Natural` 从哪一位开始不再精确、`BEYOND` 是什么、为什么库存会"明明没满却报满"。
- 已知的取舍与硬限制。
- FAQ：24 条「症状 → 原因 → 处理」，都是这个引擎里真实会发生的情况。
- 性能调优的旋钮在哪。

## 数量的精度边界（`Natural`）

物品数量、容量、血量与伤害都是 `Natural`（见 [12-numbers-and-googology.md](12-numbers-and-googology.md)），它**在小数值上精确、在大数值上近似**。下面这几条是你能在运行中观察到的全部边界，也是"数字看起来不对"时最常被怀疑的地方。

| 事实 | 具体表现 | 从哪里来 |
| --- | --- | --- |
| 精确阈值是 **4096 位**（`Natural.EXACT_BIT_LIMIT = 4096`，约 1233 位十进制） | 阈值内用 `BigInteger`，加减乘与比较全部无损；`2^4095` 仍然精确，`2^4096` 变成结构形 | `Natural.java` |
| 超过阈值后是**结构形记号** `base ↑^arrows height` | `10^5000` 打印成 `10^5000`、四则幂塔打印成 `10↑↑10`、五级运算打印成 `10↑↑↑10`，更多箭头只是继续加箭头 | `Natural.arrow(...)` / `NaturalFormat.scientific(...)` |
| 结构形上的加减乘**按设计是近似的**，和浮点在小数端一样 | 给塔加/减一个很小的数，表示不变；乘两个塔得较大的那个；`subtractClamped` 夹在 0 | `Natural.add` / `multiply` / `subtractClamped` |
| `Natural.BEYOND` 是**文档化的顶元素，不是自然数** | 比一切都大，吸收加减乘（`BEYOND - 任何有限值 = BEYOND`，`任何有限值 - BEYOND = 0`）；引擎把 `Inventory.UNLIMITED` 定义成它 | `Natural.BEYOND`、`Inventory.UNLIMITED` |
| **没有除法、没有取余、没有负数** | `Natural` 只提供 `add` / `subtractClamped` / `subtract` / `multiply` / `min` / `max`；要比例就用 `fractionOf(whole)`，它给 `float` | `Natural.java` |
| 比较是**量级序**、`equals` 是**形式序** | 两边都精确时完全精确；否则先按预算（`Natural.COMPARISON_REDUCTION_BUDGET = 32`）把两边各自归约成更规范的值（`2{ω}2`、甚至 `2{ε₀}2` 都会变成精确的 `4`），再比（级别 → 底数 → 高度），级别统一到超运算级别这把尺子上。所以 `10{ω}10`、`10{11}10` 与未归约的 `10{ω}10` 互比都是 `0`，但 `equals` 只对形式相同者为真；排序仍是全序，可以安全用作 `Collections.sort` 的键 | `Natural.compareTo`、`Natural.reducedLevel` |
| 要"有证明的答案"用四值比较 | `Natural.compareMagnitude(other)` 返回 `LESS`/`EQUAL`/`GREATER`（都是证明：退化恒等式、两个精确值、下界引理、**10 底对数距离**（差距超过一个数量级即判，与底数无关：`2↑↑10 > 3↑↑4`）、同底有限级别的单调性与**递归下降**（`10↑↑10 > 10^100000`）、两侧都没归约完时的序数级别比较）或 `UNKNOWN`（没有可用规则）。**`UNKNOWN` 不是 `EQUAL`**：截断只证不等。需要排序时继续用 `compareTo` 的全序兜底 | `ComparisonResult`、`Natural.compareMagnitude` |
| 仍然 `UNKNOWN` 的三类 | ① 两者 `log10` 相差不足一个数量级且没有其他证书；② **深层极限**（走不到后继）对**大**有限级别：`10{ω^ω^ω}10`、`10{ε₀}10` 对 `10{100}10`——实测基本列走 4096 步后级别仍是极限（`ω^ω` 只需 12 步）；③ 记号给不出下一项基本列（如 `ε₀[300]` 超过 `MAX_TOWER_HEIGHT`） | `Natural.compareMagnitude` |
| 超限后继级别对有限级别**已经判得开** | 级别是超限后继（含归约后得到的后继）且高度 ≥ 3 时，值至少是 `10{ω+1}3 = 10{ω}(10{ω}10)`；它展开成 `10{X+1}X` 后是有限级别塔，交给同底有限级别证书即可。于是 `10{ω+1}3`、`10{ω+2}2`、`10{ω+10}2`（以及经归约变成后继的 `ω·2`/`ω²`/`ω^ω`）都大于 `10{100}10` | `Natural.compareTransfiniteFloor` |
| 比较在**巨大序数级别**上会回落 | 把级别归约到后继所需的步数随级别爆炸：`ω` 1 步、`ω²` 2 步、`ω^ω` 12 步，但 `ω^ω^ω` 实测十分钟未完成、`ε₀` 是指数塔级（约 `f_3(n)`）。超出预算就不归约：两侧都没归约完时按序数级别判，其余情况才落到 `UNKNOWN`/兜底倾向 | `Natural.COMPARISON_REDUCTION_BUDGET`、`Ordinal.normaliseAt` |
| 比较结果是**按值缓存**的 | 归约结果与比较级别都是值的纯函数，记在值自身上（懒计算），所以排序大量塔值时每个值只付一次预算，而不是每次比较都付 | `Natural.compareTo`（`comparisonValue`/`comparisonRankCache`） |
| 库的 `selfcheck` 任务有 698 项断言 | `./gradlew :projects:oc:oc-googology:selfcheck`，覆盖阈值边界、提升规则、结构形算术、排序、格式化与解析往返、序数、超限超运算、四值比较与 BMS 标准式、展开与非递归 BMS、BMS 极限之上的抽象序数名册（第 11–20 节） | `GoogologyCheck.java` |

`float` 与 `int` 的领地没有被 `Natural` 取代，而且仍然各自带边界：位置/速度/半径/射程/`EntitySnapshot.healthFraction` 是 `float`，格坐标与一切 tick 时长（`buildTimeTicks`、`Recipe.durationTicks`、冷却、超时）是 `int`/`long`。位置与速度用的是 `float`，而 `Vec2` 只做 `float` 基本算术（这也是"同种子必然复现"的前提之一）：`float` 只有 24 位有效二进制位，**离原点越远分辨率越差**——原点附近相邻可表示值的间隔约 `1.2e-7` 格，到 10 万格处约 `0.0078` 格，到 100 万格处约 `0.0625` 格，此时"精确停在某个坐标"已没有意义（`UnitType.radius()` 通常在 0.35 格上下）。大数解决的是"数量"，不是"坐标"。

## 能力清单：哪些已经实现，哪些没有

| 能力 | 现状 | 你可以怎么做 |
| --- | --- | --- |
| 渲染、窗口、输入、选中 UI | 引擎零图形依赖 | 用[只读快照](08-snapshots-and-rendering.md)自己写渲染层；`AsciiMapRenderer` 是一个完整的消费示例 |
| 战斗 | **已实现**：相位 450 的 `CombatSystem` 自动索敌、扣护甲、结算伤害与死亡；目标是单位**或建筑**，建筑按占地边缘吃射程并走同一条 `World.destroy` | 见 [14-combat.md](14-combat.md)；建筑是否算敌人仍由 `Player.isAlliedWith` 决定 |
| 死亡与尸体 | 战斗会把血量为零的单位立刻标死并在 tick 末移除，**没有“死亡动画播完再消失”的流程** | 自己写系统：订阅 `Events.EntityKilled` → 播 `AnimationClips.DEATH` → 若干 tick 后清理 |
| AI | 没有 | 它本质上就是"按 tick 读快照/世界，然后调 `UnitDirector` 与 `ProductionSystem`"，不需要引擎改动 |
| 战争迷雾 | 没有可见性/探索状态 | 自己按玩家维护一张已探索区块集合，订阅 `Events.ChunkLoaded` 更新 |
| 网络同步 | 没有 | 引擎是确定性的，理论上可以做 lockstep：同步种子 + 每 tick 的命令序列 |
| 完整存档 | 只有区块持久化的接缝（`ChunkStore`） | 实体、订单、生产队列都需要你自己序列化；`ChunkStore` 只覆盖地形 |
| 群体寻路 / 流场 | 每个单位各自跑一次 A*（受 `PathService` 预算约束） | 大队形移动时观察 `pendingRequests()`；预算调大或改成共享路径/流场 |
| 建筑碰撞 | **建筑不阻挡单位移动**：`MovementSystem` 只检查地形是否可通行 | 见下面的 [FAQ 第 13 条](#13-单位会穿过建筑) |
| 维度之间的旅行 | **已经实现**：`Gateway` + `World.route(...)` + `UnitDirector.travel(...)` | 见 [13-dimensions.md](13-dimensions.md)；没有门就是订单失败，不会瞬移 |
| 无界地形 | **已经实现**：`TerrainMap`/`ChunkedMap` 本来就没有宽高；`InfiniteTerrainMap` 再给它一个 LRU 区块预算，任意有符号格坐标都能查询 | `OcRtsEngine.builder(content).infiniteTerrain(seed).maxLoadedChunks(n)`；见 [03](03-map-and-pathfinding.md#4b-infiniteterrainmap无边界--lru-区块预算) |
| 跨维度送货 | **代码就位（"模拟货运"），但生产端的请求链路还没跨图，端到端仍不可用（自校验把这一点记为待办）**：`HaulingSystem` 只挑同维度的搬运工，跨图的货按"直送延迟 × 门链跳数"发运 | 见 [13-dimensions.md](13-dimensions.md) 与 [06](06-economy-logistics-and-production.md)；走路单位永远不会被派去另一张图 |
| 按维度的快照 | **已经实现**：`SnapshotFactory.capture(DimensionId, ...)`、`WorldSnapshot.dimension()` / `dimensionName()`、图例带维度 | 见 [08-snapshots-and-rendering.md](08-snapshots-and-rendering.md) |
| 环境危害（不可呼吸、重力、辐射） | **没有**：`hazardDamagePerTick` / `gravity` / `breathable` / `requiresSealedUnits()` 无人读取 | 自己写一个按 `dimensionId()` 分组的危害系统；见 [13-dimensions.md](13-dimensions.md) |
| 维度进存档 | **没有**：没有整体存档格式，`ChunkStore` 只覆盖地形，门列表与维度注册表没人序列化 | 存档格式自己带上维度键、各维度的地形 store 与 `world.gateways()` |
| 单位体积碰撞的严格解 | 用的是分离力（软避让），不是物理求解 | 调 `SteeringSettings`；需要硬碰撞就自己写一个系统 |
| 多线程 | 单线程，没有同步 | 渲染可以用快照在别的线程画，模拟本身不行 |

## 预留但未接通的字段

这些名字出现在 API 里，但引擎的模拟逻辑没有真正让它们生效（有的只在表现层生效，有的完全没有调用方）：看到它们别以为已经生效。

| 类型 / 成员 | 状态 |
| --- | --- |
| `TerrainMap.setTerrain(...)` | 已实现（`ChunkedMap` 里真的会改地形并标脏），但**没有游戏逻辑调用**它——改地形/修路/炸桥都得你自己调 |
| `DimensionProfile.hazardDamagePerTick` / `gravity` / `breathable` | 只是数据：引擎目前只读 `movementSpeedFactor()`（见 [13-dimensions.md](13-dimensions.md)），危害**不会真的掉血** |
| `DimensionProfile.requiresSealedUnits()` | 已实现（`!breathable || hazardous()`），但**没有调用方**——"没穿宇航服就死"要自己写系统 |
| `Direction` / `AnimationSet` 的方向变体 | 引擎会设置朝向，方向变体的选择发生在 `UnitAnimator` 里（需要你在动画集里提供对应方向） |

## 已知取舍与硬限制

1. **出生地地形单调**。`NoiseSettings` 会把原点附近混合到 `originElevation`，保证一开局有可建造的陆地（默认半径 36 格内逐渐过渡）。代价是出生点周围相对平坦；把 `originRadius` 调小会更"野"，但可建造区也变小。
2. **区块流式有每 tick 预算，而且是所有维度共享的**。`ChunkStreamer` 默认每 tick 最多 4 次区块进出，而且**卸载与加载都算一次**：焦点从原点跳到另一处（半径 4）要卸掉 81 个再装上 81 个，约 41 tick 才能铺满；半径 2 实测是 25 卸 + 25 载 ≈ 13 tick。想更快就调大 `setChunksPerTick(...)`。多维度下这 4 次要先平均分给各维度（余数给先注册的），所以三张图各 1 块/ tick（地球还多拿 1 块）——加地图不会让预算翻倍，只会让每张图更慢铺满。
3. **空间索引格固定 4 格，而且是每个维度各一份**。`Dimension` 内部用 `new GridSpatialIndex(4f)`；包围盒查询成本大致随半径平方增长，维度数只影响内存与每 tick 预算，不影响单次查询成本。
4. **库存没有重量/体积概念**，容量只有"格数"与"总件数"两个上限，也不区分货架类型。
5. **物流没有优先级与公平性**。`Logistics` 按订单注册顺序与距离处理；两个工厂抢同一批料时先到先得。
6. **每单位一次寻路**。队形移动时每个单位都会走 `PathService` 请求一次；12 个单位的编队就是 12 次搜索（同 tick 内受预算约束）。
7. **异常直接冒泡**。事件监听器抛异常会中断 `tick()`；自己订阅时记得兜住。
8. **没有序列化**。`World`、订单、生产队列都没有 `Serializable` 或存档格式。
9. **`Inventory` 的过滤器不可变**。要按配方动态改变可接受的物品，得自己构造新的 `Inventory`（或改 `Building` 的库存定义）。
10. **文本地图的 y 轴向下**（第一行是最大的 y），字符是角色字母（`C` 战斗 / `W` 工人 / `S` 支援），不是单位名首字母。
11. **超出 4096 位（`Natural.EXACT_BIT_LIMIT`）的数量一律近似**。这个阈值之上 `Natural` 只保留量级：加减乘都可能"抹掉"小操作数，`subtractClamped` 夹在 0，而且**没有除法**（要比例用 `fractionOf`，它给 `float`）。阈值之内完全没有这个问题，普通玩法也碰不到它。
12. **`Natural.BEYOND` 不是数字，是顶元素**。它比一切都大、吸收运算，引擎用它表达"无上限"（`Inventory.UNLIMITED = Natural.BEYOND`）。因此它不能当作普通数量参与结算，也**不能放进 `ItemBundle`**（`ItemBundle.Builder.add` 会拒绝）。要表达"装得下无限多"请把它写在 `Inventory` 的上限上。
13. **门是单向的，旅行有整段超时**。`Gateway` 默认只朝一个方向，回程要 `reversed()` 或另写一条；`TransferOrder.TIMEOUT_TICKS = 2400`（两分钟）是**整段旅程**的预算，不是每跳重算。没有路线时 `travel` 直接失败，不会瞬移。
14. **一个单位同时只能在一条供应线上**。`travel` 会替换订单队列（`orders().replace(...)`），而搬运工按"同图 + 空闲"挑选；让一个单位既长途旅行又承担本图短驳，只会让它两头都做不好。
15. **"无界"指没有地图边缘，不是无限精度**。格坐标是 `int`，`Integer.MAX_VALUE`/`MIN_VALUE` 附近做 `+radius` 仍可能溢出（`ChunkStreamer` 会把焦点附近的半径夹到剩余空间），`Vec2` 是 `float`，10 万格处分辨率约 `0.0078` 格、100 万格处约 `0.0625` 格。`InfiniteTerrainMap` 保证的是**内存**有界（默认 4096 块，LRU），不是坐标有无限精度；如果预算小于流式窗口，两个策略会互相驱逐，帧率会变差但结果仍正确。

## FAQ

### 1. 单位完全不动

依次检查：① `unit.orders().isEmpty()` 为 true（没人下命令，或订单已经结束）；② `unit.motion().hasGoal()` 为 false（订单没设目标）；③ `engine.pathService().pendingRequests()` 长期不为 0（预算被占满）；④ 目标不可达（水中央），此时 A* 会退回最近的可通行邻格，若四周都是水则无路；⑤ 你忘了调 `tick()`。

### 2. 「等全部单位到达」在第 0 tick 就返回 true

`Motion.isArrived()` 在单位从未接过目标时**默认就是 true**。等待条件必须同时判断"没有进行中的订单"或位置距离，见 [01](01-getting-started.md#三个立刻会遇到的坑)。

### 3. 单位挤在一起或原地抖动

① 移动过实体却没调 `world.spatial().update(unit)`，分离力用的是旧邻居；② 分离参数过小（`SteeringSettings` 的分离半径/强度）；③ 大量单位同时要求同一个阵位时会有推挤，这是软避让的正常现象——把队形间距调大，或减少同 tick 的编队规模。

### 4. 生产卡在 `WAITING_MATERIALS`

① 材料在**工厂自己的缓冲区**或**搬运工背包**里——这些都不算"可用"；② 玩家根本没有仓库（建筑类型没写 `.warehouse()`）；③ 仓库未建成（`isConstructed()` 为 false）；④ 仓库的 `storageTag` 把该物品过滤掉了；⑤ 配方里的物品路径拼错（`ContentRegistry` 构建时就会报错，所以一般不会走到这里）。

### 5. 生产卡在 `DELIVERING`

`directDelivery(false)` 且没有任何能搬运的单位（`UnitType.canHaul()` 为 false 的工人不行）。派一个 `cargo > 0` 的工人即可；超时（3000 tick）后任务会退回 `WAITING_MATERIALS` 并释放预订。

### 6. 生产卡在 `WAITING_OUTPUT`

产出的物品放不下：给建筑更多 `storage(n)` 格，或建一个仓库（`ProductionSystem` 会把非仓库建筑的产出转入玩家的物流网络；仓库也满时就会停在这里，物品留在缓冲区，不会被销毁）。

### 7. 仓库不认物品 / `logistics().amount(item)` 一直是 0

① 建筑类型没标 `.warehouse()`；② 建筑还是未建成状态；③ `Inventory.insert(...)` 的返回值被忽略（可能只塞进去一部分，甚至 0，因为槽位满了或过滤器拒绝）；④ 你以为在建筑缓冲区里的东西其实进的是工厂自己的库存。

### 8. 地图全是草地，看不出地形变化

先看是不是出生地附近：默认 `originRadius` 内会被混合成可建造陆地。要看自然地貌就往远处看（例如 `engine.render(new GridPos(96, 64), 24)`）。想整体更丰富就调大 `NoiseSettings.frequency()` 或换阈值；直接用 `FlatChunkGenerator` 则永远只有一种地形。

### 9. 64×64 范围内完全没有水或岩石

海平面与各阈值相对噪声分布偏极端。把 `seaThreshold` 调高（更多水）或把 `rockThreshold` 调低（更多岩石）；也可以增加 `octaves` 让起伏更剧烈。模块自校验里用的默认值是频率 `1/32`、`sea=0.38 / sand=0.42 / hill=0.56 / rock=0.74`。

### 10. 快照里看不到单位

① 单位在 `snapshot.bounds()` 之外（`capture(center, radius)` 只取半径内的实体）；② 单位已经 `world.destroy(...)` 并在 tick 末 `flush()` 掉；③ 你取快照的是另一个引擎实例。

### 11. 文本地图上的位置和我想的不一样

`AsciiMapRenderer` 的 y 轴**向下**：第一行是 `bounds().maxY()`。所以 `row = tile.y() - bounds().minY()`。字符含义见图例行（`C/W/S` 是角色字母，建筑用类型路径首字母大写）。

### 12. 两次运行结果不一致

逐条对照[确定性规则](09-architecture-and-tick-pipeline.md#确定性规则清单)：有没有用 `java.util.Random`、读系统时间、开线程、依赖 `HashMap` 迭代顺序、或者在自定义系统里缓存了跨 tick 的状态？另外确认两次的命令序列完全一致（包括调用的 tick 位置）。

### 13. 单位会穿过建筑

这是**当前的设计状态**：`MovementSystem` 只用 `TerrainMap.isPassable(...)` 判断能否通行，建筑不改变地形，所以不构成障碍。两种做法：

```java
// 做法 A：把建筑占地改成不可通行地形（记得保存原地形，拆除时恢复）
world.events().subscribe(Events.ConstructionCompleted.class, event -> {
    for (GridPos tile : event.building().occupiedTiles().tiles()) {
        world.map().setTerrain(tile, content.terrain("rock"));   // 或者一个专门的"建筑"地形
    }
});
```

做法 B：自己写一个系统维护"被建筑占用的格集合"，并让你的自定义 `PathFinder`/`TerrainMap` 包装层把那些格视为不可通行。

### 14. `player.logistics()` 抛 `IllegalStateException`

引擎没有替这个玩家接上物流网络。正常路径是通过 `OcRtsEngine.builder(...).build()`（`build()` 会给每个玩家创建 `WarehouseLogistics`）；如果你手写 `new World(...)`，需要自己 `player.attachLogistics(...)`。

### 15. 中文输出在控制台是乱码

Java 17 的 `System.out` 用平台编码（中文 Windows 上是 GBK）。源码编码已经由构建脚本设为 UTF-8，但**打印中文**还需要终端配合，例如 `java -Dstdout.encoding=UTF-8 ...` 或用支持 UTF-8 的终端。

### 16. 手工 `javac` 报 `unmappable character`

源码里有中文时加 `-encoding UTF-8`（Gradle 构建已经配好，只有手工编译需要）。

### 17. 地形和生成器对不上（错位/串行）

自定义 `ChunkGenerator.chunkSize()` 与 builder 的 `chunkSize(n)` 不一致。地图按自己的 `chunkSize` 取格，生成器按自己的尺寸填格，两者必须相等。

### 18. 内存里的区块越来越多

`ChunkedMap` 默认不限制常驻区块数。要么把 `viewRadiusChunks` 调小，要么给地图设置上限：

```java
ChunkedMap map = (ChunkedMap) engine.map();
map.setMaxLoadedChunks(256);      // 0 或负数表示不限；超限时优先卸载离焦点最远的区块
```

### 19. 搬运工在路上被打死了，货会不会没了？

不会（前提是死亡走 `World.destroy(...)`）。`HaulingSystem` 订阅了 `Events.EntityDestroyed`：背包里的货先退回仓库；还没装货的任务改派给别的搬运工；已经装货的任务直接失败并释放预订，生产端看到预订失效会立刻退回 `WAITING_MATERIALS` 重新预订刚收回的库存。唯一会真正丢货的情况是**所有仓库都满了**——那就确实无处可放。直接写血量把单位弄死不会触发这套兜底。

### 20. 库存明明还能装，`isFull()` 却是 true

先分清两种情况：

- **格数或单位上限是有限的**，而某种物品的数量已经大到不再精确（超过 4096 位）：`usedSlots()` 返回 `Natural.BEYOND`，`freeSlots()` 是 `有限值 - BEYOND`，按 `subtractClamped` 的定义夹到 0，于是"满了"。这是刻意的近似，不是 bug；要写"真的满了"的判断，请额外检查 `usedSlots().isBeyond()`。
- **格数与单位上限都是 `Inventory.UNLIMITED`**（也就是 `Natural.BEYOND`）：`freeSlots()` 本身就是 `BEYOND`，这种库存**不会**因为数量巨大而被判满（`RtsScenario` 第 10 节对这两种情况都有断言）。顺带一提，无上限库存上 `capacityFor(item)` 会返回 `BEYOND`（它问的是"还能收多少"，答案是"无限"），而 `insertable(item, n)` 如实返回 `n`——两者都不该被当成精确容量。

如果并没有大数参与（所有数量都在 4096 位以内），那 `isFull()` 只说明**没有空槽**或**单位上限到顶**：半满的堆叠仍能继续补货，所以 `capacityFor` / `insert` 可能仍然给出正数。

### 21. 数量算出来对不上（少了、没变、变成 0）

按这个顺序排查：

1. 数值是不是超过了 `Natural.EXACT_BIT_LIMIT = 4096` 位？超过就进入结构形，加减乘按设计会近似：给塔加一个很小的数不会改变它，两个塔相乘得较大的那个，`subtractClamped` 夹在 0。这是 [12](12-numbers-and-googology.md) 里说明的既定取舍。
2. 有没有拿 `>`、`==`、`+` 直接比较/相加 `Natural`？它不是基本类型，要用 `compareTo`、`equals`、`add`（以及 `isZero()`、`isBeyond()` 这些谓词）。
3. 有没有把巨大值先转成 `long` 再比较？`toLongSaturated()` 会把超出范围的值压成 `Long.MAX_VALUE`，两个不同的巨大值会显得相等。
4. 有没有把比例当数量用？`Natural` **没有除法**，比例只有 `Natural.fractionOf(whole)` / `Health.fraction()` 这一条路，返回的是 `float`；大数之间差 300 个数量级以上时会直接给 0 或 1。
5. 有没有混用 `Math.min` / `Math.max`？它们只接受基本类型，请用 `Natural.min` / `max`。

### 22. 两张图上的单位互相"看见"了，或者给月球单位的命令把地球单位也带偏了

坐标是**每张地图自己的**（见 [13-dimensions.md](13-dimensions.md)），所以"同一格"在两个维度上是两个无关的位置。症状通常来自用错了访问器：

- `world.spatial()`、`world.map()`、`world.pathFinder()` 是**默认维度**的，不是"当前这张图"的。位置相关的代码要用 `world.spatial(unit.dimensionId())` 这一族；`MovementSystem` 与 `PathService` 已经这么做了。
- `world.units()` / `world.buildings()` / `world.unitsOf(player)` 是**所有维度**的和；要一张图就用 `world.units(id)`。只有一个维度时它们才是那张图的活列表，所以旧代码不会坏。
- 生成实体时漏了维度参数：`world.spawnUnit(type, owner, tile)` 会造到默认维度，多维度场景要用 `world.spawnUnit(id, type, owner, tile)`（建筑同理）。
- 订阅 `Events.ChunkLoaded` 时没看 `event.dimension()`，三张图的区块统计会混在一起。

如果只是"某张图没有邻居"，先确认那张图确实有空间索引数据：`world.spatial(id).size()` 应当是这张图上的实体数。

### 23. `travel` 订单失败，或者单位根本走不到另一张图

依次检查：① 目标维度确实存在（`world.hasDimension(target)`）；② **门是单向的**——`Gateway.of(overworld, a, moon, b)` 只把人从地球送到月球，回程要 `lift.reversed()` 或另写一条，`world.route(from, to)` 为空时 `TransferOrder` 会直接 `FAILED`；③ 门是**链式**的：地球 → 月球 → 火星需要两条门，`route` 会做 BFS，缺一环就不可达；④ 单位真的能走到第一道门（地形可通行、`PathService` 没被别的东西饿死）；⑤ 超时是**整段旅程**的 `TransferOrder.TIMEOUT_TICKS = 2400` tick（两分钟），多跳长途可能还没走完就被判失败；⑥ 有别的系统在覆盖它的订单队列——`CommandService.travel(...)` 用的是 `orders().replace(...)`，反过来别人 `replace` 也会顶掉这次旅行。排查看 `world.route(from, to)` 与 `world.gatewayAt(...)` 的返回值最直接。

### 24. 跨维度的货迟迟不到，或者搬运工被派去了别的图

先分清是"发运"还是"走路"：跨图的 job 由 `HaulingSystem.tickShipped` 按 `max(1, directDeliveryTicks) * 跳数` 发运，**不派搬运工**；只有同图的 job 才进 `findHauler`。① 迟迟不到：确认目标维度与供货仓库之间**有门链**（没有路线时 job 一直 pending，直到 3000 tick 超时失败）；② 目标缓冲区塞不下时也会一直等（`canAcceptAll(...)` 过不了就跳过，同样靠超时兜底）；③ 想控制跨图成本就调 `directDeliveryTicks`（它同时是"每跳延迟"）；④ 你自己写派单逻辑时要按 `dimensionId()` 过滤，`findHauler` 自己已经这么做了。详见 [13-dimensions.md](13-dimensions.md) 与 [06](06-economy-logistics-and-production.md)。

## 性能旋钮

| 旋钮 | 默认 | 影响 |
| --- | --- | --- |
| `viewRadiusChunks(n)` | `4` | 常驻区块数约为 `(2n+1)²`；半径每加 1，内存与每 tick 加载量都明显上升 |
| `chunkSize(n)` | `16` | 每区块 `n²` 格；越小越细粒度、区块数越多 |
| `pathBudget(requests, nodes)` | `32 / 200000` | 每 tick 的寻路上限；配合 `PathService` 的统计观察 |
| `directDelivery(boolean)` | `false` | 打开可省掉搬运工的移动与订单开销（用延迟模拟运输） |
| `ticksPerSecond(n)` | `20` | 只改推进频率；移动按"格/秒"积分，所以世界速度不变 |
| `ChunkStreamer.setChunksPerTick(n)` | `4` | 焦点跳转后的铺满速度；通过 `engine.pipeline().systems()` 找到 `ChunkStreamer` 实例调整。多维度下 `n` 是**所有维度共享**的总预算（先平均分，余数给先注册的维度），加一张图不会让预算翻倍 |
| `map.setMaxLoadedChunks(n)` | `ChunkedMap`：`0`（不限）；`InfiniteTerrainMap`：`4096` | 内存上限。`ChunkedMap` 超限时按"离焦点最远"驱逐，`InfiniteTerrainMap` 按"最近最少使用"驱逐；被驱逐的脏区块都会先写回 `ChunkStore` |
| `OcRtsEngineBuilder.infiniteTerrain([seed]).maxLoadedChunks(n)` | 关闭 / `4096` | 打开无界地图并设置其 LRU 预算；不调用 `infiniteTerrain(...)` 时仍然是历史 `ChunkedMap` |
| `SteeringSettings` | `defaults()` | 分离强度、到达减速、速度平滑、卡住判定等的集中参数 |

## 相关文档

- [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) —— 相位、事件、确定性规则、扩展点
- [10-cookbook.md](10-cookbook.md) —— 具体任务的写法
- [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md) —— 生产状态机与超时细节
- [12-numbers-and-googology.md](12-numbers-and-googology.md) —— 数量类型 `Natural` 的完整说明（阈值、近似算术、`BEYOND`）
- [13-dimensions.md](13-dimensions.md) —— 多张地图、每图的空间索引与焦点、环境档案的已生效/未生效字段
- [appendix-api-map.md](appendix-api-map.md) —— 全量类型与常量清单

## 相关源码

- 移动的地形判断：[`MovementSystem.java`](../src/main/java/io/github/sob1234509876/oc/rts/movement/MovementSystem.java)
- 战斗字段与生命：[`CombatStats.java`](../src/main/java/io/github/sob1234509876/oc/rts/combat/CombatStats.java)、[`CombatSystem.java`](../src/main/java/io/github/sob1234509876/oc/rts/combat/CombatSystem.java)、[`UnitType.java`](../src/main/java/io/github/sob1234509876/oc/rts/content/UnitType.java)、[`Health.java`](../src/main/java/io/github/sob1234509876/oc/rts/entity/Health.java)、[`Events.java`](../src/main/java/io/github/sob1234509876/oc/rts/event/Events.java)
- 数量类型的边界：[`Natural.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/Natural.java)、[`NaturalFormat.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/NaturalFormat.java)、[`Inventory.java`](../src/main/java/io/github/sob1234509876/oc/rts/economy/Inventory.java)
- 大数自校验：[`GoogologyCheck.java`](../../oc-googology/src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java)（698 项）、[`RtsScenario.java`](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java)（第 1c、10、11、12、12b、13、14、15、16 节，整份 515 项）
