/**
 * Offline checks for the pure render math.
 *
 * Nothing here touches the DOM. Node 24 strips the TypeScript types and runs the file directly, so
 * the check exercises the same `camera.ts`, `view.ts`, `status.ts`, `marquee.ts`, `sweep.ts`, `flash.ts`,
 * `crosshair.ts`, `follow.ts`, `toast.ts`, `atlas.ts`,
 * `fallback.ts`, `interpolate.ts`, `selection.ts`, `commandbar.ts`, `upgrade.ts`, `tooltip.ts`,
 * `events.ts`, `resources.ts`, `logistics.ts`, `projectile.ts` and `latex.ts` the browser imports - no duplicated formulas. `scripts/tsconfig.check.json` is only
 * used for type checking.
 *
 * Run: `node scripts/check-render-math.ts` (or `pnpm check:render`).
 */

import { readFileSync } from "node:fs";
import {
  MAX_BATCH_COMMANDS,
  MAX_MOVE_POINTS,
  assist,
  batchCommands,
  build,
  buildBatch,
  buildPlacementBatch,
  orderAttack,
  orderAttackMove,
  orderMove,
  orderMoveRoute,
  orderStop,
  parseWhoAmI,
  reclaim,
  repair,
  sequenceLine,
  train,
  trainBatch,
  transfer,
  travel,
  upgrade,
  upgradeUnit,
  whoAmI,
} from "../src/commands.ts";
import { HEARTBEAT_MS, shouldHeartbeat } from "../src/heartbeat.ts";
import {
  CHUNK_SIZE_TILES,
  SHADOW_ALPHA,
  approachShadowAlpha,
  chunkKey,
  chunkOfTile,
  shadowTargetAlpha,
} from "../src/render/shadow.ts";
import {
  CAMERA_SAFETY_BOUND_TILES,
  Camera,
  MAX_ZOOM,
  MIN_ZOOM,
  tileToWorld,
  worldToTile,
} from "../src/render/camera.ts";
import {
  MAX_INTERPOLATION_DISTANCE_TILES,
  alphaFor,
  interpolate,
  type Frame,
} from "../src/render/interpolate.ts";
import {
  atlasLabel,
  buildAtlasSources,
  compositeAtlas,
  entityDisplayName,
  entityFrameRects,
  itemDisplayLabel,
  mergeLayers,
  parseAtlas,
  readManifestEntry,
  lookupAtlasEntity,
  tileDisplayName,
  tileRect,
  type AtlasData,
  type AtlasSource,
} from "../src/render/atlas.ts";
import {
  PROGRESS_BORDER_COLOR,
  PROGRESS_FILL_COLOR,
  PROGRESS_TRACK_COLOR,
  SELECTION_COLOR,
  TERRAIN_COLORS,
  terrainColor,
} from "../src/render/fallback.ts";
import { FONT_STACK } from "../src/render/font.ts";
import {
  FOLLOW_PULSE_MS,
  FOLLOW_RING_COLOR,
  FOLLOW_RING_MAX_ALPHA,
  FOLLOW_RING_MAX_SCALE,
  FOLLOW_RING_MIN_ALPHA,
  FOLLOW_RING_MIN_SCALE,
  followPulse,
  followRingAlpha,
  followRingScale,
} from "../src/render/follow.ts";
import {
  CLICK_FLASH_STRENGTH,
  FLASH_COLOR,
  FLASH_MS,
  SWEEP_FLASH_STRENGTH,
  clickFlashIds,
  flashIntensity,
  flashRingCount,
  pruneFlashes,
  startFlash,
} from "../src/render/flash.ts";
import {
  CROSSHAIR_BASE_RADIUS_PX,
  CROSSHAIR_COLOR,
  CROSSHAIR_EDGE_INSET_PX,
  CROSSHAIR_MS,
  CROSSHAIR_SCALE_END,
  CROSSHAIR_SCALE_START,
  clampCrosshairToCanvas,
  crosshairAlpha,
  crosshairFrame,
  crosshairProgress,
  crosshairRadius,
  crosshairScale,
  screenToBackingPixels,
} from "../src/render/crosshair.ts";
import {
  MarqueeSweep,
  marqueeCoveredIds,
  releaseFlashIds,
} from "../src/render/sweep.ts";
import {
  combatLabel,
  demandIcon,
  demandMissingText,
  formatProgressPercent,
  incomeSuffix,
  progressBarRatio,
  queueItemNumber,
  queueMarkerText,
  queueProgressRatio,
  sizeLabel,
  stockTagVisible,
  unitCargoLabel,
  unitStockTagLabel,
  unitTaskLabel,
  warehouseContentsLabel,
} from "../src/render/status.ts";
import {
  POP_LIFETIME_MS,
  POP_RISE_PX,
  POP_THROTTLE_MS,
  activePops,
  popAlpha,
  popProgress,
  popRise,
  prunePops,
  registerSnapshotPops,
  type PopRecord,
} from "../src/render/pop.ts";
import {
  PROJECTILE_HEAD_TILES,
  PROJECTILE_SPEED_TPS,
  PROJECTILE_TRAIL_TILES,
  projectileAngle,
  projectileDrawState,
  projectileFlightMs,
  projectileHeadPoints,
  projectileTrail,
  trackProjectiles,
  type TrackedProjectile,
} from "../src/render/projectile.ts";
import {
  CONTROL_GROUP_MAX,
  EMPTY_CONTROL_GROUPS,
  add as addControlGroup,
  assign as assignControlGroup,
  describe as describeControlGroup,
  prune as pruneControlGroups,
  recall as recallControlGroup,
  type ControlGroups,
} from "../src/controlGroups.ts";
import {
  marqueeTileRange,
  screenRectBetween,
  toggleSelection,
} from "../src/render/marquee.ts";
import {
  TOAST_ERROR_LIFETIME_MS,
  TOAST_LIFETIME_MS,
  TOAST_VISIBLE_CAP,
  capToasts,
  isErrAnswer,
  toastLifetimeMs,
  toastTone,
  trimToastLine,
} from "../src/toast.ts";
import {
  demoWorld,
  ENEMY_UNIT_BASE_COLOR,
  entityCoversTile,
  entityFootprintHeight,
  entityFootprintOrigin,
  entityFootprintWidth,
  entityInMarquee,
  entityInWindow,
  isBuildingKind,
  isWorldTile,
  localPlayerIdFromRoster,
  marqueeSelection,
  MAX_SNAPSHOT_PATH_POINTS,
  OWN_UNIT_OUTLINE_COLOR,
  OWNERSHIP_ENEMY_COLOR,
  OWNERSHIP_MINE_COLOR,
  OWNERSHIP_NEUTRAL_COLOR,
  ownerBadge,
  ownershipColor,
  ownershipDecision,
  ownershipOf,
  ownershipTag,
  ownershipTagFor,
  SCENE_PLAYER_ID,
  snapshotOrDemo,
  snapshotUrl,
  TERRAIN_IDS,
  TERRAIN_VOID,
  terrainAt,
  underConstruction,
  windowTileIndex,
  worldViewFromSnapshot,
  type EntityView,
  type TerrainId,
  type WorldView,
} from "../src/render/view.ts";
import {
  upgradeBatchPlan,
  upgradeBatchSummary,
  upgradeCostLabel,
  upgradeEntries,
  upgradeEntry,
  unitUpgradeEntry,
} from "../src/render/upgrade.ts";
import { isBuilderUnit } from "../src/render/selection.ts";
import {
  DICTIONARIES,
  LANGUAGES,
  currentI18nLanguage,
  dimensionLabel,
  genericContentLabel,
  initI18n,
  registerServerKeyValues,
  resolveLanguage,
  serverKeyValues,
  t,
} from "../src/i18n.ts";
import {
  constructionDeficit,
  formatDeficit,
  parseContentCatalogue,
  producerDeficit,
  registerCatalogueNames,
} from "../src/content.ts";
import {
  WORLD_BOOTSTRAP_PATH,
  bootstrapUrl,
  bootstrapVersionHandshake,
  clearWorldBootstrap,
  getWorldBootstrap,
  parseWorldBootstrap,
  setWorldBootstrap,
  withBootstrapData,
  type WorldBootstrap,
} from "../src/bootstrap.ts";
import { ATTACK_PATH_COLOR } from "../src/render/paths.ts";
import {
  isKnownNotifyType,
  parseContentVersionNotify,
  parseNotify,
  routeFrame,
} from "../src/notify.ts";
import {
  RASTER_BUCKET_MAX_PX,
  RASTER_CACHE_MAX_ENTRIES,
  rasterBucket,
  rasterCacheKey,
} from "../src/render/raster.ts";
import {
  chunkShadowTargetAlpha,
  chunkSpansInRange,
} from "../src/render/shadow.ts";
import { isLanguageSetting } from "../src/settings.ts";
import {
  REPAIR_ACTION_LABEL,
  RECLAIM_ACTION_LABEL,
  buildingActionModel,
  buildingCostLabel,
  reclaimLabel,
  repairLabel,
} from "../src/render/buildingActions.ts";
import {
  PATH_COLOR,
  pathPointsFor,
  pathSegments,
  pathSegmentVisible,
  pendingRoutePoints,
} from "../src/render/paths.ts";
import {
  SNAPSHOT_EDGE_MARGIN_TILES,
  SNAPSHOT_RADIUS_TILES,
  SnapshotRequestTracker,
  shouldRequestWindow,
  shouldStreamWindow,
  snapshotOrigin,
  windowCentre,
  windowEdgeDistance,
  windowNeedsStream,
} from "../src/render/stream.ts";
import {
  DEFAULT_BUILDING_FOOTPRINT,
  MAX_BUILD_REACH_TILES,
  PLACEMENT_BLOCKED_FILL,
  PLACEMENT_BLOCKED_STROKE,
  PLACEMENT_LEGAL_FILL,
  PLACEMENT_OUT_OF_REACH_FILL,
  buildPlacementPreview,
  buildReachDistance,
  combinePlacementStates,
  entityFootprint,
  entityFootprintCovers,
  footprintForKind,
  footprintTiles,
  isWithinBuildReach,
  placementFillColor,
  placementReasonText,
  placementStrokeColor,
  tileOccupiedByEntities,
  type Footprint,
} from "../src/render/placement.ts";
import {
  MINIMAP_DOT_SIZE,
  MINIMAP_PANEL_SIZE,
  minimapCameraTarget,
  minimapEdgeGeometry,
  minimapLayout,
  minimapPointForTile,
  minimapTileAt,
} from "../src/render/minimap.ts";
import {
  COMPASS_SECTORS,
  anchorCameraTarget,
  compassSector,
  tileDistance,
} from "../src/render/enemy.ts";
import {
  homeAnchorUnknownText,
  MAX_MARKERS,
  clearMarkers,
  formatMarkerLabel,
  homeCameraTarget,
  markerCameraTarget,
  markerEdgeArrow,
  nearestOutsideMarker,
  nearestOutsideMarkerArrow,
  ownAnchorOf,
  placeMarker,
  removeLastMarker,
  markerInDrawnDimension,
  worldDimensionKey,
  type Marker,
} from "../src/render/markers.ts";
import {
  doubleClickSelection,
  engineerForPlacement,
  engineerSelection,
  engineersForPlacement,
  isEngineerKind,
  selectByPriority,
} from "../src/render/selection.ts";
import { pickEntityAt } from "../src/render/picking.ts";
import {
  SELECTION_CHIP_LIMIT,
  attackMoveActionLabel,
  chipLabel,
  commandBarModel,
  emptySelectionHint,
  healthBarRatio,
  selectionChipList,
  stopActionLabel,
  trainMenu,
  trainPathsFor,
} from "../src/render/commandbar.ts";
import {
  TOOLTIP_GAP_PX,
  TOOLTIP_IDLE_HIDE_MS,
  TOOLTIP_MARGIN_PX,
  TOOLTIP_THUMBNAIL_SIZE_PX,
  clampTooltipPosition,
  kindFirstCharacter,
  tooltipContent,
  tooltipIdleExpired,
  tooltipThumbnail,
} from "../src/render/tooltip.ts";
import {
  ATTACKER_DETECTION_TILES,
  EVENT_DEDUPE_MS,
  EventDeduplicator,
  HEALTH_DROP_THRESHOLD,
  attackerTierNear,
  diffSnapshotEvents,
  meleeTierOf,
} from "../src/render/events.ts";
import {
  CURRENCY_PATHS,
  EMPTY_RESOURCE_MARKS,
  clearResourceMarks,
  fullResourceList,
  inventoryTotals,
  isWarehouseKind,
  markAllResources,
  markedResourceItems,
  markedSummaryLabel,
  toggleResourceMark,
  warehouseResourceTotals,
} from "../src/render/resources.ts";
import {
  ownWarehouses,
  selectedOwnWarehouse,
  transferAnswerLabel,
  transferFormModel,
  transferTargetLabel,
  warehouseInboundLabel,
  warehouseMarkNote,
  warehousePanelModel,
  warehouseScopeToggleSingleLabel,
  warehouseScopeToggleTotalLabel,
  warehouseStorageLabel,
} from "../src/render/logistics.ts";
import {
  escapeHtml,
  plainTexFallback,
  renderTexSegment,
  splitTex,
  type TexSegment,
} from "../src/render/latex.ts";
import {
  ALL_MESSAGES,
  DEFAULT_INVENTORY_RADIUS_TILES,
  FILTER_PRESETS,
  MAX_INVENTORY_RADIUS_TILES,
  MESSAGE_CATEGORIES,
  MESSAGE_CATEGORY_LABELS,
  MIN_INVENTORY_RADIUS_TILES,
  SETTINGS_STORAGE_KEY,
  SETTINGS_VERSION,
  clampInventoryRadius,
  classifyCommand,
  classifyNotice,
  classifyToast,
  defaultSettings,
  isMessageCategory,
  isToastAllowed,
  parseSettings,
  presetFilters,
  serializeSettings,
  type MessageCategory,
  type Settings,
} from "../src/settings.ts";
import {
  LOOP_MODES,
  addTracks,
  clampIndex,
  clampVolume,
  formatTime,
  nextIndex,
  prevIndex,
  progressSliderValue,
  removeTrack,
  type Track,
} from "../src/music.ts";
import {
  TOAST_SLIDE_MS,
  UI_FORBIDDEN_STYLE_PROPERTIES,
  UI_PALETTE,
  UI_WEIGHTS,
} from "../src/theme.ts";

/**
 * Every building kind the classic catalogue ships, in upgrade-ladder order.
 *
 * <p>This is a **check fixture**, not game data the client renders from: it exists so the atlas
 * coverage, footprint and command-builder assertions can walk the classic plugin's content the same
 * way the server's `ClassicContent` declares it. The BUILD and TRAIN menus never read a client-side
 * catalogue — the snapshot's `buildable` / `trains` arrays are the catalogue — so production code
 * carries no such list (removed from `src/commands.ts` in the hardcoded-data audit).
 */
const CLASSIC_BUILDING_KINDS = [
  "building.armory.t1",
  "building.armory.t2",
  "building.mine.t1",
  "building.mine.t2",
  "building.mine.t3",
  "building.lumbercamp.t1",
  "building.lumbercamp.t2",
  "building.lumbercamp.t3",
  "building.turret.t1",
  "building.turret.t2",
  "building.turret.t3",
  "building.warehouse",
] as const;

let passed = 0;
const failures: string[] = [];

// The fixtures below assert the exact strings the page printed before the i18n layer existed, which is
// the zh-CN wording. The HUD label rules resolve through `t()`, so the check pins zh-CN here and the
// dedicated i18n block further down switches languages explicitly (and pins zh-CN back).
initI18n("zh-CN");

function check(name: string, condition: boolean, detail: string | null = ""): void {
  if (condition) {
    passed += 1;
    console.log(`ok ${passed} - ${name}`);
    return;
  }
  const message = detail === "" ? name : `${name} (${detail})`;
  failures.push(message);
  console.log(`fail - ${message}`);
}

function near(actual: number, expected: number, epsilon = 1e-6): boolean {
  return Math.abs(actual - expected) <= epsilon;
}

function tileTypes(world: WorldView): Set<string> {
  const types = new Set<string>();
  for (const row of world.tiles) {
    for (const terrain of row) {
      types.add(terrain);
    }
  }
  return types;
}

/** A JSON object as a string-keyed table, or null for every other JSON value. */
function recordTable(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

// ---------------------------------------------------------------------------------------------
// Camera
// ---------------------------------------------------------------------------------------------

// The world is unbounded: the camera is constructed with a home world pixel, not a map size.
const camera = new Camera(32, 1, 640, 480);
camera.setViewport(800, 600);
camera.reset();
check(
  "复位回到家位置；平移无边界（右下与左上、可越过原点进入负坐标）",
  near(camera.centerX, 640) &&
    near(camera.centerY, 480) &&
    (() => {
      camera.pan(1_000_000, 1_000_000);
      const rightDown = near(camera.centerX, 1_000_640) && near(camera.centerY, 1_000_480);
      camera.pan(-2_000_000, -2_000_000);
      return rightDown && near(camera.centerX, -999_360) && near(camera.centerY, -999_520);
    })(),
);

camera.setCenter(-100 * 32, -50 * 32);
const visible = camera.visibleTileRange();
check(
  "镜头在负坐标时可见瓦片范围也是负的（不再夹到 0..）",
  visible.minX < 0 &&
    visible.minY < 0 &&
    visible.maxX < 0 &&
    visible.maxY < 0 &&
    visible.minX <= -100 &&
    visible.maxX >= -100 &&
    visible.minY <= -50 &&
    visible.maxY >= -50,
  `${visible.minX}..${visible.maxX} x ${visible.minY}..${visible.maxY}`,
);

const roundTrip = camera.screenToWorld(
  camera.worldToScreen(-3_200, -1_600).x,
  camera.worldToScreen(-3_200, -1_600).y,
);
check(
  "负世界坐标与屏幕坐标可逆",
  near(roundTrip.x, -3_200) && near(roundTrip.y, -1_600),
  `${roundTrip.x}, ${roundTrip.y}`,
);

camera.reset();
const anchorBefore = camera.screenToWorld(137, 211);
camera.zoomAt(137, 211, 1.8);
const anchorAfter = camera.screenToWorld(137, 211);
check(
  "缩放：以光标为中心时光标下世界点不动、倍率生效，上下限 2.5/0.5，复位恢复默认缩放与家位置",
  near(anchorBefore.x, anchorAfter.x) &&
    near(anchorBefore.y, anchorAfter.y) &&
    near(camera.zoom, 1.8, 1e-9) &&
    (() => {
      camera.zoomAt(400, 300, 1_000_000);
      const capped = camera.zoom === MAX_ZOOM;
      camera.zoomAt(400, 300, 0.000001);
      const floored = camera.zoom === MIN_ZOOM;
      camera.reset();
      return capped && floored && camera.zoom === 1 && near(camera.centerX, 640) && near(camera.centerY, 480);
    })(),
);

// The safety bound is not a map edge: it exists because the engine stores positions in
// single-precision Vec2 values, whose resolution degrades beyond roughly 500,000 tiles.
camera.setCenter(-100_000 * 32, -100_000 * 32);
check(
  "镜头远行：-100000 瓦片不被夹紧；±500000 瓦片安全边界把镜头停住",
  (() => {
    camera.setCenter(-100_000 * 32, -100_000 * 32);
    const free = near(camera.centerX, -3_200_000) && near(camera.centerY, -3_200_000);
    camera.setCenter(-1_000_000_000_000, 1_000_000_000_000);
    const boundFocus = camera.centerTile();
    return (
      free &&
      CAMERA_SAFETY_BOUND_TILES === 500_000 &&
      near(camera.centerX, -CAMERA_SAFETY_BOUND_TILES * 32) &&
      near(camera.centerY, CAMERA_SAFETY_BOUND_TILES * 32) &&
      boundFocus.x === -CAMERA_SAFETY_BOUND_TILES &&
      boundFocus.y === CAMERA_SAFETY_BOUND_TILES
    );
  })(),
);

camera.setCenter(-16, -16);
check(
  "负坐标瓦片：中心与屏幕取瓦片都向下取整；整数瓦片判定拒绝小数与 NaN",
  camera.centerTile().x === -1 &&
    camera.centerTile().y === -1 &&
    camera.tileAtScreen(camera.viewportWidth / 2, camera.viewportHeight / 2).x === -1 &&
    isWorldTile(-100_000, 500_000) &&
    !isWorldTile(-0.5, 0) &&
    !isWorldTile(0, Number.NaN),
);

// ---------------------------------------------------------------------------------------------
// Marquee selection: canvas rectangle -> signed world tiles, own-unit containment, shift toggle
// ---------------------------------------------------------------------------------------------

// The camera sits west and north of the world origin, so the converted range is entirely negative:
// screen (100,120) -> world (-3500,-1780) -> tile (-110,-56); no truncation toward zero is allowed.
const marqueeCamera = new Camera(32, 1, -3_200, -1_600);
marqueeCamera.setViewport(800, 600);
const marqueeRect = screenRectBetween({ x: 100, y: 120 }, { x: 300, y: 260 });
const marqueeRange = marqueeTileRange(marqueeCamera, marqueeRect);
check(
  "框选几何：矩形 -> 负世界瓦片范围、反方向拖拽同矩形、零面积矩形仍选中单格（负坐标按 floor）",
  marqueeRange.minX === -110 &&
    marqueeRange.maxX === -104 &&
    marqueeRange.minY === -56 &&
    marqueeRange.maxY === -52 &&
    JSON.stringify(screenRectBetween({ x: 300, y: 260 }, { x: 100, y: 120 })) ===
      JSON.stringify(marqueeRect) &&
    JSON.stringify(marqueeTileRange(marqueeCamera, screenRectBetween({ x: 300, y: 260 }, { x: 100, y: 120 }))) ===
      JSON.stringify(marqueeRange) &&
    JSON.stringify(marqueeTileRange(marqueeCamera, screenRectBetween({ x: 101, y: 121 }, { x: 101, y: 121 }))) ===
      JSON.stringify({ minX: -110, maxX: -110, minY: -56, maxY: -56 }),
  `${marqueeRange.minX},${marqueeRange.minY}..${marqueeRange.maxX},${marqueeRange.maxY}`,
);

const marqueeWorld: WorldView = {
  originX: 0,
  originY: 0,
  width: 8,
  height: 8,
  tiles: Array.from({ length: 8 }, () => Array.from({ length: 8 }, () => "grass" as TerrainId)),
  entities: [
    { id: 1, kind: "worker", x: 2.4, y: 2.6, playerId: 1, role: "worker" },
    { id: 2, kind: "soldier", x: 3.1, y: 4.9, playerId: 1, role: "combat" },
    { id: 3, kind: "worker", x: 2.5, y: 2.5, playerId: 2, role: "worker" },
    { id: 4, kind: "warehouse", x: 2.5, y: 2.5, playerId: 1 },
    { id: 5, kind: "worker", x: 7.5, y: 7.5, playerId: 1 },
    { id: 6, kind: "worker", x: 2.5, y: 3, playerId: 0 },
    { id: 7, kind: "building.lumbercamp.t1", x: 3, y: 3, playerId: 1 },
  ],
};
const marqueeSquare = { minX: 2, minY: 2, maxX: 4, maxY: 4 };
const marqueeIds = marqueeSelection(marqueeWorld, marqueeSquare, 1);
check(
  "框选优先级与几何：战斗单位压过工兵与建筑，敌人/中立/范围外永不入选；边界 [min, max+1)；建筑几何同规则",
  JSON.stringify(marqueeIds) === JSON.stringify([2]) &&
    !marqueeIds.includes(3) &&
    !marqueeIds.includes(4) &&
    !marqueeIds.includes(6) &&
    !marqueeIds.includes(7) &&
    !marqueeIds.includes(5) &&
    entityInMarquee({ id: 8, kind: "worker", x: 2, y: 2, playerId: 1 }, marqueeSquare, 1) === true &&
    entityInMarquee({ id: 9, kind: "worker", x: 5, y: 2.5, playerId: 1 }, marqueeSquare, 1) === false &&
    entityInMarquee({ id: 10, kind: "worker", x: 4.999, y: 4.999, playerId: 1 }, marqueeSquare, 1) === true &&
    entityInMarquee({ id: 11, kind: "building.warehouse", x: 3, y: 3, playerId: 1 }, marqueeSquare, 1) === true &&
    entityInMarquee({ id: 12, kind: "building.warehouse", x: 3, y: 3, playerId: 2 }, marqueeSquare, 1) === false,
  marqueeIds.join(","),
);

// A marquee that covers only own builders and own buildings keeps the builders: the engineer group
// outranks the building group. The enemy engineer must not join it.
const builderWorld: WorldView = {
  ...marqueeWorld,
  entities: [
    { id: 1, kind: "unit.engineer", x: 3, y: 3, playerId: 1 },
    { id: 2, kind: "building.warehouse", x: 2.5, y: 2.5, playerId: 1 },
    { id: 3, kind: "unit.engineer", x: 3, y: 3, playerId: 2 },
  ],
};
const builderIds = marqueeSelection(builderWorld, marqueeSquare, 1);

// With no unit at all the marquee falls through to the own buildings it covers, enemy buildings stay out.
const buildingWorld: WorldView = {
  ...marqueeWorld,
  entities: [
    { id: 1, kind: "building.warehouse", x: 2.5, y: 2.5, playerId: 1 },
    { id: 2, kind: "building.lumbercamp.t1", x: 3, y: 3, playerId: 1 },
    { id: 3, kind: "building.warehouse", x: 3, y: 3, playerId: 2 },
  ],
};
const buildingIds = marqueeSelection(buildingWorld, marqueeSquare, 1);
check(
  "框选优先级：工兵压过建筑（敌人不入选），只覆盖建筑时选全部自己的建筑",
  JSON.stringify(builderIds) === JSON.stringify([1]) &&
    JSON.stringify(buildingIds) === JSON.stringify([1, 2]),
  `${builderIds.join(",")} / ${buildingIds.join(",")}`,
);

// The same priority rule as a pure function, so the group choice can be read without a world or window.
const priorityEntities: EntityView[] = [
  { id: 21, kind: "unit.melee.t1", x: 2, y: 2, playerId: 1 },
  { id: 22, kind: "unit.melee.t2", x: 3, y: 3, playerId: 1 },
  { id: 23, kind: "unit.engineer", x: 3, y: 3, playerId: 1 },
  { id: 24, kind: "building.warehouse", x: 3, y: 3, playerId: 1 },
];
check(
  "selectByPriority：战斗组优先、工兵压过建筑；role 优先于 attackDamage；空输入不选中",
  JSON.stringify(selectByPriority(priorityEntities)) === JSON.stringify([21, 22]) &&
    JSON.stringify(selectByPriority(priorityEntities.slice(2))) === JSON.stringify([23]) &&
    JSON.stringify(selectByPriority([priorityEntities[3]!])) === JSON.stringify([24]) &&
    JSON.stringify(
      selectByPriority([
        { id: 31, kind: "unit.engineer", x: 2, y: 2, playerId: 1, role: "support", attackDamage: 2 },
        { id: 32, kind: "unit.melee.t1", x: 3, y: 3, playerId: 1, role: "combat", attackDamage: 10 },
      ]),
    ) === JSON.stringify([32]) &&
    selectByPriority([]).length === 0 &&
    marqueeSelection({ ...marqueeWorld, entities: [] }, marqueeSquare, 1).length === 0 &&
    marqueeSelection(buildingWorld, marqueeSquare, null).length === 0,
);

// Ownership is repeated by the optional player id, so a direct caller also cannot select an enemy or
// neutral entity even when it is attack capable.
const ownershipWorld: WorldView = {
  ...marqueeWorld,
  entities: [
    { id: 1, kind: "unit.melee.t1", x: 3, y: 3, playerId: 2 },
    { id: 2, kind: "unit.melee.t1", x: 3, y: 3, playerId: 0 },
    { id: 3, kind: "building.warehouse", x: 3, y: 3, playerId: 1 },
  ],
};
check(
  "框选所有权过滤与 Shift 切换：敌人/中立不入选、自己的建筑照常；先加后减顺序稳定不重复",
  JSON.stringify(marqueeSelection(ownershipWorld, marqueeSquare, 1)) === JSON.stringify([3]) &&
    JSON.stringify(selectByPriority(ownershipWorld.entities, 1)) === JSON.stringify([3]) &&
    JSON.stringify(toggleSelection(toggleSelection(toggleSelection([], 1), 2), 1)) ===
      JSON.stringify([2]) &&
    JSON.stringify(toggleSelection([2], 2)) === JSON.stringify([]),
);

// ---------------------------------------------------------------------------------------------
// Toast console rules: normal/error lifetime, error tone, visible cap, trimming
// ---------------------------------------------------------------------------------------------

check(
  "toast 规则：普通 4 秒错误 8 秒、ERR/客户端拒绝是错误 tone、最多 5 条丢最旧、文本折叠截断",
  TOAST_LIFETIME_MS === 4_000 &&
    TOAST_ERROR_LIFETIME_MS === 8_000 &&
    toastLifetimeMs("error") === 8_000 &&
    isErrAnswer("ERR login first") &&
    !isErrAnswer("ERROR tolerated") &&
    toastTone("ERR login first") === "error" &&
    toastTone("OK", true) === "error" &&
    TOAST_VISIBLE_CAP === 5 &&
    JSON.stringify(capToasts([1, 2, 3, 4, 5, 6])) === JSON.stringify([2, 3, 4, 5, 6]) &&
    trimToastLine("  engine-info\n  报告  ") === "engine-info 报告" &&
    trimToastLine("abcdef", 5) === "abcd…",
);





// ---------------------------------------------------------------------------------------------
// Demo world determinism
// ---------------------------------------------------------------------------------------------

const seed = 20260217;
const first = demoWorld(seed);
const second = demoWorld(seed);
const different = demoWorld(seed + 1);

check(
  "demoWorld：同种子完全一致、异种子不同；44×32 网格行列完整；12 个实体",
  JSON.stringify(first) === JSON.stringify(second) &&
    JSON.stringify(first) !== JSON.stringify(different) &&
    first.width === 44 &&
    first.height === 32 &&
    first.tiles.length === first.height &&
    first.tiles.every((row) => row.length === first.width) &&
    first.entities.length === 12,
);

const entityIds = new Set(first.entities.map((entity) => entity.id));
const kinds = new Set(first.entities.map((entity) => entity.kind));
const terrainSet = tileTypes(first);
check(
  "占位地图实体集：id 唯一、12 个实体、包含五种 kind、恰好两座建筑",
  entityIds.size === first.entities.length &&
    first.entities.length === 12 &&
    ["worker", "soldier", "medic", "warehouse", "barracks"].every((kind) => kinds.has(kind)) &&
    first.entities.filter((entity) => isBuildingKind(entity.kind)).length === 2,
  [...kinds].join(","),
);
check(
  "占位地图地形集：六种地形齐备，所有实体都在地图内的可建造陆地上，血条比例在 [0, 1]",
  ["water", "sand", "grass", "forest", "hill", "rock"].every((terrain) => terrainSet.has(terrain)) &&
    first.entities.every(
      (entity) =>
        entity.x > 0 &&
        entity.x < first.width &&
        entity.y > 0 &&
        entity.y < first.height &&
        (terrainAt(first, Math.floor(entity.x), Math.floor(entity.y)) === "grass" ||
          terrainAt(first, Math.floor(entity.x), Math.floor(entity.y)) === "sand") &&
        (entity.healthRatio === undefined ||
          (entity.healthRatio >= 0 && entity.healthRatio <= 1)),
    ),
  [...terrainSet].join(","),
);

// ---------------------------------------------------------------------------------------------
// Server snapshot -> WorldView
// ---------------------------------------------------------------------------------------------

const serverSnapshot = worldViewFromSnapshot({
  tick: 17,
  width: 3,
  height: 2,
  tilePixels: 16,
  tiles: [
    ["grass", "sand", "oc:forest"],
    ["rock", "water", "hill"],
  ],
  entities: [
    { id: 1, kind: "worker", x: 1.5, y: 0.5, player: 1, health: 0.8 },
    { id: 2, kind: "oc:warehouse", x: 2, y: 1, player: 2, health: 1 },
  ],
});
check(
  "服务器快照转换：tick/宽高保留、地形去命名空间、实体字段映射、建筑用内容路径",
  serverSnapshot !== null &&
    serverSnapshot.tick === 17 &&
    serverSnapshot.width === 3 &&
    serverSnapshot.height === 2 &&
    serverSnapshot.tiles[0]?.[0] === "grass" &&
    serverSnapshot.tiles[0]?.[2] === "forest" &&
    serverSnapshot.tiles[1]?.[0] === "rock" &&
    serverSnapshot.entities.length === 2 &&
    (() => {
      const worker = serverSnapshot.entities.find((entity) => entity.id === 1);
      const warehouse = serverSnapshot.entities.find((entity) => entity.id === 2);
      return (
        worker !== undefined &&
        worker.kind === "worker" &&
        worker.x === 1.5 &&
        worker.y === 0.5 &&
        worker.playerId === 1 &&
        worker.healthRatio === 0.8 &&
        warehouse !== undefined &&
        warehouse.kind === "warehouse" &&
        warehouse.playerId === 2
      );
    })(),
  JSON.stringify(serverSnapshot?.tiles),
);

// ---------------------------------------------------------------------------------------------
// Entity status chrome: footprint, construction progress, production queue marker
// ---------------------------------------------------------------------------------------------

/** Whether a `#rrggbb` colour has equal red, green and blue channels (page chrome must be greyscale). */
function isGreyscaleHex(value: string): boolean {
  const match = /^#([0-9a-f]{6})$/u.exec(value.toLowerCase());
  if (match === null) {
    return false;
  }
  const hex = match[1] ?? "";
  return hex.slice(0, 2) === hex.slice(2, 4) && hex.slice(2, 4) === hex.slice(4, 6);
}

const detailedSnapshot = worldViewFromSnapshot({
  tick: 18,
  width: 2,
  height: 2,
  tiles: [
    ["grass", "grass"],
    ["grass", "grass"],
  ],
  entities: [
    {
      id: 1,
      kind: "building.warehouse",
      x: 1,
      y: 1,
      player: 1,
      w: 3,
      h: 3,
      progress: 1,
      queue: 0,
      contents: { gold: 120, wood: 40, ore: 0 },
    },
    {
      id: 2,
      kind: "building.barracks",
      x: 2,
      y: 2,
      player: 1,
      w: 3,
      h: 3,
      progress: 0.42,
      queue: 2,
      queueTicks: 60,
      queueItems: [
        { kind: "unit.melee.t1", ticks: 87, total: 120 },
        { kind: "oc:unit.engineer", ticks: 45, total: 45 },
        { kind: "unit.broken", ticks: 20, total: 0 },
        { kind: "", ticks: 1, total: 1 },
      ],
    },
    {
      id: 3,
      kind: "worker",
      x: 0,
      y: 0,
      player: 1,
      w: 1,
      h: 1,
      progress: 0.8,
      task: "建造中 → 兵工厂",
      orderQueue: ["建造 兵工厂 @ (12,5)", "  ", "修理 #42", "搬运 → 仓库"],
    },
    {
      id: 4,
      kind: "building.turret.t1",
      x: 0,
      y: 2,
      player: 1,
      w: 2,
      h: 2,
      progress: 1,
      attackDamage: 18,
      attackRange: 6,
    },
  ],
});
const detailedWarehouse = detailedSnapshot?.entities.find((entity) => entity.id === 1);
const detailedBarracks = detailedSnapshot?.entities.find((entity) => entity.id === 2);
const detailedWorker = detailedSnapshot?.entities.find((entity) => entity.id === 3);
const detailedTurret = detailedSnapshot?.entities.find((entity) => entity.id === 4);
check(
  "快照实体细节字段：footprint、progress/queue/queueTicks、queueItems 坏项丢弃、orderQueue 去空白、攻击字段",
  detailedWarehouse?.footprint?.width === 3 &&
    detailedWarehouse?.footprint?.height === 3 &&
    detailedBarracks?.progress === 0.42 &&
    detailedBarracks?.queue === 2 &&
    detailedBarracks?.queueTicks === 60 &&
    detailedBarracks?.queueItems?.length === 2 &&
    detailedBarracks.queueItems[0]?.kind === "unit.melee.t1" &&
    detailedBarracks.queueItems[0]?.ticks === 87 &&
    detailedBarracks.queueItems[1]?.kind === "unit.engineer" &&
    detailedWorker?.footprint?.width === 1 &&
    detailedWorker?.footprint?.height === 1 &&
    detailedWorker?.progress === 0.8 &&
    JSON.stringify(detailedWorker?.orderQueue) ===
      JSON.stringify(["建造 兵工厂 @ (12,5)", "修理 #42", "搬运 → 仓库"]) &&
    detailedTurret?.attackDamage === 18 &&
    detailedTurret?.attackRange === 6 &&
    combatLabel(detailedTurret) === "攻击 18 · 射程 6",
  JSON.stringify(detailedBarracks?.queueItems),
);
check(
  "没有武器的旧实体不显示攻击行",
  combatLabel({ id: 5, kind: "building.warehouse", x: 0, y: 0 }) === null &&
    combatLabel({ id: 6, kind: "building.turret.t2", x: 0, y: 0, attackDamage: 32, attackRange: 7.5 }) ===
      "攻击 32 · 射程 7.5",
);
const legacySnapshot = worldViewFromSnapshot({
  tick: 1,
  width: 2,
  height: 2,
  tiles: [
    ["grass", "grass"],
    ["grass", "grass"],
  ],
  entities: [{ id: 7, kind: "warehouse", x: 1, y: 1, player: 1, health: 1 }],
});
const legacyEntity = legacySnapshot?.entities[0];
check(
  "旧快照没有新字段时照常转换（新字段全部可选）",
  legacySnapshot !== null &&
    legacyEntity !== undefined &&
    legacyEntity.footprint === undefined &&
    legacyEntity.progress === undefined &&
    legacyEntity.queue === undefined &&
    legacyEntity.queueItems === undefined &&
    legacyEntity.orderQueue === undefined &&
    legacyEntity.contents === undefined,
  JSON.stringify(legacyEntity),
);
check(
  "进度与队列的展示数学：百分比夹紧、比例夹紧、队列标记与收入后缀的缺省规则",
  formatProgressPercent(0.645) === "65%" &&
    formatProgressPercent(-1) === "0%" &&
    formatProgressPercent(2) === "100%" &&
    progressBarRatio(-2) === 0 &&
    progressBarRatio(2) === 1 &&
    progressBarRatio(Number.NaN) === 0 &&
    queueMarkerText(undefined) === "" &&
    queueMarkerText(-3) === "" &&
    queueMarkerText(2) === "生产中 ×2" &&
    incomeSuffix(undefined) === "" &&
    incomeSuffix(0) === " (+0/分)" &&
    incomeSuffix(-1) === "" &&
    queueProgressRatio({ kind: "unit.melee.t1", ticks: 87, total: 120 }) === 87 / 120 &&
    queueProgressRatio({ kind: "unit.melee.t1", ticks: 200, total: 120 }) === 1 &&
    queueProgressRatio({ kind: "unit.melee.t1", ticks: 1, total: 0 }) === 0 &&
    queueItemNumber(0, 2) === "1/2" &&
    queueItemNumber(5, 2) === "2/2" &&
    queueItemNumber(0, 0) === "0/0",
);
check(
  "尺寸与库存标签：尺寸：W×H（缺 footprint 为 null）、库存行按固定顺序、无 contents 为 null",
  detailedWarehouse !== undefined &&
    sizeLabel(detailedWarehouse) === "尺寸：3×3" &&
    sizeLabel({ id: 9, kind: "warehouse", x: 0, y: 0 }) === null &&
    detailedWarehouse?.contents?.gold === 120 &&
    !("ore" in (detailedWarehouse?.contents ?? {})) &&
    warehouseContentsLabel(detailedWarehouse) === "库存：金 120 · 木 40" &&
    warehouseContentsLabel({ id: 9, kind: "building.warehouse", x: 0, y: 0 }) === null &&
    warehouseContentsLabel({ ...detailedWarehouse, contents: { ore: 7 } }) === "库存：ore 7",
  detailedWarehouse === undefined ? "missing" : String(warehouseContentsLabel(detailedWarehouse)),
);

// The unit cargo line: one item, two items in the same fixed order, no cargo at all, and an item the
// manifest does not know, which must keep its raw path instead of being hidden or renamed.
const cargoNames: Readonly<Record<string, string>> = { gold: "金币", wood: "木材" };
const cargoLabelFor = (path: string): string => cargoNames[path] ?? path;
const cargoUnit: EntityView = {
  id: 42,
  kind: "unit.engineer",
  x: 0,
  y: 0,
  contents: { gold: 7 },
};
check(
  "携带标签：一/两个物品按固定顺序、无货物为 null、清单外物品回退原始路径",
  unitCargoLabel(cargoUnit, cargoLabelFor) === "携带：金币 7" &&
    unitCargoLabel({ ...cargoUnit, contents: { gold: 7, wood: 3 } }, cargoLabelFor) ===
      "携带：金币 7 · 木材 3" &&
    unitCargoLabel({ ...cargoUnit, contents: {} }, cargoLabelFor) === null &&
    unitCargoLabel({ ...cargoUnit, contents: { gold: 0 } }, cargoLabelFor) === null &&
    unitCargoLabel({ ...cargoUnit, contents: { "oc:unobtainium": 2 } }, cargoLabelFor) ===
      "携带：oc:unobtainium 2",
);
check(
  "在建判定只对未完工的建筑成立（单位血量不算在建）",
  detailedBarracks !== undefined &&
    underConstruction(detailedBarracks) === true &&
    detailedWarehouse !== undefined &&
    underConstruction(detailedWarehouse) === false &&
    detailedWorker !== undefined &&
    underConstruction(detailedWorker) === false &&
    underConstruction({ id: 9, kind: "building.warehouse", x: 0, y: 0 }) === false,
);
check(
  "建造进度条是灰阶：浅灰轨道、白色填充、灰阶描边",
  isGreyscaleHex(PROGRESS_TRACK_COLOR) &&
    isGreyscaleHex(PROGRESS_FILL_COLOR) &&
    isGreyscaleHex(PROGRESS_BORDER_COLOR) &&
    PROGRESS_FILL_COLOR === "#ffffff",
  `${PROGRESS_TRACK_COLOR} / ${PROGRESS_FILL_COLOR} / ${PROGRESS_BORDER_COLOR}`,
);

const validSnapshot = {
  tick: 1,
  width: 2,
  height: 2,
  tiles: [
    ["grass", "grass"],
    ["grass", "grass"],
  ],
  entities: [],
};
check(
  "结构错误拒绝整个快照：非对象顶层、行数/行宽不符、非字符串地形、NaN 实体坐标",
  worldViewFromSnapshot(null) === null &&
    worldViewFromSnapshot({ ...validSnapshot, height: 2, tiles: [["grass", "grass"]] }) === null &&
    worldViewFromSnapshot({ ...validSnapshot, tiles: [["grass"], ["grass"]] }) === null &&
    worldViewFromSnapshot({ ...validSnapshot, tiles: [[1, "grass"], ["grass", "grass"]] }) === null &&
    worldViewFromSnapshot({
      ...validSnapshot,
      entities: [{ id: 1, kind: "worker", x: Number.NaN, y: 1 }],
    }) === null,
);
// The world is unbounded: entity positions are signed world coordinates, not indices into a map.
const negativeEntitySnapshot = worldViewFromSnapshot({
  ...validSnapshot,
  originX: -16,
  originY: -16,
  entities: [
    { id: 1, kind: "worker", x: -12.5, y: -3.5 },
    { id: 2, kind: "soldier", x: -100_000.25, y: 500_000.5 },
  ],
});
const negativeWorker = negativeEntitySnapshot?.entities.find((entity) => entity.id === 1);
const farEntity = negativeEntitySnapshot?.entities.find((entity) => entity.id === 2);
check(
  "负实体坐标被接受并原样保留（往返）",
  negativeEntitySnapshot !== null &&
    negativeWorker?.x === -12.5 &&
    negativeWorker.y === -3.5,
  JSON.stringify(negativeWorker),
);
check(
  "远景实体坐标（负数与很大值）不再按地图边界拒绝",
  negativeEntitySnapshot !== null &&
    farEntity?.x === -100_000.25 &&
    farEntity.y === 500_000.5,
  JSON.stringify(farEntity),
);
check(
  "更多结构错误拒绝：非整数/非有限的窗口原点、缺 kind、缺 id；越界血量夹到 [0, 1]",
  worldViewFromSnapshot({ ...validSnapshot, originX: -0.5, originY: 0 }) === null &&
    worldViewFromSnapshot({ ...validSnapshot, originX: 0, originY: 1.5 }) === null &&
    worldViewFromSnapshot({ ...validSnapshot, originX: Number.NaN, originY: 0 }) === null &&
    worldViewFromSnapshot({ ...validSnapshot, originX: 0, originY: Number.POSITIVE_INFINITY }) ===
      null &&
    worldViewFromSnapshot({
      ...validSnapshot,
      entities: [{ id: 1, kind: "", x: 1, y: 1 }],
    }) === null &&
    worldViewFromSnapshot({ ...validSnapshot, entities: [{ kind: "worker", x: 1, y: 1 }] }) ===
      null &&
    worldViewFromSnapshot({
      ...validSnapshot,
      entities: [{ id: 1, kind: "worker", x: 1, y: 1, health: 2 }],
    })?.entities[0]?.healthRatio === 1,
);

const fallbackWorld = snapshotOrDemo(null, seed);
check(
  "转换失败（null）时回退到 demoWorld",
  JSON.stringify(fallbackWorld) === JSON.stringify(demoWorld(seed)),
);
const serverOrDemo = snapshotOrDemo(validSnapshot, seed);
check(
  "有快照时使用服务器世界而不是 demo",
  serverOrDemo.width === 2 && serverOrDemo.height === 2,
  `${serverOrDemo.width}x${serverOrDemo.height}`,
);

// The server's `players` array: parsed row by row, optional for old snapshots, rejected when malformed.
const playersSnapshot = worldViewFromSnapshot({
  ...validSnapshot,
  players: [
    { id: 1, name: "Player 1", gold: 0, wood: 0, anchor: [3, 2] },
    {
      id: 3,
      name: "alice",
      gold: 300,
      wood: 60,
      goldPerMinute: 30,
      woodPerMinute: 20,
      anchor: [-21, -46],
    },
  ],
});
const aliceRow = playersSnapshot === null ? undefined : playersSnapshot.players?.[1];
const malformedRateSnapshot = worldViewFromSnapshot({
  ...validSnapshot,
  players: [
    { id: 3, name: "alice", gold: 300, wood: 60, goldPerMinute: -1, woodPerMinute: "20" },
  ],
});
check(
  "players 行解析：id/name/gold/wood 与可选收入速率、可选 anchor；畸形速率被忽略而非拒绝",
  playersSnapshot !== null &&
    playersSnapshot.players?.length === 2 &&
    aliceRow?.id === 3 &&
    aliceRow.name === "alice" &&
    aliceRow.gold === 300 &&
    aliceRow.wood === 60 &&
    aliceRow?.goldPerMinute === 30 &&
    aliceRow.woodPerMinute === 20 &&
    playersSnapshot?.players?.[0]?.goldPerMinute === undefined &&
    playersSnapshot?.players?.[0]?.anchor?.x === 3 &&
    aliceRow?.anchor?.x === -21 &&
    aliceRow?.anchor?.y === -46 &&
    malformedRateSnapshot !== null &&
    malformedRateSnapshot.players?.[0]?.goldPerMinute === undefined &&
    malformedRateSnapshot.players?.[0]?.woodPerMinute === undefined,
  JSON.stringify(malformedRateSnapshot?.players),
);
check(
  "anchor 畸形时缺席：缺字段、单元素、非整数都不算 anchor；没有 players 的旧快照 players 为 undefined",
  worldViewFromSnapshot({
    ...validSnapshot,
    players: [{ id: 1, name: "Player 1", gold: 0, wood: 0 }],
  })?.players?.[0]?.anchor === undefined &&
    worldViewFromSnapshot({
      ...validSnapshot,
      players: [{ id: 1, name: "Player 1", gold: 0, wood: 0, anchor: [1] }],
    })?.players?.[0]?.anchor === undefined &&
    worldViewFromSnapshot({
      ...validSnapshot,
      players: [{ id: 1, name: "Player 1", gold: 0, wood: 0, anchor: [1.5, 2] }],
    })?.players?.[0]?.anchor === undefined &&
    worldViewFromSnapshot(validSnapshot)?.players === undefined,
);
check(
  "players 结构错误拒绝：非数组、缺 gold、负数或小数金额、空 name",
  worldViewFromSnapshot({ ...validSnapshot, players: { id: 3 } }) === null &&
    worldViewFromSnapshot({
      ...validSnapshot,
      players: [{ id: 3, name: "alice", wood: 60 }],
    }) === null &&
    worldViewFromSnapshot({
      ...validSnapshot,
      players: [{ id: 3, name: "alice", gold: -1, wood: 60 }],
    }) === null &&
    worldViewFromSnapshot({
      ...validSnapshot,
      players: [{ id: 3, name: "alice", gold: 1.5, wood: 60 }],
    }) === null &&
    worldViewFromSnapshot({
      ...validSnapshot,
      players: [{ id: 3, name: "  ", gold: 300, wood: 60 }],
    }) === null,
);

// ---------------------------------------------------------------------------------------------
// Windowed snapshot: unbounded signed coordinates and window translation
// ---------------------------------------------------------------------------------------------

const windowSnapshotPayload = {
  tick: 23,
  originX: -16,
  originY: -16,
  width: 4,
  height: 3,
  tiles: [
    ["grass", "sand", "forest", "rock"],
    ["water", "hill", "grass", "sand"],
    ["rock", "water", "hill", "grass"],
  ],
  entities: [
    { id: 1, kind: "worker", x: -15.5, y: -15.5, player: 1 },
    { id: 2, kind: "soldier", x: 200.5, y: 200.5, player: 2 },
  ],
};
const windowed = worldViewFromSnapshot(windowSnapshotPayload);
check(
  "窗口快照：负原点解析、无地图大小字段、窗口索引映射（含负 floor）、地形绝对坐标、窗外 void、窗外实体保留",
  windowed !== null &&
    windowed.originX === -16 &&
    windowed.originY === -16 &&
    windowed.width === 4 &&
    windowed.height === 3 &&
    !Object.prototype.hasOwnProperty.call(windowed, "mapWidth") &&
    !Object.prototype.hasOwnProperty.call(windowed, "mapHeight") &&
    windowTileIndex(windowed, -16, -16)?.x === 0 &&
    windowTileIndex(windowed, -13, -14)?.x === 3 &&
    windowTileIndex(windowed, -17, -16) === null &&
    windowTileIndex({ ...windowed, originX: 0, originY: 0 }, -1, 0) === null &&
    windowTileIndex({ ...windowed, originX: -1, originY: -1 }, -0.1, -0.1)?.x === 0 &&
    terrainAt(windowed, -16, -16) === "grass" &&
    terrainAt(windowed, -15, -15) === "hill" &&
    terrainAt(windowed, -17, -16) === TERRAIN_VOID &&
    terrainAt(windowed, -16, -17) === TERRAIN_VOID &&
    windowed.entities.length === 2 &&
    entityInWindow(windowed, windowed.entities[0]!) === true &&
    entityInWindow(windowed, windowed.entities[1]!) === false,
  JSON.stringify(windowed),
);
check(
  "窗口原点兼容：任意有符号整数、旧 mapWidth/mapHeight 被忽略、缺 origin 以 (0,0) 为原点；snapshot URL 用负中心",
  worldViewFromSnapshot({ ...windowSnapshotPayload, originX: -100_000, originY: 50_000 })
    ?.originX === -100_000 &&
    (() => {
      const legacySized = worldViewFromSnapshot({
        ...windowSnapshotPayload,
        mapWidth: 512,
        mapHeight: 512,
      });
      return (
        legacySized !== null &&
        legacySized.width === 4 &&
        !Object.prototype.hasOwnProperty.call(legacySized, "mapWidth")
      );
    })() &&
    worldViewFromSnapshot(validSnapshot)?.originX === 0 &&
    snapshotUrl("", { cx: -1_250, cy: -582, r: SNAPSHOT_RADIUS_TILES }) ===
      "/world/snapshot?cx=-1250&cy=-582&r=40" &&
    snapshotUrl("", null) === "/world/snapshot",
);

// ---------------------------------------------------------------------------------------------
// Snapshot projectiles: optional top-level rows, malformed rows dropped, absence tolerated
// ---------------------------------------------------------------------------------------------

const projectileSnapshot = worldViewFromSnapshot({
  ...validSnapshot,
  projectiles: [
    { id: 7, x: -3, y: 4.5, tx: 12, ty: 4.5 },
    { id: 8.5, x: 0, y: 0, tx: 1, ty: 1 }, // non-integer id: the whole row is dropped
    { id: 9, x: 0, y: Number.NaN, tx: 1, ty: 1 }, // non-finite coordinate: row dropped
    { id: 10, x: "2", y: 0, tx: 1, ty: 1 }, // non-number coordinate: row dropped
    { id: 11, x: 0, y: 0, tx: Number.POSITIVE_INFINITY, ty: 1 }, // non-finite target: row dropped
    { x: 0, y: 0, tx: 1, ty: 1 }, // missing id: row dropped
    "nope", // not an object: row dropped
  ],
});
check(
  "顶层 projectiles：合法行保留（负与小数）、坏行逐行丢弃、缺字段/非数组是 undefined、空数组保留",
  projectileSnapshot !== null &&
    projectileSnapshot.projectiles?.length === 1 &&
    JSON.stringify(projectileSnapshot.projectiles[0]) ===
      JSON.stringify({ id: 7, x: -3, y: 4.5, tx: 12, ty: 4.5 }) &&
    worldViewFromSnapshot(validSnapshot)?.projectiles === undefined &&
    worldViewFromSnapshot({ ...validSnapshot, projectiles: { id: 1 } })?.projectiles === undefined &&
    worldViewFromSnapshot({ ...validSnapshot, projectiles: [] })?.projectiles?.length === 0,
);

// ---------------------------------------------------------------------------------------------
// Camera-driven window streaming: signed centres, the duplicate-origin guard and request ordering
// ---------------------------------------------------------------------------------------------

// ---------------------------------------------------------------------------------------------
// Camera-driven window streaming: signed centres, the duplicate-origin guard and request ordering
// ---------------------------------------------------------------------------------------------

const streamBounds = {
  originX: -1_250 - SNAPSHOT_RADIUS_TILES,
  originY: -582 - SNAPSHOT_RADIUS_TILES,
  width: SNAPSHOT_RADIUS_TILES * 2,
  height: SNAPSHOT_RADIUS_TILES * 2,
};

check(
  "窗口边缘触发：距边缘 8 格请求（shouldStreamWindow/shouldRequestWindow 四边），9 格保持；中心原点 centre-radius、定期刷新、有符号边缘距离",
  SNAPSHOT_EDGE_MARGIN_TILES === 8 &&
    SNAPSHOT_RADIUS_TILES === 40 &&
    shouldStreamWindow(streamBounds, { x: streamBounds.originX + 8, y: -590 }) &&
    shouldStreamWindow(streamBounds, { x: -1_250, y: streamBounds.originY + streamBounds.height - 1 - 8 }) &&
    shouldRequestWindow(streamBounds, { x: streamBounds.originX + 8, y: -590 }) &&
    shouldRequestWindow(streamBounds, { x: -1_250, y: streamBounds.originY + 8 }) &&
    !shouldStreamWindow(streamBounds, { x: streamBounds.originX + 9, y: -590 }) &&
    !shouldRequestWindow(streamBounds, { x: -1_250, y: streamBounds.originY + 9 }) &&
    snapshotOrigin({ x: -1_250, y: -582 }, SNAPSHOT_RADIUS_TILES).x === streamBounds.originX &&
    snapshotOrigin({ x: -1_250, y: -582 }, SNAPSHOT_RADIUS_TILES).y === streamBounds.originY &&
    !windowNeedsStream(streamBounds, { x: -1_250, y: -582 }) &&
    windowNeedsStream(streamBounds, { x: -1_250, y: -590 }) &&
    windowCentre(streamBounds).x === -1_250 &&
    windowCentre(streamBounds).y === -582 &&
    windowEdgeDistance({ originX: -10, originY: -10, width: 4, height: 4 }, { x: -11, y: -10 }) === -1 &&
    windowEdgeDistance({ originX: -10, originY: -10, width: 4, height: 4 }, { x: -7, y: -7 }) === 0,
);

const requestTracker = new SnapshotRequestTracker();
const firstRequestId = requestTracker.begin();
const secondRequestId = requestTracker.begin();
check(
  "快照请求 id 单调递增；新回答被接受、乱序旧回答与未知 id（0/未来）被丢弃",
  secondRequestId === firstRequestId + 1 &&
    requestTracker.latest === secondRequestId &&
    requestTracker.accept(secondRequestId) === true &&
    requestTracker.accept(firstRequestId) === false &&
    requestTracker.accept(0) === false &&
    requestTracker.accept(99) === false,
  `${firstRequestId} -> ${secondRequestId}`,
);

// The page keeps the loaded window unless the tracker accepts a non-null response. Model that rule
// with two windows around a negative centre: the newer answer lands first, then the older one.
const oldWindow = {
  originX: -1_100,
  originY: -1_100,
  width: SNAPSHOT_RADIUS_TILES * 2,
  height: SNAPSHOT_RADIUS_TILES * 2,
};
const newWindow = {
  originX: -1_100 - SNAPSHOT_RADIUS_TILES,
  originY: -1_100 - SNAPSHOT_RADIUS_TILES,
  width: SNAPSHOT_RADIUS_TILES * 2,
  height: SNAPSHOT_RADIUS_TILES * 2,
};
let loadedWindow = oldWindow;
if (requestTracker.accept(secondRequestId)) {
  loadedWindow = newWindow;
}
if (requestTracker.accept(firstRequestId)) {
  loadedWindow = oldWindow;
}
check(
  "乱序到达的旧回答不会替换已经加载的新窗口（保留前一个窗口）",
  loadedWindow === newWindow &&
    newWindow.originX ===
      snapshotOrigin({ x: -1_100, y: -1_100 }, SNAPSHOT_RADIUS_TILES).x,
  JSON.stringify(loadedWindow),
);

// ---------------------------------------------------------------------------------------------
// Snapshot interpolation
// ---------------------------------------------------------------------------------------------

const FRAME_WIDTH = 16;
const FRAME_HEIGHT = 16;
const FRAME_GRASS: readonly TerrainId[] = Array.from({ length: FRAME_WIDTH }, () => "grass");

/** A grass-only 16x16 window, large enough for a ten-tile jump between two frames. */
function frameView(entities: readonly EntityView[]): WorldView {
  const tiles = Array.from({ length: FRAME_HEIGHT }, () => FRAME_GRASS);
  return {
    originX: 0,
    originY: 0,
    width: FRAME_WIDTH,
    height: FRAME_HEIGHT,
    tiles,
    entities,
  };
}

function frameEntity(
  id: number,
  x: number,
  y: number,
  kind = "worker",
  playerId = 1,
): EntityView {
  return { id, kind, x, y, playerId };
}

const previousSnapshot: Frame = {
  tick: 100,
  view: frameView([frameEntity(1, 2, 3), frameEntity(2, 5, 5)]),
};
const currentSnapshot: Frame = {
  tick: 110,
  view: frameView([frameEntity(1, 4, 3), frameEntity(3, 9, 9)]),
};

check(
  "插值系数：帧前/到达夹 0，中点 0.5，满间隔及之后夹 1",
  alphaFor(250, 500, 500) === 0 &&
    alphaFor(750, 500, 500) === 0.5 &&
    alphaFor(1_000, 500, 500) === 1 &&
    alphaFor(9_000, 500, 500) === 1,
);

const atStart = interpolate(previousSnapshot, currentSnapshot, 0);
const atMiddle = interpolate(previousSnapshot, currentSnapshot, 0.5);
const atEnd = interpolate(previousSnapshot, currentSnapshot, 1);
const startMover = atStart.entities.find((entity) => entity.id === 1);
const middleMover = atMiddle.entities.find((entity) => entity.id === 1);
const endMover = atEnd.entities.find((entity) => entity.id === 1);
check(
  "插值端点与集合规则：alpha 0 用旧位、0.5 在中点、alpha 1 用新位；新帧实体按新位置出现、旧帧实体消失",
  startMover?.x === 2 &&
    startMover.y === 3 &&
    middleMover?.x === 3 &&
    endMover?.x === 4 &&
    endMover.y === 3 &&
    atMiddle.entities.some((entity) => entity.id === 3 && entity.x === 9 && entity.y === 9) &&
    atMiddle.entities.every((entity) => entity.id !== 2) &&
    atMiddle.entities.length === 2,
  `${JSON.stringify(startMover)}, ${JSON.stringify(endMover)}`,
);

const jumpSnapshot: Frame = {
  tick: 120,
  view: frameView([frameEntity(1, 12, 3)]),
};
const jumped = interpolate(previousSnapshot, jumpSnapshot, 0.5).entities.find(
  (entity) => entity.id === 1,
);
check(
  "跳动超过 4 格（10 格）的实体直接吸附到新帧，不在地图上滑过",
  jumped?.x === 12 && jumped.y === 3,
  JSON.stringify(jumped),
);

const boundarySnapshot: Frame = {
  tick: 130,
  view: frameView([frameEntity(1, 6, 3)]),
};
const atThreshold = interpolate(previousSnapshot, boundarySnapshot, 0.5).entities.find(
  (entity) => entity.id === 1,
);
check(
  "正好移动 4 格仍在中点插值（阈值是“超过 4 格”）",
  MAX_INTERPOLATION_DISTANCE_TILES === 4 && atThreshold?.x === 4 && atThreshold.y === 3,
  JSON.stringify(atThreshold),
);

const kindSnapshot: Frame = {
  tick: 140,
  view: frameView([frameEntity(1, 4, 3, "soldier")]),
};
const kindChanged = interpolate(previousSnapshot, kindSnapshot, 0.5).entities.find(
  (entity) => entity.id === 1,
);
check(
  "kind 变化的实体吸附到新帧",
  kindChanged?.kind === "soldier" && kindChanged.x === 4 && kindChanged.y === 3,
  JSON.stringify(kindChanged),
);

const ownerSnapshot: Frame = {
  tick: 150,
  view: frameView([frameEntity(1, 4, 3, "worker", 2)]),
};
const ownerChanged = interpolate(previousSnapshot, ownerSnapshot, 0.5).entities.find(
  (entity) => entity.id === 1,
);
check(
  "player 变化的实体同样吸附到新帧（吸附到新帧位置，不保留旧帧位置）",
  ownerChanged?.playerId === 2 && ownerChanged.x === 4 && ownerChanged.y === 3,
  JSON.stringify(ownerChanged),
);

// ---------------------------------------------------------------------------------------------
// Atlas parsing (per-material schema: one image file per tile, one vertical strip per entity)
// ---------------------------------------------------------------------------------------------

/** Resolves test image paths the way the loader does: entry base + relative path, no document needed. */
function testResolveUrl(data: AtlasData, imagePath: string): string {
  return `${data.baseUrl}${imagePath}`;
}

/** One fake decoded image per referenced file, always the given size. */
function fakeImages(data: AtlasData, width: number, height: number): Map<string, HTMLImageElement> {
  const images = new Map<string, HTMLImageElement>();
  const paths = new Set<string>();
  for (const tile of data.tiles.values()) {
    paths.add(tile.image);
  }
  for (const entity of data.entities.values()) {
    paths.add(entity.image);
  }
  for (const imagePath of paths) {
    images.set(
      testResolveUrl(data, imagePath),
      { naturalWidth: width, naturalHeight: height } as unknown as HTMLImageElement,
    );
  }
  return images;
}

const parsed = parseAtlas(
  {
    plugin: "oc-fixture",
    tilePixels: 16,
    tiles: {
      grass: { image: "tiles/grass.png", name: "草地" },
      water: { image: "tiles/water.png" },
      traversal: { image: "../evil/atlas.png", name: "越权" },
      absolute: { image: "/absolute.png", name: "绝对" },
      backslash: { image: "tiles\\grass.png", name: "反斜杠" },
      empty: { image: "  ", name: "空" },
      tooLong: { image: `${"a".repeat(255)}/b.png`, name: "过长" },
      notAnObject: 7,
    },
    entities: {
      "unit.melee.t1": {
        image: "entities/unit.melee.t1.png",
        frames: 2,
        fps: 6,
        anchor: [0.5, 0.9],
        scale: 1.5,
        name: "普通",
      },
      worker: { image: "entities/worker.png", frames: 1 },
      zeroFrames: { image: "entities/zero.png", frames: 0 },
      tooManyFrames: { image: "entities/many.png", frames: 17 },
      fractionalFrames: { image: "entities/half.png", frames: 1.5 },
      missingImage: { frames: 2 },
      notAnObject: 7,
    },
  },
  "oc-fixture",
  "/plugin-assets/oc-fixture/",
);

check(
  "材质清单解析：合法地块/实体保留（image、name、frames、fps、anchor、scale），baseUrl 补斜杠",
  parsed.ok &&
    parsed.data.tiles.get("grass")?.image === "tiles/grass.png" &&
    parsed.data.tiles.get("grass")?.name === "草地" &&
    parsed.data.tiles.get("water")?.name === "" &&
    parsed.data.entities.get("unit.melee.t1")?.frames === 2 &&
    parsed.data.entities.get("unit.melee.t1")?.fps === 6 &&
    parsed.data.entities.get("unit.melee.t1")?.anchor[1] === 0.9 &&
    parsed.data.entities.get("unit.melee.t1")?.scale === 1.5 &&
    parsed.data.entities.get("unit.melee.t1")?.name === "普通" &&
    parsed.data.baseUrl === "/plugin-assets/oc-fixture/",
  parsed.ok ? "" : parsed.reason,
);
if (parsed.ok) {
  check(
    "不安全的 image 路径与坏实体被拒绝，且各自给出警告",
    ["traversal", "absolute", "backslash", "empty", "tooLong", "notAnObject"].every(
      (label) =>
        !parsed.data.tiles.has(label) &&
        parsed.warnings.some((warning) => warning.includes(`tiles.${label}`)),
    ) &&
      ["zeroFrames", "tooManyFrames", "fractionalFrames", "missingImage", "notAnObject"].every(
        (label) =>
          !parsed.data.entities.has(label) &&
          parsed.warnings.some((warning) => warning.includes(`entities.${label}`)),
      ),
    [...parsed.data.tiles.keys()].join(","),
  );
  check(
    "images 是去重后的相对路径列表（多个名字共用一个文件时只出现一次）",
    (() => {
      const shared = parseAtlas(
        {
          tiles: { grass: { image: "tiles/shared.png" }, dirt: { image: "tiles/shared.png" } },
          entities: { worker: { image: "tiles/shared.png", frames: 1 } },
        },
        "oc-shared",
        "/plugin-assets/oc-shared/",
      );
      return shared.ok && JSON.stringify(shared.images) === JSON.stringify(["tiles/shared.png"]);
    })(),
    "dedupe",
  );
}

// One file serves as a tile OR as an entity material, never both: entities are collected first, a
// tile reusing an entity image is dropped (that terrain falls back to procedural drawing), and a
// later entity reusing an earlier entity's image is dropped as well.
const clash = parseAtlas(
  {
    tiles: {
      grass: { image: "tiles/grass.png" },
      ghost: { image: "entities/worker.png", name: "占用实体贴图的地块" },
    },
    entities: {
      worker: { image: "entities/worker.png", frames: 2 },
      rider: { image: "entities/worker.png", frames: 1 },
    },
  },
  "oc-clash",
  "/plugin-assets/oc-clash/",
);
check(
  "与实体贴图重复的地块被丢弃（回退程序化绘制），其余地块照常保留",
  clash.ok &&
    clash.data.tiles.has("grass") &&
    !clash.data.tiles.has("ghost") &&
    clash.data.entities.has("worker") &&
    clash.warnings.some((warning) => warning.includes("tiles.ghost")),
  clash.ok ? clash.warnings.join("|") : "parse failed",
);
check(
  "同一清单里两个实体共用一张贴图时，后出现的实体被丢弃，下载列表只有一个文件",
  clash.ok &&
    clash.data.entities.has("worker") &&
    !clash.data.entities.has("rider") &&
    clash.warnings.some((warning) => warning.includes("entities.rider")) &&
    JSON.stringify(clash.images) === JSON.stringify(["tiles/grass.png", "entities/worker.png"]),
  clash.ok ? JSON.stringify(clash.images) : "parse failed",
);

const minimal = parseAtlas(
  {
    tiles: { grass: { image: "tiles/grass.png" } },
    entities: { worker: { image: "entities/worker.png", frames: 4 } },
  },
  "oc-min",
  "/plugin-assets/oc-min",
);
check("缺省 tilePixels 按 32 处理", minimal.ok && minimal.data.tilePixels === 32);
if (minimal.ok) {
  const worker = minimal.data.entities.get("worker");
  check(
    "缺省 fps 是 4、anchor 是脚底 [0.5, 0.75]、scale 是 1、name 为空",
    worker !== undefined &&
      worker.fps === 4 &&
      worker.anchor[0] === 0.5 &&
      worker.anchor[1] === 0.75 &&
      worker.scale === 1 &&
      worker.name === "",
    JSON.stringify(worker),
  );
}

const stripFrames = entityFrameRects(16, 48, 3);
check(
  "地块 rect 是整张图；实体条带切成等高竖片；高度不整除/尺寸/帧数非法返回 null",
  JSON.stringify(tileRect(16, 16)) === JSON.stringify({ x: 0, y: 0, width: 16, height: 16 }) &&
    stripFrames !== null &&
    stripFrames.length === 3 &&
    JSON.stringify(stripFrames[1]) === JSON.stringify({ x: 0, y: 16, width: 16, height: 16 }) &&
    entityFrameRects(16, 50, 3) === null &&
    entityFrameRects(16, 0, 1) === null &&
    entityFrameRects(16, 16, 0) === null &&
    entityFrameRects(16, 16, 2.5) === null,
);

// The manifest may label tiles and entity kinds; a nameless entry falls back to its own id.
const namedFixture = parseAtlas(
  {
    tilePixels: 16,
    tiles: {
      grass: { image: "tiles/grass.png", name: "草地" },
      water: { image: "tiles/water.png" },
    },
    entities: {
      "unit.melee.t1": { image: "entities/melee.png", frames: 2, name: "普通" },
      "unit.engineer": { image: "entities/engineer.png", frames: 1 },
    },
  },
  "oc-named",
  "/plugin-assets/oc-named/",
);
check("带 name 的清单解析成功", namedFixture.ok);
if (namedFixture.ok) {
  // One file per material: four entries, four sources, each frame a rect into its own file.
  const namedAtlas = compositeAtlas(
    buildAtlasSources([namedFixture.data], fakeImages(namedFixture.data, 16, 32), testResolveUrl)
      .sources,
  );
  const meleeEntry = namedAtlas === null ? undefined : lookupAtlasEntity(namedAtlas, "unit.melee.t1");
  check(
    "每张图变成自己的 source：每文件一个 source、地块 rect 是整张图、实体帧是等高竖片、单帧占满整图",
    namedAtlas !== null &&
      namedAtlas.sources.length === 4 &&
      namedAtlas.sources.every((source) => source.plugin === "oc-named") &&
      meleeEntry !== undefined &&
      JSON.stringify(meleeEntry.sprite.frames) ===
        JSON.stringify([
          { x: 0, y: 0, width: 16, height: 16 },
          { x: 0, y: 16, width: 16, height: 16 },
        ]) &&
      lookupAtlasEntity(namedAtlas, "unit.engineer")?.sprite.frames[0]?.height === 32,
    `sources=${namedAtlas?.sources.length}`,
  );
  check(
    "标签表与显示名：带 name 的条目得到名字，无名回退 id，带命名空间的 kind 也能命中",
    namedAtlas !== null &&
      namedAtlas.names.tiles.get("grass") === "草地" &&
      namedAtlas.names.tiles.get("water") === "water" &&
      namedAtlas.names.entities.get("unit.melee.t1") === "普通" &&
      namedAtlas.names.entities.get("unit.engineer") === "unit.engineer" &&
      entityDisplayName(namedAtlas, "oc:unit.melee.t1") === "普通" &&
      entityDisplayName(null, "unit.melee.t1") === "unit.melee.t1" &&
      tileDisplayName(namedAtlas, "oc:grass") === "草地" &&
      tileDisplayName(null, "water") === "water",
  );
}

// A plugin may define items (not art) in the manifest's optional `items` table; the full resource page
// treats those paths as "every resource that exists" even when the player owns none.
const itemFixture = parseAtlas(
  {
    tilePixels: 16,
    tiles: { grass: { image: "tiles/grass.png" } },
    items: {
      gold: "金币",
      wood: { name: "木材" },
      "oc:ore": "矿石",
      blank: "",
    },
  },
  "oc-items",
  "/plugin-assets/oc-items/",
);
check(
  "清单的物品定义支持字符串与 { name } 两种写法，空名称回退到路径",
  itemFixture.ok &&
    itemFixture.data.items.get("gold") === "金币" &&
    itemFixture.data.items.get("wood") === "木材" &&
    itemFixture.data.items.get("oc:ore") === "矿石" &&
    itemFixture.data.items.get("blank") === "",
  itemFixture.ok ? JSON.stringify([...itemFixture.data.items]) : itemFixture.reason,
);
if (itemFixture.ok) {
  const itemAtlas = compositeAtlas(
    buildAtlasSources([itemFixture.data], fakeImages(itemFixture.data, 16, 16), testResolveUrl)
      .sources,
  );
  check(
    "物品标签按清单名称解析，未定义名称时回退到路径（带命名空间也能命中）",
    itemAtlas !== null &&
      itemAtlas.names.items.get("blank") === "blank" &&
      itemDisplayLabel(itemAtlas, "gold") === "金币" &&
      itemDisplayLabel(itemAtlas, "oc:ore") === "矿石" &&
      itemDisplayLabel(itemAtlas, "unknown") === "unknown" &&
      itemDisplayLabel(null, "gold") === "gold",
  );
}

check("非对象清单被拒绝", parseAtlas(null, "p", "/plugin-assets/p/").ok === false);
check(
  "只剩坏路径地块的清单被拒绝",
  parseAtlas({ tiles: { grass: { image: "../up.png" } } }, "p", "/plugin-assets/p/").ok === false,
);
check(
  "没有任何地块、实体或物品的清单被拒绝",
  parseAtlas({ tilePixels: 16 }, "p", "/plugin-assets/p/").ok === false,
);
check(
  "所有 frames 都越界的清单被拒绝",
  parseAtlas(
    { entities: { worker: { image: "entities/worker.png", frames: 99 } } },
    "p",
    "/plugin-assets/p/",
  ).ok === false,
);
check(
  "物品定义单独也能撑起一个清单",
  parseAtlas({ items: { gold: "金币" } }, "p", "/plugin-assets/p/").ok === true,
);

// ---------------------------------------------------------------------------------------------
// Composite atlas: the server's manifest array of per-file sources, last wins
// ---------------------------------------------------------------------------------------------

const firstEntry = parseAtlas(
  {
    plugin: "oc-hello",
    tilePixels: 32,
    tiles: { grass: { image: "tiles/grass.png" }, water: { image: "tiles/water.png" } },
    entities: { worker: { image: "entities/worker.png", frames: 2 } },
  },
  "oc-hello",
  "/plugin-assets/oc-hello/",
);
const secondEntry = parseAtlas(
  {
    plugin: "oc-forest",
    tilePixels: 16,
    tiles: { grass: { image: "tiles/grass.png" }, sand: { image: "tiles/sand.png" } },
    entities: {
      worker: { image: "entities/worker.png", frames: 2, fps: 8 },
      soldier: { image: "entities/soldier.png", frames: 1 },
    },
  },
  "oc-forest",
  "/plugin-assets/oc-forest/",
);
check("两个插件条目都解析成功", firstEntry.ok && secondEntry.ok);

if (firstEntry.ok && secondEntry.ok) {
  const firstSources = buildAtlasSources(
    [firstEntry.data],
    fakeImages(firstEntry.data, 32, 32),
    testResolveUrl,
  );
  const secondSources = buildAtlasSources(
    [secondEntry.data],
    fakeImages(secondEntry.data, 16, 32),
    testResolveUrl,
  );
  const sources: readonly AtlasSource[] = [...firstSources.sources, ...secondSources.sources];
  const merged = mergeLayers(sources);
  const grassLayer = merged.tiles.get("grass");
  const waterLayer = merged.tiles.get("water");
  const sandLayer = merged.tiles.get("sand");
  check(
    "后面的插件覆盖同名地形（last wins），只属于一条的地形保留自己的来源",
    grassLayer !== undefined &&
      sources[grassLayer]?.plugin === "oc-forest" &&
      waterLayer !== undefined &&
      sources[waterLayer]?.plugin === "oc-hello" &&
      sandLayer !== undefined &&
      sources[sandLayer]?.plugin === "oc-forest",
    `grass=${grassLayer} water=${waterLayer} sand=${sandLayer}`,
  );
  const workerLayer = merged.entities.get("worker");
  check(
    "last wins 覆盖同名实体、贡献插件去重、合成图集用胜出插件的文件与 tilePixels",
    workerLayer !== undefined &&
      sources[workerLayer]?.plugin === "oc-forest" &&
      merged.plugins.join(",") === "oc-hello,oc-forest" &&
      compositeAtlas(sources)?.tiles.get("grass")?.source.plugin === "oc-forest" &&
      compositeAtlas(sources)?.tiles.get("grass")?.source.tilePixels === 16 &&
      compositeAtlas(sources)?.entities.get("soldier")?.source.plugin === "oc-forest",
  );
  const single = compositeAtlas(firstSources.sources);
  const partialImages = fakeImages(firstEntry.data, 32, 32);
  partialImages.delete(testResolveUrl(firstEntry.data, "tiles/water.png"));
  const partial = compositeAtlas(
    buildAtlasSources([firstEntry.data], partialImages, testResolveUrl).sources,
  );
  const odd = buildAtlasSources([firstEntry.data], fakeImages(firstEntry.data, 32, 51), testResolveUrl);
  check(
    "失败与坏条目的局部性：单插件合成照常、解码失败的文件只丢引用它的名字、高度不整除丢实体并给出问题行",
    single?.tiles.get("water")?.source.plugin === "oc-hello" &&
      single.entities.get("worker")?.sprite.frames.length === 2 &&
      partial?.tiles.has("grass") === true &&
      partial?.tiles.has("water") === false &&
      partial?.entities.has("worker") === true &&
      odd.sources.every((source) => source.entities.size === 0) &&
      odd.sources.some((source) => source.tiles.size > 0) &&
      odd.problems.length === 1,
    JSON.stringify(odd.problems),
  );
}

check("空数组没有图集，客户端因此回退到程序化绘制", compositeAtlas([]) === null);

// The checked-in classic manifest is the one the game view actually receives. It is being converted
// from the packed atlas to one PNG per material; the raw-count and coverage checks below hold for both
// shapes, and once the v2 entries land on disk the per-material checks (names, safe paths, every
// referenced file present as a real PNG) start asserting against them.
const CLASSIC_MANIFEST_URL = new URL(
  "../../projects/oc/oc-game-server-classic/src/main/resources/assets/manifest.json",
  import.meta.url,
);
let classicManifestText = "";
try {
  classicManifestText = readFileSync(CLASSIC_MANIFEST_URL, "utf8");
} catch (error: unknown) {
  console.error(
    `读取经典插件清单失败：${error instanceof Error ? error.message : String(error)}`,
  );
}
let classicManifest: unknown = null;
try {
  classicManifest = JSON.parse(classicManifestText) as unknown;
} catch (error: unknown) {
  console.error(
    `经典插件清单不是合法 JSON：${error instanceof Error ? error.message : String(error)}`,
  );
}
const classicRoot = recordTable(classicManifest);
const classicTiles = classicRoot === null ? null : recordTable(classicRoot["tiles"]);
const classicEntities = classicRoot === null ? null : recordTable(classicRoot["entities"]);

/**
 * The served `trains` menu the classic armory row carries: the historical melee ladder plus the
 * engineer, exactly the rows the server's recipe list declares. The only TRAIN menu source now —
 * a row without the field offers no menu, so no placeholder list can leak into the panel.
 */
const SERVED_ARMORY_TRAINS: NonNullable<EntityView["trains"]> = [
  { path: "unit.melee.t1", name: "普通", gold: 20, level: 1 },
  { path: "unit.melee.t2", name: "强化", gold: 45, level: 1 },
  { path: "unit.melee.t3", name: "精锐", gold: 90, level: 2 },
  { path: "unit.melee.t4", name: "勇士", gold: 180, level: 2 },
  { path: "unit.melee.t5", name: "战王", gold: 320, level: 3 },
  { path: "unit.engineer", name: "工程师", gold: 50, level: 1 },
];

check(
  "经典清单：24 地块与 37 实体，实体覆盖训练回退目录与经典建筑 kind，地块覆盖全部渲染地形与 void",
  classicTiles !== null &&
    Object.keys(classicTiles).length === 24 &&
    classicEntities !== null &&
    Object.keys(classicEntities).length === 37 &&
    SERVED_ARMORY_TRAINS.every((row) => classicEntities[row.path] !== undefined) &&
    CLASSIC_BUILDING_KINDS.every((kind) => classicEntities[kind] !== undefined) &&
    TERRAIN_IDS.every((terrain) => classicTiles[terrain] !== undefined) &&
    classicTiles[TERRAIN_VOID] !== undefined,
);

const classicAtlas = parseAtlas(classicManifest, "oc-classic", "/plugin-assets/oc-classic/");
if (classicAtlas.ok) {
  check(
    "经典清单的每个 v2 地块都有非空 name",
    classicAtlas.data.tiles.size === (classicTiles === null ? 0 : Object.keys(classicTiles).length) &&
      [...classicAtlas.data.tiles.values()].every((tile) => tile.name.trim() !== ""),
    `${classicAtlas.data.tiles.size} tiles parsed`,
  );
  check(
    "经典清单的每个 v2 实体都有非空 name",
    classicAtlas.data.entities.size ===
      (classicEntities === null ? 0 : Object.keys(classicEntities).length) &&
      [...classicAtlas.data.entities.values()].every((entity) => entity.name.trim() !== ""),
    `${classicAtlas.data.entities.size} entities parsed`,
  );

  // Every referenced file must exist on disk next to the manifest and be a loadable image: either a
  // PNG (89 50 4E 47 signature) or an SVG whose root carries explicit width/height (the intrinsic
  // size browsers need for naturalWidth). A typo'd path or a stub file fails here instead of on the
  // player's screen.
  const assetsDir = new URL(".", CLASSIC_MANIFEST_URL);
  const missing: string[] = [];
  const notImage: string[] = [];
  for (const imagePath of classicAtlas.images) {
    let bytes: Uint8Array;
    try {
      bytes = readFileSync(new URL(imagePath, assetsDir));
    } catch {
      missing.push(imagePath);
      continue;
    }
    const isPng =
      bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47;
    const text = isPng ? "" : new TextDecoder("utf-8").decode(bytes);
    const svg = text.match(/<svg\b[^>]*>/)?.[0] ?? "";
    const dimensioned = /(^|\s)width\s*=\s*"\d+(\.\d+)?"/.test(svg) &&
      /(^|\s)height\s*=\s*"\d+(\.\d+)?"/.test(svg);
    if (!isPng && !dimensioned) {
      notImage.push(imagePath);
    }
  }
  check(
    "经典清单引用的每个材质文件都存在且可加载（PNG 签名，或带显式 width/height 的 SVG 根）",
    missing.length === 0 && notImage.length === 0,
    `missing=[${missing.join(", ")}] notImage=[${notImage.join(", ")}]`,
  );
} else {
  console.info(
    `经典清单还不是 v2 材质格式（${classicAtlas.reason}）；PNG 拆分落地后，v2 地块/实体的名称、路径与文件校验会生效`,
  );
}

// ---- dimension, gateway and unloaded-chunk shadow -----------------------------------------

check(
  "snapshotUrl 把维度编码为 dim 参数，缺省时不带",
  snapshotUrl("", { cx: -3.2, cy: 40, r: 40, dim: "hell" })
    === "/world/snapshot?cx=-4&cy=40&r=40&dim=hell" &&
    snapshotUrl("", { cx: 1, cy: 2, r: 40 }) === "/world/snapshot?cx=1&cy=2&r=40",
  snapshotUrl("", { cx: -3.2, cy: 40, r: 40, dim: "hell" }),
);
const metaWorld = worldViewFromSnapshot({
  tick: 9,
  width: 1,
  height: 1,
  tiles: [["grass"]],
  entities: [
    {
      id: 3,
      kind: "oc:building.hell_gate",
      x: 0.5,
      y: 0.5,
      player: 1,
      gateway: { to: "hell", arrival: [8, 8] },
    },
  ],
  dimension: "hell",
  dimensionName: "地狱",
  dimensions: [
    { key: "overworld", name: "主世界" },
    { key: "hell", name: "地狱" },
  ],
  loadedChunks: [
    [0, 0],
    [3, -2],
  ],
});
check(
  "快照解析维度名册、loadedChunks 与建筑 gateway 目标",
  metaWorld !== null &&
    metaWorld.dimension === "hell" &&
    metaWorld.dimensionName === "地狱" &&
    metaWorld.dimensions?.length === 2 &&
    metaWorld.dimensions[1]?.name === "地狱" &&
    metaWorld.loadedChunks?.length === 2 &&
    metaWorld.loadedChunks[1]?.x === 3 &&
    metaWorld.loadedChunks[1]?.y === -2 &&
    metaWorld.entities[0]?.gateway?.to === "hell" &&
    metaWorld.entities[0]?.gateway?.arrival.x === 8 &&
    metaWorld.entities[0]?.gateway?.arrival.y === 8,
  JSON.stringify(metaWorld?.entities.map((entity) => entity.gateway)),
);
check(
  "旧服务端快照没有维度字段时照常解析，实体没有 gateway",
  (() => {
    const legacy = worldViewFromSnapshot({
      tick: 1,
      width: 1,
      height: 1,
      tiles: [["grass"]],
      entities: [{ id: 5, kind: "building.armory.t1", x: 0.5, y: 0.5, player: 1 }],
    });
    return (
      legacy !== null &&
      legacy.dimension === undefined &&
      legacy.dimensions === undefined &&
      legacy.loadedChunks === undefined &&
      legacy.entities[0]?.gateway === undefined
    );
  })(),
);
check(
  "区块坐标：16 瓦一块，负坐标向下取整",
  chunkOfTile(0, 0).x === 0 &&
    chunkOfTile(15, 16).y === 1 &&
    chunkOfTile(-1, -16).x === -1 &&
    chunkOfTile(-1, -16).y === -1 &&
    CHUNK_SIZE_TILES === 16,
);
check(
  "阴影目标：loadedChunks 缺省不遮，未加载块给满阴影，加载块透明；逐块目标与逐瓦片等价",
  (() => {
    const loaded = new Set([chunkKey(0, 0), chunkKey(2, -1)]);
    return (
      shadowTargetAlpha(undefined, 5, 5) === 0 &&
      shadowTargetAlpha(loaded, 5, 5) === 0 &&
      shadowTargetAlpha(loaded, 20, 0) === SHADOW_ALPHA &&
      shadowTargetAlpha(loaded, 40, -16) === 0 &&
      chunkShadowTargetAlpha(undefined, 0, 0) === 0 &&
      chunkShadowTargetAlpha(loaded, 0, 0) === 0 &&
      chunkShadowTargetAlpha(loaded, 1, 0) === SHADOW_ALPHA
    );
  })(),
);
check(
  "阴影散开：alpha 逐步逼近目标且不越过",
  approachShadowAlpha(0.0, SHADOW_ALPHA, 0.08) === 0.08 &&
    approachShadowAlpha(SHADOW_ALPHA - 0.03, SHADOW_ALPHA, 0.08) === SHADOW_ALPHA &&
    approachShadowAlpha(0.5, 0, 0.08) === 0.42 &&
    approachShadowAlpha(0.05, 0, 0.08) === 0,
);
check(
  "性能：阴影按可见区块聚合（每块一个矩形、裁剪到可见范围），光栅缓存的桶量化与键稳定",
  (() => {
    const spans = chunkSpansInRange({ minX: -3, minY: -3, maxX: 18, maxY: 2 });
    return (
      spans.length === 6 &&
      spans.every((span) => span.maxX - span.minX <= CHUNK_SIZE_TILES) &&
      spans.some((span) => span.minX === -3 && span.maxX === -1) &&
      spans.some((span) => span.minX === 0 && span.maxX === 15) &&
      spans.some((span) => span.minX === 16 && span.maxX === 18) &&
      rasterBucket(0) === 0 &&
      rasterBucket(Number.NaN) === 0 &&
      rasterBucket(32.4) === 32 &&
      rasterBucket(99_999) === RASTER_BUCKET_MAX_PX &&
      rasterCacheKey(3, { x: 0, y: 0, width: 32, height: 32 }, 32) ===
        rasterCacheKey(3, { x: 0, y: 0, width: 32, height: 32 }, 32) &&
      rasterCacheKey(3, { x: 0, y: 0, width: 32, height: 32 }, 32) !==
        rasterCacheKey(4, { x: 0, y: 0, width: 32, height: 32 }, 32) &&
      RASTER_CACHE_MAX_ENTRIES === 512
    );
  })(),
  JSON.stringify(chunkSpansInRange({ minX: -3, minY: -3, maxX: 18, maxY: 2 }).map((span) => span.minX)),
);

const goodEntry = readManifestEntry({
  plugin: "oc-hello",
  baseUrl: "/plugin-assets/oc-hello/",
});
check(
  "manifest 条目校验：合法条目给出 plugin/baseUrl，畸形条目全部被跳过并给出警告，同源绝对路径被接受",
  goodEntry.ok &&
    goodEntry.entry.plugin === "oc-hello" &&
    goodEntry.entry.baseUrl === "/plugin-assets/oc-hello/" &&
    [
      readManifestEntry(null),
      readManifestEntry("atlas.png"),
      readManifestEntry({}),
      readManifestEntry({ baseUrl: "/plugin-assets/x/" }),
      readManifestEntry({ plugin: "x" }),
      readManifestEntry({ plugin: "x", baseUrl: "" }),
      readManifestEntry({ plugin: "x", baseUrl: 7 }),
      readManifestEntry({ plugin: "x", baseUrl: "plugin-assets/x/" }),
      readManifestEntry({ plugin: "x", baseUrl: "http://evil.example/x/" }),
      readManifestEntry({ plugin: "x", baseUrl: "//evil.example/x/" }),
    ].every((result) => !result.ok && result.warning !== "") &&
    readManifestEntry({ plugin: "x", baseUrl: "/plugin-assets/x/" }).ok === true,
);

check(
  "HUD 图集标签：procedural 兜底、插件 id 列表、超过三个显示数量、重复只算一次",
  atlasLabel([]) === "procedural" &&
    atlasLabel(["oc-hello"]) === "atlas · oc-hello" &&
    atlasLabel(["oc-hello", "oc-forest", "oc-debug"]) === "atlas · oc-hello, oc-forest, oc-debug" &&
    atlasLabel(["a", "b", "c", "d"]) === "atlas · 4 plugins" &&
    atlasLabel(["a", "a", "b"]) === "atlas · a, b",
);

// ---------------------------------------------------------------------------------------------
// Fallback palette
// ---------------------------------------------------------------------------------------------

const primaryTerrains = ["water", "sand", "grass", "forest", "hill", "rock"];
const missingColors = primaryTerrains.filter((terrain) => TERRAIN_COLORS[terrain] === undefined);
check("六种主地形都有颜色", missingColors.length === 0, missingColors.join(", "));
const primaryColors = primaryTerrains.map((terrain) => TERRAIN_COLORS[terrain]);
check(
  "六种主地形颜色互不相同",
  new Set(primaryColors).size === primaryTerrains.length,
  primaryColors.join(", "),
);
check("void 有颜色", TERRAIN_COLORS["void"] !== undefined);
check(
  "TERRAIN_IDS 里每个地形都有颜色",
  TERRAIN_IDS.every((terrain) => TERRAIN_COLORS[terrain] !== undefined),
);
check(
  "未知地形得到稳定且不同的颜色",
  terrainColor("unobtainium") === terrainColor("unobtainium") &&
    terrainColor("unobtainium") !== terrainColor("water"),
  terrainColor("unobtainium"),
);

// ---------------------------------------------------------------------------------------------
// Ownership rings
// ---------------------------------------------------------------------------------------------

check(
  "归属颜色：我的是蓝色 #3b82f6",
  ownershipColor(3, 3) === OWNERSHIP_MINE_COLOR && OWNERSHIP_MINE_COLOR === "#3b82f6",
  ownershipColor(3, 3),
);
check(
  "归属颜色：敌人是红色 #ef4444",
  ownershipColor(2, 3) === OWNERSHIP_ENEMY_COLOR && OWNERSHIP_ENEMY_COLOR === "#ef4444",
  ownershipColor(2, 3),
);
check(
  "归属颜色：没有 owner 或 owner 为 0 是灰色 #9ca3af",
  ownershipColor(undefined, 3) === OWNERSHIP_NEUTRAL_COLOR &&
    ownershipColor(0, 3) === OWNERSHIP_NEUTRAL_COLOR &&
    OWNERSHIP_NEUTRAL_COLOR === "#9ca3af",
);
check(
  "归属颜色：没有 myPlayerId 时所有实体都是灰色（演示世界）",
  [1, 2, 3, undefined].every((playerId) => ownershipColor(playerId, null) === OWNERSHIP_NEUTRAL_COLOR),
);
check(
  "己方单位额外标记：细亮白描边（无彩色，符合灰阶面板规则）",
  ownerBadge({ id: 30, kind: "unit.melee.t1", x: 0, y: 0, playerId: 1 }, 1) === "own-outline" &&
    OWN_UNIT_OUTLINE_COLOR === "rgba(255, 255, 255, 0.95)",
  OWN_UNIT_OUTLINE_COLOR,
);
check(
  "敌方单位额外标记：脚下红色底环；中立单位无额外标记",
  ownerBadge({ id: 31, kind: "unit.melee.t5", x: 0, y: 0, playerId: 2 }, 1) === "enemy-base" &&
    ENEMY_UNIT_BASE_COLOR.startsWith("rgba(239, 68, 68") &&
    ownerBadge({ id: 32, kind: "unit.melee.t1", x: 0, y: 0 }, 1) === "none",
  `${ownerBadge({ id: 31, kind: "unit.melee.t5", x: 0, y: 0, playerId: 2 }, 1)} / ${ENEMY_UNIT_BASE_COLOR}`,
);
check(
  "建筑不画白描边或红底环，保留原有归属环（提示框仍加粗显示归属）",
  ownerBadge(
    { id: 33, kind: "building.warehouse", x: 0, y: 0, playerId: 1, footprint: { width: 3, height: 3 } },
    1,
  ) === "none" &&
    ownerBadge(
      { id: 34, kind: "building.warehouse", x: 0, y: 0, playerId: 2, footprint: { width: 3, height: 3 } },
      1,
    ) === "none",
);
check(
  "选中环颜色保持不变，仍是金色 #ffd166",
  SELECTION_COLOR === "#ffd166",
  SELECTION_COLOR,
);
check(
  "HUD 归属标签：我 / 敌 / 中立",
  ownershipOf(3, 3) === "mine" &&
    ownershipTag("mine") === "我" &&
    ownershipOf(2, 3) === "enemy" &&
    ownershipTag("enemy") === "敌" &&
    ownershipOf(undefined, 3) === "neutral" &&
    ownershipTag("neutral") === "中立",
);

// ---------------------------------------------------------------------------------------------
// Gameplay command builders
// ---------------------------------------------------------------------------------------------

/** Whether the builder throws an Error carrying a Chinese explanation, which is the UI contract. */
function throwsChinese(action: () => unknown): boolean {
  try {
    action();
    return false;
  } catch (error: unknown) {
    return error instanceof Error && /[\u4e00-\u9fff]/u.test(error.message);
  }
}

// Golden lines: exactly the grammar documented in protocol/oc-protocol.md, one check per builder family.
check(
  "命令构建器生成协议文档里的精确行（WHOAMI/MOVE/ATTACK/ATTACKMOVE/STOP/BUILD/TRAIN）",
  whoAmI() === "WHOAMI" &&
    orderMove(7, 12, 8) === "ORDER MOVE 7 12 8" &&
    orderAttack(7, 42) === "ORDER ATTACK 7 42" &&
    orderAttackMove(7, 3, 4) === "ORDER ATTACKMOVE 7 3 4" &&
    orderStop(7) === "ORDER STOP 7" &&
    build(9, "building.armory.t1", 20, 40) === "BUILD 9 building.armory.t1 20 40" &&
    train(42, "unit.melee.t1") === "TRAIN 42 unit.melee.t1",
  `${orderMove(7, 12, 8)} / ${build(9, "building.armory.t1", 20, 40)}`,
);

check(
  "构建器本地拒绝非法输入：非正整数 id、非有限坐标、含空白的内容路径",
  [0, -1, 1.5, Number.NaN, Number.POSITIVE_INFINITY, Number.MAX_SAFE_INTEGER + 1].every((id) =>
    throwsChinese(() => orderStop(id)),
  ) &&
    [0, -2, 3.25, Number.NaN].every((id) => throwsChinese(() => orderAttack(7, id))) &&
    [Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY].every(
      (value) =>
        throwsChinese(() => orderMove(7, value, 1)) &&
        throwsChinese(() => orderMove(7, 1, value)) &&
        throwsChinese(() => orderAttackMove(7, value, 1)) &&
        throwsChinese(() => build(9, "building.armory.t1", value, 2)) &&
        throwsChinese(() => build(9, "building.armory.t1", 2, value)),
    ) &&
    ["", " building.armory.t1", "building.armory.t1 extra", "building\tarmory", "unit.\nengineer"].every(
      (path) => throwsChinese(() => build(9, path, 1, 2)) && throwsChinese(() => train(42, path)),
    ),
);

check("parseWhoAmI 接受 OK player 7", parseWhoAmI("OK player 7") === 7);
check(
  "parseWhoAmI 拒绝所有非规范回答",
  [
    "",
    "player 7",
    "OK 7",
    "OK player",
    "OK player seven",
    "OK player 7.0",
    "OK player 0",
    "OK player -3",
    "OK player 07",
    "OK player 7 extra",
    "ERR login first",
    "OK player 9007199254740993",
  ].every((answer) => parseWhoAmI(answer) === null),
  [parseWhoAmI("OK player 0"), parseWhoAmI("OK player 7.0")].join(", "),
);

check(
  "经典目录固定：served trains 目录六个条目、经典建筑 kind 都能生成 BUILD/TRAIN 行（BUILD 菜单本身只由快照 buildable 驱动）",
  SERVED_ARMORY_TRAINS.map((row) => row.path).join(",") ===
    "unit.melee.t1,unit.melee.t2,unit.melee.t3,unit.melee.t4,unit.melee.t5,unit.engineer" &&
    SERVED_ARMORY_TRAINS.every((row) => train(1, row.path) === `TRAIN 1 ${row.path}`) &&
    CLASSIC_BUILDING_KINDS.every((path) => build(1, path, 0, 0) === `BUILD 1 ${path} 0 0`),
);

// Shift-click queueing (item 27): the plan keeps the mode armed and every line carries the queue token.
const shiftPlan = buildPlacementBatch([11, 12, 13], "building.turret.t1", 5, 6, true);
check(
  "Shift 点击生成三条带 queue 的 BUILD，并保持放置模式",
  shiftPlan.keepArmed === true &&
    shiftPlan.lines.length === 3 &&
    shiftPlan.lines[0] === "BUILD 11 building.turret.t1 5 6 queue" &&
    shiftPlan.lines[1] === "BUILD 12 building.turret.t1 5 6 queue" &&
    shiftPlan.lines[2] === "BUILD 13 building.turret.t1 5 6 queue",
  shiftPlan.lines.join(" | "),
);
const plainPlan = buildPlacementBatch([11], "building.turret.t1", 5, 6, false);
check(
  "普通点击只生成一条不带 queue 的 BUILD，并清除放置模式",
  plainPlan.keepArmed === false &&
    plainPlan.lines.length === 1 &&
    plainPlan.lines[0] === "BUILD 11 building.turret.t1 5 6",
  plainPlan.lines.join(" | "),
);
check(
  "buildBatch 的 queue 参数只追加标记，不改变数量或工兵顺序",
  JSON.stringify(buildBatch([3, 1, 2], "building.warehouse", 4, 5, true)) ===
    JSON.stringify([
      "BUILD 3 building.warehouse 4 5 queue",
      "BUILD 1 building.warehouse 4 5 queue",
      "BUILD 2 building.warehouse 4 5 queue",
    ]),
  buildBatch([3, 1, 2], "building.warehouse", 4, 5, true).join(" | "),
);
// ---------------------------------------------------------------------------------------------
// Heartbeat
// ---------------------------------------------------------------------------------------------

check("心跳间隔常量是 20 秒", HEARTBEAT_MS === 20_000, String(HEARTBEAT_MS));
check(
  "心跳只在连接成功后发送，且同一时刻最多一个 PING 在途",
  shouldHeartbeat({ connected: false, pingInFlight: false }) === false &&
    shouldHeartbeat({ connected: true, pingInFlight: false }) === true &&
    shouldHeartbeat({ connected: true, pingInFlight: true }) === false,
);

// ---------------------------------------------------------------------------------------------
// Control groups
// ---------------------------------------------------------------------------------------------

/** Whether an action rejects with the documented RangeError for a group outside 1..9. */
function rejectsBadGroup(action: () => void): boolean {
  try {
    action();
    return false;
  } catch (error: unknown) {
    return error instanceof RangeError;
  }
}

let groups: ControlGroups = EMPTY_CONTROL_GROUPS;
check(
  "编组基础：初始为空、1..9 合法、超范围 RangeError；assign 替换并只保留正整数去重、add 追加、空编队可绑定",
  CONTROL_GROUP_MAX === 9 &&
    recallControlGroup(groups, 1).length === 0 &&
    describeControlGroup(groups, 9) === "编队 9 为空" &&
    [0, 10, 1.5, Number.NaN].every((group) =>
      rejectsBadGroup(() => recallControlGroup(groups, group)),
    ) &&
    (() => {
      groups = assignControlGroup(groups, 1, [3, 5, 3, 0, -1, 3.5]);
      const assigned = JSON.stringify(recallControlGroup(groups, 1)) === JSON.stringify([3, 5]) &&
        describeControlGroup(groups, 1) === "编队 1：2 个实体";
      groups = addControlGroup(groups, 1, [5, 7, 7]);
      const added = JSON.stringify(recallControlGroup(groups, 1)) === JSON.stringify([3, 5, 7]);
      groups = assignControlGroup(groups, 2, []);
      const empty = describeControlGroup(groups, 2) === "编队 2 为空";
      return assigned && added && empty;
    })(),
);
check(
  "编组 prune：删除已不存在的 id，保留顺序与空编队，未变化返回原对象；recall 返回副本",
  (() => {
    groups = pruneControlGroups(groups, [3, 7]);
    const recalled = recallControlGroup(groups, 1) as number[];
    recalled.push(99);
    return (
      JSON.stringify(recallControlGroup(groups, 1)) === JSON.stringify([3, 7]) &&
      describeControlGroup(groups, 2) === "编队 2 为空" &&
      pruneControlGroups(groups, [3, 7]) === groups &&
      JSON.stringify(recallControlGroup(groups, 1)) === JSON.stringify([3, 7])
    );
  })(),
);
check(
  "非法编号赋给 assign/add 会抛 RangeError，而不是静默写坏映射",
  [0, 10, 2.5].every((group) =>
    rejectsBadGroup(() => assignControlGroup(groups, group, [1])) &&
    rejectsBadGroup(() => addControlGroup(groups, group, [1])),
  ),
);

// ---------------------------------------------------------------------------------------------
// Build placement ghost: footprint lookup, footprint tiles, occupancy, reach and state precedence
// ---------------------------------------------------------------------------------------------

const ONE_TILE_FOOTPRINT: Footprint = { width: 1, height: 1 };

/** A 3x3 classic warehouse whose footprint starts at (5,5), so its centre is (6.5,6.5). */
const PLACEMENT_BUILDING: EntityView = {
  id: 1,
  kind: "building.warehouse",
  x: 6.5,
  y: 6.5,
  playerId: 1,
  footprint: { width: 3, height: 3 },
};

/** An own engineer standing at (1.4, 1.7), so its tile is the floor (1,1). */
const PLACEMENT_ENGINEER: EntityView = {
  id: 2,
  kind: "unit.engineer",
  x: 1.4,
  y: 1.7,
  playerId: 1,
  footprint: { width: 1, height: 1 },
};

/** A grass 8x8 window with one water tile at (2,2), a 3x3 warehouse at (5,5) and the engineer. */
function placementWorld(): WorldView {
  const tiles: TerrainId[][] = Array.from({ length: 8 }, () =>
    Array.from({ length: 8 }, () => "grass" as TerrainId),
  );
  const waterRow = tiles[2];
  if (waterRow !== undefined) {
    waterRow[2] = "water";
  }
  return {
    originX: 0,
    originY: 0,
    width: 8,
    height: 8,
    tiles,
    entities: [PLACEMENT_BUILDING, PLACEMENT_ENGINEER],
  };
}

const placementFixture = placementWorld();

check(
  "足迹查表与展开：哨塔 2×2 其余 3×3（含命名空间）、未知 kind 回退 3×3、瓦片列表行优先含负原点与零尺寸",
  CLASSIC_BUILDING_KINDS.every((path) => {
    const footprint = footprintForKind(path);
    return path.startsWith("building.turret.")
      ? footprint.width === 2 && footprint.height === 2
      : footprint.width === 3 && footprint.height === 3;
  }) &&
    footprintForKind("oc:building.warehouse").height === 3 &&
    DEFAULT_BUILDING_FOOTPRINT.width === 3 &&
    footprintForKind("").width === 3 &&
    JSON.stringify(footprintTiles({ x: 2, y: 3 }, { width: 2, height: 2 })) ===
      JSON.stringify([
        { x: 2, y: 3 },
        { x: 3, y: 3 },
        { x: 2, y: 4 },
        { x: 3, y: 4 },
      ]) &&
    footprintTiles({ x: 0, y: 0 }, { width: 0, height: 3 }).length === 0,
);
check(
  "实体足迹占用：3×3 建筑覆盖右下角不越界，单位只占脚下 1 格（小数与负坐标按 floor）",
  entityFootprint(PLACEMENT_BUILDING).width === 3 &&
    entityFootprintCovers(PLACEMENT_BUILDING, 5, 5) &&
    entityFootprintCovers(PLACEMENT_BUILDING, 7, 7) &&
    !entityFootprintCovers(PLACEMENT_BUILDING, 8, 5) &&
    !entityFootprintCovers(PLACEMENT_BUILDING, 4, 5) &&
    entityFootprintCovers(PLACEMENT_ENGINEER, 1, 1) &&
    !entityFootprintCovers(PLACEMENT_ENGINEER, 2, 1) &&
    entityFootprintCovers({ ...PLACEMENT_ENGINEER, x: -0.2, y: -1.1 }, -1, -2) &&
    !entityFootprintCovers({ ...PLACEMENT_ENGINEER, x: -0.2, y: -1.1 }, 0, -2),
);
check(
  "占用与工兵范围：占用来自实体列表（旧实体一格）；16 格可达 17 格超出（负坐标同样）；足迹边缘算 0/1 格；幽灵状态优先级 blocked > out-of-reach > legal",
  tileOccupiedByEntities(placementFixture.entities, 6, 6) &&
    !tileOccupiedByEntities(placementFixture.entities, 0, 0) &&
    tileOccupiedByEntities([{ id: 9, kind: "warehouse", x: 4, y: 6, playerId: 1 }], 4, 6) &&
    !tileOccupiedByEntities([{ id: 9, kind: "warehouse", x: 4, y: 6, playerId: 1 }], 5, 6) &&
    MAX_BUILD_REACH_TILES === 16 &&
    buildReachDistance({ x: 0, y: 0 }, { x: 16, y: 0 }, ONE_TILE_FOOTPRINT) === 16 &&
    isWithinBuildReach({ x: 0, y: 0 }, { x: 16, y: 0 }, ONE_TILE_FOOTPRINT) &&
    !isWithinBuildReach({ x: 0, y: 0 }, { x: 17, y: 0 }, ONE_TILE_FOOTPRINT) &&
    isWithinBuildReach({ x: -20, y: 0 }, { x: -4, y: 0 }, ONE_TILE_FOOTPRINT) &&
    !isWithinBuildReach({ x: -20, y: 0 }, { x: -3, y: 0 }, ONE_TILE_FOOTPRINT) &&
    buildReachDistance({ x: 1, y: 1 }, { x: 0, y: 0 }, { width: 3, height: 3 }) === 0 &&
    buildReachDistance({ x: 3, y: 3 }, { x: 0, y: 0 }, { width: 3, height: 3 }) === 1 &&
    combinePlacementStates(["legal", "out-of-reach"]) === "out-of-reach" &&
    combinePlacementStates(["out-of-reach", "blocked", "legal"]) === "blocked" &&
    combinePlacementStates([]) === "legal",
);

const legalPreview = buildPlacementPreview(
  placementFixture,
  "building.warehouse",
  { x: 1, y: 5 },
  PLACEMENT_ENGINEER,
);
const waterPreview = buildPlacementPreview(
  placementFixture,
  "building.warehouse",
  { x: 2, y: 2 },
  PLACEMENT_ENGINEER,
);
const occupiedPreview = buildPlacementPreview(
  placementFixture,
  "building.warehouse",
  { x: 5, y: 5 },
  PLACEMENT_ENGINEER,
);
check(
  "工地预览：合法 9 格全 legal；水域整块报地形不可建；实体占用整块报位置被占用（状态行原因文本固定）",
  legalPreview.state === "legal" &&
    legalPreview.tiles.length === 9 &&
    legalPreview.tiles.every((tile) => tile.state === "legal") &&
    placementReasonText("legal") === "可建造" &&
    waterPreview.state === "blocked" &&
    waterPreview.reason === "blocked-terrain" &&
    placementReasonText("blocked-terrain") === "地形不可建" &&
    occupiedPreview.state === "blocked" &&
    occupiedPreview.reason === "blocked-occupied" &&
    occupiedPreview.tiles.every((tile) => tile.state === "blocked") &&
    placementReasonText("blocked-occupied") === "位置被占用",
);
const plannedPreview = buildPlacementPreview(
  placementFixture,
  "building.warehouse",
  { x: 1, y: 5 },
  PLACEMENT_ENGINEER,
  [{ x: 3, y: 5, width: 3, height: 3 }],
);
check(
  "刚下达但快照里还没有的工地也算阻挡（Q2 的第二个及以后工地）",
  plannedPreview.state === "blocked" &&
    plannedPreview.reason === "blocked-planned" &&
    placementReasonText(plannedPreview.reason) === "与刚下达的工地重叠" &&
    plannedPreview.tiles.some((tile) => tile.reason === "blocked-planned") &&
    plannedPreview.tiles.some((tile) => tile.state === "legal"),
  JSON.stringify(plannedPreview.tiles.find((tile) => tile.reason === "blocked-planned")),
);

/** An own unfinished armory site at (5,5), i.e. exactly the footprint a second engineer aims at. */
const joinSite: EntityView = {
  id: 30,
  kind: "building.armory.t1",
  x: 6.5,
  y: 6.5,
  playerId: 1,
  footprint: { width: 3, height: 3 },
  progress: 0.4,
};
const joinWorld: WorldView = {
  ...placementFixture,
  entities: [joinSite, PLACEMENT_ENGINEER],
};
const joinPreview = buildPlacementPreview(
  joinWorld,
  "building.armory.t1",
  { x: 5, y: 5 },
  PLACEMENT_ENGINEER,
);
const farJoinPreview = buildPlacementPreview(
  joinWorld,
  "building.armory.t1",
  { x: 5, y: 5 },
  { id: 31, kind: "unit.engineer", x: 40.5, y: 40.5, playerId: 1, footprint: { width: 1, height: 1 } },
);
check(
  "可加入工地：自己的同类型未完工工地 legal 并带 joinSiteId；敌方/完工/异类不算；超 16 格报超出范围",
  joinPreview.state === "legal" &&
    joinPreview.joinSiteId === joinSite.id &&
    joinPreview.tiles.every((tile) => tile.state === "legal") &&
    buildPlacementPreview(
      { ...joinWorld, entities: [{ ...joinSite, playerId: 2 }, PLACEMENT_ENGINEER] },
      "building.armory.t1",
      { x: 5, y: 5 },
      PLACEMENT_ENGINEER,
    ).joinSiteId === null &&
    buildPlacementPreview(
      { ...joinWorld, entities: [{ ...joinSite, progress: 1 }, PLACEMENT_ENGINEER] },
      "building.armory.t1",
      { x: 5, y: 5 },
      PLACEMENT_ENGINEER,
    ).joinSiteId === null &&
    buildPlacementPreview(joinWorld, "building.mine.t1", { x: 5, y: 5 }, PLACEMENT_ENGINEER)
      .joinSiteId === null &&
    farJoinPreview.state === "out-of-reach" &&
    farJoinPreview.joinSiteId === joinSite.id,
);
const windowPreview = buildPlacementPreview(
  placementFixture,
  "building.warehouse",
  { x: 6, y: 0 },
  PLACEMENT_ENGINEER,
);
check(
  "窗口边缘阻挡：足迹越过已载入窗口时报超出已载入窗口",
  windowPreview.state === "blocked" &&
    windowPreview.reason === "blocked-window" &&
    placementReasonText("blocked-window") === "超出已载入窗口",
);
const farEngineer: EntityView = {
  id: 3,
  kind: "unit.engineer",
  x: 40.5,
  y: 40.5,
  playerId: 1,
  footprint: { width: 1, height: 1 },
};
const reachPreview = buildPlacementPreview(
  placementFixture,
  "building.warehouse",
  { x: 1, y: 5 },
  farEngineer,
);
check(
  "超出射程：所有可建格是 out-of-reach，状态行报 16 格",
  reachPreview.state === "out-of-reach" &&
    reachPreview.reason === "out-of-reach" &&
    reachPreview.tiles.every((tile) => tile.state === "out-of-reach") &&
    placementReasonText(reachPreview.reason) === "超出工兵范围 16 格",
  `${reachPreview.state}/${reachPreview.reason}`,
);
const blockedFarPreview = buildPlacementPreview(
  placementFixture,
  "building.warehouse",
  { x: 2, y: 2 },
  farEngineer,
);
check(
  "状态优先级在整块工地上生效：地形阻挡压过超出射程",
  blockedFarPreview.state === "blocked" &&
    blockedFarPreview.reason === "blocked-terrain",
  `${blockedFarPreview.state}/${blockedFarPreview.reason}`,
);

/** A 4x4 world whose (0,0) tile is both water and under a 3x3 building, for reason precedence. */
const precedenceTiles: TerrainId[][] = Array.from({ length: 4 }, (_, y) =>
  Array.from({ length: 4 }, (_, x) => (x === 0 && y === 0 ? "water" : "grass") as TerrainId),
);
const precedenceWorld: WorldView = {
  originX: 0,
  originY: 0,
  width: 4,
  height: 4,
  tiles: precedenceTiles,
  entities: [
    {
      id: 9,
      kind: "building.warehouse",
      x: 0,
      y: 0,
      playerId: 1,
      footprint: { width: 3, height: 3 },
    },
  ],
};
const occupiedAndWater = buildPlacementPreview(
  precedenceWorld,
  "building.warehouse",
  { x: 0, y: 0 },
  farEngineer,
);
const windowEdge = buildPlacementPreview(
  precedenceWorld,
  "building.warehouse",
  { x: 2, y: 2 },
  farEngineer,
);
check(
  "每格原因顺序：窗口 > 占用 > 地形",
  occupiedAndWater.tiles[0]?.reason === "blocked-occupied" &&
    windowEdge.tiles.some((tile) => tile.reason === "blocked-window") &&
    !windowEdge.tiles.some((tile) => tile.reason === "blocked-terrain"),
  `${occupiedAndWater.tiles[0]?.reason} / ${windowEdge.tiles.map((tile) => tile.reason).join(",")}`,
);

/** An 8x8 grass window with one 3x3 warehouse at the given origin and an engineer five tiles away. */
function flushPlacementWorld(
  originX: number,
  originY: number,
  buildingOriginX = 0,
  buildingOriginY = 0,
): WorldView {
  const tiles: TerrainId[][] = Array.from({ length: 8 }, () =>
    Array.from({ length: 8 }, () => "grass" as TerrainId),
  );
  return {
    originX,
    originY,
    width: 8,
    height: 8,
    tiles,
    entities: [
      {
        id: 21,
        kind: "building.warehouse",
        x: buildingOriginX + 1.5,
        y: buildingOriginY + 1.5,
        playerId: 1,
        footprint: { width: 3, height: 3 },
        progress: 1,
      },
      {
        id: 22,
        kind: "unit.engineer",
        x: originX + 7.5,
        y: originY + 7.5,
        playerId: 1,
        footprint: { width: 1, height: 1 },
      },
    ],
  };
}

const flushWorld = flushPlacementWorld(0, 0);
const flushRightPreview = buildPlacementPreview(
  flushWorld,
  "building.warehouse",
  { x: 3, y: 0 },
  flushWorld.entities[1]!,
);
const overlappingRightPreview = buildPlacementPreview(
  flushWorld,
  "building.warehouse",
  { x: 2, y: 0 },
  flushWorld.entities[1]!,
);
check(
  "客户端幽灵：3×3 建筑紧贴另一座 3×3 的右边（origin 3）是合法，退一格（origin 2）被占用",
  flushRightPreview.state === "legal" &&
    flushRightPreview.tiles.every((tile) => tile.state === "legal") &&
    overlappingRightPreview.state === "blocked" &&
    overlappingRightPreview.reason === "blocked-occupied",
);
const negativeFlushWorld = flushPlacementWorld(-4, -4, -3, -3);
const negativeFlushPreview = buildPlacementPreview(
  negativeFlushWorld,
  "building.warehouse",
  { x: 0, y: -3 },
  negativeFlushWorld.entities[1]!,
);
const negativeOverlapPreview = buildPlacementPreview(
  negativeFlushWorld,
  "building.warehouse",
  { x: -1, y: -3 },
  negativeFlushWorld.entities[1]!,
);
check(
  "客户端幽灵在负坐标上同样按覆盖格判定；幽灵颜色是画布内容：合法绿、阻挡红、超距琥珀",
  negativeFlushPreview.state === "legal" &&
    negativeFlushPreview.tiles.every((tile) => tile.state === "legal") &&
    negativeOverlapPreview.state === "blocked" &&
    negativeOverlapPreview.reason === "blocked-occupied" &&
    PLACEMENT_LEGAL_FILL === "rgba(74, 222, 128, 0.25)" &&
    PLACEMENT_BLOCKED_FILL.startsWith("rgba(239, 68, 68") &&
    PLACEMENT_OUT_OF_REACH_FILL.startsWith("rgba(245, 158, 11") &&
    placementFillColor("legal") === PLACEMENT_LEGAL_FILL &&
    placementFillColor("blocked") === PLACEMENT_BLOCKED_FILL &&
    placementFillColor("out-of-reach") === PLACEMENT_OUT_OF_REACH_FILL &&
    placementStrokeColor("blocked") === PLACEMENT_BLOCKED_STROKE,
);

// ---------------------------------------------------------------------------------------------
// Minimap: layout fitting, panel<->world mapping in both directions, and the clamped camera jump
// ---------------------------------------------------------------------------------------------

/** A 64x64 window with a negative origin: the server's usual 2 * 32 window west and north of zero. */
const squareMap = { originX: -100, originY: -50, width: 64, height: 64 };
const squareLayout = minimapLayout(squareMap);
const wideMap = { originX: 0, originY: 0, width: 44, height: 32 };
const wideLayout = minimapLayout(wideMap);
check(
  "小地图布局：64×64 铺满 180px 面板（点 2×2）；非方形按比例信箱化不拉伸",
  MINIMAP_PANEL_SIZE === 180 &&
    MINIMAP_DOT_SIZE === 2 &&
    near(squareLayout.scale, 180 / 64) &&
    near(squareLayout.contentHeight, 180) &&
    near(wideLayout.scale, 180 / 44) &&
    near(wideLayout.contentHeight, (32 * 180) / 44) &&
    near(wideLayout.offsetY, (180 - (32 * 180) / 44) / 2),
);
check(
  "小地图映射：负原点窗口左上角即面板左上角、双向还原（含格内偏移）、取格 floor 到右下格、内容区外返回 null",
  JSON.stringify(minimapPointForTile(squareMap, -100, -50)) === JSON.stringify({ x: 0, y: 0 }) &&
    JSON.stringify(minimapTileAt(squareMap, 0, 0)) === JSON.stringify({ x: -100, y: -50 }) &&
    (() => {
      const point = minimapPointForTile(squareMap, -95, -45);
      const back = minimapTileAt(squareMap, point.x + 1, point.y + 1);
      return near(point.x, 5 * (180 / 64)) && back?.x === -95 && back?.y === -45;
    })() &&
    minimapTileAt(squareMap, -0.001, 0) === null &&
    minimapTileAt(squareMap, 180, 90) === null &&
    minimapTileAt(wideMap, 90, 5) === null &&
    minimapTileAt(wideMap, 100, 100)?.x === 24 &&
    minimapCameraTarget(squareMap, -5, 0, 32) === null,
);
check(
  "小地图镜头跳转：落点格格中心成为镜头中心；被夹在 ±500000 瓦片安全边界上",
  (() => {
    const centre = minimapCameraTarget({ originX: 10, originY: 20, width: 2, height: 2 }, 45, 45, 32);
    const bound = minimapCameraTarget(
      { originX: 600_000, originY: -600_000, width: 2, height: 2 },
      45,
      45,
      32,
    );
    return (
      centre !== null &&
      near(centre.x, 10.5 * 32) &&
      bound !== null &&
      bound.x === CAMERA_SAFETY_BOUND_TILES * 32 &&
      bound.y === -CAMERA_SAFETY_BOUND_TILES * 32
    );
  })(),
);

// ---------------------------------------------------------------------------------------------
// Double-click same-kind selection and the engineer shortcut
// ---------------------------------------------------------------------------------------------

/** An 8x8 grass window with own/enemy/outside same-kind units plus two buildings. */
const selectionWorld: WorldView = {
  originX: 0,
  originY: 0,
  width: 8,
  height: 8,
  tiles: Array.from({ length: 8 }, () => Array.from({ length: 8 }, () => "grass" as TerrainId)),
  entities: [
    { id: 1, kind: "unit.melee.t1", x: 1.2, y: 1.2, playerId: 1 },
    { id: 2, kind: "unit.melee.t1", x: 2.2, y: 2.2, playerId: 1 },
    { id: 3, kind: "unit.melee.t1", x: 3.2, y: 3.2, playerId: 2 },
    // Same kind and owner, but its ground tile is outside the loaded 8x8 window.
    { id: 4, kind: "unit.melee.t1", x: 9.2, y: 1.2, playerId: 1 },
    { id: 5, kind: "unit.engineer", x: 4.2, y: 4.2, playerId: 1 },
    { id: 6, kind: "unit.engineer", x: 5.2, y: 5.2, playerId: 2 },
    { id: 7, kind: "unit.engineer", x: 20.2, y: 5.2, playerId: 1 },
    {
      id: 8,
      kind: "building.armory.t1",
      x: 3.5,
      y: 5.5,
      playerId: 1,
      footprint: { width: 3, height: 3 },
      progress: 1,
    },
    {
      id: 9,
      kind: "building.warehouse",
      x: 6.5,
      y: 6.5,
      playerId: 2,
      footprint: { width: 3, height: 3 },
    },
    {
      id: 10,
      kind: "building.armory.t1",
      x: 1.5,
      y: 5.5,
      playerId: 1,
      footprint: { width: 3, height: 3 },
      progress: 1,
    },
  ],
};
const doubleClickedWorker = doubleClickSelection(selectionWorld, 1, 1);
check(
  "双击单位只选窗口内自己的同类：排除敌人、中立与窗口外的同 kind 单位",
  JSON.stringify(doubleClickedWorker) === JSON.stringify([1, 2]),
  doubleClickedWorker.join(","),
);
check(
  "双击敌方单位仍然只选自己的同类，不会把敌方单位选进来",
  JSON.stringify(doubleClickSelection(selectionWorld, 3, 1)) === JSON.stringify([1, 2]),
  doubleClickSelection(selectionWorld, 3, 1).join(","),
);
check(
  "双击结果只包含窗口内实体：窗口外的同类单位 id 不会出现在选中里",
  doubleClickedWorker.every((id) => {
    const entity = selectionWorld.entities.find((candidate) => candidate.id === id);
    return entity !== undefined && entityInWindow(selectionWorld, entity);
  }) && !doubleClickedWorker.includes(4),
  doubleClickedWorker.join(","),
);
check(
  "双击自己的建筑选中窗口内全部同类型建筑；敌方建筑仍只选它自己",
  JSON.stringify(doubleClickSelection(selectionWorld, 8, 1)) === JSON.stringify([8, 10]) &&
    JSON.stringify(doubleClickSelection(selectionWorld, 9, 1)) === JSON.stringify([9]),
  `${doubleClickSelection(selectionWorld, 8, 1).join(",")} / ` +
    `${doubleClickSelection(selectionWorld, 9, 1).join(",")}`,
);
check(
  "双击空地清空选中",
  JSON.stringify(doubleClickSelection(selectionWorld, null, 1)) === JSON.stringify([]),
);
const engineers = engineerSelection(selectionWorld, 1);
check(
  "工兵快捷键只选窗口内自己的工程师（敌方与窗口外都被排除）",
  JSON.stringify(engineers) === JSON.stringify([5]) && isEngineerKind("unit.engineer"),
  engineers.join(","),
);
check(
  "工程师 kind 判定覆盖 unit.engineer、engineer 与带命名空间的路径，不误判战斗单位",
  isEngineerKind("engineer") &&
    isEngineerKind("oc:unit.engineer") &&
    !isEngineerKind("unit.melee.t1") &&
    !isEngineerKind("building.warehouse"),
);

// ---------------------------------------------------------------------------------------------
// Hover tooltip: text composition and window clamping
// ---------------------------------------------------------------------------------------------

const unitTooltip = tooltipContent(
  "普通",
  { id: 11, kind: "unit.melee.t1", x: 1, y: 1, playerId: 1, healthRatio: 0.72 },
  "mine",
);
const buildingTooltip = tooltipContent(
  "兵工厂 1 级",
  {
    id: 12,
    kind: "building.armory.t1",
    x: 2,
    y: 2,
    playerId: 2,
    healthRatio: 0.42,
    footprint: { width: 3, height: 3 },
    queue: 2,
  },
  "enemy",
);
const noHealthTooltip = tooltipContent(
  "矿井",
  { id: 13, kind: "building.mine.t1", x: 0, y: 0, playerId: 1, footprint: { width: 3, height: 3 } },
  "mine",
);
const neutralTooltip = tooltipContent("worker", { id: 14, kind: "worker", x: 0, y: 0 }, "neutral");
check(
  "提示框内容：名字+归属+生命行；建筑追加尺寸与队列摘要；无 healthRatio 不编造；无主中立且 queue=0 无摘要",
  unitTooltip.name === "普通" &&
    unitTooltip.ownerTag === "我" &&
    JSON.stringify(unitTooltip.lines) === JSON.stringify(["生命 72%"]) &&
    buildingTooltip.ownerTag === "敌" &&
    JSON.stringify(buildingTooltip.lines) ===
      JSON.stringify(["生命 42%", "尺寸：3×3", "生产中 ×2"]) &&
    JSON.stringify(noHealthTooltip.lines) === JSON.stringify(["尺寸：3×3"]) &&
    neutralTooltip.ownerTag === "中立" &&
    JSON.stringify(neutralTooltip.lines) === JSON.stringify([]),
);
check(
  "提示框定位：默认边距 8px、光标间距 14px；右/下边缘翻到另一侧仍夹窗内；极限角完整落窗；窗口过小从边距开始不返负",
  TOOLTIP_MARGIN_PX === 8 &&
    TOOLTIP_GAP_PX === 14 &&
    JSON.stringify(clampTooltipPosition({ x: 900, y: 700 }, { width: 200, height: 80 }, { width: 1000, height: 800 })) ===
      JSON.stringify({ x: 686, y: 606 }) &&
    (() => {
      const edgeTooltip = clampTooltipPosition(
        { x: 995, y: 795 },
        { width: 200, height: 80 },
        { width: 1000, height: 800 },
      );
      return edgeTooltip.x === 781 && edgeTooltip.y === 701;
    })() &&
    JSON.stringify(clampTooltipPosition({ x: 50, y: 50 }, { width: 200, height: 100 }, { width: 100, height: 50 })) ===
      JSON.stringify({ x: 8, y: 8 }),
);

// The thumbnail half of the hover tooltip: a fixed 48×48 square whose source is chosen by the same
// atlas lookup as the world renderer, with a neutral grey placeholder carrying the kind's first
// character when that lookup has no sprite.
const atlasThumbnail = tooltipThumbnail(true, "unit.melee.t1");
const placeholderThumbnail = tooltipThumbnail(false, "unit.melee.t1");
const blankThumbnail = tooltipThumbnail(false, "  ");
check(
  "缩略图与停留隐藏：有帧用图集/无帧用占位符（固定 48×48）、首字符取 Unicode 码点、6 秒自隐藏且换实体重计",
  TOOLTIP_THUMBNAIL_SIZE_PX === 48 &&
    atlasThumbnail.kind === "atlas" &&
    placeholderThumbnail.kind === "placeholder" &&
    (placeholderThumbnail.kind === "placeholder" ? placeholderThumbnail.label : "") === "u" &&
    kindFirstCharacter("building.warehouse") === "b" &&
    kindFirstCharacter("  ") === "?" &&
    kindFirstCharacter("\u{1F600}unit") === "\u{1F600}" &&
    (blankThumbnail.kind === "placeholder" ? blankThumbnail.label : "") === "?" &&
    TOOLTIP_IDLE_HIDE_MS === 6_000 &&
    !tooltipIdleExpired(1_000, 6_999) &&
    tooltipIdleExpired(1_000, 7_000) &&
    !tooltipIdleExpired(1_000, 20_000, 0) &&
    !tooltipIdleExpired(Number.NaN, 20_000),
);

// ---------------------------------------------------------------------------------------------
// Snapshot diff events: building completion, 10% health drop, five-second de-duplication
// ---------------------------------------------------------------------------------------------

function eventWorld(entities: readonly EntityView[]): WorldView {
  return {
    originX: 0,
    originY: 0,
    width: 8,
    height: 8,
    tiles: Array.from({ length: 8 }, () => Array.from({ length: 8 }, () => "grass" as TerrainId)),
    entities,
  };
}

const eventBefore = eventWorld([
  { id: 1, kind: "building.armory.t1", x: 1, y: 1, playerId: 1, progress: 0.5, healthRatio: 1 },
  { id: 2, kind: "building.warehouse", x: 2, y: 2, playerId: 1, progress: 1, healthRatio: 1 },
  { id: 3, kind: "unit.melee.t1", x: 3, y: 3, playerId: 1, healthRatio: 1 },
  { id: 4, kind: "unit.melee.t1", x: 4, y: 4, playerId: 2, healthRatio: 1 },
  { id: 5, kind: "unit.melee.t1", x: 5, y: 5, playerId: 1, healthRatio: 0.5 },
  { id: 8, kind: "unit.melee.t1", x: 6, y: 6, playerId: 1, healthRatio: 1 },
  // Hostiles near the two damaged own units: the toast must name the highest tier nearby.
  { id: 6, kind: "unit.melee.t3", x: 2, y: 2, playerId: 2, healthRatio: 1 },
  { id: 10, kind: "unit.melee.t5", x: 6, y: 6, playerId: 2, healthRatio: 1 },
]);
const eventAfter = eventWorld([
  { id: 1, kind: "building.armory.t1", x: 1, y: 1, playerId: 1, progress: 1, healthRatio: 1 },
  { id: 2, kind: "building.warehouse", x: 2, y: 2, playerId: 1, progress: 1, healthRatio: 1 },
  // Exactly a 10-point drop: 1 -> 0.9 must fire despite binary floating point.
  { id: 3, kind: "unit.melee.t1", x: 3, y: 3, playerId: 1, healthRatio: 0.9 },
  // Enemy unit loses 9 points: below the threshold, and never "my base under attack" anyway.
  { id: 4, kind: "unit.melee.t1", x: 4, y: 4, playerId: 2, healthRatio: 0.91 },
  { id: 5, kind: "unit.melee.t1", x: 5, y: 5, playerId: 1, healthRatio: 0.4 },
  // Appears only in the current snapshot: no previous health to compare.
  { id: 20, kind: "unit.melee.t1", x: 7, y: 7, playerId: 1, healthRatio: 0.2 },
  { id: 6, kind: "unit.melee.t3", x: 2, y: 2, playerId: 2, healthRatio: 1 },
  { id: 10, kind: "unit.melee.t5", x: 6, y: 6, playerId: 2, healthRatio: 1 },
]);
const eventLabel = (kind: string): string =>
  kind === "building.armory.t1"
    ? "兵工厂 1 级"
    : kind === "unit.melee.t1"
      ? "普通"
      : kind;
const eventDedupe = new EventDeduplicator();
const firstEvents = diffSnapshotEvents(eventBefore, eventAfter, eventLabel, 1_000, eventDedupe, 1);
check(
  "快照差分产出建筑完成与遭到攻击事件，且只比较两帧都存在的实体",
  firstEvents
    .map((event) => `${event.kind}:${event.entityId}`)
    .join(",") === "building-complete:1,under-attack:3,under-attack:5",
  firstEvents.map((event) => `${event.kind}:${event.entityId}`).join(","),
);
check(
  "攻击提示是 遭到攻击：<名称>（攻击者：tN），N 取附近最高等级",
  firstEvents[0]?.text === "建筑完成：兵工厂 1 级" &&
    firstEvents[1]?.text === "遭到攻击：普通（攻击者：t3）" &&
    firstEvents[2]?.text === "遭到攻击：普通（攻击者：t5）",
  firstEvents.map((event) => event.text).join(" / "),
);
check(
  "只有自己的建筑/单位被打才提示；敌方单位掉血不产生 under-attack",
  firstEvents.every((event) => event.kind !== "under-attack" || event.entityId !== 4) &&
    ownershipOf(4, 1) === "enemy",
);
check(
  "生命下降至少 10% 才触发：1→0.9 触发，1→0.91 不触发，0.5→0.4 触发",
  HEALTH_DROP_THRESHOLD === 0.1 &&
    firstEvents.some((event) => event.entityId === 3) &&
    !firstEvents.some((event) => event.entityId === 4) &&
    firstEvents.some((event) => event.entityId === 5),
);
check(
  "攻击者等级解析与邻近推断：t3/t5 路径可解析，8 格内取最高，远处不误报",
  meleeTierOf("unit.melee.t3") === 3 &&
    meleeTierOf("unit.melee.t5") === 5 &&
    meleeTierOf("unit.engineer") === 0 &&
    ATTACKER_DETECTION_TILES === 4 &&
    attackerTierNear(eventAfter.entities[2]!, eventAfter.entities) === 3 &&
    attackerTierNear(
      { id: 99, kind: "unit.melee.t1", x: 0, y: 0, playerId: 1 },
      [{ id: 100, kind: "unit.melee.t5", x: 40, y: 40, playerId: 2 }],
    ) === 0,
);
const tooSoonEvents = diffSnapshotEvents(eventBefore, eventAfter, eventLabel, 5_999, eventDedupe, 1);
check(
  "同一实体 5 秒内不会重复产生事件（4.999 秒时全部被抑制）",
  EVENT_DEDUPE_MS === 5_000 && tooSoonEvents.length === 0,
  tooSoonEvents.map((event) => event.text).join(" / "),
);
const afterFiveSeconds = diffSnapshotEvents(eventBefore, eventAfter, eventLabel, 6_000, eventDedupe, 1);
check(
  "满 5 秒后同一实体可以再次产生事件",
  afterFiveSeconds.length === 3,
  afterFiveSeconds.map((event) => event.text).join(" / "),
);
check(
  "未知本地玩家时不把自己的归属猜错：myPlayerId 为 null 不产生 under-attack",
  diffSnapshotEvents(
    eventBefore,
    eventAfter,
    eventLabel,
    20_000,
    new EventDeduplicator(),
    null,
  ).every((event) => event.kind !== "under-attack"),
);
const independentDedupe = new EventDeduplicator();
check(
  "去重按实体独立计时：一个实体的事件不会压制另一个实体",
  independentDedupe.allow(1, 100) &&
    independentDedupe.allow(2, 100) &&
    !independentDedupe.allow(1, 5_099) &&
    independentDedupe.allow(1, 5_100),
);

// ---------------------------------------------------------------------------------------------
// Warehouse resource total: own buildings only, summed and sorted by display name
// ---------------------------------------------------------------------------------------------

const warehouseWorld = eventWorld([
  {
    id: 1,
    kind: "building.warehouse",
    x: 0,
    y: 0,
    playerId: 1,
    contents: { gold: 120, wood: 40 },
  },
  {
    id: 2,
    kind: "warehouse",
    x: 4,
    y: 4,
    playerId: 1,
    contents: { gold: 80, wood: 10, ore: 5 },
  },
  {
    id: 3,
    kind: "building.warehouse",
    x: 6,
    y: 6,
    playerId: 2,
    contents: { gold: 9_999 },
  },
  { id: 4, kind: "building.armory.t1", x: 2, y: 2, playerId: 1 },
  { id: 5, kind: "unit.engineer", x: 1, y: 1, playerId: 1 },
]);
const warehouseTotals = warehouseResourceTotals(warehouseWorld, 1);
const warehouseRows = warehouseTotals.items
  .map((item) => `${item.name}=${item.amount}`)
  .join(", ");
check(
  "仓库库存只合计自己的建筑：敌方仓库与自己无 contents 的建筑都不计入",
  warehouseTotals.warehouseCount === 2 &&
    warehouseRows === "ore=5, 木=50, 金=200",
  `${warehouseTotals.warehouseCount} / ${warehouseRows}`,
);
check(
  "仓库库存按显示名排序，数量是各仓库之和",
  warehouseTotals.items.length === 3 &&
    warehouseTotals.items.every((item) => item.amount > 0),
  warehouseRows,
);
check(
  "没有自己的仓库时数量为 0、列表为空；空仓库计数仍为 1",
  warehouseResourceTotals(
    eventWorld([
      { id: 21, kind: "unit.engineer", x: 1, y: 1, playerId: 1 },
      { id: 22, kind: "building.armory.t1", x: 2, y: 2, playerId: 1 },
    ]),
    1,
  ).warehouseCount === 0 &&
    warehouseResourceTotals(
      eventWorld([{ id: 23, kind: "building.warehouse", x: 3, y: 3, playerId: 1 }]),
      1,
    ).items.length === 0 &&
    warehouseResourceTotals(
      eventWorld([{ id: 24, kind: "building.warehouse", x: 3, y: 3, playerId: 1 }]),
      1,
    ).warehouseCount === 1,
);
check(
  "仓库 kind 判定同时接受 building.warehouse 与旧名 warehouse，拒绝其他建筑",
  isWarehouseKind("building.warehouse") &&
    isWarehouseKind("warehouse") &&
    isWarehouseKind("oc:building.warehouse") &&
    !isWarehouseKind("building.armory.t1"),
);
check(
  "没有玩家 id 时仓库合计为空（不把无主建筑当成自己的）",
  warehouseResourceTotals(warehouseWorld, null).warehouseCount === 0 &&
    warehouseResourceTotals(warehouseWorld, null).items.length === 0,
);

// ---------------------------------------------------------------------------------------------
// Marks, the marked HUD line, the inventory panel and the full resource page
// ---------------------------------------------------------------------------------------------

const goldMarked = toggleResourceMark(EMPTY_RESOURCE_MARKS, "gold");
const bothMarked = toggleResourceMark(goldMarked, "wood");
const goldUnmarked = toggleResourceMark(bothMarked, "gold");
check(
  "标记规则：添加、移除与重复切换互不影响，空白路径不写入，原集合不被修改",
  goldMarked.has("gold") &&
    goldMarked.size === 1 &&
    !goldUnmarked.has("gold") &&
    goldUnmarked.has("wood") &&
    goldUnmarked.size === 1 &&
    toggleResourceMark(goldMarked, "gold").size === 0 &&
    toggleResourceMark(EMPTY_RESOURCE_MARKS, "  ").size === 0 &&
    EMPTY_RESOURCE_MARKS.size === 0 &&
    bothMarked.size === 2,
  `${[...goldUnmarked].join(",")} / ${[...bothMarked].join(",")}`,
);
const allMarked = markAllResources(bothMarked, ["gold", "ore", "  "]);
check(
  "标记全部取并集并忽略空白，清除返回空集合",
  allMarked.size === 3 &&
    allMarked.has("gold") &&
    allMarked.has("wood") &&
    allMarked.has("ore") &&
    clearResourceMarks().size === 0 &&
    bothMarked.size === 2,
  `${[...allMarked].join(",")}`,
);

const markedRows = markedResourceItems(bothMarked, warehouseTotals);
check(
  "已标记总量使用仓库合计，并按固定物品顺序排列",
  markedRows.map((row) => `${row.name}=${row.amount}`).join(",") === "金=200,木=50",
  markedRows.map((row) => `${row.name}=${row.amount}`).join(","),
);
check(
  "已标记 HUD 行格式为 已标记：金 120 · 木 40；无标记时是 已标记：—",
  markedSummaryLabel(bothMarked, warehouseTotals) === "已标记：金 200 · 木 50" &&
    markedSummaryLabel(EMPTY_RESOURCE_MARKS, warehouseTotals) === "已标记：—",
  markedSummaryLabel(bothMarked, warehouseTotals),
);
const missingMarked = toggleResourceMark(bothMarked, "silver");
const missingRows = markedResourceItems(missingMarked, warehouseTotals);
check(
  "已标记但仓库里没有的物品保留 0 行，而不是消失",
  missingRows.length === 3 &&
    missingRows[2]?.path === "silver" &&
    missingRows[2]?.amount === 0 &&
    markedSummaryLabel(missingMarked, warehouseTotals) ===
      "已标记：金 200 · 木 50 · silver 0",
  markedSummaryLabel(missingMarked, warehouseTotals),
);

const inventoryLabels: Readonly<Record<string, string>> = { gold: "金", wood: "木" };
const inventoryLabel = (path: string): string => inventoryLabels[path] ?? path;
const cargoEngineer: EntityView = {
  id: 71,
  kind: "unit.engineer",
  x: 0,
  y: 0,
  playerId: 1,
  contents: { gold: 7 },
};
const oneUnitInventory = inventoryTotals([cargoEngineer], inventoryLabel);
check(
  "单个单位库存：货物列表带名称与数量，合计正确",
  oneUnitInventory.unitCount === 1 &&
    oneUnitInventory.carryingCount === 1 &&
    oneUnitInventory.items.map((item) => `${item.name}=${item.amount}`).join(",") === "金=7" &&
    oneUnitInventory.total === 7,
  JSON.stringify(oneUnitInventory),
);
const twoUnitInventory = inventoryTotals(
  [
    { ...cargoEngineer, id: 72, contents: { gold: 2, wood: 3 } },
    { ...cargoEngineer, id: 73, contents: { gold: 4 } },
  ],
  inventoryLabel,
);
check(
  "多个单位库存：货物聚合并显示数量行，合计是所有单位之和",
  twoUnitInventory.unitCount === 2 &&
    twoUnitInventory.carryingCount === 2 &&
    twoUnitInventory.items.map((item) => `${item.name}=${item.amount}`).join(",") ===
      "木=3,金=6" &&
    twoUnitInventory.total === 9,
  twoUnitInventory.items.map((item) => `${item.name}=${item.amount}`).join(","),
);
const emptyInventory = inventoryTotals([], inventoryLabel);
const emptyCargoInventory = inventoryTotals(
  [{ ...cargoEngineer, contents: undefined }],
  inventoryLabel,
);
const buildingInventory = inventoryTotals(
  [{ id: 74, kind: "building.warehouse", x: 0, y: 0, playerId: 1, contents: { gold: 99 } }],
  inventoryLabel,
);
check(
  "没有选中单位时库存为空；单位没有货物时为 0；建筑不当作单位库存",
  emptyInventory.unitCount === 0 &&
    emptyInventory.items.length === 0 &&
    emptyInventory.total === 0 &&
    emptyCargoInventory.unitCount === 1 &&
    emptyCargoInventory.carryingCount === 0 &&
    emptyCargoInventory.items.length === 0 &&
    emptyCargoInventory.total === 0 &&
    buildingInventory.unitCount === 0 &&
    buildingInventory.items.length === 0,
  `${emptyInventory.unitCount}/${emptyCargoInventory.unitCount}/${buildingInventory.unitCount}`,
);

const fullLabels: Readonly<Record<string, string>> = {
  gold: "金",
  wood: "木",
  silver: "银",
  crystal: "水晶",
  ore: "矿石",
};
const fullLabel = (path: string): string => fullLabels[path] ?? path;
const fullResourceWorld: WorldView = {
  ...eventWorld([
    {
      id: 1,
      kind: "building.warehouse",
      x: 0,
      y: 0,
      playerId: 1,
      contents: { gold: 120, wood: 40, ore: 5 },
    },
    {
      id: 2,
      kind: "unit.engineer",
      x: 1,
      y: 1,
      playerId: 1,
      contents: { gold: 7, crystal: 2 },
    },
    {
      id: 3,
      kind: "building.warehouse",
      x: 2,
      y: 2,
      playerId: 2,
      contents: { gold: 9_999 },
    },
    { id: 4, kind: "unit.melee.t1", x: 3, y: 3, playerId: 2, contents: { gold: 1 } },
    { id: 5, kind: "building.armory.t1", x: 4, y: 4, playerId: 1 },
  ]),
  players: [{ id: 1, name: "alice", gold: 300, wood: 60 }],
};
const fullRows = fullResourceList(fullResourceWorld, 1, ["silver", "gold"], fullLabel);
check(
  "完整资源清单：清单物品 + 仓库物品 + 单位携带 + 两种货币，按路径去重并按名称排序，0 保留",
  JSON.stringify(CURRENCY_PATHS) === JSON.stringify(["gold", "wood"]) &&
    fullRows.map((row) => `${row.name}=${row.amount}`).join(",") ===
      "木=100,水晶=2,矿石=5,金=427,银=0",
  fullRows.map((row) => `${row.name}=${row.amount}`).join(","),
);
const fullGoldRow = fullRows.find((row) => row.path === "gold");
const fullOreRow = fullRows.find((row) => row.path === "ore");
const fullSilverRow = fullRows.find((row) => row.path === "silver");
check(
  "完整资源清单：货币用玩家余额并加上自己的仓库存货，敌方仓库与敌方携带不计入",
  fullGoldRow?.amount === 427 &&
    JSON.stringify(fullGoldRow.buildingIds) === JSON.stringify([1]) &&
    JSON.stringify(fullGoldRow.unitIds) === JSON.stringify([2]) &&
    JSON.stringify(fullOreRow?.buildingIds) === JSON.stringify([1]) &&
    fullOreRow?.unitIds.length === 0 &&
    fullSilverRow?.amount === 0 &&
    fullSilverRow?.buildingIds.length === 0 &&
    fullSilverRow?.unitIds.length === 0,
  JSON.stringify(fullGoldRow),
);
const anonymousRows = fullResourceList(fullResourceWorld, null, ["silver"], fullLabel);
check(
  "未登录时完整资源页仍保留清单物品与两种货币，数量显式为 0",
  anonymousRows.map((row) => `${row.name}=${row.amount}`).join(",") === "木=0,金=0,银=0",
  anonymousRows.map((row) => `${row.name}=${row.amount}`).join(","),
);

// ---------------------------------------------------------------------------------------------
// Logistics: the per-warehouse view, its labels and the manual transfer form
// ---------------------------------------------------------------------------------------------

const logisticsWorld: WorldView = eventWorld([
  {
    id: 1,
    kind: "building.warehouse",
    x: 0.5,
    y: 0.5,
    playerId: 1,
    progress: 1,
    contents: { gold: 120, wood: 40 },
    storage: { slots: 24, used: 2, free: 22 },
    inbound: 1,
  },
  {
    id: 2,
    kind: "building.warehouse",
    x: 10.5,
    y: 0.5,
    playerId: 1,
    progress: 1,
    contents: { gold: 30 },
    storage: { slots: 24, used: 1, free: 23 },
  },
  {
    id: 3,
    kind: "building.warehouse",
    x: 20.5,
    y: 0.5,
    playerId: 2,
    progress: 1,
    contents: { gold: 999 },
  },
  {
    id: 4,
    kind: "building.warehouse",
    x: 30.5,
    y: 0.5,
    playerId: 1,
    progress: 0.5,
    storage: { slots: 24, used: 0, free: 24 },
  },
  { id: 5, kind: "building.armory.t1", x: 40.5, y: 0.5, playerId: 1, progress: 1 },
  // A legacy warehouse without `storage` and `contents`, to prove the old-snapshot path.
  { id: 6, kind: "building.warehouse", x: 50.5, y: 0.5, playerId: 1, progress: 1 },
]);
const warehouseOne = logisticsWorld.entities.find((entity) => entity.id === 1);
const warehouseTwo = logisticsWorld.entities.find((entity) => entity.id === 2);
const unfinishedWarehouse = logisticsWorld.entities.find((entity) => entity.id === 4);
const legacyWarehouse = logisticsWorld.entities.find((entity) => entity.id === 6);
const enemyWarehouse = logisticsWorld.entities.find((entity) => entity.id === 3);
const ownArmory = logisticsWorld.entities.find((entity) => entity.id === 5);

check(
  "仓库视图：自己的仓库按 id 排列，敌方仓库与普通建筑不算",
  ownWarehouses(logisticsWorld, 1).map((entity) => entity.id).join(",") === "1,2,4,6" &&
    ownWarehouses(logisticsWorld, null).length === 0,
  ownWarehouses(logisticsWorld, 1).map((entity) => entity.id).join(","),
);
const onlyOne = warehouseOne === undefined ? [] : [warehouseOne];
const twoSelected =
  warehouseOne === undefined || warehouseTwo === undefined ? [] : [warehouseOne, warehouseTwo];
check(
  "仓库视图：恰好选中一座自己的仓库才进入单仓库模式",
  selectedOwnWarehouse(onlyOne, 1)?.id === 1 &&
    selectedOwnWarehouse(twoSelected, 1) === null &&
    selectedOwnWarehouse(enemyWarehouse === undefined ? [] : [enemyWarehouse], 1) === null &&
    selectedOwnWarehouse(ownArmory === undefined ? [] : [ownArmory], 1) === null &&
    selectedOwnWarehouse([], 1) === null,
  String(selectedOwnWarehouse(onlyOne, 1)?.id),
);

const totalPanel = warehousePanelModel(logisticsWorld, 1, [], "single", fullLabel);
check(
  "总计模式：合计所有自己的建筑，表头是仓库数量，没有切换按钮",
  totalPanel.scope === "total" &&
    totalPanel.rows.map((row) => `${row.name}=${row.amount}`).join(",") === "木=40,金=150" &&
    totalPanel.summary === "4 个仓库" &&
    totalPanel.toggleLabel === null &&
    totalPanel.note === "" &&
    totalPanel.transferVisible === false,
  `${totalPanel.scope} / ${totalPanel.summary} / ${totalPanel.rows.map((row) => row.name).join(",")}`,
);

const singlePanel = warehousePanelModel(logisticsWorld, 1, onlyOne, "single", fullLabel);
check(
  "单仓库模式：只显示这一座的库存，表头带容量、已用、空闲槽位和在途数",
  singlePanel.scope === "single" &&
    singlePanel.rows.map((row) => `${row.name}=${row.amount}`).join(",") === "木=40,金=120" &&
    singlePanel.summary === "仓库 #1 · 容量 24 · 已用 2 · 空闲 22 · 在途 1" &&
    singlePanel.toggleLabel === warehouseScopeToggleSingleLabel() &&
    singlePanel.note === warehouseMarkNote() &&
    singlePanel.transferVisible === true &&
    singlePanel.transferReady === true,
  `${singlePanel.summary} / ${singlePanel.rows.map((row) => row.name).join(",")}`,
);
check(
  "单仓库模式：目标列表是自己的其他已完工仓库，来源物品列表就是本仓库存",
  singlePanel.targets.map((entity) => entity.id).join(",") === "2,6" &&
    singlePanel.sourceItems.map((item) => item.path).join(",") === "wood,gold" &&
    singlePanel.transferReason === "",
  singlePanel.targets.map((entity) => entity.id).join(","),
);

const totalWithSingle = warehousePanelModel(logisticsWorld, 1, onlyOne, "total", fullLabel);
check(
  "总计偏好：同一座仓库选中时切回合计，表头说明总计，切换按钮回到单仓库",
  totalWithSingle.scope === "total" &&
    totalWithSingle.summary === "4 个仓库 · 总计" &&
    totalWithSingle.toggleLabel === warehouseScopeToggleTotalLabel() &&
    totalWithSingle.note === "" &&
    totalWithSingle.transferVisible === false,
  `${totalWithSingle.scope} / ${totalWithSingle.summary}`,
);

const legacyPanel = warehousePanelModel(
  logisticsWorld,
  1,
  legacyWarehouse === undefined ? [] : [legacyWarehouse],
  "single",
  fullLabel,
);
check(
  "旧快照没有 storage 时单仓库表头说容量未知，在途按 0 显示",
  legacyPanel.summary === "仓库 #6 · 容量未知（旧快照） · 在途 0 · 暂无库存" &&
    warehouseStorageLabel(legacyWarehouse ?? { id: 6, kind: "building.warehouse", x: 0, y: 0 }) ===
      "容量未知（旧快照）" &&
    legacyPanel.transferReady === false &&
    legacyPanel.transferReason.includes("没有库存"),
  legacyPanel.summary,
);
check(
  "在途数标签：有任务时显示数量，没有字段时是 0",
  warehouseInboundLabel(warehouseOne ?? { id: 1, kind: "building.warehouse", x: 0, y: 0 }) === "在途 1" &&
    warehouseInboundLabel(warehouseTwo ?? { id: 2, kind: "building.warehouse", x: 0, y: 0 }) === "在途 0",
  warehouseInboundLabel(warehouseTwo ?? { id: 2, kind: "building.warehouse", x: 0, y: 0 }),
);

const unfinishedPanel = warehousePanelModel(
  logisticsWorld,
  1,
  unfinishedWarehouse === undefined ? [] : [unfinishedWarehouse],
  "single",
  fullLabel,
);
check(
  "未完工仓库仍是单仓库视图，但不能调拨，表头说明原因",
  unfinishedPanel.scope === "single" &&
    unfinishedPanel.transferVisible === true &&
    unfinishedPanel.transferReady === false &&
    unfinishedPanel.transferReason.includes("尚未完工") &&
    unfinishedPanel.summary.includes("暂无库存"),
  unfinishedPanel.transferReason,
);

const noWarehouseWorld = eventWorld([
  { id: 7, kind: "building.warehouse", x: 1, y: 1, playerId: 2, progress: 1 },
]);
const noWarehousePanel = warehousePanelModel(noWarehouseWorld, 1, [], "single", fullLabel);
check(
  "没有任何自己的仓库时面板回到 未发现自己的仓库",
  noWarehousePanel.scope === "none" &&
    noWarehousePanel.summary === "未发现自己的仓库" &&
    noWarehousePanel.toggleLabel === null &&
    noWarehousePanel.transferVisible === false &&
    noWarehousePanel.rows.length === 0,
  noWarehousePanel.summary,
);
const contentsOnlyWorld = eventWorld([
  { id: 8, kind: "building.armory.t1", x: 1, y: 1, playerId: 1, progress: 1, contents: { gold: 5 } },
]);
const contentsOnlyPanel = warehousePanelModel(contentsOnlyWorld, 1, [], "single", fullLabel);
check(
  "只有带有 contents 的非仓库建筑时仍显示库存合计，而不是谎称没有仓库",
  contentsOnlyPanel.scope === "total" &&
    contentsOnlyPanel.summary === "未发现自己的仓库 · 库存合计" &&
    contentsOnlyPanel.rows.map((row) => `${row.name}=${row.amount}`).join(",") === "金=5",
  contentsOnlyPanel.summary,
);

// ---------------------------------------------------------------------------------------------
// The manual transfer form and its answer labels
// ---------------------------------------------------------------------------------------------

const formSource =
  warehouseOne ?? { id: 1, kind: "building.warehouse", x: 0, y: 0, playerId: 1, progress: 1 };
const formTargets = singlePanel.targets;
const goodForm = transferFormModel(formSource, formTargets, "gold", "50", 2);
check(
  "调拨表单：合法输入生成 TRANSFER 行，数量与目标都来自选择",
  goodForm.ok && goodForm.line === "TRANSFER 1 2 gold 50" && goodForm.amount === 50 &&
    goodForm.targetId === 2,
  goodForm.line,
);
const allForm = transferFormModel(formSource, formTargets, "gold", "all", 2);
check(
  "调拨表单：amount 可以写 all，并按字面量发送",
  allForm.ok && allForm.line === "TRANSFER 1 2 gold all" && allForm.amount === null,
  allForm.line,
);
check(
  "调拨表单：没选物品 / 选了不持有的物品都被拒绝",
  transferFormModel(formSource, formTargets, "", "5", 2).refusal === "请选择要调拨的物品" &&
    transferFormModel(formSource, formTargets, "ore", "5", 2).refusal === "来源仓库没有这件物品",
  transferFormModel(formSource, formTargets, "ore", "5", 2).refusal,
);
check(
  "调拨表单：空数量、零、负数、小数和非数字都被拒绝",
  transferFormModel(formSource, formTargets, "gold", " ", 2).refusal === "请填写调拨数量" &&
    transferFormModel(formSource, formTargets, "gold", "0", 2).refusal === "调拨数量必须是正整数" &&
    transferFormModel(formSource, formTargets, "gold", "-2", 2).refusal === "调拨数量必须是正整数" &&
    transferFormModel(formSource, formTargets, "gold", "1.5", 2).refusal === "调拨数量必须是正整数" &&
    transferFormModel(formSource, formTargets, "gold", "many", 2).refusal === "调拨数量必须是正整数" &&
    transferFormModel(formSource, formTargets, "gold", "9007199254740993", 2).refusal ===
      "调拨数量必须是正整数",
  transferFormModel(formSource, formTargets, "gold", "0", 2).refusal,
);
check(
  "调拨表单：缺少目标、目标就是来源、目标不在自己其他仓库里都被拒绝",
  transferFormModel(formSource, formTargets, "gold", "5", null).refusal === "请选择目标仓库" &&
    transferFormModel(formSource, formTargets, "gold", "5", 1).refusal.includes("同一建筑") &&
    transferFormModel(formSource, formTargets, "gold", "5", 3).refusal.includes("不可达") &&
    transferFormModel(formSource, formTargets, "gold", "5", 99).refusal.includes("不可达"),
  transferFormModel(formSource, formTargets, "gold", "5", 99).refusal,
);
const unfinishedTargetForm = transferFormModel(
  formSource,
  unfinishedWarehouse === undefined ? [] : [unfinishedWarehouse],
  "gold",
  "5",
  4,
);
check(
  "调拨表单：未完工的目标仓库按不可达/未完工被拒绝",
  unfinishedTargetForm.ok === false && unfinishedTargetForm.refusal.includes("尚未完工"),
  unfinishedTargetForm.refusal,
);
const unfinishedSourceForm = transferFormModel(
  unfinishedWarehouse ?? formSource,
  unfinishedWarehouse === undefined ? [] : [unfinishedWarehouse],
  "gold",
  "5",
  4,
);
check(
  "调拨表单：未完工的来源仓库不能调拨",
  unfinishedSourceForm.ok === false && unfinishedSourceForm.refusal.includes("尚未完工"),
  unfinishedSourceForm.refusal,
);
check(
  "TRANSFER 命令行生成器：数字与 all 两种数量，非法数量在本地抛中文错误",
  transfer(1, 2, "gold", 50) === "TRANSFER 1 2 gold 50" &&
    transfer(1, 2, "gold", "all") === "TRANSFER 1 2 gold all" &&
    throwsChinese(() => transfer(1, 2, "gold", 0)) &&
    throwsChinese(() => transfer(1, 2, "gold", -1)) &&
    throwsChinese(() => transfer(1, 2, "gold", 1.5)),
  transfer(1, 2, "gold", 50),
);
check(
  "目标仓库选项标签是实体 id，状态行与命令里的 id 一致",
  transferTargetLabel(formSource) === "#1" &&
    transferTargetLabel(warehouseTwo ?? formSource) === "#2",
  transferTargetLabel(warehouseTwo ?? formSource),
);
check(
  "调拨回答：OK <数量> <物品> 读成 已调拨，ERR 保留引擎理由，未知回答原样显示",
  transferAnswerLabel("OK 120 gold", fullLabel).ok &&
    transferAnswerLabel("OK 120 gold", fullLabel).text === "已调拨：120 金" &&
    transferAnswerLabel("WHAT 12 gold").text.startsWith("调拨回答") &&
    transferAnswerLabel("ERR no stock of gold").ok === false &&
    transferAnswerLabel("ERR no stock of gold").text === "调拨失败：no stock of gold" &&
    transferAnswerLabel("OK").text === "已调拨" &&
    transferAnswerLabel("ERR").text === "调拨失败",
  transferAnswerLabel("OK 120 gold", fullLabel).text,
);

const logisticsSnapshot = worldViewFromSnapshot({
  tick: 1,
  width: 3,
  height: 3,
  tiles: [
    ["grass", "grass", "grass"],
    ["grass", "grass", "grass"],
    ["grass", "grass", "grass"],
  ],
  entities: [
    {
      id: 1,
      kind: "building.warehouse",
      x: 0.5,
      y: 0.5,
      player: 1,
      health: 1,
      w: 3,
      h: 3,
      progress: 1,
      contents: { gold: 120 },
      storage: { slots: 24, used: 2, free: 22 },
      inbound: 1,
    },
    {
      id: 2,
      kind: "building.warehouse",
      x: 1.5,
      y: 0.5,
      player: 1,
      health: 1,
      w: 3,
      h: 3,
      progress: 1,
      storage: { slots: -1, used: 0, free: 0 },
      inbound: -2,
    },
  ],
});
const parsedStorageRow = logisticsSnapshot?.entities.find((entity) => entity.id === 1);
const malformedStorageRow = logisticsSnapshot?.entities.find((entity) => entity.id === 2);
check(
  "快照解析：storage 与 inbound 被读成可选字段，负数/畸形值被丢弃",
  parsedStorageRow?.storage?.slots === 24 &&
    parsedStorageRow?.storage?.used === 2 &&
    parsedStorageRow?.storage?.free === 22 &&
    parsedStorageRow?.inbound === 1 &&
    malformedStorageRow?.storage === undefined &&
    malformedStorageRow?.inbound === undefined,
  JSON.stringify(parsedStorageRow?.storage),
);

// ---------------------------------------------------------------------------------------------
// Command bar: selection actions, the selected-units strip, abbreviations and health-bar ratios
// ---------------------------------------------------------------------------------------------

/** One unit fixture for the command-bar checks; `playerId` defaults to the local player. */
function commandUnit(
  id: number,
  kind: string,
  playerId = 1,
  healthRatio?: number,
): EntityView {
  return {
    id,
    kind,
    x: id,
    y: id,
    playerId,
    ...(healthRatio === undefined ? {} : { healthRatio }),
  };
}

/** One finished building fixture; `playerId` defaults to the local player. */
function commandBuilding(
  id: number,
  kind = "building.armory.t1",
  playerId = 1,
): EntityView {
  return {
    id,
    kind,
    x: id,
    y: id,
    playerId,
    progress: 1,
    ...(kind === "building.armory.t1" && playerId === 1 ? { trains: SERVED_ARMORY_TRAINS } : {}),
  };
}

const emptyCommand = commandBarModel([], 1);
check(
  "未选中时命令栏只有一条提示，没有任何动作或芯片",
  emptyCommand.kind === "none" &&
    emptyCommand.mixed === false &&
    emptyCommand.actions.length === 0 &&
    emptyCommand.hint === emptySelectionHint(),
  `${emptyCommand.kind} / ${emptyCommand.actions.length}`,
);

const unitCommand = commandBarModel(
  [commandUnit(1, "unit.melee.t1"), commandUnit(2, "unit.melee.t2")],
  1,
);
check(
  "选中单位时动作是 停止 + 攻击移动，且都在中间命令栏",
  unitCommand.kind === "units" &&
    unitCommand.actions.map((action) => action.kind).join(",") === "stop,attack-move" &&
    unitCommand.actions.every((action) => action.zone === "bar") &&
    unitCommand.actions.map((action) => action.label).join(",") ===
      `${stopActionLabel()},${attackMoveActionLabel()}`,
  `${unitCommand.kind} / ${unitCommand.actions.map((action) => action.kind).join(",")}`,
);

const engineerCommand = commandBarModel([commandUnit(3, "unit.engineer")], 1);
check(
  "选中工兵：单位动作 + BUILD 条目只来自 served buildable；没有 served 目录就没有 BUILD 条目（item 1）",
  engineerCommand.kind === "engineer" &&
    engineerCommand.actions.slice(0, 2).map((action) => action.kind).join(",") ===
      "stop,attack-move" &&
    commandBarModel([commandUnit(3, "unit.engineer")], 1, {}).actions.filter(
      (action) => action.kind === "build",
    ).length === 0 &&
    commandBarModel([{ id: 3, kind: "unit.engineer", x: 3, y: 3, playerId: 1 }], 1, {
      buildingPaths: ["building.hell_gate", "building.mine.t1"],
    })
      .actions.filter((action) => action.kind === "build")
      .map((action) => action.path)
      .join(",") === "building.hell_gate,building.mine.t1",
  `${engineerCommand.kind} / ${engineerCommand.actions.length}`,
);

const buildingCommand = commandBarModel([commandBuilding(4)], 1, {
  labelForPath: (path) => (path === "unit.melee.t1" ? "普通" : path),
});
check(
  "选中自己的建筑时只给出 TRAIN 条目（右侧面板），没有单位动作；菜单只来自 served trains 六条",
  buildingCommand.kind === "building" &&
    buildingCommand.actions.length === SERVED_ARMORY_TRAINS.length &&
    buildingCommand.actions.every(
      (action) => action.kind === "train" && action.zone === "context",
    ) &&
    buildingCommand.actions[0]?.label === "普通",
  `${buildingCommand.kind} / ${buildingCommand.actions.length}`,
);

const multiBuildingCommand = commandBarModel(
  [commandBuilding(4), commandBuilding(10), commandBuilding(11)],
  1,
  { labelForPath: (path) => path },
);
check(
  "选中多座同类型建筑仍给出 TRAIN，提示说明一次点击会并行排产",
  multiBuildingCommand.kind === "building" &&
    multiBuildingCommand.buildingCount === 3 &&
    multiBuildingCommand.actions.length === SERVED_ARMORY_TRAINS.length &&
    multiBuildingCommand.actions.every((action) => action.kind === "train") &&
    multiBuildingCommand.hint.includes("并行生产"),
  `${multiBuildingCommand.kind} / ${multiBuildingCommand.hint}`,
);
const batches = trainBatch([4, 10, 11], "unit.melee.t2");
check(
  "批量 TRAIN 为每座建筑生成一条指令，顺序与选择一致",
  JSON.stringify(batches) ===
    JSON.stringify([
      "TRAIN 4 unit.melee.t2",
      "TRAIN 10 unit.melee.t2",
      "TRAIN 11 unit.melee.t2",
    ]) &&
    trainBatch([7], "unit.engineer").length === 1 &&
    (() => {
      try {
        trainBatch([], "unit.melee.t1");
        return false;
      } catch {
        return true;
      }
    })(),
  batches.join(" / "),
);
const mixedBuildingKinds = commandBarModel(
  [commandBuilding(4), commandBuilding(12, "building.warehouse")],
  1,
);
check(
  "多座但类型不同时不提供 TRAIN，并说明需要同类型",
  mixedBuildingKinds.kind === "other" &&
    mixedBuildingKinds.actions.length === 0 &&
    mixedBuildingKinds.hint.includes("同类型"),
  `${mixedBuildingKinds.kind} / ${mixedBuildingKinds.hint}`,
);

const mixedCommand = commandBarModel(
  [commandUnit(5, "unit.melee.t1"), commandBuilding(6)],
  1,
);
check(
  "单位 + 建筑混合选择只提供单位动作，并在提示里说明",
  mixedCommand.kind === "mixed" &&
    mixedCommand.mixed === true &&
    mixedCommand.actions.map((action) => action.kind).join(",") === "stop,attack-move" &&
    mixedCommand.actions.every((action) => action.zone === "bar") &&
    !mixedCommand.actions.some(
      (action) => action.kind === "train" || action.kind === "build",
    ) &&
    mixedCommand.hint.includes("混合选择只提供单位动作"),
  mixedCommand.hint,
);

const engineerGroupCommand = commandBarModel(
  [commandUnit(7, "unit.engineer"), commandUnit(8, "unit.melee.t1")],
  1,
);
check(
  "工兵与其他单位一起选中时按普通单位处理，不提供 BUILD",
  engineerGroupCommand.kind === "units" &&
    !engineerGroupCommand.actions.some((action) => action.kind === "build"),
  engineerGroupCommand.kind,
);

const enemyBuildingCommand = commandBarModel(
  [commandBuilding(9, "building.armory.t1", 2)],
  1,
);
check(
  "敌方建筑不提供 TRAIN，命令栏没有动作",
  enemyBuildingCommand.kind === "other" &&
    enemyBuildingCommand.actions.length === 0,
  `${enemyBuildingCommand.kind} / ${enemyBuildingCommand.actions.length}`,
);

// ---------------------------------------------------------------------------------------------
// Upgrade entry: only own buildings with an upgrade field, target name, cost and affordability
// ---------------------------------------------------------------------------------------------

/** One finished armory fixture carrying the snapshot's optional upgrade object. */
function upgradeBuilding(
  id: number,
  gold: number,
  wood: number,
  playerId = 1,
): EntityView {
  return {
    id,
    kind: "building.armory.t1",
    x: id,
    y: id,
    playerId,
    progress: 1,
    upgrade: { options: [{ to: "building.armory.t2", gold, wood }] },
  };
}

const upgradeLabels = (path: string): string =>
  path === "building.armory.t2" ? "兵营·强化" : path;
const affordableUpgrade = upgradeEntry(
  upgradeBuilding(40, 140, 40),
  1,
  { id: 1, name: "Player 1", gold: 300, wood: 60 },
  upgradeLabels,
);
const plainBuilding: EntityView = {
  id: 46,
  kind: "building.armory.t2",
  x: 46,
  y: 46,
  playerId: 1,
  progress: 1,
};
const upgradeUnitRow: EntityView = {
  id: 47,
  kind: "unit.melee.t1",
  x: 47,
  y: 47,
  playerId: 1,
  upgrade: { options: [{ to: "building.armory.t2", gold: 140, wood: 40 }] },
};
check(
  "升级条目规则：自己的建筑带 upgrade 才出现，标签带目标名与成本；付得起可用、缺一项禁用、无玩家行禁用；敌方/无字段/单位行都不出现",
  affordableUpgrade !== null &&
    affordableUpgrade.target === "building.armory.t2" &&
    affordableUpgrade.targetName === "兵营·强化" &&
    affordableUpgrade.gold === 140 &&
    affordableUpgrade.wood === 40 &&
    affordableUpgrade.affordable &&
    affordableUpgrade.label ===
      `${t("action.upgrade")} · 兵营·强化 · ${upgradeCostLabel(140, 40)}` &&
    upgradeEntry(
      upgradeBuilding(41, 140, 40),
      1,
      { id: 1, name: "Player 1", gold: 140, wood: 40 },
      upgradeLabels,
    )?.affordable === true &&
    upgradeEntry(
      upgradeBuilding(42, 140, 40),
      1,
      { id: 1, name: "Player 1", gold: 139, wood: 60 },
      upgradeLabels,
    )?.disabled === true &&
    upgradeEntry(
      upgradeBuilding(43, 140, 40),
      1,
      { id: 1, name: "Player 1", gold: 300, wood: 39 },
      upgradeLabels,
    )?.disabled === true &&
    upgradeEntry(upgradeBuilding(44, 140, 40), 1, undefined, upgradeLabels)?.disabled === true &&
    upgradeEntry(
      upgradeBuilding(45, 140, 40, 2),
      1,
      { id: 1, name: "Player 1", gold: 300, wood: 60 },
      upgradeLabels,
    ) === null &&
    upgradeEntry(plainBuilding, 1, { id: 1, name: "Player 1", gold: 999, wood: 999 }) ===
      null &&
    upgradeEntry(upgradeUnitRow, 1, { id: 1, name: "Player 1", gold: 999, wood: 999 }) ===
      null,
  affordableUpgrade?.label ?? "<null>",
);
const variantBuilding: EntityView = {
  id: 48,
  kind: "building.armory.t1",
  x: 48,
  y: 48,
  playerId: 1,
  progress: 1,
  upgrade: {
    options: [
      { to: "building.armory.t2", gold: 140, wood: 40 },
      { to: "building.purgatory", gold: 260, wood: 40 },
    ],
  },
};
const variantLabels = (path: string): string =>
  path === "building.purgatory" ? "炼狱工厂" : upgradeLabels(path);
check(
  "变体升级：upgradeEntry 给第一变体，upgradeEntries 按声明顺序给全部并各带费用",
  (() => {
    const player = { id: 1, name: "Player 1", gold: 300, wood: 60 };
    const entries = upgradeEntries(variantBuilding, 1, player, variantLabels);
    const first = upgradeEntry(variantBuilding, 1, player, variantLabels);
    return (
      first !== null &&
      first.target === "building.armory.t2" &&
      entries.length === 2 &&
      entries[1]?.target === "building.purgatory" &&
      entries[1]?.targetName === "炼狱工厂" &&
      entries[1]?.gold === 260 &&
      entries[1]?.wood === 40 &&
      entries[1]?.affordable &&
      entries[1]?.label === `${t("action.upgrade")} · 炼狱工厂 · ${upgradeCostLabel(260, 40)}`
    );
  })(),
);
check(
  "UPGRADE 命令构建器生成 UPGRADE <id>，带变体时生成 UPGRADE <id> <variantPath>，并拒绝非法输入",
  upgrade(12) === "UPGRADE 12" &&
    upgrade(12, "building.purgatory") === "UPGRADE 12 building.purgatory" &&
    throwsChinese(() => upgrade(0)) &&
    throwsChinese(() => upgrade(1.5)) &&
    throwsChinese(() => upgrade(-3)) &&
    throwsChinese(() => upgrade(12, "  ")),
);

const batchPlayer = { id: 1, name: "Player 1", gold: 300, wood: 100 };
const batchBuildings = [
  upgradeBuilding(50, 140, 40),
  upgradeBuilding(51, 140, 40),
  upgradeBuilding(52, 140, 40),
];
const batchPlan = upgradeBatchPlan(batchBuildings, 1, batchPlayer);
check(
  "升级全部：按选择顺序每个买得起的建筑一条 UPGRADE，余额不足的那个跳过并给原因",
  batchPlan !== null &&
    t("action.batch") === "批量" &&
    batchPlan.lines.join(",") === "UPGRADE 50 building.armory.t2,UPGRADE 51 building.armory.t2" &&
    batchPlan.buildingIds.join(",") === "50,51" &&
    batchPlan.skipped.length === 1 &&
    batchPlan.skipped[0]?.buildingId === 52 &&
    batchPlan.skipped[0]?.reason === "unaffordable" &&
    batchPlan.skipped[0]?.text === "资源不足" &&
    batchPlan.summary === "已发送 2 条升级 / 1 条被拒绝" &&
    upgradeBatchSummary(2, 1) === "已发送 2 条升级 / 1 条被拒绝",
  batchPlan?.summary ?? "null",
);
check(
  "升级全部的预算会随计划递减：金 150 只能计划一条 140 的升级",
  upgradeBatchPlan(
    [upgradeBuilding(53, 140, 40), upgradeBuilding(54, 140, 40)],
    1,
    { ...batchPlayer, gold: 150 },
  )?.lines.join(",") === "UPGRADE 53 building.armory.t2",
);
check(
  "升级全部跳过未完工与没有后续等级的建筑，其余照常发送",
  (() => {
    const unfinished: EntityView = {
      id: 55,
      kind: "building.armory.t1",
      x: 55,
      y: 55,
      playerId: 1,
      progress: 0.3,
      upgrade: { options: [{ to: "building.armory.t2", gold: 140, wood: 40 }] },
    };
    const topTier: EntityView = {
      id: 56,
      kind: "building.armory.t1",
      x: 56,
      y: 56,
      playerId: 1,
      progress: 1,
    };
    const plan = upgradeBatchPlan(
      [unfinished, topTier, upgradeBuilding(57, 140, 40)],
      1,
      batchPlayer,
    );
    return plan !== null &&
      plan.lines.join(",") === "UPGRADE 57 building.armory.t2" &&
      plan.skipped.map((skip) => `${skip.buildingId}:${skip.reason}`).join(",") ===
        "55:unfinished,56:no-successor" &&
      plan.summary === "已发送 1 条升级 / 2 条被拒绝";
  })(),
);
check(
  "升级全部只接受自己的同类型建筑组；不同种类、敌方、单位或空选择都没有入口",
  upgradeBatchPlan([], 1, batchPlayer) === null &&
    upgradeBatchPlan(
      [upgradeBuilding(58, 140, 40), { ...upgradeBuilding(59, 140, 40), kind: "building.mine.t1" }],
      1,
      batchPlayer,
    ) === null &&
    upgradeBatchPlan([upgradeBuilding(60, 140, 40, 2)], 1, batchPlayer) === null &&
    upgradeBatchPlan([upgradeUnitRow], 1, batchPlayer) === null,
  "all null",
);
check(
  "没有玩家行时升级全部计划为空，但每个建筑仍带被拒绝原因",
  (() => {
    const plan = upgradeBatchPlan([upgradeBuilding(61, 140, 40)], 1, undefined);
    return plan !== null &&
      plan.lines.length === 0 &&
      plan.skipped.length === 1 &&
      plan.summary === "已发送 0 条升级 / 1 条被拒绝";
  })(),
);

const upgradeSnapshot = worldViewFromSnapshot({
  tick: 5,
  width: 1,
  height: 1,
  tiles: [["grass"]],
  entities: [
    {
      id: 7,
      kind: "oc:building.armory.t1",
      x: 0.5,
      y: 0.5,
      player: 1,
      upgrade: { options: [{ to: "oc:building.armory.t2", gold: 140, wood: 40 }] },
    },
    {
      id: 8,
      kind: "building.armory.t2",
      x: 1.5,
      y: 0.5,
      player: 1,
      upgrade: { options: [{ to: "", gold: -1, wood: "none" }] },
    },
  ],
});
check(
  "快照解析 upgrade 对象并去掉命名空间；畸形对象整体忽略",
  upgradeSnapshot !== null &&
    upgradeSnapshot.entities[0]?.upgrade?.options[0]?.to === "building.armory.t2" &&
    upgradeSnapshot.entities[0]?.upgrade?.options[0]?.gold === 140 &&
    upgradeSnapshot.entities[0]?.upgrade?.options[0]?.wood === 40 &&
    upgradeSnapshot.entities[1]?.upgrade === undefined,
  JSON.stringify(upgradeSnapshot?.entities.map((entity) => entity.upgrade)),
);
check(
  "旧快照没有 upgrade 字段时实体照常解析",
  worldViewFromSnapshot({
    tick: 6,
    width: 1,
    height: 1,
    tiles: [["grass"]],
    entities: [{ id: 9, kind: "building.armory.t1", x: 0.5, y: 0.5, player: 1 }],
  })?.entities[0]?.upgrade === undefined,
);

const chipName = (kind: string): string =>
  kind === "unit.melee.t1" ? "普通" : kind === "unit.melee.t2" ? "强化" : kind;
const chipSelection = [
  commandUnit(11, "unit.melee.t1", 1, 1),
  commandUnit(12, "unit.melee.t2", 1, 0.5),
  commandBuilding(13),
  commandUnit(14, "unit.melee.t1", 2, 0),
];
const chipList = selectionChipList(chipSelection, 1, chipName);
check(
  "选中条按单位生成芯片、排除建筑，并带上归属颜色与生命比例",
  chipList.chips.length === 3 &&
    chipList.total === 3 &&
    chipList.overflow === 0 &&
    chipList.overflowLabel === "" &&
    chipList.chips.map((chip) => chip.label).join(",") === "普通,强化,普通" &&
    chipList.chips[0]?.color === OWNERSHIP_MINE_COLOR &&
    chipList.chips[2]?.color === OWNERSHIP_ENEMY_COLOR &&
    chipList.chips[1]?.healthRatio === 0.5,
  chipList.chips.map((chip) => `${chip.label}:${chip.healthRatio}`).join(","),
);

const twelveUnits = Array.from({ length: SELECTION_CHIP_LIMIT }, (_unused, index) =>
  commandUnit(100 + index, "unit.melee.t1"),
);
const fourteenChips = selectionChipList(
  [...twelveUnits, commandUnit(200, "unit.melee.t1"), commandUnit(201, "unit.melee.t1")],
  1,
  chipName,
);
check(
  "芯片上限：正好 12 个显示 12 个没有 +N；超过 12 个截断为前 12 个并追加 +N",
  selectionChipList(twelveUnits, 1, chipName).overflowLabel === "" &&
    fourteenChips.chips.length === SELECTION_CHIP_LIMIT &&
    fourteenChips.total === 14 &&
    fourteenChips.overflow === 2 &&
    fourteenChips.overflowLabel === "+2" &&
    fourteenChips.chips[0]?.entityId === 100 &&
    fourteenChips.chips[11]?.entityId === 111,
  `${fourteenChips.overflowLabel}`,
);

check(
  "芯片缩写取前两个字符：单字名保留、带数字名保留数字、空名显示 ?",
  chipLabel("工程师") === "工程" &&
    chipLabel("战王") === "战王" &&
    chipLabel("王") === "王" &&
    chipLabel("3级兵营") === "3级" &&
    chipLabel("  ") === "?",
  [chipLabel("工程师"), chipLabel("王"), chipLabel("3级兵营"), chipLabel("  ")].join(","),
);

const healthEntity = (healthRatio?: number): EntityView => ({
  id: 21,
  kind: "unit.melee.t1",
  x: 0,
  y: 0,
  ...(healthRatio === undefined ? {} : { healthRatio }),
});
check(
  "芯片生命条比例：0% -> 0，50% -> 0.5，100%/未报告 -> 1，越界值夹紧",
  healthBarRatio(healthEntity(0)) === 0 &&
    healthBarRatio(healthEntity(0.5)) === 0.5 &&
    healthBarRatio(healthEntity(2)) === 1 &&
    healthBarRatio(healthEntity(undefined)) === 1 &&
    healthBarRatio(healthEntity(-1)) === 0,
);

// ---------------------------------------------------------------------------------------------
// Selection flash: a quarter-second achromatic halo for player-driven selection changes
// ---------------------------------------------------------------------------------------------

check(
  "选中闪烁：时长 300ms；强度开始 1、半程 0.5、结束 0、非正时长 0、负流逝夹 1、全部采样在 0..1",
  FLASH_MS === 300 &&
    flashIntensity(1_000, 1_000, FLASH_MS) === 1 &&
    near(flashIntensity(1_000, 1_000 + FLASH_MS / 2, FLASH_MS), 0.5) &&
    flashIntensity(1_000, 1_000 + FLASH_MS - 1, FLASH_MS) > 0 &&
    flashIntensity(1_000, 1_000 + FLASH_MS, FLASH_MS) === 0 &&
    flashIntensity(1_000, 1_000, 0) === 0 &&
    flashIntensity(1_000, 900, FLASH_MS) === 1 &&
    [-10_000, -1, 0, 1, FLASH_MS / 2, FLASH_MS - 1, FLASH_MS, 10_000].every((elapsed) => {
      const value = flashIntensity(1_000, 1_000 + elapsed, FLASH_MS);
      return value >= 0 && value <= 1;
    }),
);

check(
  "startFlash/pruneFlashes：只写新选中 id 并重启时间、空列表不动条目、prune 删结束留进行中；颜色纯白",
  (() => {
    const flashMap = new Map<number, number>();
    startFlash(flashMap, [1, 2], 1_000);
    startFlash(flashMap, [2, 3], 1_200);
    const writes =
      flashMap.size === 3 && flashMap.get(1) === 1_000 && flashMap.get(2) === 1_200 && flashMap.get(3) === 1_200;
    const before = new Map(flashMap);
    startFlash(flashMap, [], 2_000);
    const emptyKeeps = [...before].every(([id, startedAt]) => flashMap.get(id) === startedAt);
    const pruningMap = new Map<number, number>([
      [1, 1_000],
      [2, 1_100],
      [3, 1_400],
    ]);
    pruneFlashes(pruningMap, 1_300);
    return (
      writes &&
      emptyKeeps &&
      pruningMap.size === 2 &&
      !pruningMap.has(1) &&
      pruningMap.has(2) &&
      pruningMap.has(3) &&
      isGreyscaleHex(FLASH_COLOR) &&
      FLASH_COLOR === "#ffffff"
    );
  })(),
);
check(
  "单击闪光比扫过闪光更强：单击两环、扫过一环；单击空地无闪、单击实体只闪它",
  CLICK_FLASH_STRENGTH === "strong" &&
    SWEEP_FLASH_STRENGTH === "normal" &&
    flashRingCount(CLICK_FLASH_STRENGTH) === 2 &&
    flashRingCount(SWEEP_FLASH_STRENGTH) === 1 &&
    clickFlashIds(null).length === 0 &&
    JSON.stringify(clickFlashIds(7)) === JSON.stringify([7]),
);

// ---------------------------------------------------------------------------------------------
// Marquee sweep flash: candidates, entered-ids set, the leaves-and-re-enters rule, release rule
// ---------------------------------------------------------------------------------------------

const sweepCandidateIds = marqueeCoveredIds(marqueeWorld, marqueeSquare, 1);
check(
  "扫过候选与释放候选同规则：己方单位与己方建筑且在窗口内；窗口外实体与未登录身份都不进入",
  JSON.stringify(sweepCandidateIds) === JSON.stringify([1, 2, 4, 7]) &&
    !sweepCandidateIds.includes(3) &&
    !sweepCandidateIds.includes(6) &&
    marqueeCoveredIds(
      { ...marqueeWorld, originX: 100, originY: 100, width: 2, height: 2 },
      marqueeSquare,
      1,
    ).length === 0 &&
    marqueeCoveredIds(marqueeWorld, marqueeSquare, null).length === 0,
  sweepCandidateIds.join(","),
);

const dragSweep = new MarqueeSweep();
check(
  "扫过帧规则：第一帧框内全算进入、之后只报新进入、离开再进入不重复、基线实体不算进入",
  JSON.stringify(dragSweep.frame([1, 2])) === JSON.stringify([1, 2]) &&
    JSON.stringify(dragSweep.frame([2, 3])) === JSON.stringify([3]) &&
    JSON.stringify(dragSweep.frame([2])) === JSON.stringify([]) &&
    JSON.stringify([...dragSweep.flashedIds]) === JSON.stringify([1, 2, 3]) &&
    (() => {
      const baselineSweep = new MarqueeSweep([1]);
      return (
        JSON.stringify(baselineSweep.frame([1, 2])) === JSON.stringify([2]) &&
        !baselineSweep.flashedIds.has(1)
      );
    })(),
);
check(
  "释放规则：不重复闪已扫过的实体，仍闪起点框内且未被扫过的实体",
  JSON.stringify(releaseFlashIds([2], [], [2])) === JSON.stringify([]) &&
    JSON.stringify(releaseFlashIds([1, 2, 3], [1], [2])) === JSON.stringify([3]) &&
    JSON.stringify(releaseFlashIds([1], [], [])) === JSON.stringify([1]) &&
    JSON.stringify(releaseFlashIds([1, 2], [], [2])) === JSON.stringify([1]),
);
check(
  "扫过 + 释放整合：扫过的战斗单位不在释放时再闪，起点建筑也未误闪",
  (() => {
    const baselineIds = marqueeCoveredIds(marqueeWorld, { minX: 2, minY: 2, maxX: 2, maxY: 2 }, 1);
    const integrated = new MarqueeSweep(baselineIds);
    const first = integrated.frame(marqueeCoveredIds(marqueeWorld, marqueeSquare, 1));
    integrated.frame(marqueeCoveredIds(marqueeWorld, { minX: 2, minY: 2, maxX: 2, maxY: 2 }, 1));
    const released = releaseFlashIds([2], [], integrated.flashedIds);
    return (
      JSON.stringify(baselineIds) === JSON.stringify([1, 4]) &&
      JSON.stringify(first) === JSON.stringify([2, 7]) &&
      released.length === 0
    );
  })(),
);

// ---------------------------------------------------------------------------------------------
// Order crosshair: 350 ms lifetime curve, scale/alpha, CSS -> backing pixels, edge clamp, colour
// ---------------------------------------------------------------------------------------------

const crosshairStart = 10_000;
check(
  "准星曲线：350ms 寿命（起点 0、半程 0.5、终点 1、回退 0）；缩放 0.6..1.4；透明度 1..0 夹紧；帧只在寿命内可见",
  CROSSHAIR_MS === 350 &&
    crosshairProgress(crosshairStart, crosshairStart) === 0 &&
    near(crosshairProgress(crosshairStart, crosshairStart + CROSSHAIR_MS / 2), 0.5) &&
    crosshairProgress(crosshairStart, crosshairStart - 1) === 0 &&
    CROSSHAIR_SCALE_START === 0.6 &&
    CROSSHAIR_SCALE_END === 1.4 &&
    near(crosshairScale(0.5), 1.0) &&
    crosshairAlpha(0) === 1 &&
    crosshairAlpha(1) === 0 &&
    crosshairAlpha(-2) === 1 &&
    crosshairAlpha(2) === 0 &&
    (() => {
      const startFrame = crosshairFrame(crosshairStart, crosshairStart);
      const endFrame = crosshairFrame(crosshairStart, crosshairStart + CROSSHAIR_MS);
      return startFrame.visible && !endFrame.visible && endFrame.alpha === 0;
    })(),
);
check(
  "准星几何：CSS->后备像素（1x 不变、2x 翻倍、非法 DPR 按 1x）、半径按 DPR 放大",
  (() => {
    const point = { x: 137.5, y: 42.25 };
    const atOne = screenToBackingPixels(point, 1);
    const atTwo = screenToBackingPixels(point, 2);
    const invalid = screenToBackingPixels(point, Number.NaN);
    return (
      near(atTwo.x, 275) &&
      near(invalid.x, atOne.x) &&
      near(crosshairRadius(0, 1), CROSSHAIR_BASE_RADIUS_PX * 0.6) &&
      near(crosshairRadius(1, 2), CROSSHAIR_BASE_RADIUS_PX * 1.4 * 2)
    );
  })(),
);
check(
  "准星钳制与颜色：中央不动不缩、贴边内移 ≤ 2px 且半径缩到仍可见（1x/2x）、颜色纯白",
  (() => {
    const centred = clampCrosshairToCanvas({ x: 400, y: 300 }, 30, 800, 600);
    const atOne = clampCrosshairToCanvas({ x: 0, y: 0 }, 30, 800, 600);
    const atTwo = clampCrosshairToCanvas({ x: 0, y: 0 }, 60, 1_600, 1_200);
    const fits = (placed: { x: number; y: number; radius: number }, width: number, height: number) =>
      placed.radius > 0 &&
      placed.x + placed.radius <= width + 1e-9 &&
      placed.y + placed.radius <= height + 1e-9;
    return (
      near(centred.x, 400) &&
      near(centred.radius, 30) &&
      near(atOne.x, CROSSHAIR_EDGE_INSET_PX) &&
      near(atTwo.y, CROSSHAIR_EDGE_INSET_PX) &&
      fits(atOne, 800, 600) &&
      fits(atTwo, 1_600, 1_200) &&
      isGreyscaleHex(CROSSHAIR_COLOR) &&
      CROSSHAIR_COLOR === "#ffffff"
    );
  })(),
);

// ---------------------------------------------------------------------------------------------
// Selection ownership, classic building content, grid anchoring and click picking
// ---------------------------------------------------------------------------------------------

// BUG A: one verdict decides the tag, the actions and the message. A known id with an own unit is
// "own"; another player's unit is "other"; an unknown id is explicitly "not logged in" rather than
// "not yours", which is what the owner saw before the id was known.
const ownDecision = ownershipDecision(3, 3);
const otherDecision = ownershipDecision(2, 3);
const unknownDecision = ownershipDecision(3, null);
check(
  "归属判定：已知 id + 自己的单位 -> own，标签 我",
  ownDecision.kind === "own" && ownDecision.tag === "我" && ownDecision.ownership === "mine",
  `${ownDecision.kind} / ${ownDecision.tag}`,
);
check(
  "归属判定：已知 id + 别人的单位 -> other，标签 敌",
  otherDecision.kind === "other" && otherDecision.tag === "敌" && otherDecision.ownership === "enemy",
  `${otherDecision.kind} / ${otherDecision.tag}`,
);
check(
  "归属判定：未知 id -> logged-out，标签 未登录",
  unknownDecision.kind === "logged-out" &&
    unknownDecision.tag === "未登录" &&
    ownershipTagFor(3, null) === "未登录",
  `${unknownDecision.kind} / ${unknownDecision.tag}`,
);

// The inverse mapping: a snapshot with the sample-scene player 1 and the bot player 2, viewed by a
// logged-in human whose id is 1. The scene's units and buildings must tag as 我 (not 敌), and the bot's
// base must stay 敌. This is the owner's report in the one world where the human really is player 1.
const sceneSnapshot = worldViewFromSnapshot({
  ...validSnapshot,
  players: [
    { id: 1, name: "Player 1", gold: 300, wood: 60, anchor: [3, 2] },
    { id: 2, name: "Enemy", gold: 0, wood: 0, anchor: [192, 192] },
  ],
  entities: [
    { id: 11, kind: "unit.melee.t1", x: 1, y: 1, player: 1 },
    { id: 12, kind: "building.warehouse", x: 2, y: 2, player: 1 },
    { id: 20, kind: "building.warehouse", x: 3, y: 3, player: 2 },
  ],
});
const sceneEntityIds = [11, 12];
const sceneEntitiesOfPlayerOne =
  sceneSnapshot?.entities.filter((entity) => sceneEntityIds.includes(entity.id)) ?? [];
const botBaseEntity = sceneSnapshot?.entities.find((entity) => entity.id === 20);
check(
  "逆向归属：players 1+2、登录玩家 id=1 时，样例场景的实体按 id 全部是 我，不是 敌",
  sceneSnapshot !== null &&
    SCENE_PLAYER_ID === 1 &&
    sceneEntitiesOfPlayerOne.length === 2 &&
    sceneEntitiesOfPlayerOne.every(
      (entity) =>
        entity.playerId === 1 &&
        ownershipOf(entity.playerId, 1) === "mine" &&
        ownershipTagFor(entity.playerId, 1) === "我",
    ) &&
    sceneEntitiesOfPlayerOne.every((entity) => ownershipTagFor(entity.playerId, 1) !== "敌"),
  JSON.stringify(sceneEntitiesOfPlayerOne),
);
check(
  "逆向归属：同一快照里玩家 2 的基地是 敌，场景玩家 1 不是",
  botBaseEntity?.playerId === 2 &&
    ownershipOf(botBaseEntity.playerId, 1) === "enemy" &&
    ownershipTagFor(botBaseEntity.playerId, 1) === "敌" &&
    ownershipTagFor(1, 1) === "我",
  JSON.stringify(botBaseEntity),
);
check(
  "样例场景是展示阵营：真人不是 1 时它显示 中立（不是 敌），真正的敌人玩家 2 仍然是 敌",
  ownershipOf(SCENE_PLAYER_ID, 3) === "neutral" &&
    ownershipTagFor(SCENE_PLAYER_ID, 3) === "中立" &&
    ownershipTagFor(SCENE_PLAYER_ID, 3) !== "敌" &&
    ownershipTagFor(2, 3) === "敌" &&
    ownershipOf(2, 3) === "enemy",
  `${ownershipTagFor(SCENE_PLAYER_ID, 3)} / ${ownershipTagFor(2, 3)}`,
);

// ---------------------------------------------------------------------------------------------
// Enemy anchors: eight compass sectors, the exact readout, the edge arrow and the click target
// ---------------------------------------------------------------------------------------------

check(
  "八方向字符串与顺序固定：北,东北,东,东南,南,西南,西,西北",
  COMPASS_SECTORS.length === 8 &&
    COMPASS_SECTORS.join(",") === "北,东北,东,东南,南,西南,西,西北",
  COMPASS_SECTORS.join(","),
);
check(
  "八方向判定：北/东北/东/东南/南/西南/西/西北，零位移没有方向",
  compassSector(0, -1) === "北" &&
    compassSector(1, -1) === "东北" &&
    compassSector(1, 0) === "东" &&
    compassSector(1, 1) === "东南" &&
    compassSector(0, 1) === "南" &&
    compassSector(-1, 1) === "西南" &&
    compassSector(-1, 0) === "西" &&
    compassSector(-1, -1) === "西北" &&
    compassSector(0, 0) === null &&
    compassSector(Number.NaN, 1) === null,
  `${COMPASS_SECTORS.map((_, index) => {
    const directions: readonly [number, number][] = [
      [0, -1],
      [1, -1],
      [1, 0],
      [1, 1],
      [0, 1],
      [-1, 1],
      [-1, 0],
      [-1, -1],
    ];
    return compassSector(directions[index]![0], directions[index]![1]);
  }).join(",")}`,
);
// The top-chrome enemy line is gone (item 5); what remains is the shared anchor arithmetic the
// jumps and the minimap arrows still use: the tile distance, the compass sector and the camera target.
const arrowCamera = new Camera(16, 1, 32 * 16, 32 * 16);
const clickTarget = anchorCameraTarget({ x: 192, y: 192 }, 16);
check(
  "锚点跳转算术：距 (39,39) 到 (192,192) 是 216 格东南，镜头目标落在锚点格中心 (3080,3080)，负锚点同样成立",
  tileDistance({ x: 39, y: 39 }, { x: 192, y: 192 }) === 216 &&
    compassSector(192 - 39, 192 - 39) === "东南" &&
    clickTarget.x === 3080 &&
    clickTarget.y === 3080 &&
    anchorCameraTarget({ x: -1, y: -1 }, 16).x === -8 &&
    anchorCameraTarget({ x: -1, y: -1 }, 16).y === -8,
  JSON.stringify(clickTarget),
);

// ---------------------------------------------------------------------------------------------
// Bring me home: my own anchor row, the two wordings, and the enemy line's clamped jump
// ---------------------------------------------------------------------------------------------

const homeWorld = worldViewFromSnapshot({
  ...validSnapshot,
  originX: 0,
  originY: 0,
  width: 64,
  height: 64,
  tiles: Array.from({ length: 64 }, () => Array<string>(64).fill("grass")),
  players: [
    { id: 1, name: "Player 1", gold: 300, wood: 60, anchor: [3, 2] },
    { id: 2, name: "Enemy", gold: 0, wood: 0, anchor: [192, 192] },
    { id: 3, name: "owner1", gold: 300, wood: 60, anchor: [40, 40] },
  ],
});
const homeRows = homeWorld?.players;
const myAnchor = ownAnchorOf(homeRows, 3);
check(
  "我方基地：只读自己 id 那一行；未登录 / 旧快照 / 无 anchor 都答 null；状态行文案明确说 anchor（顶部 home 行已删，item 5）",
  myAnchor?.x === 40 &&
    ownAnchorOf(undefined, 3) === null &&
    ownAnchorOf(homeRows, null) === null &&
    ownAnchorOf([{ id: 3, name: "owner1", gold: 0, wood: 0 }], 3) === null &&
    ownAnchorOf(homeRows, 9) === null &&
    homeAnchorUnknownText().includes("anchor") &&
    homeAnchorUnknownText().includes("未登录"),
);
const homeTarget = homeCameraTarget({ x: 192, y: 192 }, 16);
check(
  "H / 基地 的镜头目标与锚点算术一致：锚点格中心 (192.5,192.5) → 世界像素 (3080,3080)",
  homeTarget.x === 3080 &&
    homeTarget.y === 3080 &&
    homeTarget.x === anchorCameraTarget({ x: 192, y: 192 }, 16).x &&
    homeTarget.y === anchorCameraTarget({ x: 192, y: 192 }, 16).y,
  JSON.stringify(homeTarget),
);
const homeArrowGeometry =
  homeWorld === null
    ? null
    : minimapEdgeGeometry(homeWorld, arrowCamera, { x: 192, y: 192 }, MINIMAP_PANEL_SIZE);
const markerGeometry =
  homeWorld === null
    ? null
    : markerEdgeArrow(homeWorld, arrowCamera, { number: 1, tile: { x: 192, y: 192 }, dimension: "overworld" }, MINIMAP_PANEL_SIZE);
check(
  "小地图白色回家/标记箭头与共享几何一致：扇区 东南、距离 226、落点与角度相同",
  homeArrowGeometry !== null &&
    homeArrowGeometry.sector === "东南" &&
    homeArrowGeometry.distance === 226 &&
    markerGeometry !== null &&
    homeArrowGeometry.x === markerGeometry.x &&
    homeArrowGeometry.y === markerGeometry.y &&
    Math.abs(homeArrowGeometry.angle - markerGeometry.angle) < 1e-12,
  JSON.stringify(homeArrowGeometry),
);

// ---------------------------------------------------------------------------------------------
// Personal markers: numbering, cap, duplicate rule, removal, nearest-outside arrow and labels
// ---------------------------------------------------------------------------------------------

let markerList: readonly Marker[] = clearMarkers();
const firstMarker = placeMarker(markerList, { x: 10, y: 20 });
if (firstMarker.kind === "placed") {
  markerList = firstMarker.markers;
}
const secondMarker = placeMarker(markerList, { x: 11.9, y: 20.8 });
if (secondMarker.kind === "placed") {
  markerList = secondMarker.markers;
}
check(
  "标记：按放置顺序编号 1..n，坐标按地块向下取整",
  firstMarker.kind === "placed" &&
    firstMarker.marker.number === 1 &&
    secondMarker.kind === "placed" &&
    secondMarker.marker.number === 2 &&
    secondMarker.marker.tile.x === 11 &&
    secondMarker.marker.tile.y === 20 &&
    markerList.length === 2,
  `${markerList.length} / ${formatMarkerLabel(markerList[1]!, { x: 0, y: 0 })}`,
);
const duplicateMarker = placeMarker(markerList, { x: 11, y: 20 });
check(
  "标记：同一地块的重复放置被忽略，已有编号被报出且列表不变",
  duplicateMarker.kind === "duplicate" &&
    duplicateMarker.marker.number === 2 &&
    duplicateMarker.markers.length === 2 &&
    markerList.length === 2,
  duplicateMarker.kind,
);
let cappedMarkers: readonly Marker[] = clearMarkers();
for (let index = 0; index < MAX_MARKERS; index += 1) {
  const placed = placeMarker(cappedMarkers, { x: index, y: 0 });
  if (placed.kind === "placed") {
    cappedMarkers = placed.markers;
  }
}
const overflowMarker = placeMarker(cappedMarkers, { x: MAX_MARKERS, y: 0 });
check(
  `标记：上限 ${MAX_MARKERS} 个，第 ${MAX_MARKERS + 1} 个被拒绝且列表不变`,
  cappedMarkers.length === MAX_MARKERS &&
    cappedMarkers[MAX_MARKERS - 1]?.number === MAX_MARKERS &&
    overflowMarker.kind === "full" &&
    overflowMarker.markers.length === MAX_MARKERS,
  `${cappedMarkers.length} / ${overflowMarker.kind}`,
);
const removal = removeLastMarker(markerList);
check(
  "标记：删除最近一个（编号最大），剩下的编号保持连续",
  removal.removed?.number === 2 &&
    removal.markers.length === 1 &&
    removal.markers[0]?.number === 1,
  JSON.stringify(removal.markers),
);
check(
  "标记：空列表删除答 null，清除全部得到空列表",
  removeLastMarker(clearMarkers()).removed === null && clearMarkers().length === 0,
);
const labelMarker: Marker = { number: 2, tile: { x: 40, y: 40 }, dimension: "overworld" };
check(
  "标记标签：标记 2：距 11 格，东南，扇区来自敌方行的 compassSector",
  formatMarkerLabel(labelMarker, { x: 32, y: 32 }) === "标记 2：距 11 格，东南" &&
    compassSector(40 - 32, 40 - 32) === "东南",
  formatMarkerLabel(labelMarker, { x: 32, y: 32 }),
);
check(
  "标记标签：标记就在镜头中心时没有方向，只写距离",
  formatMarkerLabel({ number: 1, tile: { x: 32, y: 32 }, dimension: "overworld" }, { x: 32, y: 32 }) === "标记 1：距 0 格",
);
check(
  "标记跳转的镜头目标是地块中心并受安全边界钳制，与回家跳转同一算术",
  markerCameraTarget({ number: 3, tile: { x: 192, y: 192 }, dimension: "overworld" }, 16).x ===
    3080 &&
    markerCameraTarget({ number: 3, tile: { x: -1, y: -1 }, dimension: "overworld" }, 16).x === -8 &&
    markerCameraTarget({ number: 3, tile: { x: 192, y: 192 }, dimension: "overworld" }, 16).x ===
      homeCameraTarget({ x: 192, y: 192 }, 16).x,
);
const insideMarker: Marker = { number: 1, tile: { x: 40, y: 40 }, dimension: "overworld" };
const farMarker: Marker = { number: 2, tile: { x: 192, y: 192 }, dimension: "overworld" };
const eastMarker: Marker = { number: 3, tile: { x: 70, y: 32 }, dimension: "overworld" };
const tieMarker: Marker = { number: 4, tile: { x: 32, y: 70 }, dimension: "overworld" };
const nearestOutside =
  homeWorld === null
    ? null
    : nearestOutsideMarker(homeWorld, { x: 32, y: 32 }, [insideMarker, farMarker, eastMarker]);
const tiedOutside =
  homeWorld === null
    ? null
    : nearestOutsideMarker(homeWorld, { x: 32, y: 32 }, [eastMarker, tieMarker]);
check(
  "标记：窗口内的标记不参与“窗外箭头”，多个窗外标记只取最近的一个",
  nearestOutside?.number === 3 &&
    homeWorld !== null &&
    nearestOutsideMarker(homeWorld, { x: 32, y: 32 }, [insideMarker]) === null &&
    nearestOutsideMarker(homeWorld, { x: 32, y: 32 }, []) === null &&
    tiedOutside?.number === 3,
  JSON.stringify(nearestOutside),
);
const markerArrow =
  homeWorld === null ? null : markerEdgeArrow(homeWorld, arrowCamera, farMarker, MINIMAP_PANEL_SIZE);
check(
  "标记边缘箭头：窗口外的标记有与敌方箭头相同的几何，并带上自己的编号",
  markerArrow !== null &&
    markerArrow.number === 2 &&
    markerArrow.sector === "东南" &&
    markerArrow.distance === 226 &&
    markerArrow.x > MINIMAP_PANEL_SIZE / 2 &&
    markerArrow.y > MINIMAP_PANEL_SIZE / 2 &&
    markerArrow.x === homeArrowGeometry?.x &&
    markerArrow.y === homeArrowGeometry?.y,
  JSON.stringify(markerArrow),
);
check(
  "标记边缘箭头：窗口内的标记没有箭头，最近窗外规则只产生一个箭头",
  homeWorld !== null &&
    markerEdgeArrow(homeWorld, arrowCamera, insideMarker, MINIMAP_PANEL_SIZE) === null,
);
check(
  "标记记住维度（item 18）：放置写入维度、另一维度的标记恒为窗外、同维标记按窗口判断",
  (() => {
    const placed = placeMarker(clearMarkers(), { x: 5, y: 6 }, "hell");
    const hellWorldDimension = { dimension: "hell" } as WorldView;
    const overworldDimension = { dimension: "overworld" } as WorldView;
    const hellMarker = placed.kind === "placed" ? placed.marker : null;
    if (hellMarker === null) {
      return false;
    }
    return (
      placed.kind === "placed" &&
      hellMarker.dimension === "hell" &&
      placeMarker(clearMarkers(), { x: 5, y: 6 }, "overworld").kind === "placed" &&
      worldDimensionKey(hellWorldDimension) === "hell" &&
      !markerInDrawnDimension(hellMarker, overworldDimension) &&
      markerInDrawnDimension({ ...hellMarker, dimension: "overworld" }, overworldDimension)
    );
  })(),
);
check(
  "跨维度标记的标签带上维度名；同维标签保持纯距离+方向",
  formatMarkerLabel({ number: 2, tile: { x: 40, y: 40 }, dimension: "hell" }, { x: 32, y: 32 }, "地狱") ===
    "标记 2：距 11 格，东南 · 地狱" &&
    formatMarkerLabel(labelMarker, { x: 32, y: 32 }) === "标记 2：距 11 格，东南",
  formatMarkerLabel({ number: 2, tile: { x: 40, y: 40 }, dimension: "hell" }, { x: 32, y: 32 }, "地狱"),
);
const nearestMarkerArrow =
  homeWorld === null
    ? null
    : nearestOutsideMarkerArrow(
        homeWorld,
        arrowCamera,
        [insideMarker, farMarker, eastMarker],
        MINIMAP_PANEL_SIZE,
      );
check(
  "标记边缘箭头：只画最近的那一个（编号 3，东侧 38 格）",
  nearestMarkerArrow !== null &&
    nearestMarkerArrow.number === 3 &&
    nearestMarkerArrow.sector === "东" &&
    nearestMarkerArrow.distance === 38,
  JSON.stringify(nearestMarkerArrow),
);

check(
  "WHOAMI 丢失时用 players 里的账号名恢复玩家 id；名字不匹配则不猜",
  localPlayerIdFromRoster(
    [
      { id: 1, name: "Player 1", gold: 0, wood: 0 },
      { id: 3, name: "owner1", gold: 300, wood: 60 },
    ],
    "owner1",
  ) === 3 &&
    localPlayerIdFromRoster(
      [
        { id: 1, name: "Player 1", gold: 0, wood: 0 },
        { id: 3, name: "owner1", gold: 300, wood: 60 },
      ],
      "someone-else",
    ) === null &&
    localPlayerIdFromRoster(undefined, "owner1") === null &&
    localPlayerIdFromRoster([{ id: 3, name: "owner1", gold: 0, wood: 0 }], null) === null,
);
const ownUnitFixture: EntityView = { id: 31, kind: "unit.melee.t1", x: 5.5, y: 5.5, playerId: 3 };
const otherUnitFixture: EntityView = { id: 32, kind: "unit.melee.t1", x: 6.5, y: 6.5, playerId: 2 };
const ownUnitCommand = commandBarModel([ownUnitFixture], 3);
const otherUnitCommand = commandBarModel([otherUnitFixture], 3);
const loggedOutCommand = commandBarModel([ownUnitFixture], null);
check(
  "自己的单位：标签 我、提供停止/攻击移动，提示里没有“不属于你”",
  ownUnitCommand.ownershipTag === "我" &&
    ownUnitCommand.ownership === "own" &&
    ownUnitCommand.actions.map((action) => action.kind).join(",") === "stop,attack-move" &&
    !ownUnitCommand.hint.includes("不属于你"),
  ownUnitCommand.hint,
);
check(
  "别人的单位：标签 敌、没有单位动作、提示明确说“不属于你”",
  otherUnitCommand.ownershipTag === "敌" &&
    otherUnitCommand.ownership === "other" &&
    otherUnitCommand.actions.length === 0 &&
    otherUnitCommand.hint.includes("不属于你"),
  otherUnitCommand.hint,
);
check(
  "未知玩家 id：标签 未登录、没有动作、提示要求先 LOGIN（不是“不属于你”）",
  loggedOutCommand.ownershipTag === "未登录" &&
    loggedOutCommand.ownership === "logged-out" &&
    loggedOutCommand.actions.length === 0 &&
    loggedOutCommand.hint.includes("尚未登录") &&
    !loggedOutCommand.hint.includes("不属于你"),
  loggedOutCommand.hint,
);

// BUG E: several own engineers offer every served BUILD entry, and the first engineer is the
// placement engineer. A mixed engineer + combat group must not silently pick one. There is no
// fallback catalogue (item 1), so the entries come from the served `buildable` array alone.
const engineerA: EntityView = { id: 41, kind: "unit.engineer", x: 5.5, y: 5.5, playerId: 3 };
const engineerB: EntityView = { id: 42, kind: "unit.engineer", x: 6.5, y: 5.5, playerId: 3 };
const engineerC: EntityView = { id: 43, kind: "unit.engineer", x: 7.5, y: 5.5, playerId: 3 };
const servedGroupCatalogue = ["building.hell_gate", "building.mine.t1", "building.warehouse"];
const multiEngineerCommand = commandBarModel(
  [engineerA, engineerB, engineerC],
  3,
  { buildingPaths: servedGroupCatalogue },
);
const engineerBuildActions = multiEngineerCommand.actions.filter((action) => action.kind === "build");
check(
  "选中多个工兵时仍显示全部 served BUILD 条目（无回退目录），条目都在 context 区",
  multiEngineerCommand.kind === "engineer" &&
    engineerBuildActions.map((action) => action.path).join(",") === servedGroupCatalogue.join(",") &&
    engineerBuildActions.every((action) => action.zone === "context"),
  `${multiEngineerCommand.kind} / ${engineerBuildActions.map((action) => action.path).join(",")}`,
);
check(
  "多个工兵时用选中的第一个工兵判断幽灵射程，但整组都进入放置顺序；混合单位组不选工兵",
  engineerForPlacement([engineerA, engineerB, engineerC], 3)?.id === engineerA.id &&
    engineersForPlacement([engineerA, engineerB, engineerC], 3)
      .map((engineer) => engineer.id)
      .join(",") === "41,42,43" &&
    engineersForPlacement([engineerA, ownUnitFixture], 3).length === 0 &&
    engineersForPlacement([], 3).length === 0,
  engineersForPlacement([engineerA, engineerB], 3)
    .map((engineer) => engineer.id)
    .join(","),
);
check(
  "两个工兵选中后同一次放置生成两条 BUILD，全部指向同一个足迹",
  (() => {
    const lines = buildBatch([engineerA.id, engineerB.id], "building.armory.t1", 5, 6);
    return lines.length === 2 &&
      lines[0] === "BUILD 41 building.armory.t1 5 6" &&
      lines[1] === "BUILD 42 building.armory.t1 5 6" &&
      throwsChinese(() => buildBatch([], "building.armory.t1", 5, 6));
  })(),
  buildBatch([engineerA.id, engineerB.id], "building.armory.t1", 5, 6).join(" | "),
);
check(
  "工程师的 BUILD 目录与面板一致，每个路径都可以生成 BUILD 命令",
  CLASSIC_BUILDING_KINDS.every((path) => build(1, path, 0, 0) === `BUILD 1 ${path} 0 0`),
  CLASSIC_BUILDING_KINDS.join(","),
);

// BUG B: every classic building kind has its own content entry and its own train list, and - under the
// per-material schema - its own PNG file, so no two kinds ever draw from the same art. The old manifest
// pointed lumber camp t1/t2/t3 and the warehouse at the armory/mine cells, and the panel reused the
// armory's unit list for every building.
if (classicAtlas.ok) {
  const buildingEntries = CLASSIC_BUILDING_KINDS.map(
    (kind) => classicAtlas.data.entities.get(kind) ?? null,
  );
  check(
    "每个经典建筑 kind 都有自己的材质文件与至少一帧",
    buildingEntries.every((entry) => entry !== null && entry.image !== "" && entry.frames >= 1),
    buildingEntries.map((entry) => (entry === null ? "missing" : entry.image)).join(" | "),
  );
  check(
    "没有两个经典建筑共用同一个材质文件",
    new Set(buildingEntries.map((entry) => (entry === null ? "" : entry.image))).size ===
      CLASSIC_BUILDING_KINDS.length,
    buildingEntries.map((entry) => (entry === null ? "" : entry.image)).join(" | "),
  );
} else {
  console.info("经典清单仍是旧版格式，跳过每个建筑一个材质文件的校验（PNG 拆分落地后生效）");
}
// The train menu is the building row's `trains` array now; assert the parse over a served armory row.
const trainsWorld = worldViewFromSnapshot({
  tick: 30,
  width: 1,
  height: 1,
  tiles: [["grass"]],
  entities: [
    {
      id: 21,
      kind: "oc:building.armory.t1",
      x: 0.5,
      y: 0.5,
      player: 1,
      trains: [
        { path: "oc:unit.melee.t1", name: "普通", gold: 20, level: 1 },
        { path: "oc:unit.ranged.t3", name: "弓手·精英", gold: 120, level: 1 },
        { path: "oc:unit.engineer.t5", name: "工程师·战王", gold: 300, level: 2 },
      ],
    },
    {
      id: 22,
      kind: "building.mine.t1",
      x: 1.5,
      y: 0.5,
      player: 1,
      trains: [{ path: "", gold: -5, level: "two" }],
    },
  ],
});
check(
  "UPGRADEUNIT 命令构建器：正整数生成 UPGRADEUNIT <id>，零/负数/小数抛中文错误",
  upgradeUnit(9) === "UPGRADEUNIT 9" &&
    throwsChinese(() => upgradeUnit(0)) &&
    throwsChinese(() => upgradeUnit(-2)) &&
    throwsChinese(() => upgradeUnit(1.5)),
  upgradeUnit(9),
);
check(
  "快照解析建筑行的 trains 菜单（路径去命名空间、坏行丢弃、缺字段不出现）",
  trainsWorld !== null &&
    trainsWorld.entities[0]?.trains?.length === 3 &&
    trainsWorld.entities[0]?.trains[0]?.path === "unit.melee.t1" &&
    trainsWorld.entities[0]?.trains[0]?.name === "普通" &&
    trainsWorld.entities[0]?.trains[0]?.gold === 20 &&
    trainsWorld.entities[0]?.trains[0]?.level === 1 &&
    trainsWorld.entities[0]?.trains[2]?.level === 2 &&
    trainsWorld.entities[1]?.trains?.length === 0,
  JSON.stringify(trainsWorld?.entities.map((entity) => entity.trains)),
);

// BUG C: centre coordinates -> footprint origin is exactly one conversion, and the BUILD ghost and a
// click use it. A 3x3 building at 6.5 owns tiles 5..7, a unit at 5.5 owns tile 5.
const tileRoundTrip = worldToTile(tileToWorld({ x: -3, y: 4 }, 32).x, tileToWorld({ x: -3, y: 4 }, 32).y, 32);
check(
  "tileToWorld / worldToTile 往返：整数与负坐标保持不变",
  tileRoundTrip.x === -3 && tileRoundTrip.y === 4,
  `${tileRoundTrip.x},${tileRoundTrip.y}`,
);
check(
  "单位中心 5.5 落在 tile 5，3x3 建筑中心 6.5 的左上角是 tile 5",
  worldToTile(5.5 * 32, 5.5 * 32, 32).x === 5 &&
    entityFootprintOrigin({ id: 1, kind: "building.armory.t1", x: 6.5, y: 6.5, footprint: { width: 3, height: 3 } }).x === 5 &&
    entityFootprintWidth({ id: 1, kind: "building.armory.t1", x: 6.5, y: 6.5, footprint: { width: 3, height: 3 } }) === 3 &&
    entityFootprintHeight({ id: 1, kind: "unit.engineer", x: 5.5, y: 5.5 }) === 1,
);
const footprintThreeByThree = footprintTiles({ x: 5, y: 5 }, { width: 3, height: 3 });
check(
  "3x3 足迹锚在整数 tile (5,5) 时恰好覆盖 5..7 两轴的全部九格",
  footprintThreeByThree.length === 9 &&
    footprintThreeByThree.every((tile) => tile.x >= 5 && tile.x <= 7 && tile.y >= 5 && tile.y <= 7) &&
    JSON.stringify(footprintThreeByThree) ===
      JSON.stringify(
        [5, 6, 7].flatMap((y) => [5, 6, 7].map((x) => ({ x, y }))),
      ),
  JSON.stringify(footprintThreeByThree),
);
const threeByThreeBuilding: EntityView = {
  id: 51,
  kind: "building.armory.t1",
  x: 6.5,
  y: 6.5,
  playerId: 3,
  footprint: { width: 3, height: 3 },
};
check(
  "占用判定用足迹左上角：3x3 建筑覆盖 5..7，不覆盖 4 或 8",
  entityFootprintCovers(threeByThreeBuilding, 5, 5) &&
    entityFootprintCovers(threeByThreeBuilding, 7, 7) &&
    !entityFootprintCovers(threeByThreeBuilding, 4, 5) &&
    !entityFootprintCovers(threeByThreeBuilding, 8, 8) &&
    entityCoversTile(threeByThreeBuilding, 6, 6) &&
    !entityCoversTile(threeByThreeBuilding, 8, 6),
);

// Click picking: a unit's visible body (not only its feet) selects it, and any tile of a 3x3 footprint
// selects that building while an outside tile does not.
function pickWorldOf(entities: readonly EntityView[]): WorldView {
  return {
    originX: 0,
    originY: 0,
    width: 24,
    height: 24,
    tiles: Array.from({ length: 24 }, () =>
      Array.from({ length: 24 }, () => "grass" as TerrainId),
    ),
    entities,
  };
}
const pickCamera = new Camera(32, 1, 10.5 * 32, 10.5 * 32);
pickCamera.setViewport(800, 600);
const unitPickWorld = pickWorldOf([{ id: 61, kind: "unit.melee.t1", x: 10.5, y: 10.5, playerId: 3 }]);
const unitBodyPoint = pickCamera.worldToScreen(10.5 * 32, 10.1 * 32);
const unitFootPoint = pickCamera.worldToScreen(10.5 * 32, 10.05 * 32);
const unitMissPoint = pickCamera.worldToScreen(13.5 * 32, 13.5 * 32);
check(
  "点击单位可见身体选中该单位；点空地不选中",
  pickEntityAt(unitPickWorld, pickCamera, unitBodyPoint.x, unitBodyPoint.y) === 61 &&
    pickEntityAt(unitPickWorld, pickCamera, unitFootPoint.x, unitFootPoint.y) === 61 &&
    pickEntityAt(unitPickWorld, pickCamera, unitMissPoint.x, unitMissPoint.y) === null,
  `${pickEntityAt(unitPickWorld, pickCamera, unitBodyPoint.x, unitBodyPoint.y)}`,
);
const buildingPickWorld = pickWorldOf([threeByThreeBuilding]);
const buildingCornerPoint = pickCamera.worldToScreen(5.2 * 32, 5.2 * 32);
const buildingCentrePoint = pickCamera.worldToScreen(6.5 * 32, 6.5 * 32);
const buildingOutsidePoint = pickCamera.worldToScreen(9.2 * 32, 9.2 * 32);
check(
  "3x3 建筑的任意足迹 tile 都能点中，足迹外点不中",
  pickEntityAt(buildingPickWorld, pickCamera, buildingCornerPoint.x, buildingCornerPoint.y) === 51 &&
    pickEntityAt(buildingPickWorld, pickCamera, buildingCentrePoint.x, buildingCentrePoint.y) === 51 &&
    pickEntityAt(buildingPickWorld, pickCamera, buildingOutsidePoint.x, buildingOutsidePoint.y) === null,
  `${pickEntityAt(buildingPickWorld, pickCamera, buildingCornerPoint.x, buildingCornerPoint.y)}`,
);
const mixedPickWorld = pickWorldOf([
  threeByThreeBuilding,
  { id: 62, kind: "unit.melee.t1", x: 6.5, y: 6.5, playerId: 3 },
]);
const unitOverBuildingPoint = pickCamera.worldToScreen(6.5 * 32, 6.1 * 32);
check(
  "单位与建筑足迹重叠时优先选中单位（可见身体优先）",
  pickEntityAt(mixedPickWorld, pickCamera, unitOverBuildingPoint.x, unitOverBuildingPoint.y) === 62,
  String(pickEntityAt(mixedPickWorld, pickCamera, unitOverBuildingPoint.x, unitOverBuildingPoint.y)),
);

// BUG D client half: STOP and the following move/build builders stay valid. The actual "a stop then a
// move/build applies" assertion lives in ClassicCheck against a real CommandService.
check(
  "STOP 之后仍能生成合法的 MOVE 与 BUILD 命令",
  orderStop(7) === "ORDER STOP 7" &&
    orderMove(7, 8, 9) === "ORDER MOVE 7 8 9" &&
    build(7, "building.lumbercamp.t1", 10, 11) === "BUILD 7 building.lumbercamp.t1 10 11",
);

// ---------------------------------------------------------------------------------------------
// LaTeX segments: the inline `$...$` / display `$$...$$` splitter, plain-text fallback and KaTeX
// ---------------------------------------------------------------------------------------------

/** A compact, readable form of a split result for failure details. */
function segmentKinds(segments: readonly TexSegment[]): string {
  return segments.map((segment) => `${segment.kind}:${segment.text}`).join(" | ");
}

const noDollarSegments = splitTex("plain text, no maths");
check(
  "不含 $ 的字符串恰好产生一个纯文本片段",
  noDollarSegments.length === 1 &&
    noDollarSegments[0]?.kind === "text" &&
    noDollarSegments[0]?.text === "plain text, no maths",
  segmentKinds(noDollarSegments),
);
check(
  "空字符串与单独的 $ 都是纯文本（函数始终有返回值）",
  JSON.stringify(splitTex("")) === JSON.stringify([{ kind: "text", text: "" }]) &&
    JSON.stringify(splitTex("$")) === JSON.stringify([{ kind: "text", text: "$" }]),
  segmentKinds(splitTex("$")),
);

const inlineSegments = splitTex("area $x^2$ end");
check(
  "一个行内公式：前后纯文本 + 去掉了 $ 的 TeX 源码",
  JSON.stringify(inlineSegments) ===
    JSON.stringify([
      { kind: "text", text: "area " },
      { kind: "inline", text: "x^2" },
      { kind: "text", text: " end" },
    ]),
  segmentKinds(inlineSegments),
);
check(
  "两个行内公式保留中间纯文本",
  JSON.stringify(splitTex("$a$ + $b$")) ===
    JSON.stringify([
      { kind: "inline", text: "a" },
      { kind: "text", text: " + " },
      { kind: "inline", text: "b" },
    ]),
  segmentKinds(splitTex("$a$ + $b$")),
);

const displaySegments = splitTex("before $$x^2$$ after");
check(
  "独立公式：$$ 优先于 $，且前后文本保留",
  JSON.stringify(displaySegments) ===
    JSON.stringify([
      { kind: "text", text: "before " },
      { kind: "display", text: "x^2" },
      { kind: "text", text: " after" },
    ]),
  segmentKinds(displaySegments),
);

const escapedSegments = splitTex("price \\$5");
check(
  "转义美元 $ 是字面美元，永远不开始数学",
  escapedSegments.length === 1 &&
    escapedSegments[0]?.kind === "text" &&
    escapedSegments[0]?.text === "price $5",
  segmentKinds(escapedSegments),
);
check(
  "转义美元之后仍可开始一个真正的行内公式",
  JSON.stringify(splitTex("\\$$x$")) ===
    JSON.stringify([
      { kind: "text", text: "$" },
      { kind: "inline", text: "x" },
    ]),
  segmentKinds(splitTex("\\$$x$")),
);
check(
  "未闭合的 $ 与 $$ 保持纯文本",
  JSON.stringify(splitTex("cost $5")) === JSON.stringify([{ kind: "text", text: "cost $5" }]) &&
    JSON.stringify(splitTex("$$open")) === JSON.stringify([{ kind: "text", text: "$$open" }]),
  `${segmentKinds(splitTex("cost $5"))} / ${segmentKinds(splitTex("$$open"))}`,
);
check(
  "空的 $$$$ 与 $$  $$（含空白）对都按纯文本忽略",
  JSON.stringify(splitTex("$$$$")) === JSON.stringify([{ kind: "text", text: "$$$$" }]) &&
    JSON.stringify(splitTex("a $$  $$ b")) ===
      JSON.stringify([{ kind: "text", text: "a $$  $$ b" }]),
  `${segmentKinds(splitTex("$$$$"))} / ${segmentKinds(splitTex("a $$  $$ b"))}`,
);

const mixedSegments = splitTex("a $x$ b $$y$$ c");
check(
  "混合文本：纯文本 / 行内 / 纯文本 / 独立 / 纯文本按顺序排列",
  JSON.stringify(mixedSegments) ===
    JSON.stringify([
      { kind: "text", text: "a " },
      { kind: "inline", text: "x" },
      { kind: "text", text: " b " },
      { kind: "display", text: "y" },
      { kind: "text", text: " c" },
    ]),
  segmentKinds(mixedSegments),
);
check(
  "拆分器是全集：任何字符串都不会抛出，且总返回至少一个片段",
  (() => {
    const inputs = ["", "$", "$$", "$$$", "\\", "\\$", "$x", "x$", "$$x", "$$$$$", "a $ b $$ c"];
    try {
      return inputs.every((input) => {
        const segments = splitTex(input);
        return (
          segments.length > 0 &&
          (input === "" || segments.some((segment) => segment.text !== ""))
        );
      });
    } catch {
      return false;
    }
  })(),
);

check(
  "纯文本回退转义 < > & 引号，并保留公式的 $ 定界符",
  escapeHtml("<b>&\"'") === "&lt;b&gt;&amp;&quot;&#39;" &&
    plainTexFallback("inline", "<b> & </b>") ===
      escapeHtml("$<b> & </b>$") &&
    plainTexFallback("display", "<b> & </b>") ===
      escapeHtml("$$<b> & </b>$$") &&
    plainTexFallback("inline", "<b> & </b>").includes("&lt;b&gt;") &&
    plainTexFallback("inline", "<b> & </b>").includes("&amp;") &&
    !plainTexFallback("inline", "<b> & </b>").includes("<b>"),
  plainTexFallback("inline", "<b> & </b>"),
);

const missingKatex = await renderTexSegment("inline", "<x> & y", async () => null);
check(
  "KaTeX 不可用时回退为转义纯文本，而不是抛出",
  missingKatex === plainTexFallback("inline", "<x> & y") &&
    missingKatex.includes("&lt;x&gt;") &&
    missingKatex.includes("&amp;"),
  missingKatex,
);
const throwingKatex = await renderTexSegment("display", "<x> & y", async () => ({
  renderToString: (): string => {
    throw new Error("synthetic KaTeX failure");
  },
}));
check(
  "KaTeX 抛出异常时同样回退为转义纯文本",
  throwingKatex === plainTexFallback("display", "<x> & y") &&
    throwingKatex.includes("&lt;") &&
    throwingKatex.includes("&amp;"),
  throwingKatex,
);

const renderedInline = await renderTexSegment("inline", "x^2");
const renderedDisplay = await renderTexSegment("display", "x^2");
check(
  "可用时用 KaTeX 渲染：行内输出 katex，独立公式输出 katex-display",
  renderedInline.includes('class="katex"') &&
    !renderedInline.includes("katex-display") &&
    renderedDisplay.includes('class="katex"') &&
    renderedDisplay.includes("katex-display"),
  `${renderedInline.slice(0, 60)} / ${renderedDisplay.slice(0, 80)}`,
);

// ---------------------------------------------------------------------------------------------
// Item 20-22: repair/reclaim entries, route commands, path parsing and path rendering math
// ---------------------------------------------------------------------------------------------

check(
  "单点 ORDER MOVE 与旧的 orderMove 字节完全相同",
  orderMove(7, 3, -2) === "ORDER MOVE 7 3 -2" &&
    orderMoveRoute(7, [{ x: 3, y: -2 }]) === orderMove(7, 3, -2),
  `${orderMove(7, 3, -2)} / ${orderMoveRoute(7, [{ x: 3, y: -2 }])}`,
);
check(
  "两点与三点 ORDER MOVE 按点顺序拼接坐标",
  orderMoveRoute(7, [{ x: 1, y: 2 }, { x: 3, y: 4 }]) === "ORDER MOVE 7 1 2 3 4" &&
    orderMoveRoute(7, [{ x: 1, y: 2 }, { x: 3, y: 4 }, { x: -5, y: 6 }]) ===
      "ORDER MOVE 7 1 2 3 4 -5 6",
  orderMoveRoute(7, [{ x: 1, y: 2 }, { x: 3, y: 4 }, { x: -5, y: 6 }]),
);
check(
  "九个路径点被客户端拒绝，空路线也被拒绝",
  MAX_MOVE_POINTS === 8 &&
    (() => {
      try {
        orderMoveRoute(7, Array.from({ length: 9 }, (_, index) => ({ x: index, y: 0 })));
        return false;
      } catch {
        return true;
      }
    })() &&
    (() => {
      try {
        orderMoveRoute(7, []);
        return false;
      } catch {
        return true;
      }
    })(),
  `MAX_MOVE_POINTS=${MAX_MOVE_POINTS}`,
);
check(
  "REPAIR 与 RECLAIM 生成正确的命令行，非法 id 在本地拒绝",
  repair(9, 12) === "REPAIR 9 12" &&
    reclaim(12) === "RECLAIM 12" &&
    (() => {
      try {
        repair(0, 12);
        return false;
      } catch {
        return true;
      }
    })(),
  `${repair(9, 12)} / ${reclaim(12)}`,
);
check(
  "TRAVEL 一行携带全部单位 id 与网关 id；重复/非法 id 在本地拒绝（HAUL 已随服务端删除）",
  travel([4, 5, 6], 9) === "TRAVEL 4 5 6 9" &&
    travel([4], 9) === "TRAVEL 4 9" &&
    (() => {
      try {
        travel([], 9);
        travel([4, 4], 9);
        travel([0], 9);
        travel([4], 0);
        return false;
      } catch {
        return true;
      }
    })(),
  travel([4, 5], 9),
);
check(
  "ASSIST 一行一个工兵与建筑；非法 id 在本地拒绝",
  assist(9, 12) === "ASSIST 9 12" &&
    (() => {
      try {
        assist(0, 12);
        assist(9, -1);
        return false;
      } catch {
        return true;
      }
    })(),
  assist(9, 12),
);

const pathPayload = worldViewFromSnapshot({
  tick: 1,
  originX: 0,
  originY: 0,
  width: 2,
  height: 2,
  tiles: [["grass", "grass"], ["grass", "grass"]],
  entities: [
    {
      id: 1,
      kind: "unit.melee.t1",
      x: 1.5,
      y: 1.5,
      player: 1,
      path: [[0, 0], [1, 1]],
      target: [5, 5],
    },
  ],
});
const pathEntity = pathPayload?.entities[0];
check(
  "快照 path / target 被解析为绝对瓦片点",
  pathEntity?.path?.length === 2 &&
    pathEntity.path[0]?.x === 0 &&
    pathEntity.path[1]?.y === 1 &&
    pathEntity.pathTarget?.x === 5 &&
    pathEntity.pathTarget?.y === 5,
  JSON.stringify(pathEntity?.path) + " -> " + JSON.stringify(pathEntity?.pathTarget),
);
const overlongPayload = worldViewFromSnapshot({
  tick: 1,
  originX: 0,
  originY: 0,
  width: 2,
  height: 2,
  tiles: [["grass", "grass"], ["grass", "grass"]],
  entities: [
    {
      id: 1,
      kind: "unit.melee.t1",
      x: 1.5,
      y: 1.5,
      player: 1,
      path: Array.from({ length: 100 }, (_, index) => [index, 0]),
    },
  ],
});
check(
  "超长快照 path 按上限截断",
  overlongPayload?.entities[0]?.path?.length === MAX_SNAPSHOT_PATH_POINTS &&
    MAX_SNAPSHOT_PATH_POINTS === 64,
  String(overlongPayload?.entities[0]?.path?.length),
);
const malformedPathPayload = worldViewFromSnapshot({
  tick: 1,
  originX: 0,
  originY: 0,
  width: 2,
  height: 2,
  tiles: [["grass", "grass"], ["grass", "grass"]],
  entities: [
    {
      id: 1,
      kind: "unit.melee.t1",
      x: 1.5,
      y: 1.5,
      player: 1,
      path: [[0, 0], ["x", 1], [2.5, 3], [4, 5]],
      target: ["bad", 1],
    },
  ],
});
check(
  "非法 path 点被逐点丢弃，非法 target 直接缺席",
  malformedPathPayload?.entities[0]?.path?.length === 2 &&
    malformedPathPayload.entities[0]?.pathTarget === undefined,
  JSON.stringify(malformedPathPayload?.entities[0]?.path) + " / " +
    JSON.stringify(malformedPathPayload?.entities[0]?.pathTarget),
);

const pathFixture: EntityView = {
  id: 1,
  kind: "unit.melee.t1",
  x: 2.5,
  y: 3.5,
  playerId: 1,
  path: [{ x: 2, y: 3 }, { x: 4, y: 3 }],
  pathTarget: { x: 6, y: 3 },
};
const drawnPath = pathPointsFor(pathFixture);
check(
  "路径折线从单位位置开始，去掉重复点并补上目标点",
  drawnPath.length === 3 &&
    drawnPath[0]?.x === 2.5 &&
    drawnPath[0]?.y === 3.5 &&
    drawnPath[1]?.x === 4.5 &&
    drawnPath[2]?.x === 6.5,
  JSON.stringify(drawnPath),
);
const cappedFixture: EntityView = {
  ...pathFixture,
  path: Array.from({ length: 100 }, (_, index) => ({ x: index, y: 0 })),
};
check(
  "客户端折线同样按 64 点上限截断",
  pathPointsFor(cappedFixture).length === MAX_SNAPSHOT_PATH_POINTS,
  String(pathPointsFor(cappedFixture).length),
);
check(
  "折线段数量是点数减一，屏幕外线段被剔除",
  pathSegments(drawnPath).length === 2 &&
    pathSegmentVisible(
      { x: 0, y: 0 },
      { x: 10, y: 10 },
      { x: 5, y: 5, width: 20, height: 20 },
    ) &&
    !pathSegmentVisible(
      { x: 100, y: 100 },
      { x: 110, y: 110 },
      { x: 5, y: 5, width: 20, height: 20 },
    ) &&
    !pathSegmentVisible(
      { x: Number.NaN, y: 0 },
      { x: 1, y: 1 },
      { x: 0, y: 0, width: 10, height: 10 },
    ),
  `${pathSegments(drawnPath).length} / ${PATH_COLOR}`,
);
check(
  "待定路径点转换为瓦片中心",
  JSON.stringify(pendingRoutePoints([{ x: 0, y: 0 }, { x: 2, y: 3 }])) ===
    JSON.stringify([{ x: 0.5, y: 0.5 }, { x: 2.5, y: 3.5 }]),
  JSON.stringify(pendingRoutePoints([{ x: 0, y: 0 }, { x: 2, y: 3 }])),
);

const servicePlayer = { id: 3, name: "service", gold: 100, wood: 0 };
const serviceEngineer: EntityView = {
  id: 9,
  kind: "unit.engineer",
  x: 1,
  y: 1,
  playerId: 3,
};
const damagedBuilding: EntityView = {
  id: 5,
  kind: "building.armory.t1",
  x: 5.5,
  y: 5.5,
  playerId: 3,
  progress: 1,
  healthRatio: 0.5,
  repairCost: { gold: 30, wood: 0 },
};
const unfinishedBuilding: EntityView = {
  id: 6,
  kind: "building.armory.t1",
  x: 8.5,
  y: 5.5,
  playerId: 3,
  progress: 0.4,
  reclaim: { gold: 50, wood: 10 },
};
const repairModel = buildingActionModel(damagedBuilding, 3, servicePlayer, serviceEngineer);
check(
  "受损的自有完工建筑提供 修理 (cost)",
  repairModel !== null &&
    repairModel.repair &&
    !repairModel.reclaim &&
    repairModel.repairEnabled &&
    repairModel.repairLabel === `${REPAIR_ACTION_LABEL} (金 30 · 木 0)` &&
    buildingCostLabel({ gold: 30, wood: 0 }) === "金 30 · 木 0",
  repairModel?.repairLabel ?? "null",
);
const poorRepair = buildingActionModel(
  damagedBuilding,
  3,
  { ...servicePlayer, gold: 10 },
  serviceEngineer,
);
check(
  "买不起时修理入口保留但禁用",
  poorRepair !== null && poorRepair.repair && !poorRepair.repairEnabled,
  String(poorRepair?.repairEnabled),
);
const engineerlessRepair = buildingActionModel(damagedBuilding, 3, servicePlayer, undefined);
check(
  "没有己方工兵时修理禁用但可见",
  engineerlessRepair !== null &&
    engineerlessRepair.repair &&
    !engineerlessRepair.repairEnabled,
  String(engineerlessRepair?.repairEnabled),
);
// The 强制搬运 (HAUL) entry is gone with the server's HAUL command: supplying a producer is now the
// ASSIST contextual command's job, sent per engineer at right-click time, so a producer with a
// production queue offers no building-service entry of its own any more.
const producerBuilding: EntityView = {
  ...damagedBuilding,
  id: 7,
  repairCost: undefined,
  demand: { ready: false, items: [{ item: "gold", need: 20, have: 0 }] },
};
check(
  "HAUL 已删除：完工生产建筑不再提供 强制搬运 条目（供给改由 ASSIST 右键命令承担）",
  buildingActionModel(producerBuilding, 3, servicePlayer, serviceEngineer) === null,
);
const unfinishedModel = buildingActionModel(
  unfinishedBuilding,
  3,
  servicePlayer,
  serviceEngineer,
);
check(
  "未完工的自有工地只提供 回收 (refund)：继续建造已删除（ASSIST 右键覆盖续建）",
  unfinishedModel !== null &&
    unfinishedModel.reclaim &&
    !unfinishedModel.repair &&
    unfinishedModel.reclaimEnabled &&
    unfinishedModel.reclaimLabel === `${RECLAIM_ACTION_LABEL} (金 50 · 木 10)` &&
    reclaimLabel({ gold: 50, wood: 10 }) === "回收 (金 50 · 木 10)" &&
    repairLabel({ gold: 30, wood: 0 }) === "修理 (金 30 · 木 0)",
  unfinishedModel?.reclaimLabel ?? "null",
);
check(
  "敌方建筑、单位与空选择不提供建筑服务入口",
  buildingActionModel({ ...damagedBuilding, playerId: 2 }, 3, servicePlayer, serviceEngineer) ===
    null &&
    buildingActionModel(
      { ...damagedBuilding, kind: "unit.engineer" },
      3,
      servicePlayer,
      serviceEngineer,
    ) === null &&
    buildingActionModel(undefined, 3, servicePlayer, serviceEngineer) === null &&
    buildingActionModel({ ...damagedBuilding, repairCost: undefined }, 3, servicePlayer,
      serviceEngineer) === null,
  "all null",
);

// ---------------------------------------------------------------------------------------------
// Producer status, engineer task, stock tags and haul pops
// ---------------------------------------------------------------------------------------------

const producingNow = { kind: "unit.melee.t1", position: 1, length: 3 };
check(
  "生产中标签用当前位置/队列长度（生产中 1/3）",
  queueMarkerText(3, producingNow) === "生产中 1/3",
  queueMarkerText(3, producingNow),
);
check(
  "没有 producingNow 的旧快照仍用生产中 ×N",
  queueMarkerText(3) === "生产中 ×3" && queueMarkerText(0) === "" && queueMarkerText(undefined) === "",
  `"${queueMarkerText(3)}" / "${queueMarkerText(0)}"`,
);
check(
  "非法 producingNow 回退到旧标记而不是打印 NaN",
  queueMarkerText(2, { kind: "x", position: 0, length: 2 }) === "生产中 ×2" &&
    queueMarkerText(2, { kind: "", position: 1, length: 2 }) === "生产中 ×2",
  queueMarkerText(2, { kind: "x", position: 0, length: 2 }),
);
check(
  "需求图标：ready 用空心勾，缺料用警告，没有 demand 不画",
  demandIcon({ ready: true, items: [] }) === "✓" &&
    demandIcon({ ready: false, items: [] }) === "⚠" &&
    demandIcon(undefined) === "",
  `${demandIcon({ ready: true, items: [] })}/${demandIcon({ ready: false, items: [] })}`,
);
const shortDemand = {
  ready: false,
  items: [
    { item: "gold", need: 80, have: 50 },
    { item: "wood", need: 20, have: 10 },
  ],
} as const;
check(
  "缺料提示列出缺的物品与 need/have",
  demandMissingText(shortDemand) === "缺：金 80/50 · 木 20/10",
  demandMissingText(shortDemand),
);
check(
  "满足需求或没有缺项时没有缺料提示",
  demandMissingText({ ready: true, items: shortDemand.items }) === "" &&
    demandMissingText({ ready: false, items: [{ item: "gold", need: 5, have: 5 }] }) === "",
  demandMissingText({ ready: true, items: shortDemand.items }),
);
check(
  "未知物品在缺料提示里回退到内容路径",
  demandMissingText({ ready: false, items: [{ item: "ore", need: 4, have: 1 }] }) === "缺：ore 4/1",
  demandMissingText({ ready: false, items: [{ item: "ore", need: 4, have: 1 }] }),
);

const loadedUnit = {
  id: 1,
  kind: "unit.engineer",
  x: 10,
  y: 10,
  contents: { gold: 80, wood: 40 },
} as const;
const singleCargoUnit = {
  id: 2,
  kind: "unit.engineer",
  x: 10,
  y: 10,
  contents: { gold: 120 },
} as const;
check(
  "库存标签列出全部物品（Q9：显示所有物品）",
  unitStockTagLabel(loadedUnit) === "金 80 · 木 40" &&
    unitStockTagLabel(singleCargoUnit) === "金 120",
  unitStockTagLabel(loadedUnit),
);
check(
  "空货舱、建筑与旧快照不画库存标签",
  unitStockTagLabel({ ...loadedUnit, contents: {} }) === null &&
    unitStockTagLabel({ id: 3, kind: "building.warehouse", x: 0, y: 0, contents: { gold: 5 } }) ===
      null &&
    unitStockTagLabel({ id: 4, kind: "unit.engineer", x: 0, y: 0 }) === null,
  String(unitStockTagLabel({ ...loadedUnit, contents: {} })),
);
check(
  "库存标签：选中总是显示；未选中只在鼠标半径内显示（边界含等号）",
  stockTagVisible(loadedUnit, true, null, null, 10) &&
    stockTagVisible(loadedUnit, true, 100, 100, 10) &&
    stockTagVisible(loadedUnit, false, 10, 20, 10) &&
    !stockTagVisible(loadedUnit, false, 10, 20.01, 10) &&
    stockTagVisible(loadedUnit, false, 10, 10, 0) &&
    !stockTagVisible(loadedUnit, false, null, null, 10) &&
    !stockTagVisible({ ...loadedUnit, contents: {} }, true, null, null, 10) &&
    !stockTagVisible(
      { id: 5, kind: "building.warehouse", x: 10, y: 10, contents: { gold: 1 } },
      true,
      null,
      null,
      10,
    ),
  String(stockTagVisible(loadedUnit, false, 10, 20.01, 10)),
);
check(
  "补给标签半径默认 10 格，可在设置里调，钳制到 1..32",
  DEFAULT_INVENTORY_RADIUS_TILES === 10 &&
    MIN_INVENTORY_RADIUS_TILES === 1 &&
    MAX_INVENTORY_RADIUS_TILES === 32 &&
    clampInventoryRadius(0) === 1 &&
    clampInventoryRadius(33) === 32 &&
    clampInventoryRadius(7.6) === 8 &&
    clampInventoryRadius(Number.NaN) === 10,
  String(clampInventoryRadius(33)),
);
check(
  "工程师任务行带 任务： 前缀且忽略空白",
  unitTaskLabel("搬运中 → 兵工厂") === "任务：搬运中 → 兵工厂" &&
    unitTaskLabel("建造中 → 兵营") === "任务：建造中 → 兵营" &&
    unitTaskLabel("修理中 → 仓库") === "任务：修理中 → 仓库" &&
    unitTaskLabel("空闲") === "任务：空闲" &&
    unitTaskLabel("") === null &&
    unitTaskLabel(undefined) === null,
  String(unitTaskLabel("搬运中 → 兵工厂")),
);

check(
  "queue 形式：ORDER MOVE/ATTACK/ATTACKMOVE/BUILD/REPAIR 追加 queue 令牌（Q16）",
  orderMove(7, 3, -2, true) === "ORDER MOVE 7 3 -2 queue" &&
    orderMoveRoute(7, [{ x: 1, y: 2 }, { x: 3, y: 4 }], true) === "ORDER MOVE 7 1 2 3 4 queue" &&
    orderAttack(7, 9, true) === "ORDER ATTACK 7 9 queue" &&
    orderAttackMove(7, 3, 4, true) === "ORDER ATTACKMOVE 7 3 4 queue" &&
    build(5, "building.armory.t1", 1, 2, true) === "BUILD 5 building.armory.t1 1 2 queue" &&
    repair(9, 12, true) === "REPAIR 9 12 queue" &&
    orderAttack(7, 9) === "ORDER ATTACK 7 9" &&
    repair(9, 12) === "REPAIR 9 12",
  `${orderMove(7, 3, -2, true)} / ${orderAttack(7, 9, true)}`,
);
check(
  "BATCH 把一串命令合成一条抽象订单，最多 16 条，分号/换行/空命令在本地拒绝",
  MAX_BATCH_COMMANDS === 16 &&
    batchCommands(["BUILD 5 building.armory.t1 1 2 queue", "TRAIN 3 unit.melee.t1"]) ===
      "BATCH BUILD 5 building.armory.t1 1 2 queue; TRAIN 3 unit.melee.t1" &&
    (() => {
      try {
        batchCommands([]);
        return false;
      } catch {
        return true;
      }
    })() &&
    (() => {
      try {
        batchCommands(["PING; PONG"]);
        return false;
      } catch {
        return true;
      }
    })() &&
    (() => {
      try {
        batchCommands(Array.from({ length: 17 }, () => "ORDER STOP 5"));
        return false;
      } catch {
        return true;
      }
    })(),
  batchCommands(["TRAIN 3 unit.melee.t1"]),
);
check(
  "sequenceLine 把单位命令改成 queue 形式，其余命令保持不变（Q16）",
  sequenceLine("ORDER MOVE 5 1 1") === "ORDER MOVE 5 1 1 queue" &&
    sequenceLine("ORDER ATTACK 5 9") === "ORDER ATTACK 5 9 queue" &&
    sequenceLine("ORDER ATTACKMOVE 5 1 1") === "ORDER ATTACKMOVE 5 1 1 queue" &&
    sequenceLine("BUILD 5 building.armory.t1 1 1") === "BUILD 5 building.armory.t1 1 1 queue" &&
    sequenceLine("BUILD 5 building.armory.t1 1 1 queue") === "BUILD 5 building.armory.t1 1 1 queue" &&
    sequenceLine("REPAIR 5 9") === "REPAIR 5 9 queue" &&
    sequenceLine("TRAIN 3 unit.melee.t1") === "TRAIN 3 unit.melee.t1" &&
    sequenceLine("UPGRADE 3") === "UPGRADE 3" &&
    sequenceLine("ORDER STOP 5") === "ORDER STOP 5" &&
    sequenceLine("  TRANSFER 1 2 gold 5 ") === "TRANSFER 1 2 gold 5",
  sequenceLine("ORDER MOVE 5 1 1"),
);

const tooltipEngineer = {
  id: 9,
  kind: "unit.engineer",
  x: 1,
  y: 2,
  task: "修理中 → 兵工厂",
  demand: shortDemand,
  queue: 2,
  queueItems: [
    { kind: "unit.melee.t1", ticks: 10, total: 20 },
    { kind: "unit.melee.t1", ticks: 20, total: 20 },
  ],
  producingNow,
} as const;
const engineerTooltip = tooltipContent("工程师", tooltipEngineer, "mine");
check(
  "提示框显示生产中 1/3、任务行和缺料行",
  engineerTooltip.lines.includes("生产中 1/3") &&
    engineerTooltip.lines.includes("任务：修理中 → 兵工厂") &&
    engineerTooltip.lines.includes("缺：金 80/50 · 木 20/10"),
  engineerTooltip.lines.join(" | "),
);

const parsedDetail = worldViewFromSnapshot({
  tick: 3,
  originX: 0,
  originY: 0,
  width: 1,
  height: 1,
  tiles: [["grass"]],
  entities: [
    {
      id: 1,
      kind: "building.barracks",
      x: 0.5,
      y: 0.5,
      queue: 2,
      producingNow: { kind: "unit.melee.t1", position: 1, length: 2 },
      demand: { ready: false, items: [{ item: "gold", need: 10, have: 2, stock: 500 }] },
      pop: { kind: "pickup", tick: 7 },
      task: "搬运中 → 兵工厂",
    },
    {
      id: 2,
      kind: "unit.engineer",
      x: 0.5,
      y: 0.5,
      task: "空闲",
      producingNow: { kind: "x", position: 0, length: 0 },
      demand: { ready: "yes", items: [] },
      pop: { kind: "pickup", tick: -1 },
    },
  ],
});
const parsedFirst = parsedDetail?.entities[0];
const parsedSecond = parsedDetail?.entities[1];
check(
  "快照解析 producingNow/demand/task/pop",
  parsedFirst?.producingNow?.position === 1 &&
    parsedFirst.producingNow.length === 2 &&
    parsedFirst.demand?.ready === false &&
    parsedFirst.demand.items[0]?.have === 2 &&
    !("stock" in (parsedFirst.demand.items[0] ?? {})) &&
    parsedFirst.task === "搬运中 → 兵工厂" &&
    parsedFirst.pop?.kind === "pickup" &&
    parsedFirst.pop.tick === 7,
  JSON.stringify(parsedFirst ?? null),
);
check(
  "快照里非法的 producingNow/demand/pop 被忽略而不是拒绝整份世界",
  parsedSecond !== undefined &&
    parsedSecond.producingNow === undefined &&
    parsedSecond.demand === undefined &&
    parsedSecond.pop === undefined &&
    parsedSecond.task === "空闲",
  JSON.stringify(parsedSecond ?? null),
);

const popRecords = new Map<number, PopRecord>();
registerSnapshotPops(popRecords, [{ id: 1, pop: { kind: "pickup", tick: 5 } }], 1_000);
check(
  "一次搬运事件开始一个弹跳",
  popRecords.get(1)?.startedAt === 1_000 && activePops(popRecords, 1_001).length === 1,
  JSON.stringify([...popRecords.values()]),
);
registerSnapshotPops(popRecords, [{ id: 1, pop: { kind: "pickup", tick: 5 } }], 1_400);
check(
  "同一事件 tick 的重复快照不重启动画",
  popRecords.get(1)?.startedAt === 1_000,
  String(popRecords.get(1)?.startedAt),
);
registerSnapshotPops(popRecords, [{ id: 1, pop: { kind: "dropoff", tick: 6 } }], 1_500);
check(
  "一秒内同一单位的第二个弹跳被节流",
  popRecords.get(1)?.startedAt === 1_000 && popRecords.get(1)?.tick === 6,
  `${popRecords.get(1)?.startedAt}/${popRecords.get(1)?.tick}`,
);
registerSnapshotPops(popRecords, [{ id: 1, pop: { kind: "dropoff", tick: 7 } }], 2_100);
check(
  "超过一秒后允许下一个弹跳",
  popRecords.get(1)?.startedAt === 2_100 && popRecords.get(1)?.tick === 7,
  String(popRecords.get(1)?.startedAt),
);
check(
  "弹跳生命周期是 600ms，进度与上升曲线确定",
  POP_LIFETIME_MS === 600 &&
    POP_THROTTLE_MS === 1_000 &&
    popProgress(2_100, 2_100) === 0 &&
    popProgress(2_100, 2_400) === 0.5 &&
    popProgress(2_100, 2_700) === 1 &&
    popRise(1) === POP_RISE_PX &&
    popAlpha(0) === 1 &&
    popAlpha(POP_LIFETIME_MS) === 0,
  `${popProgress(2_100, 2_400)}/${popRise(1)}/${popAlpha(600)}`,
);
check(
  "弹跳在 600ms 后从活动列表消失，重复快照不会复活它",
  activePops(popRecords, 2_699).length === 1 && activePops(popRecords, 2_700).length === 0,
  String(activePops(popRecords, 2_700).length),
);
registerSnapshotPops(popRecords, [{ id: 1, pop: { kind: "dropoff", tick: 7 } }], 2_750);
check(
  "过期记录仍记住已见 tick，迟到的同一快照不会重新弹出",
  activePops(popRecords, 2_751).length === 0,
  String(activePops(popRecords, 2_751).length),
);
prunePops(popRecords, 2_100 + POP_LIFETIME_MS + POP_THROTTLE_MS + 1);
check(
  "超过生命周期加节流窗口后记录被清理",
  popRecords.size === 0,
  String(popRecords.size),
);

// ---------------------------------------------------------------------------------------------
// Projectiles: tracking, angle math, draw states and the renderer-relevant clamping
// ---------------------------------------------------------------------------------------------

const shotSeen = new Map<number, TrackedProjectile>();
// 14 tiles at 14 tiles/s: exactly one second of flight, pointing east (0 rad).
const SHOT_START = { x: 2, y: 3, tx: 16, ty: 3 };
check(
  "飞行速度是 14 格/秒，14 格的飞行恰好一秒，零长度立即到达",
  PROJECTILE_SPEED_TPS === 14 &&
    PROJECTILE_TRAIL_TILES === 0.6 &&
    PROJECTILE_HEAD_TILES === 0.24 &&
    projectileFlightMs(2, 3, 16, 3) === 1_000 &&
    projectileFlightMs(4, 4, 4, 4) === 0,
  String(projectileFlightMs(2, 3, 16, 3)),
);

const firstFlight = trackProjectiles(shotSeen, [{ id: 1, ...SHOT_START }], 5_000);
check(
  "新 id 以当前时钟登记为起飞时刻，起始帧的位置就是起点",
  shotSeen.get(1)?.startMs === 5_000 &&
    firstFlight.length === 1 &&
    firstFlight[0]?.x === 2 &&
    firstFlight[0]?.y === 3 &&
    firstFlight[0]?.tx === 16 &&
    firstFlight[0]?.angle === 0 &&
    firstFlight[0]?.flightMs === 1_000,
  JSON.stringify(firstFlight),
);

const repeatedShot = trackProjectiles(shotSeen, [{ id: 1, x: 9, y: 9, tx: 9, ty: 9 }], 5_100);
check(
  "同一 id 的重复快照被忽略：飞行不重新计时也不改道",
  shotSeen.get(1)?.startMs === 5_000 &&
    shotSeen.get(1)?.tx === 16 &&
    shotSeen.get(1)?.ty === 3 &&
    repeatedShot.length === 1 &&
    repeatedShot[0]?.tx === 16,
  JSON.stringify([...shotSeen.values()]),
);

// Second id registers alongside the first; the answer comes back in id order, and the southbound
// 14-tile leg is one second at +90°.
const twoShots = trackProjectiles(shotSeen, [{ id: 2, x: 4, y: 10, tx: 4, ty: 24 }], 5_200);
check(
  "第二个 id 正常登记，活动列表按 id 排序，90° 飞行的方向角是 +π/2",
  twoShots.length === 2 &&
    twoShots[0]?.id === 1 &&
    twoShots[1]?.id === 2 &&
    near(twoShots[1]?.angle ?? 0, Math.PI / 2),
  JSON.stringify(twoShots),
);

// Mid-flight: the frame position is the straight lerp. Shot 1 is at half time (progress 0.5); shot 2
// started at 5200, so 300 ms into its flight is progress 0.3.
const halfway = trackProjectiles(shotSeen, undefined, 5_500);
check(
  "半程时位置是起终点的直线插值（各自按自己的起飞时刻计算进度）",
  halfway.length === 2 &&
    halfway[0]?.x === 9 &&
    halfway[0]?.y === 3 &&
    near(halfway[1]?.x ?? 0, 4) &&
    near(halfway[1]?.y ?? 0, 14.2),
  JSON.stringify(halfway),
);

check(
  "方向角：0°、90°、45° 三个基准方向",
  projectileAngle(0, 0, 5, 0) === 0 &&
    projectileAngle(0, 0, 0, 5) === Math.PI / 2 &&
    near(projectileAngle(0, 0, 5, 5), Math.PI / 4),
  `${projectileAngle(0, 0, 5, 0)}/${projectileAngle(0, 0, 0, 5)}/${projectileAngle(0, 0, 5, 5)}`,
);

// Near arrival the lerp stays on the segment and never overshoots the target tile (shot 2 is at
// progress 0.799 here, shot 1 at 0.999).
const nearArrival = trackProjectiles(shotSeen, undefined, 5_999);
check(
  "接近终点的帧仍在线性插值上，位置不会越过目标格",
  nearArrival.length === 2 &&
    near(nearArrival[0]?.x ?? 0, 15.986) &&
    near(nearArrival[0]?.y ?? 0, 3) &&
    near(nearArrival[1]?.x ?? 0, 4) &&
    near(nearArrival[1]?.y ?? 0, 21.186),
  JSON.stringify(nearArrival),
);

// Arrival: shot 1's final frame is clamped exactly onto its target tile, then the entry is pruned;
// shot 2 (started at 5200) is still 200 ms short of its own arrival and keeps flying.
const arrival = trackProjectiles(shotSeen, undefined, 6_000);
check(
  "到达时最后一帧被钳制在目标格上，随后条目从 seen 里清除；未到点的飞行继续",
  shotSeen.has(1) === false &&
    shotSeen.has(2) === true &&
    arrival.length === 2 &&
    arrival[0]?.x === 16 &&
    arrival[0]?.y === 3 &&
    near(arrival[1]?.x ?? 0, 4) &&
    near(arrival[1]?.y ?? 0, 21.2),
  JSON.stringify(arrival),
);
// Shot 2 reaches its target at 6200: clamped final frame, then pruned; the next frame draws nothing.
const secondArrival = trackProjectiles(shotSeen, undefined, 6_200);
check(
  "第二发到达：同样钳制在目标格并被清理，之后的帧不再绘制",
  shotSeen.has(2) === false &&
    secondArrival.length === 1 &&
    secondArrival[0]?.x === 4 &&
    secondArrival[0]?.y === 24 &&
    trackProjectiles(shotSeen, undefined, 6_201).length === 0 &&
    shotSeen.size === 0,
  JSON.stringify(secondArrival),
);

const drawnStates = projectileDrawState(halfway);
check(
  "projectileDrawState 只给出渲染需要的位置与方向角",
  drawnStates.length === 2 &&
    JSON.stringify(drawnStates[0]) === JSON.stringify({ x: 9, y: 3, angle: 0 }),
  JSON.stringify(drawnStates),
);

const trailEast = projectileTrail({ x: 9, y: 3, angle: 0 });
const headEast = projectileHeadPoints({ x: 9, y: 3, angle: 0 });
check(
  "拖尾从 -angle 方向后方 0.6 格连到当前位置，箭头尖朝 angle 前方",
  near(trailEast.fromX, 8.4) &&
    near(trailEast.fromY, 3) &&
    trailEast.toX === 9 &&
    trailEast.toY === 3 &&
    headEast.length === 3 &&
    near(headEast[0]?.[0] ?? 0, 9 + PROJECTILE_HEAD_TILES) &&
    near(headEast[0]?.[1] ?? 0, 3) &&
    (headEast[1]?.[0] ?? 0) < 9 &&
    (headEast[2]?.[0] ?? 0) < 9,
  JSON.stringify({ trailEast, headEast }),
);

// The renderer-relevant clamping: an arrival frame's draw state sits exactly on the target tile.
check(
  "渲染端关心的钳制：到达帧的绘制状态就是目标格与方向角",
  JSON.stringify(projectileDrawState(arrival)[0]) ===
    JSON.stringify({ x: 16, y: 3, angle: 0 }),
  JSON.stringify(projectileDrawState(arrival)),
);

// ---------------------------------------------------------------------------------------------
// UI font stack
// ---------------------------------------------------------------------------------------------

check(
  "界面等宽字体栈以 JetBrains Mono 开头，并以通用 monospace 兜底",
  FONT_STACK.startsWith('"JetBrains Mono"') && FONT_STACK.endsWith("monospace"),
  FONT_STACK,
);

// The base body stack lives in the page stylesheet, so read it from disk: JetBrains Mono has no
// Chinese glyphs and the body rule must keep a CJK fallback for the Chinese UI.
const INDEX_HTML_URL = new URL("../index.html", import.meta.url);
let indexHtml = "";
try {
  indexHtml = readFileSync(INDEX_HTML_URL, "utf8");
} catch (error: unknown) {
  console.error(
    `读取 index.html 失败：${error instanceof Error ? error.message : String(error)}`,
  );
}
const bodyStack = /font:\s*15px\/1\.55\s+([^;]+);/.exec(indexHtml)?.[1]?.trim() ?? "";
check(
  "基础正文字体栈以 JetBrains Mono 开头，并保留 Microsoft YaHei 作为中文字形回退",
  bodyStack.startsWith('"JetBrains Mono"') &&
    bodyStack.includes('"Microsoft YaHei"') &&
    bodyStack.endsWith("sans-serif"),
  bodyStack,
);

/** The slice of index.html between two anchors, or null when either anchor is missing. */
function sliceBetween(text: string, start: string, end: string): string | null {
  const from = text.indexOf(start);
  if (from < 0) {
    return null;
  }
  const to = text.indexOf(end, from);
  return to < 0 ? null : text.slice(from, to);
}

// ---------------------------------------------------------------------------------------------
// Settings, message filters and the palette
// ---------------------------------------------------------------------------------------------

check(
  "消息筛选有七个类别且标签非空；默认是安静预设（错误恒开，事件开，其余关）",
  MESSAGE_CATEGORIES.length === 7 &&
    MESSAGE_CATEGORIES.every(
      (category) => MESSAGE_CATEGORY_LABELS[category].trim() !== "",
    ) &&
    isToastAllowed(defaultSettings().filters, "error") &&
    isToastAllowed(defaultSettings().filters, "event") &&
    !isToastAllowed(defaultSettings().filters, "ping") &&
    !isToastAllowed(defaultSettings().filters, "command"),
  MESSAGE_CATEGORIES.join(","),
);

const pingOff = { ...ALL_MESSAGES, ping: false };
check(
  "单个类别的开关决定该类别是否允许提示，其他类别不受影响",
  !isToastAllowed(pingOff, "ping") &&
    isToastAllowed(pingOff, "error") &&
    isToastAllowed(pingOff, "command") &&
    isToastAllowed(ALL_MESSAGES, "ping"),
  JSON.stringify(pingOff),
);

const errorsPreset = presetFilters("errors");
const quietPreset = presetFilters("quiet");
const allPreset = presetFilters("all");
check(
  "“只显示错误”只留下错误，“安静”保留错误与事件，“全部”打开所有类别",
  MESSAGE_CATEGORIES.every((category) => errorsPreset[category] === (category === "error")) &&
    MESSAGE_CATEGORIES.every(
      (category) => quietPreset[category] === (category === "error" || category === "event"),
    ) &&
    MESSAGE_CATEGORIES.every((category) => allPreset[category] === true),
  `${MESSAGE_CATEGORIES.map((category) => `${category}:${String(errorsPreset[category])}/${String(quietPreset[category])}`).join(",")}`,
);

check(
  "任何预设都不隐藏错误；三个预设的元数据与实现一致",
  errorsPreset.error &&
    quietPreset.error &&
    allPreset.error &&
    FILTER_PRESETS.length === 3 &&
    FILTER_PRESETS.map((preset) => preset.id).join(",") === "errors,quiet,all" &&
    FILTER_PRESETS.every((preset) => preset.label.trim() !== "" && preset.filters.error),
  FILTER_PRESETS.map((preset) => preset.label).join(","),
);

check(
  "类别守卫接受七个类别并拒绝未知值",
  MESSAGE_CATEGORIES.every((category) => isMessageCategory(category)) &&
    isMessageCategory("error") &&
    !isMessageCategory("") &&
    !isMessageCategory("commands") &&
    !isMessageCategory("PING"),
  MESSAGE_CATEGORIES.join(","),
);

const commandCases: readonly (readonly [string, MessageCategory])[] = [
  ["PING", "ping"],
  ["BUILD 1 building.mine.t1 0 0", "build"],
  ["UPGRADE 1", "build"],
  ["TRAIN 1 unit.engineer", "production"],
  ["ORDER ATTACK 1 2", "combat"],
  ["ORDER ATTACKMOVE 1 2 3", "combat"],
  ["ORDER MOVE 1 2 3", "command"],
  ["WHOAMI", "command"],
];
check(
  "命令分类覆盖 PING / 建造 / 生产 / 攻击 / 普通命令，空命令落到事件",
  commandCases.every(([command, category]) => classifyCommand(command) === category) &&
    classifyCommand(null) === "event",
  commandCases
    .map(([command, category]) => `${command.slice(0, 12)}=${category}`)
    .join(","),
);

check(
  "通知分类：建筑完成与普通通知是事件，遭到攻击是战斗，ERR 是错误",
  classifyNotice("建筑完成：兵工厂") === "event" &&
    classifyNotice("普通通知") === "event" &&
    classifyNotice("遭到攻击：我的仓库") === "combat" &&
    classifyNotice("ERR 权限不足") === "error",
  classifyNotice("遭到攻击：我的仓库"),
);

check(
  "错误回答压过命令类别；正常回答按命令归类，无命令的通知按文本归类",
  classifyToast("BUILD 1 building.mine.t1 0 0", "ERR 拒绝") === "error" &&
    classifyToast("ORDER MOVE 1 2 3", "OK", "error") === "error" &&
    classifyToast("BUILD 1 building.mine.t1 0 0", "OK") === "build" &&
    classifyToast("PING", "PONG") === "ping" &&
    classifyToast(null, "遭到攻击：我的仓库") === "combat" &&
    classifyToast(null, "建筑完成：兵工厂") === "event",
  `${classifyToast("PING", "PONG")}/${classifyToast(null, "建筑完成：兵工厂")}`,
);

const savedSettings: Settings = {
  ...defaultSettings(),
  showPaths: false,
  viewHidden: true,
  inventoryRadiusTiles: 12,
  filters: presetFilters("quiet"),
  connection: { host: "127.0.0.1", port: "25567" },
};
const restoredSettings = parseSettings(serializeSettings(savedSettings));
check(
  "设置序列化后按版本原样恢复（筛选、界面开关、补给半径与服务器地址）",
  restoredSettings.showPaths === false &&
    restoredSettings.viewHidden === true &&
    restoredSettings.inventoryRadiusTiles === 12 &&
    restoredSettings.connection.host === "127.0.0.1" &&
    restoredSettings.connection.port === "25567" &&
    MESSAGE_CATEGORIES.every(
      (category) => restoredSettings.filters[category] === savedSettings.filters[category],
    ) &&
    serializeSettings(restoredSettings) === serializeSettings(savedSettings),
  serializeSettings(savedSettings),
);

check(
  "保存值带版本号，并且整份设置只使用一个固定的 localStorage 键",
  serializeSettings(savedSettings) ===
    `{"version":${SETTINGS_VERSION},"filters":${JSON.stringify(savedSettings.filters)},"showPaths":false,"viewHidden":true,"inventoryRadiusTiles":12,"language":"auto","connection":{"host":"127.0.0.1","port":"25567"},"accountName":"","accountPassword":""}` &&
    SETTINGS_STORAGE_KEY === "omni-com.web-client.settings" &&
    SETTINGS_VERSION === 1,
  SETTINGS_STORAGE_KEY,
);

const defaults = defaultSettings();
check(
  "缺失、非 JSON、非对象或旧版本的值回退到默认设置",
  parseSettings(null).showPaths &&
    parseSettings("").filters.error &&
    parseSettings("not json").filters.error &&
    parseSettings("[]").connection.host === "" &&
    parseSettings(JSON.stringify({ version: SETTINGS_VERSION - 1, filters: ALL_MESSAGES })).filters
      .error &&
    !parseSettings(JSON.stringify({ version: SETTINGS_VERSION - 1, filters: ALL_MESSAGES })).filters
      .command &&
    parseSettings(JSON.stringify({ version: 999 })).showPaths === defaults.showPaths,
  serializeSettings(parseSettings("not json")),
);

check(
  "非法字段类型整体回退；同版本缺少的可选字段由默认值补齐",
  parseSettings(
    JSON.stringify({
      version: SETTINGS_VERSION,
      filters: { ...ALL_MESSAGES },
      showPaths: false,
      connection: { host: 1, port: "25567" },
    }),
  ).showPaths === true &&
    parseSettings(
      JSON.stringify({
        version: SETTINGS_VERSION,
        filters: { ...ALL_MESSAGES, ping: "yes" },
      }),
    ).filters.ping === false &&
    parseSettings(
      JSON.stringify({
        version: SETTINGS_VERSION,
        filters: { command: false },
        showPaths: false,
      }),
    ).filters.command === false &&
    parseSettings(
      JSON.stringify({
        version: SETTINGS_VERSION,
        filters: { command: false },
        showPaths: false,
      }),
    ).showPaths === false &&
    parseSettings(
      JSON.stringify({
        version: SETTINGS_VERSION,
        filters: { command: false },
        showPaths: false,
      }),
    ).connection.host === "",
  "invalid field types",
);

check(
  "补给半径：缺失补默认 10，超范围钳制，类型错误整份回退",
  parseSettings(
    JSON.stringify({ version: SETTINGS_VERSION, filters: ALL_MESSAGES }),
  ).inventoryRadiusTiles === DEFAULT_INVENTORY_RADIUS_TILES &&
    parseSettings(
      JSON.stringify({ version: SETTINGS_VERSION, filters: ALL_MESSAGES, inventoryRadiusTiles: 99 }),
    ).inventoryRadiusTiles === MAX_INVENTORY_RADIUS_TILES &&
    parseSettings(
      JSON.stringify({ version: SETTINGS_VERSION, filters: ALL_MESSAGES, inventoryRadiusTiles: 0 }),
    ).inventoryRadiusTiles === MIN_INVENTORY_RADIUS_TILES &&
    parseSettings(
      JSON.stringify({ version: SETTINGS_VERSION, filters: ALL_MESSAGES, inventoryRadiusTiles: "wide" }),
    ).inventoryRadiusTiles === DEFAULT_INVENTORY_RADIUS_TILES,
  "inventory radius rules",
);

check(
  "记住账号：序列化往返恢复账号与密码，缺失补空串，类型错误整份回退",
  (() => {
    const withAccount: Settings = {
      ...savedSettings,
      accountName: "alice",
      accountPassword: "secret",
    };
    const restoredAccount = parseSettings(serializeSettings(withAccount));
    const withoutAccount = parseSettings(
      JSON.stringify({ version: SETTINGS_VERSION, filters: ALL_MESSAGES }),
    );
    return (
      restoredAccount.accountName === "alice" &&
      restoredAccount.accountPassword === "secret" &&
      serializeSettings(restoredAccount) === serializeSettings(withAccount) &&
      withoutAccount.accountName === "" &&
      withoutAccount.accountPassword === "" &&
      // A non-string account field is the same corruption rule as the other optional strings: the
      // whole stored object falls back to the defaults, which carry empty account fields.
      parseSettings(
        JSON.stringify({ version: SETTINGS_VERSION, filters: ALL_MESSAGES, accountName: 42 }),
      ).accountName === "" &&
      parseSettings(
        JSON.stringify({ version: SETTINGS_VERSION, filters: ALL_MESSAGES, accountPassword: 7 }),
      ).accountPassword === "" &&
      parseSettings(
        JSON.stringify({ version: SETTINGS_VERSION, filters: ALL_MESSAGES, accountName: 42 }),
      ).connection.host === ""
    );
  })(),
  "account persistence rules",
);

// ---------------------------------------------------------------------------------------------
// Music playlist
// ---------------------------------------------------------------------------------------------

const addedTracks = addTracks([], [
  { key: "a", name: "A.mp3", url: "blob:a" },
  { key: "b", name: "", url: "blob:b" },
]);
check(
  "加入本地文件时按顺序分配 id，空文件名使用占位名",
  addedTracks.tracks.length === 2 &&
    addedTracks.added[0]?.id === 1 &&
    addedTracks.added[1]?.id === 2 &&
    addedTracks.added[0]?.name === "A.mp3" &&
    addedTracks.added[1]?.name === "未命名音频" &&
    addedTracks.skipped === 0,
  JSON.stringify(addedTracks.added),
);

const deduped = addTracks(addedTracks.tracks, [
  { key: "a", name: "again.mp3", url: "blob:c" },
  { key: "b", name: "B.mp3", url: "blob:d" },
  { key: "", name: "no-url.mp3", url: "" },
]);
check(
  "重复的文件 key 与空对象 URL 被跳过，已有列表不被修改",
  deduped.added.length === 0 &&
    deduped.skipped === 3 &&
    deduped.tracks.length === 2 &&
    addedTracks.tracks.length === 2,
  `${String(deduped.skipped)}/${String(deduped.tracks.length)}`,
);

const removedTrack = removeTrack(addedTracks.tracks, 1);
const missingTrack = removeTrack(addedTracks.tracks, 999);
check(
  "移除中间一首后保持其余顺序；未知 id 返回 -1 且不改动列表",
  removedTrack.index === 0 &&
    removedTrack.removed?.name === "A.mp3" &&
    removedTrack.tracks.length === 1 &&
    removedTrack.tracks[0]?.name === "未命名音频" &&
    missingTrack.index === -1 &&
    missingTrack.removed === null &&
    missingTrack.tracks.length === 2,
  JSON.stringify(removedTrack.removed),
);

check(
  "曲目索引被夹到有效范围",
  clampIndex(-4, 3) === 0 &&
    clampIndex(9, 3) === 2 &&
    clampIndex(1.9, 3) === 1 &&
    clampIndex(Number.NaN, 3) === 0 &&
    clampIndex(0, 0) === -1,
  `${clampIndex(-4, 3)}/${clampIndex(9, 3)}/${clampIndex(0, 0)}`,
);

check(
  "不循环时末尾没有下一首、开头没有上一首；普通步进正确",
  nextIndex(2, 3, { loop: "off", shuffle: false }) === -1 &&
    prevIndex(0, 3, { loop: "off", shuffle: false }) === -1 &&
    nextIndex(0, 3, { loop: "off", shuffle: false }) === 1 &&
    prevIndex(2, 3, { loop: "off", shuffle: false }) === 1,
  `${nextIndex(2, 3, { loop: "off", shuffle: false })}/${prevIndex(0, 3, { loop: "off", shuffle: false })}`,
);

check(
  "列表循环在两端回绕；单曲循环只在自动结束时重复当前曲目",
  nextIndex(2, 3, { loop: "all", shuffle: false }) === 0 &&
    prevIndex(0, 3, { loop: "all", shuffle: false }) === 2 &&
    nextIndex(1, 3, { loop: "one", shuffle: false, auto: true }) === 1 &&
    nextIndex(1, 3, { loop: "one", shuffle: false }) === 2,
  `${nextIndex(2, 3, { loop: "all", shuffle: false })}/${prevIndex(0, 3, { loop: "all", shuffle: false })}`,
);

check(
  "空列表与单曲列表的边界",
  nextIndex(0, 0, { loop: "all", shuffle: false }) === -1 &&
    prevIndex(0, 0, { loop: "all", shuffle: false }) === -1 &&
    nextIndex(0, 1, { loop: "off", shuffle: false }) === -1 &&
    prevIndex(0, 1, { loop: "all", shuffle: false }) === 0,
  `${nextIndex(0, 1, { loop: "off", shuffle: false })}/${prevIndex(0, 1, { loop: "all", shuffle: false })}`,
);

check(
  "随机播放不返回当前曲目，结果由注入的随机源决定",
  nextIndex(1, 4, { loop: "off", shuffle: true, rand: () => 0 }) === 0 &&
    nextIndex(1, 4, { loop: "off", shuffle: true, rand: () => 0.9 }) === 3 &&
    prevIndex(2, 4, { loop: "all", shuffle: true, rand: () => 0.5 }) === 1 &&
    nextIndex(1, 1, { loop: "off", shuffle: true, rand: () => 0.5 }) === 0,
  `${nextIndex(1, 4, { loop: "off", shuffle: true, rand: () => 0.9 })}/${prevIndex(2, 4, { loop: "all", shuffle: true, rand: () => 0.5 })}`,
);

check(
  "循环模式的菜单常量是 不循环 / 列表循环 / 单曲循环",
  LOOP_MODES.join(",") === "off,all,one",
  LOOP_MODES.join(","),
);

check(
  "媒体时钟格式化为 m:ss，超过一小时为 h:mm:ss，坏值显示 0:00",
  formatTime(0) === "0:00" &&
    formatTime(5) === "0:05" &&
    formatTime(65) === "1:05" &&
    formatTime(3_600) === "1:00:00" &&
    formatTime(3_661) === "1:01:01" &&
    formatTime(Number.NaN) === "0:00" &&
    formatTime(-1) === "0:00" &&
    formatTime(Number.POSITIVE_INFINITY) === "0:00",
  `${formatTime(65)}/${formatTime(3_661)}`,
);

check(
  "音量夹到 0..1，进度滑块夹到 0..1000",
  clampVolume(-1) === 0 &&
    clampVolume(0.25) === 0.25 &&
    clampVolume(2) === 1 &&
    clampVolume(Number.NaN) === 1 &&
    progressSliderValue(0.5) === 500 &&
    progressSliderValue(-1) === 0 &&
    progressSliderValue(2) === 1_000 &&
    progressSliderValue(Number.NaN) === 0,
  `${clampVolume(2)}/${progressSliderValue(0.5)}`,
);

const trackFixture: Track = { id: 1, key: "a", name: "A.mp3", url: "blob:a" };
check(
  "曲目对象保留 id、key、名称与对象 URL",
  trackFixture.id === 1 &&
    trackFixture.key === "a" &&
    trackFixture.name === "A.mp3" &&
    trackFixture.url === "blob:a",
  JSON.stringify(trackFixture),
);

// ---------------------------------------------------------------------------------------------
// Palette / weight tokens and the stylesheet's square rules
// ---------------------------------------------------------------------------------------------

check(
  "调色板与 owner 的 global.css 语言完全一致",
  UI_PALETTE.page === "#101010" &&
    UI_PALETTE.surface === "#202020" &&
    UI_PALETTE.border === "#303030" &&
    UI_PALETTE.text === "#e0e0e0" &&
    UI_PALETTE.muted === "#b0b0b0" &&
    UI_PALETTE.errorBackground === "#402020" &&
    UI_PALETTE.errorText === "#ffe0e0" &&
    UI_PALETTE.errorBorder === "#503030",
  JSON.stringify(UI_PALETTE),
);

check(
  "单一 Medium 字体只用 400/500；600/700 不能声称真实层级，真正加粗需要 Bold 字重",
  UI_WEIGHTS.body === 400 &&
    UI_WEIGHTS.emphasis === 500 &&
    !Object.values(UI_WEIGHTS).some((weight) => weight >= 600),
  JSON.stringify(UI_WEIGHTS),
);

check(
  "index.html 直接使用同一套调色板字面量",
  Object.values(UI_PALETTE).every((value) => indexHtml.includes(value)) &&
    /--bg:\s*#101010/.test(indexHtml) &&
    /--surface|--panel:\s*#202020/.test(indexHtml) &&
    /--line:\s*#303030/.test(indexHtml) &&
    /--text:\s*#e0e0e0/.test(indexHtml),
  Object.values(UI_PALETTE).join(","),
);

check(
  "chrome 没有圆角、阴影、模糊或发光",
  UI_FORBIDDEN_STYLE_PROPERTIES.every((property) => !indexHtml.includes(property)) &&
    !/filter:\s*blur\(/.test(indexHtml) &&
    !/filter:\s*drop-shadow\(/.test(indexHtml),
  UI_FORBIDDEN_STYLE_PROPERTIES.join(","),
);

check(
  "样式表不声明 600/700 字重（600 与 500 使用同一 Medium 字形）",
  !/font-weight:\s*(600|700)\b/.test(indexHtml) &&
    !/font:\s*(600|700)\s/.test(indexHtml),
  "font-weight 600/700",
);

check(
  "按钮悬停用 brightness(75%)；输入框为 32px 的 #101010 方框",
  /button:hover:not\(:disabled\)\s*\{\s*filter:\s*brightness\(75%\)/.test(indexHtml) &&
    /input\s*\{\s*height:\s*32px/.test(indexHtml) &&
    /input,\s*\n\s*textarea\s*\{[^}]*background:\s*#101010/s.test(indexHtml),
  "button hover / input height",
);

check(
  "toast 从右侧滑入约 1 秒，但仍锚定右下角（命令栏占据底边）",
  TOAST_SLIDE_MS === 1_000 &&
    /@keyframes\s+toast-slide-in/.test(indexHtml) &&
    /animation:\s*toast-slide-in 1s/.test(indexHtml) &&
    /\.toast-stack\s*\{[^}]*right:\s*14px;[^}]*bottom:\s*calc\(var\(--bottom-bar-height\) \+ 26px\)/s.test(
      indexHtml,
    ),
  `${TOAST_SLIDE_MS}ms`,
);

check(
  "错误是唯一使用色相的界面状态（背景 #402020 / 文字 #ffe0e0 / 边框 #503030）",
  /\.toast-error\s*\{[^}]*border-color:\s*#503030[^}]*background:\s*#402020[^}]*color:\s*#ffe0e0/s.test(
    indexHtml,
  ) &&
    /#status\[data-state="error"\]\s*\{[^}]*border-color:\s*#503030[^}]*background:\s*#402020[^}]*color:\s*#ffe0e0/s.test(
      indexHtml,
    ),
  "error treatment",
);

check(
  "没有左侧 10% 导航栏：全画布 RTS 保持小地图左下、命令栏底部",
  !indexHtml.includes("navigator-ul") &&
    !indexHtml.includes("navigator-li") &&
    /class="minimap"/.test(indexHtml) &&
    /class="bottom-bar"/.test(indexHtml),
  "full-canvas RTS layout",
);

// ---------------------------------------------------------------------------------------------
// Follow-lock highlight
// ---------------------------------------------------------------------------------------------

const followPulseZero = followPulse(0);
const followPulseQuarter = followPulse(FOLLOW_PULSE_MS / 4);
const followPulseHalf = followPulse(FOLLOW_PULSE_MS / 2);
const followPulseWrap = followPulse(FOLLOW_PULSE_MS);
check(
  "跟随光环的呼吸相位：起点 0、四分之一周期 0.5、半周期 1、整周期回绕到 0",
  followPulseZero === 0 &&
    near(followPulseQuarter, 0.5) &&
    near(followPulseHalf, 1) &&
    near(followPulseWrap, followPulseZero) &&
    near(followPulse(FOLLOW_PULSE_MS * 3 + 5, FOLLOW_PULSE_MS), followPulse(5)),
  `${followPulseZero}/${followPulseQuarter}/${followPulseHalf}/${followPulseWrap}`,
);

check(
  "跟随光环呼吸相位始终落在 0..1；坏时钟与非正周期坍缩为静止相位",
  followPulse(FOLLOW_PULSE_MS * 0.25) >= 0 &&
    followPulse(FOLLOW_PULSE_MS * 0.75) <= 1 &&
    followPulse(Number.NaN) === 0 &&
    followPulse(Number.POSITIVE_INFINITY) === 0 &&
    followPulse(1_000, 0) === 0 &&
    followPulse(1_000, -5) === 0,
  "follow pulse guards",
);

const followPulseSample = followPulse(FOLLOW_PULSE_MS / 3);
check(
  "跟随光环的半径与不透明度随相位单调变化，并停在文档范围内（非有限相位回落到静止值）",
  followRingScale(0) === FOLLOW_RING_MIN_SCALE &&
    near(followRingScale(1), FOLLOW_RING_MAX_SCALE) &&
    followRingScale(followPulseSample) > FOLLOW_RING_MIN_SCALE &&
    followRingScale(followPulseSample) < FOLLOW_RING_MAX_SCALE &&
    followRingAlpha(0) === FOLLOW_RING_MIN_ALPHA &&
    near(followRingAlpha(1), FOLLOW_RING_MAX_ALPHA) &&
    followRingAlpha(followPulseSample) > FOLLOW_RING_MIN_ALPHA &&
    followRingAlpha(followPulseSample) < FOLLOW_RING_MAX_ALPHA &&
    followRingAlpha(1) <= 1 &&
    followRingAlpha(0) >= 0 &&
    followRingScale(Number.NaN) === FOLLOW_RING_MIN_SCALE &&
    followRingAlpha(Number.NaN) === FOLLOW_RING_MIN_ALPHA,
  `${followRingScale(followPulseSample)}/${followRingAlpha(followPulseSample)}`,
);

check(
  "跟随光环是画布内容：白色描边，不借用选中环的金色或任何归属色",
  FOLLOW_RING_COLOR.startsWith("rgba(255, 255, 255") &&
    !SELECTION_COLOR.startsWith("rgba(255, 255, 255") &&
    Number.isFinite(FOLLOW_RING_MIN_SCALE) &&
    FOLLOW_RING_MIN_SCALE >= 1 &&
    FOLLOW_RING_MAX_SCALE > FOLLOW_RING_MIN_SCALE &&
    FOLLOW_RING_MAX_ALPHA <= 1 &&
    FOLLOW_RING_MAX_ALPHA > FOLLOW_RING_MIN_ALPHA,
  FOLLOW_RING_COLOR,
);

// ---------------------------------------------------------------------------------------------
// Chrome layout: BUILD rail, left-docked console, help view and the debug key
// ---------------------------------------------------------------------------------------------

const commandContextBlock = sliceBetween(indexHtml, 'id="command-context"', "</aside>");
const buildRailBlock = sliceBetween(indexHtml, 'id="build-panel"', "</aside>");
const buildRailRule = /(?:^|\n)\s*\.build-rail\s*\{[^}]*\}/.exec(indexHtml)?.[0] ?? "";
check(
  "BUILD 是右侧全高面板并贴满整条右边缘（item 2）：#build-section 在 #build-panel，三个 id 不变，rail 锚定 top/right/bottom 全为 0",
  buildRailBlock !== null &&
    buildRailBlock.includes('id="build-section"') &&
    buildRailBlock.includes('id="build-buttons"') &&
    buildRailBlock.includes('id="build-target"') &&
    commandContextBlock !== null &&
    !commandContextBlock.includes("build-section") &&
    /--build-rail-width:\s*\d+px/.test(indexHtml) &&
    buildRailRule.includes("position: absolute") &&
    buildRailRule.includes("right: 0") &&
    buildRailRule.includes("bottom: 0") &&
    buildRailRule.includes("top: 0"),
  "build rail",
);

check(
  "BUILD 按钮不带客户端造价：造价是服务器内容，BUILD 目录只显示路径并由清单命名",
  readFileSync(new URL("../src/main.ts", import.meta.url), "utf8")
    .includes("button.textContent = buildingPath;") &&
    !readFileSync(new URL("../src/commands.ts", import.meta.url), "utf8").includes(
      "BUILDING_COSTS",
    ),
  "build buttons are content-driven",
);

check(
  "打开 BUILD 面板时顶部列、底部栏、提示堆栈与连接面板整体左移一个轨宽；隐藏画面时轨道一并隐藏",
  /body\.build-rail-open \.top-right\s*\{[^}]*right:\s*calc\(var\(--build-rail-width\)/s.test(indexHtml) &&
    /body\.build-rail-open \.bottom-bar\s*\{[^}]*right:\s*calc\(var\(--build-rail-width\)/s.test(indexHtml) &&
    /body\.build-rail-open \.toast-stack\s*\{[^}]*right:\s*calc\(var\(--build-rail-width\)/s.test(indexHtml) &&
    /body\.build-rail-open \.connection-overlay\s*\{[^}]*right:\s*calc\(var\(--build-rail-width\)/s.test(indexHtml) &&
    /\.build-rail-buttons button\s*\{[^}]*font-size:\s*13px/s.test(indexHtml) &&
    indexHtml.includes("body.view-hidden .build-rail"),
  "build rail shifts",
);

const consoleOverlayRule = /(?:^|\n)\s*\.console-overlay\s*\{[^}]*\}/.exec(indexHtml)?.[0] ?? "";
const consoleBlock = sliceBetween(indexHtml, 'id="console-overlay"', 'id="help-overlay"');
check(
  "控制台停靠在左侧且更紧凑：overlay 锚定 left:14px（不再锚定 right），面板收紧到 10px 内边距",
  consoleOverlayRule.includes("position: fixed") &&
    consoleOverlayRule.includes("left: 14px") &&
    consoleOverlayRule.includes("bottom: 14px") &&
    !consoleOverlayRule.includes("right: 14px") &&
    /\.console-panel\s*\{[^}]*padding:\s*10px/s.test(indexHtml),
  "console dock",
);

const connectionBlock = sliceBetween(indexHtml, 'id="connection-overlay"', 'id="console-overlay"');
check(
  "debug.key 输入在控制台面板里（engine-info 的密钥与命令同处一地），连接面板不再携带它",
  consoleBlock !== null &&
    consoleBlock.includes('id="debug-key"') &&
    connectionBlock !== null &&
    !connectionBlock.includes("debug-key"),
  "debug key",
);

const helpBlock = sliceBetween(indexHtml, 'id="help-overlay"', '<div id="entity-tooltip"');
check(
  "帮助视图：连接说明与操作提示从连接面板移入 #help-overlay，入口是 帮助/操作说明… 两个按钮，出口有关闭按钮",
  helpBlock !== null &&
    helpBlock.includes("操作说明") &&
    helpBlock.includes("游戏操作提示") &&
    indexHtml.includes('id="open-help"') &&
    indexHtml.includes('id="help-close"') &&
    indexHtml.includes('id="connection-help-link"') &&
    /\.help-overlay\s*\{[^}]*position:\s*fixed/s.test(indexHtml) &&
    connectionBlock !== null &&
    !connectionBlock.includes("游戏操作提示"),
  "help view",
);

// ---------------------------------------------------------------------------------------------
// Wire adaptation: served buildable catalogue, builder capability, options-driven upgrades,
// contents / storage display and the batchable UPGRADEUNIT
// ---------------------------------------------------------------------------------------------

// The BUILD menu reads the snapshot's top-level `buildable` array, and there is NO fallback: a
// snapshot without the field (an older server, the demo world) paints no BUILD buttons (item 1).
const servedCatalogue = ["building.hell_gate", "building.mine.t1", "building.warehouse"];
const servedBuilderCommand = commandBarModel(
  [{ id: 61, kind: "unit.engineer", x: 1.5, y: 1.5, playerId: 1 }],
  1,
  { buildingPaths: servedCatalogue },
);
const servedBuildActions = servedBuilderCommand.actions.filter((action) => action.kind === "build");
check(
  "BUILD 目录只来自快照的 buildable 数组（地狱门在列）；缺字段时没有任何 BUILD 条目（item 1）",
  servedBuildActions.map((action) => action.path).join(",") === servedCatalogue.join(",") &&
    commandBarModel([{ id: 62, kind: "unit.engineer", x: 1.5, y: 1.5, playerId: 1 }], 1, {})
      .actions.filter((action) => action.kind === "build").length === 0 &&
    commandBarModel([{ id: 63, kind: "unit.engineer", x: 1.5, y: 1.5, playerId: 1 }], 1).actions.filter(
      (action) => action.kind === "build",
    ).length === 0,
  servedBuildActions.map((action) => action.path).join(","),
);

// The server declares builders of any tier with `builder: true`; the name rule is only the fallback.
const upgradedEngineer: EntityView = {
  id: 71,
  kind: "unit.engineer.t2",
  x: 2.5,
  y: 2.5,
  playerId: 1,
  builder: true,
};
const namedEngineer: EntityView = { id: 72, kind: "unit.engineer", x: 3.5, y: 3.5, playerId: 1 };
check(
  "builder 能力字段让升级后的工兵层获得 BUILD 菜单；建筑与普通单位不算工兵",
  isBuilderUnit(upgradedEngineer) &&
    isBuilderUnit(namedEngineer) &&
    !isBuilderUnit({ id: 73, kind: "unit.engineer.t2", x: 4.5, y: 4.5, playerId: 1 }) &&
    !isBuilderUnit({ id: 74, kind: "building.warehouse", x: 5.5, y: 5.5, playerId: 1, builder: true }) &&
    !isBuilderUnit({ id: 75, kind: "unit.melee.t1", x: 6.5, y: 6.5, playerId: 1, builder: false }),
  "builder capability",
);
check(
  "升级工兵层单独选中时进入 engineer 分类并拿到 BUILD 条目（服务目录）",
  commandBarModel([upgradedEngineer], 1, { buildingPaths: servedCatalogue }).kind === "engineer" &&
    commandBarModel([upgradedEngineer], 1, { buildingPaths: servedCatalogue })
      .actions.filter((action) => action.kind === "build")
      .length === servedCatalogue.length,
  commandBarModel([upgradedEngineer], 1, { buildingPaths: servedCatalogue }).kind,
);
check(
  "快照解析保留布尔 builder 字段（只接受 true），非布尔值被丢弃",
  (() => {
    const parsed = worldViewFromSnapshot({
      tick: 1,
      width: 1,
      height: 1,
      tiles: [["grass"]],
      entities: [
        { id: 1, kind: "unit.engineer.t2", x: 0.5, y: 0.5, player: 1, builder: true },
        { id: 2, kind: "unit.engineer", x: 0.5, y: 0.5, player: 1, builder: "yes" },
        { id: 3, kind: "unit.melee.t1", x: 0.5, y: 0.5, player: 1 },
      ],
    });
    return (
      parsed !== null &&
      parsed.entities[0]?.builder === true &&
      parsed.entities[1]?.builder === undefined &&
      parsed.entities[2]?.builder === undefined
    );
  })(),
  "snapshot builder flag",
);

// The upgrade offer is an options array: one entry per option, one batch plan per variant.
const tripleOptionBuilding: EntityView = {
  id: 81,
  kind: "building.armory.t1",
  x: 8.5,
  y: 8.5,
  playerId: 1,
  progress: 1,
  upgrade: {
    options: [
      { to: "building.armory.t2", gold: 140, wood: 40 },
      { to: "building.purgatory", gold: 260, wood: 40 },
      { to: "building.hell_gate", gold: 400, wood: 100 },
    ],
  },
};
check(
  "options 数组驱动的升级条目：每条路线一个条目并各带自己的费用",
  upgradeEntries(tripleOptionBuilding, 1, { id: 1, name: "P", gold: 999, wood: 999 }).length === 3 &&
    upgradeEntries(tripleOptionBuilding, 1, { id: 1, name: "P", gold: 999, wood: 999 })[2]?.target ===
      "building.hell_gate" &&
    upgradeEntries(tripleOptionBuilding, 1, { id: 1, name: "P", gold: 999, wood: 999 })[2]?.gold === 400,
  "three options, three entries",
);
check(
  "批量升级按 options.length 生成每条路线的计划：三条路线各得一个 plan；炮塔 t1 只有一条路线（一个批量按钮）",
  (() => {
    const player = { id: 1, name: "P", gold: 100000, wood: 100000 };
    const third = upgradeBatchPlan([tripleOptionBuilding], 1, player, 2);
    const turret = { id: 91, kind: "building.turret.t1", x: 1, y: 1, playerId: 1, progress: 1,
      upgrade: { options: [{ to: "building.turret.t2", gold: 260, wood: 70 }] } };
    const turretGroup = [turret, { ...turret, id: 92, x: 2 }];
    // The page derives the plan count from the first selected building's options length, so a turret
    // t1 group paints exactly one 批量 button (variant 0) and no bogus second one.
    const turretPlans = Array.from({ length: turret.upgrade?.options.length ?? 0 }, (_, variant) =>
      upgradeBatchPlan(turretGroup, 1, player, variant),
    );
    const firstPlan = turretPlans[0] ?? null;
    return (
      third !== null &&
      third.variantIndex === 2 &&
      third.lines.join(",") === "UPGRADE 81 building.hell_gate" &&
      turretPlans.length === 1 &&
      firstPlan !== null &&
      firstPlan.variantIndex === 0 &&
      firstPlan.lines.length === 2 &&
      firstPlan.lines.every((line) => line.startsWith("UPGRADE ")) &&
      firstPlan.lines.every((line) => line.endsWith("building.turret.t2"))
    );
  })(),
  "batch plans per option",
);
check(
  "UPGRADEUNIT 接受 tierPath（UPGRADEUNIT <id> <path>）并可与 BUILD 一样进入 BATCH",
  upgradeUnit(7, "unit.engineer.t2") === "UPGRADEUNIT 7 unit.engineer.t2" &&
    upgradeUnit(7) === "UPGRADEUNIT 7" &&
    sequenceLine("UPGRADEUNIT 7 unit.engineer.t2") === "UPGRADEUNIT 7 unit.engineer.t2" &&
    batchCommands(["TRAIN 3 unit.melee.t1", "UPGRADEUNIT 7 unit.engineer.t2"]) ===
      "BATCH TRAIN 3 unit.melee.t1; UPGRADEUNIT 7 unit.engineer.t2",
  "UPGRADEUNIT batchable",
);
check(
  "unitUpgradeEntry 读单位行的 options 数组并把目标 tier 交给命令",
  (() => {
    const unit: EntityView = {
      id: 97,
      kind: "unit.engineer",
      x: 9.5,
      y: 9.5,
      playerId: 1,
      upgrade: { options: [{ to: "unit.engineer.t2", gold: 15, wood: 0 }] },
    };
    const offer = unitUpgradeEntry(unit, 1, { id: 1, name: "P", gold: 30, wood: 5 });
    return offer !== null && offer.unitId === 97 && offer.target === "unit.engineer.t2" && offer.affordable;
  })(),
  "unit upgrade entry",
);

// Every entity with a non-empty inventory shows what it holds; a warehouse also shows slot accounting.
check(
  "contents 渲染：建筑缓冲库存与单位货物共用一条稳定规则，仓库槽位来自服务端 storage",
  (() => {
    const bufferBuilding: EntityView = {
      id: 101,
      kind: "building.mine.t1",
      x: 1, y: 1, playerId: 1,
      contents: { gold: 30 },
    };
    const warehouse: EntityView = {
      id: 102,
      kind: "building.warehouse",
      x: 2, y: 2, playerId: 1,
      contents: { gold: 120, wood: 40 },
      storage: { slots: 24, used: 2, free: 22 },
    };
    const enemyUnit: EntityView = {
      id: 103,
      kind: "unit.melee.t1",
      x: 3, y: 3, playerId: 2,
      contents: { wood: 10 },
    };
    return (
      warehouseContentsLabel(bufferBuilding) === `库存：${t("item.gold")} 30` &&
      warehouseContentsLabel(warehouse) === `库存：${t("item.gold")} 120 · ${t("item.wood")} 40` &&
      unitCargoLabel(enemyUnit) === `携带：${t("item.wood")} 10` &&
      warehouseContentsLabel({ id: 104, kind: "building.warehouse", x: 4, y: 4 }) === null &&
      warehouseStorageLabel(warehouse) === `容量 24 · 已用 2 · 空闲 22` &&
      warehouseStorageLabel({ id: 105, kind: "building.warehouse", x: 5, y: 5 }) === "容量未知（旧快照）"
    );
  })(),
  "contents and storage labels",
);

// ---------------------------------------------------------------------------------------------
// i18n: dictionaries, fallback chain, auto detection
// ---------------------------------------------------------------------------------------------

check(
  "en 与 zh-CN 字典键完全一致（en 完备，zh 不缺键）",
  LANGUAGES.join(",") === "en,zh-CN" &&
    Object.keys(DICTIONARIES.en).every((key) => DICTIONARIES["zh-CN"][key] !== undefined) &&
    Object.keys(DICTIONARIES["zh-CN"]).every((key) => DICTIONARIES.en[key] !== undefined),
  `en=${Object.keys(DICTIONARIES.en).length} zh=${Object.keys(DICTIONARIES["zh-CN"]).length}`,
);
check(
  "语言设置守卫只接受 auto/en/zh-CN",
  isLanguageSetting("auto") &&
    isLanguageSetting("en") &&
    isLanguageSetting("zh-CN") &&
    !isLanguageSetting("fr") &&
    !isLanguageSetting(""),
  "language settings guard",
);
check(
  "resolveLanguage：精确 zh-CN、基础 zh、zh 变体都命中中文；未知与缺失回退英文",
  resolveLanguage("auto", "zh-CN") === "zh-CN" &&
    resolveLanguage("auto", "zh") === "zh-CN" &&
    resolveLanguage("auto", "zh-TW") === "zh-CN" &&
    resolveLanguage("auto", "ZH-CN") === "zh-CN" &&
    resolveLanguage("auto", "en-GB") === "en" &&
    resolveLanguage("auto", "fr") === "en" &&
    resolveLanguage("auto", "") === "en" &&
    resolveLanguage("auto", null) === "en" &&
    resolveLanguage("en", "zh-CN") === "en" &&
    resolveLanguage("zh-CN", "en") === "zh-CN",
  "auto detection",
);
check(
  "t()：当前语言命中、缺键回退 en、两边都缺回退键名、{name} 占位符插值",
  (() => {
    initI18n("zh-CN");
    const zhUpgrade = t("action.upgrade") === "升级";
    initI18n("en");
    const enUpgrade = t("action.upgrade") === "Upgrade";
    const fallbackToKey = t("i18n.missing-in-both") === "i18n.missing-in-both";
    // A key that exists only in en (a zh translation not written yet) reads as English: the check
    // plants one through a mutable view, asserts, and removes it again.
    const mutableEn = DICTIONARIES.en as Record<string, string>;
    initI18n("en");
    mutableEn["i18n.temp.only-en"] = "only english";
    initI18n("zh-CN");
    const fallsBackToEn = t("i18n.temp.only-en") === "only english";
    delete mutableEn["i18n.temp.only-en"];
    initI18n("zh-CN");
    const interpolated = t("upgrade.summary", { sent: 2, rejected: 1 }) ===
      "已发送 2 条升级 / 1 条被拒绝";
    initI18n("en");
    const interpolatedEn = t("upgrade.summary", { sent: 2, rejected: 1 }) ===
      "sent 2 upgrade(s) / 1 refused";
    initI18n("zh-CN");
    return zhUpgrade && enUpgrade && fallbackToKey && fallsBackToEn && interpolated && interpolatedEn &&
      currentI18nLanguage() === "zh-CN";
  })(),
  "t() chain",
);
check(
  "语言切换重绘 HUD 标签：动作与费用标签跟随当前语言",
  (() => {
    initI18n("en");
    const enOk =
      stopActionLabel() === "Stop" &&
      attackMoveActionLabel() === "Attack-move" &&
      emptySelectionHint().startsWith("Nothing selected") &&
      upgradeCostLabel(140, 40) === "gold 140 · wood 40";
    initI18n("zh-CN");
    const zhOk =
      stopActionLabel() === "停止" &&
      attackMoveActionLabel() === "攻击移动" &&
      emptySelectionHint().startsWith("未选中任何单位") &&
      upgradeCostLabel(140, 40) === "金 140 · 木 40";
    return enOk && zhOk;
  })(),
  "language switch repaints labels",
);

// ---------------------------------------------------------------------------------------------
// Resource HUD consolidation, BUILD rail labels and the rail spacing rhythm
// ---------------------------------------------------------------------------------------------

const mainTsText = readFileSync(new URL("../src/main.ts", import.meta.url), "utf8");

// The playtester kept one resource surface: the right-edge warehouse panel that marks items. The
// top HUD resource chips and the full resource page overlay are gone from the DOM, the CSS and the
// page wiring, while the kept panel and its mark controls stay referenced.
check(
  "资源显示只保留最右侧的仓库面板：#hud-resources / #hud-marked / #resources-overlay 从 DOM 与 main.ts 移除，#warehouse-panel 与其标记控件保留",
  !indexHtml.includes('id="hud-resources"') &&
    !indexHtml.includes('id="hud-marked"') &&
    !indexHtml.includes('id="resources-overlay"') &&
    !indexHtml.includes('id="open-resources"') &&
    !indexHtml.includes("#hud-resources") &&
    !indexHtml.includes("#hud-marked") &&
    !indexHtml.includes(".resources-overlay") &&
    !mainTsText.includes("#hud-resources") &&
    !mainTsText.includes("#hud-marked") &&
    !mainTsText.includes("#resources-overlay") &&
    !mainTsText.includes("setResourcesOpen") &&
    indexHtml.includes('id="warehouse-panel"') &&
    indexHtml.includes('id="warehouse-mark-all"') &&
    indexHtml.includes('id="warehouse-clear-marks"') &&
    mainTsText.includes("warehouseMarkAllButton") &&
    mainTsText.includes("toggleResourceMark"),
  "resource panels kept/removed",
);

// Every classic content path the client's dictionaries name resolves to a display name in both
// languages, and the server namespace cooperates: the client dictionary wins, a server-registered
// name is the fallback, and clearing/overwriting works (item 22's resolution order).
check(
  "内容标签双语解析：客户端字典优先、服务器注册名兜底、覆盖与原始路径回退",
  (() => {
    const catalogue = [
      "building.armory.t1",
      "building.mine.t1",
      "building.lumbercamp.t1",
      "building.warehouse",
      "building.turret.t1",
      ...SERVED_ARMORY_TRAINS.map((row) => row.path),
    ];
    initI18n("zh-CN");
    const zhOk =
      catalogue.every((path) => {
        const label = genericContentLabel(path);
        return label !== undefined && label !== "" && label !== path;
      }) &&
      genericContentLabel("building.armory.t1") === "兵工厂 1 级" &&
      genericContentLabel("unit.melee.t1") === "普通";
    initI18n("en");
    const enOk = genericContentLabel("building.armory.t1") === "Armory T1";
    const keysInBoth = catalogue.every(
      (path) =>
        DICTIONARIES.en[`content.${path}`] !== undefined &&
        DICTIONARIES["zh-CN"][`content.${path}`] !== undefined,
    );
    initI18n("zh-CN");
    return zhOk && enOk && keysInBoth;
  })(),
  "content labels resolve in both languages",
);

// The BUILD rail buttons are relabelled the moment they are rebuilt (a served catalogue arriving
// after the atlas no longer flashes raw paths) and the language switch re-resolves existing ones.
check(
  "BUILD 按钮重建时就地应用显示名（contentLabel），语言切换会重绘既有按钮标签",
  mainTsText.includes("const label = contentLabel(buildingPath);") &&
    mainTsText.includes("button.textContent = buildingPath;") &&
    /refreshContentLabels\(\);\n\}/.test(mainTsText),
  "build buttons relabelled",
);

// The rail follows the 14px edge rhythm: offset 14px from the window edges (pinned above) and the
// section carries 14px inner padding, so the heading and the vertical button list never touch the
// panel chrome or the screen edge.
const buildRailSectionRule =
  /(?:^|\n)\s*\.build-rail \.build-rail-section\s*\{[^}]*\}/.exec(indexHtml)?.[0] ?? "";
check(
  "BUILD 面板内外间距符合 14px 节奏：section 内边距 14px，按钮列表是带间距的垂直列表",
  buildRailSectionRule.includes("padding: 14px") &&
    /\.build-rail-buttons\s*\{[^}]*gap:\s*8px/.test(indexHtml) &&
    /\.build-rail-buttons button\s*\{[^}]*padding:\s*10px 12px/.test(indexHtml),
  "build rail spacing",
);

// ---------------------------------------------------------------------------------------------
// Content catalogue store (item 4 / item 22): defensive parse, registry names, client-side deficits
// ---------------------------------------------------------------------------------------------

const contentWorld = worldViewFromSnapshot({
  tick: 40,
  width: 1,
  height: 1,
  tiles: [["grass"]],
  entities: [{ id: 1, kind: "building.mine.t1", x: 0.5, y: 0.5, player: 1 }],
  content: {
    dimensions: { overworld: { name: "主世界" }, hell: "地狱" },
    units: { "unit.melee.t1": { name: "普通", cost: [30, 0] } },
    buildings: {
      "building.mine.t1": {
        name: "矿井",
        cost: { gold: 50, wood: 0 },
        size: [3, 3],
        recipes: [{ inputs: { gold: 20, ore: 5 } }],
      },
      "building.warehouse": [60, 20],
      "building.hell_gate": "地狱门",
    },
  },
});
const contentCatalogue = contentWorld?.content;
check(
  "content 目录防御解析：map 与数组、cost 对象与 [gold,wood] 对、size 对与对组、名字字符串都接受；坏行丢弃；缺 content 为 undefined",
  contentCatalogue !== undefined &&
    contentCatalogue.dimensions.get("overworld") === "主世界" &&
    contentCatalogue.dimensions.get("hell") === "地狱" &&
    contentCatalogue.units.get("unit.melee.t1")?.name === "普通" &&
    contentCatalogue.units.get("unit.melee.t1")?.cost?.gold === 30 &&
    contentCatalogue.buildings.get("building.mine.t1")?.name === "矿井" &&
    contentCatalogue.buildings.get("building.mine.t1")?.size?.width === 3 &&
    contentCatalogue.buildings.get("building.warehouse")?.cost?.wood === 20 &&
    worldViewFromSnapshot({ tick: 1, width: 1, height: 1, tiles: [["grass"]], entities: [] })
      ?.content === undefined &&
    parseContentCatalogue(null) === undefined &&
    parseContentCatalogue({}) === undefined,
  JSON.stringify(contentCatalogue?.buildings.get("building.mine.t1") ?? null),
);

check(
  "content 目录进入 WorldView；i18n 注册表接收目录名（content.<path> 与 dim.<key>），客户端字典优先、服务器名兜底",
  contentWorld !== null &&
    (() => {
      registerCatalogueNames(contentCatalogue);
      // A path the client dictionary does not name shows the served name verbatim...
      const serverWins = genericContentLabel("building.hell_gate") === "地狱门";
      // ...and one the client dictionary names keeps the client translation.
      const clientWins = genericContentLabel("building.mine.t1") === "矿井 普通";
      // A client dictionary entry overrides the server name for the same id.
      const warehouseClient = genericContentLabel("building.warehouse") === "仓库" &&
        DICTIONARIES["zh-CN"]["content.building.warehouse"] !== undefined;
      const unknownKey = genericContentLabel("building.mystery.t9") === undefined;
      const dimensionNames =
        dimensionLabel("hell") === "地狱" &&
        dimensionLabel("overworld") === "主世界" &&
        dimensionLabel("mystery") === "mystery";
      const directT = t("dim.hell") === "地狱";
      registerServerKeyValues({ "content.from.plain": "plainName" });
      const plainRegistered = genericContentLabel("from.plain") === "plainName";
      registerServerKeyValues({ "": "blank", "blank.value": 7 });
      const defensive = genericContentLabel("blank.value") === undefined;
      return (
        serverWins &&
        clientWins &&
        warehouseClient &&
        unknownKey &&
        dimensionNames &&
        directT &&
        plainRegistered &&
        defensive
      );
    })(),
);

const mineEntity = contentWorld?.entities[0];
check(
  "客户端推算缺料（item 4）：生产缺料 = 当前任务配方输入 − 建筑库存；建造缺料 = 目录成本 × 未完工比例；无目录/无任务/已完工都不发明数字",
  mineEntity !== undefined &&
    (() => {
      const producing: EntityView = {
        ...mineEntity,
        producingNow: { kind: "building.mine.t1", position: 1, length: 2 },
        contents: { gold: 5 },
      };
      const inputDeficit = producerDeficit(contentCatalogue, producing);
      const satisfied = producerDeficit(contentCatalogue, {
        ...producing,
        contents: { gold: 20, ore: 5 },
      });
      const noTask = producerDeficit(contentCatalogue, mineEntity);
      const noCatalogue = producerDeficit(undefined, producing);
      const constructing: EntityView = { ...mineEntity, progress: 0.5 };
      const buildDeficit = constructionDeficit(contentCatalogue, constructing);
      const finished = constructionDeficit(contentCatalogue, { ...mineEntity, progress: 1 });
      const noProgress = constructionDeficit(contentCatalogue, mineEntity);
      const unknownCost = constructionDeficit(contentCatalogue, {
        ...mineEntity,
        kind: "building.mystery.t9",
        progress: 0.5,
      });
      return (
        inputDeficit !== undefined &&
        inputDeficit.length === 2 &&
        inputDeficit[0]?.item === "gold" &&
        inputDeficit[0]?.missing === 15 &&
        inputDeficit[1]?.item === "ore" &&
        inputDeficit[1]?.missing === 5 &&
        satisfied === undefined &&
        noTask === undefined &&
        noCatalogue === undefined &&
        buildDeficit !== undefined &&
        buildDeficit.length === 1 &&
        buildDeficit[0]?.item === "gold" &&
        buildDeficit[0]?.missing === 25 &&
        finished === undefined &&
        noProgress === undefined &&
        unknownCost === undefined &&
        formatDeficit(inputDeficit, (path) => path) === "gold 15 · ore 5"
      );
    })(),
);

// ---------------------------------------------------------------------------------------------
// Bootstrap fetch-once (static per-world data): parse → session store → snapshot merge → consumers
// ---------------------------------------------------------------------------------------------

const bootstrapPayload = {
  tilePixels: 32,
  buildable: ["building.mine.t1", "  ", "building.hell_gate", 42],
  dimensions: [{ key: "overworld", name: "主世界" }, { key: "", name: "x" }, { key: "hell", name: "地狱" }],
  content: {
    dimensions: { overworld: "主世界", hell: "地狱" },
    buildings: { "building.mine.t1": { name: "矿井", cost: { gold: 50, wood: 10 }, size: [3, 3] } },
    units: { "unit.melee.t1": { name: "普通", cost: { gold: 30, wood: 0 } } },
  },
};

check(
  "bootstrap 一次性数据接入：防御解析 → 会话存储 → 快照补齐 content/buildable/dimensions；老服务器快照自带字段优先；deficit 数学照常走目录",
  (() => {
    const parsed = parseWorldBootstrap(bootstrapPayload);
    const stored =
      parsed !== undefined &&
      parsed.tilePixels === 32 &&
      parsed.buildable?.join(",") === "building.mine.t1,building.hell_gate" &&
      parsed.dimensions?.length === 2 &&
      parsed.content?.buildings.get("building.mine.t1")?.cost?.gold === 50;
    if (!stored) {
      return false;
    }
    setWorldBootstrap(parsed);
    const roundTripped = getWorldBootstrap() === parsed;
    const snap = worldViewFromSnapshot({
      tick: 1,
      width: 1,
      height: 1,
      tiles: [["grass"]],
      entities: [],
    });
    if (snap === null) {
      return false;
    }
    // A snapshot without the three fields (new server) is topped up from the store.
    const merged = withBootstrapData(snap, getWorldBootstrap());
    const toppedUp =
      merged.buildable?.join(",") === "building.mine.t1,building.hell_gate" &&
      merged.dimensions?.length === 2 &&
      merged.dimensions?.[0]?.key === "overworld" &&
      merged.dimensions?.[1]?.name === "地狱" &&
      merged.content?.buildings.get("building.mine.t1")?.cost?.wood === 10 &&
      merged.tick === 1;
    // The deficit math keeps reading the same catalogue fields, whatever delivered them.
    const deficit = constructionDeficit(merged.content, {
      kind: "building.mine.t1",
      progress: 0.5,
    });
    // An old server still ships the fields per snapshot: they win over the bootstrap store.
    const oldSnap = worldViewFromSnapshot({
      tick: 2,
      width: 1,
      height: 1,
      tiles: [["grass"]],
      entities: [],
      buildable: ["building.warehouse"],
      dimensions: [{ key: "overworld", name: "老名单" }],
      content: {
        buildings: { "building.mine.t1": { cost: { gold: 99, wood: 99 } } },
      },
    });
    const oldServerKept =
      oldSnap !== null &&
      withBootstrapData(oldSnap, getWorldBootstrap()) === oldSnap &&
      oldSnap.buildable?.join(",") === "building.warehouse" &&
      oldSnap.dimensions?.[0]?.name === "老名单" &&
      oldSnap.content?.buildings.get("building.mine.t1")?.cost?.gold === 99;
    // The registry wiring: bootstrap names land in the i18n server namespace, then the fixture
    // catalogue is re-registered so the checks above keep their expected registry state.
    registerCatalogueNames(parsed.content);
    const registered =
      serverKeyValues().get("dim.hell") === "地狱" &&
      serverKeyValues().get("content.building.mine.t1") === "矿井";
    registerCatalogueNames(contentCatalogue);
    return stored && roundTripped && toppedUp && deficit !== undefined && oldServerKept && registered;
  })(),
);

// Playtester round-trip in one place: the exact shape `WorldHost.buildBootstrapJson` emits → parse →
// i18n registry → resolved display labels (never a raw path); the TRAIN menu comes only from the
// served `trains` field (no placeholder ladder, so 战王 / 工程师 cannot leak into a row the server did
// not declare); and the page structure keeps TRAIN and BUILD swapping inside the one BUILD rail while
// 继续建造 stays deleted (the ASSIST right-click covers resume-construction).
check(
  "bootstrap 端到端：WorldHost 真实 JSON 形状 → 解析 → 服务器命名空间 → 标签解析出显示名；served trains 是唯一训练菜单来源；TRAIN/BUILD 同轨互换且 继续建造 已删除",
  (() => {
    const previousLanguage = currentI18nLanguage();
    initI18n("zh-CN");
    const serverShape = {
      tilePixels: 24,
      contentVersion: 3,
      dimensions: [
        { key: "overworld", name: "World" },
        { key: "hell", name: "地狱" },
      ],
      // Only directly-constructible buildings; upgrade-only tiers carry "buildable":false and stay out.
      buildable: [
        "building.armory.t1",
        "building.mine.t1",
        "building.lumbercamp.t1",
        "building.warehouse",
        "building.turret.t1",
        "building.hell_gate",
      ],
      content: {
        dimensions: { overworld: { name: "World" }, hell: { name: "地狱" } },
        items: { gold: { name: "金" }, wood: { name: "木" } },
        buildings: {
          "building.armory.t1": {
            name: "兵工厂",
            size: [3, 3],
            health: 900,
            cost: { gold: 200, wood: 80 },
            buildTime: 600,
            level: 1,
            upgrades: [{ to: "building.armory.t2", cost: { gold: 400, wood: 160 } }],
          },
          "building.mine.t2": {
            name: "金矿·强化",
            size: [2, 2],
            health: 400,
            buildTime: 300,
            level: 2,
            buildable: false,
          },
          "building.hell_gate": { name: "地狱门", size: [3, 3], health: 2000, level: 1 },
        },
        units: {
          "unit.melee.t1": {
            name: "普通",
            role: "melee",
            health: 100,
            speed: 1.5,
            cost: { gold: 20, wood: 5 },
            buildTime: 120,
          },
          "unit.engineer": {
            name: "工程师",
            role: "builder",
            health: 60,
            speed: 1.8,
            cost: { gold: 30 },
            buildTime: 150,
            builder: true,
          },
        },
        recipes: {
          "recipe.melee.t1": {
            producers: ["armory"],
            inputs: { gold: 20 },
            output: "unit.melee.t1",
            duration: 120,
            level: 1,
          },
        },
      },
    } satisfies Record<string, unknown>;
    const doc = parseWorldBootstrap(serverShape);
    const parsedOk =
      doc !== undefined &&
      doc.contentVersion === 3 &&
      doc.buildable?.length === 6 &&
      !doc.buildable.includes("building.mine.t2") &&
      doc.dimensions?.length === 2 &&
      doc.content?.buildings.size === 3 &&
      doc.content?.units.size === 2 &&
      doc.content?.dimensions.size === 2;
    registerCatalogueNames(doc?.content);
    const labelsResolve =
      // Client dictionary first, server name as fallback, never the raw path.
      genericContentLabel("building.armory.t1") === "兵工厂 1 级" &&
      genericContentLabel("unit.melee.t1") === "普通" &&
      genericContentLabel("unit.engineer") === "工程师" &&
      genericContentLabel("building.mine.t2") === "金矿·强化" &&
      genericContentLabel("building.hell_gate") === "地狱门" &&
      dimensionLabel("overworld", "World") === "主世界" &&
      dimensionLabel("hell", "地狱") === "地狱";
    // Placeholder exclusion: a row without the served `trains` field offers no menu at all, and the
    // served field is used verbatim when present — no client fallback ladder, no 战王/工程师 leakage.
    const servedRow = commandBuilding(20);
    const bareRow = { ...servedRow, trains: undefined } as EntityView;
    const noFallback =
      trainPathsFor(bareRow).length === 0 &&
      trainMenu(bareRow, undefined).length === 0 &&
      trainPathsFor(servedRow).join(",") === SERVED_ARMORY_TRAINS.map((row) => row.path).join(",");
    registerCatalogueNames(contentCatalogue);
    initI18n(previousLanguage);
    // Train-rail swap presence: TRAIN now lives in the same full-height rail as BUILD, and the
    // 继续建造 button is gone from the DOM, the wiring and the dictionaries.
    const structureOk =
      !indexHtml.includes("continue-build-button") &&
      !indexHtml.includes('id="continue-build-button"') &&
      !mainTsText.includes("continueBuildButton") &&
      !mainTsText.includes("doContinueBuild") &&
      !DICTIONARIES.en["ctx.continueBuild"] &&
      !DICTIONARIES["zh-CN"]["ctx.continueBuild"] &&
      mainTsText.includes('toggle("build-rail-open", showBuild || showTrain)') &&
      mainTsText.includes("refreshContentLabels();") &&
      indexHtml.includes('<aside id="build-panel"') &&
      (() => {
        const panelStart = indexHtml.indexOf('<aside id="build-panel"');
        const panelEnd = indexHtml.indexOf("</aside>", panelStart);
        const panel = indexHtml.slice(panelStart, panelEnd);
        return (
          panel.includes('id="build-section"') &&
          panel.includes('id="train-section"') &&
          panel.includes("build-rail-section")
        );
      })();
    return parsedOk === true && labelsResolve && noFallback && structureOk;
  })(),
);

check(
  "bootstrap 缺席时行为不变：空存储返回同一快照对象、字段保持 undefined；部分 bootstrap 只补缺失字段；URL 与坏文档拒绝",
  (() => {
    const snap = worldViewFromSnapshot({
      tick: 3,
      width: 1,
      height: 1,
      tiles: [["grass"]],
      entities: [],
    });
    if (snap === null) {
      return false;
    }
    const noStore = withBootstrapData(snap, undefined) === snap;
    const partial = parseWorldBootstrap({ buildable: ["building.warehouse"] });
    const partiallyTopped =
      partial !== undefined &&
      (() => {
        const merged = withBootstrapData(snap, partial);
        return (
          merged.buildable?.join(",") === "building.warehouse" &&
          merged.dimensions === undefined &&
          merged.content === undefined
        );
      })();
    clearWorldBootstrap();
    const cleared = getWorldBootstrap() === undefined;
    return (
      noStore &&
      partiallyTopped &&
      cleared &&
      WORLD_BOOTSTRAP_PATH === "/world/bootstrap" &&
      bootstrapUrl() === "/world/bootstrap" &&
      bootstrapUrl("http://127.0.0.1:25567/") === "http://127.0.0.1:25567/world/bootstrap" &&
      parseWorldBootstrap(null) === undefined &&
      parseWorldBootstrap([]) === undefined &&
      parseWorldBootstrap({}) === undefined &&
      parseWorldBootstrap({ buildable: ["  ", 42] }) === undefined
    );
  })(),
);

check(
  "bootstrap 内容版本握手：双方 contentVersion 防御解析（缺/坏字段容忍）；不相等 → refetch 一次，已处理过 → settle 不循环；任一侧不可读或相等 → 不动；换新版本重新触发",
  (() => {
    // Snapshot side: a readable non-negative integer lands on the view; an old server's missing
    // field and every malformed value degrade to `undefined` without rejecting the snapshot.
    const versioned = worldViewFromSnapshot({
      tick: 4,
      width: 1,
      height: 1,
      tiles: [["grass"]],
      entities: [],
      contentVersion: 2,
    });
    const unversioned = worldViewFromSnapshot({
      tick: 5,
      width: 1,
      height: 1,
      tiles: [["grass"]],
      entities: [],
    });
    const malformed = worldViewFromSnapshot({
      tick: 6,
      width: 1,
      height: 1,
      tiles: [["grass"]],
      entities: [],
      contentVersion: "2",
    });
    const fractional = worldViewFromSnapshot({
      tick: 7,
      width: 1,
      height: 1,
      tiles: [["grass"]],
      entities: [],
      contentVersion: 1.5,
    });
    const negative = worldViewFromSnapshot({
      tick: 8,
      width: 1,
      height: 1,
      tiles: [["grass"]],
      entities: [],
      contentVersion: -1,
    });
    const snapshotParsed =
      versioned !== null &&
      versioned.contentVersion === 2 &&
      unversioned !== null &&
      unversioned.contentVersion === undefined &&
      malformed !== null &&
      malformed.contentVersion === undefined &&
      fractional !== null &&
      fractional.contentVersion === undefined &&
      negative !== null &&
      negative.contentVersion === undefined;
    if (!snapshotParsed) {
      return false;
    }
    // Bootstrap side: the same defensiveness, and the version is stored with the document.
    const doc1 = parseWorldBootstrap({ tilePixels: 16, contentVersion: 1, buildable: ["building.mine.t1"] });
    const docNoVersion = parseWorldBootstrap({ buildable: ["building.warehouse"] });
    const docBadVersion = parseWorldBootstrap({ contentVersion: "9" });
    const docParsed =
      doc1 !== undefined &&
      doc1.contentVersion === 1 &&
      docNoVersion !== undefined &&
      docNoVersion.contentVersion === undefined &&
      docBadVersion === undefined;
    if (!docParsed) {
      return false;
    }
    // The handshake decision table for one received snapshot poll.
    const decide = (
      bootstrap: WorldBootstrap | undefined,
      snapshotVersion: number | undefined,
      refetchedFor: number | undefined,
    ) => bootstrapVersionHandshake(bootstrap, snapshotVersion, refetchedFor);
    const decided =
      decide(undefined, 2, undefined) === "none" && // no stored document (demo world): nothing to compare
      decide(doc1, undefined, undefined) === "none" && // old server snapshot: do nothing
      decide(docNoVersion, 2, undefined) === "none" && // stored document predates the field: nothing to compare
      decide(doc1, 1, undefined) === "none" && // equal: no request is spent
      decide(doc1, 2, undefined) === "refetch" && // mismatch: refetch once
      decide(doc1, 2, 2) === "settle" && // surviving mismatch: refetched doc is authoritative, no loop
      decide(doc1, 3, 2) === "refetch"; // a later bump is a new mismatch
    // End-to-end through the session holder: v1 stored → snapshot v2 detected → refetch stores v2 →
    // the same snapshot version now compares equal.
    clearWorldBootstrap();
    setWorldBootstrap(doc1);
    const detected = decide(getWorldBootstrap(), 2, undefined) === "refetch";
    const doc2 = parseWorldBootstrap({
      tilePixels: 16,
      contentVersion: 2,
      buildable: ["building.mine.t1", "building.hell_gate"],
      dimensions: [{ key: "overworld", name: "主世界" }, { key: "hell", name: "地狱" }],
    });
    if (doc2 === undefined) {
      return false;
    }
    setWorldBootstrap(doc2);
    const stored = getWorldBootstrap();
    const refetched =
      stored !== undefined &&
      stored.contentVersion === 2 &&
      stored.buildable?.length === 2 &&
      stored.dimensions?.length === 2 &&
      decide(stored, 2, undefined) === "none";
    clearWorldBootstrap();
    return snapshotParsed && docParsed && decided && detected && refetched;
  })(),
);

check(
  "NOTIFY 推送帧：前缀分流先于答案匹配（answer 永不变成 push、push 永不占用答案槽位）；防御解析（坏 JSON/非对象/坏 type → 丢弃，不抛错）；contentVersion 提取非负整数；未知 type 经注册表被忽略",
  (() => {
    // The pure router — exactly the split the socket's receive path applies BEFORE the in-order
    // answer matcher. Every ordinary answer line (and a non-canonical NOTIFY variant the server
    // never sends) stays an answer.
    const answered =
      routeFrame("OK player 7").kind === "answer" &&
      routeFrame("PONG").kind === "answer" &&
      routeFrame("ERR bad arguments").kind === "answer" &&
      routeFrame("").kind === "answer" &&
      routeFrame('notify {"type":"contentVersion","value":2}').kind === "answer" &&
      routeFrame('NOTIFY{"type":"contentVersion","value":2}').kind === "answer";
    // A readable push becomes a typed notification carrying the whole payload object.
    const push = routeFrame('NOTIFY {"type":"contentVersion","value":2}');
    const parsed =
      push.kind === "notify" &&
      push.message.type === "contentVersion" &&
      push.message.payload["value"] === 2 &&
      parseNotify('NOTIFY {"type":"contentVersion","value":2}')?.type === "contentVersion";
    // Unparsable frames degrade to `drop` (the caller logs once and drops), never throw.
    const dropped =
      routeFrame("NOTIFY {oops").kind === "drop" &&
      routeFrame("NOTIFY ").kind === "drop" &&
      routeFrame("NOTIFY [1,2]").kind === "drop" &&
      routeFrame('NOTIFY "contentVersion"').kind === "drop" &&
      routeFrame("NOTIFY 42").kind === "drop" &&
      routeFrame('NOTIFY {"value":2}').kind === "drop" &&
      routeFrame('NOTIFY {"type":42}').kind === "drop" &&
      routeFrame('NOTIFY {"type":""}').kind === "drop" &&
      routeFrame('NOTIFY {"type":"  "}').kind === "drop";
    // The registry: the known type dispatches, unknown types are ignored by design.
    const registry =
      isKnownNotifyType("contentVersion") && !isKnownNotifyType("futureThing");
    if (push.kind !== "notify") {
      return false;
    }
    // The `contentVersion` extractor: a readable value only as a non-negative safe integer; an
    // unknown type yields nothing even with a well-formed payload.
    const extracted =
      parseContentVersionNotify(push.message) === 2 &&
      parseContentVersionNotify({ type: "contentVersion", payload: { value: "2" } }) ===
        undefined &&
      parseContentVersionNotify({ type: "contentVersion", payload: {} }) === undefined &&
      parseContentVersionNotify({ type: "contentVersion", payload: { value: 2.5 } }) ===
        undefined &&
      parseContentVersionNotify({ type: "contentVersion", payload: { value: -1 } }) ===
        undefined &&
      parseContentVersionNotify({ type: "futureThing", payload: { value: 2 } }) === undefined;
    // The desync proof, mirroring the socket's dispatch loop: the router decides BEFORE the queue
    // is touched, so pushes (readable or garbage) never shift a pending answer slot.
    const queue: string[] = ["answer-1", "answer-2"];
    let pushes = 0;
    for (const frame of [
      'NOTIFY {"type":"contentVersion","value":2}',
      "NOTIFY {oops",
      "OK 3",
    ]) {
      const route = routeFrame(frame);
      if (route.kind === "answer") {
        queue.shift();
      } else {
        pushes += 1;
      }
    }
    return (
      answered && parsed && dropped && registry && extracted && pushes === 2 &&
      queue.length === 1 && queue[0] === "answer-2"
    );
  })(),
);

check(
  "攻击路线以红色绘制（item 15）：ATTACK_PATH_COLOR 是归属红，普通路线保持白/灰",
  ATTACK_PATH_COLOR.startsWith("rgba(239, 68, 68") &&
    PATH_COLOR.startsWith("rgba(214, 214, 214"),
);

// ---------------------------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------------------------

if (failures.length > 0) {
  console.error(`\n${failures.length} 项失败，${passed} 项通过：`);
  for (const failure of failures) {
    console.error(`fail - ${failure}`);
  }
  throw new Error(`${failures.length} 项渲染数学检查失败`);
}
console.log(`\n${passed} 项全部通过。`);
