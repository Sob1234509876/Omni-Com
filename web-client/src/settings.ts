/**
 * Persistent client settings and the pure message-filter rules.
 *
 * The page has three groups of settings (连接 / 消息筛选 / 界面), all stored under one versioned
 * `localStorage` key. This module owns the shape, the defaults, the presets and the serialisation; the DOM
 * half lives in `main.ts`, so `scripts/check-render-math.ts` can exercise every rule without a browser.
 *
 * The filter is consulted **when a toast is pushed** (see `toastview.ts`), never in the log: the log
 * overlay is the complete history and must stay complete regardless of the filter.
 *
 * Error toasts are their own category. The presets always leave 错误 on, because the owner asked that no
 * preset can silently hide an error; only a deliberate uncheck of the 错误 checkbox does that.
 *
 * The connection group also carries the remembered login (`accountName` / `accountPassword`). The owner
 * explicitly asked to be logged back in after every reload, so the password is stored as plain text in
 * `localStorage` — the documented trade-off of that request. `main.ts` prefills the fields and sends one
 * automatic `LOGIN` per connection; a 设置 reset clears both fields again.
 */

/** One `localStorage` key for the whole settings object; never a key per field. */
export const SETTINGS_STORAGE_KEY = "omni-com.web-client.settings";

/** Bumped when the stored shape changes incompatibly; any other version falls back to the defaults. */
export const SETTINGS_VERSION = 1;

/** The toast categories the filter checkboxes expose. */
export type MessageCategory =
  | "command"
  | "ping"
  | "build"
  | "production"
  | "combat"
  | "event"
  | "error";

/** Every category, in the same order the settings panel lists them. */
export const MESSAGE_CATEGORIES: readonly MessageCategory[] = [
  "command",
  "ping",
  "build",
  "production",
  "combat",
  "event",
  "error",
] as const;

/** The Chinese labels the settings panel and the documentation use. */
export const MESSAGE_CATEGORY_LABELS: Readonly<Record<MessageCategory, string>> = {
  command: "命令与回答",
  ping: "PING",
  build: "建造",
  production: "生产",
  combat: "攻击/被袭",
  event: "事件",
  error: "错误",
};

/** Whether each category may produce a toast. */
export type MessageFilters = Readonly<Record<MessageCategory, boolean>>;

/** Every category enabled. */
export const ALL_MESSAGES: MessageFilters = {
  command: true,
  ping: true,
  build: true,
  production: true,
  combat: true,
  event: true,
  error: true,
};

/**
 * The quiet filter table: errors and the derived world events stay on, routine exchange and action
 * chatter stays off. This is the factory default (the toast stack should not narrate every command to
 * a player who has not asked for it) and the `安静` preset's table; 错误 is always on, whatever the
 * preset or stored value was.
 */
export const QUIET_MESSAGES: MessageFilters = {
  ...ALL_MESSAGES,
  command: false,
  ping: false,
  build: false,
  production: false,
  combat: false,
  event: true,
};

/** The three named filter presets. */
export type FilterPreset = "errors" | "quiet" | "all";

/** Preset metadata for the settings panel; the filters are returned as copies. */
export const FILTER_PRESETS: readonly {
  readonly id: FilterPreset;
  readonly label: string;
  readonly filters: MessageFilters;
}[] = [
  {
    id: "errors",
    label: "只显示错误",
    filters: { ...ALL_MESSAGES, command: false, ping: false, build: false, production: false, combat: false, event: false },
  },
  {
    id: "quiet",
    label: "安静",
    // The 安静 preset and the factory default share one table; see `QUIET_MESSAGES`.
    filters: QUIET_MESSAGES,
  },
  { id: "all", label: "全部", filters: ALL_MESSAGES },
] as const;

/** A copy of one preset's filters; 错误 is always on, whatever the stored/preset value was. */
export function presetFilters(preset: FilterPreset): MessageFilters {
  const found = FILTER_PRESETS.find((entry) => entry.id === preset);
  const source = found?.filters ?? ALL_MESSAGES;
  return { ...source, error: true };
}

/** Whether one category is currently allowed to produce a toast. */
export function isToastAllowed(filters: MessageFilters, category: MessageCategory): boolean {
  return filters[category] === true;
}

/** Runtime guard for a category read from a `data-` attribute or from JSON. */
export function isMessageCategory(value: string): value is MessageCategory {
  return (MESSAGE_CATEGORIES as readonly string[]).includes(value);
}

/** The toast category of a command line, decided by its first token(s). */
export function classifyCommand(command: string | null): MessageCategory {
  if (command === null) {
    return "event";
  }
  const tokens = command.trim().split(/\s+/u);
  const verb = tokens[0]?.toUpperCase() ?? "";
  switch (verb) {
    case "PING":
      return "ping";
    case "BUILD":
    case "UPGRADE":
    case "UPGRADEUNIT":
      return "build";
    case "TRAIN":
      return "production";
    case "ORDER": {
      const action = tokens[1]?.toUpperCase() ?? "";
      return action === "ATTACK" || action === "ATTACKMOVE" ? "combat" : "command";
    }
    default:
      return "command";
  }
}

