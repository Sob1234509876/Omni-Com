English | [简体中文](README_zh_CN.md)

# oc-web-client

> Omni Com's optional browser client. It talks to the Java server over WebSocket and uses only DOM, Canvas 2D, and WebCrypto; it depends on no frontend framework.

## Parallel toolchains

The Java side and the Web side of this repository are two **completely parallel** toolchains, and neither calls the other:

| | Java side | Web side |
| --- | --- | --- |
| Build tool | Gradle wrapper (`gradlew` / `gradlew.bat`) | pnpm |
| Third-party dependencies | **Zero**; only JDK 17 and JPMS | the two devDependencies `typescript` and `vite` |
| Network | Not needed; `./gradlew build --offline` builds and runs the self-checks | the first `pnpm install` **needs the network**; dev/build works offline afterwards |
| Who calls whom | Gradle **never** calls npm or pnpm | pnpm **never** calls Gradle |

If pnpm install prints `[ERR_PNPM_IGNORED_BUILDS] Ignored build scripts: esbuild` that is pnpm 10 blocking
dependency lifecycle scripts by default, not a failure: esbuild's platform binary also arrives as an optional platform
package, so the client runs. The `pnpm.onlyBuiltDependencies` field in `package.json` allows that one script
explicitly, which keeps the warning (and a `--frozen-lockfile` CI run) quiet.

That gives two hard rules:

1. `./gradlew build --offline` must always be green, and its output has nothing to do with `web-client/`; Java still builds without `node_modules`.
2. A broken, missing, or offline Web client never affects the Java side; conversely, a Java server that is not running only means this page cannot connect.

## Prerequisites

| Requirement | Version |
| --- | --- |
| Node.js | 18+ (required by Vite 6; 20+ recommended; `pnpm check:render` executes TypeScript directly and needs 24+) |
| pnpm | 9+; `package.json` declares `packageManager: "pnpm@9"` |
| Browser | a modern browser with native ES modules, WebSocket, and WebCrypto |

Dependency ranges are pinned in `package.json`: `typescript` **^6.0.3** and `vite` **^6.0.0** (resolving to Vite 6.x).

## Running it

Start the Java server first, and confirm that it serves `/ws` and the static page on `25567` (how to start it and the port are governed by the server documentation and its startup output:
[oc-game-server quick start](../projects/oc/oc-game-server/help/01-getting-started.md)).

```shell
cd web-client
pnpm install
pnpm dev
```

Open the address Vite prints (usually `http://localhost:5173/`). When **both the host and the port on the page are left empty**, the client connects to the same-origin
`/ws`, which Vite's dev proxy forwards to `ws://127.0.0.1:25567`; fill in a host and port to connect directly. The same dev proxy also forwards
`/plugin-assets` to the Java server, so during development the textures likewise come from plugins rather than from `public/`. The same proxy forwards
`/world` to the Java server, which is how the game view polls the live `GET /world/snapshot`.

After connecting, the page sends `HELLO 1` first and enables the command buttons only once it receives `OK OC/1`. Then:

1. Fill in a username and password, click `REGISTER` to create an account (registering does not log in), then click `LOGIN`; only after that is `REQ` available. A successful `LOGIN` also sends `WHOAMI` automatically, stores the engine player id and shows it in the HUD; the gameplay panel uses that id for its ownership check.
2. Paste the contents of the server's `data/oc-debug/debug.key` into the `debug.key` text box.
3. Click `engine-info`: the page seals an envelope with `INFO json`, sends `REQ engine-info <base64>`, then opens the reply and formats the JSON.
4. Type a name into the `hello` name box (default `world`) and click `hello`: the page sends `REQ hello <base64(UTF-8 name)>`,
   decodes the reply's base64 into UTF-8 text, and shows it in the log. This requires installing the `oc-hello` plugin described in the next section first.
5. `PING`, `COUNT`, and `QUIT` work as the protocol table says.

Creating an account takes only two steps: click `REGISTER` once (the username is 3-32 characters and may use only letters, digits, `_`, `.`, and `-`; the password is at least 6 characters; the server validates both rules), then click `LOGIN` to sign in; a successful registration does **not** log in automatically. A reply of `OK …` from the server means the account was created; a duplicate name gets `ERR …`, and the page shows the message the server gave verbatim in the status line and the log.

