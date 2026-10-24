/**
 * The client-side world model.
 *
 * `WorldView` deliberately mirrors what the server snapshot carries: one tile grid plus entities with
 * an id, a kind, a ground position and presentation flags. The demo generator is a placeholder for a
 * streamed snapshot; the renderer must not care which one it receives.
 *
 * The world is **unbounded**. A snapshot carries one window of it, so `originX` / `originY` and every
 * entity coordinate are absolute signed world coordinates; there is no map width or height anywhere in
 * the model. Negative and very large coordinates are ordinary data, not errors.
 *
 * Terrain ids are the engine's content paths (`TerrainType.key().path()`), not display names. The
 * self-check scenario defines water, sand, grass, forest, hill and rock, and the engine adds `void`
 * as its fallback terrain.
 */

import type { TileRange } from "./camera";

export type TerrainId = string;

/** Terrain path the engine uses outside a loaded window (and its fallback content). */
export const TERRAIN_VOID: TerrainId = "void";

/** Terrain paths the scenario content defines, in the order the legend uses them. */
export const TERRAIN_IDS: readonly TerrainId[] = [
  "water",
  "sand",
  "grass",
  "forest",
  "hill",
  "rock",
  "void",
];

export interface EntityView {
  /** Stable entity id; the demo uses small numbers, the server will use its own ids. */
  readonly id: number;
  /** Entity kind / content path, for example `worker` or `warehouse`. */
  readonly kind: string;
  /** Absolute world position in tiles; x grows right, y grows down, either may be negative. */
  readonly x: number;
  /** Absolute world position in tiles; larger y is closer to the viewer, either may be negative. */
  readonly y: number;
  /** Server-side owner; used to tint the fallback shapes. */
  readonly playerId?: number;
  /**
   * Optional animation frame sequence (indices into the kind's atlas row).
   *
   * A snapshot can use it to pick a clip inside a shared row, for example idle `[0, 1]` versus walk
   * `[2, 3, 4, 5]`. When omitted the whole atlas row is animated.
   */
  readonly frames?: readonly number[];
  /** Selection flag owned by the presentation layer. */
  readonly selected?: boolean;
  /** Remaining health in [0, 1]; a bar is drawn when it is below 1. */
  readonly healthRatio?: number;
  /**
   * Footprint in tiles from the snapshot's optional `w` / `h`, when the server reports one.
   *
   * A building carries its content footprint (`3×3` for a warehouse); a unit carries `1×1`. The demo
   * world and an older server omit both, so the HUD must treat a missing footprint as "unknown size".
   */
  readonly footprint?: { readonly width: number; readonly height: number };
  /**
   * Completion fraction in [0, 1] from the snapshot's optional `progress`.
   *
   * For a building this is its construction progress (`1` when finished); for a unit the server reports
   * its health fraction, because the engine has no per-unit construction counter. A missing value means
   * an older snapshot and is treated as finished.
   */
  readonly progress?: number;
  /**
   * Number of queued production jobs from the snapshot's optional `queue`, if the server reported one.
   *
   * Units and buildings without a queue report `0` (or omit the field), and the renderer then draws no
   * production marker.
   */
  readonly queue?: number;
  /** Remaining ticks of the job the building is producing, when the server reports it. */
  readonly queueTicks?: number;
}

export interface WorldView {
  /**
   * Absolute world tile of the window's top-left corner.
   *
   * The server sends a square window (`width` / `height`) of an unbounded world. Both origin values
   * are signed integers: they may be negative or very large, because there is no map to clamp them
   * into. The demo map starts at `0, 0`.
   */
  readonly originX: number;
  readonly originY: number;
  /** Window width in tiles; `tiles[0]` has exactly this many entries. */
  readonly width: number;
  /** Window height in tiles; `tiles` has exactly this many rows. */
  readonly height: number;
  /** Row-major terrain for the window: `tiles[windowY][windowX]`. */
  readonly tiles: readonly (readonly TerrainId[])[];
  readonly entities: readonly EntityView[];
  /**
   * Server player rows, oldest id first, or `undefined` when the snapshot predates the field.
   *
   * The demo world and an older server both omit it, so every reader has to tolerate its absence.
   */
  readonly players?: readonly PlayerView[];
}

