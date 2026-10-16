# 01 · 快速上手

> 从零到"一群士兵按队形走到目标点并打印出地图"。

## 这一篇讲什么

- 怎么构建、怎么跑引擎自带的自校验场景。
- 引擎的心智模型：谁持有状态、谁负责推进、为什么没有渲染。
- 一个完整可运行的最小例子（含全部 import，已编译验证）。
- `OcRtsEngineBuilder` 的全部选项与默认值、`OcRtsEngine` 与 `World` 的常用方法速查。
- 三个几乎一定会踩到的坑。

## 前置条件

| 需要 | 说明 |
| --- | --- |
| JDK 17 | 模块用 `java.toolchain` 固定为 17；`JAVA_HOME` 指向 17 即可 |
| Gradle | 仓库自带 wrapper（`gradlew` / `gradlew.bat`），无需本机安装 |
| 网络 | **不需要**。引擎没有任何第三方依赖，`build` 与 `scenario` 都可离线执行 |
| 源码编码 | 构建脚本已设 `options.encoding = 'UTF-8'`（中文物品名/注释可以直接写）。若你手工用 `javac` 编译，记得加 `-encoding UTF-8` |

## 构建与自校验

```shell
./gradlew :projects:oc:oc-rts-engine:build      # 编译、打 jar
./gradlew :projects:oc:oc-rts-engine:scenario   # 无框架自校验
```

`scenario` 的末尾会打印一行结论：

```text
RTS engine scenario: all 152 checks passed.
```

它逐节覆盖：[区块地图与寻路](03-map-and-pathfinding.md)、[队形移动](05-formations.md)、[物品与仓库](06-economy-logistics-and-production.md)、[生产](06-economy-logistics-and-production.md)、[真实搬运](06-economy-logistics-and-production.md)、[贴图与动画](07-sprites-and-animation.md)、[确定性](09-architecture-and-tick-pipeline.md)，然后是[大数与 `Natural`](12-numbers-and-googology.md)（物品每格 `10^5000`、血量 `10↑↑10`）与[维度](13-dimensions.md)（地球/月球/火星三张图、两跳传送门与旅行、跨维度送货）。把它当活文档读，比读本文更快。

自校验不是框架测试，所以它位于独立的 `selfCheck` 源集（`src/selfcheck/java/.../scenario/`），由 `scenario` 任务以普通程序运行；`src/test/java` 因此保持为空，给以后的单元测试用。

## 心智模型

1. **状态都在 `World` 里**：地形是 `World.map()`，实体是 `World.units()` / `World.buildings()`，玩家是 `World.players()`。系统不持有游戏状态，只操作 `World`。
2. **没有主循环**：`engine.tick()` 推进一个固定步长（默认每秒 20 tick），谁来调用由你决定。GUI 在渲染帧里调，测试在 `for` 里调，脚本用 `tickUntil(...)`。
3. **每 tick 按相位跑系统**：区块流式(100) → 订单(200) → 寻路请求(300) → 移动(400) → 建造(500) → 生产(600) → 搬运(700) → 动画(800)。细节见 [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md)。
4. **不含渲染**：引擎只维护"谁在哪儿、朝哪边、在播哪段动画"。想画出来就取一份[只读快照](08-snapshots-and-rendering.md)；想快速看一眼就用 `engine.render(...)` 打文本地图。
5. **单线程、不读时钟、不用 `java.util.Random`**：所以同种子 + 同命令 = 逐位相同的结果。

## 最小可运行例子

下面这段代码是完整的类，可以直接放进 `src/selfcheck/java` 跑（本仓库验证过编译与运行）。
它建一个平坦草地世界、造 8 个士兵、让它们以楔形走到 (40, 24)，然后打印文本地图。

