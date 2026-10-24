/**
 * Build-placement validity: the pure rules behind the ghost that marks an armed BUILD site.
 *
 * The server is the only judge of a BUILD (ownership, the served content, terrain, occupancy and the
 * reach path), but a click should not have to travel to the server to learn that a site is impossible.
 * While a building path is armed the page computes this preview for the tile under the cursor and
 * paints one state per footprint tile:
 *
 * - **legal** - a green outline and a 25% fill;
 * - **blocked** - a red outline and fill, because a footprint tile is outside the loaded window, is
 *   already covered by an entity's footprint, or is not buildable terrain in the snapshot;
 * - **out of reach** - an amber outline and fill, because the selected engineer is too far away.
 *
 * The whole site combines the three in that order: `blocked` beats `out of reach` beats `legal`. An
 * illegal site never sends anything: the click keeps the mode armed and reports the reason instead.
 *
 * Colours live here, not in the page chrome, because the ghost is canvas content - like the ownership
 * rings, which are also coloured while the panels stay greyscale.
 *
 * Everything here is pure and DOM-free so `scripts/check-render-math.ts` can exercise it under Node.
 */

import type { TileCoord } from "./camera.ts";
import {
  kindName,
  terrainAt,
  tileInWindow,
  type EntityView,
  type TerrainId,
  type WorldView,
} from "./view.ts";

/** A building's footprint in tiles, with its top-left tile as the origin. */
export interface Footprint {
  readonly width: number;
  readonly height: number;
}

/**
 * The server's documented build reach: `CommandService.MAX_BUILD_PATH_TILES = 16f`.
 *
 * The server's rule is a *path* rule - the builder must be able to walk to the footprint's one-tile
 * approach ring within 16 tiles - while the client only has the snapshot and no path finder. The
 * client therefore approximates it with the Chebyshev gap between the engineer's tile and the
 * footprint rectangle: a site 16 tiles away at most is treated as reachable, 17 is not. The
 * approximation can accept a site behind a wall, but it never blocks a site the server would accept
 * for distance alone; the server still has the last word.
 */
export const MAX_BUILD_REACH_TILES = 16;

/**
 * The footprint used when the client does not know the armed building's size.
 *
 * The client cannot ask the server for a building type's footprint before placing it: the snapshot
 * only reports `w` / `h` for *existing* entities. Every building in the classic catalogue is 3x3, so
 * an unknown kind is assumed to be 3x3 as well; a plugin building with another size would draw a ghost
 * slightly too large or too small, never a wrong click (the server still validates the real footprint).
 */
export const DEFAULT_BUILDING_FOOTPRINT: Footprint = { width: 3, height: 3 };

/**
 * Footprints the client knows, keyed by content path.
 *
 * These mirror the classic catalogue (`ClassicContent`), where the armory tiers, the mine tiers, the
 * lumber-camp tiers and the drop-off warehouse all declare `.size(3, 3)`. Keep this table in step with
 * the catalogue; a path that is missing here simply uses {@link DEFAULT_BUILDING_FOOTPRINT}.
 */
const BUILDING_FOOTPRINTS: ReadonlyMap<string, Footprint> = new Map<string, Footprint>([
  ["building.armory.t1", { width: 3, height: 3 }],
  ["building.armory.t2", { width: 3, height: 3 }],
  ["building.mine.t1", { width: 3, height: 3 }],
  ["building.mine.t2", { width: 3, height: 3 }],
  ["building.mine.t3", { width: 3, height: 3 }],
  ["building.lumbercamp.t1", { width: 3, height: 3 }],
  ["building.lumbercamp.t2", { width: 3, height: 3 }],
  ["building.lumbercamp.t3", { width: 3, height: 3 }],
  ["building.warehouse", { width: 3, height: 3 }],
]);

/**
 * The footprint of one building kind.
 *
 * A namespace prefix (`oc:building.warehouse`) is stripped first, so a plugin that namespaces the
 * classic content still hits the table. An unknown kind answers {@link DEFAULT_BUILDING_FOOTPRINT}.
 */
export function footprintForKind(kind: string): Footprint {
  if (typeof kind !== "string" || kind.trim() === "") {
    return DEFAULT_BUILDING_FOOTPRINT;
  }
  return (
    BUILDING_FOOTPRINTS.get(kind) ??
    BUILDING_FOOTPRINTS.get(kindName(kind)) ??
    DEFAULT_BUILDING_FOOTPRINT
  );
}

/**
 * Every tile of a footprint, row-major from the origin.
 *
 * A non-positive size has no tiles; the caller never passes one, but returning an empty list keeps the
 * helper total instead of throwing inside an animation frame.
 */
