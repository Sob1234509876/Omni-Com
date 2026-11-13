# Omni Com 客户端/服务端术语表（AI 工作参考）

> 面向 AI 助手的精确参考。所有代码位置相对仓库根目录；行号以编写时（2026-11）的代码为准，
> 引用形式为 `路径` 或 `路径#类名`。机制细节见姊妹文档 [server-architecture.md](server-architecture.md)。
> 代码是唯一权威：本文与代码冲突时以代码为准。

## 世界与模拟

| 中文术语 | 英文术语 | 一句话解释 | 主要代码位置 |
| --- | --- | --- | --- |
| 世界 | World | 模拟状态的根对象：持有若干维度、玩家、网关，并负责实体 id 的全局发放；刻意无线程，由系统按流水线顺序变更。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/World.java` |
| 引擎 | Engine (OcRtsEngine) | 一次正在进行的对局：世界 + 按相位排序的系统流水线 + 玩家可调用的服务（命令、生产、寻路）；对调用者纯回合制，`tick()` 恰好推进一个固定步长。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngine.java` |
| tick | tick / fixed step | 模拟的最小时间步，固定 1/20 秒（50 ms）；`World.advanceTick()` 递增计数器，流水线各系统按 `order()` 相位依次执行，最后 `World.flush()` 移除死亡实体。 | `World.advanceTick()`；`OcRtsEngine.tick()`；`projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/sim/SystemPipeline.java` |
| 确定性随机 | DeterministicRandom (SplitMix64) | 模拟内唯一随机源：splitmix64 生成器，按 `seed ^ 0xC0FFEE123456789` 混入流名派生子流；禁用 `java.util.Random` 与墙钟以保重放一致。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/core/DeterministicRandom.java` |
| 种子派生 | seed derivation / mix64 | 各处从世界种子整数混合派生子种子：维度种子（世界种子 ^ 黄金比例，再对维度 key 逐字符 FNV 式乘混）、bot 种子 `mix64(worldSeed*31+i)`、野外刷怪 `mix64(seed ^ dim ^ x*SALT ^ y*SALT)`。 | `OcRtsEngineBuilder.seedFor()`；`projects/oc/oc-game-server-classic/src/main/java/io/github/sob1234509876/oc/server/classic/BotStrategy.java#mix64`；`WildSpawns.rollOf()` |
| 玩家 | Player | 拥有实体的派系：id、显示名、颜色、锚点（anchor），并挂载一个物流网络（WarehouseLogistics）。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/Player.java` |
| 实体 | Entity | 站在与建筑共同基类：id、所有者、位置（`Vec2`）、生命、所在维度；死亡只是标记，tick 末尾 `flush()` 才真正移除。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/entity/Entity.java` |
| 单位 | Unit | 可移动实体：携带 `Motion`（移动/路径）、`OrderQueue`（订单队列）、`cargo`（货物）、`CombatStats`。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/entity/Unit.java` |
| 建筑 | Building | 占据 `sizeX×sizeY` 脚印的固定实体：有 `inventory`、建造进度 `constructionFraction()`、生产队列（经 ProductionSystem）。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/entity/Building.java` |
| 实体 id | EntityId | 全世界唯一、跨维度唯一的数字句柄；升级类命令会换新 id（旧 id 被显式从所有单位的战斗目标中清除，见 `World.clearTargets`）。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/core/EntityId.java`；`World.clearTargets()` |
| 空间索引 | SpatialIndex (GridSpatialIndex) | 每个维度自己的邻居查询索引（网格，格宽 4）；位置相关代码必须用所在维度的索引。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/entity/GridSpatialIndex.java` |

## 内容注册与升级

