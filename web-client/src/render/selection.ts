/**
 * Whole-world selection rules: the double-click same-kind selection and the engineer shortcut.
 *
 * A plain click only knows the entity under the cursor, so its rule lives in `input.ts` and
 * `main.ts`. These two actions need the rest of the loaded window, so they are pure functions here
 * and `scripts/check-render-math.ts` can exercise them without a browser:
 *
 * - a double click on a unit replaces the selection with **every own unit of the same kind inside the
 *   loaded window** (the window is the same visibility rule picking uses);
 * - a double click on a building selects just that building, whichever player owns it;
 * - the engineer shortcut selects every own engineer inside the loaded window. The snapshot carries
 *   no order/busy state, so this deliberately means "all engineers", not "idle engineers".
 *
 * Both actions replace the selection rather than merging into it.
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
 * The selection a double click on `entityId` produces, in world entity order.
 *
 * A building - own, enemy or neutral - answers just its own id, because it has no group order. A unit
 * answers every **own, in-window** unit of the same kind, including the clicked unit itself; enemy and
 * neutral entities of that kind are excluded, and so is a same-kind unit standing outside the loaded
 * window (the renderer and the picker never show it either). Double-clicking empty ground clears the
 * selection.
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
  if (isBuildingEntityKind(clicked.kind)) {
    return [clicked.id];
  }
  const ids: number[] = [];
  for (const entity of world.entities) {
    if (!entityInWindow(world, entity) || isBuildingEntityKind(entity.kind)) {
      continue;
    }
    if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
      continue;
    }
    if (sameKind(entity.kind, clicked.kind)) {
      ids.push(entity.id);
    }
  }
  return ids;
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
