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
   decodes the reply's base64 into UTF-8 text, and shows it in a toast and in the log overlay. This requires installing the `oc-hello` plugin described in the next section first.
5. `PING`, `COUNT`, and `QUIT` work as the protocol table says.

Creating an account takes only two steps: click `REGISTER` once (the username is 3-32 characters and may use only letters, digits, `_`, `.`, and `-`; the password is at least 6 characters; the server validates both rules), then click `LOGIN` to sign in; a successful registration does **not** log in automatically. A reply of `OK …` from the server means the account was created; a duplicate name gets `ERR …`, and the page shows the message the server gave verbatim in the status line, as an error toast and in the log overlay.

OC/1 uses text frames only. If the server sends a binary frame, the page just logs one line, "the server sent a binary frame, ignored (the protocol uses text frames only)",
**without closing the connection**, and it does not drop commands still waiting for a reply; this matches the server's behavior of replying `ERR binary frames are not commands` and keeping the connection.

**Heartbeat.** While a connection is usable the page sends `PING` every 20 seconds (`HEARTBEAT_MS` in
[`src/heartbeat.ts`](src/heartbeat.ts)) through the same in-order queue as every other command; the server answers `PONG`, and both
lines appear in the log overlay as one exchange toast. The timer starts only after the `HELLO 1` / `OK OC/1` handshake succeeds, never queues a second `PING`
while one is waiting, and is cleared on close and before a reconnect. This keeps an idle page alive against the WebSocket idle
timeout (`/world/snapshot` is plain HTTP and does not refresh the socket); the `PING` button remains a manual one-off.

## The toast console and the log overlay

The permanent protocol log panel is gone. Each command/answer exchange now appears as a toast in the bottom-right corner:
one line for the sent command (`→`) and one for the answer (`←`), both trimmed to a single line so the whole exchange fits
one or two lines. A normal toast fades after about **4 seconds**; an error toast — an `ERR ...` answer or a client-side
refusal such as "log in first" — stays about **8 seconds** and is set apart by brightness and weight only (white border and
bold white text), because the page chrome is greyscale. The newest toast is on top, at most **5** are visible, and a sixth
push drops the oldest. Batch orders use one toast whose command and answer halves join the per-unit lines with ` · `.
Snapshot-diff events (`建筑完成：…` / `遭到攻击：…`) use the same info toast (no command half) and are also written to the history.

Nothing is lost: the small **日志** button at the top of the protocol panel opens a full-screen history overlay with the
original `#log` text in order. It has a **关闭** (close) button, a **清空** (clear) action that empties both the history and
the visible toasts, closes on `Esc` or a backdrop click, and returns focus to the button. The status line, the connection
form, the HUD, the gameplay panel and every element id the TypeScript binds are unchanged.

## The game view

The left side of the page is the whole Canvas 2D world view, and the right side (below it on narrow screens) is the original protocol panel; the panel's
`connect`, `REGISTER`, `LOGIN`, `PING`, `COUNT`, `engine-info`, `hello`, and `QUIT` commands are unchanged, while their exchanges are shown as toasts with the full history still available behind the **日志** button.
No new dependencies are installed: still the two devDependencies `typescript` and `vite`.

```shell
cd web-client
pnpm dev
```

