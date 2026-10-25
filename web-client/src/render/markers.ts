/**
 * "Bring me home" and the personal, client-only markers - pure, DOM-free rules.
 *
 * <p>The owner's report was that he loses his own base while playing ("视野跑丢"): the camera can be
 * panned anywhere in the unbounded world, and nothing in the client answered "which way is my base".
 * The snapshot already publishes every player's starting tile as the optional `anchor` pair (the same
 * field the enemy line uses), so the local player's own row is the one honest answer: this module looks
 * up **my** row, formats the readout, and gives the click/hotkey its camera target.
 *
 * <p>Markers are a second, even simpler aid: a place the owner pinned himself. They are **personal and
 * client only**: nothing is sent to the server, nothing is stored in `localStorage`, no other player can
 * see them, and they are ordinary session state that disappears on reload. They are numbered 1..n in
 * placement order, capped at {@link MAX_MARKERS}, and a tile that already carries a marker is ignored
 * (the caller reports that on the status line).
 *
 * <p>The compass sector comes from {@link compassSector} in `enemy.ts`, not from a copy: the enemy line
 * and the home line must use exactly the same eight words. The edge-arrow geometry comes from
 * `minimap.ts` (`minimapEdgeGeometry`), so the red enemy arrow, the white home arrow and a marker's white
 * numbered arrow are one ray calculation with three decorations. Everything here is pure so
 * `scripts/check-render-math.ts` can pin the cap, the duplicate and nearest-only rules, the formats and
 * the camera target.
 */

import type { Camera, Point, TileCoord } from "./camera.ts";
import {
  anchorCameraTarget,
  compassSector,
  tileDistance,
  type CompassSector,
} from "./enemy.ts";
import {
  MINIMAP_PANEL_SIZE,
  minimapEdgeGeometry,
  type MinimapEdgeGeometry,
} from "./minimap.ts";
import { tileInWindow, type PlayerView, type WorldView } from "./view.ts";

// ---------------------------------------------------------------------------------------------
// The local player's home anchor
// ---------------------------------------------------------------------------------------------

/**
 * The status-line text when there is no home anchor to jump to.
 *
 * <p>This is the exact wording the page prints after `H` / `基地`: the feature says so instead of doing
 * nothing silently. Both causes are named because they are indistinguishable from the client: a snapshot
 * from an older server carries no `anchor` field at all, and a page that has not logged in has no own
 * player row to look in.
 */
export const HOME_ANCHOR_UNKNOWN_TEXT =
  "我方基地未知：快照没有你的 anchor（未登录或旧服务端）";

/**
 * The absolute anchor tile of **the local player's own row**, or `null` when it cannot be known.
 *
 * <p>Only a row whose `id` equals `myPlayerId` is read. That is deliberately not the ownership helper:
 * `ownershipOf` maps the host's sample-scene player 1 to `neutral` for a human, so a "mine" filter would
 * be right too, but the exact id is the narrower and clearer statement - the home anchor is *this*
 * account's starting tile, never a nearby ally's. An absent `players` array, a null id, a row without
 * `anchor`, and a row for somebody else all answer `null`.
 *
 * @param players the snapshot's player rows, or `undefined` for an older snapshot / the demo world
 * @param myPlayerId the local engine player id, or `null` before LOGIN / WHOAMI
 * @return my anchor tile, or `null` when it is unknown
 */
export function ownAnchorOf(
  players: readonly PlayerView[] | undefined,
  myPlayerId: number | null,
): TileCoord | null {
  if (players === undefined || myPlayerId === null || !Number.isFinite(myPlayerId)) {
    return null;
  }
  for (const player of players) {
    if (player.id === myPlayerId && player.anchor !== undefined) {
      return player.anchor;
    }
  }
  return null;
}

/** The local player's base anchor, expressed relative to the camera and the loaded window. */
export interface HomeAnchorEntry {
  /** Absolute anchor tile. */
  readonly anchor: TileCoord;
  /** Rounded Euclidean distance from the camera centre, in tiles. */
  readonly distance: number;
  /** Compass sector from the camera centre to the anchor; `null` when both are the same tile. */
  readonly sector: CompassSector | null;
  /** Whether the anchor tile lies inside the loaded window, which the line then says instead. */
  readonly visible: boolean;
}

/**
 * The home readout for the current camera and window, or `null` when my anchor is unknown.
 *
 * <p>`visible` is the same `tileInWindow` rule the renderer, the picker and the enemy anchor arrow use:
 * the minimap can only draw the loaded window, so "可见" is true exactly when a marker on the minimap
 * would already answer "where". A zero delta has no compass sector (`compassSector` answers `null`) and
 * the formatter then prints the distance without a direction.
 */