/** One player row of a server snapshot: the two starting resources plus the display name. */
export interface PlayerView {
  /** Engine player id, the same number as `EntityView.playerId`. */
  readonly id: number;
  /** Display name the world bound to the account. */
  readonly name: string;
  /** Gold in the player's warehouses, as a non-negative integer. */
  readonly gold: number;
  /** Wood in the player's warehouses, as a non-negative integer. */
  readonly wood: number;
}

/** How one entity stands relative to the local player. */
export type Ownership = "mine" | "enemy" | "neutral";

/**
 * Classifies an entity's owner against the player id answered by `WHOAMI`.
 *
 * A missing `myPlayerId` (the demo world, or the moment before `LOGIN`) and a missing or non-positive
 * entity owner are deliberately `neutral`: the renderer then draws the grey ownership ring, which is
 * also what the demo world shows.
 */
export function ownershipOf(playerId: number | undefined, myPlayerId: number | null): Ownership {
  if (myPlayerId === null || playerId === undefined || !Number.isFinite(playerId) || playerId <= 0) {
    return "neutral";
  }
  return playerId === myPlayerId ? "mine" : "enemy";
}

/** The one-character HUD tag for an ownership, as shown next to the selected entity's kind. */
export function ownershipTag(ownership: Ownership): string {
  switch (ownership) {
    case "mine":
      return "我";
    case "enemy":
      return "敌";
    default:
      return "中立";
  }
}

/** Ownership ring colours: mine, enemy, and neutral / no owner / no local player. */
export const OWNERSHIP_MINE_COLOR = "#3b82f6";
export const OWNERSHIP_ENEMY_COLOR = "#ef4444";
export const OWNERSHIP_NEUTRAL_COLOR = "#9ca3af";

/**
 * The ground-ring colour for one entity's owner relative to the local player.
 *
 * This is the pure rule the renderer draws with, kept beside {@link ownershipOf} so the ring and the HUD
 * tag can never classify the same entity differently. `myPlayerId === null` (the demo world, or before
 * `WHOAMI` answers) colours every ring grey.
 */
export function ownershipColor(playerId: number | undefined, myPlayerId: number | null): string {
  switch (ownershipOf(playerId, myPlayerId)) {
    case "mine":
      return OWNERSHIP_MINE_COLOR;
    case "enemy":
      return OWNERSHIP_ENEMY_COLOR;
    default:
      return OWNERSHIP_NEUTRAL_COLOR;
  }
}

/** Kinds the fallback renderer draws as buildings rather than units. */
const BUILDING_KINDS: ReadonlySet<string> = new Set<string>([
  "warehouse",
  "ore_silo",
  "barracks",
  "workshop",
  "town_center",
  "house",
  "depot",
  "tower",
  "mill",
  "farm",
]);

/** Strips a namespace such as `oc:` from a content path. */
export function kindName(kind: string): string {
  const separator = kind.lastIndexOf(":");
  return separator >= 0 ? kind.slice(separator + 1) : kind;
}

/** Whether the fallback renderer should draw this kind as a building. */
export function isBuildingKind(kind: string): boolean {
  return BUILDING_KINDS.has(kindName(kind));
}

/**
 * Whether an entity kind is a building, including the engine's `building.*` content paths.
 *
 * The fallback set above only covers the classic short names. Real snapshots carry content paths such
 * as `building.lumbercamp.t1`, which the fallback renderer cannot know by name; the namespace prefix is
 * the stable rule the gameplay panel, picking and marquee selection all share.
 */
export function isBuildingEntityKind(kind: string): boolean {
  return kindName(kind).startsWith("building.") || isBuildingKind(kind);
}

/**
 * Whether the entity is a building the server reports as still under construction.
 *
 * Only buildings qualify: a unit's optional `progress` is its health fraction (the engine has no
 * unit-level construction counter), so a damaged unit must never draw a construction bar. A missing
 * `progress` is treated as finished, which is what an older snapshot means by not reporting one.
 */
export function underConstruction(entity: EntityView): boolean {
  return isBuildingEntityKind(entity.kind) && entity.progress !== undefined && entity.progress < 1;
}

/** One integer position inside a window's `tiles` array. */
export interface WindowIndex {
  readonly x: number;
  readonly y: number;
}

