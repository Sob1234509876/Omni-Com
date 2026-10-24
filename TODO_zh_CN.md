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
| googology | `:projects:oc:oc-googology:selfcheck` | 267 | [README](projects/oc/oc-googology/README.md) 的自检行；`GoogologyCheck` 末尾打印 `googology self check: all 267 checks passed.` |
| engine | `:projects:oc:oc-rts-engine:scenario` | 336 | [README](projects/oc/oc-rts-engine/README.md)；[help/11](projects/oc/oc-rts-engine/help/11-limits-and-faq.md) 与 [help/13](projects/oc/oc-rts-engine/help/13-dimensions.md) 引用同一总数 |
| game server | `:projects:oc:oc-game-server:selfcheck` | 813 | [README](projects/oc/oc-game-server/README.md)；十一个检查类之和：`AuthCheck` 65、`NetworkCheck` 57、`PluginCheck` 76、`RequestCheck` 43、`WebSocketCheck` 88、`HttpCheck` 47、`PluginAssetsCheck` 80、`WorldHostCheck` 187、`WorldTickHookCheck` 23、`PlayerCommandCheck` 87、`JoinBaseCheck` 60 |
| debug | `:projects:oc:oc-game-server-debug:selfcheck` | 85 | [README](projects/oc/oc-game-server-debug/README.md)；四节：信封 15、密钥与报告 37、请求注册表 14、真实 socket 19 |
| hello | `:projects:oc:oc-game-server-hello:selfcheck` | 126 | [README](projects/oc/oc-game-server-hello/README.md)；五节，含贴图断言 |
| classic | `:projects:oc:oc-game-server-classic:selfcheck` | 349 | [README](projects/oc/oc-game-server-classic/README.md)；十三节，从内容目录（82）到插件加载器（12） |
| client render math | `node scripts/check-render-math.ts` | 165 | 由 [check-render-math.ts](web-client/scripts/check-render-math.ts) 里的 `check(...)` 调用逐条数出；脚本最后打印通过总数 |

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

**验收标准。** `LOGIN alice` → `OK player 3`；断开；`LOGIN alice` → 仍然是 `OK player 3`，同一座仓库、同一名工程师、金币/木材与断开前一致，也没有第二座基地；`LOGIN bob` → `OK player 4`；第七个不同账号仍然得到 `ERR world full`。`JoinBaseCheck`（60 条）保持全绿，并新增重连复用断言。

**涉及文件。** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java)、[CommandDispatcher.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/net/CommandDispatcher.java)、[JoinBaseCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/JoinBaseCheck.java)。

**代价。** 小。

### e. 空闲约一分钟就断线

**现象。** 页面开着、不点任何东西，大约 60 秒后 WebSocket 以 `1001` 关闭；日志显示连接已断开。

**原因。** 浏览器端点的 socket 读超时同时充当 WebSocket 空闲超时，`WebSocketConfig.DEFAULT_IDLE_TIMEOUT_MILLIS` 是 `60_000`。`HttpWebServer` 用 `setSoTimeout` 应用它，`WebSocketConnection` 在读取超时时以 `1001` 关闭。客户端只在用户点按钮时发 `PING`；`/world/snapshot` 是普通 HTTP，不会刷新 WebSocket。

**建议对策。** 在 socket 打开期间每 20 秒发一次客户端 `PING` 并要求 `PONG`，走与其他命令相同的按序 `send` 队列，关闭时清掉定时器。给 `OcGameServer` 加 `--ws-idle N` 选项，把它传进 `WebSocketConfig`，默认五分钟（`300_000` 毫秒），并校验 `N > 0`。可选地再让服务器发 ping 帧作为第二道保险；客户端已经能容忍意外的单行回答。

**验收标准。** 默认配置下页面空闲六分钟仍保持连接，HUD 继续更新；日志里大约每 20 秒出现一次 `PING`/`PONG`；`--ws-idle 60` 能复现旧的 60 秒关闭，证明确实接上了这个开关；`WebSocketCheck`（88 条）保持全绿，并新增心跳与开关检查。

**涉及文件。** [WebSocketConfig.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/ws/WebSocketConfig.java)、[OcGameServer.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/OcGameServer.java)、[HttpWebServer.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/ws/HttpWebServer.java)、[main.ts](web-client/src/main.ts)、[socket.ts](web-client/src/socket.ts)、[WebSocketCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WebSocketCheck.java)。

**代价。** 小。

