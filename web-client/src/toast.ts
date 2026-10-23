/**
 * Pure rules for the protocol toast console.
 *
 * The permanent log pane is gone: every command/answer exchange becomes one toast in the bottom-right
 * corner, and the full history stays available from the "日志" overlay. This module holds only the
 * decisions the DOM view needs, so `scripts/check-render-math.ts` can exercise them without a browser:
 * how long a toast lives, how an error is recognised, how a long exchange is trimmed and how many
 * toasts may be visible at once.
 *
 * The page chrome is greyscale: an error toast differs from a normal one by brightness and weight, not
 * by hue. That styling lives in `index.html`; here an error is only a longer-lived tone.
 */

/** Lifetime of a normal exchange toast, in milliseconds. */
export const TOAST_LIFETIME_MS = 4_000;

/** Lifetime of an error toast (`ERR ...` or a client-side refusal), in milliseconds. */
export const TOAST_ERROR_LIFETIME_MS = 8_000;

/** At most this many toasts are visible; the oldest one drops off when a newer one arrives. */
export const TOAST_VISIBLE_CAP = 5;

/** Longest command or answer line kept inside a toast; the rest is ellipsized. */
export const TOAST_LINE_MAX_CHARS = 140;

/** A toast is either an ordinary exchange or an error that lives twice as long. */
export type ToastTone = "info" | "error";

/** One toast: the command line and its answer, both already display-formatted. */
export interface ToastEntry {
  /** Monotonic id local to the toast console; used as the DOM key and the timer key. */
  readonly id: number;
  readonly tone: ToastTone;
  /** The sent command, or `null` for a notice that carries no command (an unsolicited server line). */
  readonly command: string | null;
  readonly answer: string;
}

/** Whether a server answer is the protocol's error line. */
export function isErrAnswer(answer: string): boolean {
  return answer === "ERR" || answer.startsWith("ERR ");
}

/**
 * The tone of one exchange.
 *
 * A server `ERR ...` is an error. A client-side refusal (nothing was sent because the UI could not
 * build the command) passes `clientRefusal` and is an error too. Every other answer, including an
 * unexpected but non-error line, keeps the normal tone.
 */
export function toastTone(answer: string, clientRefusal = false): ToastTone {
  return clientRefusal || isErrAnswer(answer) ? "error" : "info";
}

/** The auto-dismiss delay for a tone: about four seconds normally, about eight for an error. */
export function toastLifetimeMs(tone: ToastTone): number {
  return tone === "error" ? TOAST_ERROR_LIFETIME_MS : TOAST_LIFETIME_MS;
}

/**
 * Collapses every run of whitespace (including newlines from a multi-line engine-info report) to one
 * space and truncates the result with an ellipsis, so one exchange always fits the toast's two lines.
 */
export function trimToastLine(text: string, maxChars = TOAST_LINE_MAX_CHARS): string {
  const collapsed = text.replace(/\s+/gu, " ").trim();
  const limit = Number.isFinite(maxChars) ? Math.max(2, Math.floor(maxChars)) : TOAST_LINE_MAX_CHARS;
  if (collapsed.length <= limit) {
    return collapsed;
  }
  return `${collapsed.slice(0, limit - 1)}…`;
}

/**
 * Keeps the newest `cap` entries of an oldest-first list.
 *
 * The toast stack stores entries oldest first and renders newest at the top; capping the list is what
 * makes the sixth-newest toast disappear instead of growing the page forever.
 */
export function capToasts<T>(entries: readonly T[], cap = TOAST_VISIBLE_CAP): T[] {
  const limit = Number.isInteger(cap) && cap > 0 ? cap : TOAST_VISIBLE_CAP;
  if (entries.length <= limit) {
    return [...entries];
  }
  return entries.slice(entries.length - limit);
}