export function footprintTiles(origin: TileCoord, footprint: Footprint): TileCoord[] {
  if (footprint.width <= 0 || footprint.height <= 0) {
    return [];
  }
  const tiles: TileCoord[] = [];
  for (let offsetY = 0; offsetY < footprint.height; offsetY += 1) {
    for (let offsetX = 0; offsetX < footprint.width; offsetX += 1) {
      tiles.push({ x: origin.x + offsetX, y: origin.y + offsetY });
    }
  }
  return tiles;
}

/**
 * The footprint of an entity that already exists.
 *
 * A snapshot reports `w` / `h` for a building and `1x1` for a unit; an older snapshot and the demo
 * world report neither. Without a reported footprint the client only knows the entity's own ground
 * tile, so that single tile is used - it never invents a building-sized obstruction.
 */
export function entityFootprint(entity: EntityView): Footprint {
  return entity.footprint ?? { width: 1, height: 1 };
}

/** The whole tile an entity stands on; floors fractional positions so negative coordinates stay right. */
export function entityTile(entity: EntityView): TileCoord {
  return { x: Math.floor(entity.x), y: Math.floor(entity.y) };
}

/** Whether one existing entity's footprint covers a whole world tile. */
export function entityFootprintCovers(entity: EntityView, x: number, y: number): boolean {
  const footprint = entityFootprint(entity);
  const originX = Math.floor(entity.x);
  const originY = Math.floor(entity.y);
  return (
    x >= originX &&
    y >= originY &&
    x < originX + footprint.width &&
    y < originY + footprint.height
  );
}

/**
 * Whether any entity's footprint covers a tile.
 *
 * The check is deliberately about every entity, not only buildings: the owner sees a unit or a
 * building standing in the way as red rather than as a site that looks free until the server refuses
 * it. (The server's `Dimension.canPlace` rejects overlapping *buildings*; a unit standing on the
 * footprint is a warning here, not a server rule the client is trying to reproduce exactly.)
 */
export function tileOccupiedByEntities(
  entities: readonly EntityView[],
  x: number,
  y: number,
): boolean {
  return entities.some((entity) => entityFootprintCovers(entity, x, y));
}

/**
 * Chebyshev gap in tiles between the engineer's tile and the footprint rectangle.
 *
 * A footprint the engineer stands on or beside has gap `0`; one tile further away has gap `1`. This is
 * the same lower bound the server computes before its path search
 * (`Math.max(gapX, gapY) > MAX_BUILD_PATH_TILES`), which is why it can never reject a reachable site -
 * it only knows less than the full path test.
 */
export function buildReachDistance(
  engineerTile: TileCoord,
  origin: TileCoord,
  footprint: Footprint,
): number {
  const gapX = Math.max(
    0,
    Math.max(origin.x - engineerTile.x, engineerTile.x - (origin.x + footprint.width - 1)),
  );
  const gapY = Math.max(
    0,
    Math.max(origin.y - engineerTile.y, engineerTile.y - (origin.y + footprint.height - 1)),
  );
  return Math.max(gapX, gapY);
}

/** Whether the site is within the client's 16-tile approximation of the server's reach rule. */
export function isWithinBuildReach(
  engineerTile: TileCoord,
  origin: TileCoord,
  footprint: Footprint,
): boolean {
  return buildReachDistance(engineerTile, origin, footprint) <= MAX_BUILD_REACH_TILES;
}

/**
 * Terrains the scenario content declares `buildable()`: open grass and sand.
 *
 * `water` and `rock` are impassable, `forest` and `hill` are walkable but not buildable, and `void` is
 * the engine's fallback terrain outside the served content. A terrain a plugin added is not known to
 * the client and is treated as unbuildable too: the ghost stays red instead of promising a site the
 * server might refuse. Like the reach test, this is an approximation the server overrules.
 */
export const BUILDABLE_TERRAINS: ReadonlySet<TerrainId> = new Set<TerrainId>(["grass", "sand"]);

/** Whether a snapshot terrain id is one the client treats as buildable. */
export function isBuildableTerrain(terrain: TerrainId): boolean {
  return BUILDABLE_TERRAINS.has(terrain);
}

/** One footprint tile's verdict. */
export type PlacementTileState = "legal" | "blocked" | "out-of-reach";

/** Why a site (or one footprint tile) has its state; the status line prints the matching text. */
export type PlacementReason =
  | "legal"
  | "blocked-terrain"
  | "blocked-occupied"
  | "blocked-window"
  | "out-of-reach";

/** One footprint tile with its painted state and the specific reason for it. */
export interface PlacementTile extends TileCoord {
  readonly state: PlacementTileState;
  readonly reason: PlacementReason;
}

/** The whole ghost for one armed BUILD: the site state, its reason and every footprint tile. */
export interface PlacementPreview {
  readonly state: PlacementTileState;
  readonly reason: PlacementReason;
  readonly buildingPath: string;
  readonly footprint: Footprint;
  readonly origin: TileCoord;
  readonly tiles: readonly PlacementTile[];
}

