/**
 * Canvas 2D composition for one world frame.
 *
 * The renderer owns the backing store size: CSS pixels come from `getBoundingClientRect`, device
 * pixels from `devicePixelRatio`, and the context is scaled so every drawing call below can use CSS
 * pixels. It never assumes the two sizes are equal.
 *
 * Draw order is terrain, tile grid (fallback only), the hover tile or the build-placement ghost, ground
 * shadows, enemy unit base tints, ownership rings, entities sorted by y so lower entities overlap higher
 * ones, own-unit white outlines, their health bars and the greyscale construction bar / `生产中 ×N`
 * marker above buildings, the brief achromatic selection flash halo, one gold selection ring per
 * selected entity, the personal marker squares with their numbers, and finally the marquee rectangle.
 * Ownership rings are thinner than selection rings
 * and are drawn first, so every gold selection ring always stays on top. The enemy base tint is the one
 * extra hostile mark, and the white own-unit outline is deliberately achromatic; both live on the
 * canvas, while the surrounding chrome stays greyscale. The flash halo is drawn over the sprite but
 * under the gold ring, so the feedback never hides the selection colour. The construction chrome sits
 * above the roof, never over the sprite.
 *
 * The placement ghost is the one deliberate exception to the greyscale chrome rule: it is canvas
 * content, like the ownership rings, so each footprint tile is painted green, red or amber by its
 * state. The selection flash stays achromatic on purpose: it is canvas content, but the UI around it is
 * grey, and a white halo must not compete with the gold selection ring or the ownership colours.
 * Everything in the HTML chrome around the canvas stays grey.
 *
 * Every tile and entity is drawn from the plugin image the composite atlas resolved it to, so one frame can
 * mix art from several plugins without any of them sharing a texture page. An entity's art scale comes from
 * the winning plugin's `tilePixels`, which keeps a 16, 32 and 64 pixel plugin aligned on the same world.
 */

import {
  lookupAtlasEntity,
  lookupAtlasTile,
  type Atlas,
  type AtlasEntity,
  type AtlasSource,
  type AtlasTile,
  type EntitySprite,
  type SpriteRect,
} from "./atlas";
import type { Camera, TileCoord } from "./camera";
import type { Marker } from "./markers.ts";
import type { ScreenRect } from "./marquee";
import {
  drawBuildingProgressBar,
  drawBuildingShape,
  drawEnemyBaseTint,
  drawFlashHalo,
  drawGroundShadow,
  drawHealthBar,
  drawOwnUnitOutline,
  drawOwnershipRing,
  drawQueueMarker,
  drawSelectionRing,
  drawTerrainTile,
  drawTileGrid,
  drawUnitShape,
  playerColor,
} from "./fallback";
import { FLASH_COLOR, FLASH_MS, flashIntensity } from "./flash";
import { queueMarkerText } from "./status";
import {
  placementFillColor,
  placementStrokeColor,
  type PlacementPreview,
} from "./placement";
import {
  ENEMY_UNIT_BASE_COLOR,
  OWN_UNIT_OUTLINE_COLOR,
  entityInWindow,
  isBuildingEntityKind,
  ownerBadge,
  ownershipColor,
  ownershipOf,
  terrainAt,
  tileInWindow,
  underConstruction,
  type EntityView,
  type WorldView,
} from "./view";
import {
  PATH_COLOR,
  PATH_DASH,
  PATH_TARGET_DOT_RATIO,
  PENDING_CROSS_RATIO,
  pathPointsFor,
  pathSegments,
  pathSegmentVisible,
  pendingRoutePoints,
  type PathPoint,
} from "./paths";

const BACKGROUND_COLOR = "#0b1016";

/** The one achromatic colour of a personal marker on the canvas: chrome, not ownership. */
const MARKER_COLOR = "rgba(255, 255, 255, 0.95)";

