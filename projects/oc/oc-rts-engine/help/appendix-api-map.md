# 附录：API 地图与速查

> 一页纸找到正确的类型：从"我想做 X"跳到对应文档，再按包查全部 public 类型、常量和三个门面类的方法。

## 这一篇讲什么

- 这是一份**索引**，不是教程：讲解在 01~11 篇里，这里只回答"该看哪一篇、该碰哪个类型"。
- 类型清单是**扫描 `src/main/java` 下全部 95 个源文件**得到的，共 113 个 public 类型（含嵌套 public 类型）。包私有类型（如 `economy.ReservationPool`、`logistics.HaulOrder.Phase`、各类 `Draft`）不在清单里，因为它们不是导出 API。
- 每个包一节，逐行给出：类型 → 一句话作用 → 关键方法/字段 → 详见的文档。
- 常量值全部从源码读出，包含默认参数与枚举成员；`throws` 列只写源码里真实存在的校验与异常。
- 约定：`Foo#bar()` 表示实例方法，`Foo.bar()` 表示静态方法，`Foo.CONST` 表示常量。所有类型都在模块 `oc.projects.oc.rts.engine` 的 14 个导出包里。

## 我想做 X → 看哪篇

| 我想做 | 看这篇 | 关键类型 |
| --- | --- | --- |
| 给引擎加一种新地形 | [02](02-content-and-items.md) / [03](03-map-and-pathfinding.md) | `ContentRegistry.Builder#terrain`、`TerrainType` |
| 给引擎加一种新物品/资源 | [02](02-content-and-items.md) | `ContentRegistry.Builder#item`、`ItemType` |
| 给引擎加一种新兵种 | [02](02-content-and-items.md) | `ContentRegistry.Builder#unit`、`UnitType`、`UnitRole` |
| 给引擎加一种新建筑 | [02](02-content-and-items.md) | `ContentRegistry.Builder#building`、`BuildingType` |
| 加一条新配方 | [02](02-content-and-items.md) / [06](06-economy-logistics-and-production.md) | `ContentRegistry.Builder#recipe`、`Recipe` |
| 让配方产出单位而不是物品 | [06](06-economy-logistics-and-production.md) | `Recipe#isUnitRecipe`、`ContentRegistry.Builder#unitOutput` |
| 让一辆车依次做多件事（走队列） | [04](04-units-orders-and-movement.md) | `OrderQueue#push`、`Order#tick` |
| 让一组单位排成楔形 | [05](05-formations.md) | `CommandService#moveGroup`、`FormationShape#WEDGE` |
| 拖拽时实时改队形落点 | [05](05-formations.md) | `CommandService#updateFormationAnchor`、`FormationMoveOrder#updateAnchor` |
| 把材料送进工厂 | [06](06-economy-logistics-and-production.md) | `Logistics#requestDelivery`、`DeliveryJob` |
| 让工厂产出物品并送回仓库 | [06](06-economy-logistics-and-production.md) | `ProductionJob#pendingOutput`、`Logistics#deposit`、`Logistics#store` |
| 不要搬运工，用定时传送代替 | [06](06-economy-logistics-and-production.md) | `OcRtsEngineBuilder#directDelivery`、`HaulingSystem#directDelivery` |
| 换掉地形生成算法 | [03](03-map-and-pathfinding.md) | `ChunkGenerator`、`OcRtsEngineBuilder#generator` |
| 调噪声地形的海平面/森林线/山线 | [03](03-map-and-pathfinding.md) | `NoiseSettings#withSeaThreshold`、`NoiseSettings#withPaths` |
| 让开局附近一定是可建陆地 | [03](03-map-and-pathfinding.md) | `NoiseSettings#withOrigin`、`NoiseChunkGenerator#guaranteedLandRadius` |
| 把编辑过的区块存到磁盘 | [03](03-map-and-pathfinding.md) | `ChunkStore#save`、`OcRtsEngineBuilder#chunkStore` |
| 限制内存里同时保留多少区块 | [03](03-map-and-pathfinding.md) | `ChunkedMap#setMaxLoadedChunks`、`ChunkStreamer#setChunksPerTick` |
| 调整寻路预算（每 tick 请求数/节点数） | [03](03-map-and-pathfinding.md) / [11](11-limits-and-faq.md) | `OcRtsEngineBuilder#pathBudget`、`PathService` |
| 单次寻路更省或更彻底 | [03](03-map-and-pathfinding.md) | `PathOptions.DEFAULT/FAST/EXHAUSTIVE` |
| 调整分离避让强度 / 卡位重寻路 | [04](04-units-orders-and-movement.md) / [11](11-limits-and-faq.md) | `SteeringSettings`、`MovementSystem#settings` |
| 让单位没接到命令就原地不动 | [04](04-units-orders-and-movement.md) | `HoldOrder`、`CommandService#hold` |
| 给单位加一个自定义订单 | [04](04-units-orders-and-movement.md) / [10](10-cookbook.md) | `Order`、`OrderContext`、`OrderQueue#push` |
| 把动画接到真实图集 | [07](07-sprites-and-animation.md) | `VisualLibrary.Builder`、`SpriteSheet`、`SpriteFrame` |
| 做八方向行走动画 | [07](07-sprites-and-animation.md) | `VisualLibrary.ClipBuilder#direction`、`AnimationSet#clip(String,Direction)`、`Direction` |
| 用快照写渲染循环 | [08](08-snapshots-and-rendering.md) | `SnapshotFactory#capture`、`WorldSnapshot`、`EntitySnapshot` |
| 把世界画成 ASCII 看看效果 | [08](08-snapshots-and-rendering.md) / [10](10-cookbook.md) | `AsciiMapRenderer#render`、`OcRtsEngine#render` |
| 定位"生产卡住"的原因 | [06](06-economy-logistics-and-production.md) / [10](10-cookbook.md) | `ProductionState`、`ProductionJob#state`、`Events.ProductionStateChanged` |
| 定位"单位不动"的原因 | [04](04-units-orders-and-movement.md) / [10](10-cookbook.md) | `Motion#isArrived`、`Motion#isPathPending`、`PathService#failedLastTick` |
| 让模拟可复现（同种子同结果） | [09](09-architecture-and-tick-pipeline.md) | `DeterministicRandom`、`OcRtsEngineBuilder#seed`、`SystemPipeline` |
| 调整 tick 速率 | [09](09-architecture-and-tick-pipeline.md) | `OcRtsEngineBuilder#ticksPerSecond`、`Tick` |
| 监听"单位生成/物品产出/区块加载" | [09](09-architecture-and-tick-pipeline.md) / [10](10-cookbook.md) | `EventBus#subscribe`、`Events.*` |
| 把自己的系统插进 tick 管线 | [09](09-architecture-and-tick-pipeline.md) / [10](10-cookbook.md) | `SimulationSystem`、`SystemPipeline#add` |
| 查某个点周围有哪些单位 | [04](04-units-orders-and-movement.md) | `SpatialIndex#inRadius`、`GridSpatialIndex` |
| 建楼前检查位置能不能放 | [03](03-map-and-pathfinding.md) / [06](06-economy-logistics-and-production.md) | `World#canPlace`、`TerrainMap#isBuildable` |
| 把仓库容量改大 | [02](02-content-and-items.md) / [06](06-economy-logistics-and-production.md) | `BuildingType#storageSlots`、`Inventory#unitLimit`、`Inventory.UNLIMITED` |
| 限制某个仓库只收矿石 | [02](02-content-and-items.md) / [06](06-economy-logistics-and-production.md) | `Inventory` 构造器的 `Predicate<ItemType>`、`BuildingType#storageTags` |
| 让建筑像工厂一样能跑配方 | [06](06-economy-logistics-and-production.md) | `BuildingType#producerTags`、`Recipe#producerTags`、`ProductionSystem#availableRecipes` |
| 让新生产的单位自动走到集结点 | [06](06-economy-logistics-and-production.md) | `Building#rallyPoint`、`Building#setRallyPoint` |

