/**
 * Pure logistics rules: the warehouse panel's per-warehouse view, its header labels and the manual
 * transfer form's validation.
 *
 * The snapshot gives every own warehouse its own `contents` and `storage` object (capacity / used / free
 * slots) plus the optional `inbound` count of jobs in flight towards it. The panel used to sum every own
 * building, so a player with two warehouses could not answer "what is in *this* one". The model built
 * here is the rule the DOM half in `main.ts` paints:
 *
 * - **Single view.** Exactly one own warehouse selected -> that building's own rows and capacity.
 * - **Total view.** Everything else -> the existing sum over every own building, with the same rows and
 *   marks as before.
 * - **Marks stay item-level.** A checkbox marks an item path for the HUD's `已标记` line, and that line is
 *   always the all-warehouse total (the invariant `resources.ts` documents). In single mode the panel says
 *   so instead of silently showing one warehouse's amount under a total label.
 * - **The transfer form.** The pure planner validates empty item, zero/negative amount, the same building
 *   and an unreachable (unknown or under-construction) target before a line reaches the socket; the engine
 *   owns the real same-dimension/path rules and its `ERR` text is shown verbatim.
 *
 * `scripts/check-render-math.ts` exercises every function here offline.
 */

import { transfer } from "../commands.ts";
import { t } from "../i18n.ts";
import {
  isWarehouseRow,
  sumContents,
  warehouseResourceTotals,
  type WarehouseResource,
  type WarehouseTotals,
} from "./resources.ts";
import { itemDisplayName } from "./status.ts";
import {
  isBuildingEntityKind,
  ownershipOf,
  underConstruction,
  type EntityView,
  type WorldView,
} from "./view.ts";

/** Which view the player asked for; only meaningful while exactly one own warehouse is selected. */
export type WarehouseScope = "single" | "total";

/** The view the panel actually renders, after the selection and the preference are combined. */
export type WarehousePanelScope = "none" | "single" | "total";

/**
 * Label of the scope toggle in single mode: it returns to the sum over every own warehouse.
 *
 * Both words the requirement names are in the label so a player can find either; the title explains what
 * a click does. These labels are functions, not constants, so a language switch re-renders them.
 */
export function warehouseScopeToggleSingleLabel(): string {
  return t("logistics.scopeSingle");
}

/** Label of the scope toggle in total mode: it returns to the selected warehouse's own contents. */
export function warehouseScopeToggleTotalLabel(): string {
  return t("logistics.scopeTotal");
}

/** Title of the scope toggle while the panel shows one warehouse. */
export function warehouseScopeTitleToTotal(): string {
  return t("logistics.scopeTitleToTotal");
}

/** Title of the scope toggle while the panel shows the sum. */
export function warehouseScopeTitleToSingle(): string {
  return t("logistics.scopeTitleToSingle");
}

/** What a checkbox means in single mode; shown under the rows so the totals line is unambiguous. */
export function warehouseMarkNote(): string {
  return t("logistics.markNote");
}

/** Label of the form's "fill the source amount" shortcut. */
export function warehouseTransferAllLabel(): string {
  return t("logistics.transferAll");
}

/** One rendered view of the warehouse panel. */
export interface WarehousePanelModel {
  /** `none` when the player owns no warehouse at all, otherwise the view in use. */
  readonly scope: WarehousePanelScope;
  /** The single selected own warehouse, or `null` when the selection is not exactly one. */
  readonly single: EntityView | null;
  /** The all-warehouse sum; the marked HUD line always reads this, in either view. */
  readonly total: WarehouseTotals;
  /** The rows the list shows: the single warehouse's contents or the sum. */
  readonly rows: readonly WarehouseResource[];
  /** Header line: capacity / used / free / inbound in single mode, the warehouse count in total mode. */
  readonly summary: string;
  /** Scope toggle label, or `null` when there is nothing to toggle (no single warehouse selected). */
  readonly toggleLabel: string | null;
  /** Scope-toggle tooltip; empty when the toggle is hidden. */
  readonly toggleTitle: string;
  /** Marking explanation, only in single mode; empty otherwise. */
  readonly note: string;
  /** Own finished warehouses other than the source, sorted by id; the transfer target options. */
  readonly targets: readonly EntityView[];
  /** Items the source holds, sorted by display name; the transfer item options. */
  readonly sourceItems: readonly WarehouseResource[];
  /** Whether the transfer form belongs in the panel (single view only). */
  readonly transferVisible: boolean;
  /** Whether a transfer can be sent right now. */
  readonly transferReady: boolean;
  /** Why the form is unavailable, or empty when it is ready. */
  readonly transferReason: string;
}

