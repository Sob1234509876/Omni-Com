/**
 * Pure presentation rules for entity status chrome.
 *
 * These are the exact strings and ratios the HUD and the building render pass use, kept DOM-free and
 * canvas-free so `scripts/check-render-math.ts` can assert them without a browser. Every value is derived
 * from the optional snapshot fields `w`, `h`, `progress`, `queue`, `queueTicks`, `queueItems` and
 * `contents`; the snapshot may omit them (an older server), so every helper has to accept their absence.
 */

import {
  isBuildingEntityKind,
  type DemandItemView,
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
    return `生产中 ${producingNow.position}/${producingNow.length}`;
  }
  if (queue === undefined || !Number.isFinite(queue)) {
    return "";
  }
  const count = Math.max(0, Math.trunc(queue));
  return count === 0 ? "" : `生产中 ×${count}`;
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
  return parts.length === 0 ? "" : `缺：${parts.join(" · ")}`;
}

/** The items a warning is about: short inputs in recipe order. */
function shortItems(demand: ProductionDemandView | undefined): readonly DemandItemView[] {
  if (demand === undefined || demand.ready) {
    return [];
  }
  return demand.items.filter(
    (item) => Number.isSafeInteger(item.need) && Number.isSafeInteger(item.have) && item.have < item.need,
  );
}

/**
 * The compact transferable-stock number beside the demand icon: `仓 500`, or `仓 500/100` when the
 * recipe has two short inputs.
 *
 * Q10's default is "the arsenal's own stock, with the transferable warehouse amount shown next to it":
 * `ready` and the icon speak for the building buffer, and this line answers what a haul could still
 * bring from the owner's unreserved warehouse stock. Values are per short input in recipe order; an
 * item whose `stock` is absent (a pre-Q10 snapshot) or zero contributes nothing, so an old server
 * simply shows no adjacent number. A ready demand shows nothing, because the local hold already covers
 * the job.
 */
export function demandStockText(demand: ProductionDemandView | undefined): string {
  const values: string[] = [];
  for (const item of shortItems(demand)) {
    const stock = item.stock;
    if (stock === undefined || !Number.isSafeInteger(stock) || stock <= 0) {
      continue;
    }
    values.push(String(stock));
  }
  return values.length === 0 ? "" : `仓 ${values.join("/")}`;
}

/**
 * The full tooltip title behind {@link demandStockText}: `仓库可调拨：金 500 · 木 100`.
 *
 * It names the items, which the compact adjacent number cannot, so hovering the stock figure explains
 * where the numbers come from. Empty when there is no short input with a known warehouse amount.
 */
export function demandStockTitle(
  demand: ProductionDemandView | undefined,
  labelForPath: (path: string) => string = itemDisplayName,
): string {
  const parts: string[] = [];
  for (const item of shortItems(demand)) {
    const stock = item.stock;
    if (stock === undefined || !Number.isSafeInteger(stock) || stock <= 0) {
      continue;
    }
    const label = labelForPath(item.item).trim();
    parts.push(`${label === "" ? item.item : label} ${stock}`);
  }
  return parts.length === 0 ? "" : `仓库可调拨：${parts.join(" · ")}`;
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
 * The compact tag above a loaded unit: the first item's name and amount, plus the total when the hold
 * holds more than that one kind.
 *
 * Examples are `金 120` for a single stack and `金 80 · 共 120` for a mixed hold. A unit with no cargo,
 * an older snapshot without `contents` and a building row answer `null`, so the renderer draws no tag.
 * The first item follows the shared stable order (gold, wood, then paths lexicographically), so the same
 * hold always produces the same tag.
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
  const paths = Object.keys(contents)
    .filter((path) => {
      const amount = contents[path];
      return amount !== undefined && Number.isSafeInteger(amount) && amount > 0;
    })
    .sort(compareItemPaths);
  const firstPath = paths[0];
  if (firstPath === undefined) {
    return null;
  }
  const firstAmount = contents[firstPath] ?? 0;
  const total = carriedTotal(contents);
  const label = labelForPath(firstPath).trim();
  const name = label === "" ? firstPath : label;
  return total > firstAmount ? `${name} ${firstAmount} · 共 ${total}` : `${name} ${firstAmount}`;
}

/**
 * Whether a unit's stock tag may be drawn: the unit must be selected, carry at least one item, and stand
 * within {@link STOCK_TAG_RADIUS_TILES} tiles of the camera centre.
 *
 * The radius is deliberately small: the tag is a close-up read-out, not a map label, so it never
 * clutters a wide view. `centreX` / `centreY` are in absolute world tiles, the same space as
 * `EntityView.x` / `y`; distance is Euclidean and inclusive at the radius.
 */
export const STOCK_TAG_RADIUS_TILES = 6;

export function stockTagVisible(
  entity: EntityView,
  selected: boolean,
  centreX: number,
  centreY: number,
  radiusTiles: number = STOCK_TAG_RADIUS_TILES,
): boolean {
  if (!selected || isBuildingEntityKind(entity.kind)) {
    return false;
  }
  if (carriedTotal(entity.contents) <= 0) {
    return false;
  }
  if (!Number.isFinite(centreX) || !Number.isFinite(centreY)) {
    return false;
  }
  const radius = Number.isFinite(radiusTiles) ? Math.max(0, radiusTiles) : STOCK_TAG_RADIUS_TILES;
  return Math.hypot(entity.x - centreX, entity.y - centreY) <= radius;
}

/**
 * The selected engineer's task line, `任务：搬运中 → 兵工厂`, or `null` when the row has no task.
 *
 * The server owns the task vocabulary; this helper only rejects a blank string and adds the HUD prefix,
 * so an unknown future task still reads correctly instead of being hidden.
 */
export function unitTaskLabel(task: string | undefined): string | null {
  if (task === undefined || task.trim() === "") {
    return null;
  }
  return `任务：${task.trim()}`;
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
  return ` (+${Math.trunc(rate)}/分)`;
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
  return footprint === undefined ? null : `尺寸：${footprint.width}×${footprint.height}`;
}

/** Chinese HUD labels for the classic items; any other item path keeps its own name. */
const ITEM_LABELS: Readonly<Record<string, string>> = {
  gold: "金",
  wood: "木",
};

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
 * The display name of one resource item: `金` / `木` for the classic items, otherwise the path's own
 * name with any namespace prefix removed. The warehouse panel uses the same fallback for unknown
 * items, so the HUD chip and the panel cannot disagree about a known item.
 */
export function itemDisplayName(path: string): string {
  const separator = path.lastIndexOf(":");
  const name = separator < 0 ? path : path.slice(separator + 1);
  return ITEM_LABELS[name] ?? name;
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
  return parts.length === 0 ? null : `库存：${parts.join(" · ")}`;
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
  return parts.length === 0 ? null : `携带：${parts.join(" · ")}`;
}