## 按包的类型清单

### `io.github.sob1234509876.oc.rts`（根包）

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `OcRtsEngine` | 一场比赛的门面：世界 + 系统管线 + 各服务 | `world()`、`tick()`、`commands()`、`production()`、`snapshot()`、`describe()` | [01](01-getting-started.md) |
| `OcRtsEngineBuilder` | 链式装配 `OcRtsEngine`，每个子系统都有可用默认值 | `seed()`、`ticksPerSecond()`、`generator()`、`chunkStore()`、`visuals()`、`player()`、`build()` | [01](01-getting-started.md) |
| `Player` | 一个参赛方：拥有实体，其仓库构成一张后勤网 | `id()`、`colorArgb()`、`team()`、`logistics()`、`attachLogistics()` | [06](06-economy-logistics-and-production.md) |
| `PlayerId` | 玩家身份，全场比赛稳定 | `of(int)`、`value()` | [06](06-economy-logistics-and-production.md) |
| `World` | 模拟状态本体：区块地形、实体、玩家与各服务 | `spawnUnit()`、`spawnBuilding()`、`canPlace()`、`destroy()`、`flush()`、`spatial()`、`events()` | [09](09-architecture-and-tick-pipeline.md) |

### `...oc.rts.content`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `ContentRegistry` | 一份游戏的全部定义目录，按 `ResourceKey` 寻址 | `namespace()`、`terrain()`、`item()`、`unit()`、`building()`、`recipe()`、`recipesFor()`、`builder()` | [02](02-content-and-items.md) |
| `ContentRegistry.Builder` | 流式目录构建器，当前定义是每个属性调用的目标 | `terrain()`、`item()`、`unit()`、`building()`、`recipe()`、`storage()`、`warehouse()`、`producer()`、`build()` | [02](02-content-and-items.md) |
| `TerrainType` | 一种地形的定义 | `passable()`、`buildable()`、`moveCost()`、`spriteId()`、`mapColor()` | [02](02-content-and-items.md) |
| `ItemType` | 一种物品/资源的定义 | `key()`、`maxStack()`、`hasTag()`、`hasAnyTag()` | [02](02-content-and-items.md) |
| `ItemBundle` | 不可变、有序的物品多重集（成本、产出、库存通用） | `EMPTY`、`of()`、`plus()`、`minus()`、`minusClamped()`、`components()`、`builder()` | [02](02-content-and-items.md) |
| `ItemBundle.Amount` | 一条 (物品, 数量) 分量 | `item()`、`amount()` | [02](02-content-and-items.md) |
| `ItemBundle.Builder` | 累加式构建 `ItemBundle` | `add()`、`addAll()`、`build()` | [02](02-content-and-items.md) |
| `UnitType` | 一种兵种的定义 | `role()`、`maxHealth()`、`speed()`、`radius()`、`cost()`、`cargoCapacity()`、`canHaul()`、`canAttack()` | [02](02-content-and-items.md) |
| `UnitRole` | 兵种大类，供生产和后勤判断用途 | `COMBAT`/`WORKER`/`SUPPORT`、`id()` | [02](02-content-and-items.md) |
| `BuildingType` | 一种建筑的定义：占地、成本、血、仓储、生产标签 | `sizeX()`/`sizeY()`、`footprint()`、`storageSlots()`、`isWarehouse()`、`producerTags()`、`buildTimeTicks()` | [02](02-content-and-items.md) |
| `Recipe` | 一条生产配方：消耗什么、产出什么、要多久 | `inputs()`、`itemOutputs()`、`unitOutput()`、`isUnitRecipe()`、`durationTicks()`、`producerTags()` | [06](06-economy-logistics-and-production.md) |
| `ResourceKey` | 带命名空间的内容标识，如 `oc:iron_ore` | `namespace()`、`path()`、`of(String)`、`of(ns, path)` | [02](02-content-and-items.md) |

