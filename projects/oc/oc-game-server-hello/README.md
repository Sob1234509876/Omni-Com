English | [简体中文](README_zh_CN.md)

# Omni Com Hello World Plugin

`projects:oc:oc-game-server-hello` — the minimal example plugin `oc-hello`, and the default material set that
ships with it. `HelloPlugin` registers one read-only request, `hello`, which takes a plain UTF-8 name and
answers with one greeting; the same jar also carries `assets/manifest.json` and `assets/atlas.png`, a 128x128
Minecraft style terrain atlas built from 16x16 tiles, which the server publishes under `/plugin-assets`. One
package, one plugin, one descriptor, one request and one asset folder: this module is the template to copy for
a new plugin.

## Coordinates

| | |
| --- | --- |
| Gradle path | `:projects:oc:oc-game-server-hello` |
| Java module | `oc.projects.oc.game.server.hello` |
| Base package | `io.github.sob1234509876.oc.server.hello` |
| Group / version | `io.github.sob1234509876.oc` / `2.0a` |
| Build | `java-library`, Java 17 toolchain, JPMS |
| Dependencies | `oc-game-server` only, for the plugin API; no engine, no third-party library |
| Self check | `./gradlew --offline :projects:oc:oc-game-server-hello:selfcheck` (126 checks, including the texture assertions) |
| Request | `hello`, read only, requires `LOGIN` on the same connection |
| Assets | `assets/manifest.json` and a 128x128 `assets/atlas.png` |
| Help | [03 plugins](../oc-game-server/help/03-plugins.md) and [05 browser endpoint](../oc-game-server/help/05-browser-endpoint.md), in Chinese |

## Build, self check, run

```shell
./gradlew --offline :projects:oc:oc-game-server-hello:selfcheck
./gradlew --offline :projects:oc:oc-game-server-hello:jar
```

The self check prints one line at the end:

```text
hello plugin self check: all 126 checks passed.
```

126 is the sum of five sections: the plugin registers one request and answers it (including the blank payload,
the 64 byte truncation and `onDisable`), the descriptor inside the jar, the atlas manifest, the atlas image
itself, and a real WebSocket round trip. The texture assertions are the strict part: every one of the sixteen
tiles must be a 16x16 rectangle inside the 128x128 image, no two distinct tiles may share pixels, each tile
needs at least four distinct colours and no single colour may cover more than 80% of it, so replacing the art
with a flat block or a two tone gradient fails the check rather than the screen.

## Install

Copy `projects/oc/oc-game-server-hello/build/libs/oc-game-server-hello-2.0a.jar` into the server's `plugins/`.
That is the whole installation: this plugin needs no dependency jar, because the only API it uses is the
server's own plugin API, which the parent class loader already provides. Start the server, log in on either
transport, and the request is there:

```text
LOGIN alice secret
REQ hello <base64 of "Alice">    -> OK <base64 of the greeting>
```

## The `hello` request

| Fact | Value |
| --- | --- |
| Payload | a plain UTF-8 name, not an envelope; the transport already base64 encodes a `REQ` payload |
| Blank or whitespace only | `world` |
| Longer than 64 UTF-8 bytes | cut on a character boundary, then followed by `…` (`HelloPlugin.MAX_NAME_BYTES = 64`) |
| Answer | `Hello, <name>! This is the Omni Com plugin system talking: request "hello", answered by oc-hello 1.0.` |
| State it touches | none: no file, no field, no clock, no game object |

The handler is registered in `onLoad` and withdrawn in `onDisable`, so the very first client after startup can
reach it and a disabled plugin stops answering immediately. It reads its argument and returns a fresh byte
array; that is the entire remote surface.

## The default material set

The jar carries two resources under `assets/`:

| File | Contents |
| --- | --- |
| `manifest.json` | `image: atlas.png`, `tilePixels: 16`, 20 tile rectangles and an empty `entities` object |
| `atlas.png` | a 128x128 PNG, eight 16x16 cells per row |

The manifest names sixteen art tiles — grass, dirt, stone, cobblestone, sand, gravel, water, bedrock, planks,
log, leaves, snow, clay, coal_ore, iron_ore and mossy_cobblestone — plus the seven terrain content paths the
client resolves by name. Four of those are aliases onto an art tile: `forest` to `leaves`, `hill` to `dirt`,
`rock` to `stone` and `void` to `bedrock`. The image itself is the default material set for a world: a
Minecraft style terrain atlas with a five shade palette per tile, a transparent hole in the leaves and a
distinct colour range for water. The manifest carries no absolute URL; the server rewrites `atlasUrl` when it
merges the manifests.

The server merges every loaded plugin's `assets/manifest.json` into the array at `GET /plugin-assets/manifest.json`
(in plugin load order, a later plugin overriding an earlier one) and serves this image at
`/plugin-assets/oc-hello/atlas.png`. Both are read through the plugin's own class loader, never from the plugin
directory on disk, so the browser sees the same bytes the jar contains.

## Copy it for a new plugin

This module is the smallest complete plugin, so it is the template: copy it and change five things.

1. Rename the module and the package in `src/main/java/module-info.java`; keep `requires oc.projects.oc.game.server`
   and the `exports` of your plugin package, because the loader instantiates the plugin reflectively.
2. Keep one `Plugin` implementation and change `NAME`, `VERSION`, `REQUEST_NAME` and `description()` in it.
3. Replace `src/main/resources/omnicom-plugin.properties`: `id`, `version`, `main` (the binary class name) and
   `description`.
4. Keep `assets/manifest.json` and `assets/atlas.png` if you need textures under `/plugin-assets`, or delete
   them if you do not; the plugin works either way.
5. Build the jar with `:projects:oc:oc-game-server-hello:jar` and drop it into `plugins/`. Add the jars of any
   library you use to the same folder; that folder is the dependency mechanism.

Keep the handler read-only unless you have a reason not to: the whole plugin API is a folder, a version string,
a logger and the request registry, and that is what makes an example this small possible.

## Module layout

| Path | Contents |
| --- | --- |
| `src/main/java/.../hello/HelloPlugin.java` | the plugin: one request, one greeting, no state |
| `src/main/java/module-info.java` | requires `oc.projects.oc.game.server` and exports the plugin package |
| `src/main/resources/omnicom-plugin.properties` | the descriptor: `id=oc-hello`, `version=1.0`, `main`, `description` |
| `src/main/resources/assets/manifest.json` | the atlas manifest the server merges |
| `src/main/resources/assets/atlas.png` | the 128x128 default terrain atlas |
| `src/selfcheck/java/.../hello/check/HelloPluginCheck.java` | the 126 check self check program |
| `build.gradle` | the `selfcheck` task and the server self check output on its class path |

## Related

- [`../oc-game-server/help/03-plugins.md`](../oc-game-server/help/03-plugins.md) — lifecycle, discovery order,
  the dependency rule and the request seam, in Chinese.
- [`../oc-game-server/help/05-browser-endpoint.md`](../oc-game-server/help/05-browser-endpoint.md) — `/ws`, the
  `/plugin-assets` route and the WebSocket frame rules, in Chinese.
- [`../oc-game-server/README.md`](../oc-game-server/README.md) — the server this module is a plugin of.
- [`../oc-game-server-debug/README.md`](../oc-game-server-debug/README.md) — the other example plugin, the one
  that does have dependencies.
- [`web-client/README.md`](../../../web-client/README.md) — the browser client with the `hello` button.