/**
 * The category of a toast that carries no command line (a notice, an unsolicited server line or a
 * snapshot-diff event). Errors are recognised first, then the two derived event families.
 */
export function classifyNotice(text: string): MessageCategory {
  const value = text.trim();
  if (isErrorLine(value)) {
    return "error";
  }
  if (
    value.startsWith("遭到攻击") ||
    value.includes("受到攻击") ||
    value.includes("被袭") ||
    // The en wording of the client-derived event (the zh prefixes above stay for server text).
    value.startsWith("Under attack")
  ) {
    return "combat";
  }
  return "event";
}

/** Whether a server answer is the protocol's error line. */
function isErrorLine(answer: string): boolean {
  return answer === "ERR" || answer.startsWith("ERR ");
}

/**
 * The category of one toast. An error answer or an error tone always wins, so an `ERR` from a `BUILD`
 * exchange is filtered as 错误 rather than 建造. A command line that is a joined batch keeps its first
 * verb (the batch is homogeneous by construction).
 */
export function classifyToast(
  command: string | null,
  answer: string,
  tone: "info" | "error" = "info",
): MessageCategory {
  if (tone === "error" || isErrorLine(answer)) {
    return "error";
  }
  if (command === null || command.trim() === "") {
    return classifyNotice(answer);
  }
  return classifyCommand(command);
}

/**
 * The server address/port pair.
 *
 * Only the address lives here; the account name and password are separate top-level fields on
 * {@link Settings} (the owner explicitly asked to remember the login, so they are persisted too).
 */
export interface ConnectionSettings {
  readonly host: string;
  readonly port: string;
}

/**
 * Default radius, in tiles, of the Q9 inventory tag around the pointer.
 *
 * The owner asked for "about 10 tiles" and for the radius to live in the settings page. A selected unit
 * is always tagged whatever its distance; the radius only decides which *unselected* units show theirs.
 */
export const DEFAULT_INVENTORY_RADIUS_TILES = 10;

/** Smallest accepted radius: zero would make the pointer rule meaningless, so one tile is the floor. */
export const MIN_INVENTORY_RADIUS_TILES = 1;

/** Largest accepted radius; a wider value would tag most of a loaded window. */
export const MAX_INVENTORY_RADIUS_TILES = 32;

/** Clamps a radius to {@link MIN_INVENTORY_RADIUS_TILES}..{@link MAX_INVENTORY_RADIUS_TILES}. */
export function clampInventoryRadius(value: number): number {
  if (!Number.isFinite(value)) {
    return DEFAULT_INVENTORY_RADIUS_TILES;
  }
  return Math.min(MAX_INVENTORY_RADIUS_TILES, Math.max(MIN_INVENTORY_RADIUS_TILES, Math.round(value)));
}

/**
 * The UI language setting: a concrete language or `auto`, which resolves from `navigator.language`
 * (exact `zh-CN` match first, then the base `zh` language, else English — see `resolveLanguage` in
 * `src/i18n.ts`). English is the default everywhere: a fresh profile with no stored value resolves
 * through `auto`, and the zh-CN dictionary keeps the page's original Chinese wording.
 */
export type LanguageSetting = "auto" | "en" | "zh-CN";

/** Every value the settings panel's language selector offers, in display order. */
export const LANGUAGE_SETTINGS: readonly LanguageSetting[] = ["auto", "en", "zh-CN"] as const;

/** Whether a raw value is a language setting the panel and the parser both accept. */
export function isLanguageSetting(value: string): value is LanguageSetting {
  return (LANGUAGE_SETTINGS as readonly string[]).includes(value);
}

/** Everything under one storage key. */
export interface Settings {
  readonly filters: MessageFilters;
  readonly showPaths: boolean;
  readonly viewHidden: boolean;
  readonly connection: ConnectionSettings;
  /** Radius in tiles of the Q9 inventory tag around the pointer; see {@link clampInventoryRadius}. */
  readonly inventoryRadiusTiles: number;
  /**
   * UI language: `auto` resolves from the browser, otherwise a concrete language. Optional on the
   * wire; a stored object without it parses as the `auto` default, so an existing profile keeps its
   * behaviour (which, for a Chinese browser, was and stays Chinese).
   */
  readonly language: LanguageSetting;
  /**
   * Remembered login account name; empty when the player did not save one.
   *
   * Kept so the page can prefill the 登录 fields and, once the handshake completes, log in by itself.
   * Optional on the wire: a stored object without it parses as the empty default.
   */
  readonly accountName: string;
  /**
   * Remembered login password for {@link accountName}; empty when the player did not save one.
   *
   * **The password is stored as plain text in `localStorage` by explicit user request** (the owner
   * asked to stay logged in across page reloads and accepted the plain-text trade-off). The field is
   * optional on the wire and parses as the empty default when missing.
   */
  readonly accountPassword: string;
}

