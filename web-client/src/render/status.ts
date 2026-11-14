/**
 * Pure presentation rules for entity status chrome.
 *
 * These are the exact strings and ratios the HUD and the building render pass use, kept DOM-free and
 * canvas-free so `scripts/check-render-math.ts` can assert them without a browser. Every value is derived
 * from the optional snapshot fields `w`, `h`, `progress`, `queue`, `queueTicks`, `queueItems` and
 * `contents`; the snapshot may omit them (an older server), so every helper has to accept their absence.
 */

import { t } from "../i18n.ts";
import {
  isBuildingEntityKind,
  type EntityView,
  type ProductionDemandView,
  type ProducingNowView,
  type QueueItemView,
} from "./view.ts";

/** Clamps a completion fraction to `[0, 1]`; a non-finite value collapses to `0`. */
export function progressBarRatio(progress: number): number {
  if (!Number.isFinite(progress)) {
    return 0;
  }
  return Math.min(1, Math.max(0, progress));
}

/** Formats a completion fraction as a whole percentage, clamped to `0%..100%`. */
export function formatProgressPercent(progress: number): string {
  return `${Math.round(progressBarRatio(progress) * 100)}%`;
}

/**
 * The small production marker: `生产中 <position>/<length>` when the snapshot carries `producingNow`,
 * otherwise the legacy `生产中 ×N` for a non-empty queue.
 *
 * An absent `producingNow` and an absent `queue` (older snapshot, or a unit row) fall back to the old
 * marker, and a count of zero returns the empty string so the renderer draws no marker at all. A
 * malformed `producingNow` is ignored rather than printing `生产中 NaN/undefined`.
 */
export function queueMarkerText(
  queue: number | undefined,
  producingNow?: ProducingNowView,
): string {
  if (
    producingNow !== undefined &&
    typeof producingNow.kind === "string" &&
    producingNow.kind.trim() !== "" &&
    Number.isSafeInteger(producingNow.position) &&
    Number.isSafeInteger(producingNow.length) &&
    producingNow.position >= 1 &&
    producingNow.length >= producingNow.position
  ) {
    return t("status.producingNow", {
      position: producingNow.position,
      length: producingNow.length,
    });
  }
  if (queue === undefined || !Number.isFinite(queue)) {
    return "";
  }
  const count = Math.max(0, Math.trunc(queue));
  return count === 0 ? "" : t("status.producingCount", { count });
}

/**
 * The tiny demand icon beside a queue row: a hollow check when the job has its inputs, a warning mark
 * when it does not.
 *
 * A row without `demand` has nothing to say, so the empty string makes the caller skip the icon.
 */
export function demandIcon(demand: ProductionDemandView | undefined): string {
  if (demand === undefined) {
    return "";
  }
  return demand.ready ? "✓" : "⚠";
}

/**
 * The missing-inputs text of a warning: `缺：金 80/50 · 木 20/10`, each pair in **need/have** order.
 *
 * Only inputs with `have < need` are listed, in the recipe's own order; a ready demand or a demand with
 * no short input answers the empty string. `labelForPath` resolves the manifest display name, with the
 * same fixed `金` / `木` labels the stock and cargo chips use as the fallback.
 */
export function demandMissingText(
  demand: ProductionDemandView | undefined,
  labelForPath: (path: string) => string = itemDisplayName,
): string {
  if (demand === undefined || demand.ready) {
    return "";
  }
  const parts: string[] = [];
  for (const item of demand.items) {
    if (!Number.isSafeInteger(item.need) || !Number.isSafeInteger(item.have) || item.have >= item.need) {
      continue;
    }
    const label = labelForPath(item.item).trim();
    parts.push(`${label === "" ? item.item : label} ${item.need}/${item.have}`);
  }
  return parts.length === 0 ? "" : t("status.missingLabel", { items: parts.join(" · ") });
}

/** Total number of carried items, counting every positive `contents` amount. */
function carriedTotal(contents: Readonly<Record<string, number>> | undefined): number {
  if (contents === undefined) {
    return 0;
  }
  let total = 0;
  for (const amount of Object.values(contents)) {
    if (Number.isSafeInteger(amount) && amount > 0) {
      total += amount;
    }
  }
  return total;
}

