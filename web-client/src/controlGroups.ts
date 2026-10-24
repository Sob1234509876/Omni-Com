/**
 * Control groups 1–9: the pure binding model behind Ctrl+number, number and Shift+number.
 *
 * The page keeps one immutable `ControlGroups` value. Every helper returns a new value (or the same one
 * when nothing changed), so the caller can never accidentally mutate a group while a frame is drawing.
 * A group is a list of entity ids in selection order; it is deliberately not tied to `EntityView`, so the
 * same model works for the demo world and for a server snapshot.
 *
 * The keyboard mapping is in `src/render/input.ts`; the status strings and the HUD group number live in
 * `src/main.ts`. This module stays DOM-free so `scripts/check-render-math.ts` can exercise it under Node.
 */

/** Highest group number; the UI binds exactly 1 through 9. */
export const CONTROL_GROUP_MAX = 9;

/** Immutable group number to entity ids, in selection order. */
export type ControlGroups = ReadonlyMap<number, readonly number[]>;

/** No bound groups; the initial value of the page. */
export const EMPTY_CONTROL_GROUPS: ControlGroups = new Map<number, readonly number[]>();

/** Whether a value is one of the nine bindable group numbers. */
export function isControlGroup(value: number): boolean {
  return Number.isInteger(value) && value >= 1 && value <= CONTROL_GROUP_MAX;
}

/** Rejects a group outside 1..9 with a message naming the valid range. */
function requireControlGroup(group: number): number {
  if (!isControlGroup(group)) {
    throw new RangeError(`编队编号必须是 1..${CONTROL_GROUP_MAX} 的整数，当前是 ${String(group)}`);
  }
  return group;
}

/** Keeps positive safe integer ids, de-duplicated, in their original order. */
function normalizeIds(ids: readonly number[]): number[] {
  const seen = new Set<number>();
  const normalized: number[] = [];
  for (const id of ids) {
    if (!Number.isSafeInteger(id) || id <= 0 || seen.has(id)) {
      continue;
    }
    seen.add(id);
    normalized.push(id);
  }
  return normalized;
}

/** Merges new ids into an existing group, keeping every id once and the original order first. */
function mergeIds(existing: readonly number[], added: readonly number[]): number[] {
  const seen = new Set<number>(existing);
  const merged = [...existing];
  for (const id of added) {
    if (seen.has(id)) {
      continue;
    }
    seen.add(id);
    merged.push(id);
  }
  return merged;
}

/**
 * Binds the current selection to a group, replacing whatever that group held.
 *
 * An empty selection binds an empty group; `describe` then reports it as empty on a later recall. Ids are
 * normalized (positive safe integers, no duplicates), so malformed input cannot enter the model.
 *
 * @throws RangeError when `group` is not an integer in 1..9
 */
export function assign(
  groups: ControlGroups,
  group: number,
  ids: readonly number[],
): ControlGroups {
  const key = requireControlGroup(group);
  const next = new Map(groups);
  next.set(key, normalizeIds(ids));
  return next;
}

/**
 * Adds the current selection to a group, keeping ids that were already there.
 *
 * @throws RangeError when `group` is not an integer in 1..9
 */
export function add(
  groups: ControlGroups,
  group: number,
  ids: readonly number[],
): ControlGroups {
  const key = requireControlGroup(group);
  const next = new Map(groups);
  next.set(key, mergeIds(next.get(key) ?? [], normalizeIds(ids)));
  return next;
}

/**
 * The ids of one group, copied so a caller cannot mutate the stored array.
 *
 * An unbound group and an empty group both answer `[]`; the caller distinguishes them only through the
 * status line, where both are "empty" as far as the operator is concerned.
 *
 * @throws RangeError when `group` is not an integer in 1..9
 */
export function recall(groups: ControlGroups, group: number): readonly number[] {
  return [...(groups.get(requireControlGroup(group)) ?? [])];
}

/**
 * Drops every id that no longer exists in the world, preserving group numbers and order.
 *
 * Empty groups stay in the map: a group that was assigned and then lost all its units is still a known
 * group number, and a later recall reports it as empty instead of pretending it was never bound. The
 * original map is returned when nothing changed, which keeps the per-snapshot call cheap.
 */
export function prune(groups: ControlGroups, livingIds: Iterable<number>): ControlGroups {
  const living = new Set<number>();
  for (const id of livingIds) {
    if (Number.isSafeInteger(id) && id > 0) {
      living.add(id);
    }
  }
  let changed = false;
  const next = new Map<number, readonly number[]>();
  for (const [group, ids] of groups) {
    const kept = ids.filter((id) => living.has(id));
    if (kept.length !== ids.length) {
      changed = true;
    }
    next.set(group, kept);
  }
  return changed ? next : groups;
}

/**
 * The status-line text for one group: `编队 3：5 个实体` or `编队 3 为空`.
 *
 * A recall of an empty group uses this instead of silently clearing the selection, and an assignment
 * uses it as the confirmation. The count says "实体" because a single selected building can be bound too;
 * the wire commands still decide what may be ordered.
 *
 * @throws RangeError when `group` is not an integer in 1..9
 */
export function describe(groups: ControlGroups, group: number): string {
  const key = requireControlGroup(group);
  const ids = groups.get(key) ?? [];
  return ids.length === 0 ? `编队 ${key} 为空` : `编队 ${key}：${ids.length} 个实体`;
}
