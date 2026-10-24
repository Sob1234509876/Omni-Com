/**
 * Pure hover-tooltip rules: what the tooltip says and where it sits.
 *
 * The tooltip itself is a DOM element (never canvas) so it stays crisp, cannot be baked into a frame
 * and needs no text rasterising. This module owns the two parts worth checking offline:
 *
 * - {@link tooltipContent} composes the manifest display name, the ownership tag, the health
 *   percentage, the footprint of a building and the production-queue summary, omitting every optional
 *   field the snapshot does not carry;
 * - {@link tooltipThumbnail} chooses the fixed 48×48 thumbnail: the entity's atlas frame when the
 *   renderer has one, otherwise the neutral grey placeholder carrying the kind's first character;
 * - {@link tooltipIdleExpired} is the no-flicker rule: one unchanged hover suppresses the box after
 *   {@link TOOLTIP_IDLE_HIDE_MS} and the next entity re-arms it;
 * - {@link clampTooltipPosition} keeps the box inside the browser window, flipping it to the other
 *   side of the cursor when it would cross the right or bottom edge.
 *
 * The page styles the box as greyscale, translucent chrome with `pointer-events: none`; it never
 * captures a click meant for the world canvas.
 */

import { formatProgressPercent, queueMarkerText, sizeLabel } from "./status.ts";
import {
  isBuildingEntityKind,
  ownershipTag,
  type EntityView,
  type Ownership,
} from "./view.ts";

/** Distance in CSS pixels between the cursor and the tooltip's near corner. */
export const TOOLTIP_GAP_PX = 14;

/** Minimum distance in CSS pixels between the tooltip and the window edge. */
export const TOOLTIP_MARGIN_PX = 8;

/** The fixed square thumbnail at the left of the tooltip, in CSS pixels. */
export const TOOLTIP_THUMBNAIL_SIZE_PX = 48;

/**
 * How long one unchanged hover may keep the tooltip on screen before it suppresses itself.
 *
 * The entity under the cursor has not changed, so the box is no longer telling the operator anything
 * new and would only cover the map. Changing the hovered entity re-arms it; jitter inside the same
 * entity never re-shows it, which is what keeps the box from flickering.
 */
export const TOOLTIP_IDLE_HIDE_MS = 6_000;

/** The tooltip's fixed-size thumbnail: a real atlas frame, or the greyscale placeholder. */
export type TooltipThumbnail =
  | { readonly kind: "atlas"; readonly size: number }
  | { readonly kind: "placeholder"; readonly size: number; readonly label: string };

/** The two halves of a tooltip: a stronger name line plus the detail lines under it. */
export interface TooltipContent {
  /** Manifest display name, or the raw kind when the manifest had no name. */
  readonly name: string;
  /** Ownership tag (`我` / `敌` / `中立` / `未登录`), rendered bold on its own line. */
  readonly ownerTag: string;
  /** Short greyscale lines: health, building footprint, queue summary. */
  readonly lines: readonly string[];
}

/** A size in CSS pixels; the tooltip is measured after its text is set. */
export interface TooltipSize {
  readonly width: number;
  readonly height: number;
}

/** The browser window's inner size in CSS pixels. */
export interface TooltipViewport {
  readonly width: number;
  readonly height: number;
}

/** One point in CSS pixels, either the cursor or the clamped box corner. */
export interface TooltipPoint {
  readonly x: number;
  readonly y: number;
}

/**
 * Chooses what the thumbnail shows.
 *
 * The caller has already resolved the entity's kind through the renderer's shared atlas lookup
 * (`lookupAtlasEntity`), so `hasSprite` means the exact frame the world draws exists. When it does, the
 * thumbnail is the atlas frame at {@link TOOLTIP_THUMBNAIL_SIZE_PX}; otherwise it is a neutral grey
 * square carrying the **first character of the kind** as its only label. The size is always fixed, so
 * the thumbnail never changes the tooltip's layout when the text lines grow or shrink.
 */
export function tooltipThumbnail(
  hasSprite: boolean,
  kind: string,
  size: number = TOOLTIP_THUMBNAIL_SIZE_PX,
): TooltipThumbnail {
  const square = Number.isFinite(size) ? Math.max(1, Math.round(size)) : TOOLTIP_THUMBNAIL_SIZE_PX;
  if (hasSprite) {
    return { kind: "atlas", size: square };
  }
  return { kind: "placeholder", size: square, label: kindFirstCharacter(kind) };
}