/** Whether a pair is a whole world tile. The world is unbounded, so any integer pair is valid. */
export function isWorldTile(x: number, y: number): boolean {
  return Number.isInteger(x) && Number.isInteger(y);
}

/**
 * Translates an absolute world coordinate to its index inside the loaded window, or `null` outside it.
 *
 * The window origin and the absolute coordinate are both signed, so the translation is a plain
 * `x - originX` and works identically on either side of zero. Fractional absolute coordinates are
 * floored *before* subtracting the origin: a point at `-0.1` is tile `-1` (floor), never tile `0`
 * (truncation toward zero), which is what keeps entities west or north of the window origin in the
 * correct cell.
 */
export function windowTileIndex(world: WorldView, x: number, y: number): WindowIndex | null {
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    return null;
  }
  const windowX = Math.floor(x) - world.originX;
  const windowY = Math.floor(y) - world.originY;
  if (windowX < 0 || windowY < 0 || windowX >= world.width || windowY >= world.height) {
    return null;
  }
  return { x: windowX, y: windowY };
}

/** Whether an integer world tile lies inside the received window. */
export function tileInWindow(world: WorldView, x: number, y: number): boolean {
  return isWorldTile(x, y) && windowTileIndex(world, x, y) !== null;
}

/**
 * Terrain at an absolute world tile, or `void` outside the received window.
 *
 * The snapshot's `tiles` array starts at the window origin, so the absolute coordinate is translated
 * by subtracting `originX` / `originY`. A tile outside the window is unknown (the world is unbounded
 * and the client has simply not asked for that window yet), and `void` is the engine's own fallback
 * terrain.
 */
export function terrainAt(world: WorldView, x: number, y: number): TerrainId {
  if (!tileInWindow(world, x, y)) {
    return TERRAIN_VOID;
  }
  return world.tiles[y - world.originY]?.[x - world.originX] ?? TERRAIN_VOID;
}

/**
 * Whether an entity's ground tile lies inside the received window.
 *
 * Entity positions are absolute world coordinates and may be negative, so an entity can be alive far
 * outside the current window. The renderer and the picker both use this rule, which floors the
 * fractional position, so nothing invisible can be drawn or clicked.
 */
export function entityInWindow(world: WorldView, entity: EntityView): boolean {
  return windowTileIndex(world, entity.x, entity.y) !== null;
}

/** Whether the entity is an own unit: owned by `myPlayerId` and not a building. */
export function isOwnUnit(entity: EntityView, myPlayerId: number | null): boolean {
  return ownershipOf(entity.playerId, myPlayerId) === "mine" && !isBuildingEntityKind(entity.kind);
}

/**
 * Whether one entity's drawn ground position falls inside an inclusive tile range.
 *
 * The range covers `[minX, maxX + 1)` in world units, so a unit exactly on the left or top edge is
 * inside and one on the far side of the right or bottom edge is not. Only own units qualify: enemy and
 * neutral entities are excluded, and a building is never marquee-selected even when it belongs to the
 * player. The renderer only draws entities inside the loaded window, so the caller also filters with
 * {@link entityInWindow}.
 */
export function entityInMarquee(
  entity: EntityView,
  range: TileRange,
  myPlayerId: number | null,
): boolean {
  if (!isOwnUnit(entity, myPlayerId)) {
    return false;
  }
  return (
    entity.x >= range.minX &&
    entity.x < range.maxX + 1 &&
    entity.y >= range.minY &&
    entity.y < range.maxY + 1
  );
}

/** Every own unit drawn inside the rectangle, in world entity order. */
export function marqueeSelection(
  world: WorldView,
  range: TileRange,
  myPlayerId: number | null,
): number[] {
  const ids: number[] = [];
  for (const entity of world.entities) {
    if (!entityInWindow(world, entity)) {
      continue;
    }
    if (entityInMarquee(entity, range, myPlayerId)) {
      ids.push(entity.id);
    }
  }
  return ids;
}

/** Entity lookup for the HUD and the selection ring. */
export function entityById(world: WorldView, id: number | null): EntityView | undefined {
  if (id === null) {
    return undefined;
  }
  return world.entities.find((entity) => entity.id === id);
}

