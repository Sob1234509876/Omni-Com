/**
 * Pure builders for the OC/1 gameplay grammar and the small content catalogue the panel offers.
 *
 * The grammar is documented in `protocol/oc-protocol.md` and implemented by the server's command
 * dispatcher. Every builder returns exactly one line. Arguments are validated here, before a line can
 * reach the socket, so a UI bug can never send a typo such as `ORDRE MOVE` or `ORDER MOVE nope 2 3`.
 */

/**
 * Building content paths the classic plugin currently ships.
 *
 * The server owns the truth: this is only the catalogue the browser panel offers. A path the served
 * world does not know is answered with `ERR unknown building`, so keep this list in step with the
 * classic content when it grows. The warehouse is the content's drop-off building and is buildable by
 * the engineer; its cost (100 gold + 40 wood) is charged by the engine, not by this panel.
 */
export const BUILDING_PATHS = [
  "building.armory.t1",
  "building.mine.t1",
  "building.lumbercamp.t1",
  "building.warehouse",
] as const;

/**
 * Unit content paths the classic plugin currently ships, in training-menu order.
 *
 * The server owns the truth here too. The tier four and tier five recipes need an upgraded armory; the
 * panel still offers them and lets the server answer `ERR ...` when the selected armory is too low.
 */
export const UNIT_PATHS = [
  "unit.melee.t1",
  "unit.melee.t2",
  "unit.melee.t3",
  "unit.melee.t4",
  "unit.melee.t5",
  "unit.engineer",
] as const;

/**
 * Every building kind the classic catalogue ships, in upgrade-ladder order.
 *
 * <p>`BUILDING_PATHS` is what the engineer can place; this wider list also contains the upgraded tiers
 * that only appear through `UPGRADE`. The client uses it to give every kind its own content entry and to
 * prove that no two kinds share an atlas cell.
 */
export const CLASSIC_BUILDING_KINDS = [
  "building.armory.t1",
  "building.armory.t2",
  "building.mine.t1",
  "building.mine.t2",
  "building.mine.t3",
  "building.lumbercamp.t1",
  "building.lumbercamp.t2",
  "building.lumbercamp.t3",
  "building.warehouse",
] as const;

/** One building kind's client-side content: what it is called and what it may train. */
export interface BuildingContent {
  /** Content path, exactly as the server sends it. */
  readonly path: string;
  /**
   * Unit paths this kind's TRAIN panel offers.
   *
   * Only a producer has entries. A mine, a lumber camp and a warehouse have none: showing the armory's
   * unit list on them was the reported bug, because the panel used one global list for every building.
   */
  readonly trainableUnits: readonly string[];
}

/**
 * The trainable units of one classic building kind.
 *
 * <p>The armory is the only producer in this catalogue. Both tiers offer the full melee ladder and the
 * engineer; the engine's level gate refuses the two top tiers from a t1 armory, which is deliberate (the
 * panel lets the server give the readable refusal). Every other building answers an empty list, so a
 * selected mine, lumber camp or warehouse never paints a TRAIN section it cannot honour.
 */
const NO_UNITS: readonly string[] = [];

export const BUILDING_CONTENT: ReadonlyMap<string, BuildingContent> = new Map<
  string,
  BuildingContent
>([
  ["building.armory.t1", { path: "building.armory.t1", trainableUnits: UNIT_PATHS }],
  ["building.armory.t2", { path: "building.armory.t2", trainableUnits: UNIT_PATHS }],
  ["building.mine.t1", { path: "building.mine.t1", trainableUnits: NO_UNITS }],
  ["building.mine.t2", { path: "building.mine.t2", trainableUnits: NO_UNITS }],
  ["building.mine.t3", { path: "building.mine.t3", trainableUnits: NO_UNITS }],
  ["building.lumbercamp.t1", { path: "building.lumbercamp.t1", trainableUnits: NO_UNITS }],
  ["building.lumbercamp.t2", { path: "building.lumbercamp.t2", trainableUnits: NO_UNITS }],
  ["building.lumbercamp.t3", { path: "building.lumbercamp.t3", trainableUnits: NO_UNITS }],
  ["building.warehouse", { path: "building.warehouse", trainableUnits: NO_UNITS }],
]);

/** Strips a namespace such as `oc:` from a content path; the atlas resolves names the same way. */
function bareContentPath(path: string): string {
  const separator = path.lastIndexOf(":");
  return separator >= 0 ? path.slice(separator + 1) : path;
}

/** The content entry of one building kind, or `undefined` for a kind this catalogue does not know. */
export function buildingContent(kind: string): BuildingContent | undefined {
  return BUILDING_CONTENT.get(kind) ?? BUILDING_CONTENT.get(bareContentPath(kind));
}

/**
 * Unit paths one building kind may offer, or an empty list for a kind that is not a producer.
 *
 * <p>An unknown building answers no entries rather than the whole unit catalogue: a TRAIN button that
 * the server is guaranteed to refuse is worse than no button, and a plugin building with its own recipes
 * is not something this classic catalogue can describe.
 */
export function trainableUnitsForBuilding(kind: string): readonly string[] {
  return buildingContent(kind)?.trainableUnits ?? NO_UNITS;
}

