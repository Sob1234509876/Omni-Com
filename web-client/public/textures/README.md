English | [简体中文](README_zh_CN.md)

# Texture Atlas Contract (Plugin Assets)

**Textures are now provided by plugins, not by this directory.** On startup the client asks the server for
`GET /plugin-assets/manifest.json`, then loads each image according to the `atlasUrl` in the manifest; the local
`public/textures/atlas.json` is no longer read. This directory keeps only this document, as the format description for plugin manifests.

## Where the textures live

Pack the two files into the **plugin jar**'s `assets/` directory:

| Path (inside the jar) | Purpose |
| --- | --- |
| `assets/manifest.json` | the atlas manifest, in the format described below |
| `assets/atlas.png` | the image the manifest's `image` points to, in the same directory as the manifest |

For plugins built with Gradle, just put them in `src/main/resources/assets/`; the `jar` task packages them automatically. For example, the example plugin:

```text
projects/oc/oc-game-server-hello/src/main/resources/assets/manifest.json
projects/oc/oc-game-server-hello/src/main/resources/assets/atlas.png
```

Put the built jar into the server's `plugins/`, restart the server, and the browser client automatically uses it — no need to rebuild
`web-client`, and no need to put any file into `public/`. Loose files sitting in `plugins/<plugin>/assets/` are **not**
served: the route only reads resources inside the jar through the plugin's own class loader.

## What the server publishes

The server merges every loaded plugin that has an `assets/manifest.json` into one array, in plugin load order (jar file name order):

```json
[
  {
    "plugin": "oc-hello",
    "atlasUrl": "/plugin-assets/oc-hello/atlas.png",
    "tilePixels": 32,
    "tiles": { "grass": [64, 0, 32, 32] },
    "entities": { "worker": { "frames": [[0, 32, 32, 48]], "fps": 4 } }
  }
]
```

- `plugin` is the plugin's `Plugin#name()`, usually the same as the `id` in the plugin descriptor; it is also the plugin id in the URL.
- `atlasUrl` is rewritten by the server from the manifest's `image`; **the manifest itself must not contain absolute URLs**.
- When no plugin publishes a manifest, this route returns `200` and `[]`; the client then uses procedural drawing.
- All `/plugin-assets` responses are `Cache-Control: no-cache`, so after replacing a plugin just refresh the page to see the new images.
- The client merges in array order, **later plugins overriding earlier ones** (same-named terrain path or same-named entity kind). The array order is
  the order the server returns; the client does not re-sort.

The raw files are served by `GET /plugin-assets/<plugin id>/<file>`, for example
`/plugin-assets/oc-hello/atlas.png`. This route only allows `GET`/`HEAD`, needs no login, and is not part of the OC/1 protocol;
the details are in [`protocol/oc-protocol.md`](../../../protocol/oc-protocol.md).

## Basic conventions

- **Any atlas size works**, for example 256×256, 512×512, 1024×1024.
- A `tilePixels` of **32** is recommended: terrain frames drawn at 32×32 are the easiest to align. The client stretches terrain frames to one cell on screen, so 16, 32, and 64 work too.
- All frame coordinates are **pixels** inside the atlas image, with the origin at the top-left, x increasing to the right and y increasing downwards.
- Terrain frames may be any rectangle; one rectangle per cell is recommended.
- **Each entity kind occupies one row of animation frames**, played left to right. Frame rectangles need not be the same width and height, but keeping them consistent within one row is the least trouble.
- The `anchor` in an entity frame is the **foot anchor**: `[0.5, 0.75]` means the point at 50% of the frame width and 75% of the frame height sits on the ground position. Units/buildings grow upwards from the anchor, so frames may be taller than one cell.

## Manifest structure

`assets/manifest.json` is the old `atlas.json` format, with the meaning of exactly one field taken over by the server: `image` must be a
**file name** inside this plugin's `assets/` directory (it may not contain `/`, `..`, a drive letter, or an absolute URL).

