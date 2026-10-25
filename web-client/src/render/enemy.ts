/**
 * The enemy anchor readout: which hostile factions have a known starting position, how far away they
 * are from the camera, and which of the eight compass sectors they lie in.
 *
 * <p>The snapshot's player rows carry an optional `anchor` pair (absolute integer tiles) for the faction
 * anchor. That is a **known starting position**, not a fog-of-war observation: the server publishes it on
 * purpose so the browser can answer "which way is the enemy base" even when the anchor has never been
 * inside a loaded window. Nothing here reveals terrain, entities or a live position.
 *
 * <p>Everything in this module is pure and DOM-free so `scripts/check-render-math.ts` can assert the
 * eight sector strings, the distance rule and the camera target of a click. The page's top-chrome line and
 * the minimap edge arrow both read these functions, so the two can never point in different directions.
 */

import { CAMERA_SAFETY_BOUND_TILES, type Point, type TileCoord } from "./camera.ts";
import { ownershipOf, type PlayerView } from "./view.ts";

/**
 * The eight compass sectors, starting north and turning clockwise. These exact strings are the wire to
 * the player: the top-chrome line prints one of them, and the check pins all eight.
 */
export const COMPASS_SECTORS = ["北", "东北", "东", "东南", "南", "西南", "西", "西北"] as const;

/** One of the eight compass strings. */
export type CompassSector = (typeof COMPASS_SECTORS)[number];

/**
 * The compass sector from one tile to another.
 *
 * <p>World y grows **down**, so north is `-y`. The angle is measured clockwise from north with
 * `atan2(dx, -dy)` and rounded to the nearest of the eight 45-degree sectors. A zero delta has no
 * direction and answers `null`; every other pair (including the exact diagonals) lands on exactly one
 * sector, so the caller never has to guess.
 *
 * @param dx target x minus origin x, in tiles
 * @param dy target y minus origin y, in tiles (positive is south)
 * @return the sector, or `null` for a zero or non-finite delta
 */
export function compassSector(dx: number, dy: number): CompassSector | null {
  if (!Number.isFinite(dx) || !Number.isFinite(dy) || (dx === 0 && dy === 0)) {
    return null;
  }
  const degrees = (Math.atan2(dx, -dy) * 180) / Math.PI;
  const normalized = ((degrees % 360) + 360) % 360;
  return COMPASS_SECTORS[Math.round(normalized / 45) % COMPASS_SECTORS.length] ?? null;
}

/** Euclidean distance in tiles between two integer tiles, rounded to a whole tile. */
export function tileDistance(from: TileCoord, to: TileCoord): number {
  return Math.round(Math.hypot(to.x - from.x, to.y - from.y));
}

/** One hostile player's known starting position, expressed relative to the camera centre. */
export interface EnemyAnchorEntry {
  /** Player id of the hostile faction, the same number as `EntityView.playerId`. */
  readonly playerId: number;
  /** Display name from the player row, for a hover title; the line itself uses the id. */
  readonly name: string;
  /** Absolute anchor tile. */
  readonly anchor: TileCoord;
  /** Rounded Euclidean distance from the camera centre, in tiles. */
  readonly distance: number;
  /** Compass sector from the camera centre to the anchor. */
  readonly sector: CompassSector;
}

/**
 * Every hostile player that publishes an anchor, nearest first.
 *
 * <p>"Hostile" is the shared ownership rule: a row is included only when
 * {@link ownershipOf}(row.id, myPlayerId) is `enemy`. That excludes the local player and the neutral
 * sample-scene player, so the default one-bot world lists exactly `敌方 2`, not the showroom. Without a
 * local identity every row is neutral and the list is empty.
 *
 * <p>The result is sorted by distance and then by id, so the same snapshot and camera always produce the
 * same line: no clock, no random, no map iteration order.
 *
 * @param players the snapshot's player rows, or `undefined` for an older snapshot / the demo world
 * @param myPlayerId the local engine player id, or `null` before LOGIN / WHOAMI
 * @param centre the camera centre tile
 * @return the hostile anchored players, nearest first
 */
export function enemyAnchorEntries(
  players: readonly PlayerView[] | undefined,
  myPlayerId: number | null,
  centre: TileCoord,
): EnemyAnchorEntry[] {
  if (players === undefined) {
    return [];
  }
  const entries: EnemyAnchorEntry[] = [];
  for (const player of players) {
    if (ownershipOf(player.id, myPlayerId) !== "enemy") {
      continue;
    }
    const anchor = player.anchor;
    if (anchor === undefined) {
      continue;
    }
    const sector = compassSector(anchor.x - centre.x, anchor.y - centre.y);
    if (sector === null) {
      continue;
    }
    entries.push({
      playerId: player.id,
      name: player.name,
      anchor,
      distance: tileDistance(centre, anchor),
      sector,
    });
  }
  entries.sort((left, right) =>
    left.distance !== right.distance
      ? left.distance - right.distance
      : left.playerId - right.playerId,
  );
  return entries;
}

/**
 * One line in the documented shape, for example
 * `敌方 2：锚点 (192,192)，距 216 格，东南`.
 *
 * <p>The id is the stable identity; the anchor is the absolute tile, so the line stays meaningful after
 * the camera moves (the distance and sector are recomputed from the current camera each frame).
 */
export function formatEnemyAnchor(entry: EnemyAnchorEntry): string {
  return `敌方 ${entry.playerId}：锚点 (${entry.anchor.x},${entry.anchor.y})，距 ${entry.distance} 格，${entry.sector}`;
}

/** The compact top-chrome text: one entry per hostile anchored player, nearest first. */
export function enemyLineText(entries: readonly EnemyAnchorEntry[]): string {
  return entries.map(formatEnemyAnchor).join(" · ");
}

/**
 * The camera centre a click on an anchor should use, in world pixels.
 *
 * <p>The anchor is a tile, so the camera centres on that tile's middle - exactly the rule a minimap click
 * already uses. The result is clamped to the camera's numeric safety bound so a hostile (or malicious)
 * snapshot coordinate cannot move the camera outside the range the engine can represent. The page passes
 * `camera.tileSize`; the check asserts the arithmetic on its own.
 */
export function anchorCameraTarget(anchor: TileCoord, tileSize: number): Point {
  const size = Number.isFinite(tileSize) && tileSize > 0 ? tileSize : 1;
  const limit = CAMERA_SAFETY_BOUND_TILES * size;
  const clamp = (value: number): number => Math.min(limit, Math.max(-limit, value));
  return { x: clamp((anchor.x + 0.5) * size), y: clamp((anchor.y + 0.5) * size) };
}
