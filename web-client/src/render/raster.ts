/**
 * The sprite rasterization cache (playtester item 13, render performance).
 *
 * Every material is one SVG or PNG file, and the renderer used to `drawImage` straight from the
 * `<img>` element. For an SVG source the browser re-parses and re-rasterizes the vector on **every**
 * draw call, so a pan at 1x zoom re-rendered the whole visible terrain's vectors per frame — the
 * measured ~10 FPS in hell.
 *
 * The fix is the classic one: rasterize each source rect once per size bucket into an offscreen
 * `<canvas>` and blit the raster instead. Rasterization cost is paid once per (source, rect, bucket)
 * instead of once per tile per frame; the blit from a canvas is the same fast path a PNG atlas gets.
 *
 * Buckets: the on-screen size changes continuously with the zoom, so the requested draw size is
 * quantized to a whole-pixel bucket ({@link rasterBucket}) — at most a handful of distinct buckets per
 * source across the whole zoom range, and the fractional difference is absorbed by drawing the raster
 * at the exact requested size (the same smoothing the direct draw did).
 *
 * The cache is capped: when the entry count passes {@link RASTER_CACHE_MAX_ENTRIES} it clears whole.
 * A pan across many sources therefore cannot grow memory unboundedly, and re-rasterizing a few
 * entries after a clear is far cheaper than re-rasterizing every tile every frame.
 *
 * The bucket/key helpers are pure and DOM-free so `scripts/check-render-math.ts` can pin the
 * quantization rule; the canvas half only runs in the browser (Node checks never construct one).
 */

/** Number of cached rasters before the whole cache clears (source count × buckets stays well under this). */
export const RASTER_CACHE_MAX_ENTRIES = 512;

/** Upper bound of one bucket; a tile drawn larger than this still rasterizes at this cap. */
export const RASTER_BUCKET_MAX_PX = 320;

/**
 * Quantizes a requested on-screen size to a whole-pixel bucket.
 *
 * Zero, negative and non-finite sizes answer `0` (the caller then draws directly); everything else
 * rounds to a positive integer, clamped to {@link RASTER_BUCKET_MAX_PX} so a deeply zoomed sprite
 * cannot allocate a huge raster per bucket step.
 */
export function rasterBucket(sizePx: number): number {
  if (!Number.isFinite(sizePx) || sizePx <= 0) {
    return 0;
  }
  return Math.min(RASTER_BUCKET_MAX_PX, Math.max(1, Math.round(sizePx)));
}

/**
 * The cache key of one rasterized rect: the source's identity, its frame rect and the bucket.
 *
 * `sourceId` is a stable per-source number the caller assigns (an index into the atlas's source
 * list is ideal — it is stable for the atlas's lifetime). The key is a plain string so the cache
 * stays a plain map and the rule is pin-able offline.
 */
export function rasterCacheKey(
  sourceId: number,
  rect: { readonly x: number; readonly y: number; readonly width: number; readonly height: number },
  bucket: number,
): string {
  return `${sourceId}#${rect.x},${rect.y},${rect.width},${rect.height}@${bucket}`;
}

/**
 * The DOM half of the cache: one offscreen canvas per key. The caller supplies the stable source id
 * ({@link rasterCacheKey} documents the rule), so a replaced atlas's new image can never collide with
 * a stale raster of the old one.
 */
export class RasterCache {
  private readonly entries = new Map<string, HTMLCanvasElement>();

  /** How many rasters are currently held. */
  get size(): number {
    return this.entries.size;
  }

  /**
   * The rasterized canvas for one source rect at one bucket, rasterizing it on first request.
   *
   * Returns `null` when a canvas cannot be created (a Node-like environment, or a zero-size bucket):
   * the caller then keeps drawing straight from the source image, which is today's behaviour. The
   * raster preserves the rect's aspect at the bucket size - the tile case is square anyway and an
   * entity frame keeps its strip proportions - and the caller draws it at the exact final size.
   */
  get(
    sourceId: number,
    source: HTMLImageElement,
    rect: { readonly x: number; readonly y: number; readonly width: number; readonly height: number },
    bucket: number,
  ): HTMLCanvasElement | null {
    if (bucket <= 0 || rect.width <= 0 || rect.height <= 0) {
      return null;
    }
    const key = rasterCacheKey(sourceId, rect, bucket);
    const canvas = this.entries.get(key);
    if (canvas !== undefined) {
      return canvas;
    }
    let fresh: HTMLCanvasElement;
    try {
      fresh = document.createElement("canvas");
    } catch {
      return null;
    }
    const scale = bucket / Math.max(rect.width, rect.height);
    fresh.width = Math.max(1, Math.round(rect.width * scale));
    fresh.height = Math.max(1, Math.round(rect.height * scale));
    const context = fresh.getContext("2d");
    if (context === null) {
      return null;
    }
    context.imageSmoothingEnabled = false;
    context.drawImage(source, rect.x, rect.y, rect.width, rect.height, 0, 0, fresh.width, fresh.height);
    if (this.entries.size >= RASTER_CACHE_MAX_ENTRIES) {
      this.entries.clear();
    }
    this.entries.set(key, fresh);
    return fresh;
  }

  /** Frees every raster; the renderer calls this when the atlas is replaced. */
  clear(): void {
    this.entries.clear();
  }
}
