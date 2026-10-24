/**
 * Pure presentation rules for entity status chrome.
 *
 * These are the exact strings and ratios the HUD and the building render pass use, kept DOM-free and
 * canvas-free so `scripts/check-render-math.ts` can assert them without a browser. Every value is derived
 * from the optional snapshot fields `w`, `h`, `progress`, `queue` and `queueTicks`; the snapshot may omit
 * them (an older server), so every helper has to accept their absence.
 */

import type { EntityView } from "./view";

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
 * The footprint chip text: `尺寸：W×H`.
 *
 * A row without a footprint (an older snapshot, or the demo world) has no size to show, so the helper
 * answers `null` rather than inventing `1×1`.
 */
export function sizeLabel(entity: EntityView): string | null {
  const footprint = entity.footprint;
  return footprint === undefined ? null : `尺寸：${footprint.width}×${footprint.height}`;
}
