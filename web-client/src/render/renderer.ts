/**
 * Canvas 2D composition for one world frame.
 *
 * The renderer owns the backing store size: CSS pixels come from `getBoundingClientRect`, device
 * pixels from `devicePixelRatio`, and the context is scaled so every drawing call below can use CSS
 * pixels. It never assumes the two sizes are equal.
 *
 * Draw order is terrain, tile grid (fallback only), the hover/placement tile, ground shadows, ownership
 * rings, selection rings, then entities sorted by y so lower entities overlap higher ones, then health
 * bars. Ownership rings are thinner than selection rings and are drawn first, so the gold selection ring
 * always stays on top.
 *
 * Every tile and entity is drawn from the plugin image the composite atlas resolved it to, so one frame can
 * mix art from several plugins without any of them sharing a texture page. An entity's art scale comes from
 * the winning plugin's `tilePixels`, which keeps a 16, 32 and 64 pixel plugin aligned on the same map.
 */

import type { Atlas, AtlasEntity, AtlasSource, AtlasTile, EntitySprite, SpriteRect } from "./atlas";
import type { Camera, TileCoord } from "./camera";
import {
  drawBuildingShape,
  drawGroundShadow,
  drawHealthBar,
  drawOwnershipRing,
  drawSelectionRing,
  drawTerrainTile,
  drawTileGrid,
  drawUnitShape,
  playerColor,
} from "./fallback";
import {
  entityInWindow,
  isBuildingKind,
  kindName,
  ownershipColor,
  terrainAt,
  tileInWindow,
  type EntityView,
  type WorldView,
} from "./view";

const BACKGROUND_COLOR = "#0b1016";

export interface RenderOptions {
  /** Monotonic animation clock in seconds, supplied by the caller. */
  readonly clockSeconds: number;
  /** Tile under the mouse, or null when the pointer is outside the canvas. */
  readonly hoverTile: TileCoord | null;
  /** Currently selected entity id, or null. */
  readonly selectedId: number | null;
  /** Tile armed for build placement, or null; drawn with a stronger highlight than plain hover. */
  readonly placementTile: TileCoord | null;
  /**
   * Engine player id answered by `WHOAMI`, or null before login and in the demo world.
   *
   * With null every ownership ring is grey, which is the documented demo behaviour.
   */
  readonly myPlayerId: number | null;
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
  return atlas.tiles.get(terrain) ?? atlas.tiles.get(kindName(terrain));
}

function lookupEntity(atlas: Atlas, kind: string): AtlasEntity | undefined {
  return atlas.entities.get(kind) ?? atlas.entities.get(kindName(kind));
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
): void {
  const frame = frameFor(sprite, entity, clockSeconds);
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

    const hover = options.hoverTile;
    if (hover !== null && tileInWindow(world, hover.x, hover.y)) {
      const hoverX = origin.x + (hover.x - range.minX) * tileOnScreen;
      const hoverY = origin.y + (hover.y - range.minY) * tileOnScreen;
      const placement = options.placementTile;
      const placing =
        placement !== null && placement.x === hover.x && placement.y === hover.y;
      context.fillStyle = placing ? "rgba(255, 209, 102, 0.28)" : "rgba(230, 245, 255, 0.12)";
      context.fillRect(hoverX, hoverY, tileOnScreen, tileOnScreen);
      context.strokeStyle = placing ? "rgba(255, 226, 150, 0.95)" : "rgba(200, 235, 255, 0.75)";
      context.lineWidth = Math.max(1, (placing ? 2.5 : 1.5) / size.devicePixelRatio);
      context.strokeRect(hoverX + 0.5, hoverY + 0.5, tileOnScreen - 1, tileOnScreen - 1);
    }

    const padding = Math.max(80, tileOnScreen * 3);
    const prepared: PreparedEntity[] = [];
    for (const entity of world.entities) {
      // Entity positions are absolute map coordinates; only an entity whose ground tile is inside
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

    // Ground pass: every shadow first, then every ownership ring, then every selection ring, so a shadow
    // never hides a ring and the gold selection ring is never hidden by an ownership ring.
    for (const item of prepared) {
      drawGroundShadow(
        context,
        item.screenX,
        item.screenY,
        tileOnScreen,
        isBuildingKind(item.entity.kind),
      );
    }
    // Ownership pass: one thin ring per entity, first, so the gold selection ring can stay on top.
    for (const item of prepared) {
      drawOwnershipRing(
        context,
        item.screenX,
        item.screenY,
        tileOnScreen,
        isBuildingKind(item.entity.kind),
        ownershipColor(item.entity.playerId, options.myPlayerId),
      );
    }
    for (const item of prepared) {
      const selected = item.entity.selected === true || item.entity.id === options.selectedId;
      if (selected) {
        drawSelectionRing(
          context,
          item.screenX,
          item.screenY,
          tileOnScreen,
          isBuildingKind(item.entity.kind),
        );
      }
    }

    // Entity pass, sorted by y: entities lower on the screen are drawn later and overlap higher ones.
    for (const item of prepared) {
      const entity = item.entity;
      const building = isBuildingKind(entity.kind);
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

      const ratio = entity.healthRatio;
      if (ratio !== undefined) {
        const selected = entity.selected === true || entity.id === options.selectedId;
        if (ratio < 0.999 || selected) {
          const barWidth = tileOnScreen * (building ? 1.5 : 0.9);
          const top = item.screenY - tileOnScreen * (building ? 1.55 : 0.98);
          drawHealthBar(context, item.screenX, top, barWidth, ratio, tileOnScreen);
        }
      }
    }
  }
}
