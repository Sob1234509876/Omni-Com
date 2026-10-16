# 08 只读快照与渲染接入

> 一句话定位：每 tick 用 `engine.snapshot(cameraTile, radius)` 把可见世界拷成不可变的 `WorldSnapshot`，渲染线程只读这份快照——铁皮罐头（引擎）不带屏幕，快照就是那扇唯一的窗。

## 这一篇讲什么

- `visual.SnapshotFactory` 与 `visual.WorldSnapshot`：快照怎么采、包含什么。
- **快照是"某一张图"的**：每次 `capture(...)` 都点名一个维度，`WorldSnapshot` 带着它的键与展示名，`AsciiMapRenderer` 的图例行也以维度打头；细节见 [13-dimensions.md](13-dimensions.md)。
- `visual.EntitySnapshot` 的**逐个字段**（以 record 声明顺序为准）与几个派生方法。
- `visual.AsciiMapRenderer`：一个把快照完整消费掉、且零依赖的参考实现（字符约定、y 轴方向、图例）。
- 渲染循环的骨架、视野与区块流式（`focus` / `loadedChunks`）的配合。
- 选中、血条、朝向、当前订单名这些 UI 数据分别从哪个字段取。
- 为什么快照可以安全地交给另一个线程，以及"引擎本身单线程"这条红线。

## 一、一个渲染循环长什么样

引擎没有内部循环：`tick()` 推进恰好一个固定步长，谁调用谁定节奏。

<!-- 示意：非编译验证片段 -->
```java
GridPos camera = new GridPos(12, 10);
int radiusTiles = 24;

while (running) {
    // 1) 推进模拟（固定步长；追赶时循环调用 engine.tick() 若干次）
    engine.tick();

    // 2) 相机移动：先告诉流式系统关注点在哪，再取快照
    engine.streaming().focus(camera.center(), engine.streaming().radiusChunks());

    // 3) 采集一份只读快照
    WorldSnapshot shot = engine.snapshot(camera, radiusTiles);

    // 4) 画地形：每格一次 terrainAt(x, y)
    for (int y = shot.bounds().minY(); y <= shot.bounds().maxY(); y++) {
        for (int x = shot.bounds().minX(); x <= shot.bounds().maxX(); x++) {
            TerrainType terrain = shot.terrainAt(x, y);   // 越界返回 fallbackTerrain
            drawTerrainTile(x, y, terrain.spriteId(), terrain.mapColor());
        }
    }

    // 5) 画实体：用已解析好的 SpriteFrame 从图集裁剪
    for (EntitySnapshot entity : shot.entities()) {
        if (!entity.hasSprite()) {
            continue;                                    // 没有动画集的实体，自行兜底
        }
        SpriteFrame frame = entity.spriteFrame();
        SpriteSheet sheet = engine.visuals().sheet(frame.sheetId());
        drawSprite(sheet.imagePath(), frame.pixelRect(),
                entity.position(), frame.pivotX(), frame.pivotY());
        drawHealthBar(entity, entity.healthFraction());
        if (entity.selected()) {
            drawSelectionRing(entity.position(), entity.radius());
        }
    }
}
```

顺序上只有两条硬规则：**先 `tick()` 再 `snapshot()`**（`snapshot` 带的是刚跑完那个 tick 的 `tickNumber()`），以及**只画 `loadedChunks()` 覆盖到的东西**（见第七节）。

## 二、`SnapshotFactory`

`OcRtsEngine` 在 `build()` 时就把工厂造好了，并且会把自己的 tick 率一并传进去（`new SnapshotFactory(world, visuals, ticksPerSecond)`），平时直接用 `engine.snapshot(center, radius)` 即可；自己搭测试夹具时才需要 new 一个——这时请注意选对构造器。