```text
{
  "image": "atlas.png",          // the image file name inside this plugin's assets/
  "tilePixels": 32,              // recommended art cell size, metadata only
  "tiles": {
    "<TerrainType name>": [x, y, w, h]  // legacy form, no display name
    // named form instead: "<TerrainType name>": { "rect": [x, y, w, h], "name": "草地" }
  },
  "entities": {
    "<entity kind>": {
      "frames": [[x, y, w, h], ...],  // at least one frame, in play order
      "fps": 4,                       // optional, default 4
      "anchor": [0.5, 0.75],          // optional, default [0.5, 0.75]
      "scale": 1,                     // optional, default 1, stacked on top of the camera zoom
      "name": "普通"                  // optional display name
    }
  }
}
```

Field reference:

| Field | Required | Description |
| --- | --- | --- |
| `image` | Yes | image file name inside this plugin's `assets/`; usually `atlas.png` |
| `tilePixels` | No | suggested art cell size in pixels; treated as 32 when missing or invalid |
| `tiles` | No | terrain frame table; when missing, all terrain uses procedural drawing |
| `tiles.<name>` | — | the engine's terrain content path, see the table below; value is `[x, y, w, h]` or `{ "rect": [x, y, w, h], "name": "..." }` |
| `entities` | No | entity animation table; when missing, entities use procedural shapes |
| `entities.<kind>.frames` | Yes | at least one `[x, y, w, h]` |
| `entities.<kind>.fps` | No | positive number, default 4 |
| `entities.<kind>.anchor` | No | two numbers, default `[0.5, 0.75]` (feet) |
| `entities.<kind>.scale` | No | positive number, default 1; multiplied with the camera zoom |
| `entities.<kind>.name` | No | display name; a missing or blank name falls back to the raw kind |

## Display names

Both tables may carry a human readable **display name**: `entities.<kind>.name` for an entity kind, and the `name` member of the
named tile object for a terrain path. The client merges names with the frames under the same **last-wins** rule, and uses them in
the HUD selection line, the selected-building line, the TRAIN/BUILD button labels, and the success log lines (for example
`已下训练 普通 (unit.melee.t1)`). The protocol commands keep sending the raw content path.

Names are optional. A manifest written before names existed, a missing `name`, and a blank `name` all fall back to the raw id, so
the page never shows `undefined`. `check-render-math` proves the label map, the fallback, and that the classic plugin's on-disk
manifest names every tile and entity kind.

The server does not interpret `tiles` and `entities`; it only forwards them to the client. Frame validity is checked by the client as described under "Validation and failure behavior" below.

## Complete example

The JSON below corresponds to a 256×320 `atlas.png`. The terrain frames occupy the top row, each entity kind gets its own row, and buildings use a single frame.
Write this JSON as `assets/manifest.json` and the image as `assets/atlas.png`, pack them into the plugin jar, and it is ready to publish.

```json
{
  "image": "atlas.png",
  "tilePixels": 32,
  "tiles": {
    "water": [0, 0, 32, 32],
    "sand": [32, 0, 32, 32],
    "grass": [64, 0, 32, 32],
    "forest": [96, 0, 32, 32],
    "hill": [128, 0, 32, 32],
    "rock": [160, 0, 32, 32],
    "void": [192, 0, 32, 32]
  },
  "entities": {
    "worker": {
      "frames": [
        [0, 32, 32, 48],
        [32, 32, 32, 48],
        [64, 32, 32, 48],
        [96, 32, 32, 48]
      ],
      "fps": 4,
      "anchor": [0.5, 0.75],
      "scale": 1
    },
    "soldier": {
      "frames": [
        [0, 80, 32, 48],
        [32, 80, 32, 48],
        [64, 80, 32, 48],
        [96, 80, 32, 48]
      ],
      "fps": 6,
      "anchor": [0.5, 0.75],
      "scale": 1
    },
    "medic": {
      "frames": [
        [0, 128, 32, 48],
        [32, 128, 32, 48],
        [64, 128, 32, 48],
        [96, 128, 32, 48]
      ],
      "fps": 4,
      "anchor": [0.5, 0.75],
      "scale": 1
    },
    "warehouse": {
      "frames": [[0, 176, 96, 72]],
      "fps": 1,
      "anchor": [0.5, 0.75],
      "scale": 1
    },
    "barracks": {
      "frames": [[96, 176, 96, 72]],
      "fps": 1,
      "anchor": [0.5, 0.75],
      "scale": 1
    }
  }
}
```

