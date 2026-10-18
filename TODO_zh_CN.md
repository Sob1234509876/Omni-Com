[English](TODO.md) | 简体中文

# Omni Com 交接文档

截至 `HEAD 3abc25d`（2026-10-18）。本文是冷启动简报：仓库是什么、怎么证明它健康、怎么把游戏跑起来、七个已报告问题的推荐修复顺序、真实的欠账、值得做的后续功能，以及保持仓库可构建的规则。

## 1. 这是什么

Omni Com 是一个 Gradle monorepo，包含六个 Java 模块——`projects:oc:oc-googology`（任意精度 `Natural` 大数库）、`projects:oc:oc-rts-engine`（无界面的 RTS 模拟引擎）、`projects:oc:oc-game-server`（账号、加密 TCP 信道、插件加载器与浏览器端点）、`projects:oc:oc-game-server-debug`（`oc-debug`，只读插件与 CLI）、`projects:oc:oc-game-server-hello`（`oc-hello`，最小插件与默认图集）、`projects:oc:oc-game-server-classic`（`oc-classic`，五级兵种、经济钩子与敌人 AI）——全部是 Java 17 + JPMS、**零第三方依赖**、Gradle 可离线构建；[`web-client/`](web-client/README.md) 是独立的 pnpm 工程（TypeScript + Vite，无框架），通过 WebSocket 与 HTTP 和服务器通信。两条工具链并行：Gradle 从不调用 npm/pnpm，pnpm 也从不调用 Gradle。

## 2. 当前状态

在仓库根目录运行全部六个 Java 自检。仓库自带 `gradlew.bat`；只有 `PATH` 上有 Gradle 安装时才用 `gradle.bat`：

```powershell
gradle.bat --offline --no-daemon --console=plain :projects:oc:oc-googology:selfcheck :projects:oc:oc-rts-engine:scenario :projects:oc:oc-game-server:selfcheck :projects:oc:oc-game-server-debug:selfcheck :projects:oc:oc-game-server-hello:selfcheck :projects:oc:oc-game-server-classic:selfcheck
```

客户端检查是分开的。`node` 必须是 24+，因为脚本使用 Node 的 TypeScript 类型剥离：

```powershell
cd web-client; .\node_modules\.bin\tsc.cmd --noEmit; node scripts/check-render-math.ts
```

| 检查 | Gradle 任务 | 总数 | 总数来源 |
| --- | --- | --- | --- |
| googology | `:projects:oc:oc-googology:selfcheck` | 215 | [README](projects/oc/oc-googology/README.md) 的自检行；`GoogologyCheck` 末尾打印 `googology self check: all 215 checks passed.` |
| engine | `:projects:oc:oc-rts-engine:scenario` | 239 | [README](projects/oc/oc-rts-engine/README.md)；[help/11](projects/oc/oc-rts-engine/help/11-limits-and-faq.md) 与 [help/13](projects/oc/oc-rts-engine/help/13-dimensions.md) 引用同一总数 |
| game server | `:projects:oc:oc-game-server:selfcheck` | 633 | [README](projects/oc/oc-game-server/README.md)；十一个检查类之和：`AuthCheck` 65、`NetworkCheck` 57、`PluginCheck` 76、`RequestCheck` 43、`WebSocketCheck` 63、`HttpCheck` 47、`PluginAssetsCheck` 80、`WorldHostCheck` 82、`WorldTickHookCheck` 23、`PlayerCommandCheck` 57、`JoinBaseCheck` 40 |
| debug | `:projects:oc:oc-game-server-debug:selfcheck` | 82 | [README](projects/oc/oc-game-server-debug/README.md)；四节：信封 15、密钥与报告 34、请求注册表 14、真实 socket 19 |
| hello | `:projects:oc:oc-game-server-hello:selfcheck` | 126 | [README](projects/oc/oc-game-server-hello/README.md)；五节，含贴图断言 |
| classic | `:projects:oc:oc-game-server-classic:selfcheck` | 286 | [README](projects/oc/oc-game-server-classic/README.md)；十二节，从内容目录（82）到插件加载器（12） |
| client render math | `node scripts/check-render-math.ts` | 110 | 由 [check-render-math.ts](web-client/scripts/check-render-math.ts) 里的 `check(...)` 调用逐条数出；脚本最后打印通过总数 |

**任何提交都不得让这些检查变红；也不得为了让新功能通过而删除或削弱任何现有检查。** 如果总数变了，grep 旧数字，更新每一份引用它的文档。