| 中文术语 | 英文术语 | 一句话解释 | 主要代码位置 |
| --- | --- | --- | --- |
| 内容注册表 | ContentRegistry | 不可变目录：按 `ResourceKey` 注册地形、物品、单位、建筑、配方；由 `WorldContent.populate(builder)` 填充，服务器在启动时构建一次。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/content/ContentRegistry.java`；`WorldHost.registryOf()` |
| 内容键 / 内容路径 | ResourceKey / content path | `命名空间:路径` 形式的 id（如 `oc:building.mine.t1`）；路径为小写字母/数字/下划线的点分段。线上（snapshot `kind`、命令 token）只用路径部分，如 `building.armory.t1`、`unit.engineer`、`gold`。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/content/ResourceKey.java` |
| 世界内容接缝 | WorldContent | 插件声明整个目录的接缝：命名空间、全部类型、起始资源、维度列表、网关列表；服务器取第一个注册者（先到先得）。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/content/WorldContent.java`；`OcGameServer.selectWorldContent()` |
| 内容标签 | tag（`gateway` / `builder` / `denizen` / `income_<n>` / `wood_income_<n>`） | 类型上的字符串约定：`gateway` 标记传送门建筑（snapshot 行携带 `gateway` 目标），`builder` 标记工程师（BUILD 菜单判据），`denizen` 标记野外生物（展示场景跳过），`income_30` / `wood_income_20` 发布每分钟收入。 | `WorldHost.GATEWAY_TAG`/`BUILDER_TAG`；`ClassicContent`（`income_`、`wood_income_` 前缀）；`BuildingType.hasTag()` |
| 建筑升级阶梯 | upgrade ladder (BuildingType.UpgradeOption) | 建筑类型声明若干后继（`UpgradeOption(target, cost)`），`UPGRADE <id> [variant]` 原地替换建筑并返回新 id；同脚印 + 允许维度才被提供。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/content/BuildingType.java#UpgradeOption`；`UnitDirector.upgradeBuilding()` |
| 单位升级阶梯 | unit upgrade ladder (UnitType.upgradeTo / upgradeCost) | 单位类型的单步后继 `upgradeTo` 与费用 `upgradeCost`；`UPGRADEUNIT <id> [tier]` 原地替换单位并保留生命比例与货物。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/content/UnitType.java`；`UnitDirector.upgradeUnit()` |
| 建筑等级 | building level (level) | 建筑类型的层级数，配方经 `requiredBuildingLevel()` 以此设门槛：二级配方在一级兵工厂被拒。 | `BuildingType.level()`；`projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/content/Recipe.java` |
| 配方 | Recipe | 一次生产：输入 `ItemBundle`、输出（单位或物品）、时长 `durationTicks()`、需求建筑等级；按"产出单位"被 TRAIN 查找。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/content/Recipe.java` |

## 经济与后勤