/** A fresh copy of the factory defaults; callers may keep and mutate their copy. */
export function defaultSettings(): Settings {
  return {
    // The quiet table, not "all": the toast stack starts quiet (errors and world events only) and a
    // player who wants the narration opts in. A stored settings object is never rewritten — this
    // default only applies to a fresh browser profile, and `readFilters` keeps whatever an existing
    // user persisted, filling missing categories from `ALL_MESSAGES` as before.
    filters: { ...QUIET_MESSAGES },
    showPaths: true,
    viewHidden: false,
    connection: { host: "", port: "" },
    inventoryRadiusTiles: DEFAULT_INVENTORY_RADIUS_TILES,
    language: "auto",
    accountName: "",
    accountPassword: "",
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Reads a filter table, filling missing categories from `ALL_MESSAGES`; `null` means corrupt. */
function readFilters(value: unknown): MessageFilters | null {
  if (!isRecord(value)) {
    return null;
  }
  const filters: Record<MessageCategory, boolean> = { ...ALL_MESSAGES };
  for (const category of MESSAGE_CATEGORIES) {
    const entry = value[category];
    if (entry === undefined) {
      continue;
    }
    if (typeof entry !== "boolean") {
      return null;
    }
    filters[category] = entry;
  }
  return filters;
}

/** Reads the connection pair; a missing pair is allowed and becomes the empty address. */
function readConnection(value: unknown): ConnectionSettings | null {
  if (value === undefined) {
    return { host: "", port: "" };
  }
  if (!isRecord(value)) {
    return null;
  }
  const host = value["host"];
  const port = value["port"];
  if (host !== undefined && typeof host !== "string") {
    return null;
  }
  if (port !== undefined && typeof port !== "string") {
    return null;
  }
  return { host: host ?? "", port: port ?? "" };
}

/**
 * Parses one stored value.
 *
 * A missing, empty, non-JSON, non-object, wrong-version or wrongly-typed value answers the defaults.
 * That is the documented corruption rule: there is no partial "best effort" restore that could leave the
 * page in a half-configured state. A same-version object missing an optional field still fills that field
 * from the defaults, so a field added in a later compatible release does not wipe the others.
 */
export function parseSettings(raw: string | null | undefined): Settings {
  const fallback = defaultSettings();
  if (raw === null || raw === undefined || raw.trim() === "") {
    return fallback;
  }
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    return fallback;
  }
  if (!isRecord(value) || value["version"] !== SETTINGS_VERSION) {
    return fallback;
  }
  const filters = readFilters(value["filters"]);
  const connection = readConnection(value["connection"]);
  const showPaths = value["showPaths"];
  const viewHidden = value["viewHidden"];
  const inventoryRadiusTiles = value["inventoryRadiusTiles"];
  // The language follows the optional-string rule too: missing means `auto`, a non-string or an
  // unknown language value means the whole stored object is corrupt (the same all-or-nothing rule
  // every other field follows).
  const language = value["language"];
  // The remembered account fields follow the same optional-string rule as `host` / `port`: missing
  // means the empty default, a non-string value means the whole stored object is corrupt.
  const accountName = value["accountName"];
  const accountPassword = value["accountPassword"];
  if (filters === null || connection === null) {
    return fallback;
  }
  if (showPaths !== undefined && typeof showPaths !== "boolean") {
    return fallback;
  }
  if (viewHidden !== undefined && typeof viewHidden !== "boolean") {
    return fallback;
  }
  if (inventoryRadiusTiles !== undefined && typeof inventoryRadiusTiles !== "number") {
    return fallback;
  }
  if (language !== undefined && (typeof language !== "string" || !isLanguageSetting(language))) {
    return fallback;
  }
  if (accountName !== undefined && typeof accountName !== "string") {
    return fallback;
  }
  if (accountPassword !== undefined && typeof accountPassword !== "string") {
    return fallback;
  }
  return {
    filters,
    showPaths: showPaths ?? fallback.showPaths,
    viewHidden: viewHidden ?? fallback.viewHidden,
    connection,
    inventoryRadiusTiles: inventoryRadiusTiles === undefined
      ? fallback.inventoryRadiusTiles
      : clampInventoryRadius(inventoryRadiusTiles),
    language: language ?? fallback.language,
    accountName: accountName ?? "",
    accountPassword: accountPassword ?? "",
  };
}

/**
 * Serialises one settings object with an explicit version; field order is stable for the check.
 *
 * The account fields are written as plain text (see {@link Settings.accountPassword}); a player who
 * never saved an account serialises two empty strings, which is byte-compatible with the shape the
 * page wrote before the fields existed apart from the two extra keys.
 */
export function serializeSettings(settings: Settings): string {
  return JSON.stringify({
    version: SETTINGS_VERSION,
    filters: {
      command: settings.filters.command,
      ping: settings.filters.ping,
      build: settings.filters.build,
      production: settings.filters.production,
      combat: settings.filters.combat,
      event: settings.filters.event,
      error: settings.filters.error,
    },
    showPaths: settings.showPaths,
    viewHidden: settings.viewHidden,
    inventoryRadiusTiles: settings.inventoryRadiusTiles,
    language: settings.language,
    connection: {
      host: settings.connection.host,
      port: settings.connection.port,
    },
    accountName: settings.accountName,
    accountPassword: settings.accountPassword,
  });
}
