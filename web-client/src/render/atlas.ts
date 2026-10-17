/**
 * The texture atlas contract, now published by plugins.
 *
 * Textures are no longer a folder the page ships. Every loaded plugin may put `assets/manifest.json` and
 * its image inside its own jar; the server merges those manifests into `GET /plugin-assets/manifest.json`
 * and serves the images under `/plugin-assets/<plugin>/<file>`. The client fetches that array on start,
 * loads each entry's image, and composes the per-plugin atlases into one `Atlas`.
 *
 * The merge rule is **last wins**: entries are applied in the order the server sent them, which is plugin
 * load order, so a later plugin overrides an earlier one for the same tile path or entity kind. Every tile
 * and every entity keeps a reference to the image it came from, so the renderer always draws from the plugin
 * that won that name.
 *
 * The document is parsed defensively: a malformed entry, an image that does not decode, a server that
 * publishes nothing or a failed fetch all resolve to "no atlas" and the procedural renderer takes over. A
 * broken plugin must never stop the page, and one broken entry is skipped without discarding the others.
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

export interface EntitySprite {
  /** Animation frames, laid out however the artist likes. */
  readonly frames: readonly SpriteRect[];
  /** Frames per second; defaults to 4. */
  readonly fps: number;
  /** Point inside the sprite that sits on the entity's feet; defaults to [0.5, 0.75]. */
  readonly anchor: readonly [number, number];
  /** Extra multiplier on top of the camera zoom; defaults to 1. */
  readonly scale: number;
}

/** Everything parsed out of one manifest entry, before its image is attached. */
export interface AtlasData {
  /** The plugin that published the entry; empty only for a legacy document without one. */
  readonly plugin: string;
  /** The URL the entry named; empty when the document only had an `image` file name. */
  readonly atlasUrl: string;
  /** The image file name, for example `atlas.png`. */
  readonly imageName: string;
  /** Recommended source tile size in pixels (metadata; tiles are stretched to the camera). */
  readonly tilePixels: number;
  readonly tiles: ReadonlyMap<string, SpriteRect>;
  readonly entities: ReadonlyMap<string, EntitySprite>;
}

/** One plugin's parsed atlas with its decoded image. */
export interface AtlasSource extends AtlasData {
  readonly image: HTMLImageElement;
}

/** A tile frame together with the plugin image it must be drawn from. */
export interface AtlasTile {
  readonly rect: SpriteRect;
  readonly source: AtlasSource;
}

/** An entity animation together with the plugin image it must be drawn from. */
export interface AtlasEntity {
  readonly sprite: EntitySprite;
  readonly source: AtlasSource;
}

/** The composite of every plugin atlas: last wins per tile path and entity kind. */
export interface Atlas {
  readonly tiles: ReadonlyMap<string, AtlasTile>;
  readonly entities: ReadonlyMap<string, AtlasEntity>;
  /** The entries that contributed, in the order the server sent them. */
  readonly sources: readonly AtlasSource[];
}

export type AtlasParseResult =
  | { readonly ok: true; readonly data: AtlasData; readonly warnings: readonly string[] }
  | { readonly ok: false; readonly reason: string; readonly warnings: readonly string[] };

/** One entry of the server's manifest array, before its image is loaded. */
export interface ManifestEntry {
  readonly plugin: string;
  readonly atlasUrl: string;
}

/** The result of reading one manifest array entry; a refusal explains what was wrong with it. */
export type ManifestEntryResult =
  | { readonly ok: true; readonly entry: ManifestEntry }
  | { readonly ok: false; readonly warning: string };

/**
 * The pure result of applying every entry in order.
 *
 * The maps hold the index of the entry that won each name, which is what lets the image be attached later
 * without holding two copies of the frame data.
 */
export interface MergedLayers {
  /** Tile path to the index of the entry that owns it; the last entry that defines it wins. */
  readonly tiles: ReadonlyMap<string, number>;
  /** Entity kind to the index of the entry that owns it; the last entry that defines it wins. */
  readonly entities: ReadonlyMap<string, number>;
  /** The contributing plugin ids, in entry order, without duplicates or blank ids. */
  readonly plugins: readonly string[];
}