```java
// 引擎内部就是这样构造的：速率跟随 OcRtsEngineBuilder.ticksPerSecond
SnapshotFactory factory = new SnapshotFactory(engine.world(), engine.visuals(), engine.ticksPerSecond());
WorldSnapshot shot = factory.capture(new GridPos(12, 10), 12);
// 等价于 engine.snapshot(new GridPos(12, 10), 12)：两者都拍“默认维度”

WorldSnapshot moon = factory.capture(DimensionId.of("moon"), new GridPos(12, 10), 12);
// 等价于 engine.snapshot(DimensionId.of("moon"), new GridPos(12, 10), 12)

SnapshotFactory byDefault = new SnapshotFactory(engine.world(), engine.visuals());
byDefault.ticksPerSecond();   // 20 = SnapshotFactory.DEFAULT_TICKS_PER_SECOND
```

- `capture(DimensionId dimension, GridPos center, int radiusTiles)` 是规范入口：**地形取自该维度的 `map()`，实体取自该维度的 `spatial()`**，且 `center` 是**那张图**上的格。维度不存在抛 `IllegalArgumentException`，`dimension` 为 null 抛 `NullPointerException`。
- `capture(GridPos center, int radiusTiles)` 仍然存在，等价于 `capture(world.defaultDimensionId(), center, radiusTiles)`：单地图世界与所有旧代码的行为一模一样。
- `radiusTiles < 0` 抛 `IllegalArgumentException`，`center` 为 null 抛 `NullPointerException`。
- 地形逐格**拷贝**成 `TerrainType[bounds.height()][bounds.width()]`，索引是 `[y - minY][x - minX]`；越界读走 `terrainAt` 时返回 `world.content().fallbackTerrain()`。
- 实体来自 `world.spatial(dimension).inRect(bounds)`，即"**占用格**与矩形相交"的实体（`GridSpatialIndex.inRect` 用 `entity.occupiedTiles().intersects(rect)`），再过滤掉 `!isAlive()`，最后**按 `EntityId` 排序**。
- `loadedChunks` 在采集这一刻用 `List.copyOf(map.loadedChunks())` 拷一份——是**那张图**的区块，不是别的图的。
- 时间换算率由构造时确定：两参构造器用公开常量 `SnapshotFactory.DEFAULT_TICKS_PER_SECOND = 20`（与 `OcRtsEngineBuilder` 的默认一致），三参构造器用你给的速率；`ticksPerSecond()` 可以读回来。引擎走的是三参构造器，所以 `snapshot.elapsedSeconds()` 与 `engine.elapsedSeconds()` 相等。

## 三、`WorldSnapshot`

`WorldSnapshot` 是一个 **final class（不是 record）**；字段：`dimension`、`dimensionName`、`tick`、`elapsedSeconds`、`ticksPerSecond`、`bounds`、`terrain`、`fallbackTerrain`、`entities`、`byId`、`loadedChunks`。构造器是 public（可以让工具自己造一份合成快照）：

```java
// 规范构造器：维度身份打头，接着才是时间与范围
public WorldSnapshot(DimensionId dimension, String dimensionName, long tick, int ticksPerSecond,
        TileRect bounds, TerrainType[][] terrain, TerrainType fallbackTerrain,
        List<EntitySnapshot> entities, List<ChunkPos> loadedChunks);

// 兼容构造器（7 参）：手工拼快照用，自动补 DimensionId.overworld() 与 DimensionProfile.earth().displayName()
public WorldSnapshot(long tick, int ticksPerSecond, TileRect bounds, TerrainType[][] terrain,
        TerrainType fallbackTerrain, List<EntitySnapshot> entities, List<ChunkPos> loadedChunks);
```

两个构造器都会校验地形行数与 `bounds` 匹配、每格非 null、`ticksPerSecond > 0`；规范构造器还会校验 `dimension` 与 `dimensionName` 非 null。

