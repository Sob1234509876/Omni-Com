/**
 * Pure rules for the marquee "sweep flash".
 *
 * A marquee drag updates its rectangle continuously, but the selection itself only changes on release.
 * The owner asked for each covered entity to flash **the moment the box first touches it**, so the drag
 * needs two pieces of pure state:
 *
 * - the candidate ids the rectangle covers this frame (own units and own buildings inside the loaded
 *   window - exactly the candidate rule `marqueeSelection` applies before its priority pass);
 * - the set of ids that have already entered during this drag, so an entity that leaves and re-enters
 *   never flashes twice.
 *
 * The release has one extra rule. The sweep only flashes ids that *entered* after the first frame;
 * entities covered by the initial zero-area box at the drag's start point were "inside the box before
 * the drag started", so they are not swept over and must still flash on release (when they become
 * newly selected). Conversely an id the sweep already flashed must not flash again. {@link
 * releaseFlashIds} expresses both halves of that rule.
 *
 * Nothing here touches the DOM, so `scripts/check-render-math.ts` exercises the same code the browser
 * runs.
 */

import type { TileRange } from "./camera.ts";
import { marqueeCandidates, type WorldView } from "./view.ts";

/**
 * The ids the marquee rectangle currently covers as candidates.
 *
 * This is the same candidate rule the release uses: own units **and** own buildings, inside the loaded
 * window, geometrically inside the inclusive tile range. The combat/builder/building priority is a
 * separate selection rule and deliberately does not apply here: the flash marks what the box touches,
 * not only what it will finally select.
 */
export function marqueeCoveredIds(
  world: WorldView,
  range: TileRange,
  myPlayerId: number | null,
): number[] {
  return marqueeCandidates(world, range, myPlayerId).map((entity) => entity.id);
}

/**
 * One drag's sweep state: the covered set of the previous frame and the ids that have entered since
 * the drag started.
 *
 * The constructor captures the **baseline**: the candidates covered by the zero-area box at the drag's
 * start point. Those ids are considered "already inside" and therefore do not count as entered, which
 * is what makes the release rule possible.
 */
export class MarqueeSweep {
  private covered: ReadonlySet<number>;
  private readonly entered = new Set<number>();

  constructor(baselineIds: Iterable<number> = []) {
    this.covered = new Set(baselineIds);
  }

  /**
   * Feeds one frame's covered ids and returns the ids that first entered the box in this frame.
   *
   * An id already present on the previous frame has not entered. An id that left earlier and came back
   * is still in {@link flashedIds}, so it is not returned again: one flash per entity per drag.
   */
  frame(coveredIds: Iterable<number>): number[] {
    const current = new Set(coveredIds);
    const enteredNow: number[] = [];
    for (const id of current) {
      if (!this.covered.has(id) && !this.entered.has(id)) {
        this.entered.add(id);
        enteredNow.push(id);
      }
    }
    this.covered = current;
    return enteredNow;
  }

  /** The covered ids of the most recently fed frame. */
  get coveredIds(): ReadonlySet<number> {
    return this.covered;
  }

  /** Every id that has entered during this drag, in first-entry order (the ids already flashed). */
  get flashedIds(): ReadonlySet<number> {
    return this.entered;
  }
}

/**
 * The ids that still need a release flash.
 *
 * A release flashes an id when it is **newly selected by this drag** (`selected`, not `previousSelected`)
 * **and the sweep has not flashed it already**. The second half is what prevents a second flash for an
 * entity the box swept over. The first half still lets an entity that was already inside the starting
 * box - but never "entered" - flash once on release, and it keeps the standing rule that only newly
 * added entities flash on a player action.
 */
export function releaseFlashIds(
  selectedIds: Iterable<number>,
  previousSelected: Iterable<number>,
  sweptIds: Iterable<number>,
): number[] {
  const previous = new Set(previousSelected);
  const swept = new Set(sweptIds);
  const ids: number[] = [];
  for (const id of selectedIds) {
    if (!previous.has(id) && !swept.has(id)) {
      ids.push(id);
    }
  }
  return ids;
}
