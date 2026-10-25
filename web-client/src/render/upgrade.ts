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

import { upgrade } from "../commands.ts";
import {
  isBuildingEntityKind,
  kindName,
  ownershipOf,
  underConstruction,
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

/** The action word of the batch entry. */
export const UPGRADE_ALL_ACTION_LABEL = "升级全部";

/** Why one building of a batch is not sent. */
export type UpgradeSkipReason = "unfinished" | "no-successor" | "unaffordable";

/** One building the batch will not send, with the readable reason shown in the log and tooltip. */
export interface UpgradeSkip {
  readonly buildingId: number;
  readonly reason: UpgradeSkipReason;
  /** `未完工` / `没有后续等级` / `资源不足`. */
  readonly text: string;
}

/** The outcome of planning one batch upgrade click. */
export interface UpgradeBatchPlan {
  /** `UPGRADE <id>` lines in selection order; the first line is the first affordable building. */
  readonly lines: readonly string[];
  /** Ids of the buildings named by {@link lines}, in the same order. */
  readonly buildingIds: readonly number[];
  /** Buildings deliberately left out, each with its reason. */
  readonly skipped: readonly UpgradeSkip[];
  /** `已发送 N 条升级 / M 条被拒绝`. */
  readonly summary: string;
}

const SKIP_TEXT: Readonly<Record<UpgradeSkipReason, string>> = {
  unfinished: "未完工",
  "no-successor": "没有后续等级",
  unaffordable: "资源不足",
};

/** The readable reason of one skipped building: `未完工`, ... */
export function upgradeSkipText(reason: UpgradeSkipReason): string {
  return SKIP_TEXT[reason];
}

/** `已发送 N 条升级 / M 条被拒绝`. */
export function upgradeBatchSummary(sent: number, rejected: number): string {
  return `已发送 ${sent} 条升级 / ${rejected} 条被拒绝`;
}

/**
 * Plans the `升级全部` action for a same-kind group of own buildings.
 *
 * <p>Item 25 is a client batch for the same reason item 23 is: the wire has one `UPGRADE <id>` per
 * line, so "upgrade every arsenal at once" sends one line per building in selection order. This
 * function only decides which buildings get a line and why the rest do not; the caller sends exactly
 * the lines and logs each answer through the ordinary command path.
 *
 * <p>The rules, per building in selection order:
 *
 * <ul>
 *   <li>a site still under construction is skipped as `未完工` (the engine only upgrades finished
 *       buildings);</li>
 *   <li>a building without an `upgrade` offer is skipped as `没有后续等级` (top tier, or an older
 *       snapshot);</li>
 *   <li>a building whose cost the remaining budget cannot cover is skipped as `资源不足`. The budget is
 *       reduced by every line already planned, so two 140-gold upgrades from a 200-gold stock plan one
 *       line and skip the second instead of promising both;</li>
 *   <li>everything else becomes one `UPGRADE <id>` line.</li>
 * </ul>
 *
 * <p>The function returns `null` (no entry at all) unless the selection is a non-empty group of
 * **own, same-kind buildings** with at least one upgrade offer. The page needs that distinction: a
 * mixed selection or a selection with nothing to upgrade must not paint a `升级全部` button.
 *
 * @param selection   the selected entities, in selection order
 * @param myPlayerId  the local player id answered by `WHOAMI`, or `null`
 * @param player      the local player's own snapshot row, if the snapshot carries one
 */
export function upgradeBatchPlan(
  selection: readonly EntityView[],
  myPlayerId: number | null,
  player: PlayerView | undefined,
): UpgradeBatchPlan | null {
  if (selection.length === 0) {
    return null;
  }
  const first = selection[0];
  if (first === undefined || !isBuildingEntityKind(first.kind)) {
    return null;
  }
  for (const entity of selection) {
    if (!isBuildingEntityKind(entity.kind)) {
      return null;
    }
    if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
      return null;
    }
    if (kindName(entity.kind) !== kindName(first.kind)) {
      return null;
    }
  }
  const hasOffer = selection.some(
    (entity) => entity.upgrade !== undefined && entity.upgrade.to.trim() !== "",
  );
  if (!hasOffer) {
    return null;
  }

  let remainingGold = player !== undefined && Number.isFinite(player.gold) ? player.gold : -1;
  let remainingWood = player !== undefined && Number.isFinite(player.wood) ? player.wood : -1;
  const lines: string[] = [];
  const buildingIds: number[] = [];
  const skipped: UpgradeSkip[] = [];
  for (const entity of selection) {
    let reason: UpgradeSkipReason | null = null;
    const offer = entity.upgrade;
    if (underConstruction(entity)) {
      reason = "unfinished";
    } else if (offer === undefined || offer.to.trim() === "") {
      reason = "no-successor";
    } else if (offer.gold > remainingGold || offer.wood > remainingWood) {
      reason = "unaffordable";
    }
    if (reason !== null) {
      skipped.push({ buildingId: entity.id, reason, text: upgradeSkipText(reason) });
      continue;
    }
    if (offer === undefined) {
      // Unreachable after the reason check above, but keeps the compiler honest without a non-null
      // assertion in a pure rule.
      continue;
    }
    lines.push(upgrade(entity.id));
    buildingIds.push(entity.id);
    remainingGold -= offer.gold;
    remainingWood -= offer.wood;
  }
  return {
    lines,
    buildingIds,
    skipped,
    summary: upgradeBatchSummary(lines.length, skipped.length),
  };
}
