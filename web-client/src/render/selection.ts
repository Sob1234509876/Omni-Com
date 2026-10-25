/**
 * Whole-world selection rules: the marquee priority, the double-click same-kind selection and the
 * engineer shortcut.
 *
 * A plain click only knows the entity under the cursor, so its rule lives in `input.ts` and
 * `main.ts`. These actions need the rest of the loaded window, so they are pure functions here and
 * `scripts/check-render-math.ts` can exercise them without a browser:
 *
 * - the marquee selects the highest priority group it covers: combat units first, then builder units,
 *   then buildings (see {@link selectByPriority});
 * - a double click on a unit replaces the selection with **every own unit of the same kind inside the
 *   loaded window** (the window is the same visibility rule picking uses);
 * - a double click on an own building selects **every own building of the same kind in the loaded
 *   window**, so a row of armories can be given a batch TRAIN; a double click on an enemy or neutral
 *   building still selects just that building;
 * - the engineer shortcut selects every own engineer inside the loaded window. The snapshot carries
 *   no order/busy state, so this deliberately means "all engineers", not "idle engineers".
 *
 * The marquee and both double-click variants replace the selection rather than merging into it.
 */

import {
  entityInWindow,
  isBuildingEntityKind,
  kindName,
  ownershipOf,
  type EntityView,
  type WorldView,
} from "./view.ts";

/**
 * The engineers a BUILD placement should use, in selection order, or an empty list when the selection is
 * not an engineer-only group.
 *
 * <p>The panel offers BUILD for one **or several** selected engineers. Item 26 is the cooperative case:
 * one click sends one `BUILD` line per engineer, all for the same footprint, so the first engineer
 * places the site (and pays for it) and every other engineer joins that unfinished site. The whole
 * selection is returned in selection order so the first line is the placement and the rest are joins.
 * A mixed group (an engineer plus a combat unit) deliberately returns nothing: BUILD needs builders and
 * silently picking some of a mixed order would be misleading.
 */
export function engineersForPlacement(
  selection: readonly EntityView[],
  myPlayerId: number | null,
): EntityView[] {
  if (selection.length === 0) {
    return [];
  }
  for (const entity of selection) {
    if (ownershipOf(entity.playerId, myPlayerId) !== "mine" || !isEngineerKind(entity.kind)) {
      return [];
    }
  }
  return [...selection];
}

/**
 * The first engineer a BUILD placement should use, or `undefined` when the selection is not an
 * engineer-only group.
 *
 * <p>This stays the "who does the ghost judge reach for" accessor, and the single-engineer form of the
 * placement command. The wire order itself is built from {@link engineersForPlacement}, which keeps
 * every engineer in the group so a two-engineer selection produces two BUILD lines.
 */
export function engineerForPlacement(
  selection: readonly EntityView[],
  myPlayerId: number | null,
): EntityView | undefined {
  return engineersForPlacement(selection, myPlayerId)[0];
}

/**
 * Whether a kind is an engineer.
 *
 * The classic catalogue calls it `unit.engineer`, while the demo and legacy snapshots use the short
 * `engineer`. The namespace prefix is stripped first, so `oc:unit.engineer` matches too.
 */
export function isEngineerKind(kind: string): boolean {
  const name = kindName(kind);
  return name === "engineer" || name.endsWith(".engineer");
}

/** Two entity kinds describe the same content path once their namespace prefix is stripped. */
function sameKind(left: string, right: string): boolean {
  return kindName(left) === kindName(right);
}

/**
 * The short kinds the demo world uses for attack-capable units.
 *
 * The real game names its melee line `unit.melee.t1` ... `unit.melee.t5`; the procedural demo uses
 * `soldier`. Both are combat units even though the current snapshot carries neither a `role` nor an
 * `attackDamage` field.
 */
const COMBAT_KIND_NAMES: ReadonlySet<string> = new Set<string>(["soldier", "fighter", "warrior"]);

/**
 * Whether a content path names an attack-capable unit.
 *
 * A unit is attack capable when its kind is the engine's `unit.melee.*` line or a known legacy combat
 * kind. A snapshot that does carry the optional `role` / `attackDamage` metadata is described by
 * {@link isCombatUnit} instead, so this is only the fallback for the current wire format.
 */
export function isCombatKind(kind: string): boolean {
  const name = kindName(kind);
  return name.startsWith("unit.melee.") || COMBAT_KIND_NAMES.has(name);
}

