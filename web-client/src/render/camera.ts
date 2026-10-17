/**
 * Viewport math for the tile world.
 *
 * All screen coordinates are CSS pixels; the renderer applies `devicePixelRatio` itself. World
 * positions are unzoomed pixels: tile `(x, y)` starts at `(x * tileSize, y * tileSize)`, matching the
 * engine's `TileRect` (inclusive, x to the right, y down, origin top left).
 *
 * The class is a pure state machine: it never touches the DOM, so the Node check can exercise it.
 */

export const MIN_ZOOM = 0.5;
export const MAX_ZOOM = 2.5;

/** A point in screen (CSS px) or world (unzoomed px) space, depending on the method. */
export interface Point {
  readonly x: number;
  readonly y: number;
}

/** One integer tile coordinate. */
export interface TileCoord {
  readonly x: number;
  readonly y: number;
}

/** Inclusive tile rectangle, like the engine's `TileRect`. */
export interface TileRange {
  readonly minX: number;
  readonly minY: number;
  readonly maxX: number;
  readonly maxY: number;
}

function clampNumber(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

function clampZoom(value: number): number {
  return clampNumber(value, MIN_ZOOM, MAX_ZOOM);
}

export class Camera {
  readonly worldWidthTiles: number;
  readonly worldHeightTiles: number;
  readonly tileSize: number;
  readonly worldWidthPx: number;
  readonly worldHeightPx: number;

  private viewportWidthValue = 1;
  private viewportHeightValue = 1;
  private readonly defaultZoom: number;
  private zoomValue: number;
  private centerXValue: number;
  private centerYValue: number;

  constructor(worldWidthTiles: number, worldHeightTiles: number, tileSize: number, zoom = 1) {
    if (!Number.isFinite(worldWidthTiles) || worldWidthTiles <= 0) {
      throw new Error(`地图宽度必须是正数，当前是 ${String(worldWidthTiles)}`);
    }
    if (!Number.isFinite(worldHeightTiles) || worldHeightTiles <= 0) {
      throw new Error(`地图高度必须是正数，当前是 ${String(worldHeightTiles)}`);
    }
    if (!Number.isFinite(tileSize) || tileSize <= 0) {
      throw new Error(`瓦片像素必须是正数，当前是 ${String(tileSize)}`);
    }
    this.worldWidthTiles = Math.max(1, Math.floor(worldWidthTiles));
    this.worldHeightTiles = Math.max(1, Math.floor(worldHeightTiles));
    this.tileSize = tileSize;
    this.worldWidthPx = this.worldWidthTiles * tileSize;
    this.worldHeightPx = this.worldHeightTiles * tileSize;
    this.defaultZoom = clampZoom(Number.isFinite(zoom) ? zoom : 1);
    this.zoomValue = this.defaultZoom;
    this.centerXValue = this.worldWidthPx / 2;
    this.centerYValue = this.worldHeightPx / 2;
    this.clamp();
  }

  /** Inclusive map width in tiles. */
  get worldWidth(): number {
    return this.worldWidthTiles;
  }

  /** Inclusive map height in tiles. */
  get worldHeight(): number {
    return this.worldHeightTiles;
  }

  /** Canvas width in CSS pixels. */
  get viewportWidth(): number {
    return this.viewportWidthValue;
  }

  /** Canvas height in CSS pixels. */
  get viewportHeight(): number {
    return this.viewportHeightValue;
  }

  /** Current zoom, always inside [0.5, 2.5]. */
  get zoom(): number {
    return this.zoomValue;
  }

  set zoom(value: number) {
    if (!Number.isFinite(value)) {
      return;
    }
    this.zoomValue = clampZoom(value);
    this.clamp();
  }

  /** World pixel of the viewport centre. */
  get centerX(): number {
    return this.centerXValue;
  }

  /** World pixel of the viewport centre. */
  get centerY(): number {
    return this.centerYValue;
  }

  /** Pixels one tile covers on screen at the current zoom. */
  get effectiveTileSize(): number {
    return this.tileSize * this.zoomValue;
  }

  /** Tells the camera how large the canvas is, in CSS pixels. */
  setViewport(width: number, height: number): void {
    this.viewportWidthValue = Number.isFinite(width) ? Math.max(1, width) : 1;
    this.viewportHeightValue = Number.isFinite(height) ? Math.max(1, height) : 1;
    this.clamp();
  }

  /** Centres the view on a world pixel position, then clamps it. */
  setCenter(worldX: number, worldY: number): void {
    if (!Number.isFinite(worldX) || !Number.isFinite(worldY)) {
      return;
    }
    this.centerXValue = worldX;
    this.centerYValue = worldY;
    this.clamp();
  }

  /** Converts a world pixel to a screen (CSS) pixel. */
  worldToScreen(worldX: number, worldY: number): Point {
    return {
      x: (worldX - this.centerXValue) * this.zoomValue + this.viewportWidthValue / 2,
      y: (worldY - this.centerYValue) * this.zoomValue + this.viewportHeightValue / 2,
    };
  }

  /** Converts a screen (CSS) pixel to a world pixel. */
  screenToWorld(screenX: number, screenY: number): Point {
    return {
      x: (screenX - this.viewportWidthValue / 2) / this.zoomValue + this.centerXValue,
      y: (screenY - this.viewportHeightValue / 2) / this.zoomValue + this.centerYValue,
    };
  }

  /** Tile under a screen point; the caller decides whether it is inside the map. */
  tileAtScreen(screenX: number, screenY: number): TileCoord {
    const world = this.screenToWorld(screenX, screenY);
    return {
      x: Math.floor(world.x / this.tileSize),
      y: Math.floor(world.y / this.tileSize),
    };
  }

  /**
   * Inclusive rectangle of tiles that may be visible.
   *
   * The range is clamped to the map, so it is always safe to index. When the whole map is smaller
   * than the viewport the range is the whole map.
   */
  visibleTileRange(): TileRange {
    const topLeft = this.screenToWorld(0, 0);
    const bottomRight = this.screenToWorld(this.viewportWidthValue, this.viewportHeightValue);
    const maximumX = this.worldWidthTiles - 1;
    const maximumY = this.worldHeightTiles - 1;
    const edge = 1e-6;
    return {
      minX: clampNumber(Math.floor(topLeft.x / this.tileSize), 0, maximumX),
      maxX: clampNumber(Math.floor((bottomRight.x - edge) / this.tileSize), 0, maximumX),
      minY: clampNumber(Math.floor(topLeft.y / this.tileSize), 0, maximumY),
      maxY: clampNumber(Math.floor((bottomRight.y - edge) / this.tileSize), 0, maximumY),
    };
  }

  /**
   * Moves the camera by a screen-pixel delta.
   *
   * A positive `dx` moves the camera right, so the world appears to move left. Drag-to-pan therefore
   * passes the negated pointer delta.
   */
  pan(dxPx: number, dyPx: number): void {
    if (!Number.isFinite(dxPx) || !Number.isFinite(dyPx)) {
      return;
    }
    this.centerXValue += dxPx / this.zoomValue;
    this.centerYValue += dyPx / this.zoomValue;
    this.clamp();
  }

  /**
   * Zooms by `factor` while keeping the world point under the cursor fixed.
   *
   * Near a map edge `clamp()` wins over the anchor: the view may not leave the map, so the point can
   * shift by a few pixels there.
   */
  zoomAt(screenX: number, screenY: number, factor: number): void {
    if (!Number.isFinite(factor) || factor <= 0) {
      return;
    }
    const before = this.screenToWorld(screenX, screenY);
    const nextZoom = clampZoom(this.zoomValue * factor);
    if (nextZoom === this.zoomValue) {
      return;
    }
    this.zoomValue = nextZoom;
    const after = this.screenToWorld(screenX, screenY);
    this.centerXValue += before.x - after.x;
    this.centerYValue += before.y - after.y;
    this.clamp();
  }

  /** Keeps the viewport inside the map; a map smaller than the viewport stays centred. */
  clamp(): void {
    const halfWidth = this.viewportWidthValue / (2 * this.zoomValue);
    const halfHeight = this.viewportHeightValue / (2 * this.zoomValue);
    if (halfWidth * 2 >= this.worldWidthPx) {
      this.centerXValue = this.worldWidthPx / 2;
    } else {
      this.centerXValue = clampNumber(this.centerXValue, halfWidth, this.worldWidthPx - halfWidth);
    }
    if (halfHeight * 2 >= this.worldHeightPx) {
      this.centerYValue = this.worldHeightPx / 2;
    } else {
      this.centerYValue = clampNumber(this.centerYValue, halfHeight, this.worldHeightPx - halfHeight);
    }
  }

  /** Restores the default zoom and centres the map. */
  reset(): void {
    this.zoomValue = this.defaultZoom;
    this.centerXValue = this.worldWidthPx / 2;
    this.centerYValue = this.worldHeightPx / 2;
    this.clamp();
  }
}