| 方法 | 说明 |
| --- | --- |
| `dimension()` | 这个快照描述哪张图（`DimensionId`）；**坐标是这张图自己的** |
| `dimensionName()` | 该维度档案的 `displayName()`，给图例、小地图与天幕染色用（键给代码与存档，名字给人看） |
| `tick()` | 这个快照对应的模拟 tick |
| `elapsedSeconds()` | `tick / (float) ticksPerSecond` |
| `ticksPerSecond()` | 由产生它的 `SnapshotFactory` 写入：引擎传自己的速率，手工两参构造时是 `20` |
| `bounds()` | `TileRect`（含首尾），`minX/minY/maxX/maxY` 是 record 分量 |
| `width()` / `height()` | `2 * radius + 1` |
| `entities()` | 不可变 `List<EntitySnapshot>`，按 `EntityId` 升序 |
| `entityCount()` / `unitCount()` / `buildingCount()` | 计数；`buildingCount() = entityCount() - unitCount()` |
| `loadedChunks()` | `List<ChunkPos>`，采集时**该维度**在内存里的区块 |
| `terrainAt(int x, int y)` | 界内返回拷贝里的地形，界外返回 `fallbackTerrain()` |
| `fallbackTerrain()` | 通常是 content 的 void 地形 |
| `entity(EntityId)` / `requireEntity(EntityId)` / `contains(EntityId)` | 按 id 查；`requireEntity` 找不到抛 `NoSuchElementException` |
| `entitiesOf(PlayerId)` | 某玩家的实体，仍按 id 排序 |
| `selectedEntities()` | 快照里 `selected` 为 true 的实体 |
| `toString()` | `WorldSnapshot{moon 'Moon', tick N (X.XXs), [bounds], E entities, C chunks}` |

除新增的 `dimension()` / `dimensionName()` 与打头的两个构造器参数外，**所有旧访问器都没变**。`entities()` 与 `byId` 的顺序都是 id 升序，所以同一份世界的**绘制顺序、文本图、黄金文件都是可复现的**，即使世界内部是按空间索引顺序吐实体的。

## 四、`EntitySnapshot` 逐字段

record 的 21 个分量，顺序即声明顺序：

| # | 字段 | 类型 | 含义 |
| --- | --- | --- | --- |
| 1 | `id` | `EntityId` | 实体身份，排序键 |
| 2 | `typeKey` | `ResourceKey` | 定义键，`toString()` 形如 `oc:soldier`，`path()` 是 `soldier` |
| 3 | `displayName` | `String` | 人类可读名；构造期 null 时回退为 `typeKey.path()` |
| 4 | `owner` | `PlayerId` | 所属玩家 |
| 5 | `ownerColorArgb` | `int` | 建议染色，`0xAARRGGBB` |
| 6 | `position` | `Vec2` | 实体中心的世界坐标（单位=格，可为小数） |
| 7 | `anchorTile` | `GridPos` | 画在哪个格子：单位脚下那格，建筑是**footprint 原点** |
| 8 | `radius` | `float` | 碰撞半径（格），选框与血条宽度用 |
| 9 | `healthFraction` | `float` | 剩余血量比例 `[0, 1]`。血量本身是 `Natural`（可以是天文数字），快照只携带比例，因为渲染要画的是条而不是数字 |
| 10 | `selected` | `boolean` | 是否被选中；**纯表现层标志，模拟从不读** |
| 11 | `unit` | `boolean` | true=单位，false=建筑 |
| 12 | `role` | `UnitRole` | 单位角色（`COMBAT`/`WORKER`/`SUPPORT`），建筑为 null |
| 13 | `spriteSetId` | `String` | 动画集 id，可能为 null |
| 14 | `clipId` | `String` | 当前 clip，可能为 null |
| 15 | `frameIndex` | `int` | 当前帧在 sheet 里的下标，解析不到时 0 |
| 16 | `facing` | `float` | 朝向弧度，0 指东、逆时针为正 |
| 17 | `spriteFrame` | `SpriteFrame` | **要画的那一帧**；没有动画时为 null |
| 18 | `moving` | `boolean` | 单位当前是否有去向（建筑的 `Motion.isMoving()` 不适用，恒 false） |
| 19 | `currentOrderName` | `String` | 当前订单的 `name()`，没有订单为 null |
| 20 | `orderCount` | `int` | 订单总数（含当前），建筑恒 0 |
| 21 | `carrying` | `ItemBundle` | 单位是 `cargo().contents()`，建筑是 `inventory().contents()`，null 回退 `ItemBundle.EMPTY` |