/** The merged manifest path; the Vite dev proxy forwards this prefix to the Java server. */
export const PLUGIN_MANIFEST_URL = "/plugin-assets/manifest.json";

const DEFAULT_TILE_PIXELS = 32;
const DEFAULT_FPS = 4;
const DEFAULT_ANCHOR: readonly [number, number] = [0.5, 0.75];
const DEFAULT_SCALE = 1;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function readRect(
  value: unknown,
  imageWidth: number,
  imageHeight: number,
  label: string,
  warnings: string[],
): SpriteRect | null {
  if (!Array.isArray(value) || value.length !== 4) {
    warnings.push(`${label}：帧必须是 [x, y, w, h] 四元数组`);
    return null;
  }
  const values: readonly unknown[] = value;
  const x = readNumber(values[0]);
  const y = readNumber(values[1]);
  const width = readNumber(values[2]);
  const height = readNumber(values[3]);
  if (x === null || y === null || width === null || height === null) {
    warnings.push(`${label}：帧的四个值必须都是有限数字`);
    return null;
  }
  if (width <= 0 || height <= 0) {
    warnings.push(`${label}：帧宽高必须大于 0（当前 ${width}×${height}）`);
    return null;
  }
  if (x < 0 || y < 0 || x + width > imageWidth + 1e-6 || y + height > imageHeight + 1e-6) {
    warnings.push(
      `${label}：帧 [${x}, ${y}, ${width}, ${height}] 超出图片 ${imageWidth}×${imageHeight}，已忽略`,
    );
    return null;
  }
  return { x, y, width, height };
}

/**
 * Validates one parsed manifest entry against the size of its image.
 *
 * Never throws. Out-of-bounds and malformed entries are collected in `warnings` and skipped; the caller
 * decides whether to log them. A result with no usable tile and no usable entity is a failure. The entry
 * either names its image with `image` (the schema an artist writes) or with `atlasUrl` (what the server
 * publishes); the file name is derived from whichever is present.
 */
