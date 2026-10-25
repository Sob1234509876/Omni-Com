/**
 * Client state for the small haul animation: a resource mark that pops above a unit, rises and fades.
 *
 * The server sends one optional `pop` object per unit (`kind` = `pickup` / `dropoff`, plus the engine
 * tick). This module turns those polling snapshots into short-lived animations without any extra
 * network stream: it de-duplicates on the event tick, throttles one pop per unit per second, and hands
 * the renderer a tiny list of active entries. All of it is pure and clock-injected so the offline check
 * can pin the lifetime and the throttle.
 */

import type { PopView } from "./view";

/** How long one pop stays on screen, in milliseconds. */
export const POP_LIFETIME_MS = 600;

/** Minimum gap between two pops of the same unit, in milliseconds. */
export const POP_THROTTLE_MS = 1_000;

/** How far a pop rises over its lifetime, in CSS pixels. */
export const POP_RISE_PX = 18;

/** One remembered pop: its event tick, wire kind and the client clock it started at. */
export interface PopRecord {
  readonly kind: string;
  readonly tick: number;
  readonly startedAt: number;
}

/** One pop the renderer should draw this frame. */
export interface ActivePop {
  readonly entityId: number;
  readonly kind: string;
  readonly startedAt: number;
}

/**
 * Remembers the newest pop of every entity in a received snapshot.
 *
 * <p>A pop is started only when its event tick differs from the one already seen for that unit. That
 * de-duplication is what lets the server keep the object visible for a whole second across two polls
 * without restarting the animation; the throttle then drops a second, genuinely new pop within
 * {@link POP_THROTTLE_MS} of the previous one. The map is updated in place. An entry older than the
 * lifetime plus the throttle window is pruned by {@link prunePops}, not here, so a repeated late poll
 * cannot replay an old event.
 *
 * @param records  per-entity pop records, mutated in place
 * @param entities the snapshot's entity rows, read for their optional `pop`
 * @param now      current client clock in milliseconds
 */
export function registerSnapshotPops(
  records: Map<number, PopRecord>,
  entities: readonly { readonly id: number; readonly pop?: PopView }[],
  now: number,
): void {
  if (!Number.isFinite(now)) {
    return;
  }
  for (const entity of entities) {
    const pop = entity.pop;
    if (pop === undefined || pop.kind.trim() === "" || !Number.isSafeInteger(pop.tick)) {
      continue;
    }
    const previous = records.get(entity.id);
    if (previous !== undefined && previous.tick === pop.tick) {
      continue;
    }
    if (previous !== undefined && now - previous.startedAt < POP_THROTTLE_MS) {
      // A newer event arrived inside the throttle window: remember that it was seen (by moving the
      // recorded tick forward) without starting a second animation that would overlap the first.
      records.set(entity.id, { kind: previous.kind, tick: pop.tick, startedAt: previous.startedAt });
      continue;
    }
    records.set(entity.id, { kind: pop.kind.trim(), tick: pop.tick, startedAt: now });
  }
}

/**
 * Drops records old enough that neither an active animation nor a repeated snapshot can still need
 * them. Keeping them for the full throttle plus lifetime is deliberate: a snapshot repeated after the
 * 600 ms animation ended must not be mistaken for a new event.
 */
export function prunePops(records: Map<number, PopRecord>, now: number): void {
  if (!Number.isFinite(now)) {
    return;
  }
  for (const [entityId, record] of records) {
    const age = now - record.startedAt;
    if (!Number.isFinite(age) || age > POP_LIFETIME_MS + POP_THROTTLE_MS) {
      records.delete(entityId);
    }
  }
}

/** The pops still visible at `now`, in entity id order so a frame is deterministic. */
export function activePops(records: Map<number, PopRecord>, now: number): ActivePop[] {
  const active: ActivePop[] = [];
  if (!Number.isFinite(now)) {
    return active;
  }
  for (const [entityId, record] of records) {
    const age = now - record.startedAt;
    if (age >= 0 && age < POP_LIFETIME_MS) {
      active.push({ entityId, kind: record.kind, startedAt: record.startedAt });
    }
  }
  active.sort((left, right) => left.entityId - right.entityId);
  return active;
}

/** Progress of one pop in `[0, 1)`; a non-finite age reads as finished. */
export function popProgress(startedAt: number, now: number): number {
  if (!Number.isFinite(startedAt) || !Number.isFinite(now)) {
    return 1;
  }
  return Math.min(1, Math.max(0, (now - startedAt) / POP_LIFETIME_MS));
}

/** Upward offset of the mark at this progress: it rises {@link POP_RISE_PX} over the lifetime. */
export function popRise(progress: number): number {
  return POP_RISE_PX * Math.min(1, Math.max(0, Number.isFinite(progress) ? progress : 1));
}

/** Opacity of the mark: fully opaque for the first third, then fades linearly to zero. */
export function popAlpha(progress: number): number {
  const safe = Math.min(1, Math.max(0, Number.isFinite(progress) ? progress : 1));
  if (safe <= 1 / 3) {
    return 1;
  }
  return Math.max(0, 1 - (safe - 1 / 3) / (2 / 3));
}
