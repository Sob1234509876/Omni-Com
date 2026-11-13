/**
 * The one-time bootstrap fetch for the server's static per-world data.
 *
 * Today the 500 ms `/world/snapshot` poll re-sends three pieces of data that never change while a
 * world is up: the top-level `content` catalogue, the `buildable` array and the `dimensions` roster.
 * The server now serves them once from `GET /world/bootstrap`
 *
 *     { "tilePixels": n, "contentVersion": v, "buildable": [...], "dimensions": [{"key": ..., "name": ...}],
 *       "content": {"dimensions": ..., "items": ..., "buildings": ..., "units": ..., "recipes": ...} }
 *
 * and drops them from the snapshot, so joining the world fetches this document once instead of every
 * poll. The page calls {@link fetchWorldBootstrap} at the same lifecycle moments where a world join
 * happens (a successful `LOGIN` answer, and the first server snapshot that takes over from the demo
 * world) and stores the parsed payload in the session-level holder
 * ({@link setWorldBootstrap} / {@link getWorldBootstrap}); the snapshot flow reads the holder via
 * {@link withBootstrapData} and tops up a snapshot that no longer carries the three fields.
 *
 * Degradation mirrors the old behaviour exactly: an unreachable server, an old server (404), a 503
 * `--world off` or malformed JSON all resolve to `null` — nothing is stored, and every snapshot keeps
 * parsing its own optional `content` / `buildable` / `dimensions` fields as before. The demo world
 * never touches this module. A failed fetch is retried at the next join event, never per poll.
 *
 * The document and every snapshot poll both carry a `contentVersion` (bumped when a dimension is
 * registered mid-run): {@link bootstrapVersionHandshake} compares the recorded version against each
 * snapshot's and decides whether the document must be refetched once, so the page keeps its static
 * data current without polling this route.
 *
 * Everything here except {@link fetchWorldBootstrap} is pure and DOM-free, so
 * `scripts/check-render-math.ts` can exercise the parse and the merge directly.
 */

import { parseContentCatalogue, type ContentCatalogue } from "./content.ts";
import type { DimensionView, ServerWorldView } from "./render/view.ts";

/** The static-data document the `GET /world/bootstrap` endpoint serves for a live world. */
export interface WorldBootstrap {
  /** Tile size in pixels the server draws at, when the document carries one. */
  readonly tilePixels?: number;
  /**
   * Version of the static per-world payload, bumped by exactly 1 each time a dimension is registered
   * on the running world. Every snapshot poll repeats it so the client can detect the bump; missing
   * or malformed on either side means the handshake does nothing.
   */
  readonly contentVersion?: number;
  /** Content paths of every directly-buildable building, in the server's declared order. */
  readonly buildable?: readonly string[];
  /** Every dimension of the world, key first. */
  readonly dimensions?: readonly DimensionView[];
  /** The raw content catalogue, parsed defensively into the same store the snapshot field used. */
  readonly content?: ContentCatalogue;
}

/** The HTTP path of the one-time bootstrap endpoint (same origin as `/world/snapshot`). */
export const WORLD_BOOTSTRAP_PATH = "/world/bootstrap";

// ---------------------------------------------------------------------------------------------
// Session-level holder
// ---------------------------------------------------------------------------------------------

/** The bootstrap payload of the world the page last joined; `undefined` before the first success. */
let sessionBootstrap: WorldBootstrap | undefined;

/** The stored bootstrap payload, or `undefined` when no join has fetched one yet. */
export function getWorldBootstrap(): WorldBootstrap | undefined {
  return sessionBootstrap;
}

/** Stores the bootstrap payload of a successful join; a later join simply replaces it. */
export function setWorldBootstrap(data: WorldBootstrap): void {
  sessionBootstrap = data;
}

/** Forgets the stored payload. Unused by the page (a stale catalogue is harmless) but kept for tests. */
export function clearWorldBootstrap(): void {
  sessionBootstrap = undefined;
}