/**
 * The compact tag above a loaded unit: every item the hold carries, in the shared stable order.
 *
 * <p>Q9 asks for "all the items", so the tag lists each positive stack, for example `金 80 · 木 40`.
 * A unit with no cargo, an older snapshot without `contents` and a building row answer `null`, so the
 * renderer draws no tag. The order is the shared one (gold, wood, then paths lexicographically), so the
 * same hold always produces the same tag.
 */
export function unitStockTagLabel(
  entity: EntityView,
  labelForPath: (path: string) => string = itemDisplayName,
): string | null {
  if (isBuildingEntityKind(entity.kind)) {
    return null;
  }
  const contents = entity.contents;
  if (contents === undefined) {
    return null;
  }
  const parts: string[] = [];
  for (const path of Object.keys(contents).sort(compareItemPaths)) {
    const amount = contents[path];
    if (amount === undefined || !Number.isSafeInteger(amount) || amount <= 0) {
      continue;
    }
    const label = labelForPath(path).trim();
    parts.push(`${label === "" ? path : label} ${amount}`);
  }
  return parts.length === 0 ? null : parts.join(" · ");
}

/**
 * Whether a unit's cargo tag should be drawn this frame.
 *
 * <p>The playtester rule: EVERY unit that carries anything shows its tag always — own, enemy or
 * neutral, no hover and no ownership gate, because a hauler's load is at-a-glance information about
 * the world, not a hover secret. The renderer only walks entities inside the loaded window, so "in
 * window" is the caller's concern, not this predicate's. A building never gets the tag (its stock
 * belongs to the warehouse panel) and an empty hold never draws one.
 */
export function cargoTagVisible(entity: EntityView): boolean {
  return !isBuildingEntityKind(entity.kind) && carriedTotal(entity.contents) > 0;
}

/**
 * The combat chip of a unit or building: `攻击 18 · 射程 6`, or `null` when the row has no weapon.
 *
 * <p>A snapshot sends `attackDamage` and `attackRange` for any attacking entity; a building without a
 * weapon (a warehouse, a mine) and an older snapshot omit them, so no line is invented. The range is
 * printed without a decimal when it is a whole number and with one decimal otherwise, which keeps the
 * classic values readable (`6`, `7.5`, `9`).
 */
export function combatLabel(entity: EntityView): string | null {
  const damage = entity.attackDamage;
  const range = entity.attackRange;
  if (damage === undefined || range === undefined || damage <= 0 || range <= 0) {
    return null;
  }
  const rangeText = Number.isInteger(range) ? String(range) : range.toFixed(1);
  return t("status.combatLabel", { damage, range: rangeText });
}

/**
 * The order/task vocabulary: leading token → `order.*` i18n key suffix.
 *
 * <p>The server is switching the snapshot's `orderQueue` / `task` strings from authored Chinese
 * sentences to stable ASCII tokens; until the exact vocabulary lands this table accepts every token
 * the client expects (`move` / `attack` / `attackMove` / `build` / `repair` / `haul` / `transfer` /
 * `travel` / `guard`, plus the `hold` / `idle` / `more` bookkeeping the current labels imply) AND the
 * legacy Chinese leading words the engine sends today, so an old server keeps rendering. ASCII heads
 * are matched case-insensitively; anything else answers the raw string unchanged.
 */
const ORDER_TOKEN_KEYS: Readonly<Record<string, string>> = {
  move: "move",
  attack: "attack",
  attackmove: "attackMove",
  attack_move: "attackMove",
  build: "build",
  repair: "repair",
  haul: "haul",
  transfer: "transfer",
  travel: "travel",
  guard: "guard",
  hold: "hold",
  idle: "idle",
  more: "more",
  // The legacy Chinese heads (old-server tolerance): the verbs of `OrderLabels` and `UnitTask`.
  "移动": "move",
  "攻击": "attack",
  "攻击移动": "attackMove",
  "建造": "build",
  "建造中": "building",
  "建造（已完成）": "buildDone",
  "修理": "repair",
  "修理中": "repairing",
  "修理（已完成）": "repairDone",
  "搬运": "haul",
  "搬运中": "hauling",
  "原地待命": "hold",
  "空闲": "idle",
};

