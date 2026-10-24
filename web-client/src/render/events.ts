/**
 * Events derived on the client by comparing two consecutive world snapshots.
 *
 * The server has no event feed, so the page derives two events from the snapshots it already polls:
 *
 * - `建筑完成：<name>` when a building that was not finished (`progress !== 1`) is finished
 *   (`progress === 1`) in the newer snapshot;
 * - `遭到攻击：<name>` when a player-owned entity's health ratio dropped by at least
 *   {@link HEALTH_DROP_THRESHOLD} (10 percentage points).
 *
 * An entity is compared only when it exists in both snapshots: an entity that merely appeared or
 * disappeared is not an event. Both events are de-duplicated per entity for
 * {@link EVENT_DEDUPE_MS} (five seconds), so a fight that drains health over several 500 ms polls
 * produces one toast every five seconds instead of one per poll. One entity produces at most one event
 * per comparison; a building that completes while being damaged reports completion.
 *
 * Everything here is pure and DOM-free so `scripts/check-render-math.ts` can exercise the threshold
 * and the de-duplication under Node. The page turns each event into a toast and a history line.
 */

import { isBuildingEntityKind, type EntityView, type WorldView } from "./view.ts";

/** How long one entity is quiet after producing an event. */
export const EVENT_DEDUPE_MS = 5_000;

/** Absolute health-ratio drop that counts as "attacked": 10 percentage points. */
export const HEALTH_DROP_THRESHOLD = 0.1;

/**
 * Floating-point slack when comparing the drop against the threshold.
 *
 * A hit that takes health from `1` to `0.9` is a drop of `0.09999999999999998` in binary floating
 * point; without the slack the documented 10% boundary would miss by one ulp.
 */
const HEALTH_DROP_EPSILON = 1e-9;

/** The two snapshot diff events the client derives. */
export type SnapshotEventKind = "building-complete" | "under-attack";

/** One derived event ready to become a toast. */
export interface SnapshotEvent {
  readonly kind: SnapshotEventKind;
  readonly entityId: number;
  /** The ready-to-show text: `建筑完成：…` or `遭到攻击：…`. */
  readonly text: string;
}

/**
 * Per-entity de-duplication for derived events.
 *
 * The first event for an entity is always allowed. A later event for the same entity is suppressed
 * while less than {@link EVENT_DEDUPE_MS} has passed since the allowed one; exactly five seconds is
 * already allowed again. Different entities do not affect each other, and the clock is supplied by the
 * caller (`performance.now()` in the page, a literal in the check).
 */
export class EventDeduplicator {
  private readonly lastAllowedAt = new Map<number, number>();

  /** Whether an event for this entity may be shown now, recording `now` when it may. */
  allow(entityId: number, now: number): boolean {
    if (!Number.isFinite(now)) {
      return false;
    }
    const previous = this.lastAllowedAt.get(entityId);
    if (previous !== undefined && now - previous < EVENT_DEDUPE_MS) {
      return false;
    }
    this.lastAllowedAt.set(entityId, now);
    return true;
  }

  /** Forgets every timestamp, for example when a different world is adopted. */
  clear(): void {
    this.lastAllowedAt.clear();
  }
}

function healthOf(entity: EntityView): number | undefined {
  const ratio = entity.healthRatio;
  return ratio !== undefined && Number.isFinite(ratio) ? ratio : undefined;
}

/** Whether the entity belongs to a player (as opposed to neutral / ownerless). */
function isPlayerOwned(entity: EntityView): boolean {
  return (
    entity.playerId !== undefined &&
    Number.isFinite(entity.playerId) &&
    entity.playerId > 0
  );
}

function buildingJustCompleted(previous: EntityView, current: EntityView): boolean {
  return (
    isBuildingEntityKind(current.kind) &&
    previous.progress !== 1 &&
    current.progress === 1
  );
}

function healthDroppedByAtLeastOneTenth(previous: EntityView, current: EntityView): boolean {
  const before = healthOf(previous);
  const after = healthOf(current);
  if (before === undefined || after === undefined) {
    return false;
  }
  return before - after >= HEALTH_DROP_THRESHOLD - HEALTH_DROP_EPSILON;
}

/**
 * Compares the previous snapshot with the current one and returns the events to show.
 *
 * `labelFor` receives the content path and returns the manifest display name (the page passes its
 * `contentLabel`); the check can pass a deterministic table. The supplied deduplicator is updated only
 * for events that are actually returned, so a suppressed event does not extend its own silence.
 */
export function diffSnapshotEvents(
  previous: WorldView,
  current: WorldView,
  labelFor: (kind: string) => string,
  now: number,
  dedupe: EventDeduplicator,
): SnapshotEvent[] {
  const previousById = new Map<number, EntityView>();
  for (const entity of previous.entities) {
    previousById.set(entity.id, entity);
  }

  const events: SnapshotEvent[] = [];
  for (const entity of current.entities) {
    const before = previousById.get(entity.id);
    if (before === undefined) {
      continue;
    }
    let kind: SnapshotEventKind;
    if (buildingJustCompleted(before, entity)) {
      kind = "building-complete";
    } else if (isPlayerOwned(entity) && healthDroppedByAtLeastOneTenth(before, entity)) {
      kind = "under-attack";
    } else {
      continue;
    }
    if (!dedupe.allow(entity.id, now)) {
      continue;
    }
    const name = labelFor(entity.kind);
    events.push({
      kind,
      entityId: entity.id,
      text: kind === "building-complete" ? `建筑完成：${name}` : `遭到攻击：${name}`,
    });
  }
  return events;
}