| 中文术语 | 英文术语 | 一句话解释 | 主要代码位置 |
| --- | --- | --- | --- |
| 仓库 | Warehouse | `BuildingType.isWarehouse()` 为真且已完工、归本玩家所有的建筑；是玩家唯一可从其中支出物资的地方。 | `BuildingType.isWarehouse()`；`projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/economy/WarehouseLogistics.java` |
| 物流网络 | Logistics / WarehouseLogistics | 每玩家一张"仓库视图"：不持有物品，只决定哪些建筑算仓库（订阅 EntitySpawned/ConstructionCompleted/EntityDestroyed 事件维护成员），并管理预留。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/economy/WarehouseLogistics.java`、`Logistics.java` |
| 库存 | inventory / contents | 建筑实际持有物品的容器（`Building.inventory()`），带槽位（`slots/used/free`）；单位随身的是 `cargo`。生产者缓冲区（buffer）也写在建筑 `inventory` 里，但不属于物流网络、不可支出。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/economy/Inventory.java`；snapshot 的 `contents` / `storage` 字段（`WorldHost.appendContents`） |
| 预留 / 认领 | reservation / claim | 生产或调拨先在物流网络上"预订"输入物资（`LogisticsReservation`），防止两个任务花掉同一堆；`ReservationPool` 按仓库创建顺序确定性分摊。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/economy/LogisticsReservation.java`、`ReservationPool.java` |
| 生产 | Production (ProductionSystem) | 驱动所有已完工生产建筑的队列：工厂永远不拥有材料——输入预留于仓库、搬运进缓冲、消耗；产出立刻存回网络，存不进则停在 `WAITING_OUTPUT`。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/production/ProductionSystem.java`；状态机见 `ProductionState.java` |
| 生产队列 | ProductionQueue | 一栋建筑的未完成作业序列；`producingNow()` 给出 `生产中 position/length`，`demand()` 给出当前作业尚缺的输入。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/production/ProductionQueue.java`；snapshot 字段 `queue`/`queueTicks`/`queueItems`/`producingNow`/`demand` |
| 调拨 | TRANSFER (transfer) | 玩家命令：在两座自己的仓库之间移动库存。引擎侧 `UnitDirector.transfer` 强制同玩家、同维度、完工仓库、可达等规则，预留一批货并派出真实搬运工；`amount` 可为 `all`（解析为源仓库未预留余量）。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/order/CommandService.java`（实现 `UnitDirector`）；`CommandDispatcher.transfer()` |
| 搬运 | haul / HaulingSystem | 把 `DeliveryJob` 变成会走路的搬运工（或 `directDelivery` 下的定时送达）；`HAUL <engineer> <building>` 强制一名工程师为生产者补齐首个缺料作业的输入。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/logistics/HaulingSystem.java`、`DeliveryJob.java`；`UnitDirector.tryHaul()` |
| 搬运动画 | haul pop (`Events.ItemsHauled`) | 引擎在拾取/卸货瞬间发布事件；服务器按单位保留最近一条，snapshot 行携带 `pop:{kind,tick}`（20 tick TTL），客户端播放 600 ms 动画并按 tick 去重。 | `WorldHost.recentPops`/`POP_TTL_TICKS`；`web-client/src/render/pop.ts` |
| 收入 | income (`income_<n>` tags) | classic 钩子 `ClassicEconomy` 每 60 tick（一"分钟"）按玩家建筑标签之和入账；snapshot 玩家行同源报告 `goldPerMinute`/`woodPerMinute`。 | `projects/oc/oc-game-server-classic/src/main/java/io/github/sob1234509876/oc/server/classic/ClassicEconomy.java`；`WorldHost.incomePerMinute()` |

## 维度与地图

| 中文术语 | 英文术语 | 一句话解释 | 主要代码位置 |
| --- | --- | --- | --- |
| 维度 | Dimension | 一张独立地图及其上的全部实体与空间索引；坐标维度内局部，两个维度可以在同一坐标各有单位互不相扰；实体 id 仍全局唯一。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/dimension/Dimension.java` |
| 维度 id | DimensionId | 维度的字符串键（`overworld`、classic 的 `hell`）；第一个注册的维度成为默认维度。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/dimension/DimensionId.java` |
| 维度内容 | DimensionContent (WorldContent.dimensions()) | 插件声明的维度：id、显示名、噪声参数（`NoiseSettings`）；服务器把它喂给 builder，每维度派生自己的种子与地图。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/content/DimensionContent.java`；`WorldHost` 构造器 |
| 传送门/网关 | Gateway | 一张单程"门"：起点维度 + 必须站上的瓦片 → 到达维度 + 落点瓦片；纯地图数据而非内容。classic 的 `HellGates` 钩子按活着的地狱门建筑每 10 tick 同步网关集合。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/dimension/Gateway.java`；`World.route()`（BFS）；`HellGates.java` |
| 区块 | Chunk | 16×16 瓦片的地图块（`NoiseTerrainGenerator.DEFAULT_CHUNK_SIZE`）；按 `ChunkPos`（有符号整数坐标）寻址，按需生成。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/Chunk.java`、`ChunkPos.java` |
| 无界地图 | InfiniteTerrainMap | 无边缘地形图：任何有符号坐标都有地形；已加载区块存于**访问序** `LinkedHashMap`，超过 LRU 预算（默认 4096）淘汰最久未用者，脏块先写 ChunkStore。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/InfiniteTerrainMap.java` |
| 区块预算 | chunk budget (`--chunks N`, default 4096) | 内存上限而非世界大小：超限区块被逐出后按需确定性重建；`WorldHost.DEFAULT_CHUNK_BUDGET` 即 4096。 | `InfiniteTerrainMap.DEFAULT_MAX_LOADED_CHUNKS`；`OcGameServer` 的 `--chunks` 选项 |
| 区块流送 | ChunkStreamer | 流水线第 100 相位的首个系统：围绕各维度焦点卸载半径外、加载半径内缺失区块，每 tick 跨维度共享预算（默认 4 次转换）。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/ChunkStreamer.java` |
| 噪声生成 | NoiseChunkGenerator / NoiseTerrainGenerator | 纯函数值噪声地形：海拔（3 个倍频程，32 瓦片基格）+ 湿度（24 瓦片基格，独立盐），按阈值（WATER 0.32 / SAND 0.36 / HILL 0.60 / ROCK 0.78 / FOREST 0.55）分级成六种生物群系；每格点哈希用 SplitMix64 终结器。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/NoiseTerrainGenerator.java`、`NoiseChunkGenerator.java`、`BiomeTable.java` |
| 区块窗口 | snapshot window (`cx`/`cy`/`r`) | 快照只携带以请求瓦片为中心的 `2r×2r` 方窗（`r` 钳到 16..64，默认 40 = 80×80 = 五个 16 瓦片区块宽）；中心**不**钳制，世界无边界。 | `WorldHost.DEFAULT_SNAPSHOT_RADIUS`/`clampSnapshotRadius`；`snapshotJson(DimensionId,int,int,int)`；HTTP `GET /world/snapshot?cx=&cy=&r=` |
| 已加载区块 | `loadedChunks` | 快照顶层字段：所画维度当前在内存中的区块坐标数组（上限 1024 项）；客户端据此给未加载区块盖阴影。 | `WorldHost.buildSnapshotJson()`；`web-client/src/render/shadow.ts` |
| 维度区块库 | per-dimension ChunkStore | 每维度独立一份生成器与存储（默认 `InMemoryChunkStore`）：存储只按区块坐标键控，共享会互相覆写。 | `OcRtsEngineBuilder.build()`；`projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/InMemoryChunkStore.java` |

## 服务器与协议

| 中文术语 | 英文术语 | 一句话解释 | 主要代码位置 |
| --- | --- | --- | --- |
| 世界宿主 | WorldHost | 服务器侧世界：在一条 `oc-world` 守护线程上以固定步长推进引擎、串行化快照、排队命令、调用插件钩子。 | `projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java` |
| 快照 | snapshot (`snapshotJson`) | `GET /world/snapshot` 返回的 JSON：tick、窗口原点/尺寸、`tiles`、全部存活实体行、`projectiles`、`buildable`、`players`，外加 `dimension`/`dimensions`/`loadedChunks`。 | `WorldHost.buildSnapshotJson()`；协议文档 `protocol/oc-protocol.md` |
| 命令队列 | command queue (`pendingCommands`) | `ConcurrentLinkedQueue<PendingCommand>`：socket 线程唯一的入世界通道；每个固定步**开始**时按提交顺序整体排空，再推进引擎。 | `WorldHost.submitCommand()`、`step()`、`applyPendingCommands()` |
| 命令分发器 | CommandDispatcher | 与传输无关的命令语法（TCP 与 WebSocket 共用）：解析、鉴权、把游戏命令包装成 `WorldCommand` 提交并等待答案。 | `projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/net/CommandDispatcher.java` |
| BATCH | BATCH | 客户端把一条"抽象命令"里的多个子命令（≤16 条，`;` 分隔）打包发送；逐条按序执行、非事务：首个失败以 `ERR <i>/<n> <原因>` 报告，后续仍执行。 | `CommandDispatcher.batch()`、`MAX_BATCH_COMMANDS` |
| WebSocket 会话 | WebSocket session (`Session`) | 每连接的登录标记 + 账号名 + 缓存玩家 id；浏览器需先发 `HELLO 1` 版本行（服务器答 `OK OC/1`），再 `LOGIN` 之后才可用 `REQ` 与全部游戏命令。 | `projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/ws/CommandWebSocketHandler.java`；`CommandDispatcher.Session` |
| 账号/身份 | account identity | 账号名（而非连接）即身份：`joinPlayer(name)` 首次把名字绑定到最小空闲人类槽（3..8），断线不回收、重连同名返回同一玩家与基地。 | `WorldHost.joinPlayer(String)`、`accountIds`；`MAX_PLAYERS=8`、`FIRST_HUMAN_PLAYER_ID=3` |
| 显示名 | displayName (Player.name) | 玩家行的 `name` 字段：具名加入时即账号名，匿名分配时为 `Player <id>`；bot 各有自己的名字（如"荒野魔物"）。 | `Player.name()`；`WorldHost.allocatePlayer()`；`WildSpawns.faction()` |
| 玩家槽位 | human slots / bot ids | 人类占 id 3..8；经典 bot 0 占 id 2，额外 bot 从 9 起；野生魔物阵营固定 id 99。 | `WorldHost.FIRST_HUMAN_PLAYER_ID`/`MAX_PLAYERS`；`ClassicBots.FIRST_EXTRA_BOT_PLAYER_ID`；`HellDenizens.FACTION_PLAYER_ID` |
| 生成锚点 | spawn anchor | 从世界原点 (0,0) 按固定环序找到的第一块可建造瓦片（搜索半径 64）：样本场景与全部人类基地锚点都相对它排布，纯种子+目录的函数。 | `WorldHost.findSpawnAnchor()`、`SEARCH_RADIUS_TILES`、`spawnAnchor()` |
| 基地锚点 | base anchor | 每个人类槽在锚点周围的固定 3×2 网格锚（列距 24、行距 48）；即玩家行 `anchor` 字段与罗盘/小地图箭头指向的瓦片。 | `WorldHost.baseAnchor()`；bot 锚点见 `ClassicBots`（16 方位 × 5 距离环 96..192） |

## 插件系统

| 中文术语 | 英文术语 | 一句话解释 | 主要代码位置 |
| --- | --- | --- | --- |
| 插件 | Plugin | `plugins/*.jar` 中的 `io.github.sob1234509876.oc.server.plugin.Plugin` 实现：onLoad 领取上下文、onEnable 启动、onDisable 清理；类加载器永不关闭。 | `projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/plugin/Plugin.java`、`PluginLoader.java` |
| 插件清单 | plugin descriptor (`omnicom-plugin.properties`) | jar 根部的可选属性文件（`id`/`version`/`main`）；发现顺序：`META-INF/services/` → 描述符 `main` → 全 jar 类扫描。 | `projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/plugin/PluginDescriptor.java`；`PluginLoader.loadJar()` |
| 插件上下文 | PluginContext | 插件拿到的接缝束：目录、数据根、日志、REQ 注册表、世界内容/tick 钩子注册、服务器选项（如 `bots`）。 | `projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/plugin/PluginContext.java` |
| tick 钩子 | WorldTickHook | 插件进入运行世界的唯一接缝：每个固定步**末尾**（引擎 tick 后）在世界线程、状态锁内按插件加载顺序运行全部钩子；必须确定性、不得起线程/IO。 | `projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldTickHook.java`；`OcGameServer.selectWorldTickHooks()` |
| 插件资产 | plugin-assets (`/plugin-assets/...`) | HTTP 路由：合并各插件 jar 内 `assets/manifest.json` 成数组（`plugin`+`baseUrl`+`tilePixels`+`tiles`+`entities`），按类加载器读字节，无文件系统访问；`Cache-Control: no-cache`。 | `projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/ws/PluginAssets.java`、`PluginManifest.java` |
| 清单（每材质一图） | manifest v2 (per-material images) | 清单格式：每个地形瓦片一张图、每个实体一条竖条帧图（PNG 或 SVG），路径相对 `baseUrl`；客户端按序应用数组，后者覆盖前者同名瓦片/实体。 | `PluginManifest`（服务端）；`web-client/src/render/atlas.ts`（客户端解码） |
| classic 插件 | oc-classic (ClassicPlugin) | 声明经典目录并注册 6 个 tick 钩子（经济、bot、工程师池、地狱门同步、地狱魔物、野外刷怪）的内容插件。 | `projects/oc/oc-game-server-classic/src/main/java/io/github/sob1234509876/oc/server/classic/ClassicPlugin.java` |

## 订单与命令

| 中文术语 | 英文术语 | 一句话解释 | 主要代码位置 |
| --- | --- | --- | --- |
| 订单队列 | OrderQueue | 单位内部的订单序列；命令默认替换队头，`queue` 尾 token（Shift-click 形式）追加到队尾按序执行。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/order/OrderQueue.java`、`OrderSystem.java` |
| 指挥服务 | UnitDirector / CommandService | 引擎侧命令门面（`engine.commands()`）：BUILD/REPAIR/RECLAIM/TRANSFER/HAUL/UPGRADE/UPGRADEUNIT 的存在性、所有权、费用、可达等规则都在这里、在世界线程上判定。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/order/CommandService.java`（实现 `order/UnitDirector.java`） |
| 任务标签 | task / orderQueue labels | snapshot 行字段：`task` 一行摘要（`搬运中 → 兵工厂`），`orderQueue` 为执行顺序的标签数组（封顶 8 条 + `… 还有 N 项`），由引擎 `OrderLabels` 统一生成。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/visual/OrderLabels.java`、`UnitTask.java`；`WorldHost.appendTask`/`appendOrderQueue` |
| 寻路预算 | PathService budget | 寻路请求按 tick 预算分摊（默认每 tick 32 个请求、20 万节点），每单位只保留最新请求；结果落空即丢弃。 | `projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/movement/PathService.java`；builder 默认值见 `OcRtsEngineBuilder` |
| 路线 | route / `path`+`target` | snapshot 中移动单位剩余折线（≤64 点）与目标瓦片；客户端画出虚线，Shift+右键追加待发路点。 | `WorldHost.appendUnitPath()`、`MAX_SNAPSHOT_PATH_POINTS`；`web-client/src/render/paths.ts` |

## 客户端

| 中文术语 | 英文术语 | 一句话解释 | 主要代码位置 |
| --- | --- | --- | --- |
| 防御性解析 | defensive parsing | 客户端对 snapshot/manifest 的每个字段都容错：非有限数、缺失字段、旧服务器一律回退（如 `loadedChunks` 缺失时不画阴影、demo 世界兜底）。 | `web-client/src/`（`main.ts` 轮询、`render/` 各模块） |
| 图集 | atlas (PNG/SVG) | 客户端从合并清单加载每材质一张图（现在多为 SVG，需自带 width/height），实体为竖条帧图；未知 kind 回退程序化绘制。 | `web-client/src/render/atlas.ts`；`fallback.ts` |
| 渲染循环 | render loop | 客户端每帧在最近两个快照间插值绘制（500 ms 轮询 + `render/interpolate.ts`），箭矢、阴影渐散等跨快照持有状态。 | `web-client/src/render/renderer.ts`、`interpolate.ts`、`main.ts`（`WORLD_POLL_MS=500`） |
| 跟随 | follow lock（跟随） | 选中恰好一个非建筑实体时可锁定相机：每帧硬跟随插值位置，目标消失/断线自动解锁并提示。 | `web-client/src/render/follow.ts`（脉冲环）；锁定逻辑在 `web-client/src/main.ts`（`lockedEntityId`） |
| 选择 | selection / marquee | 框选按优先级：己方战斗单位 > 工程师 > 建筑；敌/中立不可框选；单击仍可直接选建筑。 | `web-client/src/render/marquee.ts`、`selection.ts`、`picking.ts` |
| 未加载阴影 | unloaded-chunk shadow | 未加载区块上的半透明阴影（α 0.72），区块加载后逐帧消散；纯数学在 `shadow.ts` 便于无浏览器检查。 | `web-client/src/render/shadow.ts` |
