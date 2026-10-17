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
`/plugin-assets` to the Java server, so during development the textures likewise come from plugins rather than from `public/`.

After connecting, the page sends `HELLO 1` first and enables the command buttons only once it receives `OK OC/1`. Then:

1. Fill in a username and password, click `REGISTER` to create an account (registering does not log in), then click `LOGIN`; only after that is `REQ` available.
2. Paste the contents of the server's `data/oc-debug/debug.key` into the `debug.key` text box.
3. Click `engine-info`: the page seals an envelope with `INFO json`, sends `REQ engine-info <base64>`, then opens the reply and formats the JSON.
4. Type a name into the `hello` name box (default `world`) and click `hello`: the page sends `REQ hello <base64(UTF-8 name)>`,
   decodes the reply's base64 into UTF-8 text, and shows it in the log. This requires installing the `oc-hello` plugin described in the next section first.
5. `PING`, `COUNT`, and `QUIT` work as the protocol table says.

Creating an account takes only two steps: click `REGISTER` once (the username is 3-32 characters and may use only letters, digits, `_`, `.`, and `-`; the password is at least 6 characters; the server validates both rules), then click `LOGIN` to sign in; a successful registration does **not** log in automatically. A reply of `OK …` from the server means the account was created; a duplicate name gets `ERR …`, and the page shows the message the server gave verbatim in the status line and the log.

OC/1 uses text frames only. If the server sends a binary frame, the page just logs one line, "the server sent a binary frame, ignored (the protocol uses text frames only)",
**without closing the connection**, and it does not drop commands still waiting for a reply; this matches the server's behavior of replying `ERR binary frames are not commands` and keeping the connection.

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

The HUD is pinned to the top-left corner of the view and shows, in real time:

- Texture source: `atlas · oc-hello` (plugin ids are listed when there are at most three plugins), `atlas · 4 plugins`; `procedural` when it has fallen back to procedural drawing;
- the tile coordinates under the mouse;
- the zoom factor (0.5× to 2.5×);
- the smoothed FPS;
- the id, kind, and ground coordinates of the selected entity; `Selected: none` when nothing is selected.

Controls (they take effect after the first click on the view; keyboard events are bound only to the canvas and never steal keystrokes from the protocol input fields):

| Action | Effect |
| --- | --- |
| Left click | selects the entity under the cursor; clicking empty ground clears the selection |
| Left drag | pans the camera (movements under 4 pixels count as a click) |
| Wheel | zooms around the cursor, keeping the tile under the cursor fixed |
| `WASD` / arrow keys | pan the camera |
| `+` / `-` | zoom in / out around the center of the view |
| `F` | resets the camera (centered, 1×) |
| `Esc` | clears the selection |

**Textures are provided by plugins**: each plugin packs `assets/manifest.json` and images into its own jar, the server merges them into
`GET /plugin-assets/manifest.json`, and the client reads that on startup and loads each image by its `atlasUrl`. When several plugins provide textures at once they are merged in array order,
**the later one overriding the earlier one** (same-named terrain path or entity kind). The local `public/textures/atlas.json` is no longer a source; this directory keeps only the
[contract document](public/textures/README.md) (manifest format, examples, foot anchor, one row of animation per entity).
When the manifest is missing, the request fails, an image fails to load, or the response is `[]`, the client automatically falls back to procedural drawing: every terrain type gets its own color and detail,
units are circles, buildings are blocks with a roof line, and at higher zoom there is a 1-pixel grid.

> The current map is generated by `demoWorld(seed)` and is only a deterministic placeholder island; once the Vanilla plugin pushes real snapshots through the same
> `WorldView` shape, the render layer will not need to change.

The rendering math (camera clamping, cursor-centered zoom, placeholder map determinism, rejection of out-of-bounds atlas frames, plugin atlas merging and fallback) can be checked offline:

```shell
pnpm check:render
```

It type-checks first, then runs [`scripts/check-render-math.ts`](scripts/check-render-math.ts) directly with Node 24's TypeScript type stripping, printing `ok` / `fail` line by line.

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

This jar also carries `assets/manifest.json` and a 256×80 `assets/atlas.png`: with the jar in `plugins/` and the server started, the
browser view automatically renders with its six terrains and two-frame worker animation — this step demonstrates the whole chain from a plugin resource in the jar to the server route to the screen.
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

## Read-only statement

**This page cannot change game state.** It offers only `PING`, `COUNT`, `REGISTER`, `LOGIN`, `REQ engine-info`,
`REQ hello`, and `QUIT`: apart from `REGISTER` creating a new account on the server, no command modifies the game, the server configuration, or plugin state.

- `engine-info` is a read-only report registered by `oc-debug`; the handler holds no writable reference, and the report is assembled from constants and temporary objects.
- `hello` is a read-only greeting registered by `oc-hello`; the handler only reads the name from the request and holds no engine, configuration, or network reference.
- `REGISTER` only creates a new account (a duplicate name gets `ERR …`); it does not log in and does not change an existing account's password or permissions.
- `LOGIN` only marks the identity on this connection and refreshes the last-login time recorded by the server; it changes no password, changes no permissions, and there is no password-change entry point.

## Files

| File | Purpose |
| --- | --- |
| `index.html` | page structure (Canvas view + HUD + protocol panel), no inline scripts; the module entry is `src/main.ts` |
| `src/protocol.ts` | OC/1 commands, base64 helpers, sealing/opening the AES-256-GCM envelope |
| `src/socket.ts` | WebSocket queue of one line command to one reply, timeouts, and close events |
| `src/main.ts` | DOM wiring: connect, handshake, buttons, log, `engine-info`, and starting the view/HUD |
| `src/render/camera.ts` | world/screen coordinates, visible tile culling, panning, cursor-centered zoom, and clamping |
| `src/render/atlas.ts` | plugin manifest parsing, composite atlas (last wins), and image loading; returns `null` on failure |
| `src/render/fallback.ts` | terrain color blocks, unit/building shapes, grid lines, shadows, and selection ring when there is no atlas |
| `src/render/view.ts` | `WorldView` / `EntityView` shapes and the deterministic `demoWorld(seed)` placeholder map |
| `src/render/renderer.ts` | drawing one Canvas 2D frame, DPR handling, y-sorting, and animation frames |
| `src/render/input.ts` | pointer/keyboard controls bound only to the canvas, and entity picking |
| `public/textures/README.md` | plugin atlas contract (manifest format, examples, anchor) |
| `scripts/check-render-math.ts` | offline self-check of the camera, map determinism, atlas validation, and plugin atlas merging |
| `vite.config.ts` | `/ws` and `/plugin-assets` dev proxies, `dist` output directory |

## Related documents

- Online protocol (the upstream of this document): [`protocol/oc-protocol.md`](../protocol/oc-protocol.md)
- Repository overview and parallel toolchains: [`README.md`](../README.md)
- Server startup, port, and data directory: [`01-getting-started.md`](../projects/oc/oc-game-server/help/01-getting-started.md)