```java
import io.github.sob1234509876.oc.rts.OcRtsEngine;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.formation.FormationShape;
import io.github.sob1234509876.oc.rts.map.FlatChunkGenerator;

import java.util.ArrayList;
import java.util.List;

public final class QuickStart {

    public static void main(String[] args) {
        // 1) 所有游戏内容都是数据：地形、物品、单位、建筑、配方（详见 02）
        ContentRegistry content = ContentRegistry.builder("oc")
                .terrain("grass").name("Grass").buildable()
                .terrain("water").name("Water").impassable()
                .item("iron_ingot").name("Iron Ingot").maxStack(60).tag("metal")
                .item("plank").name("Plank").maxStack(80).tag("material")
                .unit("soldier").name("Soldier").role(UnitRole.COMBAT)
                        .health(120).speed(2.4f).radius(0.35f)
                        .cost("iron_ingot", 50).cost("plank", 20)
                        .buildTime(120).spriteSet("soldier")
                .building("barracks").name("Barracks").size(3, 3).storage(6).producer("barracks")
                .recipe("train_soldier").producer("barracks")
                        .input("iron_ingot", 50).input("plank", 20)
                        .unitOutput("soldier").duration(120)
                .build();

        // 2) 组装引擎。示例用平坦地形，让结果完全可预测；真实地图用默认的 NoiseChunkGenerator（详见 03）
        OcRtsEngine engine = OcRtsEngine.builder(content)
                .seed(42L)
                .generator(new FlatChunkGenerator(content.terrain("grass")))
                .chunkSize(16)
                .viewRadiusChunks(3)
                .directDelivery(true)
                .build();

        // 3) 出生一队士兵（引擎会在没有玩家时自动建一个默认玩家，见下面的 Builder 表）
        Player player = engine.players().get(0);
        List<Unit> squad = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            squad.add(engine.spawnUnit("soldier", player, new Vec2(8.5f + i * 0.3f, 8.5f)));
        }

        // 4) 下命令：全部走到 (40, 24)，按楔形展开（返回值是这次编队的 id，这里不需要）
        Vec2 target = new GridPos(40, 24).center();
        engine.commands().moveGroup(squad, new GridPos(40, 24), FormationShape.WEDGE);

        // 5) 推进。注意等待条件：单位"还没接到命令"时 isArrived() 也是 true，所以要一起判断位置
        boolean arrived = engine.tickUntil(e -> squad.stream().allMatch(unit ->
                !unit.orders().hasCurrent()
                        && unit.motion().isArrived()
                        && unit.position().distance(target) <= 4f), 1200);

        System.out.println("arrived=" + arrived + ", tick=" + engine.tickNumber()
                + ", 用时 " + engine.elapsedSeconds() + " 秒");
        System.out.println(engine.describe());
        System.out.println(engine.render(new GridPos(40, 24), 12));
    }
}
```

`engine.describe()` 打印这个例子的实测结果（JDK 17，本仓库验证时）：

```text
arrived=true, tick=309, 用时 15.45 秒
tick 309 (15.4s), entities 8, units 8, buildings 0, chunks 49, pending paths 0, pending deliveries 0
systems: 100: chunk-streamer, 200: orders, 300: paths, 400: movement, 500: construction, 600: production, 700: hauling, 800: animation
```

`engine.render(...)` 会看到 8 个士兵已经展开成楔形（`C` 是战斗单位的角色字母，y 轴向下增长，详见 [08](08-snapshots-and-rendering.md)）：

```text
.........................
..........CC.............
...........C.............
.........CCCC............
.........................
```

代码里有五个约定值得记住：**内容先定义、引擎后组装、单位由 `World` 生成、命令只经 `UnitDirector`、推进由你调用**。

## `OcRtsEngineBuilder` 选项

`OcRtsEngine.builder(content)` 是唯一入口，所有选项都可省。

| 方法 | 默认值 | 作用 |
| --- | --- | --- |
| `seed(long)` | `20240101L` | 世界种子；地形与随机流都由它派生 |
| `ticksPerSecond(int)` | `20` | 固定步长。移动按"格/秒"计算，所以改这个只改推进频率，不改世界速度 |
| `chunkSize(int)` | `16` | 区块边长（格） |
| `viewRadiusChunks(int)` | `4` | 焦点周围保留多少区块；半径 4 即 9×9 个区块常驻内存 |
| `generator(ChunkGenerator)` | `NoiseChunkGenerator(seed, content)` | 地形生成器；测试常用 `FlatChunkGenerator` |
| `chunkStore(ChunkStore)` | `InMemoryChunkStore` | 被编辑过的区块存哪儿；换成自己的实现即可做存档 |
| `visuals(VisualLibrary)` | `VisualLibrary.empty()` | 动画集；为空时单位用 `NullVisualState`，不影响模拟 |
| `directDelivery(boolean)` | `false` | `true` 时物流不需要搬运工，按延迟直接送达（详见 06） |
| `directDeliveryTicks(int)` | `60` | 自动送货所需 tick 数 |
| `pathBudget(int, int)` | `32` 个请求 / `200000` 节点每 tick | 寻路吞吐上限（详见 03） |
| `player(Player)` / `players(List)` | 自动建 `Player.of(1, "Player 1", 0xFF4FC3F7)` | 参战玩家；每个玩家的仓库网络在 `build()` 时接上 |

`build()` 会按相位把 8 个系统装进 `SystemPipeline`，把 `AStarPathFinder`（用内容目录的最小地形步进作为启发式尺度）、`PathService`、`CommandService` 接到 `World` 上，并让焦点落在原点。