### d. 没有翻译，界面显示 `unit.melee.t1`

**现象。** HUD 选中行和 TRAIN/BUILD 按钮显示原始内容路径，比如 `unit.melee.t1`、`building.armory.t1`；目录里的中文名（普通、兵营·普通）从未到达页面。

**原因。** 快照里的 `kind` 是引擎内容路径，客户端原样打印：HUD 用 `selected.kind`，面板按钮用 `unitPath` / `buildingPath`。引擎目录确实带显示名（`.name("普通")`），但快照不包含它们，`assets/manifest.json` 也没有名字字段；`atlas.ts` 只解析帧表与它们的几何参数（`fps`、`anchor`、`scale`），没有显示名字段。

**建议对策。** 扩展贴图清单：实体用 `entities.<kind>.name`，地形用 `tiles.<path>.name`。地形条目现在是 `[x, y, w, h]`，所以同时接受旧数组与新对象 `{"rect": [x, y, w, h], "name": "..."}`；实体条目本来就是对象，在 `frames` 旁加可选的 `name`。服务器不解释这些字段，它本来就把清单原样转发。`atlas.ts` 把名字带进合并后的图集（后加载的插件覆盖前面的），`main.ts` 用名字显示、用内容路径兜底。给 `oc-classic` 与 `oc-hello` 的清单补名字，并更新贴图契约文档。

**验收标准。** 加载经典 jar 后，HUD 显示 `e12 普通`，TRAIN 按钮显示 `普通` / `强化` / `精英` / `将领` / `战王` / `工程师`，BUILD 按钮显示 `兵营·普通` / `矿场·普通`；清单里没有名字时同一行回退到 `unit.melee.t1`（绝不能出现 `undefined`）；`check-render-math` 增加名字合并与回退检查；所有检查保持全绿（今天是 165 条，本阶段落地时是 158 条）。

**涉及文件。** [atlas.ts](web-client/src/render/atlas.ts)、[main.ts](web-client/src/main.ts)、[ClassicContent.java](projects/oc/oc-game-server-classic/src/main/java/io/github/sob1234509876/oc/server/classic/ClassicContent.java)、[manifest.json](projects/oc/oc-game-server-classic/src/main/resources/assets/manifest.json)、[贴图 README](web-client/public/textures/README.md)、[贴图 README 中文](web-client/public/textures/README_zh_CN.md)、[check-render-math.ts](web-client/scripts/check-render-math.ts)。

**代价。** 小。

### a. 资源看不见

**现象。** HUD 不显示金币或木材；玩几分钟后玩家不知道为什么 `BUILD` 或 `TRAIN` 回答 `ERR cannot afford ...`。开局库存确实存在，但完全不可见。

**原因。** `WorldHost.snapshotJson()` 只发 `tick`、`width`、`height`、`tilePixels`、`tiles` 与 `entities`；玩家库存没有序列化。`worldViewFromSnapshot` 没有 `players` 字段可读，`main.ts` 也没有资源 HUD 行。

**建议对策。** 给 `/world/snapshot` 加 `players` 数组，例如 `[{"id":3,"name":"alice","gold":300,"wood":60}]`，从每个玩家的 `logistics().available()` bundle 构建，并用当前内容目录注册的 `gold` / `wood` 物品路径读取；每个玩家一行、按 id 排序，目录没定义的路径报 `0`。在 `view.ts` 解析并校验，放到 `ServerWorldView` 上，并为 `myPlayerId` 渲染一行 HUD（`资源：alice · 金 300 · 木 60`），世界里还没有这一行时显示破折号。成功的 `BUILD` / `TRAIN` 之后以及每次快照时刷新。扩展 `WorldHostCheck` 以及协议文档和它的中文孪生文件的快照一节。

**验收标准。** `LOGIN` 后 HUD 立刻显示 300 金币与 60 木材；放一座一级矿场（80 金币）后，下一次 500 毫秒轮询内金币变成 220；训练一个一级近战（20 金币）后再次下降；`--world off` 仍显示占位地图且没有资源行；`WorldHostCheck`（187 条）断言 `players` 的形状与数值；813 条保持全绿。

**涉及文件。** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java)、[WorldHostCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WorldHostCheck.java)、[view.ts](web-client/src/render/view.ts)、[main.ts](web-client/src/main.ts)、[index.html](web-client/index.html)、[协议](protocol/oc-protocol.md)、[协议中文](protocol/oc-protocol_zh_CN.md)。

**代价。** 小。

