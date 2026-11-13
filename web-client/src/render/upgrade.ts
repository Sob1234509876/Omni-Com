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
import { t } from "../i18n.ts";
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

/** The action word in front of every upgrade label: `升级` in zh-CN, `Upgrade` in en. */
function upgradeActionLabel(): string {
  return t("action.upgrade");
}

/**
 * Formats one amount for the label; kept here so the check can assert the same text the page shows.
 * The two words are the currency labels (`item.gold` / `item.wood`), so the line reads `金 140 · 木 40`
 * in Chinese and `gold 140 · wood 40` in English.
 */
export function upgradeCostLabel(gold: number, wood: number): string {
  return `${t("item.gold")} ${gold} · ${t("item.wood")} ${wood}`;
}

/**
 * Resolves one upgrade entry per variant of a selection, in the order the content declared them.
 *
 * A plain ladder yields exactly one entry; the classic armory t1 step (taller armory or purgatory
 * factory) yields two, which the HUD paints as one 升级 button per variant. The same ownership rules as
 * before apply: only an own building offers entries, a building without an offer yields none.
 *
 * @param entity      the single selected entity, if exactly one is selected
 * @param myPlayerId  the local player id answered by `WHOAMI`, or `null`
 * @param player      the local player's own snapshot row, if the snapshot carries one
 * @param labelForPath manifest display-name resolver; the raw path is used when it has no name
 */
export function upgradeEntries(
  entity: EntityView | undefined,
  myPlayerId: number | null,
  player: PlayerView | undefined,
  labelForPath: (path: string) => string = (path: string): string => path,
): UpgradeEntry[] {
  if (entity === undefined) {
    return [];
  }
  if (!isBuildingEntityKind(entity.kind)) {
    return [];
  }
  if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
    return [];
  }
  const upgrade = entity.upgrade;
  if (upgrade === undefined) {
    return [];
  }
  const entries: UpgradeEntry[] = [];
  for (const option of upgrade.options) {
    if (option.to.trim() === "") {
      continue;
    }
    const targetName = labelForPath(option.to).trim() || option.to;
    const affordable =
      player !== undefined &&
      Number.isFinite(player.gold) &&
      Number.isFinite(player.wood) &&
      player.gold >= option.gold &&
      player.wood >= option.wood;
    entries.push({
      buildingId: entity.id,
      target: option.to,
      targetName,
      gold: option.gold,
      wood: option.wood,
      affordable,
      disabled: !affordable,
      label: `${upgradeActionLabel()} · ${targetName} · ${upgradeCostLabel(option.gold, option.wood)}`,
    });
  }
  return entries;
}

/**
 * Resolves the upgrade entry for a selection, or `null` when the selection offers none.
 *
 * This is the first variant of {@link upgradeEntries}: the ordinary ladder step a plain building offers.
 * A variant step's further options come from {@link upgradeEntries}, which the HUD paints as additional
 * 升级 buttons.
 */
export function upgradeEntry(
  entity: EntityView | undefined,
  myPlayerId: number | null,
  player: PlayerView | undefined,
  labelForPath: (path: string) => string = (path: string): string => path,
): UpgradeEntry | null {
  return upgradeEntries(entity, myPlayerId, player, labelForPath)[0] ?? null;
}

/**
 * The 升级 entry a selected own unit may offer, or `null` when it offers none.
 *
 * A unit row's `upgrade` field carries the single successor the `UPGRADEUNIT <unitId>` command creates
 * — the same `{to, gold[, wood]}` shape as a building's offer, so one entry type mirrors
 * {@link UpgradeEntry} with the unit id in place of the building id. The same ownership rules apply:
 * only an own unit offers the entry, an enemy or neutral row with the field is ignored, and a missing
 * player row shows the entry disabled rather than hiding it.
 */
export interface UnitUpgradeEntry {
  /** Unit id the `UPGRADEUNIT <id>` command names. */
  readonly unitId: number;
  /** Target content path from the snapshot's `upgrade.to`. */
  readonly target: string;
  /** Display name of the target, resolved through the manifest label function. */
  readonly targetName: string;
  /** Gold the server will charge. */
  readonly gold: number;
  /** Wood the server will charge; a wire entry without one reads as `0`. */
  readonly wood: number;
  /** Whether the local player's row covers both amounts. */
  readonly affordable: boolean;
  /** True exactly when the entry must be rendered disabled. */
  readonly disabled: boolean;
  /** Full button label: `升级 · 工程师·高级 · 金 15 · 木 0`. */
  readonly label: string;
}

/**
 * Resolves the `UPGRADEUNIT` entry for a selected unit, or `null` when there is none.
 *
 * Only a non-building owned by the local player qualifies; the offer must name a non-empty target.
 * Affordability is the same client-side rule the building upgrade uses over the local player's row.
 */
export function unitUpgradeEntry(
  entity: EntityView | undefined,
  myPlayerId: number | null,
  player: PlayerView | undefined,
  labelForPath: (path: string) => string = (path: string): string => path,
): UnitUpgradeEntry | null {
  if (entity === undefined || isBuildingEntityKind(entity.kind)) {
    return null;
  }
  if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
    return null;
  }
  const option = entity.upgrade?.options[0];
  if (option === undefined || option.to.trim() === "") {
    return null;
  }
  const targetName = labelForPath(option.to).trim() || option.to;
  const affordable =
    player !== undefined &&
    Number.isFinite(player.gold) &&
    Number.isFinite(player.wood) &&
    player.gold >= option.gold &&
    player.wood >= option.wood;
  return {
    unitId: entity.id,
    target: option.to,
    targetName,
    gold: option.gold,
    wood: option.wood,
    affordable,
    disabled: !affordable,
    label: `${upgradeActionLabel()} · ${targetName} · ${upgradeCostLabel(option.gold, option.wood)}`,
  };
}

