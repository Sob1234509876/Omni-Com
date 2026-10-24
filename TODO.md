English | [简体中文](TODO_zh_CN.md)

# Omni Com handoff

Written against `HEAD 3abc25d` (2026-10-18). This is the cold-start brief: what the repository is, how to prove it is healthy, how to see the game, the seven reported problems in the order they should be fixed, the debts that are real, the next features worth building, and the rules that keep the tree buildable.

## 1. What this is

Omni Com is a Gradle monorepo with six Java modules — `projects:oc:oc-googology` (the arbitrary-precision `Natural` number library), `projects:oc:oc-rts-engine` (the headless RTS simulation), `projects:oc:oc-game-server` (accounts, the encrypted TCP channel, the plugin loader and the browser endpoint), `projects:oc:oc-game-server-debug` (`oc-debug`, a read-only plugin and CLI), `projects:oc:oc-game-server-hello` (`oc-hello`, the minimal plugin and the default atlas), and `projects:oc:oc-game-server-classic` (`oc-classic`, the five-tier game, the economy hook and the enemy AI) — all plain Java 17 on JPMS with **zero third-party dependencies** and offline Gradle builds; [`web-client/`](web-client/README.md) is a separate pnpm project (TypeScript + Vite, no framework) that talks to the server over WebSocket and HTTP. The two toolchains run in parallel: Gradle never invokes npm or pnpm, and pnpm never invokes Gradle.

## 2. Current state

Run all six Java self checks from the repository root. The repo ships `gradlew.bat`; use `gradle.bat` only when a Gradle installation is on `PATH`:

```powershell
gradle.bat --offline --no-daemon --console=plain :projects:oc:oc-googology:selfcheck :projects:oc:oc-rts-engine:scenario :projects:oc:oc-game-server:selfcheck :projects:oc:oc-game-server-debug:selfcheck :projects:oc:oc-game-server-hello:selfcheck :projects:oc:oc-game-server-classic:selfcheck
```

The client checks are separate. `node` must be 24+ because the script uses Node's TypeScript type stripping:

```powershell
cd web-client; .\node_modules\.bin\tsc.cmd --noEmit; node scripts/check-render-math.ts
```

| Check | Gradle task | Total | Where the total comes from |
| --- | --- | --- | --- |
| googology | `:projects:oc:oc-googology:selfcheck` | 378 | [README](projects/oc/oc-googology/README.md) self-check row; `GoogologyCheck` ends with `googology self check: all 378 checks passed.` |
| engine | `:projects:oc:oc-rts-engine:scenario` | 515 | [README](projects/oc/oc-rts-engine/README.md); [help/11](projects/oc/oc-rts-engine/help/11-limits-and-faq.md) and [help/13](projects/oc/oc-rts-engine/help/13-dimensions.md) quote the same total |
| game server | `:projects:oc:oc-game-server:selfcheck` | 1014 | [README](projects/oc/oc-game-server/README.md); sum of eleven check classes: `AuthCheck` 65, `NetworkCheck` 57, `PluginCheck` 76, `RequestCheck` 43, `WebSocketCheck` 88, `HttpCheck` 47, `PluginAssetsCheck` 80, `WorldHostCheck` 306, `WorldTickHookCheck` 23, `PlayerCommandCheck` 93, `JoinBaseCheck` 60 |
| debug | `:projects:oc:oc-game-server-debug:selfcheck` | 85 | [README](projects/oc/oc-game-server-debug/README.md); four sections: envelope 15, key/report 37, request registry 14, real socket 19 |
| hello | `:projects:oc:oc-game-server-hello:selfcheck` | 126 | [README](projects/oc/oc-game-server-hello/README.md); five sections, including the texture assertions |
| classic | `:projects:oc:oc-game-server-classic:selfcheck` | 368 | [README](projects/oc/oc-game-server-classic/README.md); fourteen sections from the catalogue (84) to the plugin loader (12) |
| client render math | `node scripts/check-render-math.ts` | 203 | counted from the `check(...)` calls in [check-render-math.ts](web-client/scripts/check-render-math.ts); the script prints the passed total |