### b. 敌我难分

**现象。** 加载经典图集后，同一种单位无论属于谁看起来都一样；玩家分不清自己的工程师和 AI 的士兵，点中敌人也没有归属提示。

**原因。** 快照带 `player`，`worldViewFromSnapshot` 把它映射成 `EntityView.playerId`；唯一的地面环是固定的金色选中环（`#ffd166`）。但 `renderer.ts` 的图集精灵路径从不读 `playerId`；只有没有图集条目时，程序化回退才把它传给 `playerColor`。HUD 选中行只打印种类和坐标，不打印归属。

**建议对策。** 在画精灵之前的 ground pass 里画一圈地面归属环：`playerId === myPlayerId` 用蓝色 `#3b82f6`，其他玩家用红色 `#ef4444`，`playerId` 缺失或为 0、或 `myPlayerId` 未知（占位地图）用灰色 `#9ca3af`。把金色 `#ffd166` 选中环保留为另一圈更粗的环、画在归属环之后，让“选中”和“归属”不互相抢。在选中信息旁加 HUD 标签（`我` / `敌` / `中立`）。如果服务器以后暴露 `Player.team`，再把分类扩展到盟友；今天的快照没有 team 字段。

**验收标准。** 登录后玩家的仓库与工程师是蓝环，AI 单位是红环，玩家 1 的示例场景也是红环（不同玩家）；点中敌人显示 `敌`；没有图集时程序化形状仍保留玩家染色并同样有环；演示世界（没有 `myPlayerId`）只有灰环；`check-render-math` 增加纯归属颜色检查并证明选中环颜色不变；所有检查保持全绿（今天是 165 条，本阶段落地时是 158 条）。

**涉及文件。** [renderer.ts](web-client/src/render/renderer.ts)、[fallback.ts](web-client/src/render/fallback.ts)、[main.ts](web-client/src/main.ts)、[view.ts](web-client/src/render/view.ts)、[check-render-math.ts](web-client/scripts/check-render-math.ts)。

**代价。** 小。

### f. 移动不平滑

**现象。** 单位一跳一跳地前进：页面每 500 毫秒拉一次快照，在 20 tick/s 下就是十个模拟 tick，每次轮询都把位置瞬间对齐到新状态。

**原因。** `WORLD_POLL_MS` 是 `500`；`pollWorldSnapshot` 替换整个世界；`WorldRenderer.render` 直接画 `entity.x` / `entity.y`，快照之间没有插值，也没有上一帧位置历史。

**建议对策。** 保留最近两份快照及它们的客户端接收时间。渲染时按 id 匹配实体，用 `alpha = (now - previousReceived) / (currentReceived - previousReceived)` 插值位置，并夹在 `[0, 1]`；当某一侧缺少该 id、tick 间隔不合理、或实体刚生成/刚死亡时直接吸附，不插值。协议保持 OC/1，JSON 形状不变。等这一步稳定后，可选地让服务器通过 WebSocket 以 10–20 Hz 推送位置/增量，再在增量之间插值；那是协议变更，必须遵守 `protocol/oc-protocol.md` 的版本规则。

**验收标准。** 移动单位的屏幕位置每个动画帧都在变，而不是每 500 毫秒变一次；平均速度与服务器一致；单位反向时不会过冲；死亡单位在下一次快照时立刻消失；`check-render-math` 覆盖插值端点、中点与 id 匹配；所有检查保持全绿（今天是 165 条，本阶段落地时是 158 条）。

**涉及文件。** [main.ts](web-client/src/main.ts)、[view.ts](web-client/src/render/view.ts)、[renderer.ts](web-client/src/render/renderer.ts)、[check-render-math.ts](web-client/scripts/check-render-math.ts)；服务器不变。

**代价。** 中。

### g. 没有无限地图 —— 已完成（stage g3）

**现象。** 托管世界到 256×256 就结束，而浏览器过去只显示地图中心 64×64 的窗口；平移镜头也不会向服务器要另一个窗口。stage g2 之前世界只到 64×64，而且每一格都是同一种平地。

**原因。** 服务端在 stage g2 已经修好：`WorldHost` 把 `MAP_SIZE` 设为 `256`，用确定性的 `NoiseTerrainGenerator` 与所服务目录的生物群系表建世界，找出可建造的出生锚点，并提供带 `cx` / `cy` / `r` 的窗口快照。引擎本来就能无界生成（`ChunkedMap`、`ChunkStreamer`），窗口请求也会移动引擎焦点。客户端是剩下的缺口：`web-client/src/render/view.ts` 与 `main.ts` 当时仍然请求无参数快照，既不翻译 `originX` / `originY`，也不发送相机中心，所以页面无法平移出中心窗口。