/**
 * Combines tile states into the site state, in the documented precedence order:
 * `blocked` > `out-of-reach` > `legal`.
 *
 * An empty list has no illegal tile and answers `legal`; the preview never produces one because every
 * footprint has at least one tile.
 */
export function combinePlacementStates(states: readonly PlacementTileState[]): PlacementTileState {
  let sawOutOfReach = false;
  for (const state of states) {
    if (state === "blocked") {
      return "blocked";
    }
    if (state === "out-of-reach") {
      sawOutOfReach = true;
    }
  }
  return sawOutOfReach ? "out-of-reach" : "legal";
}

/** The exact status-line text for a reason. */
const REASON_TEXT: Readonly<Record<PlacementReason, string>> = {
  legal: "可建造",
  "blocked-terrain": "地形不可建",
  "blocked-occupied": "位置被占用",
  "blocked-window": "超出已载入窗口",
  "out-of-reach": "超出工兵范围 16 格",
};

/** The status-line text for a placement reason: `可建造`, `地形不可建`, ... */
export function placementReasonText(reason: PlacementReason): string {
  return REASON_TEXT[reason];
}

/** Green fill of a legal footprint tile: 25% opacity, so the terrain stays readable under it. */
export const PLACEMENT_LEGAL_FILL = "rgba(74, 222, 128, 0.25)";
/** Green outline of a legal footprint tile. */
export const PLACEMENT_LEGAL_STROKE = "rgba(74, 222, 128, 0.95)";
/** Red fill of a blocked footprint tile. */
export const PLACEMENT_BLOCKED_FILL = "rgba(239, 68, 68, 0.38)";
/** Red outline of a blocked footprint tile. */
export const PLACEMENT_BLOCKED_STROKE = "rgba(239, 68, 68, 0.95)";
/** Amber fill of an out-of-reach footprint tile. */
export const PLACEMENT_OUT_OF_REACH_FILL = "rgba(245, 158, 11, 0.28)";
/** Amber outline of an out-of-reach footprint tile. */
export const PLACEMENT_OUT_OF_REACH_STROKE = "rgba(245, 158, 11, 0.95)";

/** Fill colour of one ghost tile: green, red or amber by state. */
export function placementFillColor(state: PlacementTileState): string {
  switch (state) {
    case "legal":
      return PLACEMENT_LEGAL_FILL;
    case "blocked":
      return PLACEMENT_BLOCKED_FILL;
    default:
      return PLACEMENT_OUT_OF_REACH_FILL;
  }
}

/** Outline colour of one ghost tile: green, red or amber by state. */
export function placementStrokeColor(state: PlacementTileState): string {
  switch (state) {
    case "legal":
      return PLACEMENT_LEGAL_STROKE;
    case "blocked":
      return PLACEMENT_BLOCKED_STROKE;
    default:
      return PLACEMENT_OUT_OF_REACH_STROKE;
  }
}

/**
 * Builds the full ghost for one armed BUILD at `origin` (the tile under the cursor, taken as the
 * footprint's top-left tile - exactly what `BUILD <id> <path> <x> <y>` sends).
 *
 * Per-tile reason precedence is window, occupancy, terrain: a tile that is not loaded cannot be judged
 * at all, an entity standing there is certain, and the terrain is the remaining test. The site reason
 * is the reason of the first blocked tile in row-major order, which makes it deterministic; if nothing
 * is blocked the site is out of reach only when the engineer is too far from every footprint tile.
 */
export function buildPlacementPreview(
  world: WorldView,
  buildingPath: string,
  origin: TileCoord,
  engineer: EntityView,
): PlacementPreview {
  const footprint = footprintForKind(buildingPath);
  const engineerTile = entityTile(engineer);
  const reachable = isWithinBuildReach(engineerTile, origin, footprint);
  const tiles: PlacementTile[] = [];
  for (const tile of footprintTiles(origin, footprint)) {
    let state: PlacementTileState;
    let reason: PlacementReason;
    if (!tileInWindow(world, tile.x, tile.y)) {
      state = "blocked";
      reason = "blocked-window";
    } else if (tileOccupiedByEntities(world.entities, tile.x, tile.y)) {
      state = "blocked";
      reason = "blocked-occupied";
    } else if (!isBuildableTerrain(terrainAt(world, tile.x, tile.y))) {
      state = "blocked";
      reason = "blocked-terrain";
    } else if (!reachable) {
      state = "out-of-reach";
      reason = "out-of-reach";
    } else {
      state = "legal";
      reason = "legal";
    }
    tiles.push({ x: tile.x, y: tile.y, state, reason });
  }

  const state = combinePlacementStates(tiles.map((tile) => tile.state));
  let reason: PlacementReason;
  if (state === "blocked") {
    reason = tiles.find((tile) => tile.state === "blocked")?.reason ?? "blocked-terrain";
  } else if (state === "out-of-reach") {
    reason = "out-of-reach";
  } else {
    reason = "legal";
  }
  return { state, reason, buildingPath, footprint, origin, tiles };
}