构造期校验：`id`、`typeKey`、`position`、`anchorTile` 为 null 时抛 `IllegalArgumentException`。派生方法只有四个：

```java
entity.hasSprite();     // spriteFrame != null
entity.isDead();        // healthFraction <= 0f；快照只含活实体，所以实践中恒为 false
entity.isCarrying();    // !carrying.isEmpty()
entity.roleOrNull();    // 等同于 role()
```

## 五、`AsciiMapRenderer`：一个完整的消费示例

`engine.render(center, radius)` 就是 `new AsciiMapRenderer().render(engine.snapshot(center, radius))`。它存在的意义是**当验收测试**：凡是真实渲染需要的数据（位置、归属、朝向、动画帧、选中、血量）缺失，它就印不出来。

- 网格是 `char[bounds.height()][bounds.width()]`，**y 轴向下**：第一行是 `bounds.minY()`。
- 地形字符按 `TerrainType.key().path()` 查表：`~` 水（`water`/`deep_water`/`shallow_water`）、`.` 草（`grass`/`grassland`/`meadow`）、`f` 森林、`^` 丘陵/山、`#` 岩石、`:` 沙、`,` 土、`_` 雪/冰、`=` 路、`+` 桥、`V` 虚空；其它地形一律 `%`（`AsciiMapRenderer.OTHER_SYMBOL`），拿不到地形的格子是 `?`（`UNKNOWN_SYMBOL`）。
- 实体画在地形之上，位置取 `anchorTile`：单位给角色首字母（`C`/`W`/`S`），建筑给 `typeKey` 路径的首字母大写；**选中是小写，未选是大写**，所以不开颜色也能看出选择。
- 建筑只写 footprint 原点那一格，其余格保持地形。
- 类的注释明确写着：它消费的快照只含活着的实体，所以**永远不会画出尸体**。
- 最后一行是图例，**以维度打头**，然后是 tick、秒数、实体数与分类数、实际落笔数、瓦片范围、区块数，以及本图真正用到的地形符号：

  ```text
  dimension overworld (Earth) | tick 2 (0.10s) | entities 3 (2 units, 1 buildings), drawn 3 | tiles 49x49 (-24,-24)..(24,24) | chunks 49 | terrain: . oc:grass | entities: C/W/S units, selected lower case, buildings first letter of their type
  ```

  维度必须打头：每张图有自己的地形与自己的 `(0, 0)`，没有这一段的两次输出会在终端里长得一样，diff 起来像是模拟变了而不是换了张地图。
- 输出确定：每个瓦片恰好一个字符、每行宽度相同、没有行尾空白，可以直接当黄金文件 diff。

## 六、用 `SpriteFrame` 画一个单位（接口骨架）

不引入任何图像库，渲染层自己定接口：

<!-- 示意：非编译验证片段 -->
```java
interface TextureCache {
    TextureHandle load(String imagePath);              // "sprites/soldier.png"
}

interface Renderer2D {
    void drawRegion(TextureHandle texture, TileRect pixelRect, Vec2 worldPos,
                    float pivotX, float pivotY, int tintArgb);
    void drawRect(Vec2 center, float widthTiles, float heightTiles, int argb);
}

void drawEntities(WorldSnapshot shot, VisualLibrary library, TextureCache cache, Renderer2D target) {
    for (EntitySnapshot entity : shot.entities()) {
        if (!entity.hasSprite()) {
            target.drawRect(entity.position(), entity.radius() * 2f, entity.radius() * 2f,
                    entity.ownerColorArgb());
            continue;
        }
        SpriteFrame frame = entity.spriteFrame();
        SpriteSheet sheet = library.sheet(frame.sheetId());
        SpriteFrame precise = sheet.frame(frame.index(), frame.pivotX(), frame.pivotY());
        target.drawRegion(cache.load(sheet.imagePath()), precise.pixelRect(),
                entity.position(), precise.pivotX(), precise.pivotY(), entity.ownerColorArgb());
    }
}
```

