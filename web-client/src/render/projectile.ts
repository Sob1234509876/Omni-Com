/**
 * Client-side projectile tracking and the draw math for one flying shot.
 *
 * The snapshot's optional top-level `projectiles` array carries each shot's endpoints only (`x` / `y`
 * start tile, `tx` / `ty` arrival tile) - never a live position. This module turns those endpoints into
 * the animation the renderer draws: a new id registers at the current frame's clock, the shot then flies
 * from its start toward its arrival at a fixed client-side visual speed, and the frame's position is the
 * straight lerp between the two tiles, clamped so a late frame can never overshoot the target.
 *
 * Like `render/pop.ts`, all of it is pure, DOM-free and clock-injected so `scripts/check-render-math.ts`
 * can pin the speed, the lerp and the arrival rule under Node. The `seen` map is the caller's persistent
 * state: `trackProjectiles` mutates it in place (register new ids, drop finished flights) and answers
 * only what should be drawn this frame.
 */

import type { ProjectileView } from "./view";

/** Visual flight speed in tiles per second; purely client-side, the wire carries no timing. */
export const PROJECTILE_SPEED_TPS = 14;

/** Trail length behind the current position, in world tiles (the renderer scales it to the screen). */
export const PROJECTILE_TRAIL_TILES = 0.6;

/** Head triangle size of the drawn shot, in world tiles. */
export const PROJECTILE_HEAD_TILES = 0.24;

/** One remembered shot: the wire endpoints plus the client clock its flight started at. */
export interface TrackedProjectile {
  /** Start tile of the flight, absolute world tiles. */
  readonly x: number;
  readonly y: number;
  /** Arrival tile of the flight, absolute world tiles. */
  readonly tx: number;
  readonly ty: number;
  /** `performance.now()`-style client clock value at which the flight began. */
  readonly startMs: number;
}

/** One projectile the renderer should draw this frame. */
export interface ActiveProjectile {
  readonly id: number;
  /** Current position this frame: the clamped lerp from the start toward the arrival tile. */
  readonly x: number;
  readonly y: number;
  /** Arrival tile; the position never moves past it. */
  readonly tx: number;
  readonly ty: number;
  /** Flight direction in radians, from the start tile toward the arrival tile. */
  readonly angle: number;
  /** Client clock value the flight started at. */
  readonly startMs: number;
  /** Total flight time in milliseconds at {@link PROJECTILE_SPEED_TPS}. */
  readonly flightMs: number;
}

/** The minimal state the renderer's projectile pass draws with. */
export interface ProjectileDrawState {
  /** Current position in absolute world tiles (fractional between the two tiles). */
  readonly x: number;
  readonly y: number;
  /** Flight direction in radians from the start tile toward the arrival tile. */
  readonly angle: number;
}

/** Flight time of one leg in milliseconds at the visual speed; a zero-length leg arrives instantly. */
export function projectileFlightMs(x: number, y: number, tx: number, ty: number): number {
  return (Math.hypot(tx - x, ty - y) / PROJECTILE_SPEED_TPS) * 1000;
}

/** Flight direction in radians from the start tile toward the arrival tile. */
export function projectileAngle(x: number, y: number, tx: number, ty: number): number {
  return Math.atan2(ty - y, tx - x);
}

/**
 * Remembers the shots of one snapshot and answers the ones still flying at `nowMs`.
 *
 * A row whose id is new registers with `startMs = nowMs`; an id that is already tracked is ignored, so
 * the server repeating a shot across two polls cannot restart its flight. Each tracked entry's flight
 * lasts {@link projectileFlightMs}; the frame position is the straight lerp from the start toward the
 * arrival tile with the progress clamped to `[0, 1]`, so a late frame lands exactly on the target and
 * never past it. An entry whose flight has finished is removed from `seen` - its one final frame is
 * drawn clamped at the arrival tile, and the next frame it is gone. The map is updated in place; a
 * malformed row (non-integer id, non-finite coordinate) is skipped defensively even though the view
 * parsing should have dropped it already.
 *
 * @param seen     per-id flight state, mutated in place; owned by the caller
 * @param arrivals the snapshot's optional `projectiles` rows; `undefined` keeps the flights already tracked
 * @param nowMs    current client clock in milliseconds
 */