/**
 * Draws every in-window personal marker as a small hollow square with its placement number above it.
 *
 * <p>White on a dark halo is the same achromatic chrome language the selection flash and the marquee
 * use, so a marker can never be mistaken for an ownership colour or a gold selection ring. A marker
 * outside the loaded window is skipped here deliberately: the minimap draws one white numbered arrow for
 * the nearest outside marker instead (see `markers.ts`), so the canvas can never point at terrain that is
 * not loaded.
 */
function drawMarkers(
  context: CanvasRenderingContext2D,
  world: WorldView,
  camera: Camera,
  tileSize: number,
  markers: readonly Marker[],
  width: number,
  height: number,
  devicePixelRatio: number,
): void {
  const tileOnScreen = camera.effectiveTileSize;
  const half = Math.max(4, tileOnScreen * 0.3);
  const lineWidth = Math.max(1, 1.5 / devicePixelRatio);
  const fontPixels = Math.max(9, Math.round(tileOnScreen * 0.36));
  const margin = half * 2 + fontPixels;
  context.save();
  context.lineWidth = lineWidth;
  context.strokeStyle = MARKER_COLOR;
  context.fillStyle = MARKER_COLOR;
  context.font = `${fontPixels}px ui-monospace, "Cascadia Code", Consolas, monospace`;
  context.textAlign = "center";
  context.textBaseline = "bottom";
  for (const marker of markers) {
    if (!tileInWindow(world, marker.tile.x, marker.tile.y)) {
      continue;
    }
    const centre = camera.worldToScreen(
      (marker.tile.x + 0.5) * tileSize,
      (marker.tile.y + 0.5) * tileSize,
    );
    if (
      centre.x < -margin ||
      centre.y < -margin ||
      centre.x > width + margin ||
      centre.y > height + margin
    ) {
      continue;
    }
    context.beginPath();
    context.rect(centre.x - half, centre.y - half, half * 2, half * 2);
    context.stroke();
    // The number sits above the square, stroked first with a dark halo so it reads over bright terrain.
    const label = String(marker.number);
    const labelY = centre.y - half - 2;
    context.lineWidth = Math.max(2, 2.5 / devicePixelRatio);
    context.strokeStyle = "rgba(0, 0, 0, 0.75)";
    context.strokeText(label, centre.x, labelY);
    context.fillText(label, centre.x, labelY);
    context.lineWidth = lineWidth;
    context.strokeStyle = MARKER_COLOR;
  }
  context.restore();
}

export interface RenderOptions {
  /** Monotonic animation clock in seconds, supplied by the caller. */
  readonly clockSeconds: number;
  /** Tile under the mouse, or null when the pointer is outside the canvas. */
  readonly hoverTile: TileCoord | null;
  /** Every selected entity id; each one gets the gold selection ring. */
  readonly selectedIds: readonly number[];
  /**
   * The armed build footprint under the cursor with its per-tile state, or null.
   *
   * When it is present it replaces the plain hover highlight: every footprint tile is painted with its
   * legal / blocked / out-of-reach fill and outline, which is the pre-click validity feedback.
   */
  readonly placement: PlacementPreview | null;
  /** Canvas-space marquee rectangle while a left drag is selecting, or null. */
  readonly marquee: ScreenRect | null;
  /**
   * Engine player id answered by `WHOAMI`, or null before login and in the demo world.
   *
   * With null every ownership ring is grey, which is the documented demo behaviour.
   */
  readonly myPlayerId: number | null;
  /**
   * Entity id -> `performance.now()` when that entity's selection flash started.
   *
   * Only entities a player action newly selected have an entry; the map is pruned once per frame, so
   * it stays tiny. The renderer draws a halo only for an entry that is both active and selected.
   */
  readonly flashStarts: ReadonlyMap<number, number>;
  /** Monotonic milliseconds on the same clock as the values in `flashStarts`. */
  readonly nowMs: number;
  /**
   * Whether the route overlay is enabled (`路径` toggle in the top chrome).
   *
   * It controls both the committed path of selected own units and the pending Shift+right-click route.
   */
  readonly showPaths: boolean;
  /** Pending (uncommitted) waypoints of the current selection, as absolute world tiles. */
  readonly pendingPath: readonly TileCoord[];
  /**
   * Personal markers, in placement order.
   *
   * They are client-only session state (see `markers.ts`): a small hollow square with its number per
   * marker, drawn in the same achromatic style as the rest of the chrome. A marker whose tile is outside
   * the loaded window is not drawn here; the minimap's white numbered arrow is what points at it.
   */
  readonly markers: readonly Marker[];
}