## 3. 怎么跑起来看

1. 构建经典插件 jar 与它运行期需要的两个 jar：

```powershell
gradle.bat --offline --no-daemon --console=plain :projects:oc:oc-game-server-classic:jar :projects:oc:oc-rts-engine:jar :projects:oc:oc-googology:jar
```

2. 把这三个 jar 原样复制到服务器的 `plugins/` 拖放区：

```powershell
Copy-Item projects/oc/oc-game-server-classic/build/libs/oc-game-server-classic-2.0a.jar plugins/
Copy-Item projects/oc/oc-rts-engine/build/libs/oc-rts-engine-2.0a.jar plugins/
Copy-Item projects/oc/oc-googology/build/libs/oc-googology-2.0a.jar plugins/
```

3. 从仓库根目录启动服务器：

```powershell
gradle.bat --offline --no-daemon --console=plain :projects:oc:oc-game-server:server
```

它监听 TCP `25566` 与 HTTP/WS `25567`。`server` 任务以仓库根目录为工作目录，所以默认静态根能正确解析。

4. 在另一个终端启动客户端：

```powershell
cd web-client; pnpm install; pnpm dev
```

`pnpm install` 只需要一次，并且需要网络；之后 `pnpm dev` 可离线运行。打开它打印的地址（通常是 `http://localhost:5173/`），把**主机与端口两个输入框都留空**（Vite 代理会把 `/ws`、`/world` 与 `/plugin-assets` 转发到 `127.0.0.1:25567`），然后先点 `REGISTER`，成功后再点 `LOGIN`。

5. 新登录的玩家拥有一座仓库、一名工程师、**300 金币**和 **60 木材**——也就是 `oc-classic` 的开局资源。玩家 1 是服务器的示例场景，玩家 2 是红色的 `oc-classic` AI，你的第一个玩家 id 是 `3`。`/world/snapshot` 一旦有回答，画面就会从确定性占位地图切换到实时快照。

## 4. 七个已报告的问题

按下面的顺序修。前三个是坏掉的行为，代价小；中间三个是可用性；最后一个是大改。

每一节都使用同样的六个字段：**现象**（Symptom）、**原因**（Cause）、**建议对策**（Fix）、**验收标准**（Acceptance）、**涉及文件**（Files）、**代价**（Cost）。

| 顺序 | 问题 | 代价 |
| --- | --- | --- |
| 1 | c — 每次登录都会新建一个阵营 | 小 |
| 2 | e — 空闲约一分钟就断线 | 小 |
| 3 | d — 没有翻译，界面显示 `unit.melee.t1` | 小 |
| 4 | a — 资源看不见 | 小 |
| 5 | b — 敌我难分 | 小 |
| 6 | f — 移动不平滑 | 中 |
| 7 | g — 没有无限地图 | 大 |

### c. 每次登录都会新建一个阵营

**现象。** 用同一个账号退出再登录，或者重新连接，`WHOAMI` 会回答另一个玩家 id；新玩家又拿到一座仓库、一名工程师和 300 金币 + 60 木材，而旧基地还留在世界里。

**原因。** `WorldHost.joinPlayer(String)` 从 3 扫到 `MAX_PLAYERS`（8），跳过世界里已存在的 id，并且世界存活期间从不复用 id。`CommandDispatcher` 在每次成功 `LOGIN` 时把这条连接缓存的 id 清零，于是下一条游戏命令又会调用 `joinPlayer`。账号名只被当作显示名保存，没有账号到 id 的映射；旧玩家是刻意不删除的，因为它的实体还需要归属。

**建议对策。** 在 `WorldHost` 里加一张账号名到玩家 id 的映射，归世界线程所有。`joinPlayer` 对已知名字返回已有玩家，只有新名字才分配最小未用 id。重连时保留基地、实体和资源不动；映射随世界一起清理。如果某个名字映射到的玩家被未来的重置删除，必须显式拒绝，而不是悄悄重新分配。

**验收标准。** `LOGIN alice` → `OK player 3`；断开；`LOGIN alice` → 仍然是 `OK player 3`，同一座仓库、同一名工程师、金币/木材与断开前一致，也没有第二座基地；`LOGIN bob` → `OK player 4`；第七个不同账号仍然得到 `ERR world full`。`JoinBaseCheck`（40 条）保持全绿，并新增一条重连复用断言。

