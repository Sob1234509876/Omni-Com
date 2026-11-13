/**
 * Pure builders for the OC/1 gameplay grammar.
 *
 * The grammar is documented in `protocol/oc-protocol.md` and implemented by the server's command
 * dispatcher. Every builder returns exactly one line. Arguments are validated here, before a line can
 * reach the socket, so a UI bug can never send a typo such as `ORDRE MOVE` or `ORDER MOVE nope 2 3`.
 *
 * <p>This module carries **no game content**: the BUILD and TRAIN menus are the snapshot's
 * top-level `buildable` array and the building row's `trains` array; display names come from the
 * plugin manifest; upgrade prices come from the row's `upgrade.options`. The presentation-layer
 * fallbacks for an older server live in `render/commandbar.ts`, and the classic artwork/coverage
 * fixture list the offline check walks lives in the check script itself.
 */

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
export function orderMove(entityId: number, x: number, y: number, queue = false): string {
  return orderMoveRoute(entityId, [{ x, y }], queue);
}

/**
 * Maximum number of points one `ORDER MOVE` line may carry, including the final destination.
 *
 * <p>The server refuses `ORDER MOVE` with more than eight coordinate pairs, and a client that built a
 * longer line would only learn that from an `ERR bad arguments`. The builder therefore refuses locally;
 * the same cap gates the Shift+right-click pending route.
 */
export const MAX_MOVE_POINTS = 8;

/** One destination of a move route: an absolute world tile. */
export interface MovePoint {
  readonly x: number;
  readonly y: number;
}

/**
 * `ORDER MOVE <entityId> <x> <y> [<x> <y> ...]` for a route of one to {@link MAX_MOVE_POINTS} points.
 *
 * The one-point form is byte-identical to the historical `orderMove(entityId, x, y)` line, so a client
 * that only ever moves once is unaffected by waypoints. An empty route, a route longer than the cap and
 * a non-finite coordinate all throw before a line can reach the socket.
 */
export function orderMoveRoute(
  entityId: number,
  points: readonly MovePoint[],
  queue = false,
): string {
  const id = requirePositiveInteger(entityId, "实体 id");
  if (!Array.isArray(points) || points.length === 0) {
    throw new Error("移动路线至少需要一个目标点");
  }
  if (points.length > MAX_MOVE_POINTS) {
    throw new Error(`移动路线最多 ${MAX_MOVE_POINTS} 个目标点，当前是 ${points.length} 个`);
  }
  const fields: string[] = [];
  for (const point of points) {
    const x = requireFiniteNumber(point.x, "目标瓦片 x");
    const y = requireFiniteNumber(point.y, "目标瓦片 y");
    fields.push(String(x), String(y));
  }
  return `ORDER MOVE ${id} ${fields.join(" ")}${queue ? " queue" : ""}`;
}

/**
 * `REPAIR <engineerId> <buildingId> [queue]`; the engineer repairs a damaged building of the same player.
 *
 * <p>`queue` is the sequence form used by the Q16 merge mode: the repair is pushed behind the engineer's
 * current orders instead of replacing them.
 */
export function repair(engineerId: number, buildingId: number, queue = false): string {
  const engineer = requirePositiveInteger(engineerId, "工程师 id");
  const building = requirePositiveInteger(buildingId, "建筑 id");
  return `REPAIR ${engineer} ${building}${queue ? " queue" : ""}`;
}

/** `RECLAIM <buildingId>` on an own unfinished site; the answer carries the half-cost refund. */
export function reclaim(buildingId: number): string {
  const id = requirePositiveInteger(buildingId, "建筑 id");
  return `RECLAIM ${id}`;
}

/**
 * `ORDER ATTACK <entityId> <targetId> [queue]`.
 *
 * <p>`queue` is the sequence form used by the Q16 merge mode: the attack is pushed behind the unit's
 * current orders instead of replacing them.
 */
export function orderAttack(entityId: number, targetId: number, queue = false): string {
  const id = requirePositiveInteger(entityId, "实体 id");
  const target = requirePositiveInteger(targetId, "目标 id");
  return `ORDER ATTACK ${id} ${target}${queue ? " queue" : ""}`;
}

/**
 * `ORDER ATTACKMOVE <entityId> <x> <y> [queue]`.
 *
 * <p>`queue` is the sequence form used by the Q16 merge mode: the attack-move is pushed behind the
 * unit's current orders instead of replacing them.
 */
export function orderAttackMove(entityId: number, x: number, y: number, queue = false): string {
  const id = requirePositiveInteger(entityId, "实体 id");
  const targetX = requireFiniteNumber(x, "目标瓦片 x");
  const targetY = requireFiniteNumber(y, "目标瓦片 y");
  return `ORDER ATTACKMOVE ${id} ${targetX} ${targetY}${queue ? " queue" : ""}`;
}