export function parseAtlas(
  source: unknown,
  imageWidth: number,
  imageHeight: number,
): AtlasParseResult {
  const warnings: string[] = [];
  if (!isRecord(source)) {
    return { ok: false, reason: "图集清单的顶层必须是 JSON 对象", warnings };
  }
  if (!Number.isFinite(imageWidth) || !Number.isFinite(imageHeight) || imageWidth <= 0 || imageHeight <= 0) {
    return { ok: false, reason: `图集图片尺寸无效：${imageWidth}×${imageHeight}`, warnings };
  }

  const pluginValue = source["plugin"];
  const plugin = typeof pluginValue === "string" ? pluginValue.trim() : "";
  const atlasValue = source["atlasUrl"];
  const atlasUrl = typeof atlasValue === "string" ? atlasValue.trim() : "";
  const imageValue = source["image"];
  const declaredImage = typeof imageValue === "string" ? imageValue.trim() : "";
  const slash = atlasUrl.lastIndexOf("/");
  const imageName = declaredImage !== "" ? declaredImage : atlasUrl.slice(slash + 1);
  if (imageName === "") {
    return { ok: false, reason: "图集清单缺少非空的 image 字段或 atlasUrl 字段", warnings };
  }

  const tilePixelsValue = readNumber(source["tilePixels"]);
  let tilePixels = DEFAULT_TILE_PIXELS;
  if (tilePixelsValue !== null && tilePixelsValue > 0) {
    tilePixels = tilePixelsValue;
  } else {
    warnings.push(`tilePixels 无效，按 ${DEFAULT_TILE_PIXELS} 处理`);
  }

  const tiles = new Map<string, SpriteRect>();
  const tilesValue = source["tiles"];
  if (isRecord(tilesValue)) {
    for (const [rawName, rawRect] of Object.entries(tilesValue)) {
      const name = rawName.trim();
      if (name === "") {
        warnings.push("tiles 里有空名字，已忽略");
        continue;
      }
      const rect = readRect(rawRect, imageWidth, imageHeight, `tiles.${name}`, warnings);
      if (rect !== null) {
        tiles.set(name, rect);
      }
    }
  } else if (tilesValue === undefined) {
    warnings.push("图集清单没有 tiles 对象，所有地形都会使用程序化绘制");
  } else {
    warnings.push("tiles 必须是对象，已忽略整个 tiles 字段");
  }

  const entities = new Map<string, EntitySprite>();
  const entitiesValue = source["entities"];
  if (isRecord(entitiesValue)) {
    for (const [rawName, rawEntry] of Object.entries(entitiesValue)) {
      const name = rawName.trim();
      if (name === "") {
        warnings.push("entities 里有空名字，已忽略");
        continue;
      }
      if (!isRecord(rawEntry)) {
        warnings.push(`entities.${name} 必须是对象`);
        continue;
      }
      const framesValue = rawEntry["frames"];
      if (!Array.isArray(framesValue)) {
        warnings.push(`entities.${name}.frames 必须是数组`);
        continue;
      }
      const frames: SpriteRect[] = [];
      for (const rawFrame of framesValue) {
        const frame = readRect(rawFrame, imageWidth, imageHeight, `entities.${name}.frames`, warnings);
        if (frame !== null) {
          frames.push(frame);
        }
      }
      if (frames.length === 0) {
        warnings.push(`entities.${name} 没有可用的动画帧，已忽略`);
        continue;
      }

      const fpsValue = readNumber(rawEntry["fps"]);
      const fps = fpsValue !== null && fpsValue > 0 ? fpsValue : DEFAULT_FPS;
      if (fpsValue !== null && fpsValue <= 0) {
        warnings.push(`entities.${name}.fps 必须大于 0，按 ${DEFAULT_FPS} 处理`);
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
          warnings.push(`entities.${name}.anchor 必须是两个数字，按 [0.5, 0.75] 处理`);
        }
      } else if (anchorValue !== undefined) {
        warnings.push(`entities.${name}.anchor 必须是两个数字，按 [0.5, 0.75] 处理`);
      }

      const scaleValue = readNumber(rawEntry["scale"]);
      const scale = scaleValue !== null && scaleValue > 0 ? scaleValue : DEFAULT_SCALE;
      if (scaleValue !== null && scaleValue <= 0) {
        warnings.push(`entities.${name}.scale 必须大于 0，按 1 处理`);
      }

      entities.set(name, { frames, fps, anchor, scale });
    }
  } else if (entitiesValue !== undefined) {
    warnings.push("entities 必须是对象，已忽略整个 entities 字段");
  }

  if (tiles.size === 0 && entities.size === 0) {
    return { ok: false, reason: "图集清单里没有任何可用的地形帧或实体帧", warnings };
  }

  return {
    ok: true,
    data: {
      plugin,
      atlasUrl,
      imageName,
      tilePixels,
      tiles,
      entities,
    },
    warnings,
  };
}

/**
 * Reads one entry of the server's manifest array, before its image is loaded.
 *
 * The URL rule is deliberately strict: only an absolute path on this origin is accepted. The server
 * rewrites the plugin's `image` into such a path, so an entry with a scheme or a protocol relative URL is
 * either not from this server or not something the client should fetch, and it is skipped with a warning
 * rather than being followed. A missing `plugin` is tolerated - the image can still be drawn - while a
 * missing URL cannot.
 */
export function readManifestEntry(value: unknown): ManifestEntryResult {
  if (!isRecord(value)) {
    return { ok: false, warning: "条目必须是 JSON 对象" };
  }
  const urlValue = value["atlasUrl"];
  const atlasUrl = typeof urlValue === "string" ? urlValue.trim() : "";
  if (atlasUrl === "") {
    return { ok: false, warning: "条目缺少非空的 atlasUrl 字段" };
  }
  if (!atlasUrl.startsWith("/") || atlasUrl.startsWith("//") || atlasUrl.includes("://")) {
    return { ok: false, warning: `atlasUrl 必须是同源的绝对路径，当前是 '${atlasUrl}'` };
  }
  const pluginValue = value["plugin"];
  const plugin = typeof pluginValue === "string" ? pluginValue.trim() : "";
  return { ok: true, entry: { plugin, atlasUrl } };
}