**建议对策（stage g3，客户端）。** `WorldView` 现在带有 `originX` / `originY` 与完整地图大小（`mapWidth` / `mapHeight`）；地形查找先减去原点，窗口外返回 `void`，实体保持绝对地图坐标，渲染器与拾取都只在实体瓦片位于窗口内时才绘制或选中。`main.ts` 由相机焦点发 `GET /world/snapshot?cx=<相机 tileX>&cy=<相机 tileY>&r=32`，焦点进入窗口边缘 8 格以内就请求以相机为中心的新窗口；新窗口到达前保留旧窗口，并用单调递增的请求 id 丢弃乱序回答；无参数请求保留为第一次的确定性中心请求。相机夹紧到 256×256 地图而不是 64×64 窗口，所以平移不会被已加载窗口挡住；HUD 显示窗口原点与地图大小。`web-client/README.md` 与 `README_zh_CN.md` 记录了窗口流送。

**验收标准（已满足；服务端地图大小后来被 stage i2 取代）。** 服务端在 256×256 的报告地图上提供确定性噪声地形、可建造出生锚点和 `cx` / `cy` / `r` 窗口快照；同一个 `--seed` 在新宿主上复现同一地形与锚点；两个种子给出不同世界；开局基地仍能找到可建陆地；`WorldHostCheck`（187 条）只扩展、不削弱。客户端现在请求以相机为中心的窗口，用 `originX` / `originY` 翻译绝对坐标，在新窗口到达前保留旧窗口，忽略乱序回答，并把相机夹紧到地图；`check-render-math` 覆盖窗口解析、8 格边缘触发、地图边缘保护、请求排序与地图夹紧。本阶段打印 **158** 项通过（142 + 16）；stage i3 之后当前总数为 **165**。

**涉及文件。** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java)、[OcRtsEngineBuilder.java](projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngineBuilder.java)、[NoiseChunkGenerator.java](projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/NoiseChunkGenerator.java)、[ChunkStreamer.java](projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/ChunkStreamer.java)、[WorldHostCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WorldHostCheck.java)、[view.ts](web-client/src/render/view.ts)、[stream.ts](web-client/src/render/stream.ts)、[camera.ts](web-client/src/render/camera.ts)、[renderer.ts](web-client/src/render/renderer.ts)、[input.ts](web-client/src/render/input.ts)、[main.ts](web-client/src/main.ts)、[index.html](web-client/index.html)、[客户端 README](web-client/README.md)、[协议](protocol/oc-protocol.md)、[协议中文](protocol/oc-protocol_zh_CN.md)、[05 浏览器端点](projects/oc/oc-game-server/help/05-browser-endpoint.md)。

**代价。** 大。

### h. 服务端仍然假设有限地图 —— 已完成（stage i2）

**现象。** 引擎早就能生成任意有符号 tile，但 `WorldHost` 仍报告 `MAP_SIZE = 256` 的地图：出生锚点只扫
256 格，快照把中心夹进 `0..255`，场景与基地在锚点搜索失败时回退到全图扫描，经典 AI 的选址也一样，
`oc-debug` 还把 `world.map()` 强转成有限的 `ChunkedMap`。

**建议对策（stage i2，服务端，已实现）。** `WorldHost` 改用 `OcRtsEngineBuilder.infiniteTerrain(seed)`
加一个正的 `maxLoadedChunks` 预算（`WorldHost.DEFAULT_CHUNK_BUDGET = 4096`，暴露成 `--chunks N`）；
地图大小常量被删除，`width()` / `height()` / `MAP_SIZE` 的所有引用一并消失。出生锚点从 `(0,0)` 向外
最多扫 64 格，所有原来的全图回退（场景、真人基地、工程师站位）都改成同一锚点周围的定长环形搜索。
`snapshotJson(cx, cy, r)` 接受任意有符号中心，只把 `r` 夹在 `16..64`；原点可以为负或很大，JSON 不再带
地图宽高，无参数请求以出生锚点为中心。`ClassicAi` 用远离主场景的固定 `(192,192)` 开局锚点，并只在自己
最老建筑周围 64 格内选址；`DebugPlugin` 通过 `StreamingTerrainMap` 读取一次性世界，报告
`engine-map-bounded: false` 与 `engine-chunks-budget: 4096`，不再强转 `ChunkedMap`。`WorldHostCheck` 从
163 条增加到 187 条（启动耗时与区块预算、`(0,0)` 锚点、负原点与远景窗口、远景地形、真实 HTTP 远景窗口、
场景与加入基地仍落在可建造地），经典确定性一节断言 600 tick 内 AI 所有实体都在其基地 64 格内，调试
自校验断言无界报告。那一阶段的总数：game server 813（原 789）、debug 85（原 82）、classic 293（原 291）；伐木场与建筑目标阶段之后 classic 是 349，engine 是 336。

