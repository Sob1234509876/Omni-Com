/**
 * Pure command-bar rules: which actions a selection offers, which selection chips are shown and how
 * they are abbreviated and coloured.
 *
 * The page around this module is DOM and CSS; everything that decides *what* the bottom bar offers
 * lives here so `scripts/check-render-math.ts` can exercise it without a browser:
 *
 * - {@link commandBarModel} classifies a selection (nothing / units / engineer / building / a mixed
 *   unit-and-building selection) and returns exactly the actions the page may offer. A mixed
 *   selection deliberately offers the unit actions only and says so in its hint: `TRAIN` needs one
 *   building and `BUILD` needs one engineer, so neither may be smuggled into a group order.
 * - {@link selectionChipList} turns the selected units into at most
 *   {@link SELECTION_CHIP_LIMIT} chips plus a `+N` overflow marker. Buildings are not chips: a
 *   selected building is described by the HUD chip and the right-hand context panel instead.
 * - {@link chipLabel} is the two-character manifest-name abbreviation of a chip.
 * - {@link healthBarRatio} is the thin bar under the chip label. A missing health ratio (the demo
 *   world, an older snapshot) means "full", not "dead".
 *
 * The labels are display text only; the wire commands still carry the raw content paths, and the
 * command builders stay in `src/commands.ts`. The page styles the bar as greyscale translucent chrome;
 * the only colour the model carries is the chip health bar, which uses the same ownership colour as
 * the ground rings and minimap dots (`ownershipColor`).
 */

import { BUILDING_PATHS, trainableUnitsForBuilding } from "../commands.ts";
import { isEngineerKind } from "./selection.ts";
import { progressBarRatio } from "./status.ts";
import {
  isBuildingEntityKind,
  isOwnUnit,
  kindName,
  ownershipColor,
  ownershipDecision,
  ownershipOf,
  type EntityView,
  type Ownership,
} from "./view.ts";

/** How many selection chips fit in the strip before the rest collapse into a `+N` chip. */
export const SELECTION_CHIP_LIMIT = 12;

/** The wire action behind the command bar's stop button. */
export const STOP_ACTION_LABEL = "停止";

/** The wire action behind the command bar's attack-move button. */
export const ATTACK_MOVE_ACTION_LABEL = "攻击移动";

/** The one hint line shown when nothing is selected. */
export const EMPTY_SELECTION_HINT = "未选中任何单位 · 左键点选、拖动框选，或按 O 选中工兵";

/** How a selection is classified for the context-sensitive command bar. */
export type CommandSelectionKind =
  | "none"
  | "units"
  | "engineer"
  | "building"
  | "mixed"
  | "other";

/** One wire action family the bar or the context panel can offer. */
export type CommandActionKind = "stop" | "attack-move" | "build" | "train";

/**
 * Where an action is rendered.
 *
 * `bar` is the bottom-centre command bar (stop and attack-move); `context` is its bottom-right
 * context zone (a building's TRAIN entries, an engineer's BUILD entries). Keeping the zone in the
 * model rather than in the DOM means the self-check can assert that TRAIN and BUILD never leak into
 * a unit-only command row.
 */
export type CommandActionZone = "bar" | "context";

/** One action the page may offer for the current selection. */
export interface CommandAction {
  readonly kind: CommandActionKind;
  readonly zone: CommandActionZone;
  /** Display label; the TRAIN/BUILD entries carry the catalogued path as their label here. */
  readonly label: string;
  /** Content path of a `train` / `build` entry; absent for stop and attack-move. */
  readonly path?: string;
}