### `...oc.rts.core`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `Vec2` | 世界单位（1 单位 = 1 格）的不可变二维向量 | `ZERO`、`plus()`、`minus()`、`scaled()`、`movedTowards()`、`normalized()`、`angle()`、`tile()` | [04](04-units-orders-and-movement.md) |
| `GridPos` | 一格地图瓦片的整数坐标（世界无界，坐标可为负） | `plus()`、`neighbors4()`、`neighbors8()`、`manhattanDistance()`、`chebyshevDistance()`、`center()` | [03](03-map-and-pathfinding.md) |
| `ChunkPos` | 区块坐标（`chunkSize × chunkSize` 的方块） | `of(GridPos,int)`、`plus()`、`chebyshevDistance()`、`tiles(int)` | [03](03-map-and-pathfinding.md) |
| `TileRect` | 闭区间瓦片矩形（区块范围、建筑占地、区域查询） | `width()`、`height()`、`tiles()`、`contains()`、`intersects()`、`centered()`、`fromOrigin()` | [03](03-map-and-pathfinding.md) |
| `EntityId` | 实体身份，按创建顺序发放 | `of(long)`、`value()`、`compareTo()` | [09](09-architecture-and-tick-pipeline.md) |
| `Tick` | 一个固定步长；引擎只按整 tick 前进 | `number()`、`ticksPerSecond()`、`deltaSeconds()`、`elapsedSeconds()`、`next()` | [09](09-architecture-and-tick-pipeline.md) |
| `Direction` | 八向罗盘，供精灵、队形和粗略朝向使用 | `dx()`、`dy()`、`id()`、`angle()`、`opposite()`、`fromAngle()`、`fromVector()` | [07](07-sprites-and-animation.md) |
| `DeterministicRandom` | splitmix64 确定性随机源，地形与散布都靠它 | `forStream(seed, stream)`、`nextInt()`、`nextFloat()`、`chance()`、`fork()`、`state()`/`setState()` | [09](09-architecture-and-tick-pipeline.md) |

### `...oc.rts.economy`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `Inventory` | 单个实体内部的物品存储；槽位数与总量两个独立上限，一格最多 `ItemType.maxStack()`（`usedSlots()` 按 `ceil(数量/maxStack)` 计） | `UNLIMITED`、`slotCount()`、`unitLimit()`、`accepts()`、`insert()`、`insertable()`、`capacityFor()`、`extractUpTo()`、`canAcceptAll()`、`usedSlots()`、`freeSlots()`、`isFull()` | [02](02-content-and-items.md) |
| `Logistics` | 一个玩家所有仓库合并成的可用库存 | `reserve()`、`requestDelivery()`、`deposit()`、`stored()`、`available()`、`warehousesAccepting()` | [06](06-economy-logistics-and-production.md) |
| `Logistics.Reservation` | 对仓库中某批物品的占用凭证（先占后用或退回） | `bundle()`、`source()`、`active()`、`commit()`、`release()` | [06](06-economy-logistics-and-production.md) |
| `LogisticsReservation` | `Reservation` 的默认实现，记录还没被搬走的余量 | `remaining()`、`allocationCount()`、`describe()` | [06](06-economy-logistics-and-production.md) |
| `WarehouseLogistics` | 默认后勤网：某玩家的全部仓库 + 占用登记 + 交付调度 | `of(Player)`、`pickupWarehouses()`、`pendingPickup()`、`pickUp()`、`attachHauling()`、`close()` | [06](06-economy-logistics-and-production.md) |

### `...oc.rts.entity`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `Entity` | 世界里的一切：身份、归属、位置、生命、动画状态 | `id()`、`owner()`、`position()`、`health()`、`isUnit()`、`asUnit()`、`visual()` | [09](09-architecture-and-tick-pipeline.md) |
| `Unit` | 可移动实体：士兵、工人、载具 | `type()`、`role()`、`motion()`、`orders()`、`cargo()`、`tile()`、`setSelected()` | [04](04-units-orders-and-movement.md) |
| `Building` | 带占地面积的静态实体：仓库、兵营、工厂 | `origin()`、`inventory()`、`entrance()`、`isConstructed()`、`advanceConstruction()`、`rallyPoint()` | [06](06-economy-logistics-and-production.md) |
| `Health` | 生命值小值对象，承接伤害与治疗 | `max()`、`current()`、`fraction()`、`damage()`、`heal()`、`restore()` | [09](09-architecture-and-tick-pipeline.md) |
| `SpatialIndex` | 宽阶段空间查询接口（不是位置感知的） | `inRadius()`、`inRect()`、`nearest()`、`insert()`/`update()`/`remove()` | [04](04-units-orders-and-movement.md) |
| `GridSpatialIndex` | 均匀网格实现，实体按小方格分桶 | `GridSpatialIndex(float cellSize)`（默认由 `World` 用 `4f` 构造）、`occupiedCellCount()` | [04](04-units-orders-and-movement.md) |

### `...oc.rts.event`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `EventBus` | 同步、确定性事件总线：发布事实，别的系统订阅 | `subscribe(Class, Consumer)`、`publish(Object)` | [09](09-architecture-and-tick-pipeline.md) |
| `EventBus.Subscription` | 取消监听的句柄 | `cancel()`、`active()` | [09](09-architecture-and-tick-pipeline.md) |
| `SimpleEventBus` | 默认实现：有序监听器表，无反射、无线程 | `subscribe()`、`publish()`、`listenerCount()` | [09](09-architecture-and-tick-pipeline.md) |
| `Events` | 事件 record 的容器类（只有嵌套 record） | — | [09](09-architecture-and-tick-pipeline.md) |
| `Events.EntitySpawned` | 实体被创建 | `entity`、`tick` | [09](09-architecture-and-tick-pipeline.md) |
| `Events.EntityDestroyed` | 实体被标记死亡（`flush()` 前发布） | `entity`、`tick` | [09](09-architecture-and-tick-pipeline.md) |
| `Events.DamageDealt` | 造成伤害 | `target`、`source`、`amount`、`tick` | [09](09-architecture-and-tick-pipeline.md) |
| `Events.ConstructionCompleted` | 工地完工 | `building`、`tick` | [06](06-economy-logistics-and-production.md) |
| `Events.ItemProduced` | 配方产出物品 | `player`、`building`、`items`、`recipe`、`tick` | [06](06-economy-logistics-and-production.md) |
| `Events.UnitProduced` | 配方产出单位 | `player`、`building`、`unit`、`recipe`、`tick` | [06](06-economy-logistics-and-production.md) |
| `Events.ProductionStateChanged` | 生产阶段变化 | `building`、`recipe`、`state`（`String`）、`tick` | [06](06-economy-logistics-and-production.md) |
| `Events.ChunkLoaded` | 区块被载入内存 | `pos`、`tick` | [03](03-map-and-pathfinding.md) |
| `Events.ChunkUnloaded` | 区块被移出内存 | `pos`、`tick` | [03](03-map-and-pathfinding.md) |
| `Events.OrderIssued` | 订单成为某单位的当前订单 | `unit`、`orderName`、`tick` | [04](04-units-orders-and-movement.md) |
| `Events.OrderFinished` | 订单进入终态 | `unit`、`orderName`、`state`（`OrderState`）、`tick` | [04](04-units-orders-and-movement.md) |

