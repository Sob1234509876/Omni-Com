/**
 * The minimap: the loaded window at a glance, plus a click/drag shortcut for the camera.
 *
 * The panel is a fixed-size square in the bottom-left corner of the game view. It draws the loaded
 * window's terrain as one pixel per tile (an offscreen tile-sized canvas, scaled up with smoothing
 * disabled), every in-window entity as a 2x2 dot coloured by the same ownership rule as the ground
 * rings (mine blue, enemy red, neutral grey), and the camera's visible rectangle as a white 1px
 * outline. Clicking or dragging on it centres the camera on the floor tile under the pointer.
 *
 * The page chrome around the minimap stays greyscale (grey border, translucent black background); only
 * the canvas content - terrain, ownership dots, the white camera outline - carries colour, exactly as
 * the game canvas does.
 *
 * The coordinate helpers are pure and DOM-free so `scripts/check-render-math.ts` can exercise them;
 * only {@link MinimapRenderer} touches a canvas. Both the panel size and the loaded window may change
 * while the page runs, and the mapping is derived from the window origin and size every time, so the
 * mapping keeps working for negative and very large world coordinates: the panel pixel is split by the
 * scale, floored, and only then added to the signed window origin.
 */

import { CAMERA_SAFETY_BOUND_TILES, type Camera, type Point, type TileCoord } from "./camera.ts";
import { terrainColor } from "./fallback.ts";
import type { WindowBounds } from "./stream.ts";
import { entityInWindow, ownershipColor, type WorldView } from "./view.ts";

/** Side of the minimap panel in CSS pixels; the CSS in `index.html` uses the same 180px square. */
export const MINIMAP_PANEL_SIZE = 180;

/** Side of one entity dot in CSS pixels. */
export const MINIMAP_DOT_SIZE = 2;

/** How the loaded window is fitted into the square panel. */
export interface MinimapLayout {
  /** Panel CSS pixels per world tile. */
  readonly scale: number;
  /** Width of the terrain area in panel CSS pixels; the rest is letterboxing. */
  readonly contentWidth: number;
  /** Height of the terrain area in panel CSS pixels; the rest is letterboxing. */
  readonly contentHeight: number;
  /** Panel CSS pixels left of the terrain area. */
  readonly offsetX: number;
  /** Panel CSS pixels above the terrain area. */
  readonly offsetY: number;
}

/**
 * Fits a window into the square panel while preserving its aspect ratio.
 *
 * A square window (the server's usual `2 * r` window) fills the panel exactly; a wider or taller
 * window is letterboxed instead of stretched, so a tile stays square and the dots line up with the
 * terrain beneath them.
 */
export function minimapLayout(bounds: WindowBounds, panelSize = MINIMAP_PANEL_SIZE): MinimapLayout {
  const size = Number.isFinite(panelSize) ? Math.max(1, panelSize) : MINIMAP_PANEL_SIZE;
  const width = Number.isFinite(bounds.width) && bounds.width > 0 ? Math.floor(bounds.width) : 1;
  const height = Number.isFinite(bounds.height) && bounds.height > 0 ? Math.floor(bounds.height) : 1;
  const scale = size / Math.max(width, height);
  const contentWidth = width * scale;
  const contentHeight = height * scale;
  return {
    scale,
    contentWidth,
    contentHeight,
    offsetX: (size - contentWidth) / 2,
    offsetY: (size - contentHeight) / 2,
  };
}

/**
 * The absolute world tile under one panel point, or `null` outside the terrain area.
 *
 * The point is reduced by the letterbox offset, divided by the per-tile scale and **floored** before
 * the signed window origin is added. Flooring is what keeps the mapping exact west and north of zero:
 * the panel coordinates are never negative inside the content, but the tile they resolve to may be
 * (for a window whose origin is negative), and `origin + floor(offset)` is the same operation in both
 * directions. The right and bottom edges are exclusive, so a point exactly on the content edge belongs
 * to no tile instead of wrapping to the first one.
 */