`frame.pixelRect()` 与 `frame.pivotX()/pivotY()` 已经足够画对位置；想做整数对齐时可以拿 `sheet.frame(index, pivotX, pivotY)` 重新构造一份（帧数据是纯值，重建没有副作用）。

## 七、视野与区块流式

引擎没有相机，所以**由你告诉它"玩家在看哪"**：

```java
engine.focus(new GridPos(40, 25));                             // 保持当前半径，只挪关注点（默认维度）
engine.streaming().focus(new Vec2(40.5f, 25.5f), 3);           // 挪关注点并改半径（单位：区块，默认维度）
engine.focus(DimensionId.of("moon"), new Vec2(0f, 0f), 3);     // 点名某张图：挪关注点并改它的半径
engine.streaming().radiusChunks();                             // 当前半径
engine.streaming().loadedChunks();                             // Set<ChunkPos>，此刻在内存里的
engine.streaming().loadArea(new GridPos(0, 0), new GridPos(63, 63)); // 强制预载，绕过半径
engine.loadedBounds();                                         // TileRect，或 null（没有区块时）
engine.dimension(DimensionId.of("moon")).focus(new Vec2(0f, 0f), 3); // 等价的 Dimension 写法
```

几条必须记住的事实：

1. **`focus` 只是设定关注点，区块是慢慢长出来的**。相位 100 的 `ChunkStreamer` 每 tick 最多做 `ChunkStreamer.DEFAULT_CHUNKS_PER_TICK = 4` 次区块切换（先卸载、后加载），而且这个预算是**所有维度共享**的。视野半径 2 是 5x5=25 个区块，跳一次焦点后大约要十几个 tick 才铺满——自校验场景每次跳焦点后都 `engine.tick(30)` 就是为此。多维度下每张图有自己的焦点，所以"看月球"不会把火星的区块带走。
2. **`snapshot` 会按需生成区块**。`capture` 对矩形内每一格调**那张图**的 `terrainAt(x, y)`，`ChunkedMap` 遇到不在内存的区块会 `generator.generate(...)` 并计入 `generatedChunkCount()`，同时把它塞进内存（`maxLoadedChunks` 默认 0 = 不限；之后要靠 streamer 把它们当作"半径外"逐步淘汰）。所以**别拿大半径快照去扫还没流式加载的区域**：它不会报错，只会很慢，并且把内存和生成计数顶上去。
3. **`loadedChunks()` 是"哪些地形算数"的权威**。快照给的 `terrainAt` 对界外格返回 `fallbackTerrain()`，而 `WorldSnapshot.loadedChunks()` 是采集瞬间**该维度**的真实内存状态。渲染层可以据此把 `bounds()` 之外的格子标成"未知/黑幕"，或至少避免画一大堆 fallback 地形。
4. 半径与区块大小在构造期定：`OcRtsEngine.builder(content).chunkSize(16).viewRadiusChunks(3)`（默认 `chunkSize = 16`、`viewRadiusChunks = 4`）。`engine.streaming().focus(v2, radiusChunks)` 改的是流式半径，`ChunkStreamer` 自身持有的半径在 build 时就固定了——想彻底换一套流式节奏，就在 `OcRtsEngineBuilder` 上配。
5. **旧的 `focus(...)` 只动默认维度**。`engine.focus(GridPos)` / `engine.focus(Vec2)` 跟随 `world.setDefaultDimension(...)` 指向的那张图；要动别的图就得用 `engine.focus(DimensionId, Vec2, int)`（或 `Dimension.focus(...)`），前者还能顺手改那张图的半径。

## 八、UI 数据从哪里取

