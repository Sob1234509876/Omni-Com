/**
 * Follow-lock highlight math: the pulsing ground ring drawn under the camera-followed unit.
 *
 * The 跟随 button locks the camera to one unit (`lockedEntityId` in `main.ts`); the renderer draws this
 * ring so the operator can see *which* unit owns the lock even inside a crowded fight. The geometry
 * mirrors the gold selection ring (see `drawSelectionRing` in `fallback.ts`), but the mark is
 * achromatic white and breathes on a fixed period, so it can never be mistaken for a selection or an
 * ownership colour. The pure pulse rules live here so `scripts/check-render-math.ts` can exercise them
 * without a browser; the ellipse drawing itself is `drawFollowRing` in `fallback.ts`.
 */

/** One full breath of the follow ring, in milliseconds. */
export const FOLLOW_PULSE_MS = 1_200;

/**
 * The achromatic stroke of the follow ring: canvas chrome in the same white the personal markers use,
 * not an ownership colour and not the gold selection ring.
 */
export const FOLLOW_RING_COLOR = "rgba(255, 255, 255, 0.95)";

/** Radius multiplier at the resting end of a breath: the ring starts at the selection ring's size. */
export const FOLLOW_RING_MIN_SCALE = 1;

/** Radius multiplier at the widest point of a breath. */
export const FOLLOW_RING_MAX_SCALE = 1.18;

/** Stroke alpha at the resting end of a breath. */
export const FOLLOW_RING_MIN_ALPHA = 0.35;

/** Stroke alpha at the widest point of a breath; the ring brightens as it swells. */
export const FOLLOW_RING_MAX_ALPHA = 0.85;

/**
 * The phase of one breath in `[0, 1]`: 0 at the resting ring, 1 at the widest, back to 0.
 *
 * Half a cosine per direction means the ring swells and settles without a snap at the period wrap. A
 * non-finite clock or a non-positive period collapses to the resting phase 0, which still draws a
 * visible ring - the highlight must not disappear because of a bad number.
 */
export function followPulse(nowMs: number, periodMs: number = FOLLOW_PULSE_MS): number {
  if (!Number.isFinite(nowMs) || !Number.isFinite(periodMs) || periodMs <= 0) {
    return 0;
  }
  const cycle = (nowMs % periodMs) / periodMs;
  return 0.5 - 0.5 * Math.cos(cycle * Math.PI * 2);
}

/**
 * Clamps a pulse phase into `[0, 1]`, mapping every non-finite value to the resting phase.
 *
 * Both consumers below share this guard so a caller that hands in an arbitrary number cannot push the
 * ring's radius or alpha outside the documented ranges.
 */
function pulsePhase(pulse: number): number {
  return Number.isFinite(pulse) ? Math.min(Math.max(pulse, 0), 1) : 0;
}

/** Radius multiplier of the ground ring at a pulse phase, inside `[min, max]`. */
export function followRingScale(pulse: number): number {
  const phase = pulsePhase(pulse);
  return FOLLOW_RING_MIN_SCALE + (FOLLOW_RING_MAX_SCALE - FOLLOW_RING_MIN_SCALE) * phase;
}

/** Stroke alpha of the ground ring at a pulse phase, inside `[min, max]`. */
export function followRingAlpha(pulse: number): number {
  const phase = pulsePhase(pulse);
  return FOLLOW_RING_MIN_ALPHA + (FOLLOW_RING_MAX_ALPHA - FOLLOW_RING_MIN_ALPHA) * phase;
}
