/**
 * Pure rules for the context panel's building services: repair, continue building and reclaim.
 *
 * The snapshot advertises the two priced services (`repair`, `reclaim`) and the rest is ownership and
 * construction state the client already has. Keeping the decision here means the panel, the command
 * builders and `scripts/check-render-math.ts` all agree about when an entry appears and what it says:
 *
 * - **继续建造** is offered for one of the local player's unfinished sites. The wire has no "join by
 *   building id" command, so the click reuses `BUILD <engineer> <kind> <originX> <originY>`: the engine's
 *   join path recognises the unfinished site of the same type and sends the engineer to it. A selected
 *   engineer in the loaded window supplies the id; when there is none the entry is shown disabled rather
 *   than hidden, because the site still needs a builder and the reason must be visible.
 * - **修理 (cost)** is offered for one of the local player's finished buildings whose snapshot carries a
 *   `repair` price, i.e. a damaged building whose content declares a repair cost. It is disabled when the
 *   player row cannot cover the price or no own engineer is in the window.
 * - **回收 (refund)** is offered for one of the local player's unfinished sites whose snapshot carries a
 *   `reclaim` refund. It has no additional cost.
 *
 * Labels include both amounts in the same `金 X · 木 Y` shape the upgrade entry uses. An enemy or neutral
 * building never offers anything, and a legacy snapshot without `repair` / `reclaim` omits both.
 */

import {
  isBuildingEntityKind,
  ownershipOf,
  underConstruction,
  type BuildingCostView,
  type EntityView,
  type PlayerView,
} from "./view.ts";

/** The action word of the repair entry. */
export const REPAIR_ACTION_LABEL = "修理";

/** The action word of the reclaim entry. */
export const RECLAIM_ACTION_LABEL = "回收";

/** The label of the continue-building entry. */
export const CONTINUE_BUILD_ACTION_LABEL = "继续建造";

/** One context-panel state for the selected building. */
export interface BuildingActionModel {
  /** Entity id the commands name. */
  readonly buildingId: number;
  /** Content path of the selected building, used by the `BUILD` join line. */
  readonly kind: string;
  /** `继续建造` is relevant (own unfinished site). */
  readonly continueBuild: boolean;
  /** A `修理 (cost)` entry exists. */
  readonly repair: boolean;
  /** A `回收 (refund)` entry exists. */
  readonly reclaim: boolean;
  /** Whether the continue entry may be clicked (an own engineer is available). */
  readonly continueEnabled: boolean;
  /** Whether the repair entry may be clicked (engineer available and affordable). */
  readonly repairEnabled: boolean;
  /** Whether the reclaim entry may be clicked (it has no cost, so only ownership matters). */
  readonly reclaimEnabled: boolean;
  /** Engine id of the engineer the click will send, or `null` when none is in the window. */
  readonly engineerId: number | null;
  /** Labels for the visible entries, or `null` when the entry is not offered. */
  readonly continueLabel: string | null;
  readonly repairLabel: string | null;
  readonly reclaimLabel: string | null;
}

/** `金 30 · 木 0`, the same cost shape the upgrade entry uses. */
export function buildingCostLabel(cost: BuildingCostView): string {
  return `金 ${cost.gold} · 木 ${cost.wood}`;
}

/** `修理 (金 30 · 木 0)`. */
export function repairLabel(cost: BuildingCostView): string {
  return `${REPAIR_ACTION_LABEL} (${buildingCostLabel(cost)})`;
}

/** `回收 (金 50 · 木 10)`. */
export function reclaimLabel(cost: BuildingCostView): string {
  return `${RECLAIM_ACTION_LABEL} (${buildingCostLabel(cost)})`;
}

/** Whether the local player's row can cover a service cost; a missing row counts as not affordable. */
function affordable(cost: BuildingCostView, player: PlayerView | undefined): boolean {
  if (player === undefined) {
    return false;
  }
  return (
    Number.isFinite(player.gold) &&
    Number.isFinite(player.wood) &&
    player.gold >= cost.gold &&
    player.wood >= cost.wood
  );
}

/**
 * Resolves the building-service state for one selected entity.
 *
 * @param entity      the single selected entity, if exactly one is selected
 * @param myPlayerId  the local player id answered by `WHOAMI`, or `null`
 * @param player      the local player's own snapshot row, if the snapshot carries one
 * @param engineer    an own engineer in the loaded window to receive `REPAIR` / the `BUILD` join, if any
 */
export function buildingActionModel(
  entity: EntityView | undefined,
  myPlayerId: number | null,
  player: PlayerView | undefined,
  engineer: EntityView | undefined,
): BuildingActionModel | null {
  if (entity === undefined || !isBuildingEntityKind(entity.kind)) {
    return null;
  }
  if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
    return null;
  }
  const unfinished = underConstruction(entity);
  const engineerId = engineer === undefined ? null : engineer.id;
  const repairCost = entity.repairCost;
  const reclaimCost = entity.reclaim;
  const repair = !unfinished && repairCost !== undefined;
  const reclaim = unfinished && reclaimCost !== undefined;
  const continueBuild = unfinished;
  if (!repair && !reclaim && !continueBuild) {
    return null;
  }
  return {
    buildingId: entity.id,
    kind: entity.kind,
    continueBuild,
    repair,
    reclaim,
    continueEnabled: continueBuild && engineerId !== null,
    repairEnabled: repair && engineerId !== null && repairCost !== undefined
      && affordable(repairCost, player),
    reclaimEnabled: reclaim,
    engineerId,
    continueLabel: continueBuild ? CONTINUE_BUILD_ACTION_LABEL : null,
    repairLabel: repair && repairCost !== undefined ? repairLabel(repairCost) : null,
    reclaimLabel: reclaim && reclaimCost !== undefined ? reclaimLabel(reclaimCost) : null,
  };
}
