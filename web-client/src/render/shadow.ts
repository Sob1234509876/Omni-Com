/**
 * The unloaded-chunk shadow: which chunks sit under the shade, and how the shade disperses.
 *
 * The server snapshot carries `loadedChunks`, the streamed-chunk set of the drawn dimension; a tile
 * whose chunk is absent is drawn under a translucent shadow, and once the camera keeps moving and the
 * streamer loads the chunk, the shade over its tiles fades away frame by frame. The per-chunk alphas
 * live in the renderer's map; the numbers and the approach rule here are pure so
 * `scripts/check-render-math.ts` can exercise them without a canvas.
 */

/** How many tiles one chunk spans, matching the engine's default chunk size. */
export const CHUNK_SIZE_TILES = 16;

/** The shade a fully unloaded chunk sits under. */
export const SHADOW_ALPHA = 0.72;

/** How much of the distance to the target one frame of the dispersal covers. */
export const SHADOW_FADE_STEP = 0.08;

/** `x, y` in chunk coordinates, the same units the snapshot's `loadedChunks` uses. */
export function chunkOfTile(tileX: number, tileY: number, chunkSize = CHUNK_SIZE_TILES): { x: number; y: number } {
  const size = chunkSize > 0 ? Math.floor(chunkSize) : CHUNK_SIZE_TILES;
  return { x: Math.floor(tileX / size), y: Math.floor(tileY / size) };
}

/** The set key one chunk coordinate reduces to. */
export function chunkKey(x: number, y: number): string {
  return `${x},${y}`;
}

/**
 * The shade a tile sits under this frame: fully transparent over a loaded chunk, the shadow alpha over
 * an unloaded one. An `undefined` loaded set (an older server, the demo world) keeps the world clear.
 */
export function shadowTargetAlpha(
  loadedChunks: ReadonlySet<string> | undefined,
  tileX: number,
  tileY: number,
): number {
  if (loadedChunks === undefined) {
    return 0;
  }
  const chunk = chunkOfTile(tileX, tileY);
  return loadedChunks.has(chunkKey(chunk.x, chunk.y)) ? 0 : SHADOW_ALPHA;
}

/**
 * Moves one chunk's shade toward its target by the fade step, which is what makes the shadow disperse
 * over a few frames after the chunk loads instead of snapping away.
 */
export function approachShadowAlpha(current: number, target: number, step = SHADOW_FADE_STEP): number {
  if (target > current) {
    return Math.min(target, current + step);
  }
  return Math.max(target, current - step);
}

/** One chunk's on-screen footprint, in absolute world tiles, clamped to the visible range. */
export interface ChunkSpan {
  readonly chunkX: number;
  readonly chunkY: number;
  readonly minX: number;
  readonly minY: number;
  readonly maxX: number;
  readonly maxY: number;
}

/** The minimum visible tile range the span helper accepts (the camera's `visibleTileRange`). */
export interface TileRangeLike {
  readonly minX: number;
  readonly minY: number;
  readonly maxX: number;
  readonly maxY: number;
}

/**
 * One chunk rectangle per chunk overlapping the visible tile range, in chunk order.
 *
 * This is the per-frame shadow pass's new shape (playtester item 13): instead of walking every
 * visible tile and asking "which chunk is this?" per tile, the renderer walks the handful of chunks
 * the range touches and fills **one rectangle per chunk** — the shade of a chunk is uniform, so the
 * per-tile loop collapsed into `O(visible chunks)` fill rects. The spans are clamped to the range, so
 * a chunk hanging off the visible edge only paints its visible part.
 */
export function chunkSpansInRange(
  range: TileRangeLike,
  chunkSize = CHUNK_SIZE_TILES,
): ChunkSpan[] {
  const size = chunkSize > 0 ? Math.floor(chunkSize) : CHUNK_SIZE_TILES;
  const spans: ChunkSpan[] = [];
  const firstChunkX = Math.floor(range.minX / size);
  const lastChunkX = Math.floor(range.maxX / size);
  const firstChunkY = Math.floor(range.minY / size);
  const lastChunkY = Math.floor(range.maxY / size);
  for (let chunkY = firstChunkY; chunkY <= lastChunkY; chunkY += 1) {
    for (let chunkX = firstChunkX; chunkX <= lastChunkX; chunkX += 1) {
      const minX = Math.max(range.minX, chunkX * size);
      const maxX = Math.min(range.maxX, (chunkX + 1) * size - 1);
      const minY = Math.max(range.minY, chunkY * size);
      const maxY = Math.min(range.maxY, (chunkY + 1) * size - 1);
      if (minX > maxX || minY > maxY) {
        continue;
      }
      spans.push({ chunkX, chunkY, minX, minY, maxX, maxY });
    }
  }
  return spans;
}

/** The shade one chunk sits under this frame: `0` over a loaded chunk, the shadow alpha otherwise. */
export function chunkShadowTargetAlpha(
  loadedChunks: ReadonlySet<string> | undefined,
  chunkX: number,
  chunkY: number,
): number {
  if (loadedChunks === undefined) {
    return 0;
  }
  return loadedChunks.has(chunkKey(chunkX, chunkY)) ? 0 : SHADOW_ALPHA;
}