/** Own warehouse buildings in the snapshot, sorted by entity id for a deterministic option order. */
export function ownWarehouses(world: WorldView, myPlayerId: number | null): EntityView[] {
  if (myPlayerId === null) {
    return [];
  }
  const found: EntityView[] = [];
  for (const entity of world.entities) {
    if (!isBuildingEntityKind(entity.kind) || !isWarehouseRow(entity)) {
      continue;
    }
    if (ownershipOf(entity.playerId, myPlayerId) === "mine") {
      found.push(entity);
    }
  }
  found.sort((left, right) => left.id - right.id);
  return found;
}

/**
 * The exactly-one selected own warehouse, or `null`.
 *
 * A mixed selection (a warehouse plus a unit) is deliberately not "exactly one warehouse": the panel then
 * keeps the total view instead of guessing which building the player meant.
 */
export function selectedOwnWarehouse(
  selection: readonly EntityView[],
  myPlayerId: number | null,
): EntityView | null {
  if (myPlayerId === null || selection.length !== 1) {
    return null;
  }
  const only = selection[0];
  if (only === undefined || !isBuildingEntityKind(only.kind) || !isWarehouseRow(only)) {
    return null;
  }
  return ownershipOf(only.playerId, myPlayerId) === "mine" ? only : null;
}

/**
 * The capacity line of one warehouse: `容量 24 · 已用 2 · 空闲 22`, or `容量未知（旧快照）` when the
 * snapshot has no `storage` object.
 *
 * The three numbers come from the server because only it knows every item's maximum stack size; the
 * client must not pretend a slot count it cannot compute.
 */
export function warehouseStorageLabel(entity: EntityView): string {
  const storage = entity.storage;
  if (storage === undefined) {
    return t("logistics.capacityUnknown");
  }
  return t("logistics.capacityLabel", {
    slots: storage.slots,
    used: storage.used,
    free: storage.free,
  });
}

/** The inbound-transfers fragment of the header: `在途 1`, or `在途 0` when nothing is on its way. */
export function warehouseInboundLabel(entity: EntityView): string {
  const inbound = entity.inbound;
  return t("logistics.inboundLabel", {
    count: inbound === undefined || !Number.isSafeInteger(inbound) ? 0 : inbound,
  });
}

/**
 * Builds the whole panel model from one snapshot, the selection and the player's scope preference.
 *
 * The preference only applies while exactly one own warehouse is selected: with no warehouse (or a mixed
 * selection) the panel shows the sum, and with no own warehouse at all it shows the historical "none"
 * message. The total half is computed unconditionally because the marked HUD line reads it in both views.
 */
