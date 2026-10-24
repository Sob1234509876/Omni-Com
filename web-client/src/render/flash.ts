/**
 * The brief selection flash: pure timing and colour rules for the renderer.
 *
 * Whenever a *player action* changes the selection, every entity it newly selected gets one short
 * achromatic halo. The page owns the map (entity id -> start time) and the draw call; this module only
 * answers "how bright is it now" and "which entries have finished", so the same rules can be checked
 * offline without a DOM.
 *
 * Programmatic selection changes - pruning dead ids, swapping the world when a new snapshot window
 * arrives, restoring a selection after a reconnect - never call into this module, which is why they
 * stay silent.
 */

/**
 * How long one selection flash lasts.
 *
 * A quarter of a second is long enough to be felt as feedback (about 15 frames at 60 Hz) yet short
 * enough that a player clicking through a group never sees a trail of old halos blur the new selection.
 */
export const FLASH_MS = 250;

/**
 * The halo colour.
 *
 * The flash is canvas content like the ownership rings, but it must not add a hue: the surrounding
 * chrome is greyscale, so a pure white halo reads as brightness only and never competes with the gold
 * selection ring or the ownership colours.
 */
export const FLASH_COLOR = "#ffffff";

/**
 * Starts (or restarts) the flash for the ids a player action has just newly selected.
 *
 * The caller passes only the ids that were not selected before the action: an id that was already
 * selected keeps its existing start time, so a rapid re-click does not strobe the whole group. Passing
 * an id that was deselected and is selected again inside the same flash window restarts its timer,
 * because that entity really was newly selected by this action. Entries are never removed here;
 * {@link pruneFlashes} is the only cleanup.
 */
export function startFlash(
  map: Map<number, number>,
  ids: Iterable<number>,
  now: number,
): void {
  for (const id of ids) {
    map.set(id, now);
  }
}

/**
 * How bright one flash is at `now`, from 1 at its start to 0 at and after `durationMs`.
 *
 * The fade is linear, so half the duration is exactly half intensity. A negative elapsed time (a clock
 * that appears to have gone backwards) clamps to 1 instead of overshooting, and an entry past its
 * duration clamps to 0. A non-positive or non-finite duration has no visible phase and returns 0.
 */
export function flashIntensity(startedAt: number, now: number, durationMs: number): number {
  if (!Number.isFinite(durationMs) || durationMs <= 0) {
    return 0;
  }
  const elapsed = now - startedAt;
  if (!Number.isFinite(elapsed)) {
    return 0;
  }
  if (elapsed <= 0) {
    return 1;
  }
  if (elapsed >= durationMs) {
    return 0;
  }
  return 1 - elapsed / durationMs;
}

/**
 * Drops every flash that has finished by `now`, keeping active and future-dated entries.
 *
 * Called once per animation frame; the map therefore holds at most one short-lived entry per entity
 * the player selected recently and cannot grow with the length of a session.
 */
export function pruneFlashes(map: Map<number, number>, now: number): void {
  for (const [id, startedAt] of map) {
    if (!Number.isFinite(startedAt) || now - startedAt >= FLASH_MS) {
      map.delete(id);
    }
  }
}
