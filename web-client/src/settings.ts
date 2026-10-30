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

/** Every category enabled; the factory default. */
export const ALL_MESSAGES: MessageFilters = {
  command: true,
  ping: true,
  build: true,
  production: true,
  combat: true,
  event: true,
  error: true,
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
    // Quiet keeps errors and the derived world events, and drops routine exchange/action chatter.
    filters: { ...ALL_MESSAGES, command: false, ping: false, build: false, production: false, combat: false, event: true },
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
  if (value.startsWith("遭到攻击") || value.includes("受到攻击") || value.includes("被袭")) {
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

/** The server address/port pair; credentials are deliberately never persisted. */
export interface ConnectionSettings {
  readonly host: string;
  readonly port: string;
}

/** Everything under one storage key. */
export interface Settings {
  readonly filters: MessageFilters;
  readonly showPaths: boolean;
  readonly viewHidden: boolean;
  readonly connection: ConnectionSettings;
}

/** A fresh copy of the factory defaults; callers may keep and mutate their copy. */
export function defaultSettings(): Settings {
  return {
    filters: { ...ALL_MESSAGES },
    showPaths: true,
    viewHidden: false,
    connection: { host: "", port: "" },
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
  if (filters === null || connection === null) {
    return fallback;
  }
  if (showPaths !== undefined && typeof showPaths !== "boolean") {
    return fallback;
  }
  if (viewHidden !== undefined && typeof viewHidden !== "boolean") {
    return fallback;
  }
  return {
    filters,
    showPaths: showPaths ?? fallback.showPaths,
    viewHidden: viewHidden ?? fallback.viewHidden,
    connection,
  };
}

/** Serialises one settings object with an explicit version; field order is stable for the check. */
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
    connection: {
      host: settings.connection.host,
      port: settings.connection.port,
    },
  });
}