interface Size {
  readonly width: number;
  readonly height: number;
  readonly devicePixelRatio: number;
}

interface PreparedEntity {
  readonly entity: EntityView;
  readonly screenX: number;
  readonly screenY: number;
}

function lookupTile(atlas: Atlas, terrain: string): AtlasTile | undefined {
  return lookupAtlasTile(atlas, terrain);
}

function lookupEntity(atlas: Atlas, kind: string): AtlasEntity | undefined {
  return lookupAtlasEntity(atlas, kind);
}

/** Picks the current frame, honouring an optional per-entity frame-index sequence. */
function frameFor(sprite: EntitySprite, entity: EntityView, clockSeconds: number): SpriteRect | null {
  const frameCount = sprite.frames.length;
  if (frameCount === 0) {
    return null;
  }
  const phase = (entity.id % 7) * 0.13;
  const sequence = entity.frames;
  let frameIndex: number;
  if (sequence !== undefined && sequence.length > 0) {
    const position = Math.floor(clockSeconds * sprite.fps + phase) % sequence.length;
    frameIndex = sequence[position] ?? 0;
  } else {
    frameIndex = Math.floor(clockSeconds * sprite.fps + phase) % frameCount;
  }
  const wrapped = ((frameIndex % frameCount) + frameCount) % frameCount;
  return sprite.frames[wrapped] ?? null;
}

function drawEntitySprite(
  context: CanvasRenderingContext2D,
  source: AtlasSource,
  sprite: EntitySprite,
  entity: EntityView,
  screenX: number,
  screenY: number,
  zoom: number,
  clockSeconds: number,
  tileSize: number,
): void {  const frame = frameFor(sprite, entity, clockSeconds);
  if (frame === null) {
    return;
  }
  // `tilePixels` is the size the winning plugin drew its art at; normalise it to the camera's tile size so
  // an atlas authored at 16, 32 or 64 pixels per tile fills the same ground footprint.
  const artScale = tileSize / source.tilePixels;
  const scale = sprite.scale * zoom * artScale;
  const drawWidth = frame.width * scale;
  const drawHeight = frame.height * scale;
  const drawX = screenX - drawWidth * sprite.anchor[0];
  const drawY = screenY - drawHeight * sprite.anchor[1];
  context.drawImage(
    source.image,
    frame.x,
    frame.y,
    frame.width,
    frame.height,
    drawX,
    drawY,
    drawWidth,
    drawHeight,
  );
}

/**
 * Draws the route overlay: the committed path of every selected own unit and the pending waypoints.
 *
 * The committed line is drawn only for a unit the local player owns and has selected, never for an enemy,
 * even if an enemy id somehow sits in `selectedIds`. It starts at the sprite (the view's fractional
 * position) and runs through the snapshot's remaining tile centres to the target dot. `路径` turns the
 * whole overlay off. Segments whose screen bounding box is entirely outside the padded canvas are
 * skipped, which is the cheap cull that keeps a long route from issuing hundreds of off-screen draw
 * calls; Canvas itself clips anything that survives the bounding-box test.
 */