**No commit may leave any of these red, and no existing check may be removed or weakened to make a new feature pass.** If a total changes, grep the old number and update every document that quotes it.

## 3. How to see the game running

1. Build the classic plugin jar and the two jars it needs at run time:

```powershell
gradle.bat --offline --no-daemon --console=plain :projects:oc:oc-game-server-classic:jar :projects:oc:oc-rts-engine:jar :projects:oc:oc-googology:jar
```

2. Copy exactly these three jars into the server's `plugins/` drop zone:

```powershell
Copy-Item projects/oc/oc-game-server-classic/build/libs/oc-game-server-classic-2.0a.jar plugins/
Copy-Item projects/oc/oc-rts-engine/build/libs/oc-rts-engine-2.0a.jar plugins/
Copy-Item projects/oc/oc-googology/build/libs/oc-googology-2.0a.jar plugins/
```

3. Start the server from the repository root:

```powershell
gradle.bat --offline --no-daemon --console=plain :projects:oc:oc-game-server:server
```

It listens on TCP `25566` and HTTP/WS `25567`. The `server` task runs with the repository root as its working directory, so the default static root resolves.

4. In a second terminal, start the client:

```powershell
cd web-client; pnpm install; pnpm dev
```

`pnpm install` is needed once and needs the network; after that `pnpm dev` works offline. Open the printed URL (normally `http://localhost:5173/`), leave **both** the host and port fields empty (the Vite proxy forwards `/ws`, `/world` and `/plugin-assets` to `127.0.0.1:25567`), then click `REGISTER`, and once it succeeds click `LOGIN`.

5. A fresh login owns one warehouse, one engineer, **300 gold** and **60 wood** — the `oc-classic` starting resources. Player 1 is the server's sample scene, player 2 is the red `oc-classic` AI, and your first human id is `3`. The game view switches from the deterministic demo map to the live snapshot once `/world/snapshot` answers.

## 4. The seven reported problems

Fix them in this order. The first three are broken behaviour and cheap; the next three are usability; the last one is a large architectural change.

Every subsection uses the same six fields: **Symptom** (现象), **Cause** (原因), **Fix** (建议对策), **Acceptance** (验收标准), **Files** (涉及文件), **Cost** (代价).

| Order | Problem | Cost |
| --- | --- | --- |
| 1 | c — every login creates a new faction | small |
| 2 | e — idle disconnect after about a minute | small |
| 3 | d — no translation, the UI shows `unit.melee.t1` | small |
| 4 | a — resources are invisible | small |
| 5 | b — friend and foe are hard to tell apart | small |
| 6 | f — movement is not smooth | medium |
| 7 | g — no infinite map | large |

### c. Every login creates a new faction

**Symptom.** Log out and log in again with the same account, or reconnect, and `WHOAMI` answers a different player id; the new player gets another warehouse, another engineer and another 300 gold + 60 wood, while the old base stays in the world.

**Cause.** `WorldHost.joinPlayer(String)` scans ids 3 through `MAX_PLAYERS` (8), skips every id already in the world, and never reuses an id while the world lives. `CommandDispatcher` resets the per-connection cached id on every successful `LOGIN`, so the next gameplay command calls `joinPlayer` again. The account name is stored as the display name only; no account-to-id map exists, and the old player is deliberately not removed because its entities still need an owner.

**Fix.** Add an account-name-to-player-id map to `WorldHost`, owned by the world thread. `joinPlayer` returns the existing player for a known name and only assigns the smallest unused id for a new name. Keep the base, entities and resources untouched on rejoin; clear the map with the world. A name that maps to a player removed by a future reset must be rejected explicitly rather than silently reassigned.

**Acceptance.** `LOGIN alice` → `OK player 3`; disconnect; `LOGIN alice` → `OK player 3` again, same warehouse, same engineer, the same gold/wood as before the disconnect, and no second base; `LOGIN bob` → `OK player 4`; a seventh distinct account still gets `ERR world full`. `JoinBaseCheck` (60) stays green plus the rejoin assertions.

