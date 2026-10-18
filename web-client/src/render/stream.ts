/**
 * Camera-driven snapshot window streaming.
 *
 * The server answers `GET /world/snapshot?cx=<tileX>&cy=<tileY>&r=<radius>` with a square window of
 * the map, its top-left tile in `originX` / `originY` and the window size in `width` / `height`. The
 * page keeps one window loaded and follows the camera: while the camera centre stays away from the
 * window edge it polls the same window for new ticks; once the centre comes within
 * {@link SNAPSHOT_EDGE_MARGIN_TILES} tiles of an edge it asks for a window centred on the camera.
 * The old window stays on screen until the new one arrives, so the picture never goes blank.
 *
 * At a map edge the server clamps the origin inwards and the window cannot follow the camera any
 * further. {@link shouldRequestWindow} compares the origin the server would answer against the
 * loaded one, so the edge margin staying true there does not cause a request every frame.
 *
 * Everything here is pure and DOM-free so `scripts/check-render-math.ts` can exercise it under Node.
 */

import type { TileCoord } from "./camera";

/** Radius the page requests, in tiles; the server answers a `2 * r` square window. */
export const SNAPSHOT_RADIUS_TILES = 32;

/** How close, in tiles, the camera may get to the window edge before a new window is requested. */
export const SNAPSHOT_EDGE_MARGIN_TILES = 8;

/** The subset of a world that streaming needs: one window and the full map it lies on. */
export interface WindowBounds {
  readonly originX: number;
  readonly originY: number;
  readonly width: number;
  readonly height: number;
  readonly mapWidth: number;
  readonly mapHeight: number;
}

/** Distance in tiles from `tile` to the nearest window edge; negative when the tile is outside. */
export function windowEdgeDistance(bounds: WindowBounds, tile: TileCoord): number {
  return Math.min(
    tile.x - bounds.originX,
    bounds.originX + bounds.width - 1 - tile.x,
    tile.y - bounds.originY,
    bounds.originY + bounds.height - 1 - tile.y,
  );
}

/** Whether the camera tile is within `margin` tiles of (or outside) the loaded window edge. */
export function shouldStreamWindow(
  bounds: WindowBounds,
  tile: TileCoord,
  margin: number = SNAPSHOT_EDGE_MARGIN_TILES,
): boolean {
  return windowEdgeDistance(bounds, tile) <= margin;
}

/** The loaded window's centre tile; used for the periodic refresh while the camera stays inside. */
export function windowCentre(bounds: WindowBounds): TileCoord {
  return {
    x: bounds.originX + Math.floor(bounds.width / 2),
    y: bounds.originY + Math.floor(bounds.height / 2),
  };
}

/**
 * The origin the server would answer for a window of `radius` centred on `centre`.
 *
 * This mirrors `WorldHost.snapshotJson(int, int, int)`: the requested centre is clamped to the map,
 * then the origin is shifted inwards so the window is always full size and entirely inside the map.
 */
export function snapshotOrigin(
  centre: TileCoord,
  radius: number,
  mapWidth: number,
  mapHeight: number,
): TileCoord {
  const size = radius * 2;
  const maxOriginX = Math.max(0, mapWidth - size);
  const maxOriginY = Math.max(0, mapHeight - size);
  return {
    x: Math.max(0, Math.min(centre.x - radius, maxOriginX)),
    y: Math.max(0, Math.min(centre.y - radius, maxOriginY)),
  };
}

/**
 * Whether a request centred on `centre` would actually move the loaded window.
 *
 * Near a map edge the server's clamp makes two different camera tiles resolve to the same origin;
 * this check is what stops the edge trigger from firing every frame there.
 */
export function windowNeedsStream(
  bounds: WindowBounds,
  centre: TileCoord,
  radius: number = SNAPSHOT_RADIUS_TILES,
): boolean {
  const target = snapshotOrigin(centre, radius, bounds.mapWidth, bounds.mapHeight);
  return target.x !== bounds.originX || target.y !== bounds.originY;
}

/** The full trigger: the camera is near an edge and the clamped target window is not loaded yet. */
export function shouldRequestWindow(
  bounds: WindowBounds,
  centre: TileCoord,
  radius: number = SNAPSHOT_RADIUS_TILES,
  margin: number = SNAPSHOT_EDGE_MARGIN_TILES,
): boolean {
  return shouldStreamWindow(bounds, centre, margin) && windowNeedsStream(bounds, centre, radius);
}

/**
 * Monotonic request ids for snapshot requests.
 *
 * Only one request is normally in flight, but a slow network can still deliver answers out of order
 * (or a future change can overlap an edge request with a poll). Every response is checked with
 * {@link accept}: the newest response wins and an older one is discarded instead of replacing a
 * newer window with stale terrain.
 */
export class SnapshotRequestTracker {
  private nextId = 1;
  private newestId = 0;

  /** Starts a request and returns its id; ids strictly increase. */
  begin(): number {
    const id = this.nextId;
    this.nextId += 1;
    this.newestId = id;
    return id;
  }

  /** Whether a response may be applied; an older response is discarded. */
  accept(id: number): boolean {
    if (!Number.isInteger(id) || id <= 0 || id < this.newestId || id >= this.nextId) {
      return false;
    }
    this.newestId = id;
    return true;
  }

  /** The newest request id started so far. */
  get latest(): number {
    return this.newestId;
  }
}