function drawPathOverlays(
  context: CanvasRenderingContext2D,
  world: WorldView,
  camera: Camera,
  tileSize: number,
  devicePixelRatio: number,
  width: number,
  height: number,
  selectedIds: ReadonlySet<number>,
  myPlayerId: number | null,
  pendingPath: readonly TileCoord[],
): void {
  const tileOnScreen = camera.effectiveTileSize;
  const margin = Math.max(24, tileOnScreen * 2);
  const bounds = {
    x: -margin,
    y: -margin,
    width: width + margin * 2,
    height: height + margin * 2,
  };
  const lineWidth = Math.max(1, 1.25 / devicePixelRatio);
  const toScreen = (point: PathPoint): PathPoint => {
    const screen = camera.worldToScreen(point.x * tileSize, point.y * tileSize);
    return { x: screen.x, y: screen.y };
  };

  context.save();
  context.strokeStyle = PATH_COLOR;
  context.fillStyle = PATH_COLOR;
  context.lineWidth = lineWidth;
  context.setLineDash([...PATH_DASH]);

  for (const entity of world.entities) {
    if (ownershipOf(entity.playerId, myPlayerId) !== "mine") {
      continue;
    }
    const selected = entity.selected === true || selectedIds.has(entity.id);
    if (!selected) {
      continue;
    }
    const points = pathPointsFor(entity);
    if (points.length < 2) {
      continue;
    }
    const screenPoints = points.map(toScreen);
    for (const segment of pathSegments(screenPoints)) {
      if (!pathSegmentVisible(segment.from, segment.to, bounds)) {
        continue;
      }
      context.beginPath();
      context.moveTo(segment.from.x, segment.from.y);
      context.lineTo(segment.to.x, segment.to.y);
      context.stroke();
    }
    // The target marker: the explicit goal when the snapshot carries one, otherwise the last point of a
    // truncated route. It is a small filled dot, not a ring, so it never competes with the selection ring.
    const targetScreen = entity.pathTarget === undefined
      ? screenPoints[screenPoints.length - 1]
      : toScreen({ x: entity.pathTarget.x + 0.5, y: entity.pathTarget.y + 0.5 });
    if (targetScreen !== undefined && pathSegmentVisible(targetScreen, targetScreen, bounds)) {
      const radius = Math.max(2, tileOnScreen * PATH_TARGET_DOT_RATIO);
      context.beginPath();
      context.arc(targetScreen.x, targetScreen.y, radius, 0, Math.PI * 2);
      context.fill();
    }
  }

  // Pending waypoints: small crosses joined by the same dashed light-grey line. They are not part of a
  // unit's committed order yet and are drawn even while nothing is selected only when a route exists.
  const pending = pendingRoutePoints(pendingPath);
  if (pending.length > 0) {
    const screenPending = pending.map(toScreen);
    for (const segment of pathSegments(screenPending)) {
      if (!pathSegmentVisible(segment.from, segment.to, bounds)) {
        continue;
      }
      context.beginPath();
      context.moveTo(segment.from.x, segment.from.y);
      context.lineTo(segment.to.x, segment.to.y);
      context.stroke();
    }
    const half = Math.max(3, tileOnScreen * PENDING_CROSS_RATIO);
    for (const point of screenPending) {
      if (!pathSegmentVisible(point, point, bounds)) {
        continue;
      }
      context.beginPath();
      context.moveTo(point.x - half, point.y - half);
      context.lineTo(point.x + half, point.y + half);
      context.moveTo(point.x + half, point.y - half);
      context.lineTo(point.x - half, point.y + half);
      context.stroke();
    }
  }

  context.restore();
}