### `...oc.rts.formation`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `FormationPlanner` | 把一组单位摆成队形并决定谁站哪个槽 | `MIN_SPACING`、`DEFAULT_SPACING_SCALE`、`plan(...)`、`plan(..., facingRadians, ...)`、`slotOffsets(...)` | [05](05-formations.md) |
| `FormationPlan` | 编队结果：锚点、间距、槽位偏移、槽位对应的单位 | `slotCount()`、`slotOffset()`、`slotPositionFor()`、`slotFor()`、`unitInSlot()` | [05](05-formations.md) |
| `FormationShape` | 队形形状枚举 | `NONE`/`LINE`/`COLUMN`/`BOX`/`WEDGE`/`CIRCLE`、`id()`、`label()` | [05](05-formations.md) |

### `...oc.rts.logistics`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `DeliveryJob` | 一次运输任务：把一批货从仓库送进目标建筑缓冲 | `target()`、`bundle()`、`reservation()`、`state()`、`pendingTicks()`、`delivered()`、`missing()`、`hauler()` | [06](06-economy-logistics-and-production.md) |
| `DeliveryJob.State` | 运输任务状态 | `WAITING_HAULER`/`IN_TRANSIT`/`COMPLETED`/`FAILED`、`isPending()`、`isFinished()` | [06](06-economy-logistics-and-production.md) |
| `HaulingSystem` | 把任务变成真的搬运工（或定时传送） | `SYSTEM_ORDER`、`DEFAULT_TIMEOUT_TICKS`、`submit()`、`jobs()`、`pendingCount()`、`directDelivery()` | [06](06-economy-logistics-and-production.md) |
| `HaulOrder` | 一名搬运工跑一个任务的订单：取货→送达，按背包容积往返多趟 | `TIMEOUT_TICKS`、`job()`、`name()`（返回 `"haul"`）、`tick()` | [06](06-economy-logistics-and-production.md) |

### `...oc.rts.map`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `TerrainMap` | 逐格访问的地形接口，查询可能触发区块生成 | `terrainAt()`、`setTerrain()`、`isPassable()`、`isBuildable()`、`moveCost()`、`loadedChunks()`、`loadedBounds()` | [03](03-map-and-pathfinding.md) |
| `ChunkedMap` | 按需生成、部分驻留的分块地图（`TerrainMap` + `MapStreaming`） | `chunkSize()`、`chunk()`、`peekChunk()`、`setMaxLoadedChunks()`、`focus()`、`loadArea()`、`flushDirty()`、`unload()` | [03](03-map-and-pathfinding.md) |
| `Chunk` | 一块方形地形；世界生成、存储、流式加载的单位 | `filled()`、`terrainAt()`、`setTerrain()`、`isDirty()`/`markClean()`、`rawTiles()`、`fill()`、`count()` | [03](03-map-and-pathfinding.md) |
| `ChunkGenerator` | 区块地形生成接缝；必须是区块坐标的纯函数 | `generate(ChunkPos)`、`chunkSize()` | [03](03-map-and-pathfinding.md) |
| `FlatChunkGenerator` | 全平地生成器（测试与手工搭图的场景） | `DEFAULT_CHUNK_SIZE`、`terrain()` | [03](03-map-and-pathfinding.md) |
| `NoiseChunkGenerator` | 基于哈希值噪声的无尽、确定性地貌 | `DEFAULT_CHUNK_SIZE`、`terrainFor()`、`isLand()`、`elevation()`、`moisture()`、`guaranteedLandRadius()`、`resolvedPaths()` | [03](03-map-and-pathfinding.md) |
| `NoiseSettings` | 噪声地形调参：边界高度、湿度、原点保证、内容路径 | `DEFAULT_CHUNK_SIZE`、`DEFAULT_ORIGIN_ELEVATION`、`DEFAULT_ORIGIN_RADIUS`、`defaults(ns)`、`withSeaThreshold()`、`withOrigin()`、`withPaths()`、`amplitudeSum()` | [03](03-map-and-pathfinding.md) |
| `ChunkStore` | 区块持久化接缝：被卸载的编辑必须活得比内存久 | `load()`、`save()`、`exists()` | [03](03-map-and-pathfinding.md) |
| `InMemoryChunkStore` | 内存实现，测试/工具用 | `load()`、`save()`、`exists()`、`size()`、`clear()` | [03](03-map-and-pathfinding.md) |
| `ChunkStreamer` | 保持兴趣点附近区块驻留、丢掉其余的系统 | `ORDER`、`DEFAULT_CHUNKS_PER_TICK`、`setChunksPerTick()`、`loadNow()`、`radiusChunks()` | [03](03-map-and-pathfinding.md) |
| `MapStreaming` | 控制哪一部分世界留在内存（无相机时由调用方指定焦点） | `focus(Vec2,int)`、`focus(GridPos,int)`、`radiusChunks()`、`loadedChunks()`、`loadArea()`、`unload()` | [03](03-map-and-pathfinding.md) |
| `PathFinder` | 寻路接缝；相同起点/终点/选项必得同一条路 | `findPath(start, goal)`、`findPath(start, goal, options)`、`hasLineOfSight()`、`isReachable()` | [03](03-map-and-pathfinding.md) |
| `AStarPathFinder` | A* + 对角 + 拉绳平滑 + 固定扩张上限 | `AStarPathFinder(TerrainMap)`、`AStarPathFinder(TerrainMap, float minimumStep)`、`minimumMoveCost(ContentRegistry)`、`map()`、`findPath()`、`hasLineOfSight(Vec2,Vec2)`、`hasLineOfSight(GridPos,GridPos)` | [03](03-map-and-pathfinding.md) |
| `Path` | 寻路结果：从起点朝终点的有序路点 | `start()`、`goal()`、`waypoints()`、`destination()`、`waypointCount()`、`isEmpty()`、`length()` | [03](03-map-and-pathfinding.md) |
| `PathOptions` | 单次寻路的调参 | `DEFAULT`/`FAST`/`EXHAUSTIVE`、`maxExpansions()`、`allowDiagonal()`、`allowCornerCutting()`、`heuristicWeight()`、`withMaxExpansions()`、`withDiagonals()` | [03](03-map-and-pathfinding.md) |

