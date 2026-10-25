/**
 * Pure lifetime, scaling and edge rules for the order crosshair.
 *
 * A move or attack order spawns one large achromatic crosshair at the pointer's screen position. It is
 * canvas content, drawn above the terrain and sprites, and must never intercept input: the page only
 * hands the animation loop a start time and a point, so this module stays DOM-free.
 *
 * The coordinate rule is explicit because the two spaces differ: pointer events arrive in **CSS pixels
 * relative to the canvas**, while the crosshair is stored and drawn in **canvas backing pixels**. The
 * renderer scales its context by `devicePixelRatio`, but this module resets that transform for the
 * crosshair pass, so {@link screenToBackingPixels} is the one conversion the page has to perform. The
 * function is checked at 1× and 2×.
 *
 * The animation is a 350 ms curve: progress 0..1, the outer radius scales from 0.6 to 1.4 times its
 * base, and alpha fades linearly from 1 to 0. {@link clampCrosshairToCanvas} keeps a crosshair spawned
 * next to an edge fully visible: it moves the centre at most a couple of pixels inside and shrinks the
 * radius to the remaining room, so no part is ever clipped.
 */

import type { Point } from "./camera.ts";

/** How long one order crosshair lives, in milliseconds. */
export const CROSSHAIR_MS = 350;

/** Outer scale at the start of the animation. */
export const CROSSHAIR_SCALE_START = 0.6;

/** Outer scale at the end of the animation. */
export const CROSSHAIR_SCALE_END = 1.4;

/** Base outer radius in CSS pixels at 1×; multiplied by the curve and by the device pixel ratio. */
export const CROSSHAIR_BASE_RADIUS_PX = 26;

/**
 * The crosshair colour.
 *
 * White only: the chrome around the canvas is greyscale, and the canvas must not introduce a hue for an
 * order marker. The second cross is drawn at a lower alpha, never in another colour.
 */
export const CROSSHAIR_COLOR = "#ffffff";

/** How far inside the canvas an edge crosshair may be nudged so its thinnest ring stays visible. */
export const CROSSHAIR_EDGE_INSET_PX = 2;

/**
 * A crosshair's stored state.
 *
 * `x` and `y` are **canvas backing pixels**, already converted from the pointer's CSS position when
 * the order was dispatched. Keeping the converted point means the animation cannot drift if the layout
 * changes after the click.
 */
export interface CrosshairState {
  readonly x: number;
  readonly y: number;
  readonly startedAt: number;
}

/** The current fade/scale samples of one crosshair. */
export interface CrosshairFrame {
  /** Linear 0..1 lifetime; 1 means finished and should no longer be drawn. */
  readonly progress: number;
  /** Outer radius multiplier, 0.6 at progress 0 and 1.4 at progress 1. */
  readonly scale: number;
  /** Opacity, 1 at progress 0 and 0 at progress 1. */
  readonly alpha: number;
  /** Whether this frame should be drawn at all. */
  readonly visible: boolean;
}

/** A clamped crosshair placement in backing pixels. */
export interface CrosshairGeometry {
  readonly x: number;
  readonly y: number;
  readonly radius: number;
}

function clamp01(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.min(1, Math.max(0, value));
}

/** Converts a pointer point measured in canvas CSS pixels to canvas backing pixels. */
export function screenToBackingPixels(point: Point, devicePixelRatio: number): Point {
  const ratio =
    Number.isFinite(devicePixelRatio) && devicePixelRatio > 0 ? devicePixelRatio : 1;
  return { x: point.x * ratio, y: point.y * ratio };
}

/**
 * The crosshair's lifetime fraction: 0 at its start, 1 at and after `durationMs`.
 *
 * A clock that appears to run backwards clamps to 0 (the animation is at its beginning, not negative),
 * and a non-positive or non-finite duration counts as finished so nothing is drawn forever.
 */
export function crosshairProgress(
  startedAt: number,
  now: number,
  durationMs = CROSSHAIR_MS,
): number {
  if (!Number.isFinite(durationMs) || durationMs <= 0) {
    return 1;
  }
  const elapsed = now - startedAt;
  if (!Number.isFinite(elapsed) || elapsed <= 0) {
    return 0;
  }
  if (elapsed >= durationMs) {
    return 1;
  }
  return elapsed / durationMs;
}

/** Outer scale for a progress fraction: linear from {@link CROSSHAIR_SCALE_START} to the end value. */
export function crosshairScale(progress: number): number {
  const fraction = clamp01(progress);
  return CROSSHAIR_SCALE_START + (CROSSHAIR_SCALE_END - CROSSHAIR_SCALE_START) * fraction;
}

/** Opacity for a progress fraction: linear 1 -> 0. */
export function crosshairAlpha(progress: number): number {
  return 1 - clamp01(progress);
}

/** Outer radius in backing pixels for a progress fraction and device pixel ratio. */
export function crosshairRadius(progress: number, devicePixelRatio: number): number {
  const ratio =
    Number.isFinite(devicePixelRatio) && devicePixelRatio > 0 ? devicePixelRatio : 1;
  return CROSSHAIR_BASE_RADIUS_PX * crosshairScale(progress) * ratio;
}

/** All animation samples for one frame. */
export function crosshairFrame(
  startedAt: number,
  now: number,
  durationMs = CROSSHAIR_MS,
): CrosshairFrame {
  const progress = crosshairProgress(startedAt, now, durationMs);
  return {
    progress,
    scale: crosshairScale(progress),
    alpha: crosshairAlpha(progress),
    visible: progress < 1,
  };
}

/**
 * Keeps a crosshair fully inside the canvas while leaving it as close to the pointer as possible.
 *
 * Near an edge the centre is nudged inward by at most {@link CROSSHAIR_EDGE_INSET_PX} and the radius is
 * capped by the room left on all four sides: the shape may shrink, but no arm or tick is ever clipped,
 * and even a pointer exactly on the border still produces a visible mark.
 */
export function clampCrosshairToCanvas(
  centre: Point,
  radius: number,
  width: number,
  height: number,
): CrosshairGeometry {
  const canvasWidth = Math.max(1, Number.isFinite(width) ? width : 1);
  const canvasHeight = Math.max(1, Number.isFinite(height) ? height : 1);
  const inset = Math.min(CROSSHAIR_EDGE_INSET_PX, canvasWidth / 2, canvasHeight / 2);
  const x = Math.min(Math.max(inset, centre.x), canvasWidth - inset);
  const y = Math.min(Math.max(inset, centre.y), canvasHeight - inset);
  const room = Math.max(0, Math.min(x, y, canvasWidth - x, canvasHeight - y));
  const requested = Number.isFinite(radius) ? Math.max(0, radius) : 0;
  return { x, y, radius: Math.min(requested, room) };
}