**Files.** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java), [CommandDispatcher.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/net/CommandDispatcher.java), [JoinBaseCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/JoinBaseCheck.java).

**Cost.** small.

### e. Idle disconnects after about a minute

**Symptom.** Leave the page open without clicking anything and the WebSocket closes after roughly 60 seconds with code `1001`; the log says the connection is gone.

**Cause.** The browser endpoint's socket read timeout doubles as the WebSocket idle timeout, and `WebSocketConfig.DEFAULT_IDLE_TIMEOUT_MILLIS` is `60_000`. `HttpWebServer` applies it with `setSoTimeout`, and `WebSocketConnection` closes with `1001` when a read times out. The client sends `PING` only when the user presses the button; `/world/snapshot` is plain HTTP and does not refresh the WebSocket.

**Fix.** Send a client `PING` every 20 seconds while the socket is open and require `PONG`, through the same in-order `send` queue as every other command, and clear the timer on close. Add a `--ws-idle N` option to `OcGameServer`, thread it into `WebSocketConfig`, default it to five minutes (`300_000` ms), and validate `N > 0`. Optionally send server ping frames as a second line of defence; the client already tolerates an unexpected line.

**Acceptance.** With the default, a page idle for six minutes stays connected and the HUD keeps updating; the log shows `PING`/`PONG` about every 20 seconds; `--ws-idle 60` reproduces the old close, proving the flag is wired; `WebSocketCheck` (88) stays green plus heartbeat and flag checks.

**Files.** [WebSocketConfig.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/ws/WebSocketConfig.java), [OcGameServer.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/OcGameServer.java), [HttpWebServer.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/ws/HttpWebServer.java), [main.ts](web-client/src/main.ts), [socket.ts](web-client/src/socket.ts), [WebSocketCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WebSocketCheck.java).

**Cost.** small.

### d. No translation; the UI shows `unit.melee.t1`

**Symptom.** The HUD selection line and the TRAIN/BUILD buttons show raw content paths such as `unit.melee.t1` and `building.armory.t1`; the catalogue's Chinese names (普通, 兵营·普通) never reach the page.

**Cause.** The snapshot's `kind` is the engine content path, and the client prints it verbatim: the HUD uses `selected.kind`, and the panel's buttons use `unitPath` / `buildingPath`. The engine catalogue does carry display names (`.name("普通")`), but the snapshot does not include them and `assets/manifest.json` has no name field; `atlas.ts` parses the frame tables and their geometry (`fps`, `anchor`, `scale`) but has no display-name field.

**Fix.** Extend the texture manifest with display names: `entities.<kind>.name` for entities and `tiles.<path>.name` for terrain. Because a tile entry is currently `[x, y, w, h]`, accept both the old array and a new object `{"rect": [x, y, w, h], "name": "..."}`; entity entries are already objects, so add an optional `name` beside `frames`. The server does not interpret these fields; it already forwards the manifest. `atlas.ts` carries the name through the merged atlas (last plugin wins), and `main.ts` uses it with the content path as fallback. Add names to the `oc-classic` and `oc-hello` manifests and update the texture contract documents.

**Acceptance.** With the classic jar loaded, the HUD shows `e12 普通`, the TRAIN buttons show `普通` / `强化` / `精英` / `将领` / `战王` / `工程师`, and the BUILD buttons show `兵营·普通` / `矿场·普通`; with no name in the manifest the same line falls back to `unit.melee.t1` (never `undefined`); `check-render-math` gains a name-merge and fallback check; every check stays green (165 today, 158 when this stage landed).

**Files.** [atlas.ts](web-client/src/render/atlas.ts), [main.ts](web-client/src/main.ts), [ClassicContent.java](projects/oc/oc-game-server-classic/src/main/java/io/github/sob1234509876/oc/server/classic/ClassicContent.java), [manifest.json](projects/oc/oc-game-server-classic/src/main/resources/assets/manifest.json), [textures README](web-client/public/textures/README.md), [textures README zh](web-client/public/textures/README_zh_CN.md), [check-render-math.ts](web-client/scripts/check-render-math.ts).

**Cost.** small.

