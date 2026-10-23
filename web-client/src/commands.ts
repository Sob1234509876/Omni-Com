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
 * classic content when it grows (for example the tier-one lumber camp).
 */
export const BUILDING_PATHS = [
  "building.armory.t1",
  "building.mine.t1",
  "building.lumbercamp.t1",
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
