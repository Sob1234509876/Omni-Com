English | [简体中文](README_zh_CN.md)

# plugins

This is the **operator drop zone** for the Omni Com server's plugins.

- Put plugin jars directly at this level; the jars that plugins depend on go here too. The server's `PluginLoader` puts every jar
  in the directory on each plugin's search path, so no repository, POM, or download is needed, and copying the whole directory to an offline machine works.
- Every plugin jar should carry `omnicom-plugin.properties` at its root, with at least the `id`, `version`, and `main` keys.
  A jar without this file is treated as a dependency library and raises no error.
- Plugin runtime state is **not** here: the server puts each plugin's data directory under its own `data/<plugin name>/`, and the server's own
  state is also carried by `data/`. That keeps `plugins/` a drop zone you can delete, replace, or back up wholesale, rather than the one folder
  on a machine you dare not clean up.
- `*.jar` in this directory is already ignored by `.gitignore`; do not commit plugins or keys to the repository.
- Examples: the `oc-game-server-hello-2.0a.jar` built by `oc-game-server-hello` placed here provides the `hello`
  request; `oc-debug` placed here provides `engine-info`, and its key lives at the server's `data/oc-debug/debug.key`, not in
  this directory.

By default the server reads plugins from `plugins/` at the repository root (see the startup arguments and help of `oc-game-server`).

## How plugins carry texture assets

Plugins can put art assets directly into their own jar, under the `assets/` directory:

| Path (inside the jar) | Purpose |
| --- | --- |
| `assets/manifest.json` | the atlas manifest; for the format see [`web-client/public/textures/README.md`](../web-client/public/textures/README.md) |
| `assets/atlas.png` | the image the manifest's `image` points to, in the same directory as the manifest |

Gradle-built plugins write them to `src/main/resources/assets/`, and the `jar` task packages them automatically. Once the server is started:

- `GET /plugin-assets/manifest.json` returns the merged array of all loaded plugins that **have a manifest**, in plugin load order
  (jar file name order); when no plugin publishes a manifest it returns `200` and `[]`.
- `GET /plugin-assets/<plugin id>/<file name>` returns the raw bytes of `assets/<file name>` inside that plugin's jar, with the MIME type given by the extension.
- **The plugin id is `Plugin#name()`** (the example plugin is `oc-hello`), usually the same as the `id` in the descriptor, and it is also the name of the plugin's data directory.
- Resources are read only through the plugin's own `URLClassLoader.getResourceAsStream("assets/" + file name)`, and the server **never** reads the
  `plugins/` directory on disk. So loose `assets/` folders in the plugin directory are not served; they must be packed into the jar.
- The manifest's `image` can only be a file name inside `assets/`, with no path, `..`, or absolute URL; `atlasUrl` is rewritten by the server.
- This route only allows `GET`/`HEAD`, needs no login, and has nothing to do with the OC/1 protocol; it shares the port of `--http-port` with the static page,
  and with `--http-port 0` neither exists.
- Responses are always `Cache-Control: no-cache`, so after replacing a plugin jar just restart the server and refresh the page to see the new images.
- Plugins without `assets/manifest.json` contribute no manifest entry; plugins without `assets/` return 404 on the file route across the board.
- One broken plugin manifest affects only itself: the server logs one line of reason and skips it, while other plugins publish as usual and the client loads as usual.

Example: `oc-game-server-hello`'s `src/main/resources/assets/` already ships `manifest.json` and a 256×80
`atlas.png` mapping six terrain types and a two-frame `worker`. Build the jar, put it in this directory, and the browser client renders terrain and the worker animation with that atlas.