| UI 元素 | 数据源 |
| --- | --- |
| 选中环 / 高亮 | `EntitySnapshot.selected()`（回写用 `Unit.setSelected(boolean)`，`Unit.isSelected()` 是权威） |
| 血条 | `healthFraction()`（`[0,1]`，宽度=`radius()*2` 的合理倍数）；注意当前引擎没有伤害系统，值恒为 1 |
| 朝向/箭头 | `facing()` 弧度；需要八向就用 `Direction.fromAngle(facing())` |
| 正在做什么 | `clipId()` + `frameIndex()`（例如 `move`/`carry_move` 判别搬运中） |
| 当前订单名 | `currentOrderName()`（来自 `OrderQueue.current().name()`，如 `move`/`haul`），`orderCount()` 给队列长度 |
| 队伍颜色 | `ownerColorArgb()`；玩家对象是 `engine.players()` 里的 `Player.id()` / `name()` / `colorArgb()` |
| 名字/悬浮提示 | `displayName()`、`typeKey().path()` |
| 携带物 / 库存 | `carrying()`（`ItemBundle`，`isEmpty()`/`amount(ItemType)`/`components()`） |
| 建筑占地 | `anchorTile()` + 类型尺寸（快照不直接给 footprint 宽高；`anchorTile` 是原点） |
| 是否还在施工 | 用 `AnimationClips.BUILD.equals(entity.clipId())` 判，或从 `typeKey()` 查 content 的 `buildTime` 自己算 |

反馈操作（框选、点地移动、下生产单）走的是引擎命令面，不经过快照：`engine.commands().move(unit, tile)` / `moveGroup(units, tile, FormationShape.BOX)`、`engine.production().enqueue(building, recipe, count)`，再让下一次 `tick()` 把结果带进下一份快照。

## 九、快照是拷贝，可以跨线程

- 地形在构造期逐行 `clone()`，实体是 record，字段全是 `Vec2`/`GridPos`/`ItemBundle`/`SpriteFrame` 这类不可变值，`entities`/`byId`/`loadedChunks` 都是 `Collections.unmodifiable*` 包装的副本。**`WorldSnapshot` 不持有任何指向活 `World` 的引用**。
- 因此"模拟线程 `tick()` → `snapshot()` → 把 `WorldSnapshot` 丢给绘制线程"是安全的：世界继续跑不会改到那一帧。
- 反过来不成立：**引擎本身是单线程、无锁的**。不要一边 `tick()` 一边在别的线程读 `world.units()`、`entity.visual()` 或 `Player.logistics()`——同一份快照只能证明它自己是稳定的，不能保护活世界。
- 想双缓冲/回放，就每 tick 存一份 `WorldSnapshot`（`tick()` 字段就是时间轴），或者用 public 的 `WorldSnapshot` 构造器自己拼一份合成快照做 UI 单测。

## 关键 API