// ---------------------------------------------------------------------------------------------
// Parsing
// ---------------------------------------------------------------------------------------------

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Reads the `buildable` array: one trimmed, whitespace-free content path per entry, order kept. */
function parseBuildable(value: unknown): readonly string[] | undefined {
  if (!Array.isArray(value)) {
    return undefined;
  }
  const paths: string[] = [];
  for (const rawPath of value) {
    if (typeof rawPath !== "string") {
      continue;
    }
    const path = rawPath.trim();
    if (path === "" || /\s/u.test(path)) {
      continue;
    }
    paths.push(path);
  }
  return paths.length === 0 ? undefined : paths;
}

/** Reads the `dimensions` roster in its wire shape: `{"key": ..., "name": ...}` rows. */
function parseBootstrapDimensions(value: unknown): readonly DimensionView[] | undefined {
  if (!Array.isArray(value)) {
    return undefined;
  }
  const roster: DimensionView[] = [];
  for (const rawEntry of value) {
    if (!isRecord(rawEntry)) {
      continue;
    }
    const key = rawEntry["key"];
    const name = rawEntry["name"];
    if (typeof key === "string" && key.trim() !== "" && typeof name === "string" && name.trim() !== "") {
      roster.push({ key: key.trim(), name: name.trim() });
    }
  }
  return roster.length === 0 ? undefined : roster;
}

/**
 * Parses the bootstrap document defensively, dropping anything unreadable rather than rejecting the
 * whole document.
 *
 * `undefined` for anything that is not a readable bootstrap: a non-object, an array, or an object
 * with no readable section at all. Sections that are present but empty simply contribute nothing, so
 * a partial document still tops up the fields it does carry.
 */
export function parseWorldBootstrap(payload: unknown): WorldBootstrap | undefined {
  if (!isRecord(payload)) {
    return undefined;
  }
  const rawTilePixels = payload["tilePixels"];
  const tilePixels =
    typeof rawTilePixels === "number" && Number.isFinite(rawTilePixels) && rawTilePixels > 0
      ? rawTilePixels
      : undefined;
  // The handshake version: readable only as a non-negative integer, tolerated as absent otherwise.
  const rawContentVersion = payload["contentVersion"];
  const contentVersion =
    typeof rawContentVersion === "number" &&
    Number.isSafeInteger(rawContentVersion) &&
    rawContentVersion >= 0
      ? rawContentVersion
      : undefined;
  const buildable = parseBuildable(payload["buildable"]);
  const dimensions = parseBootstrapDimensions(payload["dimensions"]);
  const content = parseContentCatalogue(payload["content"]);
  if (
    tilePixels === undefined &&
    contentVersion === undefined &&
    buildable === undefined &&
    dimensions === undefined &&
    content === undefined
  ) {
    return undefined;
  }
  return {
    ...(tilePixels === undefined ? {} : { tilePixels }),
    ...(contentVersion === undefined ? {} : { contentVersion }),
    ...(buildable === undefined ? {} : { buildable }),
    ...(dimensions === undefined ? {} : { dimensions }),
    ...(content === undefined ? {} : { content }),
  };
}

// ---------------------------------------------------------------------------------------------
// Merge into the snapshot flow
// ---------------------------------------------------------------------------------------------

/**
 * Tops up one received snapshot from the session bootstrap holder.
 *
 * A snapshot field that is present always wins: an old server that still ships `content` /
 * `buildable` / `dimensions` in every poll keeps the exact behaviour it always had. A snapshot
 * without a field (the new server, once it stops carrying them) reads the bootstrap payload instead,
 * so the BUILD rail, the dimension switcher, the i18n server namespace and the deficit math all see
 * the same data through the same `WorldView` fields as before. `snapshot` is returned unchanged when
 * nothing needs topping up (no stored bootstrap, or every field already present).
 */