**涉及文件。** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java)、[CommandDispatcher.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/net/CommandDispatcher.java)、[JoinBaseCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/JoinBaseCheck.java)。

**代价。** 小。

### e. 空闲约一分钟就断线

**现象。** 页面开着、不点任何东西，大约 60 秒后 WebSocket 以 `1001` 关闭；日志显示连接已断开。

**原因。** 浏览器端点的 socket 读超时同时充当 WebSocket 空闲超时，`WebSocketConfig.DEFAULT_IDLE_TIMEOUT_MILLIS` 是 `60_000`。`HttpWebServer` 用 `setSoTimeout` 应用它，`WebSocketConnection` 在读取超时时以 `1001` 关闭。客户端只在用户点按钮时发 `PING`；`/world/snapshot` 是普通 HTTP，不会刷新 WebSocket。

**建议对策。** 在 socket 打开期间每 20 秒发一次客户端 `PING` 并要求 `PONG`，走与其他命令相同的按序 `send` 队列，关闭时清掉定时器。给 `OcGameServer` 加 `--ws-idle N` 选项，把它传进 `WebSocketConfig`，默认五分钟（`300_000` 毫秒），并校验 `N > 0`。可选地再让服务器发 ping 帧作为第二道保险；客户端已经能容忍意外的单行回答。

**验收标准。** 默认配置下页面空闲六分钟仍保持连接，HUD 继续更新；日志里大约每 20 秒出现一次 `PING`/`PONG`；`--ws-idle 60` 能复现旧的 60 秒关闭，证明确实接上了这个开关；`WebSocketCheck`（63 条）保持全绿，并新增心跳与开关检查。

**涉及文件。** [WebSocketConfig.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/ws/WebSocketConfig.java)、[OcGameServer.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/OcGameServer.java)、[HttpWebServer.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/ws/HttpWebServer.java)、[main.ts](web-client/src/main.ts)、[socket.ts](web-client/src/socket.ts)、[WebSocketCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WebSocketCheck.java)。

**代价。** 小。

### d. 没有翻译，界面显示 `unit.melee.t1`

**现象。** HUD 选中行和 TRAIN/BUILD 按钮显示原始内容路径，比如 `unit.melee.t1`、`building.armory.t1`；目录里的中文名（普通、兵营·普通）从未到达页面。

**原因。** 快照里的 `kind` 是引擎内容路径，客户端原样打印：HUD 用 `selected.kind`，面板按钮用 `unitPath` / `buildingPath`。引擎目录确实带显示名（`.name("普通")`），但快照不包含它们，`assets/manifest.json` 也没有名字字段；`atlas.ts` 只解析帧表与它们的几何参数（`fps`、`anchor`、`scale`），没有显示名字段。

**建议对策。** 扩展贴图清单：实体用 `entities.<kind>.name`，地形用 `tiles.<path>.name`。地形条目现在是 `[x, y, w, h]`，所以同时接受旧数组与新对象 `{"rect": [x, y, w, h], "name": "..."}`；实体条目本来就是对象，在 `frames` 旁加可选的 `name`。服务器不解释这些字段，它本来就把清单原样转发。`atlas.ts` 把名字带进合并后的图集（后加载的插件覆盖前面的），`main.ts` 用名字显示、用内容路径兜底。给 `oc-classic` 与 `oc-hello` 的清单补名字，并更新贴图契约文档。

**验收标准。** 加载经典 jar 后，HUD 显示 `e12 普通`，TRAIN 按钮显示 `普通` / `强化` / `精英` / `将领` / `战王` / `工程师`，BUILD 按钮显示 `兵营·普通` / `矿场·普通`；清单里没有名字时同一行回退到 `unit.melee.t1`（绝不能出现 `undefined`）；`check-render-math` 增加名字合并与回退检查；110 条保持全绿。

**涉及文件。** [atlas.ts](web-client/src/render/atlas.ts)、[main.ts](web-client/src/main.ts)、[ClassicContent.java](projects/oc/oc-game-server-classic/src/main/java/io/github/sob1234509876/oc/server/classic/ClassicContent.java)、[manifest.json](projects/oc/oc-game-server-classic/src/main/resources/assets/manifest.json)、[贴图 README](web-client/public/textures/README.md)、[贴图 README 中文](web-client/public/textures/README_zh_CN.md)、[check-render-math.ts](web-client/scripts/check-render-math.ts)。

**代价。** 小。

### a. 资源看不见