| API | 说明 |
| --- | --- |
| `OcRtsEngine.snapshot(GridPos center, int radiusTiles)` | 采集默认维度的一份 `WorldSnapshot`（工厂已带引擎自己的 tick 率） |
| `OcRtsEngine.snapshot(DimensionId, GridPos center, int radiusTiles)` | 采集指定维度的一份 `WorldSnapshot`，`center` 是那张图上的格 |
| `OcRtsEngine.render(GridPos center, int radiusTiles)` | 同上 + `AsciiMapRenderer`，返回文本（**只有默认维度**这个重载） |
| `OcRtsEngine.dimensions()` / `dimension(DimensionId)` / `defaultDimensionId()` | 维度清单、按 id 取一张图、单地图 API 指向哪张图 |
| `SnapshotFactory(World, VisualLibrary)` | 手动采集的简便构造器，速率取 `DEFAULT_TICKS_PER_SECOND = 20` |
| `SnapshotFactory(World, VisualLibrary, int ticksPerSecond)` | 速率与引擎一致时用这个；`ticksPerSecond <= 0` 抛 `IllegalArgumentException` |
| `SnapshotFactory.capture(DimensionId, GridPos, int)` | 规范入口：该维度的地形、空间索引与档案展示名 |
| `SnapshotFactory.capture(GridPos, int)` / `world()` / `library()` / `ticksPerSecond()` | 默认维度的旧入口与读回配置 |
| `WorldSnapshot.dimension` / `dimensionName` | 这张快照是哪张图、展示名叫什么 |
| `WorldSnapshot.tick/elapsedSeconds/ticksPerSecond/bounds/width/height` | 时间与范围 |
| `WorldSnapshot.entities/entityCount/unitCount/buildingCount/loadedChunks` | 内容清单 |
| `WorldSnapshot.terrainAt/fallbackTerrain/entity/requireEntity/contains/entitiesOf/selectedEntities` | 查询 |
| `EntitySnapshot` 21 个 record 分量 | 见第四节表格 |
| `EntitySnapshot.hasSprite/isDead/isCarrying/roleOrNull` | 派生方法 |
| `AsciiMapRenderer.render(WorldSnapshot)` / `symbolOf(EntitySnapshot)` / `symbolOf(TerrainType)` | 文本渲染与字符规则（图例行以 `dimension <键> (<展示名>)` 打头）；常量 `UNKNOWN_SYMBOL='?'`、`OTHER_SYMBOL='%'` |
| `OcRtsEngine.focus(GridPos)` / `focus(Vec2)` / `focus(DimensionId, Vec2, int)` / `streaming()` / `loadedBounds()` | 相机与流式；前两个只动默认维度，第三个点名维度并可改半径 |
| `MapStreaming.focus(Vec2, int)` / `focus(GridPos, int)` / `focus()` / `radiusChunks()` / `loadedChunks()` / `loadArea(GridPos, GridPos)` / `unload(ChunkPos)` | 区块控制面（`engine.streaming()` 是默认维度的那一份） |

## 常见坑

1. **`EntitySnapshot.spriteFrame` 可能是 null**。只有 `visual instanceof UnitAnimator` 时才会解析帧，所以"没配动画集"或"实体的 spriteSetId 没有对应 set"的实体一律 null，渲染层必须先 `hasSprite()` 判断。
2. **快照与引擎的秒数现在是同一套**。`OcRtsEngine` 在 `build()` 时把自己的 tick 率交给 `SnapshotFactory`，所以 `snapshot.elapsedSeconds()` 等于 `engine.elapsedSeconds()`。只有你自己 `new SnapshotFactory(world, library)`（两参）而引擎不是 20 tick/秒时，时间才会按默认 20 算错——这时请用三参构造器 `new SnapshotFactory(world, library, engine.ticksPerSecond())`。
3. **`entityCount()` 可能大于实际画出来的数量**。`inRect` 按"占用格相交"取实体，建筑的 footprint 压到矩形里、但 `anchorTile`（原点）在矩形外时，`AsciiMapRenderer` 会跳过它，图例里的 `drawn` 数就会小于 `entityCount`。自己写渲染时要么以 `anchorTile` 判界，要么按 footprint 判。
4. **`isDead()` 在实践中永远是 false**。`SnapshotFactory` 只采集活着的实体（尸体停在不动的旧位置，还会把"屏幕内单位数"算多），而 `World.destroy(...)` 会顺手 `kill()` 并在该 tick 末尾 `flush()` 掉它；`EntitySnapshot.isDead()` 的 javadoc 现在也是这么写的。要做尸体演出，请订阅 `Events.EntityDestroyed`，在引擎外自己维护一份短命的特效列表。
5. **别在快照范围外读地形**。`terrainAt` 对界外格返回 `fallbackTerrain`（通常是虚空），不是"上一个已知地形"；同时 `capture` 本身会为矩形内每一格触发生成，大矩形 = 真金白银的生成开销。
6. **相机的"有效范围"和 `radiusTiles` 不是一回事**。`focus` 决定哪些区块在内存，`snapshot` 的 `radiusTiles` 只是你要拷多大；拷得比视野大就全是按需生成，拷得比视野小则白白丢掉了已经加载的地形。
7. **选中的权威在 `Unit` 上**，`Unit.setSelected(boolean)` 由表现层写，`EntitySnapshot.selected()` 是它在采集瞬间的拷贝；改了之后要等下一份快照才看得到。
8. **`WorldSnapshot` 的 public 构造器会校验地形尺寸**：`terrain.length` 必须等于 `bounds.height()`、每行长度等于 `bounds.width()`、每格非 null，否则抛 `IllegalArgumentException`。自己拼合成快照做 UI 测试时容易在这里踩空。
9. **不要在渲染里改世界**。快照是给你读的，唯一的写入口是命令面（`commands()` / `production()`）；顺着 `EntitySnapshot` 去 `world.entity(id)` 拿活对象再改它，等于把模拟的确定性交给绘制线程。
10. **快照默认只拍一张图**。`capture(GridPos, int)` / `engine.snapshot(center, radius)` / `engine.render(center, radius)` 都是**默认维度**的；多维度世界想看别的图必须显式传 `DimensionId`。忘了传的表现是"月球上的单位不见了"，其实画的是地球的同一片坐标。
11. **别用一张图的 `(0, 0)` 解释另一张图的实体**。`WorldSnapshot` 里的 `position` / `anchorTile` 是**那张图**的坐标；把月球快照里的实体拿去和地球的坐标比距离、或拿去 `world.spatial()` 查邻居，就会得到幻影（见 [13-dimensions.md](13-dimensions.md) 的 FAQ 与常见坑）。
12. **兼容构造器补的是 `overworld`**。手工拼快照的 7 参构造器会把 `dimension` 填成 `DimensionId.overworld()`、`dimensionName` 填成 `DimensionProfile.earth().displayName()`；只是给 UI 单测用的占位，从活世界采的快照一定要走 `SnapshotFactory`，否则图例会骗人。

