/**
 * The warehouse-resource panel's pure aggregation.
 *
 * The snapshot reports each warehouse's stored items in the optional `contents` object, but it has no
 * "marked resource" concept and no global inventory summary. The client therefore offers a read-only
 * total: sum the `contents` objects of the local player's buildings, label each item and sort the rows
 * by their display name. The panel never writes anything and does not invent a selection or marking
 * feature the server does not have.
 *
 * `scripts/check-render-math.ts` exercises this module offline; the DOM half is a tiny list in
 * `main.ts`.
 */

import { itemDisplayName } from "./status.ts";
import {
  isBuildingEntityKind,
  kindName,
  ownershipOf,
  type WorldView,
} from "./view.ts";

/** One item row of the total: its content path, its display name and the summed amount. */
export interface WarehouseResource {
  /** Item content path, for example `gold`. */
  readonly path: string;
  /** Manifest/known-label display name, for example `金`. */
  readonly name: string;
  /** Sum over every own building's positive `contents` entry for this path. */
  readonly amount: number;
}

/** The whole result of the aggregation. */
export interface WarehouseTotals {
  /** Number of own warehouse buildings inside the snapshot, even when they are empty. */
  readonly warehouseCount: number;
  /** Non-zero item totals, sorted by display name. */
  readonly items: readonly WarehouseResource[];
}

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

/**
 * Sums the stored items of every own building in the snapshot.
 *
 * Only buildings owned by `myPlayerId` contribute. A building without `contents` (an empty warehouse,
 * a unit-producing building, an older snapshot) contributes nothing but still counts when it is a
 * warehouse. Amounts are already validated by the snapshot parser (positive safe integers), and the
 * guard is repeated here so a directly constructed fixture cannot inject a fractional or negative
 * total. The default label is the classic `金` / `木` mapping with the raw path as fallback; the page
 * passes a resolver that first consults the plugin manifest.
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
  const totals = new Map<string, number>();
  for (const entity of world.entities) {
    if (!isBuildingEntityKind(entity.kind)) {
      continue;
    }
    if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
      continue;
    }
    if (isWarehouseKind(entity.kind)) {
      warehouseCount += 1;
    }
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

  const items: WarehouseResource[] = [];
  for (const [path, amount] of totals) {
    const label = labelFor(path).trim();
    items.push({ path, name: label === "" ? kindName(path) : label, amount });
  }
  items.sort(compareResources);
  return { warehouseCount, items };
}