export function minimapTileAt(
  bounds: WindowBounds,
  panelX: number,
  panelY: number,
  panelSize = MINIMAP_PANEL_SIZE,
): TileCoord | null {
  if (!Number.isFinite(panelX) || !Number.isFinite(panelY)) {
    return null;
  }
  const layout = minimapLayout(bounds, panelSize);
  const localX = panelX - layout.offsetX;
  const localY = panelY - layout.offsetY;
  if (localX < 0 || localY < 0 || localX >= layout.contentWidth || localY >= layout.contentHeight) {
    return null;
  }
  const width = Math.max(1, Math.floor(bounds.width));
  const height = Math.max(1, Math.floor(bounds.height));
  const tileX = Math.floor(localX / layout.scale);
  const tileY = Math.floor(localY / layout.scale);
  if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) {
    return null;
  }
  return { x: bounds.originX + tileX, y: bounds.originY + tileY };
}

/**
 * The top-left panel point of one absolute world tile; the inverse of {@link minimapTileAt}.
 *
 * The result is not clamped to the panel: the camera rectangle is often larger than the window, and
 * the caller clips the rectangle it strokes. A tile outside the window therefore gets a point outside
 * the terrain area rather than a misleading one at the edge.
 */
export function minimapPointForTile(
  bounds: WindowBounds,
  tileX: number,
  tileY: number,
  panelSize = MINIMAP_PANEL_SIZE,
): Point {
  const layout = minimapLayout(bounds, panelSize);
  return {
    x: layout.offsetX + (tileX - bounds.originX) * layout.scale,
    y: layout.offsetY + (tileY - bounds.originY) * layout.scale,
  };
}

/**
 * The world-pixel camera centre for one minimap click, or `null` outside the terrain area.
 *
 * The click selects the whole floor tile under the pointer and the camera centres on that tile's
 * middle (`(tile + 0.5) * tileSize`), so one minimap point maps to exactly one reproducible camera
 * position even for negative coordinates. The result is clamped to the camera's ±500,000-tile numeric
 * safety bound - the same bound `Camera.setCenter` applies - so a minimap click can never move the
 * camera past the range the engine can represent; for any realistic window the clamp never fires.
 */
export function minimapCameraTarget(
  bounds: WindowBounds,
  panelX: number,
  panelY: number,
  tileSize: number,
  panelSize = MINIMAP_PANEL_SIZE,
): Point | null {
  if (!Number.isFinite(tileSize) || tileSize <= 0) {
    return null;
  }
  const tile = minimapTileAt(bounds, panelX, panelY, panelSize);
  if (tile === null) {
    return null;
  }
  const limit = CAMERA_SAFETY_BOUND_TILES * tileSize;
  const clampToBound = (index: number): number =>
    Math.min(limit, Math.max(-limit, (index + 0.5) * tileSize));
  return { x: clampToBound(tile.x), y: clampToBound(tile.y) };
}