export function warehousePanelModel(
  world: WorldView,
  myPlayerId: number | null,
  selection: readonly EntityView[],
  scope: WarehouseScope,
  labelFor: (path: string) => string = itemDisplayName,
): WarehousePanelModel {
  const total = warehouseResourceTotals(world, myPlayerId, labelFor);
  const single = selectedOwnWarehouse(selection, myPlayerId);
  const singleView = single !== null && scope === "single";
  const empty = total.warehouseCount === 0 && total.items.length === 0;
  const panelScope: WarehousePanelScope = singleView ? "single" : empty ? "none" : "total";
  const rows = singleView && single !== null ? sumContents([single], labelFor) : total.items;
  const targets =
    single === null
      ? []
      : ownWarehouses(world, myPlayerId).filter(
          (candidate) => candidate.id !== single.id && !underConstruction(candidate),
        );
  const sourceItems = singleView ? rows : [];

  let summary: string;
  if (panelScope === "none") {
    summary = t("logistics.summaryNone");
  } else if (panelScope === "single" && single !== null) {
    const stock = rows.length === 0 ? t("logistics.summaryNoStock") : "";
    summary = t("logistics.summarySingle", {
      id: single.id,
      storage: warehouseStorageLabel(single),
      inbound: warehouseInboundLabel(single),
      stock,
    });
  } else if (total.items.length === 0) {
    summary = t("logistics.summaryCountNoStock", { count: total.warehouseCount });
  } else if (total.warehouseCount === 0) {
    summary = t("logistics.summaryAllTotal");
  } else {
    summary = single === null
      ? t("logistics.summaryCount", { count: total.warehouseCount })
      : `${t("logistics.summaryCount", { count: total.warehouseCount })}${t("logistics.summaryTotalSuffix")}`;
  }

  const transferReason = transferBlockReason(single, singleView, sourceItems, targets);
  const transferVisible = panelScope === "single";
  return {
    scope: panelScope,
    single,
    total,
    rows,
    summary,
    toggleLabel:
      single === null
        ? null
        : singleView
          ? warehouseScopeToggleSingleLabel()
          : warehouseScopeToggleTotalLabel(),
    toggleTitle:
      single === null
        ? ""
        : singleView
          ? warehouseScopeTitleToTotal()
          : warehouseScopeTitleToSingle(),
    note: panelScope === "single" ? warehouseMarkNote() : "",
    targets,
    sourceItems,
    transferVisible,
    transferReady: transferVisible && transferReason === "",
    transferReason,
  };
}

/** Why the transfer form cannot send, or empty when it can. */
function transferBlockReason(
  single: EntityView | null,
  singleView: boolean,
  sourceItems: readonly WarehouseResource[],
  targets: readonly EntityView[],
): string {
  if (!singleView || single === null) {
    return "";
  }
  if (underConstruction(single)) {
    return t("transfer.sourceUnfinished");
  }
  if (sourceItems.length === 0) {
    return t("transfer.noStock");
  }
  if (targets.length === 0) {
    return t("transfer.noTargets");
  }
  return "";
}

/** One validated transfer form submission. */
export interface TransferFormModel {
  /** Whether the line may be sent. */
  readonly ok: boolean;
  /** Readable reason when `ok` is false; empty otherwise. */
  readonly refusal: string;
  /** The command line, or the empty string on a refusal. */
  readonly line: string;
  /** The chosen item path, or the empty string. */
  readonly itemPath: string;
  /** The chosen amount, or `null` for the `all` literal and on a refusal. */
  readonly amount: number | null;
  /** The chosen target id, or `null`. */
  readonly targetId: number | null;
}

/**
 * Validates one transfer form submission without sending anything.
 *
 * The checks are the client's business: an empty item, a blank/zero/negative amount, the source itself as
 * the target and a target that is not one of the player's other finished warehouses (the "unreachable"
 * case the client can see - whether a route actually exists is the engine's rule and comes back as its
 * own `ERR ... unreachable ...`). The literal `all` is accepted and forwarded as-is.
 */