// ---------------------------------------------------------------------------------------------
// Server world snapshot
// ---------------------------------------------------------------------------------------------

/** The route the Java server answers with its current world. */
export const WORLD_SNAPSHOT_PATH = "/world/snapshot";

/**
 * The `WorldView` plus the server tick it came from.
 *
 * The renderer only needs the `WorldView` half; the extra tick is what the HUD prints next to
 * `server`, so a browser can tell a live world from a frozen one without opening the protocol panel.
 */
export interface ServerWorldView extends WorldView {
  readonly tick: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function finiteNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

/** Converts one server terrain content path to the renderer's terrain id. */
function terrainIdFromSnapshot(value: string): TerrainId {
  const name = kindName(value.trim());
  return name === "" ? TERRAIN_VOID : name;
}

/**
 * Converts `/world/snapshot` JSON into the model the renderer already consumes.
 *
 * The server sends terrain content paths (`grass`, `oc:grass`, ...): they are normalized to the
 * renderer's terrain ids. Entity `health` becomes `healthRatio`, `player` becomes `playerId`, and
 * `kind` stays the engine content path. The optional `players` array is validated row by row into
 * `PlayerView`; a snapshot without it (an older server, or the demo path) stays valid and simply has no
 * player rows.
 *
 * The optional entity detail fields are additive for the same reason: `w` / `h` become `footprint`,
 * `progress` becomes a clamped `progress`, and `queue` / `queueTicks` become `queue` / `queueTicks`.
 * A snapshot without any of them (an older server, the demo world, every check fixture written before
 * these fields existed) converts exactly as it did before, with all of those properties `undefined`. A
 * malformed optional field is ignored rather than rejecting the whole world, because these fields are
 * presentation detail: a bad `progress` must not blank the map. A footprint is kept only when both `w`
 * and `h` are positive integers, because half a footprint says nothing useful.
 *
 * The world is unbounded, so a snapshot carries `originX` / `originY` (the window's absolute top-left
 * tile) and a `width` / `height` that describe only that window. There is no `mapWidth` / `mapHeight`
 * to read or infer: both are gone from the document, and this function does not add them back. An
 * older document with no origin fields is treated as a window whose origin is `0, 0`, exactly as
 * before.
 *
 * Coordinates are validated, not bounded: a window origin must be a signed integer (negative and very
 * large values are fine), and an entity position must be finite and signed (it may be fractional and
 * may lie outside the window, because the snapshot lists the whole living entity set). Anything
 * structurally wrong is still rejected with `null`: a tile row of the wrong length, a non-string
 * tile, a missing id/kind/position, a non-finite coordinate, a non-integer origin, or a `players` row
 * without an integer id and non-negative integer gold/wood amounts. Rejecting is deliberate - a
 * partial world would draw entities at positions the simulation never had, and the caller has a
 * deterministic demo fallback.
 */
export function worldViewFromSnapshot(payload: unknown): ServerWorldView | null {
  if (!isRecord(payload)) {
    return null;
  }
  const tick = finiteNumber(payload["tick"]);
  const width = finiteNumber(payload["width"]);
  const height = finiteNumber(payload["height"]);
  if (tick === null || width === null || height === null) {
    return null;
  }
  if (!Number.isInteger(tick) || tick < 0) {
    return null;
  }
  if (!Number.isInteger(width) || !Number.isInteger(height) || width <= 0 || height <= 0) {
    return null;
  }

  // The window origin is optional so a pre-window snapshot (and every check fixture without it)
  // still describes one window. If either half is present both must be signed integers: the world is
  // unbounded, so there is no lower or upper bound to check against.
  const rawOriginX = payload["originX"];
  const rawOriginY = payload["originY"];
  const hasWindowOrigin = rawOriginX !== undefined || rawOriginY !== undefined;
  let originX = 0;
  let originY = 0;
  if (hasWindowOrigin) {
    if (
      typeof rawOriginX !== "number" ||
      typeof rawOriginY !== "number" ||
      !Number.isInteger(rawOriginX) ||
      !Number.isInteger(rawOriginY)
    ) {
      return null;
    }
    originX = rawOriginX;
    originY = rawOriginY;
  }

  const rawTiles = payload["tiles"];
  if (!Array.isArray(rawTiles) || rawTiles.length !== height) {
    return null;
  }
  const tiles: TerrainId[][] = [];
  for (const rawRow of rawTiles) {
    if (!Array.isArray(rawRow) || rawRow.length !== width) {
      return null;
    }
    const row: TerrainId[] = [];
    for (const rawTile of rawRow) {
      if (typeof rawTile !== "string" || rawTile.trim() === "") {
        return null;
      }
      row.push(terrainIdFromSnapshot(rawTile));
    }
    tiles.push(row);
  }

  const rawEntities = payload["entities"];
  if (!Array.isArray(rawEntities)) {
    return null;
  }
  const entities: EntityView[] = [];
  for (const rawEntity of rawEntities) {
    if (!isRecord(rawEntity)) {
      return null;
    }
    const id = finiteNumber(rawEntity["id"]);
    const kind = rawEntity["kind"];
    const x = finiteNumber(rawEntity["x"]);
    const y = finiteNumber(rawEntity["y"]);
    if (id === null || x === null || y === null || typeof kind !== "string" || kind.trim() === "") {
      return null;
    }
    if (!Number.isInteger(id)) {
      return null;
    }
    // Entity coordinates are absolute world coordinates, not window coordinates: they may be
    // negative, fractional or far outside the loaded window. Only finiteness matters; there is no
    // map to validate against.
    const player = finiteNumber(rawEntity["player"]);
    const health = finiteNumber(rawEntity["health"]);
    const rawWidth = finiteNumber(rawEntity["w"]);
    const rawHeight = finiteNumber(rawEntity["h"]);
    const footprint =
      rawWidth !== null &&
      rawHeight !== null &&
      Number.isInteger(rawWidth) &&
      Number.isInteger(rawHeight) &&
      rawWidth > 0 &&
      rawHeight > 0
        ? { width: rawWidth, height: rawHeight }
        : undefined;
    const progress = finiteNumber(rawEntity["progress"]);
    const queue = finiteNumber(rawEntity["queue"]);
    const queueTicks = finiteNumber(rawEntity["queueTicks"]);
    entities.push({
      id,
      kind: kindName(kind.trim()),
      x,
      y,
      selected: false,
      ...(player === null ? {} : { playerId: Math.trunc(player) }),
      ...(health === null ? {} : { healthRatio: Math.min(1, Math.max(0, health)) }),
      ...(footprint === undefined ? {} : { footprint }),
      ...(progress === null ? {} : { progress: Math.min(1, Math.max(0, progress)) }),
      ...(queue === null || !Number.isInteger(queue) || queue < 0 ? {} : { queue }),
      ...(queueTicks === null || !Number.isInteger(queueTicks) || queueTicks < 0
        ? {}
        : { queueTicks }),
    });
  }

  const rawPlayers = payload["players"];
  const players: PlayerView[] = [];
  if (rawPlayers !== undefined) {
    if (!Array.isArray(rawPlayers)) {
      return null;
    }
    for (const rawPlayer of rawPlayers) {
      if (!isRecord(rawPlayer)) {
        return null;
      }
      const id = finiteNumber(rawPlayer["id"]);
      const name = rawPlayer["name"];
      const gold = finiteNumber(rawPlayer["gold"]);
      const wood = finiteNumber(rawPlayer["wood"]);
      if (
        id === null ||
        gold === null ||
        wood === null ||
        typeof name !== "string" ||
        name.trim() === ""
      ) {
        return null;
      }
      // The server emits integers; a fractional or negative amount is a malformed row, not a number to
      // round. The name keeps its spaces because a display name such as "Player 1" is legitimate.
      if (
        !Number.isInteger(id) ||
        id < 0 ||
        !Number.isInteger(gold) ||
        gold < 0 ||
        !Number.isInteger(wood) ||
        wood < 0
      ) {
        return null;
      }
      players.push({ id, name, gold, wood });
    }
  }

  return {
    tick,
    originX,
    originY,
    width,
    height,
    tiles,
    entities,
    ...(rawPlayers === undefined ? {} : { players }),
  };
}

/**
 * One explicit snapshot window: the centre tile and the radius in tiles.
 *
 * `cx` / `cy` are absolute signed world tiles; the server uses them exactly as sent and returns the
 * window origin `cx - r` / `cy - r`, which may itself be negative.
 */
export interface SnapshotWindowRequest {
  readonly cx: number;
  readonly cy: number;
  readonly r: number;
}

/**
 * Builds the `/world/snapshot` URL for an explicit window, or the no-parameter URL for `null`.
 *
 * Exported so the offline check can assert the exact query string, including negative centres. The
 * centre is floored rather than truncated so a coordinate west of zero cannot round toward it.
 */
export function snapshotUrl(baseUrl: string, request: SnapshotWindowRequest | null): string {
  const prefix = baseUrl.trim().replace(/\/+$/u, "");
  const path = prefix === "" ? WORLD_SNAPSHOT_PATH : `${prefix}${WORLD_SNAPSHOT_PATH}`;
  if (
    request === null ||
    !Number.isFinite(request.cx) ||
    !Number.isFinite(request.cy) ||
    !Number.isFinite(request.r)
  ) {
    return path;
  }
  // The server uses `cx` / `cy` as sent (no map to clamp against) and clamps only `r`; sending the
  // camera tile keeps the client dumb and works for any signed centre.
  return (
    `${path}?cx=${Math.floor(request.cx)}` +
    `&cy=${Math.floor(request.cy)}&r=${Math.floor(request.r)}`
  );
}

/**
 * Fetches `/world/snapshot` from the given base URL and converts it.
 *
 * A `request` asks for an explicit window around a camera tile; `null` keeps the no-parameter
 * centre-window request that old clients use and that `--world off` answers with `503`.
 *
 * An empty base URL means the same origin, which is what the Vite dev proxy and the production
 * static build both use. Every failure - offline, a 503 `--world off`, malformed JSON, an invalid
 * document - resolves to `null` rather than rejecting, so the caller's demo fallback stays one
 * `??` away and a polling request can never produce an unhandled rejection.
 */
export async function fetchWorldSnapshot(
  baseUrl = "",
  request: SnapshotWindowRequest | null = null,
): Promise<ServerWorldView | null> {
  const url = snapshotUrl(baseUrl, request);
  try {
    const response = await fetch(url, {
      headers: { Accept: "application/json" },
      cache: "no-store",
    });
    if (!response.ok) {
      return null;
    }
    const payload: unknown = await response.json();
    return worldViewFromSnapshot(payload);
  } catch {
    return null;
  }
}

/**
 * The fallback rule in one place: a good snapshot wins, a missing one is the deterministic demo map.
 * The demo generator is kept intact and is still what the page shows when no server is running.
 */
export function snapshotOrDemo(payload: unknown, seed: number): WorldView {
  return worldViewFromSnapshot(payload) ?? demoWorld(seed);
}

// ---------------------------------------------------------------------------------------------
// Deterministic map generation
// ---------------------------------------------------------------------------------------------

/** Small explicit PRNG (mulberry32); the same seed always produces the same stream. */
function mulberry32(seed: number): () => number {
  let state = seed >>> 0;
  return (): number => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

function hash2(seed: number, x: number, y: number): number {
  let value = (seed ^ Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263)) >>> 0;
  value = Math.imul(value ^ (value >>> 13), 1274126177) >>> 0;
  return (value ^ (value >>> 16)) >>> 0;
}

function unitHash(seed: number, x: number, y: number): number {
  return hash2(seed, x, y) / 4294967296;
}

function smoothStep(value: number): number {
  return value * value * (3 - 2 * value);
}

function lerp(from: number, to: number, amount: number): number {
  return from + (to - from) * amount;
}

function valueNoise(seed: number, x: number, y: number): number {
  const x0 = Math.floor(x);
  const y0 = Math.floor(y);
  const fx = smoothStep(x - x0);
  const fy = smoothStep(y - y0);
  const top = lerp(unitHash(seed, x0, y0), unitHash(seed, x0 + 1, y0), fx);
  const bottom = lerp(unitHash(seed, x0, y0 + 1), unitHash(seed, x0 + 1, y0 + 1), fx);
  return lerp(top, bottom, fy);
}

function fbm(seed: number, x: number, y: number): number {
  let amplitude = 0.55;
  let frequency = 1;
  let total = 0;
  let weight = 0;
  for (let octave = 0; octave < 3; octave += 1) {
    total += valueNoise(seed + octave * 101, x * frequency, y * frequency) * amplitude;
    weight += amplitude;
    amplitude *= 0.5;
    frequency *= 2;
  }
  return weight === 0 ? 0 : total / weight;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

interface Patch {
  readonly x: number;
  readonly y: number;
  readonly radius: number;
}

/** Coastline radius at an angle, interpolated from a handful of seeded lobes. */
function coastRadius(angle: number, lobes: readonly number[]): number {
  const count = lobes.length;
  const position = ((angle + Math.PI) / (2 * Math.PI)) * count;
  const firstIndex = Math.floor(position) % count;
  const secondIndex = (firstIndex + 1) % count;
  const amount = smoothStep(position - Math.floor(position));
  const first = lobes[firstIndex] ?? 0.9;
  const second = lobes[secondIndex] ?? 0.9;
  return lerp(first, second, amount);
}

function isBuildableTerrain(terrain: TerrainId): boolean {
  return terrain === "grass" || terrain === "sand";
}

interface TilePoint {
  readonly x: number;
  readonly y: number;
}

function findLandNear(
  tiles: readonly (readonly TerrainId[])[],
  width: number,
  height: number,
  targetX: number,
  targetY: number,
): TilePoint {
  const originX = clamp(Math.round(targetX), 0, width - 1);
  const originY = clamp(Math.round(targetY), 0, height - 1);
  for (let radius = 0; radius <= 14; radius += 1) {
    for (let offsetY = -radius; offsetY <= radius; offsetY += 1) {
      for (let offsetX = -radius; offsetX <= radius; offsetX += 1) {
        if (Math.max(Math.abs(offsetX), Math.abs(offsetY)) !== radius) {
          continue;
        }
        const x = originX + offsetX;
        const y = originY + offsetY;
        if (x < 0 || y < 0 || x >= width || y >= height) {
          continue;
        }
        const terrain = tiles[y]?.[x] ?? TERRAIN_VOID;
        if (isBuildableTerrain(terrain)) {
          return { x, y };
        }
      }
    }
  }
  return { x: originX, y: originY };
}

interface EntityAnchor {
  readonly kind: string;
  /** Position as a fraction of the map, rounded to a land tile. */
  readonly atX: number;
  readonly atY: number;
  readonly playerId: number;
  readonly healthRatio?: number;
}

/**
 * Builds the placeholder island.
 *
 * The coastline comes from seeded lobes, and the pond, hill and forest patches are placed from the
 * same explicit PRNG. Nothing calls `Math.random`, so two calls with one seed produce deeply equal
 * worlds - which is what the render math check asserts.
 */
export function demoWorld(seed: number): WorldView {
  const width = 44;
  const height = 32;
  const random = mulberry32(seed);

  const lobes: number[] = [];
  for (let index = 0; index < 8; index += 1) {
    lobes.push(0.82 + random() * 0.18);
  }

  const hillCenter: Patch = {
    x: width * (0.35 + (random() - 0.5) * 0.05),
    y: height * (0.40 + (random() - 0.5) * 0.05),
    radius: 4.2 + random() * 0.8,
  };
  const pondCenter: Patch = {
    x: width * (0.65 + (random() - 0.5) * 0.06),
    y: height * (0.55 + (random() - 0.5) * 0.06),
    radius: 2.9 + random() * 0.7,
  };
  const forests: readonly Patch[] = [
    { x: width * 0.24, y: height * 0.67, radius: 5.2 + random() * 1.1 },
    { x: width * 0.52, y: height * 0.26, radius: 4.4 + random() * 1.2 },
    { x: width * 0.78, y: height * 0.64, radius: 4.6 + random() * 1.0 },
  ];

  const tiles: TerrainId[][] = [];
  for (let y = 0; y < height; y += 1) {
    const row: TerrainId[] = [];
    for (let x = 0; x < width; x += 1) {
      row.push(terrainForTile(seed, width, height, x, y, lobes, hillCenter, pondCenter, forests));
    }
    tiles.push(row);
  }

  const anchors: readonly EntityAnchor[] = [
    { kind: "warehouse", atX: 0.40, atY: 0.44, playerId: 1 },
    { kind: "barracks", atX: 0.56, atY: 0.57, playerId: 1 },
    { kind: "worker", atX: 0.33, atY: 0.55, playerId: 1 },
    { kind: "worker", atX: 0.43, atY: 0.58, playerId: 1, healthRatio: 0.72 },
    { kind: "worker", atX: 0.36, atY: 0.35, playerId: 1 },
    { kind: "worker", atX: 0.47, atY: 0.39, playerId: 1 },
    { kind: "worker", atX: 0.70, atY: 0.63, playerId: 1 },
    { kind: "soldier", atX: 0.53, atY: 0.47, playerId: 1 },
    { kind: "soldier", atX: 0.61, atY: 0.49, playerId: 1, healthRatio: 0.55 },
    { kind: "soldier", atX: 0.57, atY: 0.62, playerId: 1, healthRatio: 0.88 },
    { kind: "medic", atX: 0.48, atY: 0.51, playerId: 1 },
    { kind: "soldier", atX: 0.74, atY: 0.40, playerId: 2 },
  ];

  const entities: EntityView[] = [];
  for (let index = 0; index < anchors.length; index += 1) {
    const anchor = anchors[index];
    if (anchor === undefined) {
      continue;
    }
    const targetX = anchor.atX * width;
    const targetY = anchor.atY * height;
    const tile = findLandNear(tiles, width, height, targetX, targetY);
    const jitterX = (random() - 0.5) * 0.42;
    const jitterY = (random() - 0.5) * 0.42;
    const entity: EntityView = {
      id: index + 1,
      kind: anchor.kind,
      x: clamp(tile.x + 0.5 + jitterX, 0.3, width - 0.3),
      y: clamp(tile.y + 0.5 + jitterY, 0.3, height - 0.3),
      playerId: anchor.playerId,
      selected: false,
      ...(anchor.healthRatio === undefined ? {} : { healthRatio: anchor.healthRatio }),
    };
    entities.push(entity);
  }

  return {
    originX: 0,
    originY: 0,
    width,
    height,
    tiles,
    entities,
  };
}

function terrainForTile(
  seed: number,
  width: number,
  height: number,
  x: number,
  y: number,
  lobes: readonly number[],
  hillCenter: Patch,
  pondCenter: Patch,
  forests: readonly Patch[],
): TerrainId {
  const normalizedX = (x + 0.5 - width / 2) / (width / 2);
  const normalizedY = (y + 0.5 - height / 2) / (height / 2);
  const distance = Math.hypot(normalizedX, normalizedY);
  const angle = Math.atan2(normalizedY, normalizedX);
  const wobble = (valueNoise(seed + 17, x * 0.19, y * 0.19) - 0.5) * 0.07;
  const coast = 0.82 * coastRadius(angle, lobes) + wobble;

  if (distance > coast) {
    return "water";
  }
  if (distance > coast - 0.075) {
    return "sand";
  }

  const pondX = (x + 0.5 - pondCenter.x) / pondCenter.radius;
  const pondY = (y + 0.5 - pondCenter.y) / (pondCenter.radius * 0.72);
  if (pondX * pondX + pondY * pondY <= 1) {
    return "water";
  }

  const hillDistance = Math.hypot(x + 0.5 - hillCenter.x, (y + 0.5 - hillCenter.y) * 1.1);
  if (hillDistance < 1.1 + valueNoise(seed + 41, x * 0.4, y * 0.4) * 0.7) {
    return "rock";
  }
  if (
    hillDistance < hillCenter.radius + valueNoise(seed + 43, x * 0.3, y * 0.3) * 1.4 ||
    fbm(seed + 47, x * 0.14, y * 0.14) > 0.7
  ) {
    return "hill";
  }

  const forestNoise = fbm(seed + 61, x * 0.21, y * 0.21);
  for (const forest of forests) {
    const patchDistance = Math.hypot(x + 0.5 - forest.x, y + 0.5 - forest.y);
    const edge = forest.radius * (0.8 + valueNoise(seed + 67, x * 0.32, y * 0.32) * 0.45);
    if (patchDistance < edge && forestNoise > 0.33) {
      return "forest";
    }
  }
  if (forestNoise > 0.64) {
    return "forest";
  }
  return "grass";
}