export function withBootstrapData(
  snapshot: ServerWorldView,
  bootstrap: WorldBootstrap | undefined,
): ServerWorldView {
  if (
    bootstrap === undefined ||
    (snapshot.buildable !== undefined &&
      snapshot.dimensions !== undefined &&
      snapshot.content !== undefined)
  ) {
    return snapshot;
  }
  return {
    ...snapshot,
    ...(snapshot.buildable === undefined && bootstrap.buildable !== undefined
      ? { buildable: bootstrap.buildable }
      : {}),
    ...(snapshot.dimensions === undefined && bootstrap.dimensions !== undefined
      ? { dimensions: bootstrap.dimensions }
      : {}),
    ...(snapshot.content === undefined && bootstrap.content !== undefined
      ? { content: bootstrap.content }
      : {}),
  };
}

// ---------------------------------------------------------------------------------------------
// Version handshake
// ---------------------------------------------------------------------------------------------

/** What the documented version handshake does for one received snapshot poll. */
export type BootstrapHandshakeAction =
  /** Both versions equal, or at least one unreadable: keep everything as is. */
  | "none"
  /** Recorded and snapshot versions differ and this mismatch has not been handled yet: refetch once. */
  | "refetch"
  /**
   * The mismatch survives a refetch that already ran for this snapshot version: the refetched
   * document is authoritative, so no further request is spent (no loop).
   */
  | "settle";

/**
 * The one decision of the documented version handshake, pure so the offline check can exercise it.
 *
 * The client records the `contentVersion` it fetched the bootstrap document with; every snapshot
 * poll repeats the field. A readable snapshot version that differs from the recorded one means a
 * dimension was registered mid-run, so the bootstrap document is refetched once and everything
 * derived from it (dimension switcher, BUILD rail, i18n server namespace) is rebuilt from the new
 * bytes. Anything unreadable degrades to `"none"`: an old server never sends the field on either
 * side, and a stored document without it has nothing to compare against — the current behaviour.
 *
 * `refetchedFor` is the snapshot version a refetch attempt already covered (or `undefined`); it
 * turns a surviving mismatch into `"settle"` instead of another `"refetch"`, which is what keeps the
 * handshake from looping against a buggy or restarting server.
 */
export function bootstrapVersionHandshake(
  bootstrap: WorldBootstrap | undefined,
  snapshotVersion: number | undefined,
  refetchedFor: number | undefined,
): BootstrapHandshakeAction {
  if (bootstrap === undefined || snapshotVersion === undefined || bootstrap.contentVersion === undefined) {
    return "none";
  }
  if (bootstrap.contentVersion === snapshotVersion) {
    return "none";
  }
  return refetchedFor === snapshotVersion ? "settle" : "refetch";
}

// ---------------------------------------------------------------------------------------------
// Fetch
// ---------------------------------------------------------------------------------------------

/** Builds the `/world/bootstrap` URL for a base URL; an empty base means the same origin. */
export function bootstrapUrl(baseUrl = ""): string {
  const prefix = baseUrl.trim().replace(/\/+$/u, "");
  return prefix === "" ? WORLD_BOOTSTRAP_PATH : `${prefix}${WORLD_BOOTSTRAP_PATH}`;
}

/**
 * Fetches `GET /world/bootstrap` once per join and parses it.
 *
 * The same failure tolerance as {@link ../src/render/view.ts!fetchWorldSnapshot | the snapshot fetch}:
 * offline, a 404 from a server that predates the endpoint, a 503 `--world off`, malformed JSON or an
 * unreadable document all resolve to `null` rather than rejecting, so the caller's join flow never
 * sees an unhandled rejection. A success stores nothing here — the caller owns the holder — but the
 * parse result is already in the {@link WorldBootstrap} shape the holder takes.
 */
export async function fetchWorldBootstrap(baseUrl = ""): Promise<WorldBootstrap | null> {
  try {
    const response = await fetch(bootstrapUrl(baseUrl), {
      headers: { Accept: "application/json" },
      cache: "no-store",
    });
    if (!response.ok) {
      return null;
    }
    const payload: unknown = await response.json();
    return parseWorldBootstrap(payload) ?? null;
  } catch {
    return null;
  }
}