### a. Resources are invisible

**Symptom.** The HUD shows no gold or wood; after a few minutes the player cannot tell why `BUILD` or `TRAIN` answered `ERR cannot afford ...`. The starting stock exists but is invisible.

**Cause.** `WorldHost.snapshotJson()` emits only `tick`, `width`, `height`, `tilePixels`, `tiles` and `entities`; player inventories are not serialized. `worldViewFromSnapshot` has no `players` field to read, and `main.ts` has no HUD line for resources.

**Fix.** Add a `players` array to `/world/snapshot`, for example `[{"id":3,"name":"alice","gold":300,"wood":60}]`, built from each player's `logistics().available()` bundle and the `gold` / `wood` item paths the served catalogue registers; emit one row per player, sorted by id, with `0` for a path the catalogue does not define. Parse and validate it in `view.ts`, keep it on `ServerWorldView`, and render a HUD line for `myPlayerId` (`资源：alice · 金 300 · 木 60`), with dashes when the world has no row for the player yet. Refresh after a successful `BUILD` / `TRAIN` and on every snapshot. Extend `WorldHostCheck` and the snapshot section of the protocol document and its Chinese twin.

**Acceptance.** Immediately after `LOGIN` the HUD shows 300 gold and 60 wood; placing a tier 1 mine (80 gold) lowers gold to 220 within one 500 ms poll; training a tier 1 soldier (20 gold) lowers it again; `--world off` still shows the demo map with no resource line; `WorldHostCheck` (187) asserts the `players` shape and values; 813 stays green.

**Files.** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java), [WorldHostCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WorldHostCheck.java), [view.ts](web-client/src/render/view.ts), [main.ts](web-client/src/main.ts), [index.html](web-client/index.html), [protocol](protocol/oc-protocol.md), [protocol zh](protocol/oc-protocol_zh_CN.md).

**Cost.** small.

### b. Friend and foe are hard to tell apart

**Symptom.** With the classic atlas loaded, units of a given kind look the same regardless of owner; the player cannot tell their own engineer from the AI's soldiers, and clicking an enemy gives no ownership cue.

**Cause.** The snapshot carries `player`, and `worldViewFromSnapshot` maps it to `EntityView.playerId`; the only ground ring is the fixed gold selection ring (`#ffd166`). But the atlas sprite path in `renderer.ts` never reads `playerId`; only the procedural fallback passes it to `playerColor` when no atlas entry exists. The HUD selection line prints the kind and coordinates, not the owner.

**Fix.** Draw a ground ownership ring in the pre-sprite pass: blue `#3b82f6` when `playerId === myPlayerId`, red `#ef4444` for any other player, grey `#9ca3af` when `playerId` is missing or 0 or when `myPlayerId` is unknown (the demo map). Keep the gold `#ffd166` selection ring as a separate, thicker ring drawn after it so selection and ownership do not compete. Add an HUD label next to the selection (`我` / `敌` / `中立`). If the server later exposes `Player.team`, extend the classification to allies, but the snapshot has no team field today.

**Acceptance.** After login, the player's warehouse and engineer have blue rings, the AI's units red, player 1's scene red (different player); clicking an enemy shows `敌`; with no atlas the procedural shapes keep their player tint and still get the ring; the demo world (no `myPlayerId`) shows grey rings only; `check-render-math` gains a pure ownership-colour check and proves the selection ring colour is unchanged; every check stays green (165 today, 158 when this stage landed).

**Files.** [renderer.ts](web-client/src/render/renderer.ts), [fallback.ts](web-client/src/render/fallback.ts), [main.ts](web-client/src/main.ts), [view.ts](web-client/src/render/view.ts), [check-render-math.ts](web-client/scripts/check-render-math.ts).

**Cost.** small.

### f. Movement is not smooth

**Symptom.** Units advance in visible jumps: the page polls a snapshot every 500 ms, which is ten simulation ticks at 20 ticks per second, and every poll snaps positions to the new state.

**Cause.** `WORLD_POLL_MS` is `500`; `pollWorldSnapshot` replaces the whole world; `WorldRenderer.render` draws `entity.x` / `entity.y` directly, with no interpolation between snapshots and no previous position history.