## Terrain names

The keys of `tiles` are the engine's `TerrainType.key().path()`. The current self-check scene defines:

| Key | Description |
| --- | --- |
| `water` | water |
| `sand` | sand / coast |
| `grass` | grassland |
| `forest` | forest |
| `hill` | hills |
| `rock` | rock / impassable |
| `void` | off-map / engine fallback terrain |

Procedural drawing also recognizes these aliases: `deep_water`, `shallow_water`, `grassland`, `meadow`, `trees`, `hills`, `mountain`, `rocky`, `stone`, `desert`, `dirt`, `soil`, `snow`, `ice`, `road`, `bridge`. These keys also work in an atlas; unknown keys get a procedurally generated color.

## Entity kind

The keys of `entities` must match `EntityView.kind`, i.e. the entity's content path. The self-check scene and the placeholder map use:

| kind | Type |
| --- | --- |
| `worker` | worker (unit) |
| `soldier` | soldier (unit) |
| `medic` | medic (unit) |
| `warehouse` | warehouse (3×3 building) |
| `barracks` | barracks (3×3 building) |

Namespaced paths (for example `oc:soldier`) also match `soldier`. Entities without a matching entry use procedural shapes.

## Several plugins providing atlases at once

- The client loads in the order of the server's array, and **the last plugin that defines a given terrain path or entity kind wins**; names it does not define keep coming from earlier plugins. Different plugins can each draw part of the terrain, or one can override the whole atlas.
- Every winning frame is recorded together with the image of the plugin it came from, and the renderer never draws plugin A's coordinates onto plugin B's image. So several plugins can each maintain their own atlas without stitching one big image.
- The HUD's source label shows `atlas · <plugin id>` (all ids are listed when there are at most three plugins) or `atlas · N plugins`; when it falls back to procedural drawing it shows `procedural`.

## Validation and failure behavior

- The server requires the manifest to be a JSON object and `image` to be a non-empty, safe file name; `tiles` / `entities` must be objects whenever they appear. Non-conforming manifests are skipped with one line of reason in the server log, and other plugins are unaffected.
- The client requires every entry to have a same-origin `atlasUrl`; entries that lack one, are not this server's path, or are not objects are **skipped with a warning**, and the remaining entries load as usual.
- The top level must be a JSON object; every frame must be an array of exactly four finite numbers, and `w` and `h` must be greater than 0.
- Frames must lie entirely inside the image. Out-of-bounds frames are **silently dropped** and reported in one summarized `console.warn` in the console; other valid frames of the same entity keep being used.
- Entity entries with no valid frame are dropped; an entry with no usable terrain frame and no usable entity frame counts as unusable, and only that plugin is skipped.
- A tile may be the legacy `[x, y, w, h]` array or the named `{ "rect": [x, y, w, h], "name": "..." }` object; a malformed tile or a missing `rect` is skipped with a warning. A non-string, empty, or whitespace-only `name` is treated as "no name" and falls back to the raw path.
- An image load failure only skips that plugin; invalid `fps`, `anchor`, and `scale` values are handled with the defaults, with a warning.
- When the manifest request fails, returns `[]`, or all entries are unusable, the client falls back to procedural drawing; this never blocks page load and never affects the protocol panel.

## Animation and selection

- The client loops that kind's `frames` at `fps` frames per second. Different entities get a slight phase offset by id, so units across the screen do not step in unison.
- Future snapshots may provide a frame index sequence in `EntityView.frames` (for example `[2, 3, 4, 5]`) to select a "walking" segment within the same atlas row; when absent, the whole row is played.
- The selection ring, shadows, and health bars are drawn by the client and do not need to be painted into `atlas.png`.