**已知限制。** 本阶段按要求不修改 `web-client/`；它仍按 `DEFAULT_MAP_SIZE_TILES = 256` 校验并拒绝负实体
坐标，所以渲染新的负原点窗口需要单独的客户端阶段。该阶段就是下面的 i3：客户端现在把世界当作无界处理。

**涉及文件。** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java)、[OcGameServer.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/OcGameServer.java)、[HttpWebServer.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/ws/HttpWebServer.java)、[ClassicAi.java](projects/oc/oc-game-server-classic/src/main/java/io/github/sob1234509876/oc/server/classic/ClassicAi.java)、[DebugPlugin.java](projects/oc/oc-game-server-debug/src/main/java/io/github/sob1234509876/oc/server/debug/DebugPlugin.java)、[WorldHostCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WorldHostCheck.java)、[ClassicCheck.java](projects/oc/oc-game-server-classic/src/selfcheck/java/io/github/sob1234509876/oc/server/classic/check/ClassicCheck.java)、[DebugPluginCheck.java](projects/oc/oc-game-server-debug/src/selfcheck/java/io/github/sob1234509876/oc/server/debug/check/DebugPluginCheck.java)、[05 浏览器端点](projects/oc/oc-game-server/help/05-browser-endpoint.md)、[协议](protocol/oc-protocol.md)、[协议中文](protocol/oc-protocol_zh_CN.md)。

**代价。** 大。

### i. 浏览器客户端仍然假设有限地图 —— 已完成（stage i3）

**现象。** stage i2 之后服务器接受任意有符号窗口中心，但页面会拒绝由此产生的文档：
`worldViewFromSnapshot` 拒绝负实体坐标，并在快照没有 `mapWidth` / `mapHeight` 时虚构一个 256 格的
`DEFAULT_MAP_SIZE_TILES`。相机被夹紧到那张 256×256 的地图，HUD 打印地图大小，原点是负数的窗口永远渲染不出来，
所以镜头无法平移出出生点很远。

**原因。** stage i2 为了让服务端改动便于审查，刻意没碰 `web-client/`；客户端仍按更早的有限地图快照契约编写。

**建议对策（stage i3，客户端，已实现）。** `view.ts` 完全删掉 `mapWidth` / `mapHeight` 与
`DEFAULT_MAP_SIZE_TILES`。窗口原点必须是有符号整数（接受负数与很大的值），实体位置必须是有限的有符号数
（小数或远在窗口之外都可以），不再按地图做任何校验。绝对坐标到窗口索引的平移在向下取整后减去有符号原点，
所以负原点、负瓦片与 `-1` 边界都能正确索引。`camera.ts` 只在启动时用调用方给的家位置构造一次，没有世界尺寸，
只把中心夹紧到有文档说明的 `CAMERA_SAFETY_BOUND_TILES = 500000` 数值保护（引擎用单精度 `Vec2` 存位置，
到这个量级附近会失去亚像素分辨率）；焦点瓦片用 `Math.floor`，所以原点以西/以北的坐标也正确。
`stream.ts` 对任意有符号中心都算 `snapshotOrigin(centre, radius) = centre - radius`，保留有符号边缘触发、
重复原点保护与单调递增的请求跟踪器。`main.ts` 按任意有符号相机焦点请求窗口（第一次轮询也一样）；请求失败或
回答因乱序被拒绝时保留旧窗口；窗口到达时绝不重建相机或输入控制器。HUD 用相机坐标替换地图大小，并保留窗口原点
（`坐标：-1234,-567 · 窗口：-1250,-582`），占位世界仍从自己的 `0,0` 窗口开始。两份 README 孪生都记录了
无界世界、安全边界与新 HUD 行。