**Fix.** Keep the last two snapshots plus their client receive times. At render time, match entities by id and interpolate position with `alpha = (now - previousReceived) / (currentReceived - previousReceived)`, clamped to `[0, 1]`; snap rather than interpolate when an id is missing from either side, when the tick gap is implausible, or when the entity is newly spawned/dead. The protocol stays OC/1 and the JSON shape does not change. Optionally, after this works, have the server push positions/deltas over the WebSocket at 10–20 Hz and interpolate between deltas; that is a protocol change and must follow the version rules in `protocol/oc-protocol.md`.

**Acceptance.** A moving unit's screen position changes every animation frame, not every 500 ms; average speed matches the server's; no overshoot when a unit reverses; a dead unit disappears on the next snapshot; `check-render-math` covers the interpolation endpoints, midpoint and id matching; every check stays green (165 today, 158 when this stage landed).

**Files.** [main.ts](web-client/src/main.ts), [view.ts](web-client/src/render/view.ts), [renderer.ts](web-client/src/render/renderer.ts), [check-render-math.ts](web-client/scripts/check-render-math.ts); no server change.

**Cost.** medium.

### g. No infinite map — done (stage g3)

**Symptom.** The hosted world stops at 256×256 and the browser used to show only the 64×64 window around the map centre; panning never asked the server for another window. Before stage g2 the world ended at 64×64 and every tile was the same flat ground.

**Cause.** The server side was fixed in stage g2: `WorldHost` sets `MAP_SIZE` to `256`, builds the world with a deterministic `NoiseTerrainGenerator` and the served catalogue's biome table, finds a buildable spawn anchor, and serves a windowed snapshot with `cx` / `cy` / `r`. The engine was already unbounded (`ChunkedMap`, `ChunkStreamer`), and a window request also moves the engine focus. The client was the remaining gap: `web-client/src/render/view.ts` and `main.ts` requested the no-parameter snapshot and did not translate `originX` / `originY` or send the camera centre, so the page could not pan beyond the centre window.

**Fix (stage g3, client).** `WorldView` now carries `originX` / `originY` and the full map size (`mapWidth` / `mapHeight`); terrain lookup subtracts the origin and returns `void` outside the received window, entities stay in absolute map coordinates, and the renderer draws or picks an entity only when its tile is inside the window. `main.ts` sends `GET /world/snapshot?cx=<cameraTileX>&cy=<cameraTileY>&r=32` from the camera focus, asks for a new window when the focus comes within 8 tiles of the window edge, keeps the old window until the new one arrives, and discards an out-of-order answer with a monotonic request id; the no-parameter request remains the first, deterministic centre request. The camera is clamped to the 256×256 map instead of the 64×64 window, so panning is not blocked by the loaded window, and the HUD shows the window origin and the map size. `web-client/README.md` and `README_zh_CN.md` document the streaming.

**Acceptance (met, server map size later superseded by stage i2).** The server serves deterministic noise terrain on a reported 256×256 map with a buildable spawn anchor and a `cx` / `cy` / `r` window; the same `--seed` reproduces the same terrain and anchor on a new host; two seeds give different worlds; starting bases still find buildable land; `WorldHostCheck` (187) is extended, not weakened. The client now requests camera-centred windows, translates absolute coordinates through `originX` / `originY`, keeps the old window until a newer one arrives, ignores out-of-order responses, and clamps the camera to the map; `check-render-math` covers the window parsing, the 8-tile edge trigger, the map-edge guard, request ordering and the map clamp. This stage printed **158** passing checks (142 plus 16); the current total is **165** after stage i3.