/** Rejects anything that is not a positive, safely representable integer id. */
function requirePositiveInteger(value: number, label: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`${label}必须是正整数，当前是 ${String(value)}`);
  }
  return value;
}

/**
 * Rejects NaN and the infinities. The builders keep a finite decimal intact; the current server parses
 * whole tile indices, and the canvas only ever passes the integer tile it was clicked on.
 */
function requireFiniteNumber(value: number, label: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new Error(`${label}必须是有限数字，当前是 ${String(value)}`);
  }
  return value;
}

/** Rejects a content path that would split into two command fields or add a second line. */
function requireContentPath(value: string, label: string): string {
  if (typeof value !== "string" || value.trim() === "" || /\s/u.test(value)) {
    throw new Error(`${label}必须是非空且不含空白的内容路径，当前是 '${String(value)}'`);
  }
  return value;
}

/** `WHOAMI` -> `OK player <id>`; requires a successful LOGIN. */
export function whoAmI(): string {
  return "WHOAMI";
}

/** `ORDER MOVE <entityId> <x> <y>`; the coordinates are whole tile indices on the current server. */
export function orderMove(entityId: number, x: number, y: number): string {
  const id = requirePositiveInteger(entityId, "实体 id");
  const targetX = requireFiniteNumber(x, "目标瓦片 x");
  const targetY = requireFiniteNumber(y, "目标瓦片 y");
  return `ORDER MOVE ${id} ${targetX} ${targetY}`;
}

/** `ORDER ATTACK <entityId> <targetId>`. */
export function orderAttack(entityId: number, targetId: number): string {
  const id = requirePositiveInteger(entityId, "实体 id");
  const target = requirePositiveInteger(targetId, "目标 id");
  return `ORDER ATTACK ${id} ${target}`;
}

/** `ORDER ATTACKMOVE <entityId> <x> <y>`. */
export function orderAttackMove(entityId: number, x: number, y: number): string {
  const id = requirePositiveInteger(entityId, "实体 id");
  const targetX = requireFiniteNumber(x, "目标瓦片 x");
  const targetY = requireFiniteNumber(y, "目标瓦片 y");
  return `ORDER ATTACKMOVE ${id} ${targetX} ${targetY}`;
}

/** `ORDER STOP <entityId>`. */
export function orderStop(entityId: number): string {
  const id = requirePositiveInteger(entityId, "实体 id");
  return `ORDER STOP ${id}`;
}

/** `BUILD <engineerId> <buildingPath> <x> <y>`; the answer carries the new building id on success. */
export function build(engineerId: number, buildingPath: string, x: number, y: number): string {
  const id = requirePositiveInteger(engineerId, "工程师 id");
  const path = requireContentPath(buildingPath, "建筑路径");
  const tileX = requireFiniteNumber(x, "瓦片 x");
  const tileY = requireFiniteNumber(y, "瓦片 y");
  return `BUILD ${id} ${path} ${tileX} ${tileY}`;
}

/** `TRAIN <buildingId> <unitPath>`. */
export function train(buildingId: number, unitPath: string): string {
  const id = requirePositiveInteger(buildingId, "建筑 id");
  const path = requireContentPath(unitPath, "单位路径");
  return `TRAIN ${id} ${path}`;
}

/**
 * One `TRAIN` line per selected same-type building, for the item 23 batch command.
 *
 * <p>The server's grammar has one building per line, so "select many armories and train at once" is a
 * client-side batch: every building keeps its own queue and the production system ticks them in
 * parallel. The list is kept in selection order rather than sorted, so the log reads like the click the
 * owner made; a duplicate id is rejected by the caller's selection model (an entity is selected once),
 * but this builder does not silently de-duplicate. An empty list is a programming error and throws,
 * which stops the caller from sending a no-op batch.
 */
export function trainBatch(
  buildingIds: readonly number[],
  unitPath: string,
): string[] {
  if (buildingIds.length === 0) {
    throw new Error("批量 TRAIN 至少需要一座建筑");
  }
  return buildingIds.map((buildingId) => train(buildingId, unitPath));
}

/**
 * `UPGRADE <buildingId>`; the answer is `OK <newBuildingId>` or `ERR <reason>`.
 *
 * The replacement has a new entity id, so the caller must not keep using the old one after a successful
 * answer: the next snapshot contains only the replacement.
 */
export function upgrade(buildingId: number): string {
  const id = requirePositiveInteger(buildingId, "建筑 id");
  return `UPGRADE ${id}`;
}

/** The exact `WHOAMI` success answer; `playerId` is the engine player id of this connection. */
const WHOAMI_ANSWER = /^OK player ([1-9][0-9]*)$/u;

/**
 * Parses `OK player <id>` and returns the id, or `null` for every other answer.
 *
 * Only a canonical positive decimal id is accepted: `OK player 0`, `OK player 7.0`, a reversed word
 * order, an `ERR ...` line and trailing junk all return `null`, so the caller never stores a bogus
 * identity that would make the ownership check wrong.
 */
export function parseWhoAmI(answer: string): number | null {
  if (typeof answer !== "string") {
    return null;
  }
  const match = WHOAMI_ANSWER.exec(answer.trim());
  const rawId = match?.[1];
  if (rawId === undefined) {
    return null;
  }
  const id = Number(rawId);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