export function homeAnchorEntry(
  world: WorldView,
  myPlayerId: number | null,
  centre: TileCoord,
): HomeAnchorEntry | null {
  const anchor = ownAnchorOf(world.players, myPlayerId);
  if (anchor === null) {
    return null;
  }
  return {
    anchor,
    distance: tileDistance(centre, anchor),
    sector: compassSector(anchor.x - centre.x, anchor.y - centre.y),
    visible: tileInWindow(world, anchor.x, anchor.y),
  };
}

/**
 * The top-chrome home line: `我方基地：可见` inside the loaded window, otherwise
 * `我方基地：距 216 格，东南` with the same eight-sector vocabulary as the enemy line.
 *
 * <p>"可见" is deliberately not a distance: when the base is inside the window the minimap marker
 * already points at it, and the number would only repeat what the map shows. A zero delta (the camera
 * is exactly on the anchor) has no direction, so only the distance is written.
 */
export function formatHomeLine(entry: HomeAnchorEntry): string {
  if (entry.visible) {
    return "我方基地：可见";
  }
  if (entry.sector === null) {
    return `我方基地：距 ${entry.distance} 格`;
  }
  return `我方基地：距 ${entry.distance} 格，${entry.sector}`;
}

/**
 * The camera centre a home jump should use, in world pixels.
 *
 * <p>It is exactly {@link anchorCameraTarget}: the anchor is a tile, so the camera centres on that tile's
 * middle and the result is clamped to the camera's numeric safety bound - the same rule the enemy line's
 * click and a minimap click already use. Kept as a named function so the check can pin "the home jump and
 * the enemy jump are the same arithmetic".
 */
export function homeCameraTarget(anchor: TileCoord, tileSize: number): Point {
  return anchorCameraTarget(anchor, tileSize);
}

// ---------------------------------------------------------------------------------------------
// Personal markers
// ---------------------------------------------------------------------------------------------

/** Hard cap on the personal marker list; the 13th placement is refused, not queued. */
export const MAX_MARKERS = 12;

/**
 * One personal marker: its 1-based placement number and its absolute world tile.
 *
 * <p>The number is part of the model, not the array index, because the canvas, the minimap arrow and the
 * marker list all name the same marker. Placement numbers are contiguous (a duplicate never consumes a
 * number, and removal only ever drops the newest entry), so `number === index + 1` always holds.
 */
export interface Marker {
  readonly number: number;
  readonly tile: TileCoord;
}

/** The outcome of one placement attempt, with the unchanged list on every refusal path. */
export type MarkerPlacement =
  | { readonly kind: "placed"; readonly marker: Marker; readonly markers: readonly Marker[] }
  | { readonly kind: "duplicate"; readonly marker: Marker; readonly markers: readonly Marker[] }
  | { readonly kind: "full"; readonly markers: readonly Marker[] };

/**
 * Places one marker at a whole world tile.
 *
 * <p>The rules are exactly the feature's: a tile that already carries a marker is a `duplicate` (the
 * existing marker is returned so the caller can name it, and the list is not touched), the list is
 * capped at {@link MAX_MARKERS} (`full`), and otherwise the new marker becomes number `n + 1`. The tile
 * is floored first, so a fractional caller coordinate lands on the same tile the renderer and the
 * minimap draw - "duplicates within one tile" therefore means one floored tile.
 */
export function placeMarker(markers: readonly Marker[], tile: TileCoord): MarkerPlacement {
  const target = { x: Math.floor(tile.x), y: Math.floor(tile.y) };
  for (const marker of markers) {
    if (marker.tile.x === target.x && marker.tile.y === target.y) {
      return { kind: "duplicate", marker, markers };
    }
  }
  if (markers.length >= MAX_MARKERS) {
    return { kind: "full", markers };
  }
  const marker: Marker = { number: markers.length + 1, tile: target };
  return { kind: "placed", marker, markers: [...markers, marker] };
}

/** The outcome of one removal: which marker went away (or `null`) and the remaining list. */
export interface MarkerRemoval {
  readonly removed: Marker | null;
  readonly markers: readonly Marker[];
}

/**
 * Removes the most recently placed marker, which is also the highest number.
 *
 * <p>`Shift+M` is the documented removal gesture. Dropping the newest entry keeps the numbering
 * contiguous, so the list never has a hole and the next placement reuses the freed number `n`.
 */
export function removeLastMarker(markers: readonly Marker[]): MarkerRemoval {
  const removed = markers[markers.length - 1];
  if (removed === undefined) {
    return { removed: null, markers };
  }
  return { removed, markers: markers.slice(0, -1) };
}