**Files.** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java), [OcRtsEngineBuilder.java](projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngineBuilder.java), [NoiseChunkGenerator.java](projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/NoiseChunkGenerator.java), [ChunkStreamer.java](projects/oc/oc-rts-engine/src/main/java/io/github/sob1234509876/oc/rts/map/ChunkStreamer.java), [WorldHostCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WorldHostCheck.java), [view.ts](web-client/src/render/view.ts), [stream.ts](web-client/src/render/stream.ts), [camera.ts](web-client/src/render/camera.ts), [renderer.ts](web-client/src/render/renderer.ts), [input.ts](web-client/src/render/input.ts), [main.ts](web-client/src/main.ts), [index.html](web-client/index.html), [client README](web-client/README.md), [protocol](protocol/oc-protocol.md), [protocol zh](protocol/oc-protocol_zh_CN.md), [05 browser endpoint](projects/oc/oc-game-server/help/05-browser-endpoint.md).

**Cost.** large.

### h. The server still assumed a finite map — done (stage i2)

**Symptom.** The engine could already generate any signed tile, but `WorldHost` still reported a
`MAP_SIZE = 256` map: the spawn anchor scanned a 256-tile square, snapshots clamped the centre into
`0..255`, scene and base placement fell back to a row-major scan of the whole map, the classic AI's site
search did the same, and `oc-debug` cast `world.map()` to the finite `ChunkedMap`.

**Fix (stage i2, server).** `WorldHost` now builds the world with `OcRtsEngineBuilder.infiniteTerrain(seed)`
and a positive `maxLoadedChunks` budget (`WorldHost.DEFAULT_CHUNK_BUDGET = 4096`, exposed as `--chunks N`);
the map size constant is deleted and with it every `width()` / `height()` / `MAP_SIZE` reference. The spawn
anchor scans rings from `(0,0)` with a 64-tile bound, and every former whole-map fallback (scene, human base,
builder tile) became a bounded ring search around the same anchor. `snapshotJson(cx, cy, r)` accepts any
signed centre and clamps only `r` to `16..64`; origins may be negative or huge, the JSON carries no map
width/height, and a request without parameters is centred on the spawn anchor. `ClassicAi` uses a fixed
`(192,192)` opening anchor far from the host scene and a 64-tile site search from its own oldest building;
`DebugPlugin` reads the throwaway world through `StreamingTerrainMap`, reports `engine-map-bounded: false`
and `engine-chunks-budget: 4096`, and no longer casts to `ChunkedMap`. `WorldHostCheck` grew from 163 to 187
assertions (startup time and chunk budget, `(0,0)` anchor, negative and far origins, far-window terrain,
a full HTTP far-centre window, unchanged scene/base buildability), the classic determinism section asserts
all AI entities stay within 64 tiles of its base over 600 ticks, and the debug check asserts the unbounded
report. Totals at that stage: game server 813 (was 789), debug 85 (was 82), classic 293 (was 291); classic is
368 after the warehouse and build-reach stages, and the engine is 515.

**Known limit.** `web-client/` was deliberately left untouched in this stage; it still validated against
`DEFAULT_MAP_SIZE_TILES = 256` and rejected negative entity coordinates, so rendering the new negative-origin
windows needed a separate client stage. That stage is i3 below; the client now treats the world as unbounded.

**Files.** [WorldHost.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/world/WorldHost.java), [OcGameServer.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/OcGameServer.java), [HttpWebServer.java](projects/oc/oc-game-server/src/main/java/io/github/sob1234509876/oc/server/ws/HttpWebServer.java), [ClassicAi.java](projects/oc/oc-game-server-classic/src/main/java/io/github/sob1234509876/oc/server/classic/ClassicAi.java), [DebugPlugin.java](projects/oc/oc-game-server-debug/src/main/java/io/github/sob1234509876/oc/server/debug/DebugPlugin.java), [WorldHostCheck.java](projects/oc/oc-game-server/src/selfcheck/java/io/github/sob1234509876/oc/server/check/WorldHostCheck.java), [ClassicCheck.java](projects/oc/oc-game-server-classic/src/selfcheck/java/io/github/sob1234509876/oc/server/classic/check/ClassicCheck.java), [DebugPluginCheck.java](projects/oc/oc-game-server-debug/src/selfcheck/java/io/github/sob1234509876/oc/server/debug/check/DebugPluginCheck.java), [05 browser endpoint](projects/oc/oc-game-server/help/05-browser-endpoint.md), [protocol](protocol/oc-protocol.md), [protocol zh](protocol/oc-protocol_zh_CN.md).