OC/1 uses text frames only. If the server sends a binary frame, the page just logs one line, "the server sent a binary frame, ignored (the protocol uses text frames only)",
**without closing the connection**, and it does not drop commands still waiting for a reply; this matches the server's behavior of replying `ERR binary frames are not commands` and keeping the connection.

**Heartbeat.** While a connection is usable the page sends `PING` every 20 seconds (`HEARTBEAT_MS` in
[`src/heartbeat.ts`](src/heartbeat.ts)) through the same in-order queue as every other command; the server answers `PONG`, and both
lines appear in the log. The timer starts only after the `HELLO 1` / `OK OC/1` handshake succeeds, never queues a second `PING`
while one is waiting, and is cleared on close and before a reconnect. This keeps an idle page alive against the WebSocket idle
timeout (`/world/snapshot` is plain HTTP and does not refresh the socket); the `PING` button remains a manual one-off.

## The game view

The left side of the page is the whole Canvas 2D world view, and the right side (below it on narrow screens) is the original protocol panel; the panel's
`connect`, `REGISTER`, `LOGIN`, `PING`, `COUNT`, `engine-info`, `hello`, `QUIT`, and log behavior are exactly as before.
No new dependencies are installed: still the two devDependencies `typescript` and `vite`.

```shell
cd web-client
pnpm dev
```