Just open the address Vite prints; the view can be seen even when the Java server is not running, only the protocol panel cannot connect. The
**Hide game view / Show game view** button at the top of the right-hand protocol panel collapses the Canvas, letting the protocol panel fill the whole page;
the toast stack and the **日志** history overlay work in either view. (The page's own interface is currently Chinese — `index.html` has `lang="zh-CN"` — so the on-screen labels differ from these English names.)

**Monochrome chrome.** The page chrome — panels, buttons, headings, HUD chips, the protocol log, the gameplay panel, the minimap frame and the status line — is deliberately greyscale, with no hue at all; the error state is bright white text with a white left bar rather than red. The canvas is the exception: it is game content, so it keeps its own colours, and so do the minimap's terrain, ownership dots and white camera outline and the build-placement ghost's green/red/amber tiles. Every floating surface is also **translucent**: the side panels, the HUD chips, the minimap panel, the warehouse panel, the hover tooltip, the toast stack and the log-overlay panel use `rgba(0, 0, 0, 0.72)` with a 1px grey border, no blur and no glow (`backdrop-filter: none`), so the game view stays visible behind them. Text on those surfaces is `#e6e6e6` or white, which keeps at least 7:1 contrast even over the brightest terrain, and nothing ever puts a scrim or a filter over the canvas — the log-overlay container itself is transparent and only its panel is darkened. The tab icon comes from [`oc.png`](public/oc.png) (copied unchanged from the repository-root `oc.png`), referenced by the `icon` and `apple-touch-icon` links in `index.html`.

The HUD is pinned to the top-left corner of the view and shows, in real time:

- World source: `世界：server · tick 42` while snapshots are arriving, `世界：demo` when the page is drawing its built-in fallback map;
- Camera coordinates and window origin: `坐标：-1234,-567 · 窗口：-1250,-582`. Both may be negative and keep counting as the owner pans; the window origin changes as the camera streams a new window. On the demo map the chip starts at `坐标：22,16 · 窗口：0,0` (the demo island is its own window at origin `0,0`);
- Texture source: `atlas · oc-hello` (plugin ids are listed when there are at most three plugins), `atlas · 4 plugins`; `procedural` when it has fallen back to procedural drawing;
- the engine player id answered by `WHOAMI` (`玩家：3`; `玩家：—` before login, cleared again on every reconnect);
- the local player's resources and income rates from the `players` row whose id matches `WHOAMI`: `资源：alice · 金 300 (+30/分) · 木 60 (+20/分)`. The rate suffix comes from `goldPerMinute` / `woodPerMinute` and is omitted when an older snapshot lacks those fields (and `+0/分` is shown when a new server reports zero, so the operator can see that no mine is running). The label greys out and shows dashes (`资源：金 — · 木 —`) while the world has no row for the player yet - before login, on the demo map, or against an older server whose snapshot has no `players` field;
- the tile coordinates under the mouse;
- the zoom factor (0.5× to 2.5×);
- the smoothed FPS;
- the id, display name, ownership tag and ground coordinates of the selected entity (`选中：e12 普通 [我] (12.5, 8.5)`) when exactly one entity is selected; the tag is `我` for the local player, `敌` for any other player and `中立` for an ownerless entity or before `WHOAMI` answers. When the texture manifest has no name for that kind, the raw kind is shown instead (`选中：e12 unit.melee.t1 [中立] (…)`), and `选中：无` means nothing is selected. With more than one unit selected the same chip becomes `已选：3 个单位`, and the single-entity detail plus the building chip are intentionally suppressed;
- the display name and id of the selected building (`建筑：兵工厂 1 级 #42`), own or enemy, again falling back to the raw kind (`建筑：building.armory.t1 #42`). When the snapshot carries the footprint, the chip appends it as `· 尺寸：3×3`, and while the building is under construction a small inline bar shows the completion percentage (`▮ 42%`). A selected warehouse also appends its stored items, for example `· 库存：金 120 · 木 40`; only positive amounts are shown, in a fixed gold/wood order. A snapshot without `w`/`h`/`progress`/`contents` (an older server, the demo world) simply omits those parts;
- for the selected building, one small row per unfinished production job under the building chip: the manifest display name (raw `kind` when the manifest has no name), a thin `ticks / total` bar and the one-based `i/n` (`普通 ▮▮▯ 1/2`). A job that has not started reports `ticks === total`, so its bar starts empty. The canvas keeps the existing `生产中 ×N` marker; a legacy snapshot with `queue` but no `queueItems` shows the marker and no per-item rows.

**Double-click selection.** A double click acts on the whole loaded window, not only the clicked entity. On a unit it replaces the selection with every **own unit of the same kind whose ground tile is inside the loaded window** (the same visibility rule the renderer and the picker use); on a building - own, enemy or neutral - it selects just that building. It always replaces the selection and never merges into it. A drag is never part of a double click: the mouseup that ends a marquee, or a click that follows a camera/entity drag, cannot complete one, and the two clicks must land on the same target within 350 ms without moving more than 6 px.

**Hover tooltip.** While the game view is visible and the pointer rests on an entity, a small tooltip follows the cursor. It is a DOM element, not canvas: it shows the manifest display name, the ownership tag (`我` / `敌` / `中立`), the health percentage and - for a building - the reported footprint (`尺寸：3×3`) and the queue summary (`生产中 ×N`). It is greyscale and translucent like the other chrome, sets `pointer-events: none` so it never steals a click, stays hidden during a marquee or an armed BUILD placement, and is clamped inside the browser window (near an edge it flips to the left of or above the cursor). A snapshot without `health` / `w` / `h` / `queue` omits those parts instead of inventing values.

**Derived events.** The server has no event feed, so the client compares consecutive snapshots. A building that was not finished (`progress !== 1`) and now reports `progress === 1` toasts `建筑完成：<name>`; a player-owned entity whose health ratio dropped by at least 10 percentage points toasts `遭到攻击：<name>`. Only entities present in both snapshots are compared, and one entity is de-duplicated for five seconds, so a fight that drains health over several 500 ms polls does not toast on every poll. This is entirely client-side - no protocol or server change.

**Warehouse panel.** The top-right corner of the view holds a small **read-only** total of the local player's warehouses: the `contents` objects of all own buildings are summed per item, labelled with the item's display name and sorted by name. With no own warehouse it says `未发现自己的仓库`; an empty warehouse reports an empty total. The panel only reads the snapshot. It does not add a "marked resource" or resource-selection feature, because the server has neither and the client cannot honestly invent one.

**Engineer shortcut.** The gameplay panel's `工兵` button and the `O` hotkey select every own `unit.engineer` inside the loaded window, replacing the current selection. The snapshot carries no order or busy state, so an idle engineer cannot be distinguished from a working one: the button is deliberately labelled `工兵` rather than `空闲工兵`, and it selects all engineers, not only idle ones.

**Ownership rings.** Every entity also gets a thin ground ring under its sprite: blue `#3b82f6` when its `playerId` is the local player, red `#ef4444` for any other player, and grey `#9ca3af` when the owner is missing, zero, or no `myPlayerId` is known yet (which is the whole demo map). The gold selection ring is unchanged, is drawn after the ownership ring and stays on top. The classification lives in `src/render/view.ts` (`ownershipOf`, `ownershipColor`) so the ring and the HUD tag cannot disagree.

**Build placement ghost.** While a building path is armed, the page previews the whole footprint under the cursor before any click: the footprint's width and height come from the building kind (a kind the client does not know is assumed to be **3x3**, and the table in `src/render/placement.ts` mirrors the classic catalogue), the hovered tile is the footprint's top-left tile, and every footprint tile is painted with one of three states:

- **legal** - a green outline with a 25% fill;
- **blocked** - a red outline and fill, because a footprint tile is not buildable terrain in the snapshot (only `grass` and `sand` are), is outside the loaded window, or is already covered by an entity's footprint;
- **out of reach** - an amber outline and fill, because the site is too far from the selected engineer.

The status line names the verdict while hovering: `可建造`, `地形不可建`, `位置被占用`, `超出工兵范围 16 格`, or `超出已载入窗口`. The ghost and the click share one pure verdict (`buildPlacementPreview`): **only a legal site sends `BUILD`**; an illegal click keeps the mode armed, reports the reason (status line, log and error toast) and sends nothing. The states combine in the documented order **blocked > out of reach > legal**. The reach test is the client's approximation of the server's rule: the server allows a *path* of at most 16 tiles to the footprint's one-tile approach ring (`CommandService.MAX_BUILD_PATH_TILES = 16`), while the client only compares the Chebyshev gap between the engineer's tile and the footprint rectangle - a site 16 tiles away is accepted, 17 is not. The approximation can accept a site behind an obstacle, but it never rejects one the server would accept for distance alone; the server still validates everything. The ghost colours are canvas content, exactly like the ownership rings: the panels, HUD and status line around them stay greyscale.

**Minimap.** The bottom-left corner of the game view holds a fixed **180x180** overview panel on its own small canvas. The panel chrome is greyscale (1px grey border, translucent `rgba(0, 0, 0, 0.72)` background) and only its canvas area takes pointer input, so the rest of the world view keeps normal mouse, wheel and keyboard behaviour; there is no scrim and no filter over the game view. Inside, the loaded window's terrain is drawn as **one pixel per tile** (an offscreen tile-sized canvas scaled up with smoothing disabled), every in-window entity is a **2x2 dot** coloured by the same ownership rule as the ground rings (mine blue, enemy red, neutral grey), and the camera's visible rectangle is a **white 1px outline**. **Click or drag** on the minimap to move the camera: the clicked point is reduced by the letterbox offset, divided by the per-tile scale and **floored**, then added to the signed window origin, so the mapping is exact for negative world coordinates; the camera centres on that floor tile and the target is clamped to the same ±500,000-tile numeric safety bound the camera uses. The terrain image is rebuilt only when the window or its terrain changes, while the dots and the camera rectangle are redrawn every animation frame.

**Server world.** The game view is no longer limited to the built-in demo island. While the view is visible,
`main.ts` polls `GET /world/snapshot?cx=<cameraTileX>&cy=<cameraTileY>&r=32` every 500 ms (skipping a request while
the previous one is still in flight) and keeps the last two snapshots as frames for the animation loop to interpolate
between (see **Movement smoothness** below). The server world is **unbounded**: a snapshot carries only one square
window, `originX` / `originY` are its absolute top-left tile, `width` / `height` are the **window** size, and entities
keep absolute world coordinates. The document carries no `mapWidth` / `mapHeight` and no map size at all; both the
window origin and every entity coordinate may be negative or very large. `src/render/view.ts`
`fetchWorldSnapshot(baseUrl, request)` converts the server JSON into the same `WorldView` the renderer already
consumes: terrain content paths become terrain ids, `originX` / `originY` are kept as signed integers, `health` becomes
`healthRatio`, `player` becomes `playerId`, and the optional `players` array becomes typed `PlayerView` rows (a
snapshot without that field stays valid, so an older server keeps working). The optional entity detail fields are
additive too: `w` / `h` become `footprint`, `progress` becomes a clamped fraction, `queue` / `queueTicks` become the
production queue size and the remaining ticks of the running job, `queueItems` becomes a validated array of
`kind` / `ticks` / `total` jobs (a malformed entry is dropped), and a warehouse's `contents` becomes a validated
object of positive integer amounts. Player rows keep the optional `goldPerMinute` / `woodPerMinute` as numbers, or
leave them `undefined` for an older server. A snapshot without any of them converts exactly as before; a malformed
optional field is ignored rather than rejecting the world.
`terrainAt` translates an absolute tile by
subtracting the origin (flooring first, so a negative fractional position lands in the correct cell) and returns `void`
outside the window; the renderer and the entity picker only draw or select an entity whose tile is inside the window.
A document with wrong row lengths, a missing field, a non-finite coordinate or a non-integer window origin is rejected -
but a negative or far-away coordinate is not an error, because there is no map boundary to cross. The procedural
`demoWorld(seed)` is kept intact as the fallback for a missing snapshot (for example `--world off`, which answers 503).
The timer is cancelled on `visibilitychange` while the tab is hidden, so a background tab sends no snapshot requests.
With `--http-port 0` nothing is polled and the page stays on the demo map.

**Camera-driven window streaming.** The picture moves and the client follows it: `main.ts` keeps a camera focus (the
camera's centre tile) and asks for a new window centred on that focus as soon as it comes within 8 tiles of the loaded
window's edge, instead of panning into terrain it has not loaded. The old window stays on screen until the new one
arrives, so nothing goes blank and nothing flickers while a window is loading; a failed or rejected request keeps the
last window instead of dropping back to the demo map, and a monotonically increasing request id makes a late answer lose
to a newer one. The requested centre is sent exactly as it is, so the server answers the origin `centre - r` for any
signed centre: there is no map edge at which a window is clamped or shifted inwards.

**The world is unbounded.** There is no map width, height or boundary anywhere in the client model or the camera. The
camera is clamped only to a very generous **safety bound of ±500,000 tiles** (16 million world pixels at 32 px/tile),
and that bound is not gameplay: it exists because the engine stores positions in single-precision `Vec2` values, whose
resolution is already about one world pixel near that magnitude. The camera is built once and is never rebuilt when a
window arrives: a snapshot only changes which terrain is loaded, not where the camera may go. Panning therefore keeps
working in every direction, into negative coordinates and far past the spawn. The HUD shows the camera coordinates and
the loaded window origin (`坐标：-1234,-567 · 窗口：-1250,-582`), so the owner can watch both go negative and keep
counting.

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
| Left click | replaces the selection with the entity under the cursor; clicking empty ground clears it |
| Double-click a unit | replaces the selection with every **own unit of the same kind inside the loaded window**. Enemy/neutral units of that kind and same-kind units outside the window are excluded |
| Double-click a building | replaces the selection with just that building, own or enemy. A drag is never half of a double click, so the click that ends a marquee (or follows a drag) cannot complete one |
| Hover an entity | shows the greyscale DOM tooltip near the cursor (name, owner tag, health, building footprint, queue summary); it is hidden during a marquee or BUILD placement |
| Shift + left click | adds the clicked unit, or removes it when it is already selected (a building still replaces the selection, because it has no group order) |
| Left drag on empty ground | draws a marquee rectangle and selects every **own unit** whose drawn position is inside it; the rectangle is converted from canvas space to world tiles, so it works in negative coordinates too. Enemy and neutral units and all buildings are excluded |
| Left drag starting on an entity | still selects that entity (it never becomes a marquee) |
| Middle-button drag, or Space held + left drag | pans the camera. A left drag on empty ground is the marquee, so it no longer pans |
| Right click empty ground | sends `ORDER MOVE <id> <tileX> <tileY>` for **every selected own unit**, one line per unit |
| Right click an entity that is not mine | sends `ORDER ATTACK <id> <enemyId>` for every selected own unit |
| Wheel | zooms around the cursor, keeping the tile under the cursor fixed |
| Click or drag on the minimap | centres the camera on the floor tile under the pointer, clamped to the camera's numeric safety bound. The panel owns only its 180px square; everywhere else the world canvas keeps its normal input |
| `WASD` / arrow keys | pan the camera |
| `+` / `-` | zoom in / out around the center of the view |
| `F` | resets the camera to its starting point and 1× |
| `O` | selects every own engineer inside the loaded window (same as the `工兵` button). The snapshot has no order state, so this is all engineers, not only idle ones |
| `Ctrl` + `1` … `9` (`Cmd` works too) | assigns the current selection to that control group, replacing what the group held. The selection may include a building; an empty selection clears the group |
| `1` … `9` | recalls that control group. Dead entity ids are pruned first; an empty group is reported as `编队 N 为空` on the status line instead of silently clearing the selection. The HUD selection line is prefixed with `编队 N ·` while the selection came from that group |
| `Shift` + `1` … `9` | adds the current selection to that group without removing what it already held |
| `Esc` | cancels an armed BUILD placement; with no placement armed it clears the selection |

Selection is shared by every order path: the renderer draws the gold ring on every selected entity, the HUD shows
`已选：N 个单位` when more than one is selected, and the gameplay panel enables `STOP` as soon as at least one own unit is
selected. `STOP` sends `ORDER STOP <id>` for each of those units. Control groups are selection state too: the pure
`assign` / `add` / `recall` / `prune` / `describe` rules live in [`src/controlGroups.ts`](src/controlGroups.ts), the page
prunes groups on every snapshot, and assigning is a no-op for command legality - a group can hold a building, but only
the normal ownership and unit checks decide what an order may target. A left click still goes through placement mode first:
while a `BUILD` is armed, the next left click on the canvas places the site and does not change the selection, and an
empty-ground left drag in that mode keeps panning the camera.

The **Gameplay** panel below the protocol commands drives the rest of the grammar. It only enables a command when the selection belongs to the player id in the HUD (`entity.playerId === myPlayerId`), so a click on someone else's unit can never send an order by accident:

- `STOP` sends `ORDER STOP <selected>` for every selected own unit; it is enabled as soon as the selection contains one.
- `工兵` (hotkey `O`) is a selection shortcut, not a command: it selects every own `unit.engineer` in the loaded window. The snapshot has no order/busy flag, so it cannot select only idle engineers; the label and its tooltip say `工兵`, and both READMEs note the limitation.
- With an own building selected (exactly one entity), the panel shows one `TRAIN <buildingId> <unitPath>` button per catalogued unit path (`unit.melee.t1` … `unit.melee.t5`, `unit.engineer`). The button text is the manifest display name when one is available (`普通`, `强化`, `精英`, `将领`, `战王`, `工程师` for the classic units), with the raw path in the tooltip; without a name it falls back to the raw path. A tier the current armory cannot produce is still offered and the server's `ERR ...` is shown in the status line and as an error toast. The TRAIN/BUILD sections appear only when the selection is exactly one own building/engineer.
- With the own engineer selected (exactly one entity), the panel shows one `BUILD <engineerId> <buildingPath> <tileX> <tileY>` button per catalogued building path (`building.armory.t1`, `building.mine.t1`, `building.lumbercamp.t1`, `building.warehouse`). Its text is the manifest display name (`兵工厂 1 级`, `矿井 普通`, the lumber camp's own name when the manifest provides one) or the raw path when there is none. Pressing one arms placement: the status line shows the mode, the whole footprint under the cursor is previewed with its green legal / red blocked / amber out-of-reach tiles and the reason while hovering, and only a **legal** site lets the next left click on the canvas send the command; the mode then clears. An illegal click keeps the mode armed, reports `地形不可建`, `位置被占用`, `超出工兵范围 16 格` or `超出已载入窗口` and sends nothing. `Esc` cancels without deselecting the engineer. The 16-tile check is the client's distance approximation of the server's 16-tile path rule; see **Build placement ghost** above.

`WHOAMI` is sent automatically after `LOGIN` (and on a new login) and its `OK player <id>` answer is parsed into the HUD field. Every gameplay command and answer appears in the log overlay and as an exchange toast (a group order is one toast whose command and answer halves join the per-unit lines); an `ERR ...` answer becomes an error toast and is also shown verbatim in the red status line, while a success gets a short confirmation that uses the display name and keeps the raw id in parentheses, for example `已下训练 普通 (unit.melee.t1)` or `已在 20,40 下达 BUILD 兵工厂 1 级 (building.armory.t1)`. The wire command itself is unchanged and still carries `unit.melee.t1` / `building.armory.t1`. The catalogue lives in [`src/commands.ts`](src/commands.ts) with a comment that the server owns the truth: adding content on the server means updating that list too. The wire format takes whole tile indices; the engine interprets a `GridPos` as that tile's centre.

**Textures are provided by plugins**: each plugin packs `assets/manifest.json` and images into its own jar, the server merges them into
`GET /plugin-assets/manifest.json`, and the client reads that on startup and loads each image by its `atlasUrl`. When several plugins provide textures at once they are merged in array order,
**the later one overriding the earlier one** (same-named terrain path or entity kind, display name included). The local `public/textures/atlas.json` is no longer a source; this directory keeps only the
[contract document](public/textures/README.md) (manifest format, examples, foot anchor, one row of animation per entity).

The manifest may also carry **display names**: `entities.<kind>.name` beside the animation fields, and a name for a terrain tile either
as the named object form `tiles.<path>: {"rect": [x, y, w, h], "name": "草地"}` or, in the legacy array form, not at all. The client
merges the names with the frames (last plugin wins) and the HUD, the TRAIN/BUILD button labels and the success status/toast lines use them;
a missing or blank name always falls back to the raw content path, so a manifest without names behaves exactly as before. Names are
display-only: the protocol commands keep sending the content path.
When the manifest is missing, the request fails, an image fails to load, or the response is `[]`, the client automatically falls back to procedural drawing: every terrain type gets its own color and detail,
units are circles, buildings are blocks with a roof line, and at higher zoom there is a 1-pixel grid. The same pass draws
the greyscale status chrome above a building: a thin construction bar (light grey track, white fill, one-pixel border)
while the site is unfinished, and a small `生产中 ×N` marker while its production queue is non-empty. Both sit above the
roof, so they never cover the sprite.

> The initial map is the deterministic `demoWorld(seed)` placeholder. As soon as the Java server answers
> `/world/snapshot`, the view switches to that real engine world: an unbounded world, one 64 x 64 window at a time,
> following the camera; the render layer does not change because both sources use the same `WorldView` shape.

The rendering math and the pure client rules (camera floor semantics and the ±500,000-tile numeric safety bound,
cursor-centered zoom, placeholder map determinism, unbounded signed snapshot parsing, negative-origin window
translation, the 8-tile camera-driven window trigger, signed window origins and request ordering, snapshot
interpolation and snapping, rejection of out-of-bounds atlas frames, manifest display names and raw-id fallback, the
on-disk classic manifest, plugin atlas merging and fallback, the heartbeat constant/start rule, the marquee
rectangle-to-negative-tile conversion and own-only containment, Shift+click toggle, the toast lifetime/cap/error-tone
rules, the entity status rules (integer progress formatting, the bar/queue/size helpers and the optional
`footprint` / `progress` / `queue` / `queueTicks` / `contents` fields), the warehouse stock label, the building catalogue including `building.lumbercamp.t1` and `building.warehouse`, the build-placement ghost (classic 3x3
footprint lookup with the unknown-kind 3x3 default, row-major footprint tiles, occupancy from the entity list, the reach boundary at 16 and 17 tiles, the blocked > out-of-reach > legal precedence and the five reason texts), the minimap (aspect-fit layout, floor-based panel-to-world mapping in both directions including negative origins, and the safety-bound camera jump), the double-click same-kind rule (own only, in-window only, the building case), the engineer selection rule, the tooltip text composition and window clamping, the snapshot diff events (the 10% health-drop threshold and the five-second per-entity de-duplication), and the warehouse total aggregation) can be checked offline:

```shell
pnpm check:render
```

It type-checks first, then runs [`scripts/check-render-math.ts`](scripts/check-render-math.ts) directly with Node 24's TypeScript type stripping, printing `ok` / `fail` line by line. The script currently prints 256 passing checks.

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
| `index.html` | page structure (Canvas view + minimap panel + HUD + warehouse panel + hover tooltip + protocol panel + toast stack + log overlay), the greyscale translucent chrome, no inline scripts; the module entry is `src/main.ts` |
| `src/protocol.ts` | OC/1 commands, base64 helpers, sealing/opening the AES-256-GCM envelope |
| `src/socket.ts` | WebSocket queue of one line command to one reply, timeouts, and close events |
| `src/commands.ts` | pure gameplay builders (`WHOAMI`, `ORDER`, `BUILD`, `TRAIN`), `parseWhoAmI`, and the catalogue the gameplay panel offers (including `building.lumbercamp.t1` and `building.warehouse`) |
| `src/controlGroups.ts` | pure control-group model: `assign`, `add`, `recall`, `prune` and `describe`, immutable 1..9 bindings with de-duplicated positive ids, and the empty-group status text |
| `src/heartbeat.ts` | `HEARTBEAT_MS` (20 s), the pure `shouldHeartbeat(state)` start/stop rule, and the heartbeat's documentation |
| `src/toast.ts` | pure toast rules: 4 s / 8 s lifetimes, `ERR`/refusal tone, the five-toast cap, and one-line trimming |
| `src/toastview.ts` | DOM half of the toast stack: one exchange element with `→`/`←` lines, per-toast timers, newest on top, oldest dropped |
| `src/main.ts` | DOM wiring: connect, handshake, automatic heartbeat, buttons, exchange toasts/history overlay, `engine-info`, `WHOAMI`/gameplay panel, display-name labels, multi-selection and group orders, control groups (Ctrl/Shift/number, status line, HUD group prefix, per-snapshot pruning), the resource-rate suffix and the per-item queue list, the double-click/deep selection panel refresh, the `工兵`/`O` engineer selection, the hover tooltip DOM and its measurement, the read-only warehouse panel, snapshot-diff event toasts, starting the view/HUD, the camera-driven `/world/snapshot` streamer (signed centres, 8-tile edge trigger, request ids, old window kept until the new one arrives, camera never rebuilt for a window), the animation loop that interpolates between the last two frames of the same window, the build-placement ghost (hover reason in the status line and the "only a legal site sends BUILD" click gate) and the minimap render plus its click/drag camera jump |
| `src/render/camera.ts` | world/screen coordinates, visible tile culling, panning, cursor-centered zoom, floor-based camera focus tile, and the ±500,000-tile numeric safety bound for the unbounded world |
| `src/render/atlas.ts` | plugin manifest parsing (frames and display names), composite atlas (last wins), label helpers with raw-id fallback, and image loading; returns `null` on failure |
| `src/render/fallback.ts` | terrain color blocks, unit/building shapes, grid lines, shadows, the selection ring, the thin ownership ring, the greyscale building construction bar and the `生产中 ×N` queue marker drawn when there is no atlas |
| `src/render/status.ts` | pure status chrome: percentage formatting, the clamped bar ratio, the `生产中 ×N` marker text, the `尺寸：W×H` size label, the warehouse stock label and shared item display names, the `(+N/分)` income suffix and the per-queue-item bar ratio / `i/n` label, all tolerating the optional snapshot fields |
| `src/render/selection.ts` | pure whole-world selection rules: the double-click same-kind rule (own and in-window only, the single-building case) and the all-own-engineers shortcut, with the engine kind test `unit.engineer` / `engineer` |
| `src/render/tooltip.ts` | pure hover-tooltip rules: display name plus owner tag, optional health percentage, building footprint and queue summary, and the window-edge clamp with its cursor gap |
| `src/render/events.ts` | pure snapshot diff events: building completion (`progress !== 1` → `1`), a 10-point health drop, and the five-second per-entity de-duplicator |
| `src/render/resources.ts` | pure read-only warehouse total: sum the `contents` of own buildings, label items, sort by display name, and count own warehouses |
| `src/render/interpolate.ts` | pure snapshot interpolation: `Frame`, `alphaFor` for one poll interval, and `interpolate` with the appear/disappear/snap rules |
| `src/render/marquee.ts` | pure marquee geometry: drag-corner normalisation and the canvas rectangle to signed world-tile range conversion, plus the Shift+click add/remove rule |
| `src/render/stream.ts` | pure window-streaming rules: signed edge distance, the 8-tile trigger, the `centre - radius` origin for any signed centre, the duplicate-origin guard, and the monotonic request tracker |
| `src/render/placement.ts` | pure build-placement ghost: footprint lookup with the unknown-kind 3x3 default, row-major footprint tiles, entity-footprint occupancy, the 16-tile reach approximation of the server's path rule, the blocked > out-of-reach > legal combination, the five status-line reason texts and the three ghost colours |
| `src/render/minimap.ts` | pure minimap mapping (aspect-fit layout, floor-based panel-to-world and world-to-panel conversion for signed origins, the clamped camera target) plus the `MinimapRenderer` that draws terrain pixels, 2x2 ownership dots and the white camera rectangle on its own canvas |
| `src/render/view.ts` | `WorldView` / `EntityView` / `PlayerView` / `QueueItemView` shapes (window origin and window size, no map size, optional footprint/progress/queue/queueItems/contents fields and player income rates), signed absolute-to-window terrain translation with floor semantics and the in-window entity rule, the ownership classification and colour rule, the under-construction test, own-only marquee containment, the deterministic `demoWorld(seed)` fallback, and the `/world/snapshot` windowed fetch/conversion |
| `src/render/renderer.ts` | drawing one Canvas 2D frame, DPR handling, y-sorting, animation frames, a gold selection ring for every selected entity, the marquee rectangle, the per-tile green/red/amber placement ghost, and the construction bar / production marker above buildings |
| `src/render/input.ts` | pointer/keyboard controls bound only to the canvas: click and Shift+click selection, double-click detection with the drag/marquee guard, hover entity picking for the tooltip, empty-ground marquee drag, entity drag selection, middle/Space camera pan, entity picking, right-click orders, placement clicks, the `O` engineer shortcut, and the Ctrl/Shift/plain 1..9 control-group key mapping |
| `public/textures/README.md` | plugin atlas contract (manifest format, examples, anchor) |
| `scripts/check-render-math.ts` | offline self-check of the camera (negative floor semantics and the ±500,000-tile safety bound), map determinism, atlas validation, display-name labels and raw-id fallback, the on-disk classic manifest, plugin atlas merging, unbounded signed snapshot parsing and negative-origin translation, the signed window-edge trigger / duplicate-origin guard / request ordering, snapshot `players` parsing including optional income rates, queueItems parsing, snapshot interpolation, ownership colours, the heartbeat rule, gameplay command builders, marquee selection (negative tiles, own-only containment, Shift toggle), control groups (assign/add/recall/prune/describe, invalid numbers, empty-group text), the toast lifetime/cap rules, the entity status rules (progress percentage, bar ratio, queue marker, size label, stock label, income suffix, queue-item ratio and `i/n`, under-construction test) and the optional snapshot fields, the lumber-camp / warehouse catalogue entries, the build-placement ghost (footprint lookup/default, tile order, occupancy, the 16/17-tile reach boundary, state precedence, reason texts and colours), the minimap mapping (layout fitting, both directions including negative origins, floor semantics, the clamped camera jump), the double-click same-kind rule (own only, in-window only, building case), the engineer selection rule, the tooltip text composition and clamping, the snapshot diff events (the 10% threshold and the five-second de-duplication), and the warehouse total aggregation |
| `vite.config.ts` | `/ws`, `/plugin-assets` and `/world` dev proxies, `dist` output directory |

## Related documents

- Online protocol (the upstream of this document): [`protocol/oc-protocol.md`](../protocol/oc-protocol.md)
- Repository overview and parallel toolchains: [`README.md`](../README.md)
- Server startup, port, and data directory: [`01-getting-started.md`](../projects/oc/oc-game-server/help/01-getting-started.md)