**现象。** HUD 不显示金币或木材；玩几分钟后玩家不知道为什么 `BUILD` 或 `TRAIN` 回答 `ERR cannot afford ...`。开局库存确实存在，但完全不可见。

**原因。** `WorldHost.snapshotJson()` 只发 `tick`、`width`、`height`、`tilePixels`、`tiles` 与 `entities`；玩家库存没有序列化。`worldViewFromSnapshot` 没有 `players` 字段可读，`main.ts` 也没有资源 HUD 行。

**建议对策。** 给 `/world/snapshot` 加 `players` 数组，例如 `[{"id":3,"name":"alice","resources":{"gold":300,"wood":60}}]`，从每个玩家的 `logistics().available()` bundle 构建；只发当前内容目录定义过的物品路径，让路由保持与内容无关。在 `view.ts` 解析并校验，放到 `ServerWorldView` 上，并为 `myPlayerId` 渲染一行 HUD（`金币 300 · 木材 60`）。成功的 `BUILD` / `TRAIN` 之后以及每次快照时刷新。扩展 `WorldHostCheck` 以及协议文档和它的中文孪生文件的快照一节。

**验收标准。** `LOGIN` 后 HUD 立刻显示 300 金币与 60 木材；放一座一级矿场（80 金币）后，下一次 500 毫秒轮询内金币变成 220；训练一个一级近战（20 金币）后再次下降；`--world off` 仍显示占位地图且没有资源行；`WorldHostCheck`（82 条）断言 `players` 的形状与数值；633 条保持全绿。

**涉及文件。** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java)、[WorldHostCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WorldHostCheck.java)、[view.ts](web-client/src/render/view.ts)、[main.ts](web-client/src/main.ts)、[index.html](web-client/index.html)、[协议](protocol/oc-protocol.md)、[协议中文](protocol/oc-protocol_zh_CN.md)。

**代价。** 小。

### b. 敌我难分

**现象。** 加载经典图集后，同一种单位无论属于谁看起来都一样；玩家分不清自己的工程师和 AI 的士兵，点中敌人也没有归属提示。

**原因。** 快照带 `player`，`worldViewFromSnapshot` 把它映射成 `EntityView.playerId`；唯一的地面环是固定的金色选中环（`#ffd166`）。但 `renderer.ts` 的图集精灵路径从不读 `playerId`；只有没有图集条目时，程序化回退才把它传给 `playerColor`。HUD 选中行只打印种类和坐标，不打印归属。

**建议对策。** 在画精灵之前的 ground pass 里画一圈地面归属环：`playerId === myPlayerId` 用蓝色，其他玩家用红色，`playerId` 缺失或为 0 用灰色。把金色选中环保留为另一圈更粗的环，让“选中”和“归属”不互相抢。在选中信息旁加 HUD 标签（`友方` / `敌方` / `中立`）。如果服务器以后暴露 `Player.team`，再把分类扩展到盟友；今天的快照没有 team 字段。

**验收标准。** 登录后玩家的仓库与工程师是蓝环，AI 单位是红环，玩家 1 的示例场景也是红环（不同玩家）；点中敌人显示 `敌方`；没有图集时程序化形状仍保留玩家染色并同样有环；`check-render-math` 增加一个纯归属分类检查；110 条保持全绿。

**涉及文件。** [renderer.ts](web-client/src/render/renderer.ts)、[fallback.ts](web-client/src/render/fallback.ts)、[main.ts](web-client/src/main.ts)、[view.ts](web-client/src/render/view.ts)、[check-render-math.ts](web-client/scripts/check-render-math.ts)。

**代价。** 小。

### f. 移动不平滑

**现象。** 单位一跳一跳地前进：页面每 500 毫秒拉一次快照，在 20 tick/s 下就是十个模拟 tick，每次轮询都把位置瞬间对齐到新状态。

**原因。** `WORLD_POLL_MS` 是 `500`；`pollWorldSnapshot` 替换整个世界；`WorldRenderer.render` 直接画 `entity.x` / `entity.y`，快照之间没有插值，也没有上一帧位置历史。

**建议对策。** 保留最近两份快照及它们的客户端接收时间。渲染时按 id 匹配实体，用 `alpha = (now - previousReceived) / (currentReceived - previousReceived)` 插值位置，并夹在 `[0, 1]`；当某一侧缺少该 id、tick 间隔不合理、或实体刚生成/刚死亡时直接吸附，不插值。协议保持 OC/1，JSON 形状不变。等这一步稳定后，可选地让服务器通过 WebSocket 以 10–20 Hz 推送位置/增量，再在增量之间插值；那是协议变更，必须遵守 `protocol/oc-protocol.md` 的版本规则。