### `...oc.rts.movement`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `Motion` | 单个单位的移动意图与转向状态机 | `setGoal()`、`isArrived()`、`isMoving()`、`waypoints()`、`currentWaypoint()`、`velocity()`、`facing()`、`stuckTicks()`、`repathCooldown()`、`setFormation()` | [04](04-units-orders-and-movement.md) |
| `MovementSystem` | 每 tick 移动所有单位：补路径、转向、避让、推进路点 | `order()`（返回 400）、`steering()`、`settings()` | [04](04-units-orders-and-movement.md) |
| `PathService` | 在固定每 tick 预算内消化寻路请求 | `DEFAULT_MAX_REQUESTS_PER_TICK`、`DEFAULT_NODE_BUDGET_PER_TICK`、`request()`、`cancel()`、`pendingRequests()`、`processedLastTick()`、`failedLastTick()`、`spentNodesLastTick()` | [04](04-units-orders-and-movement.md) |
| `SteeringSystem` | 把"去那里、别撞人"折算成一个速度 | `NEIGHBOUR_RADIUS_ASSUMPTION`、`seek()`、`separation()`、`formationCohesion()`、`arrivalSpeedFactor()`、`congestionFactor()`、`smooth()` | [04](04-units-orders-and-movement.md) |
| `SteeringSettings` | 转向调参：分离力度、到达减速、卡位重寻路等 | `defaults()`、13 个 `DEFAULT_*`、`withSeparationRadiusScale()`、`withMaxSpeedMultiplier()`、`withStuckTicksBeforeRepath()` | [04](04-units-orders-and-movement.md) |

### `...oc.rts.order`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `Order` | 单位能做的一件事：走、待命、取货、送货 | `name()`、`onStart()`、`tick(OrderContext)`、`onCancel()`、`describe()` | [04](04-units-orders-and-movement.md) |
| `OrderContext` | 订单运行时能拿到的一切：世界、单位、当前 tick | `world()`、`unit()`、`tick()`、`director()` | [04](04-units-orders-and-movement.md) |
| `OrderState` | 订单生命周期 | `RUNNING`/`COMPLETED`/`FAILED`/`CANCELLED`、`isFinished()` | [04](04-units-orders-and-movement.md) |
| `OrderQueue` | 单个单位的订单队列；只有队头在跑 | `push()`、`replace()`、`replaceAll()`、`clear()`、`current()`、`pending()`、`tick()` | [04](04-units-orders-and-movement.md) |
| `OrderSystem` | 推进每个单位的订单队列 | `order()`（返回 200） | [04](04-units-orders-and-movement.md) |
| `UnitDirector` | 给单位派活的唯一入口 | `move()`、`moveGroup()`、`stop()`、`hold()` | [04](04-units-orders-and-movement.md) |
| `CommandService` | `UnitDirector` 的默认实现，把编队拆成每单位一条 `FormationMoveOrder` | `DEFAULT_TICKS_PER_SECOND`、`planner()`、`moveGroup()`、`updateFormationAnchor()`、`activeFormationCount()`、`nextFormationId()` | [04](04-units-orders-and-movement.md) |
| `MoveOrder` | 把单个单位走到某个位置 | `TIMEOUT_TICKS`、`STALLED_TICKS_BEFORE_FAILURE`、`destination()` | [04](04-units-orders-and-movement.md) |
| `HoldOrder` | 让单位留在原地 | `name()`（返回 `"hold"`） | [04](04-units-orders-and-movement.md) |
| `FormationMoveOrder` | 让单个单位走到自己在移动队形中的槽位 | `TIMEOUT_TICKS`、`STALLED_TICKS_BEFORE_FAILURE`、`ANCHOR_EPSILON`、`formationId()`、`slotOffset()`、`goal()`、`updateAnchor()`、`isActive()` | [05](05-formations.md) |

### `...oc.rts.production`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `ProductionSystem` | 推进所有已完工建筑的生产队列 | `SYSTEM_ORDER`、`DEFAULT_DELIVERY_TIMEOUT_TICKS`、`enqueue()`、`queueOf()`、`cancel()`、`availableRecipes()`、`totalJobs()`、`trackedBuildings()` | [06](06-economy-logistics-and-production.md) |
| `ProductionQueue` | 单个建筑的生产队列 | `enqueue()`、`jobs()`、`current()`、`cancel(index)`、`clear()` | [06](06-economy-logistics-and-production.md) |
| `ProductionJob` | 一次配方执行的全部账目（含欠料与待产出） | `recipe()`、`state()`、`remainingTicks()`、`reservation()`、`baseline()`、`expected()`、`stateTicks()`、`isFinished()` | [06](06-economy-logistics-and-production.md) |
| `ProductionState` | 生产任务所处的阶段 | `WAITING_MATERIALS`/`DELIVERING`/`PRODUCING`/`WAITING_OUTPUT`/`COMPLETED`/`CANCELLED`、`isFinished()` | [06](06-economy-logistics-and-production.md) |
| `ConstructionSystem` | 推进工地直到建筑可用 | `SYSTEM_ORDER` | [06](06-economy-logistics-and-production.md) |

### `...oc.rts.sim`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `SimulationSystem` | 管线中的一步；按 `order()` 升序执行 | `name()`、`tick(World,Tick)`、`order()`（默认 1000） | [09](09-architecture-and-tick-pipeline.md) |
| `SystemPipeline` | 一次 tick 的有序系统列表 | `name()`、`add()`、`remove()`、`systems()`、`tick()`、`describe()` | [09](09-architecture-and-tick-pipeline.md) |

### `...oc.rts.visual`

