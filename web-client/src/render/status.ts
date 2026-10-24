/**
 * Pure presentation rules for entity status chrome.
 *
 * These are the exact strings and ratios the HUD and the building render pass use, kept DOM-free and
 * canvas-free so `scripts/check-render-math.ts` can assert them without a browser. Every value is derived
 * from the optional snapshot fields `w`, `h`, `progress`, `queue`, `queueTicks`, `queueItems` and
 * `contents`; the snapshot may omit them (an older server), so every helper has to accept their absence.
 */

import type { EntityView, QueueItemView } from "./view";

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
 * The small production marker: `生产中 ×N` for a non-empty queue.
 *
 * An absent `queue` (older snapshot, or a unit row) and a count of zero both mean "nothing queued", so
 * they return the empty string and the renderer draws no marker at all.
 */
export function queueMarkerText(queue: number | undefined): string {
  if (queue === undefined || !Number.isFinite(queue)) {
    return "";
  }
  const count = Math.max(0, Math.trunc(queue));
  return count === 0 ? "" : `生产中 ×${count}`;
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

function compareItemPaths(left: string, right: string): number {
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
 * Only positive integer amounts are shown, in a fixed item order, and a row with no `contents` (a unit,
 * a non-warehouse building, an empty warehouse or an older server) answers `null` so the HUD adds no
 * label. The wording is the selection HUD's, not the wire format's: the snapshot keeps `gold` / `wood`.
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
