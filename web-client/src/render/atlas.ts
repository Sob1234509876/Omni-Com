/**
 * The texture atlas contract.
 *
 * `atlas.json` and `atlas.png` live in `public/textures/`. The JSON is parsed defensively: a broken
 * atlas must never stop the client, because the procedural fallback is a complete renderer. Frames
 * that fall outside the image are dropped and reported once, and any failure makes `loadAtlas`
 * resolve to `null`.
 *
 * The parsing half is free of DOM calls so `scripts/check-render-math.ts` can exercise it directly.
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

/** Everything parsed out of `atlas.json`, before the image is attached. */
export interface AtlasData {
  /** The `image` field as written in the JSON, for example `atlas.png`. */
  readonly imageName: string;
  /** Recommended source tile size in pixels (metadata; tiles are stretched to the camera). */
  readonly tilePixels: number;
  readonly tiles: ReadonlyMap<string, SpriteRect>;
  readonly entities: ReadonlyMap<string, EntitySprite>;
}

/** A loaded atlas: parsed data plus the decoded image and a short label for the HUD. */
export interface Atlas extends AtlasData {
  readonly image: HTMLImageElement;
  readonly label: string;
}

export type AtlasParseResult =
  | { readonly ok: true; readonly data: AtlasData; readonly warnings: readonly string[] }
  | { readonly ok: false; readonly reason: string; readonly warnings: readonly string[] };

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
 * Validates one parsed `atlas.json` object against an image size.
 *
 * Never throws. Out-of-bounds and malformed entries are collected in `warnings` and skipped; the
 * caller decides whether to log them. A result with no usable tile and no usable entity is a failure.
 */
export function parseAtlas(
  source: unknown,
  imageWidth: number,
  imageHeight: number,
): AtlasParseResult {
  const warnings: string[] = [];
  if (!isRecord(source)) {
    return { ok: false, reason: "atlas.json 的顶层必须是 JSON 对象", warnings };
  }
  if (!Number.isFinite(imageWidth) || !Number.isFinite(imageHeight) || imageWidth <= 0 || imageHeight <= 0) {
    return { ok: false, reason: `图集图片尺寸无效：${imageWidth}×${imageHeight}`, warnings };
  }

  const imageValue = source["image"];
  const imageName = typeof imageValue === "string" ? imageValue.trim() : "";
  if (imageName === "") {
    return { ok: false, reason: "atlas.json 缺少非空的 image 字段", warnings };
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
    warnings.push("atlas.json 没有 tiles 对象，所有地形都会使用程序化绘制");
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
    return { ok: false, reason: "atlas.json 里没有任何可用的地形帧或实体帧", warnings };
  }

  return {
    ok: true,
    data: {
      imageName,
      tilePixels,
      tiles,
      entities,
    },
    warnings,
  };
}

function withTrailingSlash(value: string): string {
  return value.endsWith("/") ? value : `${value}/`;
}

function imageNameOf(source: unknown): string | null {
  if (!isRecord(source)) {
    return null;
  }
  const value = source["image"];
  return typeof value === "string" && value.trim() !== "" ? value.trim() : null;
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

/** Logs every parse warning in one `console.warn` call, so a bad atlas is reported once. */
function reportWarnings(warnings: readonly string[]): void {
  if (warnings.length === 0) {
    return;
  }
  console.warn(`atlas.json：忽略了 ${warnings.length} 个条目：\n${warnings.join("\n")}`);
}

/**
 * Loads `<baseUrl>/atlas.json` and its image.
 *
 * Resolves to `null` for every failure: missing files, invalid JSON, an image that does not decode,
 * or an atlas with no usable frames. It never rejects, so the caller can always fall back.
 */
export async function loadAtlas(baseUrl: string): Promise<Atlas | null> {
  try {
    const jsonUrl = new URL("atlas.json", withTrailingSlash(baseUrl));
    const response = await fetch(jsonUrl);
    if (!response.ok) {
      console.info(`没有找到 ${jsonUrl.href}（HTTP ${response.status}），使用程序化绘制`);
      return null;
    }

    const source: unknown = await response.json();
    const imageName = imageNameOf(source);
    if (imageName === null) {
      console.warn("atlas.json 缺少 image 字段，使用程序化绘制");
      return null;
    }

    const imageUrl = new URL(imageName, jsonUrl);
    const image = await loadImage(imageUrl.href);
    if (image === null) {
      console.warn(`图集图片 ${imageUrl.href} 载入失败，使用程序化绘制`);
      return null;
    }

    const parsed = parseAtlas(source, image.naturalWidth, image.naturalHeight);
    reportWarnings(parsed.warnings);
    if (!parsed.ok) {
      console.warn(`atlas.json 不可用：${parsed.reason}；使用程序化绘制`);
      return null;
    }

    const label = imageName.split("/").pop() ?? imageName;
    return { ...parsed.data, image, label };
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error);
    console.warn(`载入图集失败（${message}），使用程序化绘制`);
    return null;
  }
}