| 类型 | 一句话作用 | 关键方法/字段 | 详见 |
| --- | --- | --- | --- |
| `VisualLibrary` | 内容 id → 精灵图集/动画集的查找表 | `empty()`、`builder()`、`sheet()`、`findSheet()`、`animationSet()`、`hasAnimationSet()` | [07](07-sprites-and-animation.md) |
| `VisualLibrary.Builder` | 图集与动画集的流式构建器 | `sheet(id)`、`set(spriteSetId)`、`build()` | [07](07-sprites-and-animation.md) |
| `VisualLibrary.SheetBuilder` | 描述一张图集：图片、格宽高、行列、锚点 | `image()`、`frameSize()`、`columns()`、`rows()`、`frameCount()`、`pivot()`、`frame(index)`、`toSheet()` | [07](07-sprites-and-animation.md) |
| `VisualLibrary.SetBuilder` | 声明或扩展一个 `spriteSetId` 的动画 | `spriteSetId()`、`clip(name)`、`set(other)`、`sheet(id)`、`build()` | [07](07-sprites-and-animation.md) |
| `VisualLibrary.ClipBuilder` | 拼装单个动画片段，可在中途切到方向变体 | `name()`、`frames(sheetId,start,count)`、`frame(...)`、`ticksPerFrame()`、`loop()`/`once()`、`direction(facing)`、`noDirection()` | [07](07-sprites-and-animation.md) |
| `SpriteSheet` | 一张按均匀网格切分的精灵图 | `frameWidth()`/`frameHeight()`、`columns()`/`rows()`、`frame(index)`、`pixelX()`/`pixelY()`、`pivotX()`/`pivotY()`、`frameWithDuration()` | [07](07-sprites-and-animation.md) |
| `SpriteFrame` | 一帧：像素矩形 + 锚点 + 可选时长 | `of()`、`withPivot()`、`withDuration()`、`effectiveDuration()`、`pixelRect()` | [07](07-sprites-and-animation.md) |
| `AnimationClip` | 具名帧序列，例如八帧行走循环 | `frames()`、`ticksPerFrame()`、`loop()`、`frameAt(elapsedTicks)`、`framePositionAt()`、`finishedAt()`、`durationTicks()` | [07](07-sprites-and-animation.md) |
| `AnimationSet` | 一个 `spriteSetId` 的全部动画，含方向变体 | `empty()`、`clips()`、`clip(name)`、`clip(name, facing)`、`hasDirectionalClips()`、`clipNames()` | [07](07-sprites-and-animation.md) |
| `AnimationClips` | 规范动画名常量 | `IDLE`、`MOVE`、`ATTACK`、`DEATH`、`BUILD`、`GATHER`、`CARRY_IDLE`、`CARRY_MOVE`、`PRODUCE` | [07](07-sprites-and-animation.md) |
| `VisualState` | 引擎视角下的单体动画状态（引擎只决定"放哪个动画"） | `play()`、`spriteSetId()`、`clipId()`、`frameIndex()`、`facing()`、`finished()`、`tick()` | [07](07-sprites-and-animation.md) |
| `UnitAnimator` | 单个实体的动画状态机，`VisualState` 的默认实现 | `currentFrame()`、`currentClip()`、`elapsedTicks()`、`animationSet()` | [07](07-sprites-and-animation.md) |
| `NullVisualState` | 没绑定动画集时的退化实现：只记住请求过的 clip | `clipId()`、`frameIndex()`（恒为 0） | [07](07-sprites-and-animation.md) |
| `AnimationSystem` | 从模拟状态驱动每个实体的动画 | `order()`（返回 800）、`producingCount()` | [07](07-sprites-and-animation.md) |
| `SnapshotFactory` | 把活世界拍成 `WorldSnapshot` | `DEFAULT_TICKS_PER_SECOND`、`ticksPerSecond()`、`capture(center, radiusTiles)` | [08](08-snapshots-and-rendering.md) |
| `WorldSnapshot` | 某一 tick 的不可变世界图像 | `tick()`、`elapsedSeconds()`、`bounds()`、`terrainAt()`、`entities()`、`entitiesOf()`、`selectedEntities()`、`loadedChunks()` | [08](08-snapshots-and-rendering.md) |
| `EntitySnapshot` | 快照中的单个实体，渲染所需字段已全部解析好 | `id()`、`position()`、`anchorTile()`、`healthFraction()`、`spriteFrame()`、`clipId()`、`hasSprite()`、`isDead()`、`carrying()` | [08](08-snapshots-and-rendering.md) |
| `AsciiMapRenderer` | 把快照画成纯文本 | `UNKNOWN_SYMBOL`、`OTHER_SYMBOL`、`render(snapshot)`、`symbolOf(...)` | [08](08-snapshots-and-rendering.md) |

## 常量与枚举速查

### 枚举成员（全部从源码抄录）

| 枚举 | 成员 | 附加方法 |
| --- | --- | --- |
| `OrderState` | `RUNNING`、`COMPLETED`、`FAILED`、`CANCELLED` | `isFinished()` |
| `ProductionState` | `WAITING_MATERIALS`、`DELIVERING`、`PRODUCING`、`WAITING_OUTPUT`、`COMPLETED`、`CANCELLED` | `isFinished()` |
| `DeliveryJob.State` | `WAITING_HAULER`、`IN_TRANSIT`、`COMPLETED`、`FAILED` | `isPending()`、`isFinished()` |
| `UnitRole` | `COMBAT("combat")`、`WORKER("worker")`、`SUPPORT("support")` | `id()` |
| `FormationShape` | `NONE("none","Free")`、`LINE("line","Line")`、`COLUMN("column","Column")`、`BOX("box","Box")`、`WEDGE("wedge","Wedge")`、`CIRCLE("circle","Circle")` | `id()`、`label()` |
| `Direction` | `NORTH(0,1,"north")`、`NORTH_EAST(1,1,"north_east")`、`EAST(1,0,"east")`、`SOUTH_EAST(1,-1,"south_east")`、`SOUTH(0,-1,"south")`、`SOUTH_WEST(-1,-1,"south_west")`、`WEST(-1,0,"west")`、`NORTH_WEST(-1,1,"north_west")` | `dx()`、`dy()`、`id()`、`angle()`、`opposite()`、`fromAngle()`、`fromVector()` |

### 字符串与数值常量

