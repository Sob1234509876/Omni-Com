/**
 * Viewport math for the tile world.
 *
 * All screen coordinates are CSS pixels; the renderer applies `devicePixelRatio` itself. World
 * positions are unzoomed pixels: tile `(x, y)` starts at `(x * tileSize, y * tileSize)`, matching the
 * engine's `TileRect` (inclusive, x to the right, y down, origin top left).
 *
 * The world is unbounded: there is no map width or height, tiles may be at any signed coordinate, and
 * the camera starts on a caller-supplied home point and can follow the player in every direction.
 * The only limit is {@link CAMERA_SAFETY_BOUND_TILES}, a numeric guard rather than a gameplay edge.
 *
 * The class is a pure state machine: it never touches the DOM, so the Node check can exercise it.
 */

export const MIN_ZOOM = 0.5;
export const MAX_ZOOM = 2.5;

/**
 * Largest tile coordinate, in either direction, the camera centre may reach.
 *
 * The server world is unbounded, so this is deliberately *not* a map edge: it is a float-precision
 * guard. The engine keeps positions in single-precision `Vec2` values, whose spacing is already about
 * one world pixel near `2^24` (roughly 524,000 tiles at 32 px/tile); beyond that, positions would
 * quantise and the picture would jitter. Five hundred thousand tiles (16 million world pixels at
 * 32 px/tile) stays just inside that range while being about two thousand times the old 256-tile map,
 * so no realistic session can feel it. Clamping is applied to the centre in world pixels.
 */
export const CAMERA_SAFETY_BOUND_TILES = 500_000;

/** A point in screen (CSS px) or world (unzoomed px) space, depending on the method. */
export interface Point {
  readonly x: number;
  readonly y: number;
}

/** One integer tile coordinate. The world is unbounded, so either component may be negative. */
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

/** Top-left world pixel of one whole tile. */
export function tileToWorld(tile: TileCoord, tileSize: number): Point {
  return { x: tile.x * tileSize, y: tile.y * tileSize };
}

/**
 * The whole tile containing a world position.
 *
 * The inverse of {@link tileToWorld} up to the tile area: `floor` (not truncation) keeps coordinates west
 * and north of the origin on the correct tile, and a fractional centre such as `5.5` maps to tile `5`.
 */
export function worldToTile(worldX: number, worldY: number, tileSize: number): TileCoord {
  return { x: Math.floor(worldX / tileSize), y: Math.floor(worldY / tileSize) };
}

function clampNumber(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

function clampZoom(value: number): number {
  return clampNumber(value, MIN_ZOOM, MAX_ZOOM);
}

export class Camera {
  readonly tileSize: number;

  private viewportWidthValue = 1;
  private viewportHeightValue = 1;
  private readonly defaultZoom: number;
  private readonly homeXValue: number;
  private readonly homeYValue: number;
  private zoomValue: number;
  private centerXValue: number;
  private centerYValue: number;

  /**
   * @param tileSize pixels one tile covers at 1x.
   * @param zoom initial zoom, clamped to [0.5, 2.5].
   * @param homeX world pixel the camera starts on and returns to on {@link reset}.
   * @param homeY world pixel the camera starts on and returns to on {@link reset}.
   */
  constructor(tileSize: number, zoom = 1, homeX = 0, homeY = 0) {
    if (!Number.isFinite(tileSize) || tileSize <= 0) {
      throw new Error(`瓦片像素必须是正数，当前是 ${String(tileSize)}`);
    }
    this.tileSize = tileSize;
    this.defaultZoom = clampZoom(Number.isFinite(zoom) ? zoom : 1);
    this.zoomValue = this.defaultZoom;
    this.homeXValue = Number.isFinite(homeX) ? homeX : 0;
    this.homeYValue = Number.isFinite(homeY) ? homeY : 0;
    this.centerXValue = this.homeXValue;
    this.centerYValue = this.homeYValue;
    this.clamp();
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

  /** Centres the view on a world pixel position, then clamps it to the safety bound. */
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

  /** Tile under a screen point; the caller decides whether it has a loaded window. */
  tileAtScreen(screenX: number, screenY: number): TileCoord {
    const world = this.screenToWorld(screenX, screenY);
    return worldToTile(world.x, world.y, this.tileSize);
  }

  /**
   * Tile the viewport is centred on.
   *
   * This is the camera focus the snapshot streamer follows. The world is unbounded, so the result may
   * be any signed tile; `Math.floor` (not truncation) keeps coordinates west and north of the origin
   * on the correct tile.
   */
  centerTile(): TileCoord {
    return worldToTile(this.centerXValue, this.centerYValue, this.tileSize);
  }

  /**
   * Inclusive rectangle of tiles that may be visible.
   *
   * Nothing is clamped to a map: the range follows the viewport wherever it is, so it may contain
   * negative tiles. The renderer only draws terrain for tiles inside the loaded window anyway.
   */
  visibleTileRange(): TileRange {
    const topLeft = this.screenToWorld(0, 0);
    const bottomRight = this.screenToWorld(this.viewportWidthValue, this.viewportHeightValue);
    const edge = 1e-6;
    return {
      minX: Math.floor(topLeft.x / this.tileSize),
      maxX: Math.floor((bottomRight.x - edge) / this.tileSize),
      minY: Math.floor(topLeft.y / this.tileSize),
      maxY: Math.floor((bottomRight.y - edge) / this.tileSize),
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
   * At the safety bound `clamp()` wins over the anchor, exactly as a map edge used to: the camera may
   * not leave the numeric range, so the anchored point can shift there.
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

  /**
   * Keeps the camera centre inside the numeric safety bound.
   *
   * There is no map to stay inside. The bound exists only so the engine's single-precision `Vec2`
   * never reaches a magnitude where its resolution is worse than a pixel; see
   * {@link CAMERA_SAFETY_BOUND_TILES}. The viewport half-size is deliberately not part of this
   * calculation: the map view may extend past the bound, it is terrain that does not exist there.
   */
  clamp(): void {
    const limit = CAMERA_SAFETY_BOUND_TILES * this.tileSize;
    this.centerXValue = clampNumber(this.centerXValue, -limit, limit);
    this.centerYValue = clampNumber(this.centerYValue, -limit, limit);
  }

  /** Restores the default zoom and returns to the home point the constructor was given. */
  reset(): void {
    this.zoomValue = this.defaultZoom;
    this.centerXValue = this.homeXValue;
    this.centerYValue = this.homeYValue;
    this.clamp();
  }
}