/** Whether an entity belongs to the combat-unit group (ownership is checked by the caller). */
function isCombatUnit(entity: EntityView): boolean {
  if (isBuildingEntityKind(entity.kind)) {
    return false;
  }
  // A positive attack damage is the concrete meaning of "attack capable"; the role is the declared
  // fallback for a unit whose damage the snapshot does not repeat. The current server sends neither,
  // so the content path decides in that case.
  if (entity.attackDamage !== undefined && entity.attackDamage > 0) {
    return true;
  }
  if (entity.role !== undefined) {
    return entity.role.trim().toLowerCase() === "combat";
  }
  return isCombatKind(entity.kind);
}

/**
 * The marquee priority: the highest priority group present among the entities the marquee covers.
 *
 * **Combat units first, then builder units (engineers), then buildings.** Only the winner is selected:
 * a combat unit anywhere in the rectangle suppresses the builders and buildings, and a builder
 * suppresses the buildings. Mixed combat kinds are fine inside the combat group (for example two melee
 * tiers), and a marquee that covers only buildings selects every own building it covers. An empty
 * rectangle selects nothing.
 *
 * `entities` is expected to be the marquee's candidates: only own entities, already filtered by the
 * caller for the loaded window and the rectangle (see `marqueeSelection` in `view.ts`). The optional
 * `myPlayerId` repeats the ownership filter, so a direct caller cannot accidentally include an enemy
 * or neutral entity; `undefined` means "the candidates are already own-only".
 */
export function selectByPriority(entities: readonly EntityView[], myPlayerId?: number | null): number[] {
  const candidates =
    myPlayerId === undefined
      ? entities
      : entities.filter((entity) => ownershipOf(entity.playerId, myPlayerId) === "mine");

  const combat = candidates.filter(isCombatUnit);
  if (combat.length > 0) {
    return combat.map((entity) => entity.id);
  }
  const builders = candidates.filter(
    (entity) => !isBuildingEntityKind(entity.kind) && isEngineerKind(entity.kind),
  );
  if (builders.length > 0) {
    return builders.map((entity) => entity.id);
  }
  return candidates
    .filter((entity) => isBuildingEntityKind(entity.kind))
    .map((entity) => entity.id);
}

/**
 * The selection a double click on `entityId` produces, in world entity order.
 *
 * A unit answers every **own, in-window** unit of the same kind, including the clicked unit itself;
 * enemy and neutral entities of that kind are excluded, and so is a same-kind unit standing outside the
 * loaded window (the renderer and the picker never show it either). An **own building** answers every
 * own, in-window building of the same kind, which is the multi-building selection item 23 asks for; a
 * building that is not yours answers just its own id. Double-clicking empty ground clears the selection.
 */
export function doubleClickSelection(
  world: WorldView,
  entityId: number | null,
  myPlayerId: number | null,
): number[] {
  if (entityId === null) {
    return [];
  }
  const clicked = world.entities.find((entity) => entity.id === entityId);
  if (clicked === undefined) {
    return [];
  }
  const ownBuilding = isBuildingEntityKind(clicked.kind)
    && ownershipOf(clicked.playerId, myPlayerId) === "mine";
  if (isBuildingEntityKind(clicked.kind) && !ownBuilding) {
    return [clicked.id];
  }
  const ids: number[] = [];
  for (const entity of world.entities) {
    if (!entityInWindow(world, entity) || isBuildingEntityKind(entity.kind) !== isBuildingEntityKind(clicked.kind)) {
      continue;
    }
    if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
      continue;
    }
    if (sameKind(entity.kind, clicked.kind)) {
      ids.push(entity.id);
    }
  }
  return ids.length > 0 ? ids : [clicked.id];
}

/**
 * Every own engineer inside the loaded window, in world entity order.
 *
 * This is the selection behind the `工兵` button and the `O` hotkey. The snapshot has no per-unit order
 * state, so the honest meaning is "all own engineers in the window"; the label and the READMEs say so
 * rather than promising "idle".
 */
export function engineerSelection(world: WorldView, myPlayerId: number | null): number[] {
  const ids: number[] = [];
  for (const entity of world.entities) {
    if (!engineerQualifies(world, entity, myPlayerId)) {
      continue;
    }
    ids.push(entity.id);
  }
  return ids;
}

function engineerQualifies(
  world: WorldView,
  entity: EntityView,
  myPlayerId: number | null,
): boolean {
  return (
    entityInWindow(world, entity) &&
    ownershipOf(entity.playerId, myPlayerId) === "mine" &&
    isEngineerKind(entity.kind)
  );
}