| 常量 | 真实值 |
| --- | --- |
| `AnimationClips.IDLE` / `MOVE` / `ATTACK` / `DEATH` | `"idle"` / `"move"` / `"attack"` / `"death"` |
| `AnimationClips.BUILD` / `GATHER` / `PRODUCE` | `"build"` / `"gather"` / `"produce"` |
| `AnimationClips.CARRY_IDLE` / `CARRY_MOVE` | `"carry_idle"` / `"carry_move"` |
| `Inventory.UNLIMITED` | `Integer.MAX_VALUE`（`slotCount` 与 `unitLimit` 共用） |
| `ChunkStreamer.ORDER` / `DEFAULT_CHUNKS_PER_TICK` | `100` / `4` |
| `PathOptions.DEFAULT` | `new PathOptions(20_000, true, false, 1.05f)` |
| `PathOptions.FAST` | `new PathOptions(2_000, true, false, 1.5f)` |
| `PathOptions.EXHAUSTIVE` | `new PathOptions(Integer.MAX_VALUE, true, false, 1.0f)` |
| `HaulOrder.TIMEOUT_TICKS` | `3000` |
| `NoiseSettings.DEFAULT_CHUNK_SIZE` | `16` |
| `NoiseSettings.DEFAULT_ORIGIN_ELEVATION` / `DEFAULT_ORIGIN_RADIUS` | `0.44f` / `36f` |
| `NoiseSettings.defaults(ns)` 的字段值 | `frequency=1f/32f`、`octaves=3`、`lacunarity=2f`、`gain=0.5f`、`seaThreshold=0.38f`、`sandThreshold=0.42f`、`forestThreshold=0.52f`、`hillThreshold=0.56f`、`rockThreshold=0.74f`、`moistureFrequency=1f/24f`；六个路径依次为 `water/sand/grass/forest/hill/rock` |
| `SteeringSettings.DEFAULT_*` | `SEPARATION_RADIUS_SCALE=1f`、`SEPARATION_STRENGTH=1f`、`SEPARATION_PRESSURE_CAP=3f`、`ARRIVAL_SLOWDOWN_DISTANCE=1.5f`、`ARRIVAL_SPEED_FLOOR=0.25f`、`MAX_SPEED_MULTIPLIER=1.35f`、`VELOCITY_SMOOTHING=0.5f`、`WAYPOINT_RADIUS=0.35f`、`FORMATION_COHESION_WEIGHT=0.4f`、`CONGESTION_SPEED_FLOOR=0.35f`、`STUCK_TICKS_BEFORE_REPATH=90`、`STUCK_DISPLACEMENT_PER_TICK=0.02f`、`STUCK_REPATH_COOLDOWN=60` |
| `PathService.*` | `DEFAULT_MAX_REQUESTS_PER_TICK=32`、`DEFAULT_NODE_BUDGET_PER_TICK=200_000`、`MAX_EXPANSIONS_PER_SEARCH=PathOptions.DEFAULT.maxExpansions()`（即 `20_000`）、`FAILED_REPATH_COOLDOWN=30`、`GOAL_ADJUST_RING=8` |
| `HaulingSystem.*` | `SYSTEM_ORDER=700`、`DEFAULT_TIMEOUT_TICKS=3000` |
| `ProductionSystem.*` | `SYSTEM_ORDER=600`、`DEFAULT_DELIVERY_TIMEOUT_TICKS=3000` |
| `ConstructionSystem.SYSTEM_ORDER` | `500` |
| `FormationPlanner.*` | `MIN_SPACING=0.6f`、`DEFAULT_SPACING_SCALE=1.25f` |
| `MoveOrder.*` | `TIMEOUT_TICKS=2000L`、`STALLED_TICKS_BEFORE_FAILURE=300` |
| `FormationMoveOrder.*` | `TIMEOUT_TICKS=2000L`、`STALLED_TICKS_BEFORE_FAILURE=300`、`ANCHOR_EPSILON=0.25f` |
| `CommandService.DEFAULT_TICKS_PER_SECOND` | `20` |
| `SnapshotFactory.DEFAULT_TICKS_PER_SECOND` | `20`（引擎在 `build()` 时把自己的速率传进来，见 [08](08-snapshots-and-rendering.md)） |
| `SteeringSystem.NEIGHBOUR_RADIUS_ASSUMPTION` | `1f` |
| `FlatChunkGenerator.DEFAULT_CHUNK_SIZE`、`NoiseChunkGenerator.DEFAULT_CHUNK_SIZE` | `16` |
| `AsciiMapRenderer.UNKNOWN_SYMBOL` / `OTHER_SYMBOL` | `'?'` / `'%'` |
| `ItemBundle.EMPTY`、`Vec2.ZERO` | 空 bundle / `new Vec2(0f, 0f)` |
| `OcRtsEngineBuilder` 默认值 | `seed=20240101L`、`ticksPerSecond=20`、`chunkSize=16`、`viewRadiusChunks=4`、`chunkStore=new InMemoryChunkStore()`、`visuals=VisualLibrary.empty()`、`directDelivery=false`、`directDeliveryTicks=60`、`pathRequestsPerTick=32`、`pathNodeBudgetPerTick=200_000`；`build()` 时若没有任何玩家，自动加入 `Player.of(1, "Player 1", 0xFF4FC3F7)` |
| `SimulationSystem.order()` 建议阶段 | `100` 地图流式、`200` 订单、`300` 寻路请求、`400` 移动、`500` 建造、`600` 生产、`700` 后勤、`800` 动画、`900` 清理；接口默认 `1000` |
| `World` 内部默认 | 空间索引用 `new GridSpatialIndex(4f)`；事件总线用 `SimpleEventBus` |

## 门面类方法速查

### `OcRtsEngine`（274 行，`OcRtsEngine.builder(content)` 创建）

| 方法 | 作用 | 是否可能抛异常 |
| --- | --- | --- |
| `builder(ContentRegistry)` | 创建构建器 | `content` 为 `null` → `NullPointerException` |
| `world()` / `content()` / `events()` / `players()` | 取世界、内容目录、事件总线、玩家列表 | 否 |
| `tickNumber()` / `ticksPerSecond()` / `elapsedSeconds()` / `currentTick()` | 时间信息；`currentTick()` 是"即将运行"的 tick | 否 |
| `commands()` / `production()` / `pathService()` / `pathFinder()` / `visuals()` / `pipeline()` | 取服务（指挥、生产、寻路预算、寻路器、动画库、管线） | 否 |
| `map()` / `streaming()` | 取地形与区块流式控制（同一 `ChunkedMap` 的两个视图） | 否 |
| `tick()` / `tick(int ticks)` | 前进一格 / 若干格 | 系统内部异常原样冒泡（例如世界未设地图时 `World#map()` 抛 `IllegalStateException`） |
| `tickUntil(Predicate<OcRtsEngine>, int maxTicks)` | 反复 tick 直到条件成立；返回是否达成 | 条件为 `null` → `NullPointerException` |
| `focus(Vec2)` / `focus(GridPos)` | 移动兴趣点，下一 tick 会流式载入附近区块 | 参数为 `null` → `NullPointerException`（经 `ChunkedMap#focus`） |
| `loadedBounds()` | 当前内存中区块的瓦片范围 | 否；**没有任何区块时返回 `null`** |
| `spawnUnit(String, Player, Vec2/GridPos)` | 按内容路径直接生成单位 | 路径未定义 → `NoSuchElementException`（来自 `ContentRegistry#unit`） |
| `spawnBuilding(String, Player, GridPos)` | 按内容路径生成"已完工"建筑 | 路径未定义 → `NoSuchElementException` |
| `snapshot(GridPos, int)` | 拍一张世界快照 | 半径为负 → `IllegalArgumentException`；`center` 为 `null` → `NullPointerException` |
| `render(GridPos, int)` | 快照 → ASCII 文本 | 同 `snapshot` |
| `describe()` | 多行摘要（tick、实体数、区块数、待处理路径/交付、系统清单） | 否 |