/** Clears the whole marker list (`清除全部`); the empty list is a fresh immutable value. */
export function clearMarkers(): readonly Marker[] {
  return [];
}

/** Whether a tile already carries a marker; the caller uses it for a status hint, not for placement. */
export function markerAt(markers: readonly Marker[], tile: TileCoord): Marker | undefined {
  return markers.find((marker) => marker.tile.x === tile.x && marker.tile.y === tile.y);
}

/**
 * One marker's label: `标记 2：距 12 格，东南`, the exact string the marker list shows.
 *
 * <p>The sector is the enemy line's {@link compassSector}, so a marker and a hostile base in the same
 * direction can never be described with two different words. A marker on the camera's own tile has no
 * direction and the label then says only the distance.
 */
export function formatMarkerLabel(marker: Marker, centre: TileCoord): string {
  const distance = tileDistance(centre, marker.tile);
  const sector = compassSector(marker.tile.x - centre.x, marker.tile.y - centre.y);
  return sector === null
    ? `标记 ${marker.number}：距 ${distance} 格`
    : `标记 ${marker.number}：距 ${distance} 格，${sector}`;
}

/**
 * The camera centre a click on a marker row should use, in world pixels.
 *
 * <p>The same tile-centre rule and the same numeric clamp as the home jump and the enemy line, so every
 * "jump to a tile" shortcut in the page lands on the identical pixel for the identical tile.
 */
export function markerCameraTarget(marker: Marker, tileSize: number): Point {
  return anchorCameraTarget(marker.tile, tileSize);
}

/**
 * The nearest marker whose tile is **outside the loaded window**, or `null` when every marker is inside.
 *
 * <p>The minimap can draw only the loaded window, so an outside marker is represented by one white edge
 * arrow. Showing one arrow per outside marker would pile several arrows on the same border and make the
 * numbers unreadable, hence the rule: **only the nearest outside marker gets an arrow** (the full list
 * stays reachable from the `标记` panel, which names every marker). Distances are from the camera centre;
 * a tie keeps the earlier, lower-numbered marker because only a strictly closer marker replaces it.
 */
export function nearestOutsideMarker(
  world: WorldView,
  centre: TileCoord,
  markers: readonly Marker[],
): Marker | null {
  let nearest: Marker | null = null;
  let nearestDistance = Number.POSITIVE_INFINITY;
  for (const marker of markers) {
    if (tileInWindow(world, marker.tile.x, marker.tile.y)) {
      continue;
    }
    const distance = tileDistance(centre, marker.tile);
    if (distance < nearestDistance) {
      nearest = marker;
      nearestDistance = distance;
    }
  }
  return nearest;
}

/** One outside marker's white edge arrow: the shared geometry plus the marker number it labels. */
export interface MarkerEdgeArrow extends MinimapEdgeGeometry {
  /** The marker's placement number, drawn instead of a distance. */
  readonly number: number;
  /** The marker's absolute tile, for a caller that wants the tooltip text. */
  readonly tile: TileCoord;
}

/**
 * The white edge arrow for one marker, or `null` when the marker is inside the loaded window.
 *
 * <p>It is {@link minimapEdgeGeometry} - the same ray, inset and sector calculation as the red enemy
 * arrow - decorated with the marker number. White fill with a dark outline instead of the enemy's red
 * fill with a white outline is what keeps the two arrows apart at a glance.
 */
export function markerEdgeArrow(
  world: WorldView,
  camera: Camera,
  marker: Marker,
  panelSize = MINIMAP_PANEL_SIZE,
): MarkerEdgeArrow | null {
  const geometry = minimapEdgeGeometry(world, camera, marker.tile, panelSize);
  if (geometry === null) {
    return null;
  }
  return { ...geometry, number: marker.number, tile: marker.tile };
}

/**
 * The arrow of the nearest outside marker, or `null` when every marker is inside the window.
 *
 * <p>This is the one function the minimap calls for the white numbered arrow, so the "nearest only"
 * rule is applied in exactly one place. Inside the window a marker is a small outlined square on the
 * map itself and needs no arrow.
 */
export function nearestOutsideMarkerArrow(
  world: WorldView,
  camera: Camera,
  markers: readonly Marker[],
  panelSize = MINIMAP_PANEL_SIZE,
): MarkerEdgeArrow | null {
  const marker = nearestOutsideMarker(world, camera.centerTile(), markers);
  if (marker === null) {
    return null;
  }
  return markerEdgeArrow(world, camera, marker, panelSize);
}
