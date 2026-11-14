/**
 * The client-side store for the server's `content` catalogue (playtester item 4 / i18n item 22).
 *
 * The snapshot gains an optional top-level `content` object: the raw content data (dimension /
 * unit / building names, costs and sizes, recipes with their inputs). The client parses it
 * **defensively** into this store and uses it for two things the HUD previously could not answer:
 *
 * - deriving deficits in the browser: a producer's input deficit (current task inputs − building
 *   contents) and a construction site's remaining cost (building cost − the paid share), both
 *   computed here from raw fields — never requested from the server as derived HUD numbers;
 * - the i18n registry's server namespace: every name the catalogue carries is registered as a
 *   key-value (`content.<path>`, `dim.<key>`) so display names resolve through the same registry the
 *   client dictionaries live in (client translation first, server name as fallback).
 *
 * The exact server shape is still being finalised, so the parser accepts the plausible variants
 * (maps or arrays, cost objects or `[gold, wood]` pairs, size pairs or `{width,height}` objects,
 * recipe inputs as an object map or an `{item, amount}` list) and drops anything it cannot read
 * rather than rejecting the snapshot. A snapshot without `content` — an older server, the demo
 * world — yields `undefined` and every caller keeps the current display (no invented numbers).
 *
 * This module is DOM-free so `scripts/check-render-math.ts` can exercise the parser and the two
 * deficit functions directly.
 */

import { registerServerKeyValues } from "./i18n.ts";

/** One gold/wood cost, the same shape the wire uses for repair/reclaim/upgrade prices. */
export interface ContentCost {
  readonly gold: number;
  readonly wood: number;
}

/** One content definition of the catalogue, as far as the client can read it. */
export interface ContentEntry {
  /** Server display name, when the catalogue carries one. */
  readonly name?: string;
  /** Placement / construction cost in the two wire resources. */
  readonly cost?: ContentCost;
  /** Footprint in tiles, when the catalogue reports one. */
  readonly size?: { readonly width: number; readonly height: number };
  /**
   * Recipe inputs keyed by item path, one entry per recipe the content defines. The producer
   * deficit compares the current task's recipe against the building's own contents.
   */
  readonly recipes: readonly (readonly RecipeInput[])[];
}

/** One recipe input row: the item content path and the amount the recipe consumes. */
export interface RecipeInput {
  readonly item: string;
  readonly amount: number;
}

/** One terrain row of the served `terrains` table: what the client's ghost may promise. */
export interface TerrainFacts {
  readonly buildable: boolean;
  readonly passable: boolean;
}

/** The parsed content catalogue, or `undefined` when the snapshot carried none. */
export interface ContentCatalogue {
  /** Dimension key to display name. */
  readonly dimensions: ReadonlyMap<string, string>;
  readonly units: ReadonlyMap<string, ContentEntry>;
  readonly buildings: ReadonlyMap<string, ContentBuildingEntry>;
  /**
   * Terrain id → placement facts, served so the placement ghost never guesses which terrains take
   * buildings (the classic catalogue also builds on hell_ground/hell_ash). Absent on older
   * servers; rows omitting a flag default it to `false`.
   */
  readonly terrains: ReadonlyMap<string, TerrainFacts>;
}

/** One building definition; buildings carry recipes the unit defs do not need on the client. */
export type ContentBuildingEntry = ContentEntry;