**Cost.** large.

### i. The browser client still assumed a finite map — done (stage i3)

**Symptom.** After stage i2 the server accepted any signed window centre, but the page rejected the documents that
resulted: `worldViewFromSnapshot` refused negative entity coordinates and invented a 256-tile `DEFAULT_MAP_SIZE_TILES`
when a snapshot omitted `mapWidth` / `mapHeight`. The camera was clamped to that 256 x 256 map, the HUD printed the map
size, and a window whose origin was negative could never be rendered, so panning far past the spawn was impossible.

**Cause.** Stage i2 deliberately left `web-client/` untouched to keep the server change reviewable; the client was
still built for the earlier finite-map snapshot contract.

**Fix (stage i3, client).** `view.ts` drops `mapWidth` / `mapHeight` and `DEFAULT_MAP_SIZE_TILES` entirely. A window
origin must be a signed integer (negative and very large values are accepted), an entity position must be finite and
signed (fractional or far outside the window is fine), and nothing is validated against a map. Absolute-to-window
translation subtracts the signed origin after flooring, so negative origins, negative tiles and the `-1` boundary all
index correctly. `camera.ts` is built once with a caller-supplied home point, has no world size, and clamps the centre
only to a documented `CAMERA_SAFETY_BOUND_TILES = 500_000` numeric guard (the engine's single-precision `Vec2` loses
sub-pixel resolution beyond roughly that magnitude); its focus tile uses `Math.floor` so west/north coordinates are
correct. `stream.ts` computes `snapshotOrigin(centre, radius) = centre - radius` for any signed centre, keeps the
signed edge trigger and the duplicate-origin guard, and keeps the monotonic request tracker. `main.ts` requests a
camera-centred window at any signed focus (including the first poll), keeps the old window whenever a request fails or
its answer is rejected as out of order, and never rebuilds the camera or the input controller when a window arrives.
The HUD replaces the map size with the camera coordinates and keeps the window origin
(`坐标：-1234,-567 · 窗口：-1250,-582`), and the demo world still starts on its own `0,0` window. Both README twins
document the unbounded world, the safety bound and the new HUD line.

**Acceptance (met).** `tsc --noEmit` is clean. `check-render-math` proves negative entity coordinates are accepted and
round trip; a negative origin maps an absolute tile to the right window index (including the `-1` boundary); a
negative-origin window draws its terrain and returns `void` just outside it; the camera reaches `-100000` without
clamping and is stopped at `±500000`; a negative centre produces
`/world/snapshot?cx=-1250&cy=-582&r=32`; and an out-of-order response leaves the previously loaded window in place.
The script now prints **165** passing checks (was 158).

**Files.** [view.ts](web-client/src/render/view.ts), [camera.ts](web-client/src/render/camera.ts),
[stream.ts](web-client/src/render/stream.ts), [main.ts](web-client/src/main.ts), [input.ts](web-client/src/render/input.ts),
[index.html](web-client/index.html), [check-render-math.ts](web-client/scripts/check-render-math.ts),
[client README](web-client/README.md), [client README zh](web-client/README_zh_CN.md).

**Cost.** medium.

## 5. Known debts

- **No real browser end-to-end run.** `pnpm check:render` exercises pure math; no headless/browser automation in this repository drives the actual page, canvas or WebSocket.
- **Gameplay commands are proven at the dispatcher level, not over a live socket.** `PlayerCommandCheck` (87) and `JoinBaseCheck` (60) drive `WorldHost` / `CommandDispatcher` directly; there is no browser-to-server socket test for `ORDER`, `BUILD` or `TRAIN`.
- **Allied players never fight, but only the different-player case is asserted.** `CombatSystem.isEnemy` uses `Player.isAlliedWith`, yet no check creates two players with the same `team` and proves they do not target each other.
- **No performance test with thousands of units.** The spatial index, path budget and tick pipeline are exercised at scenario scale, not at a stress scale.
- **No save or load of world state.** Only user records persist in `data/users.txt` through `LocalUserStore`; entity positions, terrain edits and production queues are lost on restart, and the engine's `ChunkStore` seam is not connected on the server.
- **The leather/stone/iron/gold/diamond art progression is documented but not machine-asserted.** The classic README describes the ladder; no check proves the atlas pixels or the tier art actually follow it (the stat and cost ladder is checked).
- **The debug plugin has no browser-side equivalent.** The page can call `engine-info`, but only after the operator pastes `debug.key` by hand; there is no browser CLI or debug panel comparable to `oc-debug`'s CLI.

