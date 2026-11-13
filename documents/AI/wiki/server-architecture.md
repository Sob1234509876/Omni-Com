# Omni Com 服务端运行原理（含区块加载机制）（AI 工作参考）

> 面向 AI 助手的服务端机制参考，描述**当前代码**的行为（2026-11 的 `HEAD`）。所有路径相对仓库根目录；
> 类名不带包名，完整路径在首次引用时给出。术语定义见 [glossary.md](glossary.md)。
> 代码是唯一权威：本文与代码冲突时以代码为准；"计划中"条目不是当前事实。

## 目录

1. [模块地图](#1-模块地图)
2. [启动流程](#2-启动流程)
3. [世界主循环（世界线程模型）](#3-世界主循环世界线程模型)
4. [区块加载机制](#4-区块加载机制)
5. [快照管线](#5-快照管线)
6. [命令管线](#6-命令管线)
7. [插件系统](#7-插件系统)
8. [客户端联动简述](#8-客户端联动简述)
9. [已知性能热点（观察/待优化）](#9-已知性能热点观察待优化)

---

## 1. 模块地图

| 模块 | 职责 |
| --- | --- |
| `projects/oc/oc-rts-engine` | 纯模拟引擎（世界、维度、实体、订单、生产、寻路、区块）。无线程、无墙钟、无第三方依赖。 |
| `projects/oc/oc-game-server` | 宿主服务器：加密 TCP（`SecureChannel`）、HTTP/WebSocket 端点（`ws/`）、鉴权（`auth/`）、插件加载（`plugin/`）、世界宿主（`world/WorldHost`）。 |
| `projects/oc/oc-game-server-classic` | 经典内容插件 `oc-classic`：目录 + 6 个 tick 钩子（收入、bot、工程师池、地狱门、地狱魔物、野外刷怪）。 |
| `projects/oc/oc-googology` | 任意精度自然数 `Natural`（库存数量上限可为 `Natural.BEYOND`，线上饱和为 `Long.MAX_VALUE`）。 |
| `web-client/` | TypeScript 浏览器客户端（Vite/pnpm，独立工具链，与 Java 零依赖交集）。 |
| `protocol/oc-protocol.md` (+`_zh_CN`) | 协议文档；注意其中快照默认半径写的是旧值 32，代码现为 40（见 §4.2）。 |

## 2. 启动流程

入口：`projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/OcGameServer.java#main`。

1. **命令行**：`--port 25566`（TCP）、`--http-port 25567`（HTTP/WS）、`--plugins plugins`、`--data data`、
   `--seed 1`、`--chunks 4096`、`--bots 1`、`--world on|off`、`--ws-idle 300`、`--static-root web-client/dist`。
   解析见 `OcGameServer.parseArguments` 与各 `parse*` 方法。
2. **加载插件**（`start()` 第一步，先于任何 socket）：`pluginLoader.loadAll()` 扫描 `plugins/*.jar`
   （文件名排序，顺序即"加载顺序"），逐 jar 建独立 `URLClassLoader`（自身 jar 优先，其余 jar 兜底），发现顺序见 §7。
3. **选世界内容**：`OcGameServer.selectWorldContent(pluginLoader.worldContents())` —— **先到先得**（第一个注册的
   `WorldContent` 胜出；理由：让"哪个插件拥有世界"成为插件文件夹里可见的属性），无注册则回退引擎示例目录
   `ScenarioWorldContent`。tick 钩子则是**全部保留**、按加载顺序运行（`selectWorldTickHooks`）：
   内容是"对世界的声明"，钩子是"对世界的贡献"。
4. **构建世界**：`new WorldHost(seed, content, tickHooks, chunkBudget)`（`projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java`）。
   构造器里依次：
   - `registryOf(worldContent)` 构建 `ContentRegistry`；
   - `biomeTable(...)` 把目录地形映射到六个规范位（缺 grass 回退首个可建造地形等，见 `WorldHost.biomeTable`）；
   - `OcRtsEngine.builder(content).seed(seed).ticksPerSecond(20).chunkSize(16).viewRadiusChunks(3)`
     `.generator(new NoiseTerrainGenerator(seed, biomes)).infiniteTerrain(seed).maxLoadedChunks(chunkBudget)`
     `.player(player)`，然后对 `worldContent.dimensions()` 逐个 `.dimension(id, profile, NoiseSettings)` ——
     每个维度从世界种子+维度 key 派生自己的种子（`OcRtsEngineBuilder.seedFor()`）；
   - `worldContent.gateways()` 经 `world.addGateway` 注册初始网关；
   - `findSpawnAnchor(world)`：从 (0,0) 按固定环序找第一块可建造瓦片（半径 64 上限，`SEARCH_RADIUS_TILES`）；
   - 订阅 `Events.ItemsHauled` / `Events.ProjectileFired`（搬运动画 pop 与箭矢都是服务器侧的最小呈现状态）；
   - `spawnScene(...)`：按目录摆样本场景（跳过 `denizen` 标记与 `gateway` 标记类型），并把前两个样本单位下移动令；
   - `depositStartingResources(...)`：起始资源必须全部被某仓库接收，否则构造失败。
5. **开 socket**：`server.start()`（加密 TCP），`host.start()`（启动 `oc-world` 守护线程），
   `HttpWebServer`（静态客户端 + `/plugin-assets/` + `/world/snapshot` + `/world/bootstrap` + WebSocket 升级）。
   任何一步失败都会回滚已启动的部分（见 `start()` 中的 undo 分支）。
6. **关停顺序**（`stop()`）：自动存盘定时器 → 插件 `onDisable` → TCP socket → HTTP 端点 → 世界线程 → 最终存盘。
   注意世界线程停在 HTTP 端点**之后**，保证没有请求看到半停的世界。

## 3. 世界主循环（世界线程模型）

### 3.1 线程与锁

- 世界线程 `oc-world`（daemon）在 `WorldHost.runLoop()` 中运行；它只把 `System.nanoTime()` 用作
  **累加器**：攒够一个 `STEP_NANOS`（50 ms）就调用一次 `step()`，单次唤醒最多补 5 步（`MAX_STEPS_PER_WAKE`），
  超出的积压直接丢弃（不螺旋追赶），之后 `Thread.sleep(1ms)`。
- 引擎本身非线程安全。所有读取（`tick()`、`snapshotJson()`）与每一步都在 `stateLock` 内串行化：
  HTTP 请求可以在两步之间抓到一致的世界，绝不出现撕裂的瓦片行。
- 钩子抛异常是致命的：世界线程停止，最后一份一致的快照继续被服务（`runLoop` 的 catch 分支）。

### 3.2 一步做什么（`WorldHost.step()`）

```
synchronized (stateLock) {
    applyPendingCommands();          // 排空命令队列，逐条应用
    engine.tick();                   // 流水线推进一个 tick，末尾 world.flush() 移除死者
    for (hook : tickHooks) hook.tick(world, engine.tickNumber());
}
```

**为什么命令只在 tick 开头跑**：客户端命令到达于 socket 线程，绝不允许在引擎推进中触碰它。
`submitCommand(WorldCommand)` 是唯一入口：在世界线程之外时命令只进
`ConcurrentLinkedQueue<PendingCommand>`（入队也在 `stateLock` 内，防止 `stop()` 排空后还有命令滑入永远等待）；
每个固定步开始时整体排空、逐条应用，于是"命令 + 它影响的那个 tick"构成一个确定性序列。
在世界线程之内调用（钩子、或一个正在应用中的命令）则立即执行——调用者已持有模拟锁。
命令抛出的运行时异常只损失一条答案（变成 `ERR <message>`），不会杀死世界线程。
`drainForTest()` 是不推进时钟的等价排空，供自检确定性地驱动世界。

### 3.3 引擎内的一个 tick（`OcRtsEngine.tick()` → `SystemPipeline`）

系统按 `order()` 相位排序执行（默认装配见 `OcRtsEngineBuilder.build()`）：

| 相位 | 系统 | 文件（`projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/...`） |
| --- | --- | --- |
| 100 | ChunkStreamer（区块流送，见 §4） | `map/ChunkStreamer.java` |
| 200 | OrderSystem（订单队列推进） | `order/OrderSystem.java` |
| 300 | PathService（预算内寻路） | `movement/PathService.java` |
| 400 | MovementSystem（移动/转向） | `movement/MovementSystem.java` |
| 450 | CombatSystem（战斗，紧跟移动） | `combat/CombatSystem.java` |
| 500 | ConstructionSystem（建造） | `production/ConstructionSystem.java` |
| 600 | ProductionSystem（生产队列） | `production/ProductionSystem.java` |
| 700 | HaulingSystem（搬运/直送） | `logistics/HaulingSystem.java` |
| 800 | AnimationSystem（动画） | `visual/AnimationSystem.java` |

`CommandService`（实现 `order/UnitDirector` 接口）与 `ProductionSystem` 同时被安装到 `World` 上，
所以只持有 world 的钩子/场景代码也能下命令、排生产。

### 3.4 WorldTickHook 参与规则

`projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldTickHook.java`：
钩子在每步**末尾**运行（引擎已完整 tick、死者已 flush），在世界线程、`stateLock` 内，按插件加载顺序。
契约：必须确定性（同状态+同 tick → 同变更），不得起线程、等待、文件/网络 IO，不得在调用后保留 world 引用。
classic 的顺序有讲究：`ClassicEconomy`（先付款）→ `ClassicBots`（后决策）→ `ClassicEngineerPool`
（看到 bot 刚排的队列）→ `HellGates` → `HellDenizens` → `WildSpawns`（`ClassicPlugin.onLoad`）。
每个钩子先验证世界确实是 `oc-classic` 目录再动手，因此 classic jar 挂在别的游戏世界里是空转。

### 3.5 确定性播种模式

- 模拟内一切随机都是 splitmix64（`core/DeterministicRandom.java`），按流名派生：
  `DeterministicRandom.forStream(seed, "world")`；`fork(salt)` 派生子流。禁 `java.util.Random`、禁墙钟。
- 地形：`NoiseTerrainGenerator.hash(x, y, octave, salt)` 把种子 ^ 盐 ^ `x*0x9E3779B97F4A7C15`（旋转 29）
  ^ `y*0xC2B2AE3D27D4EB4F`（旋转 31）^ `octave*0xD6E8FEB86659FD93`，再过 SplitMix64 终结器 `mix64`
  （`z^=z>>>30; z*=0xBF58476D1CE4E5B9; z^=z>>>27; z*=0x94D049BB133111EB; z^=z>>>31`）。
- 维度种子：`OcRtsEngineBuilder.seedFor(id)` = `seed ^ 0x9E3779B97F4A7C15` 后对维度 key 逐字符
  `mixed = (mixed ^ c) * 1099511628211`（FNV 式）。
- 插件侧：bot 派生 `BotStrategy.mix64(worldSeed * 31 + i)`（16 方位 × 5 距离环 96..192；
  bot 0 + 默认种子 1 特判回退历史锚点 (192,192) 保重放字节一致）；野外刷怪
  `WildSpawns.rollOf = mix64(seed ^ dim.hashCode ^ x*CHUNK_X_SALT ^ y*CHUNK_Y_SALT)`，
  无符号余数对 10000 为 0 即刷（3/4 地狱犬、1/4 深渊魔，阵营玩家 99）。
- 收入钩子 `ClassicEconomy` 记 `lastPaidTick` 防同一 tick 双付。

**计划中**：所有者计划把敌方基地生成改为专门的"敌方总部（enemy HQ）"生成机制（现行：`ClassicBots`
派生锚点 / bot 0 的历史 (192,192) 锚点 + 引擎常规基地搜索）。写作时该变更尚未落在代码里。

## 4. 区块加载机制

这是无界世界的核心。链条：客户端请求窗口 → 快照移动引擎焦点 → 流送系统按预算加载/卸载 →
LRU 预算兜底 → 客户端对未加载区块画阴影。

### 4.1 地图层：`InfiniteTerrainMap`

`projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/InfiniteTerrainMap.java`

- **任何有符号瓦片坐标都有地形**：`terrainAt(x,y)` 做 floor-div/mod 定位到区块（tile -1 属于区块 -1 的
  本地坐标 15），区块不在内存时先查 `ChunkStore`（默认 `InMemoryChunkStore`），再回退 `ChunkGenerator`
  （`NoiseTerrainGenerator` / 带维度 `NoiseSettings` 的 `NoiseChunkGenerator`）。区块生成是"坐标的纯函数"，
  与邻居无关，所以被逐出的区块再次访问时逐瓦片复现。
- **LRU 预算**：已加载区块放在**访问序** `LinkedHashMap`（`new LinkedHashMap<>(16, 0.75f, true)`）。
  `chunk()/peekChunk()/terrainAt()` 每次成功访问都把该区块标记为最近使用；诊断方法
  （`isChunkLoaded/loadedChunks/loadedBounds` 等）**不**算访问，不扰动淘汰序。超过 `maxLoadedChunks`
  （服务器默认 `DEFAULT_CHUNK_BUDGET = 4096`）即从最老端逐出。逐出与显式 `unload()` 走同一条路：
  脏块先存 `ChunkStore` → 移出内存 → 通知 unload 监听器（`World` 在注册地图时装上，
  发布 `Events.ChunkUnloaded`，带维度与当时 tick）。**预算是内存上限，不是世界大小**。
- 焦点（`focus(center, radiusChunks)`）不影响 LRU 淘汰谁——那是流送系统的策略；预算只是保证。
  有界变体 `ChunkedMap`（默认、无 `infiniteTerrain()` 时）则是"离焦点最远者先逐出"、默认无上限；
  服务器世界永远走 `InfiniteTerrainMap` 分支（`OcRtsEngineBuilder.createMap`）。

### 4.2 客户端如何请求窗口（`cx/cy/r`）

- HTTP 路由 `GET /world/snapshot[?cx=&cy=&r=]`（`ws/HttpWebServer.java`，`WORLD_SNAPSHOT_PATH`；
  `Cache-Control: no-store`；世界停用/停止时 503）。同文件另有一次性引导路由 `GET /world/bootstrap`
  （`WORLD_BOOTSTRAP_PATH`，见 §5.0），从宿主缓存直接应答静态世界数据（添加维度前），带
  `Cache-Control: public, no-cache` 与 ETag（命中 `If-None-Match` 回 304；不再声明 immutable，
  因为维度可变，缓存必须可失效——见 §5.0）。
- 参数解析 → `WorldHost.dimensionByKey()`（可带 `dimension` 维度键，未知则 400）→ `clampSnapshotRadius()`：
  **只有 `r` 被钳制**到 `[MIN_SNAPSHOT_RADIUS=16, MAX_SNAPSHOT_RADIUS=64]`，缺省
  `DEFAULT_SNAPSHOT_RADIUS = 40`（80×80 窗口 = 五个 16 瓦片区块宽；协议文档 `protocol/oc-protocol.md`
  第 525 行仍写默认 32，是文档滞后，代码为准）。`cx`/`cy` 原样接受（任何 int）：`cx=100000&cy=-50000`
  的原点就是 `99984,-50016`；原点仅在 int 溢出边缘饱和——那是 Java 坐标空间极限，不是世界边界。
- 无参数请求以 `spawnAnchor()` 为中心，旧客户端仍能拿到有效文档。
- **读窗口即移动焦点**：`snapshotJson(dimension, cx, cy, r)` 在同一把 `stateLock` 里先调
  `engine.focus(dimension, 窗口中心, 3)`（3 区块半径，与 builder 的 `viewRadiusChunks(3)` 一致），
  再序列化。这是缓存/流送决策：下一个 tick，ChunkStreamer 围绕新焦点开始搬运区块。JSON 本身仍是
  该 tick 的纯读。

### 4.3 流送层：`ChunkStreamer`（相位 100）

`projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/ChunkStreamer.java`

- 每个维度围绕**自己的**焦点独立流送；每 tick 的转换预算跨维度共享（`DEFAULT_CHUNKS_PER_TICK = 4`），
  余量按注册顺序分配（确定性）。
- 每次同步**先卸载后加载**：先丢弃切比雪夫距离超出 `radiusChunks` 的区块（预算内逐个 `map.unload`），
  再按行主序补齐半径内缺失区块（`map.chunk(pos)` 触发按需生成），并发布 `Events.ChunkLoaded`。
- 一次焦点大跳不会卡死 tick：每 tick 只搬 4 个区块，阴影逐块消散。
- `loadNow(world, tick)` 是绕过预算的整块加载（场景/检查用）。

### 4.4 单位、路径与未生成区块

- 寻路（`map/AStarPathFinder.java` + `movement/PathService.java`）直接在地形图上展开：搜索越出已加载
  区域时会**同步按需生成**途经区块（`terrainAt` 触发），LRU 预算保证内存有界；`PathService` 的每 tick
  预算（默认 32 请求 / 20 万节点，builder 默认）把一次大跳的代价摊到多个 tick。
- 客户端不知道"区块未生成"与"区块已卸载"的区别——服务器只发 `loadedChunks`（见 §5），
  客户端把缺失区块一律画阴影（`web-client/src/render/shadow.ts`：α 0.72，加载后每帧 -0.08 渐散）。
  实体**总是全量发送**（绝对坐标、不裁剪到窗口），所以窗口外的单位仍可被跟随/画路径；
  移动单位行带 `path`（剩余折线 ≤64 点）+ `target`，客户端照画——即使它走进阴影区。

### 4.5 每维度的区块库

`OcRtsEngineBuilder.build()` 为**每个**维度各建一份生成器与 `ChunkStore`（注释原文：存储只按区块坐标键控，
共享会让维度互相覆写地形）。overworld 与 hell 因此各自有独立的 `InfiniteTerrainMap`、独立的种子、
独立的空间索引与寻路器（`World.setPathFinder(id, ...)`）。快照的 `dimension` 字段与
`loadedChunks` 都只描述所画的那个维度。

## 5. 快照管线

实现：`WorldHost.buildSnapshotJson()`（在 `stateLock` 内，逐请求重建、无缓存——缓存会与 HUD 显示的
tick 矛盾）。手写 StringBuilder JSON，零依赖。

### 5.0 一次性引导文档（已从快照移出的静态数据）

所有者要求：键值对在第一次加入服务器时下发，可以在刚加入时就下发的数据不要反复拉取。因此快照不再
携带任何静态世界数据；它们全部移到 `WorldHost.bootstrapJson()`（`GET /world/bootstrap`，
`HttpWebServer.WORLD_BOOTSTRAP_PATH`）：

| 字段 | 内容 | 原快照来源 |
| --- | --- | --- |
| `tilePixels` | 16（客户端缩放系数）。 | 快照顶层 `tilePixels`。 |
| `dimensions` | 维度花名册 `[{key,name}]`（切换器数据源，按键序）。 | 快照顶层 `dimensions` 数组。 |
| `buildable` | `isBuildable()` 建筑路径数组（BUILD 菜单数据源，升级专属等级不在内）。 | 快照顶层 `buildable` 数组。 |
| `content` | 原始内容目录（dimensions/items/buildings/units/recipes），形状不变。 | 快照顶层 `content` 对象（缓存复用逻辑也移了过来）。 |

实现要点：

- 文档**构建一次后逐字节复用，直到有人向运行中的世界添加维度**（`bootstrapJson` 缓存字段，
  `stateLock` 内惰性构建；目录与 BUILD 数组来自不可变注册表，但**维度集合是可变世界状态**：
  `World.addDimension(...)` 是公开引擎 API，插件 tick 钩子可以在运行中途调用它）。引擎在注册那一刻
  发布 `Events.DimensionAdded`（`oc-rts-engine` 的 `World.addDimension` 尾部），宿主构造时订阅它：
  版本 `contentVersion`（`volatile long`，从 1 起）加一，同时丢弃 `bootstrapJson` 与
  `contentCatalogueJson` 两个缓存（目录里的 `dimensions` 块也镜像花名册，所以一并失效），
  下一个请求按当前花名册重建。选事件而不是逐 tick 比对维度集合，是因为事件只在真正发生变化的
  那一刻触发，零常态开销，且发布点在维度完全注册之后，订阅者回读世界看到的是一致状态。
  构造期订阅在 builder 添加内容维度之后，所以启动期的注册不会抬版本。
- 应答带 `Cache-Control: public, no-cache` 与对字节哈希的 `ETag`；`If-None-Match` 命中回 304。
  原来的 `max-age=31536000, immutable` 已废除：维度可变，一份"缓存一年"的承诺会让浏览器永远
  端着过期的花名册。`no-cache` = 可存储但每次使用前用 ETag 重验证——世界不变时每次页面加载
  只花一个小 304，版本变化改变字节、字节改变 ETag，下次重验证即得全新 200。
  世界未运行时与快照同样回 503（不落入 SPA 回退），仅 GET/HEAD。
- `dimensionName` 保留在快照里：静态但只有几个字节，且它随所画窗口同行，客户端不必跨文档查表。
- **`contentVersion`（两份文档都带，快照顶层、引导文档顶层各一个整数）**：宿主构建时从 1 开始，
  每次运行中注册维度加一（无移除 API，永不减少）。快照每轮带着它，是为了让缓存了引导文档的
  客户端从下一次轮询就能发现版本不一致。
- 实测收益（默认场景世界，80×80 窗口）：移出的静态块约 3.0 KB / 旧快照约 53 KB（≈5.7%）；
  经典目录更大（15 建筑 + 22 单位 + 20 配方），约 9.5 KB（≈14.6%）。这是每轮轮询省下的
  字节数与缓存字符串的重复拼接；快照主体（tiles/entities）本来就是动态的，仍逐请求重建。
- 客户端契约：首次加入（快照路由 200）时拉取一次并记录文档里的 `contentVersion`；此后每次快照
  轮询比对快照的 `contentVersion`——相等就继续用缓存；不一致就**重取一次**引导文档，重建维度
  切换器等派生态，并更新记录值（不要循环：重取后的下一份快照携带的就是刚拉到的版本；重取后仍
  不一致是服务器 bug）。重取失败保留旧文档照常可玩。字段全部按可选解析。协议文档
  "世界引导"一节是权威形状定义与握手契约（en+zh 同步）。

### 5.1 顶层字段

| 字段 | 内容 |
| --- | --- |
| `tick` | 当前引擎 tick。 |
| `contentVersion` | 静态世界载荷版本（§5.0）；与引导文档同源，客户端用它察觉维度花名册变化并重取引导文档一次。 |
| `originX`/`originY`/`width`/`height` | 窗口左上瓦片与尺寸（`2r×2r`）；文档**没有** mapWidth/mapHeight——世界无界。 |
| `dimension` / `dimensionName` | 所画维度键与显示名（静态但极小，随窗口同行）。 |
| `loadedChunks` | 所画维度内存中的区块坐标 `[[cx,cy],...]`，**上限 1024 项**（防病态预算撑爆文档）。客户端据此画未加载阴影。 |
| `tiles` | 行主序的窗口地形内容路径（`width*height` 个字符串）；每瓦片一次 `terrainAt`。 |
| `entities` | **全部**存活实体（本维度内），按 id 排序，不裁剪到窗口。 |
| `projectiles` | 最近 4 tick 内发射的箭矢 `[{id,x,y,tx,ty}]`（上限 128，服务器侧呈现状态）。 |
| `players` | 全部玩家行（含 bot）。 |

原表中的 `tilePixels`、`dimensions`（花名册）、`buildable` 与 `content` 目录已移到 §5.0 的一次性引导文档，快照不再逐轮重复。

### 5.2 实体行字段

基础：`id`、`kind`（`Entity.typeKey().path()`，如 `worker`、`building.armory.t1`）、`x`/`y`（一位小数，绝对坐标）、
`player`、`health`（0..1）。随后各 append 方法按实体种类添加：

- `appendFootprintProgressAndQueue`：`w`/`h`（脚印；单位恒为 1×1）、`progress`（建筑=施工比例，未完工钳到
  [0.01,0.99]；单位=生命比例）、`queue`（未完成作业数）、`queueTicks`（仅当前作业处于 `PRODUCING` 时）。
- `appendProducingNow` / `appendDemand` / `appendQueueItems`：`producingNow {kind,position,length}`、
  `demand {ready,items:[{item,need,have}]}`（have 上限 need；stock 逻辑见 `ProductionQueue.demand()`）、
  `queueItems [{kind,ticks,total}]`（未开始的作业 ticks=total，不编造倒计时）。
- `appendContents`：`contents`（建筑 `inventory()` / 单位 `cargo()` 的紧凑 `path:amount` 对；空则省略）；
  仓库另带 `storage {slots,used,free}`（空仓库也带）与 `inbound`（≥1 条以该仓库为目的地的待处理搬运作业时）。
- `appendUpgradeOffer`：`upgrade {options:[{to,gold,wood}]}` —— 只列**适用**后继（目标存在、同脚印、
  允许站在该维度）；未完工/顶级/无后继省略。可负担性**不**编码（客户端对照自己玩家行来禁用条目）。
- `appendTrains`：`trains [{path,name,gold,level}]` —— 完工生产者按自身等级过滤出的配方菜单。
- `appendBuildingServices`：`repair {gold,wood}`（完工+受损+有修理价）、`reclaim {gold,wood}`（未完工工地，
  半价退款，免费工地也带 `0,0`）。
- `appendCombat`：`role`（恒有，`combat/worker/support`）、`builder:true`（`builder` 标记）、
  `attackDamage`/`attackRange`（可攻击者）。
- `appendUnitPath`：`path [[x,y],...]`（剩余 ≤64 点）+ `target [x,y]`。
- `appendTask` / `appendOrderQueue`：`task`（工程师一行任务）、`orderQueue`（订单标签数组，封顶 8 + `… 还有 N 项`）。
- `appendPop`：`pop {kind,tick}`（最近搬运事件，20 tick TTL；客户端按 tick 去重播 600 ms 动画）。
- `appendGatewayTarget`：`gateway {to,arrival}` —— `gateway` 标记建筑，经 `World.route()` 找到可达维度时给出
  跳转目标；无路由省略（客户端不显示跳跃按钮）。

玩家行：`id`、`name`、`gold`、`wood`（读自 `player.logistics().available()`——未入仓的物品不可支出）、
`goldPerMinute`、`woodPerMinute`（收入标签之和）、可选 `anchor [x,y]`（派系锚点）。

### 5.3 "客户端可自行计算"的派生数据（计划中）

所有者有把 HUD 类派生计算**移到客户端**的常设计划。当前由服务器在每次快照中计算、且只依赖
目录数据 + 玩家建筑/作业清单（客户端原则上也拿得到）的候选字段：

| 字段 | 现行计算位置 | 移动候选的理由 |
| --- | --- | --- |
| `goldPerMinute` / `woodPerMinute` | `WorldHost.incomePerMinute()`（每次快照扫描全部玩家建筑 ×2） | 纯标签求和，是 classic 经济钩子同款公式的复读。 |
| `trains` 菜单 | `WorldHost.appendTrains()`（按等级过滤 `content.recipesFor`） | 纯目录过滤，客户端已有 `buildable`+清单可自行推导。 |
| `upgrade.options` 的适用性过滤与 gold/wood 拆解 | `WorldHost.appendUpgradeOffer()` | 过滤规则（存在/同脚印/允许维度）都是目录查询。 |
| `repair` / `reclaim` 金额 | `WorldHost.appendBuildingServices()` | `repairCost()`/`reclaimRefund()` 的金木拆解。 |
| `inbound` 计数 | `buildSnapshotJson` 顶部对 `hauling.jobs()` 的整表扫描 | 呈现性计数。 |
| `orderQueue` 标签 / `task` | 引擎 `OrderLabels`/`UnitTask`（已经是引擎侧共享实现，每实体行调用） | 中文本地化标签，属呈现层。 |

**注意**：以上是候选清单，今天的代码确实都在服务器端逐请求计算；迁移前不要假设客户端会自行推导。
§5.3 的候选没有随引导文档移动——本节列出的字段仍是逐请求计算的动态派生值；移动到引导文档的是
§5.0 表中的纯静态块（`content`/`buildable`/`dimensions`/`tilePixels`），它们本是候选字段的自算输入，
现在客户端首次加入时即可拿到，之后不必再从任何轮询中获取。

## 6. 命令管线

### 6.1 传输与鉴权

- 浏览器走 WebSocket（`ws/CommandWebSocketHandler.java`）：一文本帧一命令行；**先发版本行 `HELLO 1`**，
  服务器答 `OK OC/1`，否则拒绝（这是浏览器通道的游戏协议版本机制；加密 TCP 通道用握手里的版本字节，
  见 `net/SecureChannel.java`，OC/2，GCM，60 秒防重放窗口）。空闲超时 300 s，服务器每 25 s 发 ping。
- **账号即身份**：`LOGIN <name> <password>` 成功后 `Session.markAuthenticated(name)`
  （PBKDF2-HMAC-SHA256，21 万次迭代，128 位随机盐；`auth/Pbkdf2PasswordHasher.java`）。
  每条游戏命令在世界线程内以账号名调 `WorldHost.joinPlayer(name)`：首次绑定最小空闲人类槽（3..8），
  同名重连返回**同一**玩家（基地、实体、库存全保留）；断线**不回收**（回收会让实体无主或换主）；
  槽满答 `ERR world full`。玩家 id 缓存在 `Session.playerId`，失效时重新向世界查询。
- `REQ <name> <base64>` 是唯一能让插件干活的命令，所以也要求先登录（`CommandDispatcher.request()`）。

### 6.2 动词表（`CommandDispatcher`，与传输无关）

| 命令 | 形状 | 世界线程内的实际事务 |
| --- | --- | --- |
| `ORDER MOVE` | `<id> <x> <y>...[queue]`（1..8 点路线） | `UnitDirector.moveRoute` / `queueMoveRoute` |
| `ORDER ATTACK` | `<id> <targetId> [queue]` | `commands().attack` / `queueAttack` |
| `ORDER ATTACKMOVE` | `<id> <x> <y> [queue]` | `attackMove` / `queueAttackMove` |
| `ORDER STOP` | `<id>` | `commands().stop` |
| `BUILD` | `<engineerId> <buildingPath> <x> <y> [queue]` | `tryBuild`/`tryQueueBuild`（收费、选址、拒绝原因逐字转发） |
| `TRAIN` | `<buildingId> <unitPath>` | `production().enqueue`（等级门槛由引擎拒绝） |
| `UPGRADE` | `<buildingId> [variantPath]` | `upgradeBuilding` → `OK <newBuildingId>` |
| `UPGRADEUNIT` | `<unitId> [tierPath]` | `upgradeUnit` → `OK <newUnitId>` |
| `REPAIR` | `<engineerId> <buildingId> [queue]` | `tryRepair`/`tryQueueRepair` |
| `RECLAIM` | `<buildingId>` | `tryReclaim` → `OK <退款描述>` |
| `TRANSFER` | `<sourceId> <targetId> <item> <amount|all>` | `UnitDirector.transfer`（同玩家/同维度/完工仓库/可达；预留并派真实搬运工）→ `OK <moved> <item>` |
| `HAUL` | `<engineerId> <buildingId>` | `tryHaul`（清空该工程师当前订单，预留首缺料作业输入并指派）→ `OK <reserved>` |
| `BATCH` | `<cmd>; <cmd>; ...`（≤16，仅可批命令） | 逐条经普通 `submit` 路径按序执行；`ERR <i>/<n> <reason>` 报首个失败，其余照常应用 |

所有游戏命令：未登录 → `ERR login first`；世界缺失/未运行 → `ERR world not available|running`；
参数/实体/所有权的固定错误串见 `CommandDispatcher` 常量（`ERR bad arguments`、`ERR unknown entity`、
`ERR not yours`、`ERR unknown building|unit|item`）。引擎拒绝原因逐字转发，客户端直接红字显示。

### 6.3 到世界线程的路径与应答

```
socket 线程: dispatcher.execute(line, session)
  → worldGameplay(): host.submitCommand(world -> { ... })   // 只入队
  → CommandHandle.await(5000ms)                             // COMMAND_TIMEOUT_MILLIS
世界线程 step(): applyPendingCommands() → command.apply(host) → CompletableFuture<String>
  → 答案 "OK ..." / "ERR ..."；超时答 "ERR world busy"
```

解析（形状、id、路径查找）发生在 socket 线程，但**存在性/所有权/费用/经济**全部在队列命令应用时判定
——实体可能在提交与执行之间死亡或易主，答案描述的是命令真正遭遇的状态。`BATCH` 的子命令在提交时即
逐条入队（保持提交顺序 = 应用顺序），`PendingAnswer.combined` 汇总各答案。

## 7. 插件系统

### 7.1 发现与生命周期（`plugin/PluginLoader.java`）

- `plugins/` 目录**就是依赖机制**：每个 jar 的类路径 = 自身 jar 优先 + 其余 jar 兜底（文件名排序）；
  父加载器是加载 `Plugin` 接口的那个（父优先委托，插件无法替换服务器 API）。同一库只能存在一个版本，
  除非插件自带（自身 jar 优先使其获胜）。类加载器**永不关闭**（关闭会打断惰性查找；代价是禁用插件的类
  留在内存直到进程结束）。
- 发现顺序（逐 jar，全部记录问题不中断）：① `META-INF/services/io.github.sob1234509876.oc.server.plugin.Plugin`
  ② jar 根的 `omnicom-plugin.properties`（`PluginDescriptor`：`id`/`version`/`main`）
  ③ 全 jar 类扫描（排除 `module-info`/`package-info`/内部类，排序保证可复现）。
- 生命周期：`onLoad(context)`（注册内容、钩子、请求；**此阶段失败则插件不可见**）→ `onEnable()`。
  `onEnable` 失败会补调 `onDisable`。插件按**类名排序**构造/加载/启用，同一文件夹产出相同启动序列。
- 数据目录：`data/<pluginName>/`（服务器构造器传入服务器自己的 data 根，插件区保持纯 jar 拖放区）。
- 服务器选项经 `PluginContext.options()` 下发（今天只有 `bots`）。
- `unloadAll()`：逐插件 `onDisable`，清空插件表、worldContents、worldTickHooks、插件→加载器映射
  （资产随之停止被服务），**不**关类加载器。

### 7.2 请求注册表（REQ）

插件经 `context.requests()` 注册 `RequestHandler`（名字 ≤32 字符）；socket 的 `REQ` 在同一 `RequestRegistry`
上查找并 base64 中继。处理器抛异常只损失一条回复（`ERR request failed`），日志不含载荷。

### 7.3 插件资产（`ws/PluginAssets.java`、`PluginManifest.java`）

- 路由：`/plugin-assets/manifest.json`（合并清单）与 `/plugin-assets/<plugin>/<file>`（单文件）。
- 合并清单是**数组**：每个发布了 `assets/manifest.json` 的启用插件一个元素，按加载顺序
  `{plugin, baseUrl, tilePixels, tiles, entities}`；表内路径保持相对（客户端拼 `baseUrl`），服务器不解析帧表
  （只验证骨架：JSON 对象、数字 tilePixels、tiles/entities 为对象——否则跳过该插件并记日志）。
  无任何插件发布时答 `[]`（200），不是 404。
- 读取**完全不碰文件系统**：`ClassLoader.getResourceAsStream`。归一化资源名必须落在 `assets/` 下
  （否则 403），且必须来自插件**自己的** jar（比较加载器首个 URL 前缀，防止借邻居 jar 的清单/文件）。
  路径拒绝：编码点 `%2e`、`..`、反斜杠、NUL、`:`（Windows 盘符）、空插件 id → 400/403/404。
- 缓存策略一律 `Cache-Control: no-cache`（插件 jar 在调素材时常被替换）。
- 客户端按序应用数组，后者覆盖前者同名瓦片路径/实体 kind；冲突消解规则只在客户端一处。

## 8. 客户端联动简述

- **消费快照**：`web-client/src/main.ts` 每 500 ms（`WORLD_POLL_MS`）`fetch /world/snapshot`（带当前维度键）；
  请求失败回退内置确定性 demo 世界，HUD 显示 `server|demo + tick`。Vite 开发代理把 `/world` 转发到 Java 服务器。
  静态世界数据（目录、BUILD 数组、维度花名册、tilePixels）改为首次加入时 `fetch /world/bootstrap` 一次
  （§5.0）；此后每轮轮询比对快照顶层的 `contentVersion` 与记录值，不一致时重取一次引导文档并重建
  维度切换器等派生态（§5.0 的版本握手）；快照轮询不再携带静态块，客户端要防御性解析引导文档并容忍其缺失。
- **防御性解析**：每个字段容错——`loadedChunks` 缺失（旧服务器）不画阴影；数值非有限即回退默认；
  未知 `kind` 回退程序化形状绘制（`render/fallback.ts`）。客户端把世界当作无界：没有地图尺寸概念。
- **图集**：`render/atlas.ts` 解析合并清单，每材质一图（PNG 或 SVG，SVG 需自带 width/height；
  仓库工具 `tools/redraw_assets_svg.py` 保证），实体为竖条帧图；`render/interpolate.ts` 在最近两个快照间插值，
  渲染循环每帧绘制（`render/renderer.ts`）；箭矢、pop、阴影渐散等效果跨快照持有状态。
- **命令发送**：`src/commands.ts` 组装协议行（含 `queue` 尾 token 与 `BATCH` 序列），
  经 `src/socket.ts` 的加密通道/`src/protocol.ts` 的 OC/2 信封走 WebSocket 或 TCP。
- **跟随/选择**：`render/follow.ts`（跟随锁定：选中恰一个非建筑实体时可锁定相机，白色呼吸环标记被跟随者；
  目标离开快照即解锁并提示）；`render/marquee.ts`/`selection.ts`/`picking.ts`（框选优先级：己方战斗 >
  工程师 > 建筑，敌/中立不可框选）；`render/paths.ts`（选中己方单位的剩余路线虚线，Shift+右键追加路点，
  Esc 清空；敌人无路线覆盖）。
- **计划中**：所有者计划把 §5.3 所列 HUD 类派生计算移到客户端计算（快照字段会随之精简）；
  写作时这些字段仍由服务器计算。

## 9. 已知性能热点（观察/待优化）

> 以下均为对当前代码的诚实观察，标注"观察"者为读写成本分析，未做基准测量；"待优化"表示已有更优做法可换。

1. **快照序列化（热，每次轮询）** —— `WorldHost.buildSnapshotJson()` 每请求重建完整文档
   （默认 80×80 窗口约 50 KB，128×128 上限约 130 KB + 每移动单位 ≤64 路点）。500 ms 轮询 × 每玩家一条连接时，
   这是 O(窗口瓦片数 + 实体数 × 行字段数) 的字符串工作，且 `entities()`、`units()`、`buildingsOf(player)`、
   `incomePerMinute()`（×2 资源）、`inbound` 作业扫描都在其中。`appendTask`/`appendOrderQueue`
   对每个单位行构造标签。**已做**：静态不变部分（`content` 目录、`buildable`、维度花名册、tilePixels）
   已移到一次性的 `/world/bootstrap` 引导文档（§5.0），不再随每轮轮询序列化。
   **待优化方向**：HUD 派生字段迁移（§5.3 计划中）、差异更新。
2. **窗口读取触发同步区块生成（热，取决于相机移动）** —— `snapshotJson` 读取 `width*height` 个瓦片，
   每个未加载瓦片经 `terrainAt` → `loadedChunk` 同步生成整个 16×16 区块（每区块约 4600 次格点哈希）；
   相机每跳 80 瓦片最多新引入约 25 个区块的同步生成（焦点半径 3 区块流送只摊每 tick 4 个）。
   LRU `evictExcess()` 又在每次 `loadedChunk` 插入后检查。**观察**：噪声生成单区块亚毫秒，但首次进入
   新区域的那次快照会一次生成焦点外的窗口区块。
3. **寻路（热，大量工程师同时受命时）** —— `AStarPathFinder` 在无界地图上展开；预算
   （32 请求 / 20 万节点每 tick）把代价摊开，但同 tick 内多个搜索共享节点预算，先到者吃满，
   后来者顺延——大量 BUILD 指令（每个工程师一条去工地路径 + 搬运路径）会在预算下排队，
   表现为路径延迟而非 tick 卡顿。`PathService` 每单位只留最新请求、结果落空即弃，防止雪崩。
4. **生产/搬运的每 tick 全表工作（中）** —— `ProductionSystem`、`HaulingSystem`、classic 钩子
   （`ClassicEconomy` 扫全部建筑、`WildSpawns` 扫全部单位、`ClassicAi` 排序后扫描）都是每 tick
   线性扫描实体集合；`CommandService` 的自由瓦片搜索（环序 `scanFreeOrigin`，半径上限 64）发生在
   每次建造/升级命令的世界线程应用里。**观察**：实体数在千级时仍可接受，万级需要索引
   （`GridSpatialIndex` 目前只服务邻居查询，不服务"全部建筑"类聚合）。
5. **freeTile/freeOrigin 的 O(实体) 检查（低，启动/加入时）** —— 起始基地搜索对每个候选瓦片扫描全部
   建筑与单位（`WorldHost.freeOrigin`/`freeTile`）；仅在世界构造与玩家加入时运行，非每 tick。
6. **每 tick 5 步上限 + 1 ms 睡眠** —— `runLoop` 在负载下丢积压（`accumulator=0`），保证延迟不螺旋，
   但慢机器上模拟速率会**低于** 20 tick/s（tick 数落后于墙钟）；客户端 HUD 的 tick 显示即真实进度。
   这是设计取舍而非缺陷。

---

## 附：快速事实卡

| 事实 | 值 | 代码位置 |
| --- | --- | --- |
| tick 速率 | 20/s（50 ms） | `WorldHost.TICKS_PER_SECOND` |
| 命令超时 | 5000 ms → `ERR world busy` | `CommandDispatcher.COMMAND_TIMEOUT_MILLIS` |
| 快照半径 | 默认 40，钳 16..64；中心不钳 | `WorldHost.DEFAULT_SNAPSHOT_RADIUS`、`clampSnapshotRadius` |
| 区块大小 | 16 瓦片 | `NoiseTerrainGenerator.DEFAULT_CHUNK_SIZE` |
| 区块预算 | 4096（`--chunks N`） | `InfiniteTerrainMap.DEFAULT_MAX_LOADED_CHUNKS` |
| 流送预算 | 4 区块转换/tick（跨维度共享） | `ChunkStreamer.DEFAULT_CHUNKS_PER_TICK` |
| 焦点半径 | 3 区块（builder 与快照一致） | `OcRtsEngineBuilder.viewRadiusChunks(3)` |
| 人类槽 | 玩家 3..8（6 人）；bot 0=玩家 2，额外 bot ≥9；野怪阵营 99 | `WorldHost`、`ClassicBots`、`HellDenizens` |
| 端口 | TCP 25566，HTTP/WS 25567 | `OcGameServer.main` 帮助文本 |
| 起始资源 | 每名玩家 300 金 / 60 木（classic 目录声明） | `ClassicContent.STARTING_RESOURCES`、`WorldContent.startingResources()` |
| 收入周期 | 60 tick = 一"分钟" | `ClassicEconomy.PAYOUT_INTERVAL_TICKS` |