/** One missing-input line of a deficit: the item path and how much is still short. */
export interface DeficitItem {
  readonly item: string;
  readonly missing: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function finiteNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function nonNegativeInt(value: unknown): number | null {
  const number = finiteNumber(value);
  return number !== null && Number.isSafeInteger(number) && number >= 0 ? number : null;
}

function cleanKey(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const key = value.trim();
  return key === "" ? null : key;
}

/**
 * Normalises any item-amount shape into `[{item, amount}]`: an object map (`{gold: 20}`) or a list of
 * `{"item": ..., "amount": ...}` rows. Unreadable rows are dropped, not fatal.
 */
function parseInputs(value: unknown): RecipeInput[] {
  const inputs: RecipeInput[] = [];
  if (isRecord(value)) {
    for (const [rawItem, rawAmount] of Object.entries(value)) {
      const item = cleanKey(rawItem);
      const amount = nonNegativeInt(rawAmount);
      if (item !== null && amount !== null && amount > 0) {
        inputs.push({ item, amount });
      }
    }
    return inputs;
  }
  if (Array.isArray(value)) {
    for (const row of value) {
      if (!isRecord(row)) {
        continue;
      }
      const item = cleanKey(row["item"]);
      const amount = nonNegativeInt(row["amount"]);
      if (item !== null && amount !== null && amount > 0) {
        inputs.push({ item, amount });
      }
    }
  }
  return inputs;
}

/**
 * Reads one cost in either wire shape: `{"gold":n,"wood":n}` or `[gold, wood]`. A missing or
 * malformed half drops the whole cost, so a deficit is never invented from partial data.
 */
function parseCost(value: unknown): ContentCost | undefined {
  if (isRecord(value)) {
    const gold = nonNegativeInt(value["gold"]);
    const wood = nonNegativeInt(value["wood"]);
    if (gold !== null && wood !== null) {
      return { gold, wood };
    }
    return undefined;
  }
  if (Array.isArray(value) && value.length >= 2) {
    const gold = nonNegativeInt(value[0]);
    const wood = nonNegativeInt(value[1]);
    if (gold !== null && wood !== null) {
      return { gold, wood };
    }
  }
  return undefined;
}

/** Reads one footprint in either shape: `[width, height]` or `{"width":..,"height":..}`. */
function parseSize(value: unknown): ContentEntry["size"] {
  if (Array.isArray(value) && value.length >= 2) {
    const width = nonNegativeInt(value[0]);
    const height = nonNegativeInt(value[1]);
    if (width !== null && height !== null && width > 0 && height > 0) {
      return { width, height };
    }
    return undefined;
  }
  if (isRecord(value)) {
    const width = nonNegativeInt(value["width"]);
    const height = nonNegativeInt(value["height"]);
    if (width !== null && height !== null && width > 0 && height > 0) {
      return { width, height };
    }
  }
  return undefined;
}

/**
 * Reads one content definition. Every field is optional: a name-only entry is still useful to the
 * i18n registry, and a malformed half is dropped rather than half-read.
 */
function parseEntry(value: unknown): ContentEntry | undefined {
  if (!isRecord(value)) {
    // A bare string is the minimal "path -> name" form; a bare [gold, wood] pair is the minimal
    // "path -> cost" form; anything else is not an entry.
    if (typeof value === "string" && value.trim() !== "") {
      return { name: value.trim(), recipes: [] };
    }
    const bareCost = parseCost(value);
    if (bareCost !== undefined) {
      return { cost: bareCost, recipes: [] };
    }
    return undefined;
  }
  const name = typeof value["name"] === "string" && value["name"].trim() !== ""
    ? value["name"].trim()
    : undefined;
  const cost = parseCost(value["cost"]);
  const size = parseSize(value["size"]);
  const rawRecipes = value["recipes"];
  const recipes: RecipeInput[][] = [];
  if (Array.isArray(rawRecipes)) {
    for (const rawRecipe of rawRecipes) {
      const inputs = parseInputs(isRecord(rawRecipe) ? rawRecipe["inputs"] : rawRecipe);
      if (inputs.length > 0) {
        recipes.push(inputs);
      }
    }
  } else if (rawRecipes !== undefined) {
    // A single recipe object (not wrapped in an array) is accepted as a one-recipe list.
    const inputs = parseInputs(rawRecipes);
    if (inputs.length > 0) {
      recipes.push(inputs);
    }
  }
  if (name === undefined && cost === undefined && size === undefined && recipes.length === 0) {
    return undefined;
  }
  return {
    ...(name === undefined ? {} : { name }),
    ...(cost === undefined ? {} : { cost }),
    ...(size === undefined ? {} : { size }),
    recipes,
  };
}

/** Reads one catalogue section that is either a keyed map or a list of `{key/path, ...}` rows. */
function parseSection(
  value: unknown,
  keyFields: readonly string[],
): Map<string, ContentEntry> {
  const entries = new Map<string, ContentEntry>();
  if (isRecord(value)) {
    for (const [rawKey, rawEntry] of Object.entries(value)) {
      const key = cleanKey(rawKey);
      const entry = parseEntry(rawEntry);
      if (key !== null && entry !== undefined) {
        entries.set(key, entry);
      }
    }
    return entries;
  }
  if (Array.isArray(value)) {
    for (const rawEntry of value) {
      if (!isRecord(rawEntry)) {
        continue;
      }
      let key: string | null = null;
      for (const field of keyFields) {
        key = cleanKey(rawEntry[field]);
        if (key !== null) {
          break;
        }
      }
      const entry = parseEntry(rawEntry);
      if (key !== null && entry !== undefined) {
        entries.set(key, entry);
      }
    }
  }
  return entries;
}

/** Reads the catalogue's dimension roster (key -> name) in either map or array shape. */
function parseDimensions(value: unknown): Map<string, string> {
  const dimensions = new Map<string, string>();
  if (isRecord(value)) {
    for (const [rawKey, rawName] of Object.entries(value)) {
      const key = cleanKey(rawKey);
      const name = isRecord(rawName)
        ? cleanKey(rawName["name"]) ?? cleanKey(rawName["id"])
        : cleanKey(rawName);
      if (key !== null && name !== null) {
        dimensions.set(key, name);
      }
    }
    return dimensions;
  }
  if (Array.isArray(value)) {
    for (const row of value) {
      if (!isRecord(row)) {
        continue;
      }
      const key = cleanKey(row["key"]) ?? cleanKey(row["id"]);
      const name = cleanKey(row["name"]);
      if (key !== null && name !== null) {
        dimensions.set(key, name);
      }
    }
  }
  return dimensions;
}

/** Reads the catalogue's `terrains` table (id → {buildable, passable}); map shape only. */
function parseTerrains(value: unknown): Map<string, TerrainFacts> {
  const terrains = new Map<string, TerrainFacts>();
  if (!isRecord(value)) {
    return terrains;
  }
  for (const [rawKey, rawRow] of Object.entries(value)) {
    const key = cleanKey(rawKey);
    if (key === null || !isRecord(rawRow)) {
      continue;
    }
    terrains.set(key, {
      buildable: rawRow["buildable"] === true,
      passable: rawRow["passable"] === true,
    });
  }
  return terrains;
}

/**
 * Parses the snapshot's optional top-level `content` object into the store.
 *
 * `undefined` for anything that is not a readable catalogue: an absent field (older server, demo
 * world), a non-object, or an object with no readable section. Sections that are present but empty
 * parse as empty maps, so a partial catalogue still contributes what it can.
 */
export function parseContentCatalogue(value: unknown): ContentCatalogue | undefined {
  if (!isRecord(value)) {
    return undefined;
  }
  const catalogue: ContentCatalogue = {
    dimensions: parseDimensions(value["dimensions"]),
    units: parseSection(value["units"], ["path", "key", "id"]),
    buildings: parseSection(value["buildings"], ["path", "key", "id"]),
    terrains: parseTerrains(value["terrains"]),
  };
  if (
    catalogue.dimensions.size === 0 &&
    catalogue.units.size === 0 &&
    catalogue.buildings.size === 0 &&
    catalogue.terrains.size === 0
  ) {
    return undefined;
  }
  return catalogue;
}

/**
 * Registers the catalogue's names in the i18n server namespace: one `content.<path>` per unit /
 * building and one `dim.<key>` per dimension. Called by the page when a snapshot brings a catalogue;
 * re-registering a newer catalogue simply replaces the values.
 */
export function registerCatalogueNames(catalogue: ContentCatalogue | undefined): void {
  if (catalogue === undefined) {
    return;
  }
  const values: Record<string, string> = {};
  for (const [key, name] of catalogue.dimensions) {
    values[`dim.${key}`] = name;
  }
  for (const [path, entry] of catalogue.units) {
    if (entry.name !== undefined) {
      values[`content.${path}`] = entry.name;
    }
  }
  for (const [path, entry] of catalogue.buildings) {
    if (entry.name !== undefined) {
      values[`content.${path}`] = entry.name;
    }
  }
  registerServerKeyValues(values);
}

/**
 * The catalogue entry for one content path, preferring the buildings table for `building.*` paths.
 *
 * A path the catalogue does not define answers `undefined`, which is the "absent catalogue / absent
 * id" case every caller must keep displaying as today.
 */
export function contentEntryOf(
  catalogue: ContentCatalogue | undefined,
  kind: string,
): ContentEntry | undefined {
  if (catalogue === undefined) {
    return undefined;
  }
  return (
    catalogue.buildings.get(kind) ??
    catalogue.units.get(kind) ??
    catalogue.buildings.get(kind.replace(/^building\./u, "")) ??
    catalogue.units.get(kind.replace(/^unit\./u, ""))
  );
}

/** The placement/construction cost of one building path, or `undefined` when unknown. */
export function buildingCostOf(
  catalogue: ContentCatalogue | undefined,
  kind: string,
): ContentCost | undefined {
  return contentEntryOf(catalogue, kind)?.cost;
}

/**
 * The producer input deficit of one building: the current task's recipe inputs minus what the
 * building itself holds.
 *
 * The current task is the job the snapshot reports as producing now (`producingNow.kind`), falling
 * back to the first unfinished queue item (`queueItems[0].kind`). Inputs come from the catalogue's
 * recipe list for that output path; the held amounts come from the row's raw `contents`. Only short
 * inputs are listed, in the recipe's own order. `undefined` when the catalogue does not know the
 * recipe (the caller keeps the current display — the snapshot's `demand` object — instead of
 * inventing numbers), when the building holds everything, or when the row has no current task.
 */
export function producerDeficit(
  catalogue: ContentCatalogue | undefined,
  entity: {
    readonly kind: string;
    readonly producingNow?: { readonly kind: string };
    readonly queueItems?: readonly { readonly kind: string }[];
    readonly contents?: Readonly<Record<string, number>>;
  },
): readonly DeficitItem[] | undefined {
  if (catalogue === undefined) {
    return undefined;
  }
  const taskKind = entity.producingNow?.kind ?? entity.queueItems?.[0]?.kind;
  if (taskKind === undefined) {
    return undefined;
  }
  const recipe = contentEntryOf(catalogue, taskKind)?.recipes[0];
  if (recipe === undefined) {
    return undefined;
  }
  const contents = entity.contents ?? {};
  const deficits: DeficitItem[] = [];
  for (const input of recipe) {
    const held = contents[input.item];
    const have = typeof held === "number" && Number.isFinite(held) && held > 0 ? held : 0;
    if (have < input.amount) {
      deficits.push({ item: input.item, missing: input.amount - have });
    }
  }
  return deficits.length === 0 ? undefined : deficits;
}

/**
 * The construction deficit of one unfinished site: the building's catalogue cost minus the paid
 * share.
 *
 * The only raw progress field the snapshot carries is `progress` in [0, 1], so the paid share is
 * `cost × progress` per resource and the deficit is the remainder, rounded **up** (a half-paid gold
 * unit still needs the whole unit). `undefined` when the catalogue has no cost for the kind, when
 * the row does not report progress (an older snapshot — nothing is invented), or when the site is
 * already paid for.
 */
export function constructionDeficit(
  catalogue: ContentCatalogue | undefined,
  entity: {
    readonly kind: string;
    readonly progress?: number;
  },
): readonly DeficitItem[] | undefined {
  if (catalogue === undefined) {
    return undefined;
  }
  const cost = buildingCostOf(catalogue, entity.kind);
  const progress = entity.progress;
  if (cost === undefined || progress === undefined || !Number.isFinite(progress) || progress >= 1) {
    return undefined;
  }
  const fraction = Math.min(1, Math.max(0, progress));
  const deficits: DeficitItem[] = [];
  const gold = Math.ceil(cost.gold * (1 - fraction));
  const wood = Math.ceil(cost.wood * (1 - fraction));
  if (gold > 0) {
    deficits.push({ item: "gold", missing: gold });
  }
  if (wood > 0) {
    deficits.push({ item: "wood", missing: wood });
  }
  return deficits.length === 0 ? undefined : deficits;
}

/**
 * Formats one deficit list as `item amount · item amount` with the caller's item labels.
 *
 * The renderer of the HUD line passes the same `labelForPath` resolver every other surface uses, so
 * a deficit line can never name an item differently from the stock line next to it.
 */
export function formatDeficit(
  deficits: readonly DeficitItem[] | undefined,
  labelForPath: (path: string) => string,
): string | undefined {
  if (deficits === undefined || deficits.length === 0) {
    return undefined;
  }
  const parts = deficits.map((deficit) => `${labelForPath(deficit.item)} ${deficit.missing}`);
  return parts.join(" · ");
}