/** The complete command-bar state for one selection. */
export interface CommandBarModel {
  /** The classification the page uses to show or hide the action row, chips and context sections. */
  readonly kind: CommandSelectionKind;
  /** True only for a selection that mixes at least one own unit with at least one own building. */
  readonly mixed: boolean;
  /** Every action the selection may offer, each tagged with the zone that renders it. */
  readonly actions: readonly CommandAction[];
  /** The single hint line shown in the command bar. */
  readonly hint: string;
  /** Total number of selected entities, including buildings. */
  readonly selectedCount: number;
  /** Number of selected own units (the STOP / ATTACKMOVE targets). */
  readonly unitCount: number;
  /** Number of selected own buildings. */
  readonly buildingCount: number;
  /** Whether the local player id is known; false means "not logged in" for every message. */
  readonly loggedIn: boolean;
  /** One shared ownership verdict for the HUD tag, the message and the actions. */
  readonly ownership: "logged-out" | "own" | "other" | "neutral";
  /** `我` / `敌` / `中立`, or `未登录` when there is no local player id. */
  readonly ownershipTag: string;
}

/** Optional catalogues/labels so the check can use short fixtures and the page can use the manifest. */
export interface CommandBarOptions {
  /** Manifest display name for a content path; raw path when omitted. */
  readonly labelForPath?: (path: string) => string;
  readonly buildingPaths?: readonly string[];
  /**
   * Unit paths one building kind may train.
   *
   * Defaults to the classic catalogue in `commands.ts`. A test can inject a fixture, and the page keeps
   * one rule: a mine, lumber camp or warehouse trains nothing, while an armory trains the melee line.
   */
  readonly unitsForBuilding?: (kind: string) => readonly string[];
}

/** The two unit actions, in the order the command bar renders them. */
function unitActions(): CommandAction[] {
  return [
    { kind: "stop", zone: "bar", label: STOP_ACTION_LABEL },
    { kind: "attack-move", zone: "bar", label: ATTACK_MOVE_ACTION_LABEL },
  ];
}

/** One context-panel row per catalogued path, labelled for display but carrying the raw path. */
function pathActions(
  kind: "train" | "build",
  paths: readonly string[],
  labelForPath: (path: string) => string,
): CommandAction[] {
  return paths.map((path) => ({
    kind,
    zone: "context" as const,
    label: labelForPath(path),
    path,
  }));
}

/**
 * Classifies the selection and returns every action it may offer.
 *
 * Precedence, from strongest to weakest:
 * 1. nothing selected -> no actions, one hint line;
 * 2. own units plus an own building -> `mixed`: unit actions only, with a hint that says so. TRAIN is
 *    suppressed because it needs exactly one building, and BUILD because it needs exactly one
 *    engineer; silently applying only part of a mixed selection would be misleading;
 * 3. an own engineer-only group (one or several engineers) -> the unit actions plus one BUILD entry per
 *    catalogued building;
 * 4. any own units -> the unit actions;
 * 5. one or more own buildings of the **same kind** -> one TRAIN entry per unit that kind may train,
 *    in the context zone. With several selected, each TRAIN click queues one job in every one of them,
 *    so a row of armories produces in parallel (item 23). A mine, lumber camp or warehouse trains
 *    nothing and says so;
 * 6. anything else (enemy/neutral entities, several buildings of different kinds) -> no actions and an
 *    honest hint.
 *
 * Enemy and neutral entities never contribute actions; own units inside a larger selection still do,
 * because right-click / STOP / ATTACKMOVE target every own unit in the group.
 */
