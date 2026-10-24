# 10 · Cookbook：按任务查做法

> 19 个常见任务的写法。每条只给关键代码，原理链接到对应子系统文档。

## 这一篇讲什么

- 「我想做 X」的配方：加内容、下命令、加系统、换实现、调参数、写场景。
- 每条都标注了需要注意的地方（多数是真实踩过的坑）。
- 需要深入理解时，跟着「详见」的链接走。

---

## 1. 加一种地形

```java
ContentRegistry.builder("oc")
        .terrain("swamp").name("Swamp").moveCost(2.5f).sprite("terrain/swamp").mapColor(0x4A5D23)
        .terrain("cliff").name("Cliff").impassable().sprite("terrain/cliff")
        .terrain("plaza").name("Plaza").buildable().moveCost(0.8f).sprite("terrain/plaza")
        .build();
```

- `passable` 默认 `true`，`buildable` 默认 `false`：`.buildable()` 同时要求可通行。
- `moveCost` 是**进入**该格的相对代价，A* 与实际移动都用它；`1f` 是开阔地。
- **注意**：`NoiseChunkGenerator` 只按路径名找 `water/sand/grass/forest/hill/rock`，新地形不会被它生成。要么用[自己的生成器](#10-换地形生成器)，要么运行时用 `world.map().setTerrain(tile, terrain)` 改。

详见 [02-content-and-items.md](02-content-and-items.md)、[03-map-and-pathfinding.md](03-map-and-pathfinding.md)。

## 2. 加物品或资源（数量不限）

```java
ContentRegistry.builder("oc")
        .item("steel_ingot").name("Steel Ingot").maxStack(60).tag("metal").tag("refined")
        .item("rifle").name("Rifle").maxStack(20).tag("good").tag("military")
        .build();
```

- 物品数量由你的目录决定，引擎不枚举、不硬编码任何物品。
- `maxStack` 决定**单个库存格**能放多少：一个物品数量超过一格时它会占用多个槽位（`Inventory.usedSlots()` 按 `ceil(数量 / maxStack)` 计）。
- `tag` 用于仓储过滤（`.storageTag("ore")`），也是后续给配方/统计分组用的。

详见 [02-content-and-items.md](02-content-and-items.md)。

## 3. 加一个兵种

```java
.unit("rifleman").name("Rifleman").role(UnitRole.COMBAT)
        .health(140).speed(2.2f).radius(0.34f)
        .cost("iron_ingot", 60).cost("plank", 30)
        .buildTime(150)
        .cargo(0)                      // 0 表示不能搬运
        .spriteSet("rifleman")
        .attack(16f, 5.5f).attackCooldown(24)   // CombatSystem 读它：伤害 / 射程 / 冷却
```

- `speed` 单位是**格/秒**，与 tick 率无关（移动用 `Tick.deltaSeconds()` 积分）。
- `radius` 参与分离避让、到达判定与队形间距。
- `cargo(n)` 大于 0 的单位才会被 `HaulingSystem` 当作搬运工。

详见 [02-content-and-items.md](02-content-and-items.md)、[04-units-orders-and-movement.md](04-units-orders-and-movement.md)。

## 4. 加一个建筑：仓库 / 工厂 / 带过滤的料仓

```java
.building("warehouse").name("Warehouse").size(3, 3).storage(24).warehouse()      // 进物流网络
.building("ore_silo").name("Ore Silo").size(2, 2).storage(12).storageTag("ore").warehouse()
.building("barracks").name("Barracks").size(3, 3).storage(6).producer("barracks")  // 只是缓冲区
.building("workshop").name("Workshop").size(4, 3).storage(8).producer("workshop")
```

- `storage(n)`：给建筑 n 个库存格（生产输入/输出缓冲区）。
- `warehouse()`：把它的库存**并入玩家的物流网络**，也就是"这里的东西才能被别处使用"。目录构建时会校验"仓库必须有 storage 格"。
- `storageTag(t)`：只接受带该标签的物品，其它物品 `insert` 会返回 0。
- `producer(tag)`：声明它能跑哪些配方（配方的 `producerTags` 要与之相交）。

详见 [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md)。

## 5. 加一条配方（出单位或出物品）

```java
// 出单位：从建筑出口生成，然后走向 rallyPoint
.recipe("train_soldier").name("Train Soldier").producer("barracks")
        .input("iron_ingot", 50).input("plank", 20)
        .unitOutput("soldier").duration(120)

// 出物品：产出进建筑缓冲区，并被推入玩家的物流网络（放不下的留在缓冲区）
.recipe("smelt_steel").name("Smelt Steel").producer("workshop")
        .input("iron_ingot", 20).input("coal", 10)
        .itemOutput("steel_ingot", 10).duration(100)
```

- 配方必须有**至少一个 `producer(...)`** 和**至少一个产出**，否则 `build()` 抛异常。
- 输入引用的是物品路径，构建时才解析，所以配方可以引用后面才定义的物品。
- `duration` 单位是 tick（20 tick/秒时 120 tick = 6 秒）。

## 6. 让一组单位排成队形走过去

```java
List<Unit> squad = engine.world().unitsOf(player);
int formationId = engine.commands().moveGroup(squad, new GridPos(60, 40), FormationShape.WEDGE);
engine.tick(600);
```

`moveGroup` 会返回这次编队的 id（没有可指挥单位时返回 `0`）。边走边改目标：

```java
// UnitDirector 接口上只有移动/停止，改锚点要用具体类型
CommandService commands = (CommandService) engine.commands();
commands.updateFormationAnchor(formationId, new GridPos(20, 20).center());
```

- 队形是 `moveGroup` 的一部分；想让单位各自走就循环调 `commands().move(unit, tile)`。
- 锚点移动小于 `FormationMoveOrder.ANCHOR_EPSILON`（0.25 格）时会被忽略，避免不停重算路径。

详见 [05-formations.md](05-formations.md)、[04-units-orders-and-movement.md](04-units-orders-and-movement.md)。

## 7. 让单位停下或原地驻守

```java
engine.commands().stop(unit);        // 清空订单队列，完全停下
engine.commands().hold(unit);        // 队列里放一个"驻守"订单（不会自动结束）
engine.commands().stop(engine.world().unitsOf(player));   // 整队停下
```

`stop` 与 `hold` 的区别：前者队列为空，后者队列头上有一个 `HoldOrder`，方便以后挂"警戒/反击"行为。

## 8. 写一个自定义订单

订单只负责"设目标 + 等到达"，移动交给移动系统：

```java
public final class GatherOrder implements Order {

    private final GridPos resourceTile;
    private boolean walking;

    public GatherOrder(GridPos resourceTile) {
        this.resourceTile = resourceTile;
    }

    @Override
    public String name() {
        return "gather";
    }

    @Override
    public OrderState tick(OrderContext context) {
        Unit unit = context.unit();
        if (!walking) {
            unit.motion().setGoal(resourceTile.center());   // 关键：设目标，而不是 commands().move(...)
            walking = true;
        }
        if (!unit.motion().isArrived()) {
            return OrderState.RUNNING;
        }
        // 到了：这里做真正的采集，例如
        ItemBundle harvested = ItemBundle.of(context.world().content().item("wood"), 5);
        unit.cargo().insert(harvested);
        return OrderState.COMPLETED;
    }
}

// 下达
unit.orders().push(new GatherOrder(new GridPos(30, 30)));
```

- **绝对不要**在订单里调 `context.director().move(...)`：那会替换整个订单队列，把正在执行的自己取消掉（引擎里真的踩过这个死锁）。
- 订单要自己处理超时/失败（返回 `OrderState.FAILED`），否则单位可能永久卡住。
- 订单被取消时 `onCancel(context)` 会被调用，适合在那里退还资源/释放预订。

详见 [04-units-orders-and-movement.md](04-units-orders-and-movement.md)。

## 9. 加一个自己的系统

```java
public final class TrickleIncomeSystem implements SimulationSystem {

    private final Player player;
    private final ItemType wood;
    private int counter;

    public TrickleIncomeSystem(Player player, ItemType wood) {
        this.player = player;
        this.wood = wood;
    }

    @Override
    public String name() {
        return "trickle-income";
    }

    @Override
    public int order() {
        return 650;                       // 500 与 700 之间：建造之后、搬运之前
    }

    @Override
    public void tick(World world, Tick tick) {
        if (++counter % 20 != 0) {        // 每 20 tick 发一次，避免每 tick 都做仓库查询
            return;
        }
        if (player.hasLogistics()) {
            player.logistics().deposit(ItemBundle.of(wood, 5), Vec2.ZERO);
        }
    }
}

// 注册（引擎的管线是公开的）
engine.pipeline().add(new TrickleIncomeSystem(engine.players().get(0), content.item("wood")));
```

- `order()` 决定相位；参考 [09](09-architecture-and-tick-pipeline.md) 的相位表选一个空档。
- 系统里不要用 `java.util.Random`/时间/线程；随机用 `world.random()`。
- 生成实体时不要边遍历 `world.units()` 边插入（见 [09](09-architecture-and-tick-pipeline.md#一-tick-内实体的增删)）。

## 10. 换地形生成器

现成的平坦生成器（测试与几何断言最好用）：

```java
OcRtsEngine.builder(content)
        .generator(new FlatChunkGenerator(content.terrain("grass")))
        .build();
```

自己写一个（条纹世界）：

```java
public final class StripesGenerator implements ChunkGenerator {

    private final TerrainType land;
    private final TerrainType water;

    public StripesGenerator(TerrainType land, TerrainType water) {
        this.land = land;
        this.water = water;
    }

    @Override
    public int chunkSize() {
        return 16;
    }

    @Override
    public Chunk generate(ChunkPos pos) {
        Chunk chunk = new Chunk(pos, chunkSize());
        int originX = pos.x() * chunkSize();
        for (int localY = 0; localY < chunkSize(); localY++) {
            for (int localX = 0; localX < chunkSize(); localX++) {
                boolean dry = Math.floorDiv(originX + localX, 8) % 2 == 0;
                chunk.setTerrain(localX, localY, dry ? land : water);
            }
        }
        return chunk;
    }
}
```

- **生成必须只依赖 `ChunkPos` 与种子**：同一区块无论何时生成结果都要一样，否则流式卸载再加载会出现地形跳变、也会破坏确定性。
- **`chunkSize()` 必须与 builder 的 `chunkSize(n)` 一致**，否则地图会按错误的步长读格子。
- 想调噪声地形本身（海平面、丘陵阈值、出生地保证区）改 `NoiseSettings`，见 [03](03-map-and-pathfinding.md)。

## 11. 把区块存到磁盘

`ChunkStore` 只有三个方法，形状完全由你定（引擎不管序列化格式）：

```java
public final class FileChunkStore implements ChunkStore {

    private final Path directory;

    public FileChunkStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public Optional<Chunk> load(ChunkPos pos) {
        // 读回你自己写的格式；没有文件就返回 Optional.empty()，地图会退回生成器
        return Optional.empty();   // 示意：这里替换成真正的反序列化
    }

    @Override
    public void save(Chunk chunk) {
        // chunk.pos()、chunk.size()、chunk.rawTiles() 足以完整还原一个区块
        chunk.markClean();         // 关键：写完必须清脏标记，否则每次卸载都会重复落盘
    }

    @Override
    public boolean exists(ChunkPos pos) {
        return Files.exists(fileFor(pos));
    }
}

// 使用
OcRtsEngine.builder(content)
        .chunkStore(new FileChunkStore(Path.of("saves", "world1")))
        .build();
```

- 载入优先级是**存储优先、生成器兜底**，所以被编辑过的地形不会丢。
- 只有 `dirty` 的区块会被写回：`ChunkedMap.unload(...)` 与 `flushDirty()` 都会调用 `store.save(...)`，而 `ChunkedMap` **自己不会**替你调 `markClean()`——落盘方负责清标记（`InMemoryChunkStore` 就是这么做的），忘了它会让区块永远脏、反复写盘。
- 引擎不提供存档格式，也不保存实体与生产队列；完整存档需要你自己把 `World` 的实体状态一起写出去。

详见 [03-map-and-pathfinding.md](03-map-and-pathfinding.md)。

## 12. 调寻路吞吐

```java
OcRtsEngine.builder(content)
        .pathBudget(64, 400_000)     // 每 tick 最多 64 个请求、共 40 万节点扩展
        .build();

// 观察是否被预算卡住
PathService paths = engine.pathService();
System.out.println("排队 " + paths.pendingRequests()
        + "，本 tick 处理 " + paths.processedLastTick()
        + "，失败 " + paths.failedLastTick()
        + "，消耗节点 " + paths.spentNodesLastTick());
```

- 默认是 **32 个请求 / 200000 节点每 tick**。单个搜索的上限来自 `PathOptions.maxExpansions()`（默认 20000），所以默认预算下每 tick 实际能服务约 10 次搜索。
- 大批单位同时移动时如果 `pendingRequests()` 长期不为 0，就该调大预算或降低请求密度。
- 拿到 `Optional.empty()` 应该理解为"**这次没算出来**"（预算耗尽 / 目标不可达），`PathService` 的处理方式就是直线走 + 30 tick 后重试。
- 想减少请求数：让队伍少改目标（锚点移动有 0.25 格阈值）、或给整组共享一个目的地策略。

详见 [03-map-and-pathfinding.md](03-map-and-pathfinding.md)。

## 13. 物流：自动送货 vs 真实搬运工

```java
// 模式 A：省事。材料会在 directDeliveryTicks（默认 60）后视作送达，不需要工人
OcRtsEngine.builder(content).directDelivery(true).directDeliveryTicks(60).build();

// 模式 B：真实。需要一个能搬运的单位（cargo > 0，role 是 WORKER）
OcRtsEngine engine = OcRtsEngine.builder(content).directDelivery(false).build();
Player player = engine.players().get(0);
engine.spawnUnit("worker", player, new GridPos(12, 13).center());   // 搬运工
// 之后 HaulingSystem 会自动把仓库里的材料搬到需要它的工厂
```

- 两种模式的**规则完全一致**：都只能从仓库取货，都必须先 `reserve` 再交付。区别只在于有没有可见的运输单位。
- 模式 B 下如果没有任何搬运工，生产会停在 `DELIVERING`，并最终因超时退回 `WAITING_MATERIALS`。
- 搬运工中途被打死（走 `World.destroy(...)`）时，背包里的货会被收回仓库，任务要么改派要么关闭后由生产重新预订。

详见 [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md)。

## 14. 排产并跟踪状态

```java
Player player = engine.players().get(0);
Building barracks = engine.spawnBuilding("barracks", player, new GridPos(20, 10));
ProductionSystem production = engine.production();

production.enqueue(barracks, content.recipe("train_soldier"), 3);      // 排 3 个
engine.tick(600);

ProductionQueue queue = production.queueOf(barracks);
if (!queue.isEmpty()) {
    ProductionJob job = queue.current();
    System.out.println("当前任务 " + job.recipe().key() + " 状态 " + job.state());
}
production.cancel(barracks, 0);                                       // 取消队首
```

- `enqueue` 会校验建筑能不能跑这条配方（producer tag 不匹配会抛 `IllegalArgumentException`）。
- 状态含义：`WAITING_MATERIALS` → `DELIVERING` → `PRODUCING` → `WAITING_OUTPUT` / `COMPLETED`（完整流转见 06）。
- **未建成的建筑不生产**：用 `world.placeBuilding(...)` 放的建筑要靠附着到它上面的 `BuildOrder`（`commands().tryBuild(unit, type, origin)` 或 `commands().tryBuild(unit, site)`）让 `ConstructionSystem` 推进；没有附着建造者的工地不会自己推进，也可以直接 `building.completeConstruction()`。注意 `enqueue` 本身不检查是否建成，工地也能排产，任务会一直等到完工。

## 15. 监听引擎事件

```java
engine.events().subscribe(Events.ItemProduced.class,
        event -> System.out.println(event.building() + " 产出 " + event.items()));

engine.events().subscribe(Events.ChunkLoaded.class,
        event -> System.out.println("区块进入内存 " + event.pos()));

EventBus.Subscription sub = engine.events().subscribe(Events.OrderFinished.class,
        event -> System.out.println(event.unit() + " 结束订单 " + event.orderName() + " " + event.state()));
sub.cancel();   // 不再需要时注销
```

- 事件是**同步**分发的：监听器里做重活会直接拖慢 tick。
- 事件表与发布者清单见 [09](09-architecture-and-tick-pipeline.md#事件总线)；`Events.DamageDealt` 与 `Events.EntityKilled` 由战斗系统在相位 450 发布。

## 16. 让渲染层或工具跟上模拟

```java
// 1) 告诉引擎"玩家在看哪儿"，它只保留半径内的区块
engine.streaming().focus(new Vec2(cameraX, cameraY), 4);
// 或者用格坐标
engine.focus(new GridPos(120, 80));

// 2) 每个渲染帧取一份只读快照
WorldSnapshot snapshot = engine.snapshot(new GridPos(120, 80), 40);
for (EntitySnapshot entity : snapshot.entities()) {
    SpriteFrame frame = entity.spriteFrame();      // 可能为 null（没有动画集）
    // 用 frame.sheetId() 找到图集，按 frame.pixelX()/pixelY()/width()/height() 裁剪并绘制
}
```

- 快照是**不可变拷贝**：拿到之后世界继续跑也不会影响它，可以丢给渲染线程。
- 快照的 `elapsedSeconds()` 与 `engine.elapsedSeconds()` 是同一套时间（引擎把 tick 率传给了 `SnapshotFactory`）。
- 想看纯文本版就直接 `engine.render(center, radius)`（`AsciiMapRenderer`）。
- 视野之外的区块会被 `ChunkStreamer` 卸载；`snapshot.terrainAt(...)` 仍能按需生成，但那是磁盘/CPU 成本。

详见 [08-snapshots-and-rendering.md](08-snapshots-and-rendering.md)。

## 17. 写一个无框架自校验场景

和模块自带 `scenario` 任务完全相同的套路：**源码放在一个独立源集里，写一个带 `main` 的类，再用一个 Gradle `JavaExec` 任务跑它**，不需要 JUnit，也不需要网络。之所以不放在 `src/test/java`：没有测试框架时，`test` 任务会因为"发现了源码却没跑测试"而在 Gradle 9 报错。

```groovy
// build.gradle
sourceSets {
    selfCheck {
        java.srcDir 'src/selfcheck/java'
        compileClasspath += sourceSets.main.output + sourceSets.main.compileClasspath
        runtimeClasspath += sourceSets.main.output + sourceSets.main.runtimeClasspath
    }
}

tasks.register('scenario', JavaExec) {
    group = 'verification'
    description = 'Runs the headless RTS engine self check scenario.'
    classpath = sourceSets.selfCheck.runtimeClasspath
    mainClass = 'io.github.sob1234509876.oc.rts.scenario.RtsScenario'
}
```

```java
public static void main(String[] args) {
    Check check = new Check();
    // ... 构造引擎、下命令、跑 tick ...
    check.that(units.size() == 8, "八个单位都在场");
    check.atMost(4f, farthestDistance, "全部到达目标附近");
    check.summary("my scenario");      // 有失败就抛 AssertionError，进程退出码非 0
}
```

- `Check` 是模块里的轻量断言器（`that/equal/atLeast/atMost/close/summary`），照抄即可。
- 断言里等待到达时，记得同时判断 `!unit.orders().hasCurrent()`（见 [01](01-getting-started.md#三个立刻会遇到的坑)）。

详见模块里的 [`RtsScenario.java`](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java)。

## 18. 让模拟可复现

```java
// 两次完全相同的种子 + 完全相同的命令序列
List<Vec2> first = runShortMatch(content, 99L);
List<Vec2> second = runShortMatch(content, 99L);
// 用 Float.floatToIntBits 逐位比较，而不是 ==（NaN/精度更严格）
```

必须遵守的禁忌（详见 [09](09-architecture-and-tick-pipeline.md#确定性规则清单)）：

- 不用 `java.util.Random` / `Math.random()`，只用 `world.random()` 或其 `fork(...)`。
- 不读系统时间；时间只能来自 `Tick`。
- 不开线程、不用 `parallelStream()`。
- 不依赖 `HashMap`/`HashSet` 的迭代顺序；平局用 `EntityId` 或坐标显式打破。
- 自定义系统里不要缓存跨 tick 的派生状态而不订阅实体增删事件去维护。
- 寻路的启发式尺度也要来自内容目录而不是"当前加载了哪些区块"（引擎已经用 `AStarPathFinder.minimumMoveCost(ContentRegistry)` 这么做了）。

## 19. 让数量、容量、血量变成任意大

数量类型是 `Natural`（精确到 4096 位，再大就是超运算记号），所以内容目录里可以直接写天文数字：

```java
ContentRegistry content = ContentRegistry.builder("oc")
        .item("relic").name("Relic").maxStack(Natural.arrow(Natural.TEN, 1, Natural.of(5000)))  // 每格 10^5000
        .unit("titan").name("Titan").role(UnitRole.COMBAT)
                .health(Natural.tetration(Natural.TEN, Natural.TEN))    // 血量 10↑↑10
                .speed(1.5f).radius(0.4f)
        .build();

Inventory vault = new Inventory("vault", Inventory.UNLIMITED, Inventory.UNLIMITED, item -> true);
vault.insert(content.item("relic"), Natural.tetration(Natural.TEN, Natural.TEN));   // 一次收下 10↑↑10 件
titan.health().damage(Natural.of(1000));      // 在这个量级上打不动（近似）
titan.health().damage(Natural.BEYOND);        // 一击必杀
```

要注意的三件事：

- 所有 `int`/`long` 字面量照旧可用（`maxStack(100)`、`health(120)`、`insert(item, 5)`），但 `Natural` 不能用 `>`、
  `<`、`==` 比较，要用 `compareTo` / `isZero` / `equals`。
- 进入大数区间后算术是近似的：`10↑↑10 + 1` 还是 `10↑↑10`；`usedSlots()` 会返回 `Natural.BEYOND`，此时**格数有限**
  的库存会报告 `isFull()`，无限格数的不会。
- 时间与空间**不要**换成大数：tick 时长是 `int`，坐标/速度/射程是 `float`。

完整的类型分界、近似规则与 10 条坑见 [12-numbers-and-googology.md](12-numbers-and-googology.md)。

## 相关文档

- [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) —— 相位、生命周期、确定性规则、扩展点
- [01-getting-started.md](01-getting-started.md) —— 门面与 Builder 速查
- [12-numbers-and-googology.md](12-numbers-and-googology.md) —— 大数与 `Natural`：哪些精确、哪些近似
- [11-limits-and-faq.md](11-limits-and-faq.md) —— 出问题时的排查清单
- [appendix-api-map.md](appendix-api-map.md) —— 全量类型与常量清单
