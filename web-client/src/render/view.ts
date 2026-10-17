/**
 * The client-side world model.
 *
 * `WorldView` deliberately mirrors what the future server snapshot will carry: a tile grid plus
 * entities with an id, a kind, a ground position and presentation flags. The demo generator is a
 * placeholder for a streamed snapshot; the renderer must not care which one it receives.
 *
 * Terrain ids are the engine's content paths (`TerrainType.key().path()`), not display names. The
 * self-check scenario defines water, sand, grass, forest, hill and rock, and the engine adds `void`
 * as its fallback terrain.
 */

export type TerrainId = string;

/** Terrain path the engine uses outside a map. */
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
  /** Ground position in tiles; x grows right, y grows down. */
  readonly x: number;
  /** Ground position in tiles; larger y is closer to the viewer. */
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
}

export interface WorldView {
  readonly width: number;
  readonly height: number;
  /** Row-major terrain: `tiles[y][x]`. */
  readonly tiles: readonly (readonly TerrainId[])[];
  readonly entities: readonly EntityView[];
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

/** Terrain at a tile, or `void` outside the map, so callers never index past a row. */
export function terrainAt(world: WorldView, x: number, y: number): TerrainId {
  if (x < 0 || y < 0 || x >= world.width || y >= world.height) {
    return TERRAIN_VOID;
  }
  return world.tiles[y]?.[x] ?? TERRAIN_VOID;
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
 * `kind` stays the engine content path. Anything structurally wrong is rejected with `null`: a tile
 * row of the wrong length, a non-string tile, a missing id/kind/position, a non-finite coordinate or
 * an entity outside `[0, width) x [0, height)`. Rejecting is deliberate - a partial world would draw
 * entities at positions the simulation never had, and the caller has a deterministic demo fallback.
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
    if (x < 0 || x >= width || y < 0 || y >= height) {
      return null;
    }
    const player = finiteNumber(rawEntity["player"]);
    const health = finiteNumber(rawEntity["health"]);
    entities.push({
      id,
      kind: kindName(kind.trim()),
      x,
      y,
      selected: false,
      ...(player === null ? {} : { playerId: Math.trunc(player) }),
      ...(health === null ? {} : { healthRatio: Math.min(1, Math.max(0, health)) }),
    });
  }

  return { tick, width, height, tiles, entities };
}

/**
 * Fetches `/world/snapshot` from the given base URL and converts it.
 *
 * An empty base URL means the same origin, which is what the Vite dev proxy and the production
 * static build both use. Every failure - offline, a 503 `--world off`, malformed JSON, an invalid
 * document - resolves to `null` rather than rejecting, so the caller's demo fallback stays one
 * `??` away and a polling request can never produce an unhandled rejection.
 */
export async function fetchWorldSnapshot(baseUrl = ""): Promise<ServerWorldView | null> {
  const prefix = baseUrl.trim().replace(/\/+$/u, "");
  const url = prefix === "" ? WORLD_SNAPSHOT_PATH : `${prefix}${WORLD_SNAPSHOT_PATH}`;
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

  return { width, height, tiles, entities };
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
