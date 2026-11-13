/**
 * Server-pushed `NOTIFY` frames over the command WebSocket.
 *
 * The server can now push unsolicited text frames on the same socket the command answers use:
 *
 *     NOTIFY {"type":"contentVersion","value":2}
 *
 * A push is a wake-up hint only. The snapshot `contentVersion` handshake
 * ({@link ./bootstrap.ts!bootstrapVersionHandshake}) stays the correctness fallback; the push merely
 * lets the page react to a bootstrap change immediately instead of waiting up to one 500 ms poll.
 *
 * Everything here is pure and DOM-free so `scripts/check-render-math.ts` can exercise the routing
 * split directly: the socket's receive path calls {@link routeFrame} BEFORE it touches the in-order
 * pending-answer queue, so a push can never consume (and desync) a pending command's answer slot.
 */

/** The exact prefix a server-pushed notification frame starts with, including the separator space. */
export const NOTIFY_PREFIX = "NOTIFY ";

/** One parsed notification: a `type` tag plus the whole payload object it rode in on. */
export interface NotifyMessage {
  readonly type: string;
  readonly payload: Record<string, unknown>;
}

/** What {@link routeFrame} decides for one received WS text frame. */
export type FrameRoute =
  /** Not a notification: stays a command answer for the in-order queue. */
  | { readonly kind: "answer" }
  /** A readable notification of some `type`; the caller dispatches on the registry. */
  | { readonly kind: "notify"; readonly message: NotifyMessage }
  /**
   * A `NOTIFY `-prefixed frame that cannot be parsed: logged once and dropped by the caller,
   * never thrown, and never allowed near the answer queue.
   */
  | { readonly kind: "drop" };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/**
 * The one routing decision of the receive path: divert notification frames from the answer queue.
 *
 * The split is by prefix, decided before any pending answer is shifted. Only a frame starting with
 * the exact `NOTIFY ` literal is diverted; every other line - including a lowercase or
 * space-less `NOTIFY...` variant, which the server never sends - is an ordinary answer. A
 * diverted frame that cannot be parsed degrades to `"drop"` (the caller logs once and drops it),
 * so a malformed push can never desync the in-order answer matching.
 */
export function routeFrame(line: string): FrameRoute {
  if (typeof line !== "string" || !line.startsWith(NOTIFY_PREFIX)) {
    return { kind: "answer" };
  }
  const message = parseNotify(line);
  return message === undefined ? { kind: "drop" } : { kind: "notify", message };
}

/**
 * Parses one `NOTIFY ` frame defensively: JSON object with a non-empty string `type`.
 *
 * Returns `undefined` for every unreadable shape - missing prefix, broken JSON, a non-object
 * payload, a missing/empty/non-string `type` - instead of throwing. The rest of the payload is
 * kept verbatim for the per-type extractors below.
 */
export function parseNotify(line: string): NotifyMessage | undefined {
  if (typeof line !== "string" || !line.startsWith(NOTIFY_PREFIX)) {
    return undefined;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(line.slice(NOTIFY_PREFIX.length));
  } catch {
    return undefined;
  }
  if (!isRecord(parsed)) {
    return undefined;
  }
  const type = parsed["type"];
  if (typeof type !== "string" || type.trim() === "") {
    return undefined;
  }
  return { type, payload: parsed };
}

/**
 * The registry of known notification types, extensible by adding one entry per new `type`.
 *
 * An unknown `type` (not a registry key) is ignored by the dispatcher: a newer server may push
 * notifications this client does not know, and ignoring them keeps the page and the answer queue
 * exactly where they were. The current registry carries only `contentVersion`, whose handler reads
 * the pushed version via {@link parseContentVersionNotify}.
 */
export const KNOWN_NOTIFY_TYPES: ReadonlySet<string> = new Set<string>(["contentVersion"]);

/** True when `type` is in the registry, i.e. the dispatcher has a handler for it. */
export function isKnownNotifyType(type: string): boolean {
  return KNOWN_NOTIFY_TYPES.has(type);
}

/**
 * Reads the pushed `contentVersion` value: a non-negative safe integer, or `undefined`.
 *
 * The same defensiveness as the bootstrap document's field ({@link ./bootstrap.ts!parseWorldBootstrap}),
 * so a pushed `value` that is missing, a string, fractional, negative or non-finite is ignored and
 * the snapshot handshake stays the correctness fallback. A `type` other than `contentVersion`
 * yields `undefined` too, so the extractor alone gates the refetch path.
 */
export function parseContentVersionNotify(message: NotifyMessage): number | undefined {
  if (message.type !== "contentVersion") {
    return undefined;
  }
  const value = message.payload["value"];
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0
    ? value
    : undefined;
}