function clampNumber(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

/**
 * Draws the minimap into its own small canvas.
 *
 * The terrain image is cached by the loaded window (the tiles array plus origin and size): the page
 * rebuilds the `WorldView` every animation frame while interpolating, but the snapshot's tiles array
 * keeps its identity between polls, so the 1px-per-tile canvas is rebuilt only when the world or the
 * window really changes. The entity dots and the camera rectangle are redrawn every frame, because the
 * camera moves and the units slide even when the terrain does not.
 */
export class MinimapRenderer {
  private readonly canvas: HTMLCanvasElement;
  private readonly context: CanvasRenderingContext2D;
  private terrainCanvas: HTMLCanvasElement | null = null;
  private terrainTiles: WorldView["tiles"] | null = null;
  private terrainOriginX = 0;
  private terrainOriginY = 0;
  private terrainWidth = 0;
  private terrainHeight = 0;

  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas;
    const context = canvas.getContext("2d");
    if (context === null) {
      throw new Error("浏览器不支持 Canvas 2D，无法绘制小地图");
    }
    this.context = context;
  }

  /**
   * Sizes the backing store from the CSS box and returns the panel size in CSS pixels.
   *
   * The CSS square is 180px, but the mapping reads the real box so a media query or a browser zoom can
   * change it without desynchronising the click handling from the drawing.
   */
  private syncSize(): number {
    const rect = this.canvas.getBoundingClientRect();
    const panelSize = Math.max(1, Math.min(rect.width, rect.height));
    const devicePixelRatio = Math.max(1, window.devicePixelRatio || 1);
    const backing = Math.max(1, Math.round(panelSize * devicePixelRatio));
    if (this.canvas.width !== backing) {
      this.canvas.width = backing;
    }
    if (this.canvas.height !== backing) {
      this.canvas.height = backing;
    }
    this.context.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
    return panelSize;
  }

  /** Rebuilds the 1px-per-tile terrain canvas when the window or its terrain changed. */
  private ensureTerrain(world: WorldView): HTMLCanvasElement | null {
    if (
      this.terrainCanvas !== null &&
      this.terrainTiles === world.tiles &&
      this.terrainOriginX === world.originX &&
      this.terrainOriginY === world.originY &&
      this.terrainWidth === world.width &&
      this.terrainHeight === world.height
    ) {
      return this.terrainCanvas;
    }
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, world.width);
    canvas.height = Math.max(1, world.height);
    const context = canvas.getContext("2d");
    if (context === null) {
      return null;
    }
    for (let y = 0; y < world.height; y += 1) {
      const row = world.tiles[y];
      for (let x = 0; x < world.width; x += 1) {
        context.fillStyle = terrainColor(row?.[x] ?? "void");
        context.fillRect(x, y, 1, 1);
      }
    }
    this.terrainCanvas = canvas;
    this.terrainTiles = world.tiles;
    this.terrainOriginX = world.originX;
    this.terrainOriginY = world.originY;
    this.terrainWidth = world.width;
    this.terrainHeight = world.height;
    return canvas;
  }

  /** Draws one minimap frame; safe to call every animation frame. */
  render(world: WorldView, camera: Camera, myPlayerId: number | null): void {
    const rect = this.canvas.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
      return;
    }
    const panelSize = this.syncSize();
    const context = this.context;
    const layout = minimapLayout(world, panelSize);
    context.clearRect(0, 0, panelSize, panelSize);

    const terrain = this.ensureTerrain(world);
    if (terrain !== null) {
      context.imageSmoothingEnabled = false;
      context.drawImage(
        terrain,
        0,
        0,
        Math.max(1, world.width),
        Math.max(1, world.height),
        layout.offsetX,
        layout.offsetY,
        layout.contentWidth,
        layout.contentHeight,
      );
    }

    // Entities: one 2x2 dot per in-window entity, coloured by the same ownership rule as the rings.
    const contentRight = layout.offsetX + layout.contentWidth;
    const contentBottom = layout.offsetY + layout.contentHeight;
    for (const entity of world.entities) {
      if (!entityInWindow(world, entity)) {
        continue;
      }
      const point = minimapPointForTile(
        world,
        Math.floor(entity.x),
        Math.floor(entity.y),
        panelSize,
      );
      // Snap the 2x2 dot to whole CSS pixels so it stays a crisp square instead of an antialiased blob.
      const dotX = Math.min(
        contentRight - MINIMAP_DOT_SIZE,
        Math.max(layout.offsetX, Math.round(point.x + layout.scale / 2 - MINIMAP_DOT_SIZE / 2)),
      );
      const dotY = Math.min(
        contentBottom - MINIMAP_DOT_SIZE,
        Math.max(layout.offsetY, Math.round(point.y + layout.scale / 2 - MINIMAP_DOT_SIZE / 2)),
      );
      context.fillStyle = ownershipColor(entity.playerId, myPlayerId);
      context.fillRect(dotX, dotY, MINIMAP_DOT_SIZE, MINIMAP_DOT_SIZE);
    }

    // The camera rectangle: a white 1px outline, clipped to the terrain area. It may be larger than
    // the loaded window; clipping keeps the outline on the panel instead of across the letterbox.
    const range = camera.visibleTileRange();
    const topLeft = minimapPointForTile(world, range.minX, range.minY, panelSize);
    const bottomRight = minimapPointForTile(world, range.maxX + 1, range.maxY + 1, panelSize);
    const left = clampNumber(topLeft.x, layout.offsetX, contentRight);
    const top = clampNumber(topLeft.y, layout.offsetY, contentBottom);
    const right = clampNumber(bottomRight.x, layout.offsetX, contentRight);
    const bottom = clampNumber(bottomRight.y, layout.offsetY, contentBottom);
    context.strokeStyle = "#ffffff";
    context.lineWidth = 1;
    context.strokeRect(
      left + 0.5,
      top + 0.5,
      Math.max(0, right - left - 1),
      Math.max(0, bottom - top - 1),
    );
  }
}