## 6. Suggested next features

1. **Expose the terrain generator's thresholds.** The server already uses `NoiseTerrainGenerator`'s biome table for the unbounded world (stage i2); expose the sea/forest/hill/rock thresholds as content or server options so worlds can be tuned without a code change.
2. **Ranged units and a second building line.** Add a unit with `range > 1` and a building that produces it, reusing `CombatSystem`'s range and cooldown rules; the current melee-only ladder proves the combat layer but not projection.
3. **Save and load.** Use the engine's `ChunkStore` seam plus a serialized snapshot of players, entities and production queues; today only accounts survive a restart.
4. **A WebSocket gameplay end-to-end check.** Start a real server, connect a real client socket, `LOGIN`, send `ORDER` / `BUILD` / `TRAIN`, and assert both the wire answers and the resulting snapshot. This closes the largest debt above.
5. **An English translation of the Chinese `help/` set.** The chapters are deliberately Chinese for now; add `_en` twins or an English build only when the Chinese source is stable, and keep the protocol's English default and Chinese twin in sync.

## 7. Working rules

- **Java 17 and JPMS.** The toolchain is pinned to 17; every module has a `module-info.java`. Do not add automatic modules or classpath hacks.
- **Zero third-party dependencies in the Java modules.** The only dependencies are the JDK and the in-repo modules; do not add a library to solve a problem the JDK can solve.
- **Always `--offline`.** `gradle.bat ... --offline` must work on a machine with no network; a change that needs a download is the wrong change.
- **Determinism inside the simulation.** No `java.util.Random`, no wall clock (`System.currentTimeMillis`, `System.nanoTime`) and no threads inside the simulation. Use `DeterministicRandom`, fixed ticks and explicit id/coordinate tie-breaks. The server's pacing thread is the one place a clock may drive the simulation; transport and storage code may read a clock for timestamps, but the simulation must not.
- **Daemon threads only in the server.** Plugin threads must be daemon and stopped in `onDisable`; a non-daemon thread keeps the JVM alive after shutdown.
- **One verified stage per commit.** Run the six Java checks and both client checks before committing; do not mix a refactor and a feature in one commit.
- **When a total changes, update every document that quotes it.** Grep the old number (for example `813` before the next change) across the repository; the READMEs, `help/` and the protocol may all mention it.
- **The docs are bilingual.** Every statement changed in an English file must be changed in its `_zh_CN.md` twin. The `help/` chapters are a Chinese documentation set by design; `protocol/oc-protocol.md` is English by default with `protocol/oc-protocol_zh_CN.md` as its twin, so both must move together.
- **Runtime folders are not source.** `plugins/` is the operator drop zone and `data/` is server state; `plugins/*.jar` and `data/` are gitignored. Never commit a plugin jar, a key or an account file.

## 8. Handoff checklist

- [ ] Run the six Java self checks from §2; all six totals match.
- [ ] Run `tsc --noEmit` and `node scripts/check-render-math.ts`; 203/203.
- [ ] Open the game once by hand: classic jars in `plugins/`, server up, `pnpm dev`, REGISTER/LOGIN, see the warehouse, engineer, 300 gold and 60 wood.
- [ ] Read [help/03-plugins.md](projects/oc/oc-game-server/help/03-plugins.md) before touching `PluginLoader` or `PluginContext`.
- [ ] Confirm `git status` is clean before starting; do not commit jars, keys or `data/`.
- [ ] Pick one problem from §4, write the failing check first, and keep every existing check green.
