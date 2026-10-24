/**
 * Events derived on the client by comparing two consecutive world snapshots.
 *
 * The server has no event feed, so the page derives two events from the snapshots it already polls:
 *
 * - `建筑完成：<name>` when a building that was not finished (`progress !== 1`) is finished
 *   (`progress === 1`) in the newer snapshot;
 * - `遭到攻击：<name>（攻击者：tN）` when one of the **local player's** entities' health ratio dropped by
 *   at least {@link HEALTH_DROP_THRESHOLD} (10 percentage points). `N` is the highest melee tier among
 *   hostile units within {@link ATTACKER_DETECTION_TILES} of the victim, or `未知` when none is close.
 *
 * An entity is compared only when it exists in both snapshots: an entity that merely appeared or
 * disappeared is not an event. Both events are de-duplicated per entity for
 * {@link EVENT_DEDUPE_MS} (five seconds), so a fight that drains health over several 500 ms polls
 * produces one toast every five seconds instead of one per poll. One entity produces at most one event
 * per comparison; a building that completes while being damaged reports completion. Enemy units losing
 * health never produce an "under attack" toast, because that would be watching their fight rather than
 * being told about your own.
 *
 * Everything here is pure and DOM-free so `scripts/check-render-math.ts` can exercise the threshold
 * and the de-duplication under Node. The page turns each event into a toast and a history line.
 */

import { isCombatKind } from "./selection.ts";
import { isBuildingEntityKind, ownershipOf, type EntityView, type WorldView } from "./view.ts";

/** How long one entity is quiet after producing an event. */
export const EVENT_DEDUPE_MS = 5_000;

/** Absolute health-ratio drop that counts as "attacked": 10 percentage points. */
export const HEALTH_DROP_THRESHOLD = 0.1;

/**
 * How close a hostile combat unit must stand to a damaged entity to be named as its attacker.
 *
 * <p>The snapshot has no attacker field, so the client infers the tier from the enemy units around the
 * victim. Four tiles is wider than every classic melee range (the units hit at about 1.5 tiles) and
 * narrower than the gap between two separate fights, so a wave that has reached the base is reported
 * with the highest nearby tier instead of "unknown" and a distant elite elsewhere is not blamed. The
 * inference is explicit in the text (`攻击者：t5`); it is a nearby-hostile report, not a claim about
 * which unit dealt a particular hit.
 */
export const ATTACKER_DETECTION_TILES = 4;

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

/** The melee tier encoded in a `unit.melee.tN` content path, or 0 for a unit without one. */
export function meleeTierOf(kind: string): number {
  const match = /\.t([0-9]+)$/u.exec(kind);
  if (match === null) {
    return 0;
  }
  const tier = Number(match[1]);
  return Number.isSafeInteger(tier) && tier > 0 ? tier : 0;
}

/**
 * The highest melee tier among hostile combat units close to a damaged entity, or 0 when none is near.
 *
 * <p>Pure and deterministic: candidates are walked in entity order and only a strictly higher tier
 * replaces the current answer, so equal-tier neighbours do not change the result. Buildings are not
 * attackers for this report (the toast is about the unit wave), and a unit of the victim's own side or a
 * neutral unit is ignored.
 */
export function attackerTierNear(
  victim: EntityView,
  entities: readonly EntityView[],
): number {
  let best = 0;
  for (const candidate of entities) {
    if (candidate.playerId === undefined || candidate.playerId === victim.playerId) {
      continue;
    }
    if (isBuildingEntityKind(candidate.kind) || !isCombatKind(candidate.kind)) {
      continue;
    }
    const dx = candidate.x - victim.x;
    const dy = candidate.y - victim.y;
    if (dx * dx + dy * dy > ATTACKER_DETECTION_TILES * ATTACKER_DETECTION_TILES) {
      continue;
    }
    best = Math.max(best, meleeTierOf(candidate.kind));
  }
  return best;
}

/**
 * Compares the previous snapshot with the current one and returns the events to show.
 *
 * `<labelFor>` receives the content path and returns the manifest display name (the page passes its
 * `contentLabel`); the check can pass a deterministic table. The supplied deduplicator is updated only
 * for events that are actually returned, so a suppressed event does not extend its own silence.
 *
 * <p>Only the local player's entities produce an `under-attack` toast (`myPlayerId`); watching an enemy
 * lose health is not the owner's base being hit. The text names the highest melee tier near the victim
 * when one is known (`攻击者：t5`), because "who is hitting me" is the question the owner asked after the
 * late elite wave. The de-duplicator keeps one entity quiet for five seconds, which is exactly the
 * "at most one toast per five seconds per building/unit" throttle.
 */
export function diffSnapshotEvents(
  previous: WorldView,
  current: WorldView,
  labelFor: (kind: string) => string,
  now: number,
  dedupe: EventDeduplicator,
  myPlayerId: number | null = null,
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
    } else if (
      ownershipOf(entity.playerId, myPlayerId) === "mine" &&
      healthDroppedByAtLeastOneTenth(before, entity)
    ) {
      kind = "under-attack";
    } else {
      continue;
    }
    if (!dedupe.allow(entity.id, now)) {
      continue;
    }
    const name = labelFor(entity.kind);
    let text: string;
    if (kind === "building-complete") {
      text = `建筑完成：${name}`;
    } else {
      const tier = attackerTierNear(entity, current.entities);
      text = `遭到攻击：${name}（攻击者：${tier > 0 ? `t${tier}` : "未知"}）`;
    }
    events.push({ kind, entityId: entity.id, text });
  }
  return events;
}
