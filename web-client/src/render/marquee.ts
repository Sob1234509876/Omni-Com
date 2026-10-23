/**
 * Pure marquee-selection geometry and selection-toggle rules.
 *
 * A left drag on empty ground draws a rectangle in canvas (CSS pixel) space. On release the rectangle
 * is converted to an inclusive world-tile range and every own unit whose drawn ground position falls
 * inside it becomes selected. This module is DOM-free so the offline check can verify the conversion
 * (including negative world coordinates) and the containment rule without a browser.
 *
 * The world is unbounded, so the conversion is ordinary signed arithmetic: screen points become world
 * pixels through the camera, then tiles through `floor`, exactly like every other tile lookup. The
 * conversion deliberately uses the camera rather than a map width or height.
 */

import type { Camera, Point, TileRange } from "./camera";

/** An axis-aligned rectangle in canvas (CSS pixel) space, with a non-negative width and height. */
export interface ScreenRect {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

/** Normalises two drag corners into a canvas-space rectangle, whichever way the drag went. */
export function screenRectBetween(start: Point, end: Point): ScreenRect {
  return {
    x: Math.min(start.x, end.x),
    y: Math.min(start.y, end.y),
    width: Math.abs(end.x - start.x),
    height: Math.abs(end.y - start.y),
  };
}

/**
 * Converts a canvas-space marquee rectangle to an inclusive world-tile range.
 *
 * Both corners go through `Camera.tileAtScreen`, whose `floor` keeps coordinates west and north of
 * the origin on the correct tile (`-0.1` is tile `-1`, never tile `0`). The right and bottom edges
 * are pulled in by one world-pixel epsilon when the rectangle has area, so a rectangle that ends
 * exactly on a tile boundary does not also select the next tile. A zero-area rectangle still selects
 * the single tile under the pointer, which is what makes the smallest drag behave like a click.
 */
export function marqueeTileRange(camera: Camera, rect: ScreenRect): TileRange {
  const left = rect.x;
  const top = rect.y;
  const right = rect.x + Math.max(0, rect.width);
  const bottom = rect.y + Math.max(0, rect.height);
  const edge = 1e-6;
  const topLeft = camera.tileAtScreen(left, top);
  const bottomRight = camera.tileAtScreen(
    rect.width > 0 ? right - edge : right,
    rect.height > 0 ? bottom - edge : bottom,
  );
  return {
    minX: Math.min(topLeft.x, bottomRight.x),
    maxX: Math.max(topLeft.x, bottomRight.x),
    minY: Math.min(topLeft.y, bottomRight.y),
    maxY: Math.max(topLeft.y, bottomRight.y),
  };
}

/**
 * Shift-click add/remove for a single unit.
 *
 * The current order is preserved on removal and the id is appended on addition, so the selection
 * stays stable across a sequence of shift-clicks. An id is never duplicated.
 */
export function toggleSelection(selected: readonly number[], id: number): number[] {
  return selected.includes(id)
    ? selected.filter((current) => current !== id)
    : [...selected, id];
}