export class WorldRenderer {
  private readonly canvas: HTMLCanvasElement;
  private readonly context: CanvasRenderingContext2D;

  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas;
    const context = canvas.getContext("2d", { alpha: false });
    if (context === null) {
      throw new Error("浏览器不支持 Canvas 2D，无法绘制游戏画面");
    }
    this.context = context;
  }

  /** Sizes the backing store from the CSS box and scales the context to CSS pixels. */
  private syncSize(camera: Camera): Size {
    const rect = this.canvas.getBoundingClientRect();
    const width = Math.max(1, rect.width);
    const height = Math.max(1, rect.height);
    const devicePixelRatio = Math.max(1, window.devicePixelRatio || 1);
    const backingWidth = Math.max(1, Math.round(width * devicePixelRatio));
    const backingHeight = Math.max(1, Math.round(height * devicePixelRatio));

    if (this.canvas.width !== backingWidth) {
      this.canvas.width = backingWidth;
    }
    if (this.canvas.height !== backingHeight) {
      this.canvas.height = backingHeight;
    }
    this.context.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
    this.context.imageSmoothingEnabled = false;
    camera.setViewport(width, height);
    return { width, height, devicePixelRatio };
  }

  /** Draws one frame. The caller owns the animation clock and the selection state. */
  render(
    world: WorldView,
    camera: Camera,
    atlas: Atlas | null,
    options: RenderOptions,
  ): void {
    const context = this.context;
    const size = this.syncSize(camera);
    context.clearRect(0, 0, size.width, size.height);
    context.fillStyle = BACKGROUND_COLOR;
    context.fillRect(0, 0, size.width, size.height);

    const tileSize = camera.tileSize;
    const tileOnScreen = camera.effectiveTileSize;
    const range = camera.visibleTileRange();
    const origin = camera.worldToScreen(range.minX * tileSize, range.minY * tileSize);
    const drawSize = tileOnScreen + 0.5;
    const selectedIds = new Set(options.selectedIds);

    // Terrain: atlas frame when present, procedural fallback otherwise.
    for (let tileY = range.minY; tileY <= range.maxY; tileY += 1) {
      for (let tileX = range.minX; tileX <= range.maxX; tileX += 1) {
        const screenX = origin.x + (tileX - range.minX) * tileOnScreen;
        const screenY = origin.y + (tileY - range.minY) * tileOnScreen;
        const terrain = terrainAt(world, tileX, tileY);
        const tile = atlas === null ? undefined : lookupTile(atlas, terrain);
        if (tile !== undefined) {
          const frame = tile.rect;
          context.drawImage(
            tile.source.image,
            frame.x,
            frame.y,
            frame.width,
            frame.height,
            screenX,
            screenY,
            drawSize,
            drawSize,
          );
        } else {
          drawTerrainTile(context, terrain, tileX, tileY, screenX, screenY, drawSize);
        }
      }
    }

    if (atlas === null) {
      drawTileGrid(context, camera, range, size.devicePixelRatio);
    }

    // Route overlay: above the terrain and the grid, below every shadow, ring and sprite. It is the same
    // layer for the committed path and the pending waypoints, so the two styles can only differ by their
    // marks (a target dot versus crosses).
    if (options.showPaths) {
      drawPathOverlays(
        context,
        world,
        camera,
        tileSize,
        size.devicePixelRatio,
        size.width,
        size.height,
        selectedIds,
        options.myPlayerId,
        options.pendingPath,
      );
    }

    const hover = options.hoverTile;
    const placement = options.placement;
    if (placement !== null) {
      // Armed BUILD: one ghost cell per footprint tile. All fills are drawn first and all outlines
      // second, so a neighbouring fill can never paint over an outline.
      const ghostLineWidth = Math.max(1, 2 / size.devicePixelRatio);
      for (const tile of placement.tiles) {
        const point = camera.worldToScreen(tile.x * tileSize, tile.y * tileSize);
        context.fillStyle = placementFillColor(tile.state);
        context.fillRect(point.x, point.y, tileOnScreen, tileOnScreen);
      }
      for (const tile of placement.tiles) {
        const point = camera.worldToScreen(tile.x * tileSize, tile.y * tileSize);
        context.strokeStyle = placementStrokeColor(tile.state);
        context.lineWidth = ghostLineWidth;
        context.strokeRect(
          point.x + ghostLineWidth / 2,
          point.y + ghostLineWidth / 2,
          Math.max(0, tileOnScreen - ghostLineWidth),
          Math.max(0, tileOnScreen - ghostLineWidth),
        );
      }
    } else if (hover !== null && tileInWindow(world, hover.x, hover.y)) {
      const hoverX = origin.x + (hover.x - range.minX) * tileOnScreen;
      const hoverY = origin.y + (hover.y - range.minY) * tileOnScreen;
      context.fillStyle = "rgba(230, 245, 255, 0.12)";
      context.fillRect(hoverX, hoverY, tileOnScreen, tileOnScreen);
      context.strokeStyle = "rgba(200, 235, 255, 0.75)";
      context.lineWidth = Math.max(1, 1.5 / size.devicePixelRatio);
      context.strokeRect(
        hoverX + 0.5,
        hoverY + 0.5,
        Math.max(0, tileOnScreen - 1),
        Math.max(0, tileOnScreen - 1),
      );
    }

    const padding = Math.max(80, tileOnScreen * 3);
    const prepared: PreparedEntity[] = [];
    for (const entity of world.entities) {
      // Entity positions are absolute world coordinates; only an entity whose ground tile is inside
      // the loaded window is drawn, so a stale or off-window entity can never float over unknown
      // terrain.
      if (!entityInWindow(world, entity)) {
        continue;
      }
      const point = camera.worldToScreen(entity.x * tileSize, entity.y * tileSize);
      if (
        point.x < -padding ||
        point.y < -padding ||
        point.x > size.width + padding ||
        point.y > size.height + padding
      ) {
        continue;
      }
      prepared.push({ entity, screenX: point.x, screenY: point.y });
    }
    prepared.sort((left, right) => {
      if (left.entity.y !== right.entity.y) {
        return left.entity.y - right.entity.y;
      }
      return left.entity.id - right.entity.id;
    });

    // Ground pass: every shadow first, then every ownership ring. The gold selection ring is drawn
    // after the flash halo below, so the flash can never hide it.
    for (const item of prepared) {
      drawGroundShadow(
        context,
        item.screenX,
        item.screenY,
        tileOnScreen,
        isBuildingEntityKind(item.entity.kind),
      );
    }
    // Ownership pass: an enemy unit's red base tint first, then one thin ring per entity, so the gold
    // selection ring can stay on top. The base tint is the extra mark that survives a crowded fight;
    // the own-unit white outline is drawn over the sprite in the entity pass below.
    for (const item of prepared) {
      if (ownerBadge(item.entity, options.myPlayerId) === "enemy-base") {
        drawEnemyBaseTint(
          context,
          item.screenX,
          item.screenY,
          tileOnScreen,
          ENEMY_UNIT_BASE_COLOR,
        );
      }
    }
    for (const item of prepared) {
      drawOwnershipRing(
        context,
        item.screenX,
        item.screenY,
        tileOnScreen,
        isBuildingEntityKind(item.entity.kind),
        ownershipColor(item.entity.playerId, options.myPlayerId),
      );
    }

    // Entity pass, sorted by y: entities lower on the screen are drawn later and overlap higher ones.
    for (const item of prepared) {
      const entity = item.entity;
      const building = isBuildingEntityKind(entity.kind);
      const entry = atlas === null ? undefined : lookupEntity(atlas, entity.kind);
      if (entry !== undefined) {
        drawEntitySprite(
          context,
          entry.source,
          entry.sprite,
          entity,
          item.screenX,
          item.screenY,
          camera.zoom,
          options.clockSeconds,
          tileSize,
        );
      } else if (building) {
        drawBuildingShape(
          context,
          item.screenX,
          item.screenY,
          tileOnScreen,
          playerColor(entity.playerId),
        );
      } else {
        drawUnitShape(
          context,
          item.screenX,
          item.screenY,
          tileOnScreen,
          playerColor(entity.playerId),
        );
      }

      // Owner mark over the sprite: a thin bright white rim on the local player's units. It is
      // achromatic and deliberately weaker than the gold selection ring drawn later.
      if (ownerBadge(entity, options.myPlayerId) === "own-outline") {
        drawOwnUnitOutline(
          context,
          item.screenX,
          item.screenY,
          tileOnScreen,
          OWN_UNIT_OUTLINE_COLOR,
        );
      }

      const ratio = entity.healthRatio;
      if (ratio !== undefined) {
        const selected = entity.selected === true || selectedIds.has(entity.id);
        if (ratio < 0.999 || selected) {
          const barWidth = tileOnScreen * (building ? 1.5 : 0.9);
          const top = item.screenY - tileOnScreen * (building ? 1.55 : 0.98);
          drawHealthBar(context, item.screenX, top, barWidth, ratio, tileOnScreen);
        }
      }

      if (building) {
        // Status chrome sits above the roof, never over the sprite. The construction bar is only drawn
        // while the server reports the site as unfinished; the queue marker only while jobs remain.
        if (underConstruction(entity)) {
          drawBuildingProgressBar(
            context,
            item.screenX,
            item.screenY - tileOnScreen * 1.75,
            tileOnScreen * 1.5,
            entity.progress ?? 0,
            tileOnScreen,
          );
        }
        const marker = queueMarkerText(entity.queue);
        if (marker !== "") {
          drawQueueMarker(
            context,
            item.screenX,
            item.screenY - tileOnScreen * 1.9,
            marker,
            tileOnScreen,
          );
        }
      }
    }

    // Selection flash pass: one achromatic halo per entity that a player action just selected, drawn
    // above its sprite. The intensity comes from the shared timer, so the halo fades out in place and
    // adds no per-entity state to the renderer. A deselected entity may keep its map entry until the
    // timer expires, but no halo is drawn unless it is currently selected.
    if (options.flashStarts.size > 0) {
      for (const item of prepared) {
        const selected = item.entity.selected === true || selectedIds.has(item.entity.id);
        if (!selected) {
          continue;
        }
        const startedAt = options.flashStarts.get(item.entity.id);
        if (startedAt === undefined) {
          continue;
        }
        const intensity = flashIntensity(startedAt, options.nowMs, FLASH_MS);
        if (intensity <= 0) {
          continue;
        }
        drawFlashHalo(
          context,
          item.screenX,
          item.screenY,
          tileOnScreen,
          isBuildingEntityKind(item.entity.kind),
          FLASH_COLOR,
          intensity,
        );
      }
    }

    // Selection rings after the flash, so the gold ring is always the topmost selection mark. Each ring
    // is a ground ellipse drawn for every selected entity, including a building.
    for (const item of prepared) {
      const selected = item.entity.selected === true || selectedIds.has(item.entity.id);
      if (selected) {
        drawSelectionRing(
          context,
          item.screenX,
          item.screenY,
          tileOnScreen,
          isBuildingEntityKind(item.entity.kind),
        );
      }
    }

    // Personal markers last among the map marks: above every sprite, ring and flash, below the marquee.
    // The white square and number are chrome, so they never compete with the ownership or selection hues.
    if (options.markers.length > 0) {
      drawMarkers(
        context,
        world,
        camera,
        tileSize,
        options.markers,
        size.width,
        size.height,
        size.devicePixelRatio,
      );
    }

    // Marquee last, so the rectangle is visible over terrain, rings and sprites. It is in CSS pixels
    // because the context was already scaled for the device pixel ratio.
    if (options.marquee !== null) {
      const marquee = options.marquee;
      context.save();
      context.fillStyle = "rgba(230, 245, 255, 0.14)";
      context.fillRect(marquee.x, marquee.y, marquee.width, marquee.height);
      context.strokeStyle = "rgba(255, 255, 255, 0.9)";
      context.lineWidth = 1;
      context.setLineDash([6, 4]);
      context.strokeRect(
        marquee.x + 0.5,
        marquee.y + 0.5,
        Math.max(0, marquee.width - 1),
        Math.max(0, marquee.height - 1),
      );
      context.restore();
    }
  }
}