**验收标准。** 移动单位的屏幕位置每个动画帧都在变，而不是每 500 毫秒变一次；平均速度与服务器一致；单位反向时不会过冲；死亡单位在下一次快照时立刻消失；`check-render-math` 覆盖插值端点、中点与 id 匹配；110 条保持全绿。

**涉及文件。** [main.ts](web-client/src/main.ts)、[view.ts](web-client/src/render/view.ts)、[renderer.ts](web-client/src/render/renderer.ts)、[check-render-math.ts](web-client/scripts/check-render-math.ts)；服务器不变。

**代价。** 中。

### g. 没有无限地图

**现象。** 世界到 64×64 就结束，地形只有一种平地（草地或沙地），没有水、森林、丘陵或岩石。把镜头移过边界看不到任何新东西。

**原因。** `WorldHost` 把 `MAP_SIZE` 设为 `64`，并用 `FlatChunkGenerator` 建世界，后者用目录里选中的一种可建地形填满每一格；`snapshotJson()` 然后把整张 64×64 地块网格发出去。引擎本身已经是无界的：`ChunkedMap` 按需生成、部分驻留，`ChunkStreamer`（系统顺序 100，默认每 tick 4 个区块）保持焦点区域加载，每个维度有自己的地图/存储/寻路器，而 `NoiseChunkGenerator` / `NoiseSettings` 才是引擎默认的地形生成器。服务器只是没有用它们。

**建议对策。** 用由 `--seed` 播种的确定性 `NoiseChunkGenerator` 替换 `FlatChunkGenerator`，配一套 `NoiseSettings`，保证每个基地锚点周围有可建陆地。把快照改成视口：客户端发中心与半径（或它已有的区块坐标），服务器只返回可见地块与实体；客户端缓存区块，并由相机焦点驱动请求；服务器侧在 world 线程上把引擎焦点移到该中心，`ChunkStreamer` 只围绕焦点加载。世界只受宿主报告的坐标窗口限制，同时保留 `--world off` 的确定性回退。这会改变快照契约，所以必须在同一次改动里更新协议文档与它的中文孪生文件、浏览器端点章节，以及快照检查。

**验收标准。** 把镜头移到 (10_000, 10_000) 能看到与原点不同的确定性地形；同一个 `--seed` 在新宿主上复现同一地形；只加载相机附近的区块，JSON 大小大致恒定；两个种子给出不同世界；开局基地仍能找到可建陆地；`WorldHostCheck`（82 条）只扩展、不削弱；渲染检查覆盖新的视口解析。

**涉及文件。** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java)、[OcRtsEngineBuilder.java](projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngineBuilder.java)、[NoiseChunkGenerator.java](projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/NoiseChunkGenerator.java)、[ChunkStreamer.java](projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/ChunkStreamer.java)、[WorldHostCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WorldHostCheck.java)、[view.ts](web-client/src/render/view.ts)、[main.ts](web-client/src/main.ts)、[协议](protocol/oc-protocol.md)、[协议中文](protocol/oc-protocol_zh_CN.md)、[05 浏览器端点](projects/oc/oc-game-server/help/05-browser-endpoint.md)。

**代价。** 大。

## 5. 已知欠账

- **没有真正的浏览器端到端运行。** `pnpm check:render` 只跑纯数学；仓库里没有任何无头/浏览器自动化去驱动真实页面、画布或 WebSocket。
- **游戏命令只在 dispatcher 层证明过，没有走实时 socket。** `PlayerCommandCheck`（57 条）与 `JoinBaseCheck`（40 条）直接驱动 `WorldHost` / `CommandDispatcher`；没有浏览器到服务器的 socket 测试覆盖 `ORDER`、`BUILD` 或 `TRAIN`。
- **盟友玩家不会互相开火，但只断言了不同玩家的情况。** `CombatSystem.isEnemy` 使用 `Player.isAlliedWith`，但没有检查创建两个 `team` 相同的玩家并证明他们不互相瞄准。
- **没有几千单位规模的性能测试。** 空间索引、寻路预算与 tick 管线只在场景规模上跑过，没有压力规模。
- **世界地形单一。** 服务器只用一种平地；引擎的噪声生成器在那里没有被使用。
- **世界状态没有存档/读档。** 只有用户记录通过 `LocalUserStore` 持久化在 `data/users.txt`；实体位置、地形编辑与生产队列在重启后丢失，引擎的 `ChunkStore` 接缝在服务器上也没有接上。
- **皮革/石头/铁/金/钻石的美术递进只有文档，没有机器断言。** 经典 README 描述了这条阶梯；没有检查证明图集像素或各级美术真的遵循它（数值与成本阶梯是有检查的）。
- **调试插件没有浏览器端对应物。** 页面能调用 `engine-info`，但只能由操作员手工粘贴 `debug.key`；没有与 `oc-debug` CLI 相当的浏览器 CLI 或调试面板。

