/**
 * Pure entity picking: which entity does a screen click actually hit?
 *
 * <p>The renderer draws a unit as a body standing *above* its ground point and a building as a sprite
 * covering its whole footprint. A circular pick around the ground point therefore missed most of a
 * unit's visible body and almost all of a three-by-three building, while a click on an empty-looking
 * neighbour could still select something. This module gives the pointer the same geometry the picture
 * uses:
 *
 * - a unit is picked from a small body capsule above its feet, so its head and torso count;
 * - a building is picked from its footprint rectangle, derived from the centre coordinate and the
 *   snapshot's `w` / `h`;
 * - units outrank buildings when both overlap, and among equals the lower (larger `y`) entity wins,
 *   matching the renderer's y-sorted draw order.
 *
 * `scripts/check-render-math.ts` exercises it under Node; `input.ts` is only the DOM adapter.
 */

import type { Camera } from "./camera.ts";
import { tileToWorld } from "./camera.ts";
import {
  entityFootprintHeight,
  entityFootprintOrigin,
  entityFootprintWidth,
  entityInWindow,
  isBuildingEntityKind,
  type EntityView,
  type WorldView,
} from "./view.ts";

/** Half the width of a unit's clickable body, as a fraction of a tile. */
export const UNIT_PICK_HALF_WIDTH_TILES = 0.46;

/** How far above the feet a unit body starts, as a fraction of a tile. */
export const UNIT_PICK_BODY_HEIGHT_TILES = 1.05;

/** Minimum click radius in CSS pixels, so a unit stays clickable when zoomed far out. */
export const UNIT_PICK_MIN_RADIUS_PX = 7;

interface Candidate {
  readonly entity: EntityView;
  /** 0 = unit, 1 = building; the smaller group wins before distance is considered. */
  readonly group: number;
  /** Euclidean distance from the pointer to the body/footprint centre in CSS pixels. */
  readonly distance: number;
}

function unitCandidate(
  entity: EntityView,
  camera: Camera,
  screenX: number,
  screenY: number,
): Candidate | null {
  const tilePixels = camera.effectiveTileSize;
  const foot = camera.worldToScreen(entity.x * camera.tileSize, entity.y * camera.tileSize);
  const halfWidth = Math.max(UNIT_PICK_MIN_RADIUS_PX, tilePixels * UNIT_PICK_HALF_WIDTH_TILES);
  const bodyTop = foot.y - tilePixels * UNIT_PICK_BODY_HEIGHT_TILES;
  const bodyBottom = foot.y + tilePixels * 0.12;
  if (screenX < foot.x - halfWidth || screenX > foot.x + halfWidth) {
    return null;
  }
  if (screenY < bodyTop || screenY > bodyBottom) {
    return null;
  }
  // The body centre sits about a third of a tile above the feet; use it as the tie-breaking distance.
  const bodyY = foot.y - tilePixels * 0.35;
  return { entity, group: 0, distance: Math.hypot(foot.x - screenX, bodyY - screenY) };
}

function buildingCandidate(
  entity: EntityView,
  camera: Camera,
  screenX: number,
  screenY: number,
): Candidate | null {
  const origin = entityFootprintOrigin(entity);
  const tilePixels = camera.effectiveTileSize;
  const topLeft = camera.worldToScreen(
    tileToWorld(origin, camera.tileSize).x,
    tileToWorld(origin, camera.tileSize).y,
  );
  const width = entityFootprintWidth(entity) * tilePixels;
  const height = entityFootprintHeight(entity) * tilePixels;
  if (
    screenX < topLeft.x ||
    screenY < topLeft.y ||
    screenX > topLeft.x + width ||
    screenY > topLeft.y + height
  ) {
    return null;
  }
  const centreX = topLeft.x + width / 2;
  const centreY = topLeft.y + height / 2;
  return { entity, group: 1, distance: Math.hypot(centreX - screenX, centreY - screenY) };
}

/**
 * The entity under a screen point, or `null` when the click is on empty ground.
 *
 * The result depends only on the world, the camera and the screen point, which is what keeps the hover
 * tooltip, the selection click and the right-click order aimed at the same entity.
 */
export function pickEntityAt(
  world: WorldView,
  camera: Camera,
  screenX: number,
  screenY: number,
): number | null {
  let best: Candidate | null = null;
  for (const entity of world.entities) {
    // An entity outside the loaded window is not drawn, so it must not be pickable either.
    if (!entityInWindow(world, entity)) {
      continue;
    }
    const building = isBuildingEntityKind(entity.kind);
    const candidate = building
      ? buildingCandidate(entity, camera, screenX, screenY)
      : unitCandidate(entity, camera, screenX, screenY);
    if (candidate === null) {
      continue;
    }
    if (
      best === null ||
      candidate.group < best.group ||
      (candidate.group === best.group &&
        (candidate.entity.y > best.entity.y ||
          (candidate.entity.y === best.entity.y && candidate.distance < best.distance)))
    ) {
      best = candidate;
    }
  }
  return best === null ? null : best.entity.id;
}