/** The legacy truncation marker `… 还有 N 项`, whose count is re-rendered through `order.more`. */
const LEGACY_TRUNCATION = /^…\s*还有\s*(\d+)\s*项$/;

/**
 * One localized order/task label from one raw snapshot string.
 *
 * <p>The head (everything before the first whitespace) is looked up in {@link ORDER_TOKEN_KEYS} —
 * ASCII heads lowercased first — and replaced with the `order.<key>` label; the remainder is kept
 * verbatim (an old server may carry a Chinese display name there, which the client must not invent a
 * translation for). An unknown head answers the raw string unchanged, so a future server token the
 * client does not know still prints instead of disappearing. The legacy truncation marker
 * `… 还有 N 项` is matched whole and re-rendered with the count through `order.more`.
 */
export function orderLabel(raw: string): string {
  const text = raw.trim();
  const truncation = text.match(LEGACY_TRUNCATION);
  if (truncation !== null) {
    return t("order.more", { count: Number(truncation[1]) });
  }
  const split = text.search(/\s/u);
  const head = split < 0 ? text : text.slice(0, split);
  const rest = split < 0 ? "" : text.slice(split);
  const asciiHead = head.toLowerCase();
  const key = ORDER_TOKEN_KEYS[head] ?? ORDER_TOKEN_KEYS[asciiHead];
  if (key === undefined) {
    return text;
  }
  return t(`order.${key}`) + rest;
}

/**
 * The selected engineer's task line, `任务：搬运中 → 兵工厂`, or `null` when the row has no task.
 *
 * The task string goes through {@link orderLabel}, so a token-speaking server reads localized while an
 * old Chinese-speaking server reads exactly as before; a blank string still hides the row.
 */
export function unitTaskLabel(task: string | undefined): string | null {
  if (task === undefined || task.trim() === "") {
    return null;
  }
  return t("status.taskLabel", { task: orderLabel(task) });
}

/**
 * The production rate suffix of a resource: ` (+30/分)`, or the empty string when there is no rate.
 *
 * A legacy player row has no `goldPerMinute` / `woodPerMinute`, so the HUD must not print `+0/分` for it.
 * A rate the server does report is printed even when it is zero, because "this player has no mine" is
 * useful information; a malformed negative or non-finite value is treated like an absent one.
 */
export function incomeSuffix(rate: number | undefined): string {
  if (rate === undefined || !Number.isFinite(rate) || rate < 0) {
    return "";
  }
  return t("status.incomeSuffix", { rate: Math.trunc(rate) });
}

/**
 * Progress of one queue item, clamped to `[0, 1]`.
 *
 * The server reports `ticks` as the remaining time of the job currently producing and `total` as the
 * recipe duration; a job not started yet reads `ticks === total`, so its bar starts empty. A malformed
 * `total` (zero or negative) has no meaningful fraction and collapses to `0`.
 */
export function queueProgressRatio(item: QueueItemView): number {
  if (!Number.isFinite(item.total) || item.total <= 0 || !Number.isFinite(item.ticks)) {
    return 0;
  }
  return Math.min(1, Math.max(0, item.ticks / item.total));
}

/**
 * The `i/n` label of one queue entry, with `index` zero-based as it comes from `queueItems`.
 *
 * The label is one-based for the operator (`1/2` is the first of two jobs). A non-positive count still
 * answers `0/0` rather than throwing, because this is presentation code for an optional field.
 */
export function queueItemNumber(index: number, count: number): string {
  const total = Math.max(0, Math.trunc(count));
  if (total === 0) {
    return "0/0";
  }
  const position = Math.min(total, Math.max(1, Math.trunc(index) + 1));
  return `${position}/${total}`;
}