/** `ORDER STOP <entityId>`. */
export function orderStop(entityId: number): string {
  const id = requirePositiveInteger(entityId, "实体 id");
  return `ORDER STOP ${id}`;
}

/**
 * `BUILD <engineerId> <buildingPath> <x> <y> [queue]`; the answer carries the new building id.
 *
 * <p>The optional literal `queue` is the Shift-click form: the server appends the build order to the
 * engineer's queue instead of replacing what it was doing. The four-token form is byte-identical to the
 * historical command, so a plain click cannot accidentally queue.
 */
export function build(
  engineerId: number,
  buildingPath: string,
  x: number,
  y: number,
  queue = false,
): string {
  const id = requirePositiveInteger(engineerId, "工程师 id");
  const path = requireContentPath(buildingPath, "建筑路径");
  const tileX = requireFiniteNumber(x, "瓦片 x");
  const tileY = requireFiniteNumber(y, "瓦片 y");
  return `BUILD ${id} ${path} ${tileX} ${tileY}${queue ? " queue" : ""}`;
}

/**
 * One `BUILD` line per selected engineer, all aimed at the same footprint.
 *
 * <p>The server's grammar has one engineer per line. The first line places the site and charges it;
 * every following line names another own engineer and the same building path and origin, so the
 * engine's join path attaches that engineer to the unfinished site instead of placing a second one or
 * refusing the occupied tile. That is how "engineers build together" reaches the wire (item 26): the
 * client-side group order is a batch, not a second command kind.
 *
 * <p>An empty list is a programming error and throws, exactly like {@link trainBatch}. The ids stay in
 * selection order so the log reads like the click the owner made and the first engineer is the one who
 * places the site.
 */
export function buildBatch(
  engineerIds: readonly number[],
  buildingPath: string,
  x: number,
  y: number,
  queue = false,
): string[] {
  if (engineerIds.length === 0) {
    throw new Error("批量 BUILD 至少需要一个工兵");
  }
  return engineerIds.map((engineerId) => build(engineerId, buildingPath, x, y, queue));
}

/** The lines and the placement-mode decision of one building click. */
export interface BuildPlacementBatch {
  /** One BUILD line per engineer, in selection order. */
  readonly lines: string[];
  /** Whether the placement mode stays armed after this click: true for Shift, false for a plain click. */
  readonly keepArmed: boolean;
}

/**
 * The whole command plan of one click in BUILD placement mode.
 *
 * <p>Item 27's reading (Q14) is "queue several build tasks for the current engineer", but the selection
 * rule of item 26 sends one BUILD per selected engineer for the same footprint. The two combine without a
 * second command kind: a Shift click emits one `BUILD ... queue` line per engineer in selection order, so
 * a single-engineer selection queues one task per site, and a multi-engineer selection queues one task per
 * engineer per site. A plain click keeps the historical four-token BUILD and clears the mode.
 */
