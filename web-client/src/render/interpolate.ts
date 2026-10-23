/**
 * Snapshot interpolation for the game view.
 *
 * The server sends absolute entity positions once per snapshot, and the page polls
 * `/world/snapshot` every `WORLD_POLL_MS` (500 ms). Drawing a snapshot as soon as it arrives makes
 * units step five times a second, so the view keeps the last two received frames and renders between
 * them: `alphaFor` maps the wall clock onto `[0, 1]` across one poll interval and `interpolate` builds
 * the presentation-only `WorldView` for that moment. The renderer, HUD, selection and ownership rings
 * all read that one view, so everything moves together.
 *
 * The cost is one poll interval of latency: the picture is one snapshot behind the newest one, because
 * the newest positions are only reached when the next snapshot is due. That is the right trade while
 * the protocol sends absolute positions per tick; a server push at 10-20 Hz would remove the lag but is
 * a protocol change.
 *
 * Everything here is pure and DOM-free so `scripts/check-render-math.ts` can exercise it under Node.
 */

import type { EntityView, WorldView } from "./view";

/** One received snapshot: the converted view plus the server tick it was taken at. */
export interface Frame {
  readonly view: WorldView;
  readonly tick: number;
}

/**
 * Distance in tiles beyond which a position difference is treated as a teleport instead of motion.
 *
 * A snapshot arrives every 500 ms; a unit moving that far between two polls either teleported, was
 * spawned elsewhere, or is not the same thing the player was watching. Sliding it across the world would
 * draw a motion the simulation never had, so such an entity snaps to the newer position instead.
 */
export const MAX_INTERPOLATION_DISTANCE_TILES = 4;

function clampAlpha(alpha: number): number {
  if (!Number.isFinite(alpha)) {
    return 1;
  }
  return Math.min(1, Math.max(0, alpha));
}

/**
 * The interpolation factor for one poll interval.
 *
 * `now` and `lastFetchMillis` share one monotonic millisecond clock (`performance.now()` in the page);
 * `lastFetchMillis` is the receive time of the newer frame and `pollMillis` is the poll interval. The
 * result is 0 at the moment that frame arrived, 0.5 half a poll later, and 1 once a whole poll interval
 * has passed. Values outside the window clamp to the ends, so a late or an early animation frame never
 * extrapolates past a snapshot (no overshoot when a unit reverses). Non-finite inputs and a
 * non-positive `pollMillis` have no meaningful interval, so the newest frame is shown unchanged.
 */
export function alphaFor(now: number, lastFetchMillis: number, pollMillis: number): number {
  if (
    !Number.isFinite(now) ||
    !Number.isFinite(lastFetchMillis) ||
    !Number.isFinite(pollMillis) ||
    pollMillis <= 0
  ) {
    return 1;
  }
  return clampAlpha((now - lastFetchMillis) / pollMillis);
}

function lerp(from: number, to: number, amount: number): number {
  return from + (to - from) * amount;
}

/** Whether one matched entity may be slid between two frames, or has to snap to the newer one. */
function canInterpolate(from: EntityView, to: EntityView): boolean {
  if (from.kind !== to.kind || from.playerId !== to.playerId) {
    return false;
  }
  return Math.hypot(to.x - from.x, to.y - from.y) <= MAX_INTERPOLATION_DISTANCE_TILES;
}

/**
 * Builds the view to draw between two received frames.
 *
 * Entities are matched by id. One present in both frames has its `x` / `y` lerped from `a` to `b`
 * (keeping every other field from `b`, such as health or animation frames); one present only in `b`
 * appears at its `b` position, and one present only in `a` is gone. A matched entity snaps to `b`
 * instead of sliding when its kind or owner changed, or when it moved further than
 * {@link MAX_INTERPOLATION_DISTANCE_TILES} tiles between the two snapshots.
 *
 * Terrain, dimensions and player rows always come from `b`, the newer frame. `alpha` is clamped to
 * `[0, 1]` here as well, so a caller can never extrapolate past a snapshot.
 */
export function interpolate(a: Frame, b: Frame, alpha: number): WorldView {
  const amount = clampAlpha(alpha);
  const previous = new Map<number, EntityView>();
  for (const entity of a.view.entities) {
    previous.set(entity.id, entity);
  }

  const entities: EntityView[] = [];
  for (const entity of b.view.entities) {
    const before = previous.get(entity.id);
    if (before === undefined || !canInterpolate(before, entity)) {
      entities.push(entity);
      continue;
    }
    entities.push({
      ...entity,
      x: lerp(before.x, entity.x, amount),
      y: lerp(before.y, entity.y, amount),
    });
  }

  return { ...b.view, entities };
}
