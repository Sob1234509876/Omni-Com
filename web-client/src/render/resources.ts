/**
 * Resource aggregation, client-side marks and the inventory / full-resource models.
 *
 * The snapshot reports each building's stored items in the optional `contents` object, reports a unit's
 * cargo in the same shape, and has no "marked resource" concept. Everything here is therefore read-only
 * client state derived from one snapshot:
 *
 * - {@link sumContents} is the one summing rule shared by the warehouse total, a unit's cargo, the
 *   inventory panel and the full resource page; it never duplicates an amount or invents an item.
 * - {@link warehouseResourceTotals} sums the contents of the local player's buildings (the top-right
 *   panel), while {@link inventoryTotals} sums the cargo of the selected units (the dedicated inventory
 *   HUD).
 * - {@link toggleResourceMark}, {@link markAllResources} and {@link clearResourceMarks} are the pure
 *   mark/unmark rules. Marks are a set of item paths in the page's memory: nothing is sent to the
 *   server, and a mark for an item that has left the world is still kept so it can reappear.
 * - {@link markedResourceItems} and {@link markedSummaryLabel} produce the compact
 *   `已标记：金 120 · 木 40` HUD line from the *same* warehouse totals, so a marked item can never show
 *   a different amount than the panel above it.
 * - {@link fullResourceList} merges the item definitions a plugin manifest publishes, every item found
 *   in the local player's buildings or units' cargo, and the two currencies. Rows are de-duplicated by
 *   content path, sorted by display name, and an item with no stock keeps an explicit `0`.
 *
 * `scripts/check-render-math.ts` exercises this module offline; the DOM halves are small lists in
 * `main.ts`.
 */

import { compareItemPaths, itemDisplayName } from "./status.ts";
import {
  isBuildingEntityKind,
  kindName,
  ownershipOf,
  type EntityView,
  type WorldView,
} from "./view.ts";

/** One item row: its content path, its display name and the summed amount. */
export interface WarehouseResource {
  /** Item content path, for example `gold`. */
  readonly path: string;
  /** Manifest/known-label display name, for example `金`. */
  readonly name: string;
  /** Sum over every contributing `contents` entry; always a positive safe integer in totals. */
  readonly amount: number;
}

/** The warehouse panel's aggregation result. */
export interface WarehouseTotals {
  /** Number of own warehouse buildings inside the snapshot, even when they are empty. */
  readonly warehouseCount: number;
  /** Non-zero item totals, sorted by display name. */
  readonly items: readonly WarehouseResource[];
}

/** The selected units' cargo, aggregated for the inventory HUD. */
export interface InventoryTotals {
  /** Number of selected units (buildings do not contribute cargo). */
  readonly unitCount: number;
  /** Number of those units that carry at least one positive item amount. */
  readonly carryingCount: number;
  /** Aggregated cargo rows, sorted by display name; empty when nothing is carried. */
  readonly items: readonly WarehouseResource[];
  /** Sum of every row's amount, or `0` when nothing is carried. */
  readonly total: number;
}

/** One row of the full resource page. */
export interface FullResourceRow {
  /** Item content path, or `gold` / `wood` for the two currencies. */
  readonly path: string;
  /** Display name from the manifest or the classic labels. */
  readonly name: string;
  /**
   * The owned total: the player's currency balance for `gold` / `wood`, plus every positive amount
   * found in the local player's buildings and units. A row that exists only in the manifest keeps `0`.
   */
  readonly amount: number;
  /** Own building ids that hold the item, sorted ascending; empty for a currency-only row. */
  readonly buildingIds: readonly number[];
  /** Own unit ids whose cargo holds the item, sorted ascending. */
  readonly unitIds: readonly number[];
}

/** The two currency paths every world has, even when the player's balance is zero. */
export const CURRENCY_PATHS: readonly string[] = ["gold", "wood"];

/** The compact HUD line shown before anything is marked. */
export const MARKED_EMPTY_LABEL = "已标记：—";

/** Client-side marks keyed by item content path. Never sent to the server. */
export type ResourceMarks = ReadonlySet<string>;

/** The empty mark set; a change always returns a new set instead of mutating this one. */
export const EMPTY_RESOURCE_MARKS: ResourceMarks = new Set<string>();

/**
 * Whether a kind is a warehouse.
 *
 * The demo and legacy snapshots use `warehouse`; the server's content path is `building.warehouse`.
 * The namespace prefix is stripped first so a namespaced plugin path matches too.
 */
export function isWarehouseKind(kind: string): boolean {
  const name = kindName(kind);
  return name === "warehouse" || name === "building.warehouse" || name.startsWith("building.warehouse.");
}

function compareResources(left: WarehouseResource, right: WarehouseResource): number {
  // "Sorted by name" is a deterministic UTF-16 comparison, not a locale-dependent one: the check runs
  // under Node and the page under a browser, and both must produce the same order.
  if (left.name !== right.name) {
    return left.name < right.name ? -1 : 1;
  }
  return left.path < right.path ? -1 : left.path > right.path ? 1 : 0;
}