## `OcRtsEngine` 门面速查

| 分组 | 方法 | 说明 |
| --- | --- | --- |
| 状态 | `world()` / `content()` / `players()` / `events()` | 拿状态与事件总线 |
| 时间 | `tickNumber()` / `ticksPerSecond()` / `elapsedSeconds()` / `currentTick()` | 当前 tick 与模拟时间 |
| 推进 | `tick()` / `tick(int)` / `tickUntil(Predicate<OcRtsEngine>, int)` | `tickUntil` 返回条件是否达成 |
| 命令 | `commands()` → [`UnitDirector`](04-units-orders-and-movement.md) | `move` / `moveGroup`（返回编队 id） / `stop` / `hold` |
| 生产 | `production()` → [`ProductionSystem`](06-economy-logistics-and-production.md) | `enqueue` / `queueOf` / `cancel` / `availableRecipes` |
| 寻路 | `pathFinder()` / `pathService()` / `map()` / `streaming()` | 查询寻路、预算与区块状态 |
| 生成 | `spawnUnit(path, player, Vec2/GridPos)` / `spawnBuilding(path, player, GridPos)` | 按内容路径生成，省去自己查 `ContentRegistry` |
| 展示 | `snapshot(center, radius)` / `render(center, radius)` / `loadedBounds()` | 只读快照与文本地图（详见 08） |
| 诊断 | `describe()` / `pipeline()` / `visuals()` | 一行摘要、系统管线、动画库 |

`World` 上最常用的方法（完整清单见 [09](09-architecture-and-tick-pipeline.md)）：

| 方法 | 说明 |
| --- | --- |
| `units()` / `buildings()` / `entities()` | 实体集合（`units()`、`buildings()` 是实时列表，遍历时不要在其中生成单位） |
| `unitsOf(player)` / `buildingsOf(player)` | 按玩家过滤（返回新列表，可安全遍历） |
| `spawnUnit(type, owner, Vec2)` / `spawnBuilding(type, owner, origin)` | 生成；`spawnBuilding` 直接建成，`placeBuilding` 生成未建成状态 |
| `canPlace(type, origin)` | 该地块是否可建造（地形可建 + 不与建筑重叠） |
| `destroy(entity)` | 标记死亡；实体在 tick 末的 `flush()` 才真正移除 |
| `spatial()` | 空间索引；**移动过实体后必须 `spatial().update(entity)`** |
| `events()` | 事件总线（`Events.*`，同步分发） |
| `random()` | 世界级 `DeterministicRandom`；自定义系统请从它派生流 |
| `tickNumber()` / `advanceTick()` | tick 计数；`advanceTick()` 由引擎调用，业务代码一般只读 |

## 三个立刻会遇到的坑

1. **`Motion.isArrived()` 初始就是 `true`**。单位还没接到命令时它没有目标，自然"已到达"。任何"等全体到达"的判定都要同时看 `!unit.orders().hasCurrent()` 或位置距离，否则会在第 0 tick 就误判成功。正确写法见上面例子第 5 步。
2. **订单不要用 `commands().move(...)` 来给自己走路**。`UnitDirector.move` 会**替换**订单队列，等于取消当前正在执行的订单。自定义订单的正确做法是 `unit.motion().setGoal(...)` 然后等 `motion().isArrived()`（详见 [04](04-units-orders-and-movement.md)）。
3. **仓库要显式声明**。`storage(n)` 只是给建筑储物格；只有 `.warehouse()` 的建筑库存才进入 `player.logistics()`，生产也只能从这里取料（详见 [06](06-economy-logistics-and-production.md)）。

## 接下来读什么

- 想加内容（物品/兵种/建筑/配方） → [02-content-and-items.md](02-content-and-items.md)
- 想改地图或寻路 → [03-map-and-pathfinding.md](03-map-and-pathfinding.md)
- 想让单位做事情 → [04-units-orders-and-movement.md](04-units-orders-and-movement.md)、[05-formations.md](05-formations.md)
- 想做经济与工厂 → [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md)
- 想接渲染层 → [07-sprites-and-animation.md](07-sprites-and-animation.md)、[08-snapshots-and-rendering.md](08-snapshots-and-rendering.md)

## 相关源码

- 门面与接线：[`OcRtsEngine.java`](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngine.java)、[`OcRtsEngineBuilder.java`](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngineBuilder.java)
- 世界状态：[`World.java`](../src/main/java/io/github/sob1234509876/oc/rts/World.java)
- 自校验的真实出处：[`RtsScenario.java`](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java)、[`ScenarioContent.java`](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/ScenarioContent.java)