export function buildPlacementBatch(
  engineerIds: readonly number[],
  buildingPath: string,
  x: number,
  y: number,
  shift: boolean,
): BuildPlacementBatch {
  return {
    lines: buildBatch(engineerIds, buildingPath, x, y, shift),
    keepArmed: shift,
  };
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
 * `UPGRADE <buildingId> [variantPath]`; the answer is `OK <newBuildingId>` or `ERR <reason>`.
 *
 * The variant path is the `to` token of the snapshot's `upgrade.options` entry the player picked; a
 * single-option ladder may omit it, but sending it explicitly is always accepted. The replacement has a
 * new entity id, so the caller must not keep using the old one after a successful answer: the next
 * snapshot contains only the replacement.
 */
export function upgrade(buildingId: number, variantPath?: string): string {
  const id = requirePositiveInteger(buildingId, "建筑 id");
  if (variantPath === undefined) {
    return `UPGRADE ${id}`;
  }
  const path = variantPath.trim();
  if (path === "") {
    throw new TypeError("升级变体的内容路径不能为空白");
  }
  return `UPGRADE ${id} ${path}`;
}

/**
 * `UPGRADEUNIT <unitId> [tierPath]`; the answer is `OK <newUnitId>` or `ERR <reason>`.
 *
 * A unit row's `upgrade` field carries the successor (or successors) the command can create. The
 * optional tier path is the `to` token of the option the player picked; a single-option ladder may omit
 * it, but sending it explicitly is always accepted, exactly like the building `UPGRADE`. The command is
 * batchable: it may ride in a `BATCH` line like any other verb. The replacement has a new entity id, so
 * the caller must not keep using the old one after a successful answer: the next snapshot contains only
 * the replacement.
 */
export function upgradeUnit(unitId: number, tierPath?: string): string {
  const id = requirePositiveInteger(unitId, "单位 id");
  if (tierPath === undefined) {
    return `UPGRADEUNIT ${id}`;
  }
  const path = tierPath.trim();
  if (path === "") {
    throw new TypeError("单位升级的内容路径不能为空白");
  }
  return `UPGRADEUNIT ${id} ${path}`;
}

/**
 * `TRANSFER <sourceId> <targetId> <itemPath> <amount>`; the answer is `OK <moved> <item>` or `ERR ...`.
 *
 * <p>Both ids must be the local player's own finished warehouses; the item is a content path from the
 * source's `contents` and the amount is a positive whole number, or the literal `all`. The server clamps
 * the amount to the source's free stock (and the engine to the target's room), so a larger number is not
 * an error - the answer names what really moved. A local guard still refuses a zero/negative or
 * non-integer amount before a line can reach the socket, because that is a UI bug rather than a game
 * rule. `all` is supported by the protocol and the pure planner; the form offers it through its `全部`
 * shortcut.
 */
export function transfer(
  sourceId: number,
  targetId: number,
  itemPath: string,
  amount: number | "all",
): string {
  const source = requirePositiveInteger(sourceId, "来源仓库 id");
  const target = requirePositiveInteger(targetId, "目标仓库 id");
  const item = requireContentPath(itemPath, "物品路径");
  if (amount === "all") {
    return `TRANSFER ${source} ${target} ${item} all`;
  }
  if (typeof amount !== "number" || !Number.isSafeInteger(amount) || amount <= 0) {
    throw new Error(`调拨数量必须是正整数或 all，当前是 ${String(amount)}`);
  }
  return `TRANSFER ${source} ${target} ${item} ${amount}`;
}

/**
 * `HAUL <engineerId> <buildingId>`: force one engineer to carry a producer's next missing inputs.
 *
 * <p>The server finds the producer's first queued recipe that is still waiting for materials, reserves
 * exactly those missing inputs and assigns the named engineer, clearing whatever the engineer was doing.
 * The answer is `OK <amount> <item>` or the engine's readable `ERR <reason>`.
 */
export function haul(engineerId: number, buildingId: number): string {
  const engineer = requirePositiveInteger(engineerId, "工程师 id");
  const building = requirePositiveInteger(buildingId, "生产建筑 id");
  return `HAUL ${engineer} ${building}`;
}

/** Largest number of sub-commands one `BATCH` line may carry; mirrors the server's cap. */
export const MAX_BATCH_COMMANDS = 16;

/**
 * `BATCH <command>; <command>; ...`: one abstract order made of a series of commands.
 *
 * <p>Q16: the client merges a series of orders and operations into one line, and the server applies the
 * sub-commands in the order given. The cap mirrors the server's 16 so an over-long plan is refused
 * locally instead of learning it from `ERR bad arguments`. An empty list, a blank line, a line with a
 * `;` already in it or a newline all throw before a line can reach the socket; `;` is the separator and
 * no ordinary gameplay line contains one.
 */
export function batchCommands(lines: readonly string[]): string {
  if (!Array.isArray(lines) || lines.length === 0) {
    throw new Error("合并订单至少需要一条命令");
  }
  if (lines.length > MAX_BATCH_COMMANDS) {
    throw new Error(`合并订单最多 ${MAX_BATCH_COMMANDS} 条，当前是 ${lines.length} 条`);
  }
  const commands: string[] = [];
  for (const line of lines) {
    const command = typeof line === "string" ? line.trim() : "";
    if (command === "") {
      throw new Error("合并订单里不能有空命令");
    }
    if (/[;\r\n]/u.test(command)) {
      throw new Error(`合并订单的子命令不能包含分号或换行：'${command}'`);
    }
    commands.push(command);
  }
  return `BATCH ${commands.join("; ")}`;
}

/**
 * The queued form of one command, for a sequence.
 *
 * <p>Q16 sequences run one order after another, so the unit-operating commands are rewritten to their
 * `queue` forms: `ORDER MOVE`, `ORDER ATTACK`, `ORDER ATTACKMOVE`, `BUILD` and `REPAIR`. A command that
 * already ends in `queue`, and every other verb (`TRAIN`, `UPGRADE`, `UPGRADEUNIT`, `RECLAIM`,
 * `TRANSFER`), is returned unchanged. The rewrite is a pure string operation and the server validates
 * the result like any other line, so a malformed command still gets a readable `ERR` rather than being
 * "fixed" here.
 */
export function sequenceLine(line: string): string {
  const command = line.trim();
  if (command === "" || /(?:^|\s)queue$/iu.test(command)) {
    return command;
  }
  if (
    /^ORDER\s+(?:MOVE|ATTACK|ATTACKMOVE)\b/iu.test(command) ||
    /^(?:BUILD|REPAIR)\b/iu.test(command)
  ) {
    return `${command} queue`;
  }
  return command;
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