export function commandBarModel(
  selection: readonly EntityView[],
  myPlayerId: number | null,
  options: CommandBarOptions = {},
): CommandBarModel {
  const labelForPath = options.labelForPath ?? ((path: string): string => path);
  const unitsForBuilding = options.unitsForBuilding ?? trainableUnitsForBuilding;
  const buildingPaths = options.buildingPaths ?? BUILDING_PATHS;

  const selectedCount = selection.length;
  const loggedIn = myPlayerId !== null && Number.isFinite(myPlayerId) && myPlayerId > 0;
  const ownUnits = selection.filter((entity) => isOwnUnit(entity, myPlayerId));
  const ownBuildings = selection.filter(
    (entity) =>
      ownershipOf(entity.playerId, myPlayerId) === "mine" && isBuildingEntityKind(entity.kind),
  );
  const unitCount = ownUnits.length;
  const buildingCount = ownBuildings.length;
  const single = selectedCount === 1 ? selection[0] : undefined;
  // For a group of own buildings the ownership verdict still has to describe the group, not "no entity";
  // otherwise a mixed-kind building selection would be reported as neutral.
  const decisionSource = single ?? (ownBuildings.length > 0 ? ownBuildings[0] : undefined);
  const decision = ownershipDecision(decisionSource?.playerId, myPlayerId);
  const base = {
    selectedCount,
    unitCount,
    buildingCount,
    loggedIn,
    ownership: decision.kind,
    ownershipTag: decision.tag,
  };
  const engineerCount = ownUnits.filter((entity) => isEngineerKind(entity.kind)).length;
  // One or several engineers selected on their own: every one of them is a builder, so the panel offers
  // the four BUILD entries. A mixed unit group (an engineer plus a soldier) deliberately does not,
  // because BUILD needs one builder and silently picking one would be misleading.
  const engineerOnlySelection =
    unitCount > 0 && selectedCount === unitCount && engineerCount === unitCount;

  if (selectedCount === 0) {
    return {
      ...base,
      kind: "none",
      mixed: false,
      actions: [],
      hint: EMPTY_SELECTION_HINT,
    };
  }

  if (!loggedIn) {
    return {
      ...base,
      kind: "other",
      mixed: false,
      actions: [],
      hint: "你尚未登录（或玩家 id 未知）：请先连接并 LOGIN，再看自己的单位能否下达命令",
    };
  }

  if (unitCount > 0 && buildingCount > 0) {
    return {
      ...base,
      kind: "mixed",
      mixed: true,
      actions: unitActions(),
      hint:
        `已选 ${unitCount} 个单位与 ${buildingCount} 个建筑：混合选择只提供单位动作` +
        "（停止 / 攻击移动）；TRAIN 与 BUILD 需要单独选中一个建筑或工兵",
    };
  }

  if (engineerOnlySelection) {
    return {
      ...base,
      kind: "engineer",
      mixed: false,
      actions: [...unitActions(), ...pathActions("build", buildingPaths, labelForPath)],
      hint:
        `已选 ${engineerCount} 个工兵：停止 / 攻击移动；右下角 ${buildingPaths.length} 个 BUILD 条目，` +
        "点条目后左键放置",
    };
  }

  if (unitCount > 0) {
    return {
      ...base,
      kind: "units",
      mixed: false,
      actions: unitActions(),
      hint: `已选 ${unitCount} 个单位：停止 / 攻击移动；右键直接移动或攻击`,
    };
  }

  if (buildingCount > 0 && buildingCount === selectedCount) {
    const first = ownBuildings[0];
    const oneKind = first !== undefined
      && ownBuildings.every((building) => kindName(building.kind) === kindName(first.kind));
    if (oneKind && first !== undefined) {
      const trainable = unitsForBuilding(first.kind);
      return {
        ...base,
        kind: "building",
        mixed: false,
        actions: pathActions("train", trainable, labelForPath),
        hint:
          trainable.length === 0
            ? "选中建筑：这个建筑不训练单位（矿井 / 伐木场 / 仓库只产出资源）"
            : buildingCount === 1
              ? "选中建筑：右下角是 TRAIN 条目与生产队列，一次只排一个单位"
              : `已选 ${buildingCount} 座同类型建筑：点一次 TRAIN 会向每座各排一个，它们并行生产`,
      };
    }
  }

  if (decision.kind === "neutral") {
    return {
      ...base,
      kind: "other",
      mixed: false,
      actions: [],
      hint: "选中的实体没有归属或属于中立方，也没有可下达的单位动作",
    };
  }
  if (decision.kind === "own") {
    return {
      ...base,
      kind: "other",
      mixed: false,
      actions: [],
      hint: "已选多座不同类型的建筑：一次只能向同类型的一组建筑 TRAIN；请只选同一种建筑，或单独选一座",
    };
  }
  return {
    ...base,
    kind: "other",
    mixed: false,
    actions: [],
    hint: "选中的实体不属于你，也没有可下达的单位动作",
  };
}