/**
 * The full 批量 button label of one variant: `批量 · <targetName> · 金 n · 木 m`.
 *
 * The variant's cost comes from the first selected building's option at that index, so the operator
 * sees what the route does and what it costs before clicking. The action word is the
 * `action.batch` label (`批量` / `Batch`).
 */
export function upgradeBatchLabel(targetName: string, gold: number, wood: number): string {
  return `${t("action.batch")} · ${targetName} · ${upgradeCostLabel(gold, wood)}`;
}

/** Why one building of a batch is not sent. */
export type UpgradeSkipReason = "unfinished" | "no-successor" | "no-variant" | "unaffordable";

/** One building the batch will not send, with the readable reason shown in the log and tooltip. */
export interface UpgradeSkip {
  readonly buildingId: number;
  readonly reason: UpgradeSkipReason;
  /** `未完工` / `没有后续等级` / `没有该升级路线` / `资源不足`. */
  readonly text: string;
}

/** The outcome of planning one batch upgrade click. */
export interface UpgradeBatchPlan {
  /** Content path of the variant this plan walks, for the button label. */
  readonly targetName: string;
  /** Gold the variant charges per building. */
  readonly gold: number;
  /** Wood the variant charges per building. */
  readonly wood: number;
  /** `UPGRADE <id>` lines in selection order; the first line is the first affordable building. */
  readonly lines: readonly string[];
  /** Ids of the buildings named by {@link lines}, in the same order. */
  readonly buildingIds: readonly number[];
  /** Buildings deliberately left out, each with its reason. */
  readonly skipped: readonly UpgradeSkip[];
  /** `已发送 N 条升级 / M 条被拒绝`. */
  readonly summary: string;
  /** The variant index this plan walks; `0` is the ordinary ladder step. */
  readonly variantIndex: number;
}

/** The skip reason of one building, resolved through the `skip.*` UI labels. */
function skipText(reason: UpgradeSkipReason): string {
  switch (reason) {
    case "unfinished":
      return t("skip.unfinished");
    case "no-successor":
      return t("skip.noSuccessor");
    case "no-variant":
      return t("skip.noVariant");
    default:
      return t("skip.unaffordable");
  }
}

/** The readable reason of one skipped building: `未完工`, ... */
export function upgradeSkipText(reason: UpgradeSkipReason): string {
  return skipText(reason);
}

/** `已发送 N 条升级 / M 条被拒绝` (zh-CN) or `sent N upgrade(s) / M refused` (en). */
export function upgradeBatchSummary(sent: number, rejected: number): string {
  return t("upgrade.summary", { sent, rejected });
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
 *   <li>a building without an `upgrade` offer at all is skipped as `没有后续等级` (top tier, or an
 *       older snapshot);</li>
 *   <li>a building whose options list is shorter than `variantIndex` (or whose option at that index is
 *       malformed) is skipped as `没有该升级路线` — the multi-route step offers the batch per variant,
 *       and a building that does not grow that way must not be dragged along;</li>
 *   <li>a building whose cost the remaining budget cannot cover is skipped as `资源不足`. The budget is
 *       reduced by every line already planned, so two 140-gold upgrades from a 200-gold stock plan one
 *       line and skip the second instead of promising both;</li>
 *   <li>everything else becomes one `UPGRADE <id> <variantPath>` line for the option at
 *       `variantIndex` — the page computes one plan per variant of the first selected building's kind
 *       and paints one 批量 button per plan.</li>
 * </ul>
 *
 * <p>The function returns `null` (no entry at all) unless the selection is a non-empty group of
 * **own, same-kind buildings** with at least one upgrade offer. The page needs that distinction: a
 * mixed selection or a selection with nothing to upgrade must not paint a 批量 button.
 *
 * @param selection    the selected entities, in selection order
 * @param myPlayerId   the local player id answered by `WHOAMI`, or `null`
 * @param player       the local player's own snapshot row, if the snapshot carries one
 * @param variantIndex the upgrade option each eligible building walks; `0` (the default) is the
 *                     ordinary ladder step the historical `升级全部` walked
 */
export function upgradeBatchPlan(
  selection: readonly EntityView[],
  myPlayerId: number | null,
  player: PlayerView | undefined,
  variantIndex = 0,
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
    (entity) => entity.upgrade !== undefined && entity.upgrade.options.length > 0,
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
    const offer = entity.upgrade?.options[variantIndex];
    if (underConstruction(entity)) {
      reason = "unfinished";
    } else if (entity.upgrade === undefined) {
      reason = "no-successor";
    } else if (offer === undefined || offer.to.trim() === "") {
      reason = "no-variant";
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
    lines.push(upgrade(entity.id, offer.to));
    buildingIds.push(entity.id);
    remainingGold -= offer.gold;
    remainingWood -= offer.wood;
  }
  const winner = selection.find((entity) => entity.upgrade?.options[variantIndex] !== undefined);
  const priced = winner?.upgrade?.options[variantIndex];
  return {
    targetName: priced === undefined ? "" : priced.to,
    gold: priced === undefined ? 0 : priced.gold,
    wood: priced === undefined ? 0 : priced.wood,
    lines,
    buildingIds,
    skipped,
    summary: upgradeBatchSummary(lines.length, skipped.length),
    variantIndex,
  };
}
