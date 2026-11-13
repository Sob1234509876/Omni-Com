/**
 * The per-material texture contract, published by plugins.
 *
 * Textures are no longer a folder the page ships, and they no longer share one packed atlas page. Every
 * loaded plugin may put `assets/manifest.json` inside its own jar listing **one image file per material**
 * (raster PNG or vector SVG): one single-frame file per terrain tile (`tilePixels × tilePixels`) and one
 * vertical frame strip per entity kind (its height is `frames` times the frame height). The server merges
 * those manifests into `GET /plugin-assets/manifest.json` - a JSON array of
 * `{ plugin, baseUrl, tilePixels, tiles, entities }` - and serves every file under the entry's `baseUrl`
 * (`/plugin-assets/<plugin>/...`). The client fetches that array on start, downloads each referenced
 * image exactly once, and turns every file into its own
 * `AtlasSource`: a tile's rect is the whole image, an entity's frames are equal vertical slices of it.
 * Nothing else changes for the renderer - every tile and entity still resolves to a `SpriteRect` plus the
 * image it must be drawn from, so a frame can mix art from several plugins without any of them sharing a
 * texture page.
 *
 * The merge rule is **last wins**: entries are applied in the order the server sent them, which is plugin
 * load order, so a later plugin overrides an earlier one for the same tile path or entity kind. Every tile
 * and every entity keeps a reference to the file it came from, so the renderer always draws from the
 * plugin that won that name.
 *
 * One file serves as a tile OR as an entity material, never both. Within one manifest entry the
 * `entities.*.image` paths are collected first; a `tiles.*.image` that duplicates one is dropped with a
 * warning, so the violating tile falls back to procedural drawing instead of an entity frame strip being
 * stretched over the terrain. For the same reason an `entities.*.image` that duplicates another entity's
 * image in the same manifest is refused: the earlier entry keeps the file and the later entry is dropped.
 *
 * The document is parsed defensively: a malformed entry, a file that does not decode, a server that
 * publishes nothing or a failed fetch all resolve to "no atlas" and the procedural renderer takes over. A
 * broken plugin must never stop the page: an entry whose schema fails is skipped whole, while a single
 * file that fails to load drops exactly the tiles and entities that reference it and the rest of the
 * plugin still loads.
 *
 * The parsing and merging half is free of DOM calls so `scripts/check-render-math.ts` can exercise it
 * directly; only `loadPluginAtlas` touches `fetch`, `Image` and `document`.
 */