/**
 * Applies every entry in order and reports which entry won each name.
 *
 * This is the whole merge rule of the feature: a later entry overrides an earlier one for the same tile
 * path or entity kind, and names an entry does not define are left to the earlier ones. Entries are the
 * same array the server sent, so plugin load order is the only ordering that matters.
 */
export function mergeLayers(entries: readonly AtlasData[]): MergedLayers {
  const tiles = new Map<string, number>();
  const entities = new Map<string, number>();
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
  }
  return { tiles, entities, plugins };
}

/**
 * Builds the composite atlas from the entries whose images loaded.
 *
 * Returns `null` when no entry contributed anything, which is the case for an empty manifest array and for
 * a run in which every entry failed; the caller then keeps the procedural renderer. Each name is resolved
 * to the entry that won it, so a tile or an entity can never be drawn with another plugin's image.
 */
export function compositeAtlas(sources: readonly AtlasSource[]): Atlas | null {
  if (sources.length === 0) {
    return null;
  }
  const layers = mergeLayers(sources);
  const tiles = new Map<string, AtlasTile>();
  for (const [name, layer] of layers.tiles) {
    const source = sources[layer];
    const rect = source?.tiles.get(name);
    if (source !== undefined && rect !== undefined) {
      tiles.set(name, { rect, source });
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
  if (tiles.size === 0 && entities.size === 0) {
    return null;
  }
  return { tiles, entities, sources };
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
  console.warn(`插件图集：忽略了 ${warnings.length} 个条目：\n${warnings.join("\n")}`);
}

/** Resolves the server supplied path against the page, which keeps it on this origin. */
function resolveAtlasUrl(atlasUrl: string): string {
  return new URL(atlasUrl, document.baseURI).href;
}

/**
 * Fetches the merged plugin manifest and builds the composite atlas.
 *
 * Resolves to `null` for every failure - an unreachable server, a non-200 answer, a body that is not an
 * array, an empty array, an entry that is malformed, an image that does not decode, or an entry with no
 * usable frames. A single bad entry is logged and skipped, and the remaining entries still load. The
 * promise never rejects, so the caller can always fall back to the procedural renderer.
 */
export async function loadPluginAtlas(
  manifestUrl: string = PLUGIN_MANIFEST_URL,
): Promise<Atlas | null> {
  try {
    const response = await fetch(manifestUrl, { headers: { Accept: "application/json" } });
    if (!response.ok) {
      console.info(`没有取到插件图集清单 ${manifestUrl}（HTTP ${response.status}），使用程序化绘制`);
      return null;
    }
    const payload: unknown = await response.json();
    if (!Array.isArray(payload)) {
      console.warn("插件图集清单不是 JSON 数组，使用程序化绘制");
      return null;
    }
    const entries: readonly unknown[] = payload;
    if (entries.length === 0) {
      console.info("服务器没有发布任何插件图集，使用程序化绘制");
      return null;
    }

    const sources: AtlasSource[] = [];
    for (let index = 0; index < entries.length; index += 1) {
      const raw = entries[index];
      const read = readManifestEntry(raw);
      if (!read.ok) {
        console.warn(`插件图集第 ${index + 1} 条已跳过：${read.warning}`);
        continue;
      }
      const url = resolveAtlasUrl(read.entry.atlasUrl);
      const image = await loadImage(url);
      if (image === null) {
        console.warn(`插件图集图片 ${url} 载入失败，已跳过该插件`);
        continue;
      }
      const parsed = parseAtlas(raw, image.naturalWidth, image.naturalHeight);
      reportWarnings(parsed.warnings);
      if (!parsed.ok) {
        const name = read.entry.plugin === "" ? url : read.entry.plugin;
        console.warn(`插件图集 ${name} 不可用：${parsed.reason}，已跳过该插件`);
        continue;
      }
      sources.push({ ...parsed.data, image });
    }

    const atlas = compositeAtlas(sources);
    if (atlas === null) {
      console.warn("所有插件图集条目都不可用，使用程序化绘制");
      return null;
    }
    return atlas;
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error);
    console.warn(`载入插件图集失败（${message}），使用程序化绘制`);
    return null;
  }
}