## 相关文档

- [07-sprites-and-animation.md](07-sprites-and-animation.md) —— `spriteFrame` 是怎么被算出来的
- [03-map-and-pathfinding.md](03-map-and-pathfinding.md) —— 区块惰性生成与流式加载的细节
- [13-dimensions.md](13-dimensions.md) —— 按维度的快照、`dimension()` / `dimensionName()`、每张图自己的焦点与坐标
- [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md) —— `carrying` / `currentOrderName` 背后的物流与生产状态
- [10-cookbook.md](10-cookbook.md) —— 按任务查做法
- [11-limits-and-faq.md](11-limits-and-faq.md) —— 预留但未接通的字段（战斗、死亡流程）

## 相关源码

- [SnapshotFactory.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/SnapshotFactory.java)
- [WorldSnapshot.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/WorldSnapshot.java)
- [EntitySnapshot.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/EntitySnapshot.java)
- [AsciiMapRenderer.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/AsciiMapRenderer.java)（图例行以维度打头）
- [Events.java](../src/main/java/io/github/sob1234509876/oc/rts/event/Events.java)（`EntityDestroyed`，自建尸体特效列表时订阅它）
- [OcRtsEngine.java](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngine.java)（`snapshot` / `render` / `focus` / `dimensions` / `dimension` / `defaultDimensionId` / `loadedBounds`）
- [World.java](../src/main/java/io/github/sob1234509876/oc/rts/World.java)、[Dimension.java](../src/main/java/io/github/sob1234509876/oc/rts/dimension/Dimension.java)（快照按维度取地图、索引与档案展示名）
- [OcRtsEngineBuilder.java](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngineBuilder.java)（`ticksPerSecond` / `chunkSize` / `viewRadiusChunks` / `visuals` 的默认值，以及把 tick 率传给快照工厂）
- [MapStreaming.java](../src/main/java/io/github/sob1234509876/oc/rts/map/MapStreaming.java) 与 [ChunkStreamer.java](../src/main/java/io/github/sob1234509876/oc/rts/map/ChunkStreamer.java)（每 tick 4 次区块切换）
- [GridSpatialIndex.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/GridSpatialIndex.java)（`inRect` 按占用格相交）
- 行为证据（`selfCheck` 源集，运行任务 `:projects:oc:oc-rts-engine:scenario`）：[RtsScenario.java](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java) 第 6 节