export interface SpriteRect {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

/** One terrain frame plus the optional human readable label from the manifest. */
export interface TileFrame {
  readonly rect: SpriteRect;
  /** Display name written in the manifest; empty when the entry has none. */
  readonly name: string;
}

export interface EntitySprite {
  /** Animation frames as vertical slices of the entity's own strip file. */
  readonly frames: readonly SpriteRect[];
  /** Frames per second; defaults to 4. */
  readonly fps: number;
  /** Point inside the sprite that sits on the entity's feet; defaults to [0.5, 0.75]. */
  readonly anchor: readonly [number, number];
  /** Extra multiplier on top of the camera zoom; defaults to 1. */
  readonly scale: number;
  /** Display name written in the manifest; empty when the entry has none. */
  readonly name: string;
}

/** One tile parsed out of a manifest: the file it lives in plus its label. */
export interface TileEntry {
  /** Image path relative to the entry's `baseUrl`, for example `tiles/grass.svg`. */
  readonly image: string;
  /** Display name written in the manifest; empty when the entry has none. */
  readonly name: string;
}

/** One entity parsed out of a manifest, before its strip file is sliced into frames. */
export interface EntityEntry {
  /** Image path relative to the entry's `baseUrl`; the file's height is `frames` frame heights. */
  readonly image: string;
  /** How many vertical slices the strip file holds; 1..16. */
  readonly frames: number;
  /** Frames per second; defaults to 4. */
  readonly fps: number;
  /** Point inside the sprite that sits on the entity's feet; defaults to [0.5, 0.75]. */
  readonly anchor: readonly [number, number];
  /** Extra multiplier on top of the camera zoom; defaults to 1. */
  readonly scale: number;
  /** Display name written in the manifest; empty when the entry has none. */
  readonly name: string;
}

/** Everything parsed out of one manifest entry, before its files are downloaded. */
export interface AtlasData {
  /** The plugin that published the entry. */
  readonly plugin: string;
  /** The same-origin base path every image path resolves against, always ending in `/`. */
  readonly baseUrl: string;
  /** Recommended source tile size in pixels (metadata; tiles are stretched to the camera). */
  readonly tilePixels: number;
  readonly tiles: ReadonlyMap<string, TileEntry>;
  readonly entities: ReadonlyMap<string, EntityEntry>;
  /**
   * Optional item definitions published by the plugin: item content path to display name.
   *
   * Items are not sprite frames; they name resources the plugin defines (before this field existed the
   * client only knew `gold` / `wood`). The resource page uses the keys as the manifest half of its
   * "every resource that exists" list. An item may omit its name, in which case the map value is `""`
   * and the caller falls back to the path.
   */
  readonly items: ReadonlyMap<string, string>;
}

/** One material file, decoded, with the tiles and entities it hosts. */
export interface AtlasSource {
  /** The image file this source draws from (PNG or SVG); one file per material, never a shared page. */
  readonly image: HTMLImageElement;
  /** The plugin's recommended tile size, used to normalise art scale to the camera. */
  readonly tilePixels: number;
  /** The plugin that published the file. */
  readonly plugin: string;
  /** The tiles whose file this is, keyed by tile path. */
  readonly tiles: ReadonlyMap<string, TileFrame>;
  /** The entities whose strip file this is, keyed by entity kind. */
  readonly entities: ReadonlyMap<string, EntitySprite>;
  /** The publishing plugin's item definitions; shared by every file of the plugin. */
  readonly items: ReadonlyMap<string, string>;
}

/** A tile frame together with the plugin file it must be drawn from. */
export interface AtlasTile {
  readonly rect: SpriteRect;
  /** Display name from the winning entry; empty when that entry had none. */
  readonly name: string;
  readonly source: AtlasSource;
}

/** An entity animation together with the plugin file it must be drawn from. */
export interface AtlasEntity {
  readonly sprite: EntitySprite;
  readonly source: AtlasSource;
}

/** Human readable labels for every tile path and entity kind an atlas defines. */
export interface AtlasNames {
  /** Tile path to its display name; a nameless entry maps to its own path. */
  readonly tiles: ReadonlyMap<string, string>;
  /** Entity kind to its display name; a nameless entry maps to its own kind. */
  readonly entities: ReadonlyMap<string, string>;
  /** Item path to its display name; a nameless definition maps to its own path. */
  readonly items: ReadonlyMap<string, string>;
}

/** The composite of every plugin atlas: last wins per tile path and entity kind. */
export interface Atlas {
  readonly tiles: ReadonlyMap<string, AtlasTile>;
  readonly entities: ReadonlyMap<string, AtlasEntity>;
  /** Display labels, already resolved against the winning entries, with the id as fallback. */
  readonly names: AtlasNames;
  /** The material files that contributed, in the order the server sent them. */
  readonly sources: readonly AtlasSource[];
}

export type AtlasParseResult =
  | {
      readonly ok: true;
      readonly data: AtlasData;
      /** Every image path the entry needs, relative to `baseUrl`, unique, in first-seen order. */
      readonly images: readonly string[];
      readonly warnings: readonly string[];
    }
  | { readonly ok: false; readonly reason: string; readonly warnings: readonly string[] };

/** One entry of the server's manifest array, before its files are loaded. */
export interface ManifestEntry {
  readonly plugin: string;
  readonly baseUrl: string;
}

/** The result of reading one manifest array entry; a refusal explains what was wrong with it. */
export type ManifestEntryResult =
  | { readonly ok: true; readonly entry: ManifestEntry }
  | { readonly ok: false; readonly warning: string };

/**
 * The pure result of applying every source in order.
 *
 * The maps hold the index of the source that won each name, which is what lets the composite resolve a
 * name to its winning file without holding two copies of the frame data.
 */
export interface MergedLayers {
  /** Tile path to the index of the source that owns it; the last source that defines it wins. */
  readonly tiles: ReadonlyMap<string, number>;
  /** Entity kind to the index of the source that owns it; the last source that defines it wins. */
  readonly entities: ReadonlyMap<string, number>;
  /** Item path to the index of the source that owns it; the last source that defines it wins. */
  readonly items: ReadonlyMap<string, number>;
  /** The contributing plugin ids, in source order, without duplicates or blank ids. */
  readonly plugins: readonly string[];
}

/** The merged manifest path; the Vite dev proxy forwards this prefix to the Java server. */
export const PLUGIN_MANIFEST_URL = "/plugin-assets/manifest.json";

const DEFAULT_TILE_PIXELS = 32;
const DEFAULT_FPS = 4;
const DEFAULT_ANCHOR: readonly [number, number] = [0.5, 0.75];
const DEFAULT_SCALE = 1;
const MAX_FRAMES = 16;
const MAX_IMAGE_PATH_LENGTH = 255;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

/** Reads an optional display name; a missing, blank or non-string value means "no name". */
function readName(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

/**
 * Validates one manifest image path.
 *
 * Paths are relative to the entry's `baseUrl` and are joined onto it verbatim, so a path that escapes
 * the plugin directory (`..`), one that is absolute (`/`) or protocol relative (`//`), a backslash
 * (Windows separator: the server only ever publishes `/`), a control character or an over-long path is
 * refused. `null` with a warning means "drop the entry that named it".
 */
function readImagePath(value: unknown, label: string, warnings: string[]): string | null {
  if (typeof value !== "string") {
    warnings.push(`${label}.image 必须是字符串`);
    return null;
  }
  const path = value.trim();
  if (path === "") {
    warnings.push(`${label}.image 不能为空`);
    return null;
  }
  if (path.length > MAX_IMAGE_PATH_LENGTH) {
    warnings.push(`${label}.image 超过 ${MAX_IMAGE_PATH_LENGTH} 个字符`);
    return null;
  }
  if (path.startsWith("/") || path.includes("\\") || /[\u0000-\u001f\u007f]/.test(path)) {
    warnings.push(`${label}.image 必须是不带盘符、反斜杠和控制字符的相对路径，当前是 '${path}'`);
    return null;
  }
  const segments = path.split("/");
  if (segments.some((segment) => segment === "" || segment === "." || segment === "..")) {
    warnings.push(`${label}.image 不能包含空段、'.' 或 '..' 路径段，当前是 '${path}'`);
    return null;
  }
  return path;
}

/** The rect of one tile file: the whole image. */
export function tileRect(width: number, height: number): SpriteRect {
  return { x: 0, y: 0, width, height };
}

/**
 * The animation frames of one entity strip file: `frames` equal vertical slices.
 *
 * The file's height must divide evenly into the frame count; `null` means it does not (or a slice would
 * be empty) and the caller drops the entity instead of drawing torn frames.
 */
export function entityFrameRects(
  width: number,
  height: number,
  frames: number,
): SpriteRect[] | null {
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    return null;
  }
  if (!Number.isInteger(frames) || frames < 1) {
    return null;
  }
  const sliceHeight = height / frames;
  if (!Number.isInteger(sliceHeight) || sliceHeight <= 0) {
    return null;
  }
  return Array.from({ length: frames }, (_unused, index) => ({
    x: 0,
    y: index * sliceHeight,
    width,
    height: sliceHeight,
  }));
}

/**
 * Validates one parsed manifest entry against the per-material schema.
 *
 * Never throws. Malformed tiles and entities are collected in `warnings` and skipped; a manifest with no
 * usable tile, entity or item is a failure and is skipped whole. Entities are validated before tiles so
 * their image paths are known first: a tile whose image duplicates an entity's strip file is dropped, and
 * so is a later entity whose image duplicates an earlier one's — one file is one material. Pure schema
 * work: image dimensions are unknown here and are only needed when the files decode, because a tile's
 * rect is its whole file and an entity's frames are vertical slices of it.
 */
export function parseAtlas(
  source: unknown,
  plugin: string,
  baseUrl: string,
): AtlasParseResult {
  const warnings: string[] = [];
  if (!isRecord(source)) {
    return { ok: false, reason: "材质清单的顶层必须是 JSON 对象", warnings };
  }

  const base = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;

  const tilePixelsValue = readNumber(source["tilePixels"]);
  let tilePixels = DEFAULT_TILE_PIXELS;
  if (tilePixelsValue !== null && tilePixelsValue > 0) {
    tilePixels = tilePixelsValue;
  } else {
    warnings.push(`tilePixels 无效，按 ${DEFAULT_TILE_PIXELS} 处理`);
  }

  // Entities are parsed first and their images collected before any tile is accepted: one file may
  // serve as a tile OR as an entity strip, never both, and the entity owns the file when they clash.
  const entities = new Map<string, EntityEntry>();
  const entityImages = new Set<string>();
  const entitiesValue = source["entities"];
  if (isRecord(entitiesValue)) {
    for (const [rawKind, rawEntry] of Object.entries(entitiesValue)) {
      const kind = rawKind.trim();
      if (kind === "") {
        warnings.push("entities 里有空名字，已忽略");
        continue;
      }
      if (!isRecord(rawEntry)) {
        warnings.push(`entities.${kind} 必须是对象`);
        continue;
      }
      const image = readImagePath(rawEntry["image"], `entities.${kind}`, warnings);
      if (image === null) {
        continue;
      }
      const framesValue = readNumber(rawEntry["frames"]);
      if (framesValue === null || !Number.isInteger(framesValue) || framesValue < 1 || framesValue > MAX_FRAMES) {
        warnings.push(`entities.${kind}.frames 必须是 1..${MAX_FRAMES} 的整数，已忽略该实体`);
        continue;
      }
      // Within one manifest a strip file belongs to exactly one entity kind; a later entry naming
      // the same image is dropped so the frames of two kinds can never be sliced from one file.
      if (entityImages.has(image)) {
        warnings.push(`entities.${kind} 的 image '${image}' 与先出现的实体贴图重复，已忽略该实体`);
        continue;
      }

      const fpsValue = readNumber(rawEntry["fps"]);
      const fps = fpsValue !== null && fpsValue > 0 ? fpsValue : DEFAULT_FPS;
      if (fpsValue !== null && fpsValue <= 0) {
        warnings.push(`entities.${kind}.fps 必须大于 0，按 ${DEFAULT_FPS} 处理`);
      }

      let anchor: readonly [number, number] = DEFAULT_ANCHOR;
      const anchorValue = rawEntry["anchor"];
      if (Array.isArray(anchorValue) && anchorValue.length >= 2) {
        const anchorValues: readonly unknown[] = anchorValue;
        const anchorX = readNumber(anchorValues[0]);
        const anchorY = readNumber(anchorValues[1]);
        if (anchorX !== null && anchorY !== null) {
          anchor = [anchorX, anchorY];
        } else {
          warnings.push(`entities.${kind}.anchor 必须是两个数字，按 [0.5, 0.75] 处理`);
        }
      } else if (anchorValue !== undefined) {
        warnings.push(`entities.${kind}.anchor 必须是两个数字，按 [0.5, 0.75] 处理`);
      }

      const scaleValue = readNumber(rawEntry["scale"]);
      const scale = scaleValue !== null && scaleValue > 0 ? scaleValue : DEFAULT_SCALE;
      if (scaleValue !== null && scaleValue <= 0) {
        warnings.push(`entities.${kind}.scale 必须大于 0，按 1 处理`);
      }

      entityImages.add(image);
      entities.set(kind, {
        image,
        frames: framesValue,
        fps,
        anchor,
        scale,
        name: readName(rawEntry["name"]),
      });
    }
  } else if (entitiesValue !== undefined) {
    warnings.push("entities 必须是对象，已忽略整个 entities 字段");
  }

  const tiles = new Map<string, TileEntry>();
  const tilesValue = source["tiles"];
  if (isRecord(tilesValue)) {
    for (const [rawName, rawEntry] of Object.entries(tilesValue)) {
      const name = rawName.trim();
      if (name === "") {
        warnings.push("tiles 里有空名字，已忽略");
        continue;
      }
      if (!isRecord(rawEntry)) {
        warnings.push(`tiles.${name} 必须是 { image, name } 对象`);
        continue;
      }
      const image = readImagePath(rawEntry["image"], `tiles.${name}`, warnings);
      if (image === null) {
        continue;
      }
      // A tile may not reuse an entity's strip file: drawing an entity frame strip as whole-image
      // terrain would paint torn frames over the map, so the tile is dropped and that terrain falls
      // back to procedural drawing instead.
      if (entityImages.has(image)) {
        warnings.push(`tiles.${name} 的 image '${image}' 与实体贴图重复，已忽略该地块`);
        continue;
      }
      tiles.set(name, { image, name: readName(rawEntry["name"]) });
    }
  } else if (tilesValue === undefined) {
    warnings.push("材质清单没有 tiles 对象，所有地形都会使用程序化绘制");
  } else {
    warnings.push("tiles 必须是对象，已忽略整个 tiles 字段");
  }

  // Optional item definitions (`items: { "<path>": "<name>" | { "name": "..." } }`, or an array of
  // paths). They carry no art, so an item-only manifest is still a valid (if invisible) entry; the main
  // client uses the names to label resources the plugin defines.
  const items = new Map<string, string>();
  const itemsValue = source["items"];
  if (isRecord(itemsValue)) {
    for (const [rawPath, rawEntry] of Object.entries(itemsValue)) {
      const path = rawPath.trim();
      if (path === "") {
        warnings.push("items 里有空名字，已忽略");
        continue;
      }
      if (typeof rawEntry === "string") {
        items.set(path, rawEntry.trim());
        continue;
      }
      if (isRecord(rawEntry)) {
        items.set(path, readName(rawEntry["name"]));
        continue;
      }
      warnings.push(`items.${path} 必须是名称字符串或 { name } 对象`);
    }
  } else if (Array.isArray(itemsValue)) {
    for (const rawPath of itemsValue) {
      if (typeof rawPath !== "string" || rawPath.trim() === "") {
        warnings.push("items 数组里只能放非空物品路径");
        continue;
      }
      items.set(rawPath.trim(), "");
    }
  } else if (itemsValue !== undefined) {
    warnings.push("items 必须是对象或字符串数组，已忽略整个 items 字段");
  }

  if (tiles.size === 0 && entities.size === 0 && items.size === 0) {
    return { ok: false, reason: "材质清单里没有任何可用的地块、实体或物品定义", warnings };
  }

  const images: string[] = [];
  const seen = new Set<string>();
  for (const entry of [...tiles.values(), ...entities.values()]) {
    if (!seen.has(entry.image)) {
      seen.add(entry.image);
      images.push(entry.image);
    }
  }

  return {
    ok: true,
    data: { plugin, baseUrl: base, tilePixels, tiles, entities, items },
    images,
    warnings,
  };
}

/**
 * Reads one entry of the server's manifest array, before its files are loaded.
 *
 * The URL rule is deliberately strict: `baseUrl` must be an absolute path on this origin. The server
 * derives it from the plugin id, so an entry with a scheme or a protocol relative URL is either not from
 * this server or not something the client should fetch, and it is skipped with a warning rather than
 * being followed. Both `plugin` and `baseUrl` are required: without a plugin id the HUD cannot attribute
 * the art, and without a base the image paths have nothing to resolve against.
 */
export function readManifestEntry(value: unknown): ManifestEntryResult {
  if (!isRecord(value)) {
    return { ok: false, warning: "条目必须是 JSON 对象" };
  }
  const pluginValue = value["plugin"];
  const plugin = typeof pluginValue === "string" ? pluginValue.trim() : "";
  if (plugin === "") {
    return { ok: false, warning: "条目缺少非空的 plugin 字段" };
  }
  const baseValue = value["baseUrl"];
  const baseUrl = typeof baseValue === "string" ? baseValue.trim() : "";
  if (baseUrl === "") {
    return { ok: false, warning: "条目缺少非空的 baseUrl 字段" };
  }
  if (!baseUrl.startsWith("/") || baseUrl.startsWith("//") || baseUrl.includes("://")) {
    return { ok: false, warning: `baseUrl 必须是同源的绝对路径，当前是 '${baseUrl}'` };
  }
  return { ok: true, entry: { plugin, baseUrl } };
}

/**
 * Applies every source in order and reports which source won each name.
 *
 * This is the whole merge rule of the feature: a later source overrides an earlier one for the same tile
 * path or entity kind, and names a source does not define are left to the earlier ones. Sources are
 * grouped per file in the order the server sent the plugins, so plugin load order is the only ordering
 * that matters.
 */
export function mergeLayers(entries: readonly AtlasSource[]): MergedLayers {
  const tiles = new Map<string, number>();
  const entities = new Map<string, number>();
  const items = new Map<string, number>();
  const plugins: string[] = [];
  for (let layer = 0; layer < entries.length; layer += 1) {
    const data = entries[layer];
    if (data === undefined) {
      continue;
    }
    if (data.plugin !== "" && !plugins.includes(data.plugin)) {
      plugins.push(data.plugin);
    }
    for (const name of data.tiles.keys()) {
      tiles.set(name, layer);
    }
    for (const name of data.entities.keys()) {
      entities.set(name, layer);
    }
    for (const name of data.items.keys()) {
      items.set(name, layer);
    }
  }
  return { tiles, entities, items, plugins };
}

/**
 * Builds the composite atlas from the material files whose images loaded.
 *
 * Returns `null` when no file contributed anything, which is the case for an empty manifest array and for
 * a run in which every entry failed; the caller then keeps the procedural renderer. Each name is resolved
 * to the source that won it, so a tile or an entity can never be drawn with another plugin's file.
 */
export function compositeAtlas(sources: readonly AtlasSource[]): Atlas | null {
  if (sources.length === 0) {
    return null;
  }
  const layers = mergeLayers(sources);
  const tiles = new Map<string, AtlasTile>();
  for (const [name, layer] of layers.tiles) {
    const source = sources[layer];
    const frame = source?.tiles.get(name);
    if (source !== undefined && frame !== undefined) {
      tiles.set(name, { rect: frame.rect, name: frame.name, source });
    }
  }
  const entities = new Map<string, AtlasEntity>();
  for (const [name, layer] of layers.entities) {
    const source = sources[layer];
    const sprite = source?.entities.get(name);
    if (source !== undefined && sprite !== undefined) {
      entities.set(name, { sprite, source });
    }
  }
  const items = new Map<string, string>();
  for (const [name, layer] of layers.items) {
    const source = sources[layer];
    if (source === undefined || !source.items.has(name)) {
      continue;
    }
    items.set(name, labelOrId(source.items.get(name) ?? "", name));
  }
  if (tiles.size === 0 && entities.size === 0 && items.size === 0) {
    return null;
  }
  const tileNames = new Map<string, string>();
  for (const [name, tile] of tiles) {
    tileNames.set(name, labelOrId(tile.name, name));
  }
  const entityNames = new Map<string, string>();
  for (const [name, entity] of entities) {
    entityNames.set(name, labelOrId(entity.sprite.name, name));
  }
  return {
    tiles,
    entities,
    names: { tiles: tileNames, entities: entityNames, items },
    sources,
  };
}

/** The display label for a name, or the id itself when the entry carries no usable name. */
function labelOrId(name: string, id: string): string {
  const trimmed = name.trim();
  return trimmed === "" ? id : trimmed;
}

/** Strips a namespace such as `oc:` from a content path, matching the renderer's atlas lookup. */
function bareKind(kind: string): string {
  const separator = kind.lastIndexOf(":");
  return separator >= 0 ? kind.slice(separator + 1) : kind;
}

/**
 * The atlas entry for one entity kind, or `undefined` when the atlas has no sprite for it.
 *
 * This is the one lookup the world renderer and the tooltip thumbnail share, so the small thumbnail
 * can never show a different plant than the map does. A namespaced path falls back to its bare path,
 * exactly like {@link entityDisplayName}.
 */
export function lookupAtlasEntity(atlas: Atlas, kind: string): AtlasEntity | undefined {
  return atlas.entities.get(kind) ?? atlas.entities.get(bareKind(kind));
}

/** The atlas entry for one terrain path; the renderer's shared lookup for tiles. */
export function lookupAtlasTile(atlas: Atlas, terrain: string): AtlasTile | undefined {
  return atlas.tiles.get(terrain) ?? atlas.tiles.get(bareKind(terrain));
}

/**
 * The item label a plugin manifest defines, with the raw path as fallback.
 *
 * The manifest's optional `items` table wins; a namespaced path falls back to its bare path. The main
 * client adds the classic `金` / `木` mapping after this, so an item the manifest names is never
 * renamed by the built-in fallback table.
 */
export function itemDisplayLabel(atlas: Atlas | null, path: string): string {
  if (atlas === null) {
    return path;
  }
  return atlas.names.items.get(path) ?? atlas.names.items.get(bareKind(path)) ?? path;
}

/**
 * The display label for one entity kind, with the raw content path as fallback.
 *
 * A kind written with a namespace (`oc:unit.melee.t1`) falls back to its bare path, matching how the
 * renderer resolves atlas entries. `null` means no atlas is loaded, so the raw id is the only label.
 */
export function entityDisplayName(atlas: Atlas | null, kind: string): string {
  if (atlas === null) {
    return kind;
  }
  return atlas.names.entities.get(kind) ?? atlas.names.entities.get(bareKind(kind)) ?? kind;
}

/** The display label for one terrain path, with the raw id as fallback. */
export function tileDisplayName(atlas: Atlas | null, terrain: string): string {
  if (atlas === null) {
    return terrain;
  }
  return atlas.names.tiles.get(terrain) ?? atlas.names.tiles.get(bareKind(terrain)) ?? terrain;
}

/**
 * The HUD label for the source of the current art.
 *
 * `procedural` when nothing was loaded; the plugin ids when at most three contributed, because they fit and
 * are the useful answer; a count otherwise. A plugin without an id (a legacy document) counts as a plugin
 * but cannot be named, which is why the count form is also the fallback.
 */
export function atlasLabel(plugins: readonly string[]): string {
  const ids = [...new Set(plugins.filter((plugin) => plugin !== ""))];
  if (ids.length === 0) {
    if (plugins.length === 0) {
      return "procedural";
    }
    return `atlas · ${plugins.length} plugin${plugins.length === 1 ? "" : "s"}`;
  }
  if (ids.length <= 3) {
    return `atlas · ${ids.join(", ")}`;
  }
  return `atlas · ${ids.length} plugins`;
}

/**
 * Loads one material file.
 *
 * Format-agnostic: the browser decodes PNG and SVG alike through `new Image()`.
 * An SVG file must carry explicit `width`/`height` attributes equal to its
 * viewBox, or `naturalWidth`/`naturalHeight` stay 0 (or default to 300x150) and
 * the strip slicing below would tear; the classic plugin's generator
 * (`tools/redraw_assets_svg.py`) always emits them. The `naturalWidth > 0`
 * guard below rejects such a file and drops exactly the materials that
 * reference it, never the whole atlas.
 */
function loadImage(url: string): Promise<HTMLImageElement | null> {
  return new Promise<HTMLImageElement | null>((resolve) => {
    const image = new Image();
    image.decoding = "async";
    image.addEventListener("load", () => {
      resolve(image.naturalWidth > 0 && image.naturalHeight > 0 ? image : null);
    });
    image.addEventListener("error", () => {
      resolve(null);
    });
    image.src = url;
  });
}

/** Logs every parse warning in one `console.warn` call, so a bad entry is reported once. */
function reportWarnings(warnings: readonly string[]): void {
  if (warnings.length === 0) {
    return;
  }
  console.warn(`插件材质清单：忽略了 ${warnings.length} 个条目：\n${warnings.join("\n")}`);
}

/** Resolves a manifest path against the page, which keeps it on this origin. */
function resolveMaterialUrl(path: string): string {
  return new URL(path, document.baseURI).href;
}

/** The tiles and entities of one manifest entry that share one file. */
interface FileContents {
  readonly tiles: Map<string, TileEntry>;
  readonly entities: Map<string, EntityEntry>;
}

/** The per-file sources built from parsed entries, plus the problems found while slicing. */
export interface BuiltAtlasSources {
  /** One source per file that loaded, plugins in the order the server sent them. */
  readonly sources: AtlasSource[];
  /** One human readable line per entity whose strip file could not be sliced. */
  readonly problems: readonly string[];
}

/**
 * Turns parsed entries into per-file sources.
 *
 * Pure: the decoded images come in keyed by the same resolved URL `parseAtlas`'s `images` list produces
 * through `resolveUrl`, and a file missing from the map drops exactly the tiles and entities that
 * reference it - the loader's failure rule, testable without the DOM. A tile's rect is its whole image;
 * an entity's frames are equal vertical slices, and a strip whose height does not divide into its frame
 * count drops that entity with a collected problem instead of drawing torn frames.
 */
export function buildAtlasSources(
  datas: readonly AtlasData[],
  images: ReadonlyMap<string, HTMLImageElement>,
  resolveUrl: (data: AtlasData, imagePath: string) => string,
): BuiltAtlasSources {
  const sources: AtlasSource[] = [];
  const problems: string[] = [];
  for (const data of datas) {
    // A plugin's own url -> contents map, so a source only ever hosts its own plugin's tiles and
    // entities even in the unlikely case two entries name the same file.
    const groups = new Map<string, FileContents>();
    const groupOf = (url: string): FileContents => {
      let contents = groups.get(url);
      if (contents === undefined) {
        contents = { tiles: new Map(), entities: new Map() };
        groups.set(url, contents);
      }
      return contents;
    };
    for (const [tileName, tile] of data.tiles) {
      groupOf(resolveUrl(data, tile.image)).tiles.set(tileName, tile);
    }
    for (const [kind, entity] of data.entities) {
      groupOf(resolveUrl(data, entity.image)).entities.set(kind, entity);
    }
    for (const [url, contents] of groups) {
      const image = images.get(url);
      if (image === undefined) {
        continue;
      }
      const tiles = new Map<string, TileFrame>();
      for (const [name, tile] of contents.tiles) {
        tiles.set(name, {
          rect: tileRect(image.naturalWidth, image.naturalHeight),
          name: tile.name,
        });
      }
      const entities = new Map<string, EntitySprite>();
      for (const [kind, entity] of contents.entities) {
        const frames = entityFrameRects(image.naturalWidth, image.naturalHeight, entity.frames);
        if (frames === null) {
          problems.push(
            `${data.plugin} ${url}：entities.${kind} 的图片高度 ${image.naturalHeight} 不能整除 ${entity.frames} 帧，已忽略该实体`,
          );
          continue;
        }
        entities.set(kind, {
          frames,
          fps: entity.fps,
          anchor: entity.anchor,
          scale: entity.scale,
          name: entity.name,
        });
      }
      if (tiles.size === 0 && entities.size === 0) {
        continue;
      }
      sources.push({
        image,
        tilePixels: data.tilePixels,
        plugin: data.plugin,
        tiles,
        entities,
        items: data.items,
      });
    }
  }
  return { sources, problems };
}

/**
 * Fetches the merged plugin manifest and builds the composite atlas.
 *
 * Resolves to `null` for every failure - an unreachable server, a non-200 answer, a body that is not an
 * array, an empty array, an entry that is malformed, or one whose schema fails outright. A broken entry
 * is logged and skipped whole; a single file that fails to decode drops exactly the tiles and entities
 * that reference it while the rest of its plugin still loads. Each unique file is downloaded once and
 * becomes its own source: a tile's rect is the whole image, an entity's frames are equal vertical slices
 * (a strip whose height does not divide into its frame count is dropped with a warning). The promise
 * never rejects, so the caller can always fall back to the procedural renderer.
 */
export async function loadPluginAtlas(
  manifestUrl: string = PLUGIN_MANIFEST_URL,
): Promise<Atlas | null> {
  try {
    const response = await fetch(manifestUrl, { headers: { Accept: "application/json" } });
    if (!response.ok) {
      console.info(`没有取到插件材质清单 ${manifestUrl}（HTTP ${response.status}），使用程序化绘制`);
      return null;
    }
    const payload: unknown = await response.json();
    if (!Array.isArray(payload)) {
      console.warn("插件材质清单不是 JSON 数组，使用程序化绘制");
      return null;
    }
    const entries: readonly unknown[] = payload;
    if (entries.length === 0) {
      console.info("服务器没有发布任何插件材质清单，使用程序化绘制");
      return null;
    }

    const parsedEntries: { readonly data: AtlasData; readonly images: readonly string[] }[] = [];
    for (let index = 0; index < entries.length; index += 1) {
      const raw = entries[index];
      const read = readManifestEntry(raw);
      if (!read.ok) {
        console.warn(`插件材质清单第 ${index + 1} 条已跳过：${read.warning}`);
        continue;
      }
      const parsed = parseAtlas(raw, read.entry.plugin, read.entry.baseUrl);
      reportWarnings(parsed.warnings);
      if (!parsed.ok) {
        console.warn(`插件材质 ${read.entry.plugin} 不可用：${parsed.reason}，已跳过该插件`);
        continue;
      }
      parsedEntries.push({ data: parsed.data, images: parsed.images });
    }

    // Every unique file the surviving entries reference, in first-seen order, downloaded exactly once.
    const urls: string[] = [];
    const seenUrls = new Set<string>();
    for (const entry of parsedEntries) {
      for (const imagePath of entry.images) {
        const url = resolveMaterialUrl(entry.data.baseUrl + imagePath);
        if (!seenUrls.has(url)) {
          seenUrls.add(url);
          urls.push(url);
        }
      }
    }

    const loaded = new Map<string, HTMLImageElement>();
    await Promise.all(
      urls.map(async (url) => {
        const image = await loadImage(url);
        if (image === null) {
          console.warn(`插件材质文件 ${url} 载入失败，引用它的地块与实体已跳过`);
          return;
        }
        loaded.set(url, image);
      }),
    );

    const built = buildAtlasSources(
      parsedEntries.map((entry) => entry.data),
      loaded,
      (data, imagePath) => resolveMaterialUrl(data.baseUrl + imagePath),
    );
    if (built.problems.length > 0) {
      console.warn(`插件材质清单：\n${built.problems.join("\n")}`);
    }

    const atlas = compositeAtlas(built.sources);
    if (atlas === null) {
      console.warn("所有插件材质条目都不可用，使用程序化绘制");
      return null;
    }
    return atlas;
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error);
    console.warn(`载入插件材质失败（${message}），使用程序化绘制`);
    return null;
  }
}