export function trackProjectiles(
  seen: Map<number, TrackedProjectile>,
  arrivals: readonly ProjectileView[] | undefined,
  nowMs: number,
): ActiveProjectile[] {
  const active: ActiveProjectile[] = [];
  if (!Number.isFinite(nowMs)) {
    return active;
  }
  if (arrivals !== undefined) {
    for (const shot of arrivals) {
      if (
        !Number.isSafeInteger(shot.id) ||
        !Number.isFinite(shot.x) ||
        !Number.isFinite(shot.y) ||
        !Number.isFinite(shot.tx) ||
        !Number.isFinite(shot.ty)
      ) {
        continue;
      }
      if (seen.has(shot.id)) {
        continue;
      }
      seen.set(shot.id, { x: shot.x, y: shot.y, tx: shot.tx, ty: shot.ty, startMs: nowMs });
    }
  }
  for (const [id, shot] of seen) {
    const flightMs = projectileFlightMs(shot.x, shot.y, shot.tx, shot.ty);
    const elapsed = nowMs - shot.startMs;
    const progress =
      flightMs > 0 ? Math.min(1, Math.max(0, elapsed / flightMs)) : 1;
    const x = shot.x + (shot.tx - shot.x) * progress;
    const y = shot.y + (shot.ty - shot.y) * progress;
    active.push({
      id,
      x,
      y,
      tx: shot.tx,
      ty: shot.ty,
      angle: projectileAngle(shot.x, shot.y, shot.tx, shot.ty),
      startMs: shot.startMs,
      flightMs,
    });
    // Finished flight: the clamped frame above is the last one drawn; the entry leaves the map so a
    // repeated late snapshot cannot resurrect it as a fresh flight.
    if (progress >= 1) {
      seen.delete(id);
    }
  }
  active.sort((left, right) => left.id - right.id);
  return active;
}

/**
 * The draw states of the frame's active shots, in id order for a deterministic frame.
 *
 * This is the one projection the renderer's projectile pass consumes; the trail and head geometry
 * helpers below build on it, so the canvas code never repeats the math.
 */
export function projectileDrawState(
  active: readonly ActiveProjectile[],
): ProjectileDrawState[] {
  return active.map((shot) => ({ x: shot.x, y: shot.y, angle: shot.angle }));
}

/** Tail and tip of the trail line: `PROJECTILE_TRAIL_TILES` behind the shot along `-angle`. */
export function projectileTrail(state: ProjectileDrawState): {
  readonly fromX: number;
  readonly fromY: number;
  readonly toX: number;
  readonly toY: number;
} {
  const tailX = state.x - Math.cos(state.angle) * PROJECTILE_TRAIL_TILES;
  const tailY = state.y - Math.sin(state.angle) * PROJECTILE_TRAIL_TILES;
  return { fromX: tailX, fromY: tailY, toX: state.x, toY: state.y };
}

/** The three vertices of the arrow head at the shot's position, pointing along `angle`. */
export function projectileHeadPoints(
  state: ProjectileDrawState,
): readonly (readonly [number, number])[] {
  const cos = Math.cos(state.angle);
  const sin = Math.sin(state.angle);
  const size = PROJECTILE_HEAD_TILES;
  return [
    [state.x + cos * size, state.y + sin * size],
    [state.x - cos * size * 0.6 - sin * size * 0.55, state.y - sin * size * 0.6 + cos * size * 0.55],
    [state.x - cos * size * 0.6 + sin * size * 0.55, state.y - sin * size * 0.6 - cos * size * 0.55],
  ];
}
