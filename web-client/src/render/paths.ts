/**
 * Pure path-overlay math: the points a committed move route draws and the culling that keeps a long,
 * off-screen route cheap.
 *
 * The snapshot carries a moving unit's remaining route as absolute tile pairs (`path`) plus an optional
 * `target`. This module turns that into the polyline the renderer draws:
 *
 * - the unit's current (fractional) position is the first point, so the line starts at the sprite rather
 *   than at the centre of the tile it occupies;
 * - every snapshot point is converted to the centre of its tile;
 * - the goal tile is appended when it is not already the last point (a truncated route still ends at the
 *   target marker);
 * - consecutive duplicates are removed and the whole list is capped, so a malformed or oversized route
 *   cannot make a frame expensive.
 *
 * `pathSegments` and `pathSegmentVisible` are the drawing half: the renderer skips a segment whose
 * bounding box is entirely outside the padded canvas instead of asking Canvas to clip hundreds of
 * off-screen line segments. Everything is DOM-free, so `scripts/check-render-math.ts` exercises it under
 * Node.
 */

import type { TileCoord } from "./camera.ts";
import type { EntityView } from "./view.ts";
import { MAX_SNAPSHOT_PATH_POINTS } from "./view.ts";

/** One point of a drawn path, in absolute world tiles; fractional for the unit's own position. */
export interface PathPoint {
  readonly x: number;
  readonly y: number;
}

/** A straight piece of a path. */
export interface PathSegment {
  readonly from: PathPoint;
  readonly to: PathPoint;
}

/** Colour of the committed and pending route lines; light grey, achromatic chrome on canvas. */
export const PATH_COLOR = "rgba(214, 214, 214, 0.9)";

/** Dash pattern of the route line, in CSS pixels. */
export const PATH_DASH: readonly number[] = [5, 4];

/** Radius of the target dot relative to one tile, clamped by the renderer to at least two pixels. */
export const PATH_TARGET_DOT_RATIO = 0.11;

/** Half-size of a pending-waypoint cross relative to one tile. */
export const PENDING_CROSS_RATIO = 0.17;

/** Whether a point is a whole world tile. */
function isTilePoint(point: TileCoord): boolean {
  return Number.isInteger(point.x) && Number.isInteger(point.y);
}

/**
 * The points of one entity's committed path, or an empty list when it has none.
 *
 * The snapshot path is assumed validated (`TileCoord[]`, at most the snapshot cap); this function still
 * drops non-integer points and caps the result, because a caller may build a view by hand. The unit's
 * own position is always included first, so a route whose first waypoint is behind the unit still reads
 * from the sprite. The goal tile is appended last when provided.
 *
 * @param entity a view carrying an optional `path` / `pathTarget`
 * @param limit  maximum number of returned points; defaults to the snapshot cap
 */
export function pathPointsFor(
  entity: EntityView,
  limit: number = MAX_SNAPSHOT_PATH_POINTS,
): PathPoint[] {
  const rawPath = entity.path;
  if (rawPath === undefined || rawPath.length === 0) {
    return [];
  }
  const maximum = Number.isFinite(limit) ? Math.max(0, Math.trunc(limit)) : MAX_SNAPSHOT_PATH_POINTS;
  if (maximum === 0) {
    return [];
  }
  const points: PathPoint[] = [];
  const append = (x: number, y: number): void => {
    if (!Number.isFinite(x) || !Number.isFinite(y)) {
      return;
    }
    const last = points[points.length - 1];
    if (last !== undefined && last.x === x && last.y === y) {
      return;
    }
    points.push({ x, y });
  };
  append(entity.x, entity.y);
  for (const tile of rawPath) {
    if (!isTilePoint(tile)) {
      continue;
    }
    append(tile.x + 0.5, tile.y + 0.5);
    if (points.length >= maximum) {
      break;
    }
  }
  const target = entity.pathTarget;
  if (target !== undefined && isTilePoint(target) && points.length < maximum) {
    append(target.x + 0.5, target.y + 0.5);
  }
  return points.slice(0, maximum);
}

/** Every consecutive pair of a point list. A list of fewer than two points has no segments. */
export function pathSegments(points: readonly PathPoint[]): PathSegment[] {
  const segments: PathSegment[] = [];
  for (let index = 1; index < points.length; index += 1) {
    const from = points[index - 1];
    const to = points[index];
    if (from === undefined || to === undefined) {
      continue;
    }
    segments.push({ from, to });
  }
  return segments;
}

/** A screen-space rectangle; the renderer passes its canvas box with a padding margin. */
export interface ScreenBounds {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

/**
 * Whether a screen-space segment can be seen inside a padded rectangle.
 *
 * The test is the segment's bounding box against the rectangle, which is deliberately conservative:
 * a diagonal segment whose box overlaps but whose line misses the rectangle is drawn (Canvas clips it),
 * while a segment far outside is skipped. That is the cheap cull a 64 point route needs.
 */
export function pathSegmentVisible(from: PathPoint, to: PathPoint, bounds: ScreenBounds): boolean {
  if (
    !Number.isFinite(from.x) ||
    !Number.isFinite(from.y) ||
    !Number.isFinite(to.x) ||
    !Number.isFinite(to.y) ||
    !Number.isFinite(bounds.x) ||
    !Number.isFinite(bounds.y) ||
    !Number.isFinite(bounds.width) ||
    !Number.isFinite(bounds.height)
  ) {
    return false;
  }
  const left = Math.min(from.x, to.x);
  const right = Math.max(from.x, to.x);
  const top = Math.min(from.y, to.y);
  const bottom = Math.max(from.y, to.y);
  return (
    right >= bounds.x &&
    left <= bounds.x + bounds.width &&
    bottom >= bounds.y &&
    top <= bounds.y + bounds.height
  );
}

/** The points of a pending (uncommitted) waypoint route, in tile centres. */
export function pendingRoutePoints(tiles: readonly TileCoord[]): PathPoint[] {
  const points: PathPoint[] = [];
  const maximum = MAX_SNAPSHOT_PATH_POINTS;
  for (const tile of tiles) {
    if (!isTilePoint(tile)) {
      continue;
    }
    points.push({ x: tile.x + 0.5, y: tile.y + 0.5 });
    if (points.length >= maximum) {
      break;
    }
  }
  return points;
}