/**
 * The footprint chip text: `尺寸：W×H`.
 *
 * A row without a footprint (an older snapshot, or the demo world) has no size to show, so the helper
 * answers `null` rather than inventing `1×1`.
 */
export function sizeLabel(entity: EntityView): string | null {
  const footprint = entity.footprint;
  return footprint === undefined
    ? null
    : t("status.sizeLabel", { size: `${footprint.width}×${footprint.height}` });
}

/** Known items first, then unknown paths in plain lexicographic order, so the HUD line is stable. */
const ITEM_ORDER: readonly string[] = ["gold", "wood"];

function itemRank(path: string): number {
  const index = ITEM_ORDER.indexOf(path);
  return index < 0 ? ITEM_ORDER.length : index;
}

/**
 * The stable order of item paths: the two classic items first (`gold`, `wood`), then every other path
 * lexicographically. The HUD chips, the warehouse rows and the marked-resource line all use it, so the
 * same two resources never swap places between two surfaces.
 */
export function compareItemPaths(left: string, right: string): number {
  const byRank = itemRank(left) - itemRank(right);
  if (byRank !== 0) {
    return byRank;
  }
  return left < right ? -1 : left > right ? 1 : 0;
}

/**
 * The display name of one resource item: the `item.gold` / `item.wood` UI label for the two wire
 * currency paths (金 / 木 in zh-CN, gold / wood in en), otherwise the path's own name with any
 * namespace prefix removed. The warehouse panel uses the same fallback for unknown items, so the HUD
 * chip and the panel cannot disagree about a known item.
 */
export function itemDisplayName(path: string): string {
  const separator = path.lastIndexOf(":");
  const name = separator < 0 ? path : path.slice(separator + 1);
  return name === "gold" || name === "wood" ? t(`item.${name}`) : name;
}

/**
 * The warehouse stock chip: `库存：金 120 · 木 40`.
 *
 * Only positive integer amounts are shown, in a fixed item order, and a building with no `contents`
 * (a non-warehouse building, an empty warehouse or an older server) answers `null` so the HUD adds no
 * label. The wording is the selection HUD's, not the wire format's: the snapshot keeps `gold` / `wood`.
 * A unit's cargo uses {@link unitCargoLabel} instead, so the two rows cannot be confused.
 */
export function warehouseContentsLabel(entity: EntityView): string | null {
  const contents = entity.contents;
  if (contents === undefined) {
    return null;
  }
  const parts: string[] = [];
  for (const path of Object.keys(contents).sort(compareItemPaths)) {
    const amount = contents[path];
    if (amount === undefined || !Number.isSafeInteger(amount) || amount <= 0) {
      continue;
    }
    parts.push(`${itemDisplayName(path)} ${amount}`);
  }
  return parts.length === 0 ? null : t("status.stockLabel", { items: parts.join(" · ") });
}

/**
 * The selected unit's cargo chip: `携带：金 120 · 木 40`.
 *
 * The snapshot reports a unit's cargo in the same `contents` object a warehouse uses. Only positive
 * integer amounts are shown, in the same stable item order as the stock chip, and a unit with no cargo
 * (or an older snapshot without `contents`) answers `null` so the HUD adds no line.
 *
 * `labelForPath` resolves an item path through the plugin manifest, which is where item display names
 * live. A path the manifest does not know falls back to the path itself (`itemDisplayName` only maps
 * the two classic items), so an unknown item is never hidden or renamed to something misleading.
 */
export function unitCargoLabel(
  entity: EntityView,
  labelForPath: (path: string) => string = itemDisplayName,
): string | null {
  const contents = entity.contents;
  if (contents === undefined) {
    return null;
  }
  const parts: string[] = [];
  for (const path of Object.keys(contents).sort(compareItemPaths)) {
    const amount = contents[path];
    if (amount === undefined || !Number.isSafeInteger(amount) || amount <= 0) {
      continue;
    }
    const label = labelForPath(path).trim();
    parts.push(`${label === "" ? path : label} ${amount}`);
  }
  return parts.length === 0 ? null : t("status.cargoLabel", { items: parts.join(" · ") });
}
