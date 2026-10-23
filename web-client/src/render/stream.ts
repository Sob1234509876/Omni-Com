/**
 * Camera-driven snapshot window streaming.
 *
 * The server answers `GET /world/snapshot?cx=<tileX>&cy=<tileY>&r=<radius>` with a square window of
 * its **unbounded** world, its top-left tile in `originX` / `originY` and the window size in
 * `width` / `height`. The page keeps one window loaded and follows the camera: while the camera
 * centre stays away from the window edge it polls the same window for new ticks; once the centre
 * comes within {@link SNAPSHOT_EDGE_MARGIN_TILES} tiles of an edge it asks for a window centred on the
 * camera. The old window stays on screen until the new one arrives, so the picture never goes blank.
 *
 * There is no map edge to stop at. The server uses the requested centre exactly as sent, negative or
 * huge, and returns the origin `centre - radius`; the comparison in {@link windowNeedsStream} is
 * therefore mostly a duplicate-request guard (and stays correct against a server that still clamps).
 *
 * Everything here is pure and DOM-free so `scripts/check-render-math.ts` can exercise it under Node.
 */

import type { TileCoord } from "./camera";

/** Radius the page requests, in tiles; the server answers a `2 * r` square window. */
export const SNAPSHOT_RADIUS_TILES = 32;

/** How close, in tiles, the camera may get to the window edge before a new window is requested. */
export const SNAPSHOT_EDGE_MARGIN_TILES = 8;

/** The subset of a world that streaming needs: one loaded window, with signed origin and integer size. */
export interface WindowBounds {
  readonly originX: number;
  readonly originY: number;
  readonly width: number;
  readonly height: number;
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
 * The origin the server answers for a window of `radius` centred on `centre`.
 *
 * This mirrors `WorldHost.snapshotJson(int, int, int)`: it uses any signed centre exactly as sent and
 * places the origin at `centre - radius`, with no map to clamp against. Negative and very large
 * centres therefore produce negative and very large origins.
 */
export function snapshotOrigin(centre: TileCoord, radius: number): TileCoord {
  return { x: centre.x - radius, y: centre.y - radius };
}

/**
 * Whether a request centred on `centre` would actually move the loaded window.
 *
 * With an unbounded server two different centres normally resolve to two different origins, so this
 * is true near an edge. It stays false when the loaded window already starts at the origin this
 * centre would request (the camera sitting exactly on the window centre), which is the invariant the
 * old map-clamped streamer relied on and the check still proves for signed centres.
 */
export function windowNeedsStream(
  bounds: WindowBounds,
  centre: TileCoord,
  radius: number = SNAPSHOT_RADIUS_TILES,
): boolean {
  const target = snapshotOrigin(centre, radius);
  return target.x !== bounds.originX || target.y !== bounds.originY;
}

/** The full trigger: the camera is near an edge and the target window is not loaded yet. */
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
