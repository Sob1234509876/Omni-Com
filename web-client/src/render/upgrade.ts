/**
 * Pure upgrade-entry rules for the bottom-right context zone.
 *
 * The server snapshot carries an optional `upgrade` object on a building row
 * (`{"to":"building.armory.t2","gold":140,"wood":40}`). This module decides whether that row becomes a
 * visible **升级** entry and whether it is enabled:
 *
 * - only a building whose owner is the local player may offer the entry; an enemy or neutral building
 *   with an `upgrade` field is ignored, exactly like TRAIN;
 * - a building without an `upgrade` field (unfinished, top tier, an older snapshot) offers nothing;
 * - affordability is a client-side rule over the local player's own snapshot row: both the gold and the
 *   wood cost must be covered. A missing player row (before `WHOAMI`, no player yet) counts as not
 *   affordable, so the entry is shown disabled rather than disappearing;
 * - the label always names the target's display name and both amounts, so the operator sees what the
 *   purchase does and what it costs before clicking.
 *
 * The DOM half lives in `src/main.ts`; keeping the decision here lets `scripts/check-render-math.ts`
 * exercise every rule without a browser.
 */

import {
  isBuildingEntityKind,
  ownershipOf,
  type EntityView,
  type PlayerView,
} from "./view.ts";

/** The context-zone entry a selected own building may offer. */
export interface UpgradeEntry {
  /** Entity id the `UPGRADE <id>` command names. */
  readonly buildingId: number;
  /** Target content path from the snapshot's `upgrade.to`. */
  readonly target: string;
  /** Display name of the target, resolved through the manifest label function. */
  readonly targetName: string;
  /** Gold the server will charge. */
  readonly gold: number;
  /** Wood the server will charge. */
  readonly wood: number;
  /** Whether the local player's row covers both amounts. */
  readonly affordable: boolean;
  /** True exactly when the entry must be rendered disabled. */
  readonly disabled: boolean;
  /** Full button label: `升级 · 兵营·强化 · 金 140 · 木 40`. */
  readonly label: string;
}

/** The action word in front of every upgrade label. */
export const UPGRADE_ACTION_LABEL = "升级";

/** Formats one amount for the label; kept here so the check can assert the same text the page shows. */
export function upgradeCostLabel(gold: number, wood: number): string {
  return `金 ${gold} · 木 ${wood}`;
}

/**
 * Resolves the upgrade entry for a selection, or `null` when the selection offers none.
 *
 * @param entity      the single selected entity, if exactly one is selected
 * @param myPlayerId  the local player id answered by `WHOAMI`, or `null`
 * @param player      the local player's own snapshot row, if the snapshot carries one
 * @param labelForPath manifest display-name resolver; the raw path is used when it has no name
 */
export function upgradeEntry(
  entity: EntityView | undefined,
  myPlayerId: number | null,
  player: PlayerView | undefined,
  labelForPath: (path: string) => string = (path: string): string => path,
): UpgradeEntry | null {
  if (entity === undefined) {
    return null;
  }
  if (!isBuildingEntityKind(entity.kind)) {
    return null;
  }
  if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
    return null;
  }
  const upgrade = entity.upgrade;
  if (upgrade === undefined || upgrade.to.trim() === "") {
    return null;
  }
  const targetName = labelForPath(upgrade.to).trim() || upgrade.to;
  const affordable =
    player !== undefined &&
    Number.isFinite(player.gold) &&
    Number.isFinite(player.wood) &&
    player.gold >= upgrade.gold &&
    player.wood >= upgrade.wood;
  return {
    buildingId: entity.id,
    target: upgrade.to,
    targetName,
    gold: upgrade.gold,
    wood: upgrade.wood,
    affordable,
    disabled: !affordable,
    label: `${UPGRADE_ACTION_LABEL} · ${targetName} · ${upgradeCostLabel(upgrade.gold, upgrade.wood)}`,
  };
}