Just open the address Vite prints; the view can be seen even when the Java server is not running, only the protocol panel cannot connect. The
**Hide game view / Show game view** button at the top of the right-hand protocol panel collapses the Canvas, letting the protocol panel fill the whole page and making the log easier to read. (The page's own interface is currently Chinese — `index.html` has `lang="zh-CN"` — so the on-screen labels differ from these English names.)

**Monochrome chrome.** The page chrome — panels, buttons, headings, HUD chips, the protocol log, the gameplay panel and the status line — is deliberately greyscale, with no hue at all; the error state is bright white text with a white left bar rather than red. The canvas is the exception: it is game content, so it keeps its own colours. The tab icon comes from [`oc.png`](public/oc.png) (copied unchanged from the repository-root `oc.png`), referenced by the `icon` and `apple-touch-icon` links in `index.html`.

The HUD is pinned to the top-left corner of the view and shows, in real time:

- World source: `世界：server · tick 42` while snapshots are arriving, `世界：demo` when the page is drawing its built-in fallback map;
- Window origin and full map size: `窗口：96,96 · 地图 256×256`. The origin advances as the camera streams a new window, while the map stays 256 tiles square; on the demo map the chip reads `窗口：0,0 · 地图 44×32`;
- Texture source: `atlas · oc-hello` (plugin ids are listed when there are at most three plugins), `atlas · 4 plugins`; `procedural` when it has fallen back to procedural drawing;
- the engine player id answered by `WHOAMI` (`玩家：3`; `玩家：—` before login, cleared again on every reconnect);
- the local player's resources from the `players` row whose id matches `WHOAMI`: `资源：alice · 金 300 · 木 60`. The label greys out and shows dashes (`资源：金 — · 木 —`) while the world has no row for the player yet - before login, on the demo map, or against an older server whose snapshot has no `players` field;
- the tile coordinates under the mouse;
- the zoom factor (0.5× to 2.5×);
- the smoothed FPS;
- the id, display name, ownership tag and ground coordinates of the selected entity (`选中：e12 普通 [我] (12.5, 8.5)`); the tag is `我` for the local player, `敌` for any other player and `中立` for an ownerless entity or before `WHOAMI` answers. When the texture manifest has no name for that kind, the raw kind is shown instead (`选中：e12 unit.melee.t1 [中立] (…)`), and `选中：无` means nothing is selected;
- the display name and id of the selected building when it belongs to the player (`建筑：兵工厂 1 级 #42`), again falling back to the raw kind (`建筑：building.armory.t1 #42`).

**Ownership rings.** Every entity also gets a thin ground ring under its sprite: blue `#3b82f6` when its `playerId` is the local player, red `#ef4444` for any other player, and grey `#9ca3af` when the owner is missing, zero, or no `myPlayerId` is known yet (which is the whole demo map). The gold selection ring is unchanged, is drawn after the ownership ring and stays on top. The classification lives in `src/render/view.ts` (`ownershipOf`, `ownershipColor`) so the ring and the HUD tag cannot disagree.

**Server world.** The game view is no longer limited to the built-in demo island. While the view is visible,
`main.ts` polls `GET /world/snapshot?cx=<cameraTileX>&cy=<cameraTileY>&r=32` every 500 ms (skipping a request while
the previous one is still in flight) and keeps the last two snapshots as frames for the animation loop to interpolate
between (see **Movement smoothness** below). The server answers a 256 x 256 map as a 64 x 64 window: `originX` /
`originY` are the window's absolute top-left tile, `width` / `height` are the **window** size, and entities keep
absolute map coordinates. `src/render/view.ts` `fetchWorldSnapshot(baseUrl, request)` converts the server JSON into
the same `WorldView` the renderer already consumes: terrain content paths become terrain ids, `originX` / `originY`
are kept, `health` becomes `healthRatio`, `player` becomes `playerId`, and the optional `players` array becomes typed
`PlayerView` rows (a snapshot without that field stays valid, so an older server keeps working). `terrainAt`
translates an absolute tile by subtracting the origin and returns `void` outside the window, and the renderer and the
entity picker only draw or select an entity whose tile is inside the window. A document with wrong row lengths, a
missing field, a non-finite coordinate, an entity outside the 256 x 256 map, a window that leaves the map, or a
malformed `players` row is rejected. The procedural `demoWorld(seed)` is kept intact as the fallback for a missing
snapshot (for example `--world off`, which answers 503). The timer is cancelled on `visibilitychange` while the tab
is hidden, so a background tab sends no snapshot requests. With `--http-port 0` nothing is polled and the page stays
on the demo map.

**Camera-driven window streaming.** The picture moves and the client follows it: `main.ts` keeps a camera focus (the
camera's centre tile) and asks for a new window centred on that focus as soon as it comes within 8 tiles of the
loaded window's edge, instead of panning into terrain it has not loaded. The old window stays on screen until the new
one arrives, so nothing goes blank and nothing flickers while a window is loading; a failed request keeps the last
window instead of dropping back to the demo map, and a monotonically increasing request id makes a late answer lose
to a newer one. At the 256-tile map edge the server clamps the window inwards, so
the same origin is detected and the client does not request the same window every frame. The camera is clamped to the
full 256 x 256 map, **not** to the 64 x 64 window, so panning is never stopped at the window edge and the renderer
simply draws whichever part of the loaded window overlaps the view. The HUD shows the window origin and the map size
(`窗口：96,96 · 地图 256×256`), which is how the streamed window is visible to the owner. The map is 256 tiles square
today: the snapshot reports the window size in `width` / `height` and does not repeat the map size, so the client
uses the documented `DEFAULT_MAP_SIZE_TILES = 256`; a snapshot that does carry optional `mapWidth` / `mapHeight`
wins over that constant.

**Movement smoothness.** A snapshot only arrives every 500 ms, so drawing each one as it landed made units step. The view now
keeps the previous and the current server frame (each a converted `WorldView` plus its tick) and redraws on every animation
frame at `interpolate(previous, current, alphaFor(now, currentFetchedAt, WORLD_POLL_MS))` from
[`src/render/interpolate.ts`](src/render/interpolate.ts). Entities present in both frames are linearly interpolated; one that
appears only in the new frame appears at its new position; one that disappears is gone; and an entity that moved more than
4 tiles between the two polls, or whose kind or owner changed, snaps to the new frame instead of sliding across the map. That
costs one poll interval of latency — the picture is one snapshot behind the newest one — which is the price of smooth motion
while the server keeps sending absolute positions per tick (the protocol does not change). The demo world is unaffected: it
has no snapshots and keeps its own animation clock. The first snapshot after a start, and the first snapshot of a
different streamed window, have no predecessor and are drawn directly; while the tab is hidden both the poll and the
animation loop stop.

### Controls

They take effect after the first click on the view; keyboard events are bound only to the canvas and never steal keystrokes from the protocol input fields.

| Action | Effect |
| --- | --- |
| Left click | selects the entity under the cursor; clicking empty ground clears the selection |
| Right click empty ground | sends `ORDER MOVE <selected> <tileX> <tileY>` for the selected unit, but only when that entity belongs to the player |
| Right click an entity that is not mine | sends `ORDER ATTACK <selected> <enemyId>` |
| Left drag | pans the camera (movements under 4 pixels count as a click) |
| Wheel | zooms around the cursor, keeping the tile under the cursor fixed |
| `WASD` / arrow keys | pan the camera |
| `+` / `-` | zoom in / out around the center of the view |
| `F` | resets the camera (centered, 1×) |
| `Esc` | cancels an armed BUILD placement; with no placement armed it clears the selection |

The **Gameplay** panel below the protocol commands drives the rest of the grammar. It only enables a command when the selection belongs to the player id in the HUD (`entity.playerId === myPlayerId`), so a click on someone else's unit can never send an order by accident:

- `STOP` sends `ORDER STOP <selected>` for the selected own unit.
- With an own building selected, the panel shows one `TRAIN <buildingId> <unitPath>` button per catalogued unit path (`unit.melee.t1` … `unit.melee.t5`, `unit.engineer`). The button text is the manifest display name when one is available (`普通`, `强化`, `精英`, `将领`, `战王`, `工程师` for the classic units), with the raw path in the tooltip; without a name it falls back to the raw path. A tier the current armory cannot produce is still offered and the server's `ERR ...` is shown in red.
- With the own engineer selected, the panel shows one `BUILD <engineerId> <buildingPath> <tileX> <tileY>` button per catalogued building path (`building.armory.t1`, `building.mine.t1`). Its text is the manifest display name (`兵工厂 1 级`, `矿井 普通`) or the raw path when there is none. Pressing one arms placement: the status line shows the mode, the tile under the cursor is highlighted, and the **next left click on the canvas** sends the command; the mode then clears. `Esc` cancels without deselecting the engineer.

`WHOAMI` is sent automatically after `LOGIN` (and on a new login) and its `OK player <id>` answer is parsed into the HUD field. Every gameplay command and answer is written to the log; an `ERR ...` answer is also shown verbatim in the red status line, while a success gets a short confirmation that uses the display name and keeps the raw id in parentheses, for example `已下训练 普通 (unit.melee.t1)` or `已在 20,40 下达 BUILD 兵工厂 1 级 (building.armory.t1)`. The wire command itself is unchanged and still carries `unit.melee.t1` / `building.armory.t1`. The catalogue lives in [`src/commands.ts`](src/commands.ts) with a comment that the server owns the truth: adding content on the server means updating that list too. The wire format takes whole tile indices; the engine interprets a `GridPos` as that tile's centre.

**Textures are provided by plugins**: each plugin packs `assets/manifest.json` and images into its own jar, the server merges them into
`GET /plugin-assets/manifest.json`, and the client reads that on startup and loads each image by its `atlasUrl`. When several plugins provide textures at once they are merged in array order,
**the later one overriding the earlier one** (same-named terrain path or entity kind, display name included). The local `public/textures/atlas.json` is no longer a source; this directory keeps only the
[contract document](public/textures/README.md) (manifest format, examples, foot anchor, one row of animation per entity).

The manifest may also carry **display names**: `entities.<kind>.name` beside the animation fields, and a name for a terrain tile either
as the named object form `tiles.<path>: {"rect": [x, y, w, h], "name": "草地"}` or, in the legacy array form, not at all. The client
merges the names with the frames (last plugin wins) and the HUD, the TRAIN/BUILD button labels and the success log lines use them;
a missing or blank name always falls back to the raw content path, so a manifest without names behaves exactly as before. Names are
display-only: the protocol commands keep sending the content path.
When the manifest is missing, the request fails, an image fails to load, or the response is `[]`, the client automatically falls back to procedural drawing: every terrain type gets its own color and detail,
units are circles, buildings are blocks with a roof line, and at higher zoom there is a 1-pixel grid.

> The initial map is the deterministic `demoWorld(seed)` placeholder. As soon as the Java server answers
> `/world/snapshot`, the view switches to that real engine world: a 256 x 256 map served as a 64 x 64 window that
> follows the camera; the render layer does not change because both sources use the same `WorldView` shape.

The rendering math and the pure client rules (camera clamping to the full map, cursor-centered zoom, placeholder map determinism, server snapshot
conversion and out-of-bounds rejection, absolute-to-window terrain translation, the 8-tile camera-driven window trigger,
the map-edge clamp and request ordering, snapshot interpolation and snapping, rejection of out-of-bounds atlas frames,
manifest display names and raw-id fallback, the on-disk classic manifest, plugin atlas merging and fallback, and the
heartbeat constant/start rule) can be checked offline:

```shell
pnpm check:render
```

It type-checks first, then runs [`scripts/check-render-math.ts`](scripts/check-render-math.ts) directly with Node 24's TypeScript type stripping, printing `ok` / `fail` line by line. The script currently prints 158 passing checks.

## hello plugin demo

`hello` is the request provided by the example plugin `oc-hello`, used to demonstrate the smallest closed loop of "a plugin answers a request". It needs no `debug.key`,
but like every `REQ` it requires `LOGIN` first. It only answers a greeting and reads or writes no game state.

Build the plugin jar, put it into the server's plugin directory, start the server, then start the browser client:

```shell
./gradlew --offline :projects:oc:oc-game-server-hello:jar
# copy projects/oc/oc-game-server-hello/build/libs/oc-game-server-hello-2.0a.jar into plugins/
./gradlew --offline :projects:oc:oc-game-server:server
# then, in web-client/:
pnpm install && pnpm dev
```

`oc-game-server-hello-2.0a.jar` is the default artifact name for the module with `version = '2.0a'`; if Gradle produces a different name,
the file in `projects/oc/oc-game-server-hello/build/libs/` is authoritative.

This jar also carries `assets/manifest.json` and a 128x128 `assets/atlas.png` (8x8 cells of 16x16): with the jar in `plugins/` and the server started, the
browser view automatically renders all seven terrain content paths — `water`, `sand`, `grass`, `forest`, `hill`, `rock`, `void` — from that atlas. The manifest holds 20 tile rectangles (16 art tiles plus the four alias keys `forest`, `hill`, `rock`, `void`) and an empty `entities`, so units fall back to procedural shapes. This step demonstrates the whole chain from a plugin resource in the jar to the server route to the screen.
No images need to be put into `public/`.

`plugins/` is the operator drop zone: plugin jars and the jars they depend on all live at this level; see
[`plugins/README.md`](../plugins/README.md). Plugin-generated state lives under the server's `data/`, not in `plugins/`.
The first `pnpm install` needs the network; after that, `pnpm dev` works offline.

The page encodes the name as UTF-8 and then base64 (standard alphabet, with `=` padding); the server decodes the request payload into raw bytes and hands them to the plugin,
then base64-encodes the bytes the plugin returns; the browser finally decodes the reply into UTF-8 text. `oc-hello` does no base64 itself
and adds no envelope. Names longer than 64 bytes are truncated by the plugin with an ellipsis appended.

## Building

```shell
pnpm build
```

`build` runs `tsc --noEmit && vite build`, and the output is in `web-client/dist/`. The Java server serves this static build at
`http://<host>:25567/`. After a build you can preview it locally with:

```shell
pnpm preview
```

`pnpm install` creates `node_modules/` and a lockfile; `node_modules/`, `web-client/dist/`, and
`web-client/.vite/` are already in `.gitignore`. The first install needs the network, the only step in the whole repository that downloads dependencies.

## `debug.key`

- On first enabling the `oc-debug` plugin the server generates `<data directory>/oc-debug/debug.key`, by default `data/oc-debug/debug.key`.
- Its content is 64 hexadecimal characters, that is a 32-byte AES-256 key; it may end with a newline.
- The operator **manually** copies this file to the machine running the browser, then pastes it into the text box on the page. The key is not delivered over HTTP/WebSocket and is not generated by the page.
- The server **never overwrites** this file; deleting it and letting the plugin regenerate it invalidates every old copy at once.
- Do not commit `debug.key` to the repository, paste it into an issue, or write it into a log.
- The `engine-info` envelope has a 60-second anti-replay window, so a clock difference of more than 60 seconds between two machines makes it fail.

`engine-info` uses `crypto.subtle`. Browsers provide it only in **secure contexts**: `http://127.0.0.1:25567/` and
`http://localhost:5173/` are fine; opening the page directly at `http://<LAN IP>:25567/` usually is not, so use localhost,
SSH port forwarding, or an HTTPS reverse proxy.

## Gameplay commands and the protocol panel

The protocol panel still offers only `PING`, `COUNT`, `REGISTER`, `LOGIN`, `REQ engine-info`, `REQ hello`, and
`QUIT`; apart from `REGISTER` creating an account, none of those changes game state. The separate **Gameplay**
panel sends the server's gameplay grammar — `WHOAMI`, `ORDER`, `BUILD`, and `TRAIN` — which **does** change the
world: orders move and fight, `BUILD` places and pays for a site, and `TRAIN` queues a unit. Every gameplay
command requires a successful `LOGIN` on the same connection and is validated by the server against ownership,
entity existence, the served content, and the player's stock. The page never changes server configuration, the
plugin directory, or an account's password/permissions.

- `engine-info` is a read-only report registered by `oc-debug`; the handler holds no writable reference, and the report is assembled from constants and temporary objects.
- `hello` is a read-only greeting registered by `oc-hello`; the handler only reads the name from the request and holds no engine, configuration, or network reference.
- `REGISTER` only creates a new account (a duplicate name gets `ERR …`); it does not log in and does not change an existing account's password or permissions.
- `LOGIN` only marks the identity on this connection and refreshes the last-login time recorded by the server; it changes no password, changes no permissions, and there is no password-change entry point.
- `WHOAMI` assigns (lazily) and reports the engine player id of this connection; it changes no game state itself.

## Files

| File | Purpose |
| --- | --- |
| `index.html` | page structure (Canvas view + HUD + protocol panel), no inline scripts; the module entry is `src/main.ts` |
| `src/protocol.ts` | OC/1 commands, base64 helpers, sealing/opening the AES-256-GCM envelope |
| `src/socket.ts` | WebSocket queue of one line command to one reply, timeouts, and close events |
| `src/commands.ts` | pure gameplay builders (`WHOAMI`, `ORDER`, `BUILD`, `TRAIN`), `parseWhoAmI`, and the catalogue the gameplay panel offers |
| `src/heartbeat.ts` | `HEARTBEAT_MS` (20 s), the pure `shouldHeartbeat(state)` start/stop rule, and the heartbeat's documentation |
| `src/main.ts` | DOM wiring: connect, handshake, automatic heartbeat, buttons, log, `engine-info`, `WHOAMI`/gameplay panel, display-name labels, starting the view/HUD, the camera-driven `/world/snapshot` streamer (8-tile edge trigger, request ids, old window kept until the new one arrives), and the animation loop that interpolates between the last two frames of the same window |
| `src/render/camera.ts` | world/screen coordinates, visible tile culling, panning, cursor-centered zoom, camera focus tile, and clamping to the full map |
| `src/render/atlas.ts` | plugin manifest parsing (frames and display names), composite atlas (last wins), label helpers with raw-id fallback, and image loading; returns `null` on failure |
| `src/render/fallback.ts` | terrain color blocks, unit/building shapes, grid lines, shadows, the selection ring and the thin ownership ring when there is no atlas |
| `src/render/interpolate.ts` | pure snapshot interpolation: `Frame`, `alphaFor` for one poll interval, and `interpolate` with the appear/disappear/snap rules |
| `src/render/stream.ts` | pure window-streaming rules: edge distance, the 8-tile trigger, the server-identical origin clamp that stops requests at a map edge, and the monotonic request tracker |
| `src/render/view.ts` | `WorldView` / `EntityView` / `PlayerView` shapes (including window origin and full map size), absolute-to-window terrain translation and the in-window entity rule, the ownership classification and colour rule, the deterministic `demoWorld(seed)` fallback, and the `/world/snapshot` windowed fetch/conversion |
| `src/render/renderer.ts` | drawing one Canvas 2D frame, DPR handling, y-sorting, animation frames, and the placement highlight |
| `src/render/input.ts` | pointer/keyboard controls bound only to the canvas, entity picking, right-click orders, and placement clicks |
| `public/textures/README.md` | plugin atlas contract (manifest format, examples, anchor) |
| `scripts/check-render-math.ts` | offline self-check of the camera (including its full-map clamp), map determinism, atlas validation, display-name labels and raw-id fallback, the on-disk classic manifest, plugin atlas merging, windowed snapshot parsing and origin translation, the window-edge trigger / map-edge guard / request ordering, snapshot `players` parsing, snapshot interpolation, ownership colours, the heartbeat rule, and gameplay command builders |
| `vite.config.ts` | `/ws`, `/plugin-assets` and `/world` dev proxies, `dist` output directory |

## Related documents

- Online protocol (the upstream of this document): [`protocol/oc-protocol.md`](../protocol/oc-protocol.md)
- Repository overview and parallel toolchains: [`README.md`](../README.md)
- Server startup, port, and data directory: [`01-getting-started.md`](../projects/oc/oc-game-server/help/01-getting-started.md)