/** The display label for a path, with the raw name as fallback for a blank resolver answer. */
function labelForItem(path: string, labelFor: (path: string) => string): string {
  const label = labelFor(path).trim();
  return label === "" ? kindName(path) : label;
}

/** Turns one path -> amount table into sorted rows, dropping non-positive and malformed amounts. */
function resourceRows(
  totals: ReadonlyMap<string, number>,
  labelFor: (path: string) => string,
): WarehouseResource[] {
  const items: WarehouseResource[] = [];
  for (const [path, amount] of totals) {
    if (!Number.isSafeInteger(amount) || amount <= 0) {
      continue;
    }
    items.push({ path, name: labelForItem(path, labelFor), amount });
  }
  items.sort(compareResources);
  return items;
}

/**
 * The shared summing rule: add every positive `contents` entry of the given entities per item path.
 *
 * `contents` is the same object for a building's stock and a unit's cargo; callers choose the entity
 * set (own buildings for the warehouse panel, selected units for the inventory panel, both for the
 * full resource page). Amounts are validated by the snapshot parser and checked again here so a
 * directly constructed fixture cannot inject a fractional or negative total. The result is sorted by
 * display name, the order every resource surface uses.
 */
export function sumContents(
  entities: Iterable<EntityView>,
  labelFor: (path: string) => string = itemDisplayName,
): WarehouseResource[] {
  const totals = new Map<string, number>();
  for (const entity of entities) {
    const contents = entity.contents;
    if (contents === undefined) {
      continue;
    }
    for (const [path, amount] of Object.entries(contents)) {
      if (!Number.isSafeInteger(amount) || amount <= 0) {
        continue;
      }
      totals.set(path, (totals.get(path) ?? 0) + amount);
    }
  }
  return resourceRows(totals, labelFor);
}

/**
 * Sums the stored items of every own building in the snapshot.
 *
 * Only buildings owned by `myPlayerId` contribute. A building without `contents` (an empty warehouse,
 * a unit-producing building, an older snapshot) contributes nothing but still counts when it is a
 * warehouse. The default label is the classic `金` / `木` mapping with the raw path as fallback; the
 * page passes a resolver that first consults the plugin manifest and then the classic table.
 */
export function warehouseResourceTotals(
  world: WorldView,
  myPlayerId: number | null,
  labelFor: (path: string) => string = itemDisplayName,
): WarehouseTotals {
  if (myPlayerId === null) {
    return { warehouseCount: 0, items: [] };
  }

  let warehouseCount = 0;
  const ownBuildings: EntityView[] = [];
  for (const entity of world.entities) {
    if (!isBuildingEntityKind(entity.kind)) {
      continue;
    }
    if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
      continue;
    }
    ownBuildings.push(entity);
    if (isWarehouseKind(entity.kind)) {
      warehouseCount += 1;
    }
  }
  return { warehouseCount, items: sumContents(ownBuildings, labelFor) };
}

/**
 * Aggregates the selected units' cargo for the dedicated inventory HUD.
 *
 * Buildings in the selection are ignored: a building's stock is the warehouse panel's business, not a
 * unit's cargo. `unitCount` still reports the selected units even when none carries anything, so the
 * page can say "0 items carried" instead of pretending the selection is empty.
 */
export function inventoryTotals(
  selection: readonly EntityView[],
  labelFor: (path: string) => string = itemDisplayName,
): InventoryTotals {
  const units = selection.filter((entity) => !isBuildingEntityKind(entity.kind));
  const items = sumContents(units, labelFor);
  let carryingCount = 0;
  for (const unit of units) {
    const contents = unit.contents;
    if (contents === undefined) {
      continue;
    }
    if (Object.values(contents).some((amount) => Number.isSafeInteger(amount) && amount > 0)) {
      carryingCount += 1;
    }
  }
  return {
    unitCount: units.length,
    carryingCount,
    items,
    total: items.reduce((sum, item) => sum + item.amount, 0),
  };
}

/**
 * Toggles one item path in the client mark set.
 *
 * The result is a fresh set, so React-free code can treat marks as immutable state; marking an item
 * twice is the same as never marking it.
 */
export function toggleResourceMark(marks: ResourceMarks, path: string): ResourceMarks {
  const next = new Set(marks);
  if (next.has(path)) {
    next.delete(path);
  } else if (path.trim() !== "") {
    next.add(path);
  }
  return next;
}

/** Adds every non-blank path to the mark set (the panel's "mark all" action). */
export function markAllResources(marks: ResourceMarks, paths: readonly string[]): ResourceMarks {
  const next = new Set(marks);
  for (const path of paths) {
    if (path.trim() !== "") {
      next.add(path);
    }
  }
  return next;
}

/** Drops every mark (the panel's "clear" action). */
export function clearResourceMarks(): ResourceMarks {
  return new Set<string>();
}

/**
 * The marked rows, using the warehouse totals for their amounts.
 *
 * Marks are kept in a stable order (the classic `gold`, `wood` order first, then lexicographic), and a
 * marked path that is no longer in the total still gets an explicit `0` row instead of disappearing.
 */