export function transferFormModel(
  source: EntityView | null,
  targets: readonly EntityView[],
  itemPath: string,
  amountText: string,
  targetId: number | null,
): TransferFormModel {
  const empty: TransferFormModel = {
    ok: false,
    refusal: "",
    line: "",
    itemPath: "",
    amount: null,
    targetId: null,
  };
  if (source === null || !isBuildingEntityKind(source.kind) || !isWarehouseRow(source)) {
    return { ...empty, refusal: t("transfer.needOwn") };
  }
  if (underConstruction(source)) {
    return { ...empty, refusal: t("transfer.sourceUnfinished") };
  }
  const path = itemPath.trim();
  if (path === "") {
    return { ...empty, refusal: t("transfer.pickItem") };
  }
  const held = source.contents?.[path];
  if (held === undefined || !Number.isSafeInteger(held) || held <= 0) {
    return { ...empty, itemPath: path, refusal: t("transfer.noItem") };
  }
  const text = amountText.trim();
  if (text === "") {
    return { ...empty, itemPath: path, refusal: t("transfer.enterAmount") };
  }
  const all = text.toLowerCase() === "all";
  let amount: number | null = null;
  if (!all) {
    if (!/^\d+$/u.test(text)) {
      return { ...empty, itemPath: path, refusal: t("transfer.amountInteger") };
    }
    const parsed = Number(text);
    if (!Number.isSafeInteger(parsed) || parsed <= 0) {
      return { ...empty, itemPath: path, refusal: t("transfer.amountInteger") };
    }
    amount = parsed;
  }
  const target = targetId === null ? undefined : targets.find((candidate) => candidate.id === targetId);
  if (targetId === null) {
    return { ...empty, itemPath: path, amount, refusal: t("transfer.pickTarget") };
  }
  if (targetId === source.id) {
    return { ...empty, itemPath: path, amount, refusal: t("transfer.sameTarget") };
  }
  if (target === undefined) {
    return { ...empty, itemPath: path, amount, refusal: t("transfer.unreachable") };
  }
  if (underConstruction(target)) {
    return { ...empty, itemPath: path, amount, refusal: t("transfer.targetUnfinished") };
  }
  let line: string;
  try {
    line = transfer(source.id, target.id, path, all ? "all" : amount ?? 0);
  } catch (error: unknown) {
    return { ...empty, itemPath: path, amount, refusal: error instanceof Error ? error.message : String(error) };
  }
  return { ok: true, refusal: "", line, itemPath: path, amount, targetId: target.id };
}

/** One parsed transfer answer for the form's status line. */
export interface TransferAnswerLabel {
  readonly ok: boolean;
  readonly text: string;
}

/**
 * Turns one server answer into the status line text.
 *
 * `OK 120 gold` reads `已调拨：120 金`; an `ERR ...` keeps the engine's own reason after `调拨失败：`; any
 * other line is shown verbatim so an older server's wording is never hidden. The item label goes through
 * the same resolver the panel rows use, so the status line and the row cannot rename the same item.
 */
export function transferAnswerLabel(
  answer: string,
  labelFor: (path: string) => string = itemDisplayName,
): TransferAnswerLabel {
  const trimmed = answer.trim();
  const moved = /^OK (\d+) (\S+)$/u.exec(trimmed);
  if (moved !== null) {
    const amount = Number(moved[1]);
    const path = moved[2] ?? "";
    const name = labelFor(path).trim();
    return {
      ok: true,
      text: t("transfer.done", { amount, item: name === "" ? path : name }),
    };
  }
  if (trimmed === "OK") {
    return { ok: true, text: t("transfer.ok") };
  }
  if (trimmed === "ERR") {
    return { ok: false, text: t("transfer.failed") };
  }
  if (trimmed.startsWith("ERR ")) {
    return { ok: false, text: t("transfer.failedReason", { reason: trimmed.slice(4) }) };
  }
  return {
    ok: false,
    text: t("transfer.answer", { answer: trimmed === "" ? t("transfer.emptyAnswer") : trimmed }),
  };
}

/**
 * The readable target-option label: `#12`.
 *
 * Every target option is one of the player's own warehouses and the source is the selected warehouse, so
 * the id is the only thing that differs; the command sends that id. The form's title names the warehouse
 * while the source select names the contents.
 */
export function transferTargetLabel(entity: EntityView): string {
  return `#${entity.id}`;
}