## 6. 建议的后续功能

1. **真正的地形生成器。** 把服务器世界换成 `NoiseChunkGenerator` + `NoiseSettings`（引擎默认），并把海平面/森林线/山线/岩石线暴露成内容或服务器选项；这是 g 的前提。
2. **远程单位与第二条建筑线。** 加一个 `range > 1` 的单位和能生产它的建筑，复用 `CombatSystem` 的射程与冷却规则；现在只有近战阶梯，证明了战斗层但没证明投射。
3. **存档与读档。** 用引擎的 `ChunkStore` 接缝，加上玩家、实体与生产队列的序列化快照；今天只有账号能活过重启。
4. **WebSocket 游戏命令端到端检查。** 启动真实服务器，连真实客户端 socket，`LOGIN`，发 `ORDER` / `BUILD` / `TRAIN`，同时断言线上回答与产生的快照。这能补上上面最大的欠账。
5. **中文 `help/` 的英文翻译。** 这些章节目前刻意只用中文；等中文源稳定后再加 `_en` 孪生或英文构建，并保持协议的英文默认版与中文孪生同步。

## 7. 工作约定

- **Java 17 与 JPMS。** 工具链固定为 17；每个模块都有 `module-info.java`。不要加自动模块或 classpath 绕过。
- **Java 模块零第三方依赖。** 只依赖 JDK 与仓库内模块；JDK 能解决的问题不要加库。
- **始终 `--offline`。** `gradle.bat ... --offline` 必须在没有网络的机器上可用；需要下载的改动就是错误的改动。
- **模拟内部保持确定性。** 模拟里不用 `java.util.Random`，不读墙钟（`System.currentTimeMillis`、`System.nanoTime`），不起线程。用 `DeterministicRandom`、固定 tick，并显式按 id/坐标打破平局。服务器的节拍线程是唯一允许用时钟驱动模拟的地方；传输与存储代码可以为时间戳读时钟，但模拟本身不行。
- **服务器里只用 daemon 线程。** 插件线程必须是 daemon，并在 `onDisable` 里停掉；非 daemon 线程会让 JVM 在关闭后继续活着。
- **一次提交只做一个已验证阶段。** 提交前跑六个 Java 检查和两个客户端检查；不要把重构和功能混在同一次提交里。
- **总数变了就更新每一份引用它的文档。** 在仓库里 grep 旧数字（例如 `633`）；README、`help/` 与协议都可能提到它。
- **文档是双语的。** 英文文件里改动的每一句话，都必须同步改它的 `_zh_CN.md` 孪生。`help/` 章节按设计是中文文档集；`protocol/oc-protocol.md` 默认英文，`protocol/oc-protocol_zh_CN.md` 是它的孪生，所以两者必须一起动。
- **运行目录不是源码。** `plugins/` 是操作员拖放区，`data/` 是服务器状态；`plugins/*.jar` 与 `data/` 都被 gitignore。绝不提交插件 jar、密钥或账号文件。

## 8. 交接清单

- [ ] 从 §2 跑六个 Java 自检；六个总数全部对得上。
- [ ] 跑 `tsc --noEmit` 与 `node scripts/check-render-math.ts`；110/110。
- [ ] 手工打开一次游戏：经典 jar 放进 `plugins/`，服务器起来，`pnpm dev`，REGISTER/LOGIN，看到仓库、工程师、300 金币和 60 木材。
- [ ] 动 `PluginLoader` 或 `PluginContext` 之前先读 [help/03-plugins.md](projects/oc/oc-game-server/help/03-plugins.md)。
- [ ] 开始前确认 `git status` 干净；不要提交 jar、密钥或 `data/`。
- [ ] 从 §4 挑一个问题，先写会失败的检查，并保持所有现有检查全绿。