export function markedResourceItems(
  marks: ResourceMarks,
  totals: WarehouseTotals,
  labelFor: (path: string) => string = itemDisplayName,
): WarehouseResource[] {
  const known = new Map<string, WarehouseResource>();
  for (const item of totals.items) {
    known.set(item.path, item);
  }
  const paths = [...marks].filter((path) => path.trim() !== "").sort(compareItemPaths);
  return paths.map((path) => {
    const row = known.get(path);
    return row === undefined
      ? { path, name: labelForItem(path, labelFor), amount: 0 }
      : row;
  });
}

/**
 * The compact top-chrome line: `已标记：金 120 · 木 40`, or {@link MARKED_EMPTY_LABEL} with no marks.
 *
 * The amounts are the warehouse totals, so this line and the panel's rows cannot disagree; a marked
 * item with no stock keeps an explicit `0`.
 */
export function markedSummaryLabel(
  marks: ResourceMarks,
  totals: WarehouseTotals,
  labelFor: (path: string) => string = itemDisplayName,
): string {
  const rows = markedResourceItems(marks, totals, labelFor);
  if (rows.length === 0) {
    return MARKED_EMPTY_LABEL;
  }
  return `已标记：${rows.map((row) => `${row.name} ${row.amount}`).join(" · ")}`;
}

/** A player balance that is a non-negative safe integer, or `0` for a missing/malformed value. */
function playerAmount(value: number | undefined): number {
  return value !== undefined && Number.isSafeInteger(value) && value > 0 ? value : 0;
}

/**
 * Every resource that exists in this world, for the full resource page.
 *
 * The set is the union of:
 * 1. the item content paths the plugin manifest publishes (`manifestItemPaths`, from
 *    `atlas.names.items`), even when the player owns none;
 * 2. every item path in a local player's buildings or units' cargo;
 * 3. the two currencies `gold` and `wood`.
 *
 * Paths are de-duplicated, so a currency stored in a warehouse is one row. Its owned total is the
 * player row's balance plus every positive amount in the local player's buildings and units; a row
 * that exists only in the manifest/currency seed keeps an explicit `0` (the page never hides a zero
 * row). Building and unit ids are collected per path so the page can say where a building-only item
 * sits. The result is sorted by display name, then by path for a deterministic tie-break.
 *
 * With no local player id nothing is "mine", so only the manifest items and the zero-valued
 * currencies remain: that is exactly what the client can honestly say before LOGIN/WHOAMI.
 */
export function fullResourceList(
  world: WorldView,
  myPlayerId: number | null,
  manifestItemPaths: readonly string[],
  labelFor: (path: string) => string = itemDisplayName,
): readonly FullResourceRow[] {
  const amounts = new Map<string, number>();
  const buildingIds = new Map<string, Set<number>>();
  const unitIds = new Map<string, Set<number>>();

  const seed = (path: string): void => {
    const trimmed = path.trim();
    if (trimmed === "") {
      return;
    }
    if (!amounts.has(trimmed)) {
      amounts.set(trimmed, 0);
    }
  };
  for (const path of CURRENCY_PATHS) {
    seed(path);
  }
  for (const path of manifestItemPaths) {
    seed(path);
  }

  let currencyGold = 0;
  let currencyWood = 0;
  if (myPlayerId !== null) {
    const row = world.players?.find((player) => player.id === myPlayerId);
    currencyGold = playerAmount(row?.gold);
    currencyWood = playerAmount(row?.wood);
    for (const entity of world.entities) {
      if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
        continue;
      }
      const contents = entity.contents;
      if (contents === undefined) {
        continue;
      }
      const building = isBuildingEntityKind(entity.kind);
      for (const [path, amount] of Object.entries(contents)) {
        if (!Number.isSafeInteger(amount) || amount <= 0) {
          continue;
        }
        seed(path);
        amounts.set(path, (amounts.get(path) ?? 0) + amount);
        const ids = building ? buildingIds : unitIds;
        const found = ids.get(path);
        if (found === undefined) {
          ids.set(path, new Set([entity.id]));
        } else {
          found.add(entity.id);
        }
      }
    }
  }
  amounts.set("gold", (amounts.get("gold") ?? 0) + currencyGold);
  amounts.set("wood", (amounts.get("wood") ?? 0) + currencyWood);

  const rows: FullResourceRow[] = [];
  for (const [path, amount] of amounts) {
    rows.push({
      path,
      name: labelForItem(path, labelFor),
      amount,
      buildingIds: [...(buildingIds.get(path) ?? [])].sort((left, right) => left - right),
      unitIds: [...(unitIds.get(path) ?? [])].sort((left, right) => left - right),
    });
  }
  rows.sort((left, right) => {
    if (left.name !== right.name) {
      return left.name < right.name ? -1 : 1;
    }
    return left.path < right.path ? -1 : left.path > right.path ? 1 : 0;
  });
  return rows;
}