**验收标准（已满足）。** `tsc --noEmit` 干净。`check-render-math` 证明负实体坐标被接受并可往返；负原点把
绝对瓦片映射到正确的窗口索引（含 `-1` 边界）；负原点窗口能绘制自己的地形，恰好在窗口外返回 `void`；相机能到
`-100000` 而不被夹紧，并在 `±500000` 处被停住；负中心生成
`/world/snapshot?cx=-1250&cy=-582&r=32`；乱序回答不会替换此前已加载的窗口。脚本现在打印 **165** 项通过
（原为 158）。

**涉及文件。** [view.ts](web-client/src/render/view.ts)、[camera.ts](web-client/src/render/camera.ts)、
[stream.ts](web-client/src/render/stream.ts)、[main.ts](web-client/src/main.ts)、[input.ts](web-client/src/render/input.ts)、
[index.html](web-client/index.html)、[check-render-math.ts](web-client/scripts/check-render-math.ts)、
[客户端 README](web-client/README.md)、[客户端 README 中文](web-client/README_zh_CN.md)。

**代价。** 中。

## 5. 已知欠账

- **没有真正的浏览器端到端运行。** `pnpm check:render` 只跑纯数学；仓库里没有任何无头/浏览器自动化去驱动真实页面、画布或 WebSocket。
- **游戏命令只在 dispatcher 层证明过，没有走实时 socket。** `PlayerCommandCheck`（87 条）与 `JoinBaseCheck`（60 条）直接驱动 `WorldHost` / `CommandDispatcher`；没有浏览器到服务器的 socket 测试覆盖 `ORDER`、`BUILD` 或 `TRAIN`。
- **盟友玩家不会互相开火，但只断言了不同玩家的情况。** `CombatSystem.isEnemy` 使用 `Player.isAlliedWith`，但没有检查创建两个 `team` 相同的玩家并证明他们不互相瞄准。
- **没有几千单位规模的性能测试。** 空间索引、寻路预算与 tick 管线只在场景规模上跑过，没有压力规模。
- **世界状态没有存档/读档。** 只有用户记录通过 `LocalUserStore` 持久化在 `data/users.txt`；实体位置、地形编辑与生产队列在重启后丢失，引擎的 `ChunkStore` 接缝在服务器上也没有接上。
- **皮革/石头/铁/金/钻石的美术递进只有文档，没有机器断言。** 经典 README 描述了这条阶梯；没有检查证明图集像素或各级美术真的遵循它（数值与成本阶梯是有检查的）。
- **调试插件没有浏览器端对应物。** 页面能调用 `engine-info`，但只能由操作员手工粘贴 `debug.key`；没有与 `oc-debug` CLI 相当的浏览器 CLI 或调试面板。

## 6. 建议的后续功能

1. **把地形生成器的阈值暴露出来。** 服务端在 stage i2 已为无界世界使用 `NoiseTerrainGenerator` 的生物群系表；把海平面/森林线/山线/岩石线暴露成内容或服务器选项，让世界无需改代码即可调参。
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
- **总数变了就更新每一份引用它的文档。** 在仓库里 grep 旧数字（例如下次改动前的 `813`）；README、`help/` 与协议都可能提到它。
- **文档是双语的。** 英文文件里改动的每一句话，都必须同步改它的 `_zh_CN.md` 孪生。`help/` 章节按设计是中文文档集；`protocol/oc-protocol.md` 默认英文，`protocol/oc-protocol_zh_CN.md` 是它的孪生，所以两者必须一起动。
- **运行目录不是源码。** `plugins/` 是操作员拖放区，`data/` 是服务器状态；`plugins/*.jar` 与 `data/` 都被 gitignore。绝不提交插件 jar、密钥或账号文件。

## 8. 交接清单

- [ ] 从 §2 跑六个 Java 自检；六个总数全部对得上。
- [ ] 跑 `tsc --noEmit` 与 `node scripts/check-render-math.ts`；165/165。
- [ ] 手工打开一次游戏：经典 jar 放进 `plugins/`，服务器起来，`pnpm dev`，REGISTER/LOGIN，看到仓库、工程师、300 金币和 60 木材。
- [ ] 动 `PluginLoader` 或 `PluginContext` 之前先读 [help/03-plugins.md](projects/oc/oc-game-server/help/03-plugins.md)。
- [ ] 开始前确认 `git status` 干净；不要提交 jar、密钥或 `data/`。
- [ ] 从 §4 挑一个问题，先写会失败的检查，并保持所有现有检查全绿。