### `OcRtsEngineBuilder`（200 行）

| 方法 | 作用 | 是否可能抛异常 |
| --- | --- | --- |
| `seed(long)` | 世界种子；同种子同地形同模拟 | 否 |
| `ticksPerSecond(int)` | 模拟速率（默认 20） | `<= 0` → `IllegalArgumentException` |
| `chunkSize(int)` | 区块边长（格） | `<= 0` → `IllegalArgumentException` |
| `viewRadiusChunks(int)` | 兴趣点周围保留多少区块 | `< 0` → `IllegalArgumentException` |
| `generator(ChunkGenerator)` | 换地形生成器；不设则用 `NoiseChunkGenerator(seed, content)` | 否（`null` 等于"用默认"） |
| `chunkStore(ChunkStore)` | 换区块存储（默认内存） | `null` → `NullPointerException` |
| `visuals(VisualLibrary)` | 动画目录；默认 `VisualLibrary.empty()` | `null` → `NullPointerException` |
| `directDelivery(boolean)` | 打开后交付由定时传送完成，不需要搬运工 | 否 |
| `directDeliveryTicks(int)` | 传送耗时（默认 60） | `<= 0` → `IllegalArgumentException` |
| `pathBudget(int requestsPerTick, int nodeBudgetPerTick)` | 寻路队列预算 | 任一 `<= 0` → `IllegalArgumentException` |
| `player(Player)` / `players(List<Player>)` | 加入玩家 | 元素为 `null` → `NullPointerException` |
| `build()` | 接线世界与全部子系统（区块流 100 → 订单 200 → 寻路 300 → 移动 400 → 建造 500 → 生产 600 → 后勤 700 → 动画 800） | 内容目录非法在构造 `ContentRegistry` 时已抛；玩家列表为空时自动补一个默认玩家 |

### `World`（275 行，直接 `new` 或从 `OcRtsEngine#world()` 取）

| 方法 | 作用 | 是否可能抛异常 |
| --- | --- | --- |
| `World(long seed, ContentRegistry)` | 构造世界（含 `DeterministicRandom.forStream(seed,"world")`） | `content` 为 `null` → `NullPointerException` |
| `seed()` / `content()` / `random()` / `events()` / `spatial()` | 环境与随机源 | 否 |
| `map()` / `pathFinder()` / `director()` | 取注入的服务 | 未注入 → `IllegalStateException` |
| `setMap(TerrainMap)` / `setPathFinder(PathFinder)` / `setDirector(UnitDirector)` | 注入服务 | `null` → `NullPointerException` |
| `tickNumber()` / `advanceTick()` | tick 计数；`advanceTick()` 先自增再返回新值 | 否 |
| `players()` / `findPlayer(PlayerId)` / `addPlayer(Player)` | 玩家查询与登记 | `addPlayer(null)` → `NullPointerException` |
| `entities()` / `units()` / `buildings()` | 实体集合 | 否；注意 `units()`/`buildings()` 返回**内部可变列表**，迭代时不要生成/销毁实体 |
| `findEntity(EntityId)` / `entity(EntityId)` | 按 id 查实体 | `entity()` 查不到 → `IllegalArgumentException` |
| `unitsOf(Player)` / `buildingsOf(Player)` | 某玩家的单位/建筑 | 否 |
| `entityCount()` | 实体总数 | 否 |
| `spawnUnit(UnitType, Player, GridPos/Vec2)` | 立即生成单位（生产完成时用） | 参数为 `null` 会在后续解引用时 `NullPointerException`（未显式校验） |
| `spawnBuilding(BuildingType, Player, GridPos)` | 生成"已完工"建筑 | 同上 |
| `placeBuilding(BuildingType, Player, GridPos)` | 放置仍需建造的工地 | 同上 |
| `canPlace(BuildingType, GridPos)` | 地形可建且不与已有建筑重叠 | 未设地图 → `IllegalStateException`（经 `map()`） |
| `destroy(Entity)` | 只标记死亡并发布 `Events.EntityDestroyed` | `null` 或无生命实体是安全 no-op |
| `flush()` | 把死亡实体真正从集合移除（引擎每 tick 调一次） | 否 |

## 相关文档

- [01 入门](01-getting-started.md) —— 组装第一个引擎、tick、生成单位
- [02 内容与物品](02-content-and-items.md) —— `ContentRegistry`、地形/物品/兵种/建筑定义
- [03 地图与寻路](03-map-and-pathfinding.md) —— 区块、流式加载、`ChunkStore`、A*
- [04 单位、订单与移动](04-units-orders-and-movement.md) —— `Order`、`Motion`、转向与寻路预算
- [05 编队](05-formations.md) —— `FormationPlanner`、`FormationMoveOrder`
- [06 经济、后勤与生产](06-economy-logistics-and-production.md) —— `Inventory`、`Logistics`、`ProductionSystem`
- [07 精灵与动画](07-sprites-and-animation.md) —— `VisualLibrary`、`SpriteSheet`、`AnimationClip`
- [08 快照与渲染](08-snapshots-and-rendering.md) —— `SnapshotFactory`、`WorldSnapshot`
- [09 架构与 tick 管线](09-architecture-and-tick-pipeline.md) —— `SimulationSystem`、`World`、事件总线
- [10 实战手册](10-cookbook.md) —— 常见任务的可复制代码
- [11 限制与 FAQ](11-limits-and-faq.md) —— 已知取舍、性能与陷阱