/**
 * The two-character chip abbreviation of a manifest display name.
 *
 * The rule is the first two Unicode code points of the trimmed name, so a one-character name stays one
 * character, a leading digit is kept (`3级兵营` -> `3级`), and an astral character is never cut in
 * half. An empty name (a manifest entry without a name and no path fallback) becomes `?`, so a chip is
 * always visible.
 */
export function chipLabel(name: string): string {
  const trimmed = name.trim();
  if (trimmed === "") {
    return "?";
  }
  return Array.from(trimmed).slice(0, 2).join("");
}

/**
 * The height ratio of one chip's thin health bar.
 *
 * A snapshot may omit `healthRatio` (the demo world, an older server); that means "not reported", and
 * the chip shows a full bar rather than an empty one that would read as a dead unit. A reported ratio
 * is clamped by the same helper the HUD and the canvas health bar use, so 0, 0.5 and 1 map to 0%,
 * 50% and 100% and malformed values cannot escape `[0, 1]`.
 */
export function healthBarRatio(entity: EntityView): number {
  const ratio = entity.healthRatio;
  if (ratio === undefined || !Number.isFinite(ratio)) {
    return 1;
  }
  return progressBarRatio(ratio);
}

/** One chip of the selected-units strip. */
export interface SelectionChip {
  readonly entityId: number;
  /** Raw content path; kept for the tooltip and for a later re-label without a lookup. */
  readonly kind: string;
  /** Full manifest display name (or the raw kind when the manifest has no name). */
  readonly name: string;
  /** The two-character abbreviation shown in the chip. */
  readonly label: string;
  /** Clamped health bar ratio; a missing report becomes 1. */
  readonly healthRatio: number;
  readonly ownership: Ownership;
  /** Health bar colour from the shared ownership rule (mine blue / enemy red / neutral grey). */
  readonly color: string;
}

/** The chip strip plus its overflow marker. */
export interface SelectionChipList {
  readonly chips: readonly SelectionChip[];
  /** Number of selected units in total, including the ones collapsed into `+N`. */
  readonly total: number;
  /** Number of units not shown as individual chips. */
  readonly overflow: number;
  /** `+N` when something is collapsed, otherwise the empty string. */
  readonly overflowLabel: string;
}

/**
 * The selected-units strip: one chip per selected unit, capped at `limit`, then a `+N` marker.
 *
 * Buildings are excluded: the strip is a *units* strip and a selected building is already described by
 * the HUD chip, the context panel and the canvas selection ring. Every selected unit is included -
 * own, enemy or neutral - because the strip mirrors what is selected; the health bar's ownership colour
 * is what tells the operator whose unit it is. A non-finite or negative limit is treated as the
 * default, and a limit of zero shows no chips with the whole selection collapsed into `+total`.
 */
export function selectionChipList(
  selection: readonly EntityView[],
  myPlayerId: number | null,
  labelForKind: (kind: string) => string = (kind: string): string => kind,
  limit: number = SELECTION_CHIP_LIMIT,
): SelectionChipList {
  const units = selection.filter((entity) => !isBuildingEntityKind(entity.kind));
  const maximum = Number.isFinite(limit)
    ? Math.max(0, Math.trunc(limit))
    : SELECTION_CHIP_LIMIT;
  const shown = units.slice(0, maximum);
  const chips = shown.map((entity): SelectionChip => {
    const name = labelForKind(entity.kind);
    return {
      entityId: entity.id,
      kind: entity.kind,
      name,
      label: chipLabel(name),
      healthRatio: healthBarRatio(entity),
      ownership: ownershipOf(entity.playerId, myPlayerId),
      color: ownershipColor(entity.playerId, myPlayerId),
    };
  });
  const overflow = Math.max(0, units.length - chips.length);
  return {
    chips,
    total: units.length,
    overflow,
    overflowLabel: overflow === 0 ? "" : `+${overflow}`,
  };
}