/**
 * The first Unicode code point of the kind, or `?` for an empty/blank kind.
 *
 * `Array.from` is used rather than `charAt(0)` so an astral character (a surrogate pair) is never cut
 * in half in the placeholder.
 */
export function kindFirstCharacter(kind: string): string {
  const trimmed = kind.trim();
  if (trimmed === "") {
    return "?";
  }
  return Array.from(trimmed)[0] ?? "?";
}

/**
 * Whether an unchanged hover has stayed long enough for the tooltip to suppress itself.
 *
 * A non-finite timestamp or a non-positive timeout simply keeps the tooltip visible; the caller still
 * re-arms the timer whenever the entity under the cursor changes.
 */
export function tooltipIdleExpired(
  shownSince: number,
  now: number,
  timeout: number = TOOLTIP_IDLE_HIDE_MS,
): boolean {
  if (!Number.isFinite(shownSince) || !Number.isFinite(now) || !Number.isFinite(timeout) || timeout <= 0) {
    return false;
  }
  return now - shownSince >= timeout;
}

/**
 * The text of one entity's tooltip.
 *
 * The owner is its own field, `ownerTag`, so the page can render it in bold instead of burying it in a
 * detail line. The caller passes the shared `ownershipTagFor` label, including `未登录` when the page has
 * no local player id. A health ratio the snapshot reports becomes `生命 72%`; an old snapshot without
 * `health` omits the health part rather than inventing a full bar. A building appends its reported
 * footprint (`尺寸：3×3`) and a non-empty production queue appends the same summary the canvas marker
 * uses (`生产中 ×2`). Buildings and units share the name line - the caller passes `contentLabel(kind)`,
 * so the manifest name wins and the raw kind is the fallback.
 */
export function tooltipContent(
  name: string,
  entity: EntityView,
  ownership: Ownership,
  ownershipLabel: string = ownershipTag(ownership),
): TooltipContent {
  const detail: string[] = [];
  if (entity.healthRatio !== undefined && Number.isFinite(entity.healthRatio)) {
    detail.push(`生命 ${formatProgressPercent(entity.healthRatio)}`);
  }
  const lines: string[] = [];
  if (detail.length > 0) {
    lines.push(detail.join(" · "));
  }
  if (isBuildingEntityKind(entity.kind)) {
    const footprint = sizeLabel(entity);
    if (footprint !== null) {
      lines.push(footprint);
    }
  }
  const queue = queueMarkerText(entity.queue);
  if (queue !== "") {
    lines.push(queue);
  }
  return { name, ownerTag: ownershipLabel, lines };
}

/**
 * Places the tooltip near the cursor while keeping the whole box inside the window.
 *
 * The preferred position is below-right of the cursor. If that would cross the right edge the box is
 * moved to the left of the cursor; the same happens vertically at the bottom edge. The result is then
 * clamped to `[margin, viewport - margin - size]` in both axes. A window smaller than the box cannot
 * contain it, so the margin wins and the box simply overflows; the clamp never returns a negative
 * coordinate.
 */
export function clampTooltipPosition(
  cursor: TooltipPoint,
  size: TooltipSize,
  viewport: TooltipViewport,
  margin = TOOLTIP_MARGIN_PX,
  gap = TOOLTIP_GAP_PX,
): TooltipPoint {
  const viewportWidth = Number.isFinite(viewport.width) ? Math.max(0, viewport.width) : 0;
  const viewportHeight = Number.isFinite(viewport.height) ? Math.max(0, viewport.height) : 0;
  const width = Number.isFinite(size.width) ? Math.max(0, size.width) : 0;
  const height = Number.isFinite(size.height) ? Math.max(0, size.height) : 0;
  const cursorX = Number.isFinite(cursor.x) ? cursor.x : 0;
  const cursorY = Number.isFinite(cursor.y) ? cursor.y : 0;

  const maxLeft = Math.max(margin, viewportWidth - margin - width);
  const maxTop = Math.max(margin, viewportHeight - margin - height);

  let left = cursorX + gap;
  if (left > maxLeft) {
    left = cursorX - gap - width;
  }
  let top = cursorY + gap;
  if (top > maxTop) {
    top = cursorY - gap - height;
  }
  return {
    x: Math.min(maxLeft, Math.max(margin, left)),
    y: Math.min(maxTop, Math.max(margin, top)),
  };
}
