/**
 * Offline checks for the pure render math.
 *
 * Nothing here touches the DOM. Node 24 strips the TypeScript types and runs the file directly, so
 * the check exercises the same `camera.ts`, `view.ts`, `status.ts`, `marquee.ts`, `toast.ts`, `atlas.ts`,
 * `fallback.ts`, `interpolate.ts`, `selection.ts`, `commandbar.ts`, `upgrade.ts`, `tooltip.ts`,
 * `events.ts`, `flash.ts`, `resources.ts` and `latex.ts` the browser imports - no duplicated formulas. `scripts/tsconfig.check.json` is only
 * used for type checking.
 *
 * Run: `node scripts/check-render-math.ts` (or `pnpm check:render`).
 */

import { readFileSync } from "node:fs";
import {
  BUILDING_CONTENT,
  BUILDING_PATHS,
  CLASSIC_BUILDING_KINDS,
  UNIT_PATHS,
  build,
  buildingContent,
  orderAttack,
  orderAttackMove,
  orderMove,
  orderStop,
  parseWhoAmI,
  train,
  trainBatch,
  trainableUnitsForBuilding,
  upgrade,
  whoAmI,
} from "../src/commands.ts";
import { HEARTBEAT_MS, shouldHeartbeat } from "../src/heartbeat.ts";
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
  compositeAtlas,
  entityDisplayName,
  itemDisplayLabel,
  mergeLayers,
  parseAtlas,
  readManifestEntry,
  tileDisplayName,
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
import {
  FLASH_COLOR,
  FLASH_MS,
  flashIntensity,
  pruneFlashes,
  startFlash,
} from "../src/render/flash.ts";
import {
  formatProgressPercent,
  incomeSuffix,
  progressBarRatio,
  queueItemNumber,
  queueMarkerText,
  queueProgressRatio,
  sizeLabel,
  unitCargoLabel,
  warehouseContentsLabel,
} from "../src/render/status.ts";
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
  snapshotOrDemo,
  snapshotUrl,
  TERRAIN_IDS,
  TERRAIN_VOID,
  terrainAt,
  tileInWindow,
  underConstruction,
  windowTileIndex,
  worldViewFromSnapshot,
  type EntityView,
  type TerrainId,
  type WorldView,
} from "../src/render/view.ts";
import {
  UPGRADE_ACTION_LABEL,
  upgradeCostLabel,
  upgradeEntry,
} from "../src/render/upgrade.ts";
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
  PLACEMENT_LEGAL_STROKE,
  PLACEMENT_OUT_OF_REACH_FILL,
  PLACEMENT_OUT_OF_REACH_STROKE,
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
  minimapLayout,
  minimapPointForTile,
  minimapTileAt,
} from "../src/render/minimap.ts";
import {
  doubleClickSelection,
  engineerForPlacement,
  engineerSelection,
  isEngineerKind,
  selectByPriority,
} from "../src/render/selection.ts";
import { pickEntityAt } from "../src/render/picking.ts";
import {
  ATTACK_MOVE_ACTION_LABEL,
  EMPTY_SELECTION_HINT,
  SELECTION_CHIP_LIMIT,
  STOP_ACTION_LABEL,
  chipLabel,
  commandBarModel,
  healthBarRatio,
  selectionChipList,
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
  escapeHtml,
  plainTexFallback,
  renderTexSegment,
  splitTex,
  type TexSegment,
} from "../src/render/latex.ts";

let passed = 0;
const failures: string[] = [];

function check(name: string, condition: boolean, detail = ""): void {
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
  "复位后镜头回到构造时给的家位置",
  near(camera.centerX, 640) && near(camera.centerY, 480),
  `${camera.centerX}, ${camera.centerY}`,
);

camera.pan(1_000_000, 1_000_000);
check(
  "向右下平移不会撞到任何地图边界",
  near(camera.centerX, 1_000_640) && near(camera.centerY, 1_000_480),
  `${camera.centerX}, ${camera.centerY}`,
);

camera.pan(-2_000_000, -2_000_000);
check(
  "向左上平移同样自由（可以越过原点进入负坐标）",
  near(camera.centerX, -999_360) && near(camera.centerY, -999_520),
  `${camera.centerX}, ${camera.centerY}`,
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
  "以光标为中心缩放时，光标下的世界点不动",
  near(anchorBefore.x, anchorAfter.x) && near(anchorBefore.y, anchorAfter.y),
  `${anchorBefore.x},${anchorBefore.y} -> ${anchorAfter.x},${anchorAfter.y}`,
);
check("缩放倍率变为 1.8", near(camera.zoom, 1.8, 1e-9), String(camera.zoom));

camera.zoomAt(400, 300, 1_000_000);
check("缩放上限是 2.5", camera.zoom === MAX_ZOOM, String(camera.zoom));
camera.zoomAt(400, 300, 0.000001);
check("缩放下限是 0.5", camera.zoom === MIN_ZOOM, String(camera.zoom));

camera.reset();
check(
  "复位同时恢复默认缩放与家位置",
  camera.zoom === 1 && near(camera.centerX, 640) && near(camera.centerY, 480),
  `${camera.zoom}, ${camera.centerX}, ${camera.centerY}`,
);

// The safety bound is not a map edge: it exists because the engine stores positions in
// single-precision Vec2 values, whose resolution degrades beyond roughly 500,000 tiles.
camera.setCenter(-100_000 * 32, -100_000 * 32);
const farFocus = camera.centerTile();
check(
  "镜头可以移动到 -100000 瓦片而不被夹紧",
  near(camera.centerX, -3_200_000) &&
    near(camera.centerY, -3_200_000) &&
    farFocus.x === -100_000 &&
    farFocus.y === -100_000,
  `${camera.centerX}, ${camera.centerY} / 焦点 ${farFocus.x}, ${farFocus.y}`,
);

camera.setCenter(-1_000_000_000_000, 1_000_000_000_000);
const boundFocus = camera.centerTile();
check(
  "镜头被停在 ±500000 瓦片的安全边界上",
  CAMERA_SAFETY_BOUND_TILES === 500_000 &&
    near(camera.centerX, -CAMERA_SAFETY_BOUND_TILES * 32) &&
    near(camera.centerY, CAMERA_SAFETY_BOUND_TILES * 32) &&
    boundFocus.x === -CAMERA_SAFETY_BOUND_TILES &&
    boundFocus.y === CAMERA_SAFETY_BOUND_TILES,
  `${camera.centerX}, ${camera.centerY} / 焦点 ${boundFocus.x}, ${boundFocus.y}`,
);

camera.setCenter(-16, -16);
check(
  "中心瓦片对负坐标向下取整（-0.5 格是瓦片 -1，不是 0）",
  camera.centerTile().x === -1 && camera.centerTile().y === -1,
  `${camera.centerTile().x}, ${camera.centerTile().y}`,
);
check(
  "屏幕取瓦片对负坐标同样向下取整",
  camera.tileAtScreen(camera.viewportWidth / 2, camera.viewportHeight / 2).x === -1 &&
    camera.tileAtScreen(camera.viewportWidth / 2, camera.viewportHeight / 2).y === -1,
  `${camera.tileAtScreen(camera.viewportWidth / 2, camera.viewportHeight / 2).x}, ` +
    `${camera.tileAtScreen(camera.viewportWidth / 2, camera.viewportHeight / 2).y}`,
);
check(
  "有符号整数瓦片判定接受任意正负坐标、拒绝小数与 NaN",
  isWorldTile(-100_000, 500_000) &&
    isWorldTile(0, 0) &&
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
  "框选矩形转换为负世界瓦片范围（100,120)-(300,260) -> (-110,-56)-(-104,-52)",
  marqueeRange.minX === -110 &&
    marqueeRange.maxX === -104 &&
    marqueeRange.minY === -56 &&
    marqueeRange.maxY === -52,
  `${marqueeRange.minX},${marqueeRange.minY}..${marqueeRange.maxX},${marqueeRange.maxY}`,
);
check(
  "反方向拖拽得到同一个矩形与同一个瓦片范围",
  JSON.stringify(screenRectBetween({ x: 300, y: 260 }, { x: 100, y: 120 })) ===
    JSON.stringify(marqueeRect) &&
    JSON.stringify(marqueeTileRange(marqueeCamera, screenRectBetween({ x: 300, y: 260 }, { x: 100, y: 120 }))) ===
      JSON.stringify(marqueeRange),
);
const zeroRect = screenRectBetween({ x: 101, y: 121 }, { x: 101, y: 121 });
check(
  "零面积矩形仍选中指针下的单格（负坐标按 floor）",
  JSON.stringify(marqueeTileRange(marqueeCamera, zeroRect)) ===
    JSON.stringify({ minX: -110, maxX: -110, minY: -56, maxY: -56 }),
  JSON.stringify(marqueeTileRange(marqueeCamera, zeroRect)),
);

const marqueeWorld: WorldView = {
  originX: 0,
  originY: 0,
  width: 8,
  height: 8,
  tiles: Array.from({ length: 8 }, () => Array.from({ length: 8 }, () => "grass" as TerrainId)),
  entities: [
    { id: 1, kind: "worker", x: 2.4, y: 2.6, playerId: 1 },
    { id: 2, kind: "soldier", x: 3.1, y: 4.9, playerId: 1 },
    { id: 3, kind: "worker", x: 2.5, y: 2.5, playerId: 2 },
    { id: 4, kind: "warehouse", x: 2.5, y: 2.5, playerId: 1 },
    { id: 5, kind: "worker", x: 7.5, y: 7.5, playerId: 1 },
    { id: 6, kind: "worker", x: 2.5, y: 3, playerId: 0 },
    { id: 7, kind: "building.lumbercamp.t1", x: 3, y: 3, playerId: 1 },
  ],
};
const marqueeSquare = { minX: 2, minY: 2, maxX: 4, maxY: 4 };
const marqueeIds = marqueeSelection(marqueeWorld, marqueeSquare, 1);
check(
  "框选优先级：战斗单位压过工兵与建筑，敌人和中立永不入选",
  JSON.stringify(marqueeIds) === JSON.stringify([2]) &&
    !marqueeIds.includes(3) &&
    !marqueeIds.includes(4) &&
    !marqueeIds.includes(6) &&
    !marqueeIds.includes(7),
  marqueeIds.join(","),
);
check(
  "位于范围外的自己单位不被选中（x=7.5）",
  !marqueeIds.includes(5) && entityInMarquee(marqueeWorld.entities[4]!, marqueeSquare, 1) === false,
);
check(
  "范围左/上边界包含，右/下边界之外排除（[min, max+1)）",
  entityInMarquee({ id: 8, kind: "worker", x: 2, y: 2, playerId: 1 }, marqueeSquare, 1) === true &&
    entityInMarquee({ id: 9, kind: "worker", x: 5, y: 2.5, playerId: 1 }, marqueeSquare, 1) ===
      false &&
    entityInMarquee({ id: 10, kind: "worker", x: 4.999, y: 4.999, playerId: 1 }, marqueeSquare, 1) ===
      true,
);
check(
  "框选的几何判定接受自己的建筑，但仍拒绝敌人与中立",
  entityInMarquee({ id: 11, kind: "building.warehouse", x: 3, y: 3, playerId: 1 }, marqueeSquare, 1) ===
    true &&
    entityInMarquee({ id: 12, kind: "building.warehouse", x: 3, y: 3, playerId: 2 }, marqueeSquare, 1) ===
      false &&
    entityInMarquee({ id: 13, kind: "building.warehouse", x: 3, y: 3, playerId: 0 }, marqueeSquare, 1) ===
      false,
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
check(
  "框选优先级：工兵压过建筑，敌人的工兵不入选",
  JSON.stringify(builderIds) === JSON.stringify([1]),
  builderIds.join(","),
);

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
  "框选只覆盖建筑时选中自己的全部建筑（敌人建筑除外）",
  JSON.stringify(buildingIds) === JSON.stringify([1, 2]),
  buildingIds.join(","),
);

// The same priority rule as a pure function, so the group choice can be read without a world or window.
const priorityEntities: EntityView[] = [
  { id: 21, kind: "unit.melee.t1", x: 2, y: 2, playerId: 1 },
  { id: 22, kind: "unit.melee.t2", x: 3, y: 3, playerId: 1 },
  { id: 23, kind: "unit.engineer", x: 3, y: 3, playerId: 1 },
  { id: 24, kind: "building.warehouse", x: 3, y: 3, playerId: 1 },
];
check(
  "selectByPriority：战斗组存在时只选战斗单位，混编的战斗单位都入选",
  JSON.stringify(selectByPriority(priorityEntities)) === JSON.stringify([21, 22]),
  selectByPriority(priorityEntities).join(","),
);
check(
  "selectByPriority：没有战斗单位时工兵压过建筑",
  JSON.stringify(selectByPriority(priorityEntities.slice(2))) === JSON.stringify([23]) &&
    JSON.stringify(selectByPriority([priorityEntities[3]!])) === JSON.stringify([24]),
  `${selectByPriority(priorityEntities.slice(2)).join(",")} / ${selectByPriority([priorityEntities[3]!]).join(",")}`,
);
check(
  "空框选（没有实体）不选中任何东西",
  selectByPriority([]).length === 0 &&
    marqueeSelection({ ...marqueeWorld, entities: [] }, marqueeSquare, 1).length === 0 &&
    marqueeSelection(buildingWorld, marqueeSquare, null).length === 0,
  `${selectByPriority([]).length} / ${marqueeSelection({ ...marqueeWorld, entities: [] }, marqueeSquare, 1).length} / ${marqueeSelection(buildingWorld, marqueeSquare, null).length}`,
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
  "框选：敌人的战斗单位与中立实体不入选，自己的建筑照常入选",
  JSON.stringify(marqueeSelection(ownershipWorld, marqueeSquare, 1)) === JSON.stringify([3]) &&
    JSON.stringify(selectByPriority(ownershipWorld.entities, 1)) === JSON.stringify([3]),
  marqueeSelection(ownershipWorld, marqueeSquare, 1).join(","),
);
check(
  "Shift 切换：先加后减，顺序稳定且不会重复",
  JSON.stringify(toggleSelection([], 1)) === JSON.stringify([1]) &&
    JSON.stringify(toggleSelection(toggleSelection([], 1), 2)) === JSON.stringify([1, 2]) &&
    JSON.stringify(toggleSelection(toggleSelection(toggleSelection([], 1), 2), 1)) ===
      JSON.stringify([2]) &&
    JSON.stringify(toggleSelection([2], 2)) === JSON.stringify([]),
  JSON.stringify(toggleSelection([2], 2)),
);

// ---------------------------------------------------------------------------------------------
// Toast console rules: normal/error lifetime, error tone, visible cap, trimming
// ---------------------------------------------------------------------------------------------

check(
  "普通 toast 约 4 秒，错误 toast 约 8 秒",
  TOAST_LIFETIME_MS === 4_000 &&
    TOAST_ERROR_LIFETIME_MS === 8_000 &&
    toastLifetimeMs("info") === 4_000 &&
    toastLifetimeMs("error") === 8_000,
  `${toastLifetimeMs("info")} / ${toastLifetimeMs("error")}`,
);
check(
  "ERR 回答与客户端拒绝都是错误 toast，普通 OK 不是",
  isErrAnswer("ERR") &&
    isErrAnswer("ERR login first") &&
    !isErrAnswer("ERROR tolerated") &&
    toastTone("ERR login first") === "error" &&
    toastTone("OK player 7") === "info" &&
    toastTone("OK", true) === "error",
);
check(
  "最多 5 条 toast 可见，超出时丢掉最旧的",
  TOAST_VISIBLE_CAP === 5 &&
    JSON.stringify(capToasts([1, 2, 3, 4, 5, 6])) === JSON.stringify([2, 3, 4, 5, 6]) &&
    JSON.stringify(capToasts([1, 2, 3])) === JSON.stringify([1, 2, 3]) &&
    capToasts([]).length === 0,
);
check(
  "toast 文本折叠换行并截断到一行",
  trimToastLine("  engine-info\n  报告  ") === "engine-info 报告" &&
    trimToastLine("abcdef", 5) === "abcd…" &&
    trimToastLine("abcdef", 2) === "a…",
  trimToastLine("abcdef", 5),
);





// ---------------------------------------------------------------------------------------------
// Demo world determinism
// ---------------------------------------------------------------------------------------------

const seed = 20260217;
const first = demoWorld(seed);
const second = demoWorld(seed);
const different = demoWorld(seed + 1);

check(
  "demoWorld 同种子生成完全一致的地图",
  JSON.stringify(first) === JSON.stringify(second),
);
check(
  "demoWorld 不同种子生成不同的地图",
  JSON.stringify(first) !== JSON.stringify(different),
);
check(
  "地图是 32 行、每行 44 格",
  first.width === 44 &&
    first.height === 32 &&
    first.tiles.length === first.height &&
    first.tiles.every((row) => row.length === first.width),
  `${first.width}x${first.height}, ${first.tiles.length} rows`,
);
check("占位地图有 12 个实体", first.entities.length === 12, String(first.entities.length));

const entityIds = new Set(first.entities.map((entity) => entity.id));
check("实体 id 互不重复", entityIds.size === first.entities.length, String(entityIds.size));

const kinds = new Set(first.entities.map((entity) => entity.kind));
for (const kind of ["worker", "soldier", "medic", "warehouse", "barracks"]) {
  check(`地图包含 ${kind}`, kinds.has(kind));
}
check(
  "地图里正好两座建筑",
  first.entities.filter((entity) => isBuildingKind(entity.kind)).length === 2,
);
check(
  "所有实体都在地图内",
  first.entities.every(
    (entity) => entity.x > 0 && entity.x < first.width && entity.y > 0 && entity.y < first.height,
  ),
);
check(
  "所有实体都站在可建造的陆地上",
  first.entities.every((entity) => {
    const terrain = terrainAt(first, Math.floor(entity.x), Math.floor(entity.y));
    return terrain === "grass" || terrain === "sand";
  }),
);
check(
  "血条比例都在 [0, 1]",
  first.entities.every(
    (entity) =>
      entity.healthRatio === undefined ||
      (entity.healthRatio >= 0 && entity.healthRatio <= 1),
  ),
);

const terrainSet = tileTypes(first);
for (const terrain of ["water", "sand", "grass", "forest", "hill", "rock"]) {
  check(`地图包含地形 ${terrain}`, terrainSet.has(terrain));
}

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
check("服务器快照转换成功", serverSnapshot !== null);
if (serverSnapshot !== null) {
  check("快照保留 tick 与宽高", serverSnapshot.tick === 17 && serverSnapshot.width === 3 &&
    serverSnapshot.height === 2);
  check(
    "地形字符串被转换成渲染地形 id（去掉命名空间前缀）",
    serverSnapshot.tiles[0]?.[0] === "grass" &&
      serverSnapshot.tiles[0]?.[2] === "forest" &&
      serverSnapshot.tiles[1]?.[0] === "rock",
    JSON.stringify(serverSnapshot.tiles),
  );

  const worker = serverSnapshot.entities.find((entity) => entity.id === 1);
  check(
    "实体字段被映射：kind/x/y/playerId/healthRatio",
    worker !== undefined &&
      worker.kind === "worker" &&
      worker.x === 1.5 &&
      worker.y === 0.5 &&
      worker.playerId === 1 &&
      worker.healthRatio === 0.8,
    JSON.stringify(worker),
  );
  const warehouse = serverSnapshot.entities.find((entity) => entity.id === 2);
  check(
    "建筑同样使用内容路径作为 kind",
    warehouse !== undefined && warehouse.kind === "warehouse" && warehouse.playerId === 2,
    JSON.stringify(warehouse),
  );
  check("快照实体数量正确", serverSnapshot.entities.length === 2);
}

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
    { id: 3, kind: "worker", x: 0, y: 0, player: 1, w: 1, h: 1, progress: 0.8 },
  ],
});
const detailedWarehouse = detailedSnapshot?.entities.find((entity) => entity.id === 1);
const detailedBarracks = detailedSnapshot?.entities.find((entity) => entity.id === 2);
const detailedWorker = detailedSnapshot?.entities.find((entity) => entity.id === 3);
check(
  "快照的 w/h 被解析成 footprint（建筑 3×3）",
  detailedWarehouse?.footprint?.width === 3 && detailedWarehouse?.footprint?.height === 3,
  JSON.stringify(detailedWarehouse?.footprint),
);
check(
  "快照的 progress / queue / queueTicks 被保留",
  detailedBarracks?.progress === 0.42 &&
    detailedBarracks?.queue === 2 &&
    detailedBarracks?.queueTicks === 60,
  JSON.stringify(detailedBarracks),
);
check(
  "queueItems 按顺序保留 kind/ticks/total，去掉越界、total=0 与空 kind 的坏项",
  detailedBarracks?.queueItems?.length === 2 &&
    detailedBarracks.queueItems[0]?.kind === "unit.melee.t1" &&
    detailedBarracks.queueItems[0]?.ticks === 87 &&
    detailedBarracks.queueItems[0]?.total === 120 &&
    detailedBarracks.queueItems[1]?.kind === "unit.engineer" &&
    detailedBarracks.queueItems[1]?.ticks === 45 &&
    detailedBarracks.queueItems[1]?.total === 45,
  JSON.stringify(detailedBarracks?.queueItems),
);
check(
  "单位也带 1×1 的 footprint 与自己的 progress",
  detailedWorker?.footprint?.width === 1 &&
    detailedWorker?.footprint?.height === 1 &&
    detailedWorker?.progress === 0.8,
  JSON.stringify(detailedWorker),
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
  "没有新字段的旧快照仍然转换（新字段全部可选）",
  legacySnapshot !== null &&
    legacyEntity !== undefined &&
    legacyEntity.footprint === undefined &&
    legacyEntity.progress === undefined &&
    legacyEntity.queue === undefined &&
    legacyEntity.queueTicks === undefined &&
    legacyEntity.queueItems === undefined,
  JSON.stringify(legacyEntity),
);

check(
  "进度格式化为整数百分比，并夹在 0%..100%",
  formatProgressPercent(0) === "0%" &&
    formatProgressPercent(0.5) === "50%" &&
    formatProgressPercent(1) === "100%" &&
    formatProgressPercent(0.645) === "65%" &&
    formatProgressPercent(-1) === "0%" &&
    formatProgressPercent(2) === "100%" &&
    formatProgressPercent(Number.NaN) === "0%",
  formatProgressPercent(0.645),
);
check(
  "进度条比例夹在 [0,1]，非有限值为 0",
  progressBarRatio(-2) === 0 &&
    progressBarRatio(0) === 0 &&
    progressBarRatio(0.25) === 0.25 &&
    progressBarRatio(2) === 1 &&
    progressBarRatio(Number.NaN) === 0,
);
check(
  "队列标记只在有排队任务时出现（生产中 ×N）",
  queueMarkerText(undefined) === "" &&
    queueMarkerText(0) === "" &&
    queueMarkerText(-3) === "" &&
    queueMarkerText(1) === "生产中 ×1" &&
    queueMarkerText(2) === "生产中 ×2",
  queueMarkerText(2),
);
check(
  "收入后缀只在字段存在时出现：+30/分，0 也显示，缺失或负数不显示",
  incomeSuffix(undefined) === "" &&
    incomeSuffix(-1) === "" &&
    incomeSuffix(Number.NaN) === "" &&
    incomeSuffix(0) === " (+0/分)" &&
    incomeSuffix(30) === " (+30/分)" &&
    incomeSuffix(20.9) === " (+20/分)",
  incomeSuffix(30),
);
check(
  "队列项进度用 ticks/total，开始前 ticks=total 时条为空，并夹在 [0,1]",
  queueProgressRatio({ kind: "unit.melee.t1", ticks: 87, total: 120 }) === 87 / 120 &&
    queueProgressRatio({ kind: "unit.melee.t1", ticks: 120, total: 120 }) === 1 &&
    queueProgressRatio({ kind: "unit.melee.t1", ticks: 0, total: 120 }) === 0 &&
    queueProgressRatio({ kind: "unit.melee.t1", ticks: 200, total: 120 }) === 1 &&
    queueProgressRatio({ kind: "unit.melee.t1", ticks: 1, total: 0 }) === 0,
  String(queueProgressRatio({ kind: "unit.melee.t1", ticks: 87, total: 120 })),
);
check(
  "队列项编号从 1 开始：1/2、2/2，空队列为 0/0",
  queueItemNumber(0, 2) === "1/2" &&
    queueItemNumber(1, 2) === "2/2" &&
    queueItemNumber(5, 2) === "2/2" &&
    queueItemNumber(0, 0) === "0/0",
  queueItemNumber(0, 2),
);
check(
  "尺寸标签输出 尺寸：W×H，缺 footprint 时为 null",
  detailedWarehouse !== undefined &&
    sizeLabel(detailedWarehouse) === "尺寸：3×3" &&
    detailedWorker !== undefined &&
    sizeLabel(detailedWorker) === "尺寸：1×1" &&
    sizeLabel({ id: 9, kind: "warehouse", x: 0, y: 0 }) === null,
  detailedWarehouse === undefined ? "missing" : String(sizeLabel(detailedWarehouse)),
);
check(
  "仓库的 contents 被解析成非负整数库存，零值条目被丢弃",
  detailedWarehouse?.contents?.gold === 120 &&
    detailedWarehouse?.contents?.wood === 40 &&
    !("ore" in (detailedWarehouse?.contents ?? {})) &&
    legacyEntity?.contents === undefined,
  JSON.stringify(detailedWarehouse?.contents),
);
check(
  "库存标签按固定顺序输出 库存：金 120 · 木 40，无库存时为 null",
  detailedWarehouse !== undefined &&
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
  "携带标签：一个物品输出 携带：金币 7",
  unitCargoLabel(cargoUnit, cargoLabelFor) === "携带：金币 7",
  String(unitCargoLabel(cargoUnit, cargoLabelFor)),
);
check(
  "携带标签：两个物品按固定顺序输出 携带：金币 7 · 木材 3",
  unitCargoLabel({ ...cargoUnit, contents: { gold: 7, wood: 3 } }, cargoLabelFor) ===
    "携带：金币 7 · 木材 3",
  String(unitCargoLabel({ ...cargoUnit, contents: { gold: 7, wood: 3 } }, cargoLabelFor)),
);
check(
  "携带标签：没有货物时不显示任何行",
  unitCargoLabel({ ...cargoUnit, contents: undefined }, cargoLabelFor) === null &&
    unitCargoLabel({ ...cargoUnit, contents: {} }, cargoLabelFor) === null &&
    unitCargoLabel({ ...cargoUnit, contents: { gold: 0 } }, cargoLabelFor) === null,
  String(unitCargoLabel({ ...cargoUnit, contents: {} }, cargoLabelFor)),
);
check(
  "携带标签：清单里没有的物品回退到原始路径",
  unitCargoLabel({ ...cargoUnit, contents: { "oc:unobtainium": 2 } }, cargoLabelFor) ===
    "携带：oc:unobtainium 2",
  String(unitCargoLabel({ ...cargoUnit, contents: { "oc:unobtainium": 2 } }, cargoLabelFor)),
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
check("顶层不是对象时拒绝", worldViewFromSnapshot(null) === null);
check(
  "行数与 height 不符时拒绝",
  worldViewFromSnapshot({ ...validSnapshot, height: 2, tiles: [["grass", "grass"]] }) === null,
);
check(
  "行宽与 width 不符时拒绝",
  worldViewFromSnapshot({ ...validSnapshot, tiles: [["grass"], ["grass"]] }) === null,
);
check(
  "非字符串地形时拒绝",
  worldViewFromSnapshot({ ...validSnapshot, tiles: [[1, "grass"], ["grass", "grass"]] }) === null,
);
check(
  "实体坐标为 NaN 时拒绝",
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
  "窗口原点不是整数时仍然拒绝",
  worldViewFromSnapshot({ ...validSnapshot, originX: -0.5, originY: 0 }) === null &&
    worldViewFromSnapshot({ ...validSnapshot, originX: 0, originY: 1.5 }) === null,
);
check(
  "窗口原点为 NaN 或 Infinity 时拒绝",
  worldViewFromSnapshot({ ...validSnapshot, originX: Number.NaN, originY: 0 }) === null &&
    worldViewFromSnapshot({ ...validSnapshot, originX: 0, originY: Number.POSITIVE_INFINITY }) ===
      null,
);
check(
  "实体缺少 kind 时拒绝",
  worldViewFromSnapshot({
    ...validSnapshot,
    entities: [{ id: 1, kind: "", x: 1, y: 1 }],
  }) === null,
);
check(
  "实体缺少 id 时拒绝",
  worldViewFromSnapshot({ ...validSnapshot, entities: [{ kind: "worker", x: 1, y: 1 }] }) ===
    null,
);
check(
  "超出范围的血量被夹到 [0, 1]",
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
    { id: 1, name: "Player 1", gold: 0, wood: 0 },
    { id: 3, name: "alice", gold: 300, wood: 60, goldPerMinute: 30, woodPerMinute: 20 },
  ],
});
check(
  "带 players 的快照解析成功",
  playersSnapshot !== null && playersSnapshot.players?.length === 2,
  JSON.stringify(playersSnapshot?.players),
);
const aliceRow = playersSnapshot === null ? undefined : playersSnapshot.players?.[1];
check(
  "players 行保留 id/name/gold/wood",
  aliceRow?.id === 3 &&
    aliceRow.name === "alice" &&
    aliceRow.gold === 300 &&
    aliceRow.wood === 60,
  JSON.stringify(aliceRow),
);
check(
  "players 行保留可选的 goldPerMinute / woodPerMinute，缺失时是 undefined",
  aliceRow?.goldPerMinute === 30 &&
    aliceRow.woodPerMinute === 20 &&
    playersSnapshot?.players?.[0]?.goldPerMinute === undefined &&
    playersSnapshot?.players?.[0]?.woodPerMinute === undefined,
  JSON.stringify(aliceRow),
);
const malformedRateSnapshot = worldViewFromSnapshot({
  ...validSnapshot,
  players: [
    { id: 3, name: "alice", gold: 300, wood: 60, goldPerMinute: -1, woodPerMinute: "20" },
  ],
});
check(
  "格式错误的收入速率被忽略而不是拒绝整个世界（旧客户端兼容）",
  malformedRateSnapshot !== null &&
    malformedRateSnapshot.players?.[0]?.goldPerMinute === undefined &&
    malformedRateSnapshot.players?.[0]?.woodPerMinute === undefined,
  JSON.stringify(malformedRateSnapshot?.players),
);
check(
  "没有 players 的旧快照仍然有效，players 是 undefined",
  worldViewFromSnapshot(validSnapshot)?.players === undefined,
);
check(
  "players 不是数组时拒绝",
  worldViewFromSnapshot({ ...validSnapshot, players: { id: 3 } }) === null,
);
check(
  "players 行缺少 gold 时拒绝",
  worldViewFromSnapshot({
    ...validSnapshot,
    players: [{ id: 3, name: "alice", wood: 60 }],
  }) === null,
);
check(
  "players 行金额为负或不是整数时拒绝",
  worldViewFromSnapshot({
    ...validSnapshot,
    players: [{ id: 3, name: "alice", gold: -1, wood: 60 }],
  }) === null &&
    worldViewFromSnapshot({
      ...validSnapshot,
      players: [{ id: 3, name: "alice", gold: 1.5, wood: 60 }],
    }) === null,
);
check(
  "players 行 name 为空时拒绝",
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
  "带负 originX/originY 的窗口快照解析成功，且不带地图大小",
  windowed !== null &&
    windowed.originX === -16 &&
    windowed.originY === -16 &&
    windowed.width === 4 &&
    windowed.height === 3 &&
    !Object.prototype.hasOwnProperty.call(windowed, "mapWidth") &&
    !Object.prototype.hasOwnProperty.call(windowed, "mapHeight"),
  JSON.stringify(windowed),
);
if (windowed !== null) {
  check(
    "负原点把绝对瓦片映射到正确的窗口索引（含 -1 边界）",
    windowTileIndex(windowed, -16, -16)?.x === 0 &&
      windowTileIndex(windowed, -16, -16)?.y === 0 &&
      windowTileIndex(windowed, -13, -14)?.x === 3 &&
      windowTileIndex(windowed, -13, -14)?.y === 2 &&
      windowTileIndex(windowed, -17, -16) === null &&
      windowTileIndex(windowed, -16, -17) === null &&
      windowTileIndex(windowed, -12, -16) === null &&
      windowTileIndex({ ...windowed, originX: 0, originY: 0 }, -1, 0) === null,
  );
  check(
    "窗口索引对负小数使用 floor（-0.1 落在索引 0，而不是 trunc 的 1）",
    windowTileIndex({ ...windowed, originX: -1, originY: -1 }, -0.1, -0.1)?.x === 0 &&
      windowTileIndex({ ...windowed, originX: -1, originY: -1 }, -0.1, -0.1)?.y === 0,
  );
  check(
    "负原点窗口按绝对坐标绘制地形",
    terrainAt(windowed, -16, -16) === "grass" &&
      terrainAt(windowed, -15, -15) === "hill" &&
      terrainAt(windowed, -13, -14) === "grass",
  );
  check(
    "负原点窗口外的绝对瓦片返回 void（不是窗口内的错误格子）",
    terrainAt(windowed, -17, -16) === TERRAIN_VOID &&
      terrainAt(windowed, -12, -16) === TERRAIN_VOID &&
      terrainAt(windowed, -16, -13) === TERRAIN_VOID &&
      terrainAt(windowed, -16, -17) === TERRAIN_VOID,
  );
  const insideEntity = windowed.entities.find((entity) => entity.id === 1);
  const outsideEntity = windowed.entities.find((entity) => entity.id === 2);
  check(
    "窗口外的实体仍保留在快照里，但 entityInWindow 为 false；窗口内的实体为 true",
    windowed.entities.length === 2 &&
      insideEntity !== undefined &&
      outsideEntity !== undefined &&
      entityInWindow(windowed, insideEntity) === true &&
      entityInWindow(windowed, outsideEntity) === false &&
      tileInWindow(windowed, -15, -15) === true &&
      tileInWindow(windowed, 200, 200) === false,
  );
}
check(
  "窗口原点可以是任意有符号整数（很大且为负也不拒绝）",
  worldViewFromSnapshot({ ...windowSnapshotPayload, originX: -100_000, originY: 50_000 })
    ?.originX === -100_000 &&
    worldViewFromSnapshot({ ...windowSnapshotPayload, originX: -100_000, originY: 50_000 })
      ?.originY === 50_000,
);
const legacySized = worldViewFromSnapshot({
  ...windowSnapshotPayload,
  mapWidth: 512,
  mapHeight: 512,
});
check(
  "旧快照附带的 mapWidth/mapHeight 被忽略（世界无界，不再读取地图大小）",
  legacySized !== null &&
    legacySized.originX === -16 &&
    legacySized.width === 4 &&
    !Object.prototype.hasOwnProperty.call(legacySized, "mapWidth") &&
    !Object.prototype.hasOwnProperty.call(legacySized, "mapHeight"),
  JSON.stringify(legacySized),
);
check(
  "没有 origin 的旧快照以 (0,0) 作为窗口原点（向后兼容）",
  worldViewFromSnapshot(validSnapshot)?.originX === 0 &&
    worldViewFromSnapshot(validSnapshot)?.originY === 0,
);
check(
  "构造出的窗口请求 URL 使用负中心（不被当成越界）",
  snapshotUrl("", { cx: -1_250, cy: -582, r: SNAPSHOT_RADIUS_TILES }) ===
    "/world/snapshot?cx=-1250&cy=-582&r=32" &&
    snapshotUrl("http://127.0.0.1:25567/", { cx: -1, cy: -32, r: 32 }) ===
      "http://127.0.0.1:25567/world/snapshot?cx=-1&cy=-32&r=32" &&
    snapshotUrl("", null) === "/world/snapshot",
);

// ---------------------------------------------------------------------------------------------
// Camera-driven window streaming: signed centres, the duplicate-origin guard and request ordering
// ---------------------------------------------------------------------------------------------

const streamBounds = {
  originX: -1_282,
  originY: -614,
  width: 64,
  height: 64,
};

check(
  "窗口边缘触发：负坐标窗口距边缘正好 8 格时请求新窗口",
  SNAPSHOT_EDGE_MARGIN_TILES === 8 &&
    SNAPSHOT_RADIUS_TILES === 32 &&
    shouldStreamWindow(streamBounds, { x: -1_274, y: -590 }) &&
    shouldStreamWindow(streamBounds, { x: -1_227, y: -590 }) &&
    shouldStreamWindow(streamBounds, { x: -1_250, y: -606 }) &&
    shouldStreamWindow(streamBounds, { x: -1_250, y: -559 }) &&
    shouldRequestWindow(streamBounds, { x: -1_274, y: -590 }) &&
    shouldRequestWindow(streamBounds, { x: -1_227, y: -590 }) &&
    shouldRequestWindow(streamBounds, { x: -1_250, y: -606 }) &&
    shouldRequestWindow(streamBounds, { x: -1_250, y: -559 }),
);
check(
  "窗口边缘触发：距边缘 9 格时保持当前窗口",
  !shouldStreamWindow(streamBounds, { x: -1_273, y: -590 }) &&
    !shouldRequestWindow(streamBounds, { x: -1_273, y: -590 }) &&
    !shouldRequestWindow(streamBounds, { x: -1_228, y: -590 }) &&
    !shouldRequestWindow(streamBounds, { x: -1_250, y: -605 }) &&
    !shouldRequestWindow(streamBounds, { x: -1_250, y: -560 }),
);
check(
  "有符号中心的窗口原点就是 centre - radius，且同一原点不会重复请求",
  snapshotOrigin({ x: -1_250, y: -582 }, SNAPSHOT_RADIUS_TILES).x === -1_282 &&
    snapshotOrigin({ x: -1_250, y: -582 }, SNAPSHOT_RADIUS_TILES).y === -614 &&
    !windowNeedsStream(streamBounds, { x: -1_250, y: -582 }) &&
    windowNeedsStream(streamBounds, { x: -1_250, y: -590 }),
);
check(
  "窗口中心瓦片用于没有触碰边缘时的定期刷新（负坐标）",
  windowCentre(streamBounds).x === -1_250 && windowCentre(streamBounds).y === -582,
  `${windowCentre(streamBounds).x}, ${windowCentre(streamBounds).y}`,
);
check(
  "负原点窗口的边缘距离在边界内外以有符号数表示",
  windowEdgeDistance({ originX: -10, originY: -10, width: 4, height: 4 }, { x: -10, y: -10 }) ===
    0 &&
    windowEdgeDistance({ originX: -10, originY: -10, width: 4, height: 4 }, { x: -11, y: -10 }) ===
      -1 &&
    windowEdgeDistance({ originX: -10, originY: -10, width: 4, height: 4 }, { x: -7, y: -7 }) === 0,
);

const requestTracker = new SnapshotRequestTracker();
const firstRequestId = requestTracker.begin();
const secondRequestId = requestTracker.begin();
check(
  "快照请求 id 单调递增",
  secondRequestId === firstRequestId + 1 && requestTracker.latest === secondRequestId,
  `${firstRequestId} -> ${secondRequestId}`,
);
check("较新的快照回答被接受", requestTracker.accept(secondRequestId) === true);
check(
  "乱序到达的旧快照回答被丢弃",
  requestTracker.accept(firstRequestId) === false,
  `next=${requestTracker.latest}`,
);
check(
  "从未发出的请求 id（0 或未来值）不会被接受",
  requestTracker.accept(0) === false && requestTracker.accept(99) === false,
);

// The page keeps the loaded window unless the tracker accepts a non-null response. Model that rule
// with two windows around a negative centre: the newer answer lands first, then the older one.
const oldWindow = { originX: -1_100, originY: -1_100, width: 64, height: 64 };
const newWindow = { originX: -1_132, originY: -1_132, width: 64, height: 64 };
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
  "插值系数：在新帧到达之前和到达时刻都夹到 0",
  alphaFor(250, 500, 500) === 0 && alphaFor(500, 500, 500) === 0,
  `${alphaFor(250, 500, 500)}, ${alphaFor(500, 500, 500)}`,
);
check(
  "插值系数：一个轮询间隔的正中间是 0.5",
  alphaFor(750, 500, 500) === 0.5,
  String(alphaFor(750, 500, 500)),
);
check(
  "插值系数：满一个轮询间隔及之后都夹到 1",
  alphaFor(1_000, 500, 500) === 1 && alphaFor(9_000, 500, 500) === 1,
  `${alphaFor(1_000, 500, 500)}, ${alphaFor(9_000, 500, 500)}`,
);

const atStart = interpolate(previousSnapshot, currentSnapshot, 0);
const atMiddle = interpolate(previousSnapshot, currentSnapshot, 0.5);
const atEnd = interpolate(previousSnapshot, currentSnapshot, 1);
const startMover = atStart.entities.find((entity) => entity.id === 1);
const middleMover = atMiddle.entities.find((entity) => entity.id === 1);
const endMover = atEnd.entities.find((entity) => entity.id === 1);
check(
  "插值端点：alpha 0 用旧帧位置，alpha 1 用新帧位置",
  startMover?.x === 2 &&
    startMover.y === 3 &&
    endMover?.x === 4 &&
    endMover.y === 3,
  `${JSON.stringify(startMover)}, ${JSON.stringify(endMover)}`,
);
check(
  "两帧都有的实体移动 2 格时，alpha 0.5 位于中点",
  middleMover?.x === 3 && middleMover.y === 3,
  JSON.stringify(middleMover),
);
check(
  "只在新帧出现的实体按新位置出现",
  atMiddle.entities.some((entity) => entity.id === 3 && entity.x === 9 && entity.y === 9),
  JSON.stringify(atMiddle.entities),
);
check(
  "只在旧帧出现的实体消失",
  atMiddle.entities.every((entity) => entity.id !== 2) && atMiddle.entities.length === 2,
  JSON.stringify(atMiddle.entities),
);

const jumpSnapshot: Frame = {
  tick: 120,
  view: frameView([frameEntity(1, 12, 3)]),
};
const jumped = interpolate(previousSnapshot, jumpSnapshot, 0.5).entities.find(
  (entity) => entity.id === 1,
);
check(
  "一次跳动 10 格的实体直接吸附到新帧，不在地图上滑过",
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
  "player 变化的实体吸附到新帧",
  ownerChanged?.playerId === 2 && ownerChanged.x === 4 && ownerChanged.y === 3,
  JSON.stringify(ownerChanged),
);

// ---------------------------------------------------------------------------------------------
// Atlas parsing
// ---------------------------------------------------------------------------------------------

const parsed = parseAtlas(
  {
    image: "atlas.png",
    tilePixels: 32,
    tiles: {
      grass: [0, 0, 32, 32],
      water: [32, 0, 32, 32],
      bad: [250, 0, 32, 32],
    },
    entities: {
      worker: {
        frames: [
          [0, 32, 32, 48],
          [250, 0, 32, 32],
        ],
        fps: 6,
      },
      soldier: { frames: [[0, 80, 32, 48]] },
      broken: { frames: [[500, 0, 32, 32]] },
      notAnObject: 7,
    },
  },
  256,
  320,
);

check("合法图集解析成功", parsed.ok);
if (parsed.ok) {
  const worker = parsed.data.entities.get("worker");
  check(
    "越界帧被丢弃，合法帧保留",
    worker !== undefined && worker.frames.length === 1,
    String(worker?.frames.length),
  );
  check(
    "合法地形帧保留，越界地形帧丢弃",
    parsed.data.tiles.has("grass") && parsed.data.tiles.has("water") && !parsed.data.tiles.has("bad"),
    String(parsed.data.tiles.size),
  );
  check(
    "越界帧会给出警告",
    parsed.warnings.some((warning) => warning.includes("tiles.bad")) &&
      parsed.warnings.some((warning) => warning.includes("entities.worker.frames")),
  );
  check("所有帧都越界的实体被丢弃", !parsed.data.entities.has("broken"));
  check("非对象实体条目被丢弃", !parsed.data.entities.has("notAnObject"));
  check("显式 fps 被保留", worker?.fps === 6, String(worker?.fps));
  check(
    "缺省 anchor 是脚底 [0.5, 0.75]",
    worker?.anchor[0] === 0.5 && worker?.anchor[1] === 0.75,
  );
  check("缺省 scale 是 1", worker?.scale === 1);
}

const minimal = parseAtlas(
  {
    image: "atlas.png",
    tiles: { grass: [0, 0, 16, 16] },
    entities: { worker: { frames: [[0, 0, 16, 16]] } },
  },
  64,
  64,
);
check("缺省 tilePixels 按 32 处理", minimal.ok && minimal.data.tilePixels === 32);
if (minimal.ok) {
  const worker = minimal.data.entities.get("worker");
  check("缺省 fps 是 4", worker?.fps === 4);
  check("缺省 scale 是 1", worker?.scale === 1);
}

// The manifest may label tiles and entity kinds. A named tile uses the {"rect": ..., "name": ...}
// object form; the old array form stays valid and simply has no label.
const namedFixture = parseAtlas(
  {
    image: "atlas.png",
    tiles: {
      grass: { rect: [0, 0, 16, 16], name: "草地" },
      water: [16, 0, 16, 16],
    },
    entities: {
      "unit.melee.t1": { frames: [[0, 0, 16, 16]], name: "普通" },
      "unit.engineer": { frames: [[16, 0, 16, 16]] },
    },
  },
  64,
  64,
);
check("带 name 的清单解析成功", namedFixture.ok);
if (namedFixture.ok) {
  // The merge never reads pixels; a private placeholder keeps this block independent of the
  // composite-atlas section's shared image further down.
  const namedImage = { naturalWidth: 1, naturalHeight: 1 } as unknown as HTMLImageElement;
  const namedAtlas = compositeAtlas([{ ...namedFixture.data, image: namedImage }]);
  check(
    "带 name 的清单得到预期标签表（按地形路径与实体 kind 暴露）",
    namedAtlas !== null &&
      namedAtlas.names.tiles.get("grass") === "草地" &&
      namedAtlas.names.tiles.get("water") === "water" &&
      namedAtlas.names.entities.get("unit.melee.t1") === "普通" &&
      namedAtlas.names.entities.get("unit.engineer") === "unit.engineer",
    JSON.stringify({
      tiles: [...(namedAtlas?.names.tiles ?? [])],
      entities: [...(namedAtlas?.names.entities ?? [])],
    }),
  );
  check(
    "没有 name 时标签函数回退到原始 id，带命名空间的 kind 也能命中",
    entityDisplayName(namedAtlas, "unit.engineer") === "unit.engineer" &&
      entityDisplayName(namedAtlas, "oc:unit.melee.t1") === "普通" &&
      entityDisplayName(null, "unit.melee.t1") === "unit.melee.t1" &&
      tileDisplayName(namedAtlas, "water") === "water" &&
      tileDisplayName(namedAtlas, "oc:grass") === "草地" &&
      tileDisplayName(null, "water") === "water",
  );
}

// A plugin may define items (not art) in the manifest's optional `items` table; the full resource page
// treats those paths as "every resource that exists" even when the player owns none.
const itemFixture = parseAtlas(
  {
    image: "atlas.png",
    tiles: { grass: [0, 0, 8, 8] },
    items: {
      gold: "金币",
      wood: { name: "木材" },
      "oc:ore": "矿石",
      blank: "",
    },
  },
  8,
  8,
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
  const itemImage = { naturalWidth: 1, naturalHeight: 1 } as unknown as HTMLImageElement;
  const itemAtlas = compositeAtlas([{ ...itemFixture.data, image: itemImage }]);
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

check("非对象图集被拒绝", parseAtlas(null, 64, 64).ok === false);
check(
  "缺少 image 的图集被拒绝",
  parseAtlas({ tiles: { grass: [0, 0, 1, 1] } }, 64, 64).ok === false,
);
check("没有可用帧的图集被拒绝", parseAtlas({ image: "atlas.png" }, 64, 64).ok === false);
check(
  "无效图片尺寸被拒绝",
  parseAtlas({ image: "atlas.png", tiles: { grass: [0, 0, 1, 1] } }, 0, 0).ok === false,
);

// ---------------------------------------------------------------------------------------------
// Composite atlas: the server's manifest array, last wins
// ---------------------------------------------------------------------------------------------

// Node has no HTMLImageElement and the merge never reads the pixels, so one placeholder is enough for
// the checks that only need the attachment step.
const PLACEHOLDER_IMAGE = { naturalWidth: 1, naturalHeight: 1 } as unknown as HTMLImageElement;

const firstEntry = parseAtlas(
  {
    plugin: "oc-hello",
    atlasUrl: "/plugin-assets/oc-hello/atlas.png",
    image: "atlas.png",
    tilePixels: 32,
    tiles: { grass: [0, 0, 32, 32], water: [32, 0, 32, 32] },
    entities: { worker: { frames: [[0, 32, 32, 48]] } },
  },
  256,
  80,
);
const secondEntry = parseAtlas(
  {
    plugin: "oc-forest",
    atlasUrl: "/plugin-assets/oc-forest/atlas.png",
    image: "atlas.png",
    tilePixels: 16,
    tiles: { grass: [16, 0, 16, 16], sand: [0, 0, 16, 16] },
    entities: {
      worker: { frames: [[0, 16, 16, 24]], fps: 8 },
      soldier: { frames: [[16, 16, 16, 24]] },
    },
  },
  128,
  64,
);
check("两个插件条目都解析成功", firstEntry.ok && secondEntry.ok);

if (firstEntry.ok && secondEntry.ok) {
  const merged = mergeLayers([firstEntry.data, secondEntry.data]);
  check("后面的插件覆盖同名地形（last wins）", merged.tiles.get("grass") === 1,
    String(merged.tiles.get("grass")));
  check("只在前一条目出现的地形仍然保留", merged.tiles.get("water") === 0,
    String(merged.tiles.get("water")));
  check("只在后一条目出现的地形被加入", merged.tiles.get("sand") === 1,
    String(merged.tiles.get("sand")));
  check("后面的插件覆盖同名实体（last wins）", merged.entities.get("worker") === 1,
    String(merged.entities.get("worker")));
  check("只在前一条目出现的实体仍然保留", merged.entities.get("soldier") === 1,
    String(merged.entities.get("soldier")));
  check("贡献插件按出现顺序去重", merged.plugins.join(",") === "oc-hello,oc-forest",
    merged.plugins.join(","));

  const sources: readonly AtlasSource[] = [
    { ...firstEntry.data, image: PLACEHOLDER_IMAGE },
    { ...secondEntry.data, image: PLACEHOLDER_IMAGE },
  ];
  const composite = compositeAtlas(sources);
  check(
    "合成图集用胜出插件的帧与图片",
    composite?.tiles.get("grass")?.rect.x === 16 &&
      composite.tiles.get("grass")?.source.plugin === "oc-forest",
    String(composite?.tiles.get("grass")?.rect.x),
  );
  check(
    "合成图集里每个名字都记住自己的来源条目",
    composite?.entities.get("worker")?.source.plugin === "oc-forest" &&
      composite?.entities.get("soldier")?.source.plugin === "oc-forest",
  );

  const single = compositeAtlas([{ ...firstEntry.data, image: PLACEHOLDER_IMAGE }]);
  check(
    "只有一个插件时合成图集照常工作",
    single?.tiles.get("water")?.rect.x === 32 &&
      single.tiles.get("water")?.source.plugin === "oc-hello" &&
      single.entities.get("worker")?.source.plugin === "oc-hello",
  );
}

check("空数组没有图集，客户端因此回退到程序化绘制", compositeAtlas([]) === null);

const urlOnly = parseAtlas(
  { atlasUrl: "/plugin-assets/oc-hello/atlas.png", tiles: { grass: [0, 0, 32, 32] } },
  32,
  32,
);
check(
  "只有 atlasUrl 的条目也能解析，并从 URL 得到文件名",
  urlOnly.ok && urlOnly.data.imageName === "atlas.png" && urlOnly.data.plugin === "",
);

// The checked-in classic manifest is the one the game view actually receives, so prove from disk
// that every tile and every entity kind carries a usable name, not just that the parser accepts it.
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
const classicAtlas = parseAtlas(classicManifest, 96, 128);
check("经典插件的 manifest.json 可以被解析", classicAtlas.ok);
const classicRoot = recordTable(classicManifest);
const classicTiles = classicRoot === null ? null : recordTable(classicRoot["tiles"]);
const classicEntities = classicRoot === null ? null : recordTable(classicRoot["entities"]);
check(
  "经典清单的每个地块都有非空 name",
  classicAtlas.ok &&
    classicTiles !== null &&
    classicAtlas.data.tiles.size === Object.keys(classicTiles).length &&
    [...classicAtlas.data.tiles.values()].every((tile) => tile.name.trim() !== ""),
  classicAtlas.ok
    ? `${classicAtlas.data.tiles.size} tiles / ${classicTiles === null ? "?" : Object.keys(classicTiles).length} parsed`
    : classicAtlas.reason,
);
check(
  "经典清单的每个实体 kind 都有非空 name",
  classicAtlas.ok &&
    classicEntities !== null &&
    classicAtlas.data.entities.size === Object.keys(classicEntities).length &&
    [...classicAtlas.data.entities.values()].every((entity) => entity.name.trim() !== ""),
  classicAtlas.ok
    ? `${classicAtlas.data.entities.size} entities / ${classicEntities === null ? "?" : Object.keys(classicEntities).length} parsed`
    : classicAtlas.reason,
);

const goodEntry = readManifestEntry({
  plugin: "oc-hello",
  atlasUrl: "/plugin-assets/oc-hello/atlas.png",
});
check(
  "合法条目给出 plugin 与 atlasUrl",
  goodEntry.ok &&
    goodEntry.entry.plugin === "oc-hello" &&
    goodEntry.entry.atlasUrl === "/plugin-assets/oc-hello/atlas.png",
);
const malformedEntries = [
  readManifestEntry(null),
  readManifestEntry("atlas.png"),
  readManifestEntry({}),
  readManifestEntry({ atlasUrl: 7 }),
  readManifestEntry({ atlasUrl: "plugin-assets/atlas.png" }),
  readManifestEntry({ atlasUrl: "http://evil.example/atlas.png" }),
  readManifestEntry({ atlasUrl: "//evil.example/atlas.png" }),
];
check(
  "畸形条目被跳过并各自给出警告",
  malformedEntries.every((result) => !result.ok && result.warning !== ""),
);
check(
  "缺少 plugin 的条目仍可加载它的图片",
  readManifestEntry({ atlasUrl: "/plugin-assets/x/atlas.png" }).ok === true,
);

check("没有图集时 HUD 显示 procedural", atlasLabel([]) === "procedural");
check("一个插件时 HUD 直接列出插件 id", atlasLabel(["oc-hello"]) === "atlas · oc-hello");
check(
  "三个插件时 HUD 列出全部 id",
  atlasLabel(["oc-hello", "oc-forest", "oc-debug"]) === "atlas · oc-hello, oc-forest, oc-debug",
);
check("超过三个插件时 HUD 显示数量", atlasLabel(["a", "b", "c", "d"]) === "atlas · 4 plugins");
check("重复插件只算一次", atlasLabel(["a", "a", "b"]) === "atlas · a, b");

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
function throwsChinese(action: () => string): boolean {
  try {
    action();
    return false;
  } catch (error: unknown) {
    return error instanceof Error && /[\u4e00-\u9fff]/u.test(error.message);
  }
}

// Golden lines: exactly the grammar documented in protocol/oc-protocol.md.
check("whoAmI 生成 WHOAMI", whoAmI() === "WHOAMI", whoAmI());
check(
  "orderMove 生成 ORDER MOVE",
  orderMove(7, 12, 8) === "ORDER MOVE 7 12 8",
  orderMove(7, 12, 8),
);
check(
  "orderAttack 生成 ORDER ATTACK",
  orderAttack(7, 42) === "ORDER ATTACK 7 42",
  orderAttack(7, 42),
);
check(
  "orderAttackMove 生成 ORDER ATTACKMOVE",
  orderAttackMove(7, 3, 4) === "ORDER ATTACKMOVE 7 3 4",
  orderAttackMove(7, 3, 4),
);
check("orderStop 生成 ORDER STOP", orderStop(7) === "ORDER STOP 7", orderStop(7));
check(
  "build 生成 BUILD",
  build(9, "building.armory.t1", 20, 40) === "BUILD 9 building.armory.t1 20 40",
  build(9, "building.armory.t1", 20, 40),
);
check(
  "train 生成 TRAIN",
  train(42, "unit.melee.t1") === "TRAIN 42 unit.melee.t1",
  train(42, "unit.melee.t1"),
);

check(
  "实体 id 必须是正整数（0、负数、小数、NaN、Infinity、超出安全整数都拒绝）",
  [0, -1, 1.5, Number.NaN, Number.POSITIVE_INFINITY, Number.MAX_SAFE_INTEGER + 1].every((id) =>
    throwsChinese(() => orderStop(id)),
  ),
);
check(
  "目标 id 也必须是正整数",
  [0, -2, 3.25, Number.NaN].every((id) => throwsChinese(() => orderAttack(7, id))),
);
check(
  "ORDER 的坐标必须是有限数字",
  [Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY].every(
    (value) =>
      throwsChinese(() => orderMove(7, value, 1)) &&
      throwsChinese(() => orderMove(7, 1, value)) &&
      throwsChinese(() => orderAttackMove(7, value, 1)),
  ),
);
check(
  "BUILD 的坐标必须是有限数字",
  [Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY].every(
    (value) =>
      throwsChinese(() => build(9, "building.armory.t1", value, 2)) &&
      throwsChinese(() => build(9, "building.armory.t1", 2, value)),
  ),
);
check(
  "内容路径必须非空且不含空白或换行",
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
  "建筑目录与面板一致",
  JSON.stringify(BUILDING_PATHS) ===
    JSON.stringify([
      "building.armory.t1",
      "building.mine.t1",
      "building.lumbercamp.t1",
      "building.warehouse",
    ]),
  BUILDING_PATHS.join(", "),
);
check(
  "建筑目录包含木材营地 building.lumbercamp.t1",
  BUILDING_PATHS.includes("building.lumbercamp.t1") &&
    build(1, "building.lumbercamp.t1", 5, 6) === "BUILD 1 building.lumbercamp.t1 5 6",
  BUILDING_PATHS.join(", "),
);
check(
  "建筑目录包含工程师可建的仓库 building.warehouse",
  BUILDING_PATHS.includes("building.warehouse") &&
    build(1, "building.warehouse", 7, 8) === "BUILD 1 building.warehouse 7 8",
  BUILDING_PATHS.join(", "),
);
check(
  "单位目录与面板一致",
  JSON.stringify(UNIT_PATHS) ===
    JSON.stringify([
      "unit.melee.t1",
      "unit.melee.t2",
      "unit.melee.t3",
      "unit.melee.t4",
      "unit.melee.t5",
      "unit.engineer",
    ]),
  UNIT_PATHS.join(", "),
);
check(
  "目录里每条路径都能原样交给对应的构建器",
  UNIT_PATHS.every((path) => train(1, path) === `TRAIN 1 ${path}`) &&
    BUILDING_PATHS.every((path) => build(1, path, 0, 0) === `BUILD 1 ${path} 0 0`),
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
  "初始编队为空，1..9 都是合法编号，超出范围会拒绝",
  CONTROL_GROUP_MAX === 9 &&
    recallControlGroup(groups, 1).length === 0 &&
    describeControlGroup(groups, 9) === "编队 9 为空" &&
    [0, 10, 1.5, Number.NaN].every((group) =>
      rejectsBadGroup(() => recallControlGroup(groups, group)),
    ),
);
groups = assignControlGroup(groups, 1, [3, 5, 3, 0, -1, 3.5]);
check(
  "assign 替换编队、只保留正整数 id 并去重，原值没有被修改",
  JSON.stringify(recallControlGroup(groups, 1)) === JSON.stringify([3, 5]) &&
    recallControlGroup(EMPTY_CONTROL_GROUPS, 1).length === 0 &&
    describeControlGroup(groups, 1) === "编队 1：2 个实体",
  JSON.stringify(recallControlGroup(groups, 1)),
);
groups = addControlGroup(groups, 1, [5, 7, 7]);
check(
  "add 保留已有 id 与顺序，只追加新 id",
  JSON.stringify(recallControlGroup(groups, 1)) === JSON.stringify([3, 5, 7]) &&
    describeControlGroup(groups, 1) === "编队 1：3 个实体",
  JSON.stringify(recallControlGroup(groups, 1)),
);
groups = assignControlGroup(groups, 2, []);
check(
  "空选择也能绑定一个空编队，describe 明确报告为空",
  recallControlGroup(groups, 2).length === 0 && describeControlGroup(groups, 2) === "编队 2 为空",
  describeControlGroup(groups, 2),
);
groups = pruneControlGroups(groups, [3, 7]);
check(
  "prune 删除已不存在的 id，保留顺序与空编队，未变化时返回原对象",
  JSON.stringify(recallControlGroup(groups, 1)) === JSON.stringify([3, 7]) &&
    describeControlGroup(groups, 1) === "编队 1：2 个实体" &&
    describeControlGroup(groups, 2) === "编队 2 为空" &&
    pruneControlGroups(groups, [3, 7]) === groups,
  JSON.stringify(recallControlGroup(groups, 1)),
);
check(
  "recall 返回副本，调用方改动不会污染编队",
  (() => {
    const recalled = recallControlGroup(groups, 1) as number[];
    recalled.push(99);
    return JSON.stringify(recallControlGroup(groups, 1)) === JSON.stringify([3, 7]);
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
  "建筑足迹查表：经典目录里的四种建筑都是 3×3（命名空间也能命中）",
  BUILDING_PATHS.every((path) => {
    const footprint = footprintForKind(path);
    return footprint.width === 3 && footprint.height === 3;
  }) && footprintForKind("oc:building.warehouse").height === 3,
  BUILDING_PATHS.map((path) => `${path}=${footprintForKind(path).width}`).join(", "),
);
check(
  "未知建筑 kind 的足迹回退到默认 3×3",
  DEFAULT_BUILDING_FOOTPRINT.width === 3 &&
    DEFAULT_BUILDING_FOOTPRINT.height === 3 &&
    footprintForKind("building.mystery.t9").width === 3 &&
    footprintForKind("building.mystery.t9").height === 3 &&
    footprintForKind("").width === 3,
  JSON.stringify(footprintForKind("building.mystery.t9")),
);
check(
  "足迹瓦片列表按行优先展开，负原点与小尺寸也正确",
  JSON.stringify(footprintTiles({ x: 2, y: 3 }, { width: 2, height: 2 })) ===
    JSON.stringify([
      { x: 2, y: 3 },
      { x: 3, y: 3 },
      { x: 2, y: 4 },
      { x: 3, y: 4 },
    ]) &&
    footprintTiles({ x: -2, y: -1 }, { width: 3, height: 2 }).length === 6 &&
    footprintTiles({ x: -2, y: -1 }, { width: 3, height: 2 })[5]?.x === 0 &&
    footprintTiles({ x: 0, y: 0 }, { width: 0, height: 3 }).length === 0,
  JSON.stringify(footprintTiles({ x: 2, y: 3 }, { width: 2, height: 2 })),
);
check(
  "实体足迹占用：3×3 建筑覆盖右下角，不覆盖左/右一格之外",
  entityFootprint(PLACEMENT_BUILDING).width === 3 &&
    entityFootprintCovers(PLACEMENT_BUILDING, 5, 5) &&
    entityFootprintCovers(PLACEMENT_BUILDING, 7, 7) &&
    !entityFootprintCovers(PLACEMENT_BUILDING, 8, 5) &&
    !entityFootprintCovers(PLACEMENT_BUILDING, 4, 5),
);
check(
  "单位只占脚下 1 格，小数坐标按 floor（负坐标同样）",
  entityFootprint(PLACEMENT_ENGINEER).width === 1 &&
    entityFootprintCovers(PLACEMENT_ENGINEER, 1, 1) &&
    !entityFootprintCovers(PLACEMENT_ENGINEER, 2, 1) &&
    entityFootprintCovers({ ...PLACEMENT_ENGINEER, x: -0.2, y: -1.1 }, -1, -2) &&
    !entityFootprintCovers({ ...PLACEMENT_ENGINEER, x: -0.2, y: -1.1 }, 0, -2),
);
check(
  "占用测试来自实体列表：没有 footprint 的旧实体只占自己的一格",
  tileOccupiedByEntities(placementFixture.entities, 6, 6) &&
    tileOccupiedByEntities(placementFixture.entities, 1, 1) &&
    !tileOccupiedByEntities(placementFixture.entities, 0, 0) &&
    tileOccupiedByEntities([{ id: 9, kind: "warehouse", x: 4, y: 6, playerId: 1 }], 4, 6) &&
    !tileOccupiedByEntities([{ id: 9, kind: "warehouse", x: 4, y: 6, playerId: 1 }], 5, 6),
);
check(
  "工兵范围：恰好 16 格可达，17 格超出（MAX_BUILD_REACH_TILES = 16）",
  MAX_BUILD_REACH_TILES === 16 &&
    buildReachDistance({ x: 0, y: 0 }, { x: 16, y: 0 }, ONE_TILE_FOOTPRINT) === 16 &&
    isWithinBuildReach({ x: 0, y: 0 }, { x: 16, y: 0 }, ONE_TILE_FOOTPRINT) &&
    buildReachDistance({ x: 0, y: 0 }, { x: 17, y: 0 }, ONE_TILE_FOOTPRINT) === 17 &&
    !isWithinBuildReach({ x: 0, y: 0 }, { x: 17, y: 0 }, ONE_TILE_FOOTPRINT),
  `${buildReachDistance({ x: 0, y: 0 }, { x: 17, y: 0 }, ONE_TILE_FOOTPRINT)}`,
);
check(
  "工兵范围对负坐标同样成立（-20 到 -4 是 16 格，到 -3 是 17 格）",
  isWithinBuildReach({ x: -20, y: 0 }, { x: -4, y: 0 }, ONE_TILE_FOOTPRINT) &&
    !isWithinBuildReach({ x: -20, y: 0 }, { x: -3, y: 0 }, ONE_TILE_FOOTPRINT),
);
check(
  "范围按足迹矩形边缘计算：站在足迹内是 0 格，紧贴边缘是 1 格",
  buildReachDistance({ x: 1, y: 1 }, { x: 0, y: 0 }, { width: 3, height: 3 }) === 0 &&
    buildReachDistance({ x: 3, y: 3 }, { x: 0, y: 0 }, { width: 3, height: 3 }) === 1,
);
check(
  "幽灵状态优先级：blocked > out-of-reach > legal",
  combinePlacementStates(["legal", "out-of-reach"]) === "out-of-reach" &&
    combinePlacementStates(["out-of-reach", "blocked", "legal"]) === "blocked" &&
    combinePlacementStates(["legal", "legal"]) === "legal" &&
    combinePlacementStates([]) === "legal",
  combinePlacementStates(["legal", "out-of-reach"]),
);

const legalPreview = buildPlacementPreview(
  placementFixture,
  "building.warehouse",
  { x: 1, y: 5 },
  PLACEMENT_ENGINEER,
);
check(
  "合法工地：9 个足迹格全部 legal，状态行显示可建造",
  legalPreview.state === "legal" &&
    legalPreview.reason === "legal" &&
    legalPreview.tiles.length === 9 &&
    legalPreview.tiles.every((tile) => tile.state === "legal") &&
    placementReasonText(legalPreview.reason) === "可建造",
  JSON.stringify(legalPreview.tiles[0]),
);
const waterPreview = buildPlacementPreview(
  placementFixture,
  "building.warehouse",
  { x: 2, y: 2 },
  PLACEMENT_ENGINEER,
);
check(
  "水域阻挡：水格 blocked，整块工地报地形不可建",
  waterPreview.state === "blocked" &&
    waterPreview.reason === "blocked-terrain" &&
    waterPreview.tiles[0]?.state === "blocked" &&
    waterPreview.tiles[0]?.reason === "blocked-terrain" &&
    placementReasonText(waterPreview.reason) === "地形不可建",
  JSON.stringify(waterPreview.tiles[0]),
);
const occupiedPreview = buildPlacementPreview(
  placementFixture,
  "building.warehouse",
  { x: 5, y: 5 },
  PLACEMENT_ENGINEER,
);
check(
  "实体阻挡：仓库覆盖的格子全部 blocked，整块工地报位置被占用",
  occupiedPreview.state === "blocked" &&
    occupiedPreview.reason === "blocked-occupied" &&
    occupiedPreview.tiles.every(
      (tile) => tile.state === "blocked" && tile.reason === "blocked-occupied",
    ) &&
    placementReasonText(occupiedPreview.reason) === "位置被占用",
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
    windowPreview.tiles.some(
      (tile) => tile.state === "blocked" && tile.reason === "blocked-window",
    ) &&
    placementReasonText(windowPreview.reason) === "超出已载入窗口",
  JSON.stringify(windowPreview.tiles.find((tile) => tile.state === "blocked")),
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
check(
  "状态行五种原因文本固定",
  placementReasonText("legal") === "可建造" &&
    placementReasonText("blocked-terrain") === "地形不可建" &&
    placementReasonText("blocked-occupied") === "位置被占用" &&
    placementReasonText("out-of-reach") === "超出工兵范围 16 格" &&
    placementReasonText("blocked-window") === "超出已载入窗口",
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
    overlappingRightPreview.reason === "blocked-occupied" &&
    overlappingRightPreview.tiles.some((tile) => tile.reason === "blocked-occupied"),
  `${flushRightPreview.state} / ${overlappingRightPreview.state}:${overlappingRightPreview.reason}`,
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
  "客户端幽灵在负坐标上同样按覆盖格判定：origin -3 的建筑与 origin 0 紧贴合法，origin -1 重叠非法",
  negativeFlushPreview.state === "legal" &&
    negativeFlushPreview.tiles.every((tile) => tile.state === "legal") &&
    negativeOverlapPreview.state === "blocked" &&
    negativeOverlapPreview.reason === "blocked-occupied",
  `${negativeFlushPreview.state} / ${negativeOverlapPreview.state}:${negativeOverlapPreview.reason}`,
);
check(
  "幽灵颜色是画布内容：合法绿 25% 填充、阻挡红、超距琥珀",
  PLACEMENT_LEGAL_FILL === "rgba(74, 222, 128, 0.25)" &&
    PLACEMENT_LEGAL_STROKE.startsWith("rgba(74, 222, 128") &&
    PLACEMENT_BLOCKED_FILL.startsWith("rgba(239, 68, 68") &&
    PLACEMENT_BLOCKED_STROKE.startsWith("rgba(239, 68, 68") &&
    PLACEMENT_OUT_OF_REACH_FILL.startsWith("rgba(245, 158, 11") &&
    PLACEMENT_OUT_OF_REACH_STROKE.startsWith("rgba(245, 158, 11") &&
    placementFillColor("legal") === PLACEMENT_LEGAL_FILL &&
    placementFillColor("blocked") === PLACEMENT_BLOCKED_FILL &&
    placementFillColor("out-of-reach") === PLACEMENT_OUT_OF_REACH_FILL &&
    placementStrokeColor("legal") === PLACEMENT_LEGAL_STROKE &&
    placementStrokeColor("blocked") === PLACEMENT_BLOCKED_STROKE &&
    placementStrokeColor("out-of-reach") === PLACEMENT_OUT_OF_REACH_STROKE,
);

// ---------------------------------------------------------------------------------------------
// Minimap: layout fitting, panel<->world mapping in both directions, and the clamped camera jump
// ---------------------------------------------------------------------------------------------

/** A 64x64 window with a negative origin: the server's usual 2 * 32 window west and north of zero. */
const squareMap = { originX: -100, originY: -50, width: 64, height: 64 };
const squareLayout = minimapLayout(squareMap);
check(
  "小地图布局：64×64 窗口正好铺满 180px 面板，点是 2×2",
  MINIMAP_PANEL_SIZE === 180 &&
    MINIMAP_DOT_SIZE === 2 &&
    near(squareLayout.scale, 180 / 64) &&
    near(squareLayout.contentWidth, 180) &&
    near(squareLayout.contentHeight, 180) &&
    near(squareLayout.offsetX, 0) &&
    near(squareLayout.offsetY, 0),
  `${squareLayout.scale} / ${squareLayout.contentWidth}×${squareLayout.contentHeight}`,
);
const wideMap = { originX: 0, originY: 0, width: 44, height: 32 };
const wideLayout = minimapLayout(wideMap);
check(
  "小地图布局：非方形窗口按比例信箱化，不被拉伸",
  near(wideLayout.scale, 180 / 44) &&
    near(wideLayout.contentWidth, 180) &&
    near(wideLayout.contentHeight, (32 * 180) / 44) &&
    near(wideLayout.offsetX, 0) &&
    near(wideLayout.offsetY, (180 - (32 * 180) / 44) / 2),
  `${wideLayout.scale} / ${wideLayout.contentHeight} / ${wideLayout.offsetY}`,
);
check(
  "小地图映射：面板左上角就是负原点的窗口左上角，右下角是最后一格",
  JSON.stringify(minimapPointForTile(squareMap, -100, -50)) ===
    JSON.stringify({ x: 0, y: 0 }) &&
    JSON.stringify(minimapTileAt(squareMap, 0, 0)) ===
      JSON.stringify({ x: -100, y: -50 }) &&
    JSON.stringify(minimapTileAt(squareMap, 179.999, 179.999)) ===
      JSON.stringify({ x: -37, y: 13 }),
  JSON.stringify(minimapTileAt(squareMap, 179.999, 179.999)),
);
check(
  "小地图双向映射在负坐标下互相还原（含格内偏移）",
  (() => {
    const point = minimapPointForTile(squareMap, -95, -45);
    const back = minimapTileAt(squareMap, point.x + 1, point.y + 1);
    return (
      near(point.x, 5 * (180 / 64)) &&
      near(point.y, 5 * (180 / 64)) &&
      back?.x === -95 &&
      back?.y === -45
    );
  })(),
);
check(
  "小地图取格使用 floor：格线属于右下方的格子",
  (() => {
    const boundary = minimapPointForTile(squareMap, -95, -45);
    const before = minimapTileAt(squareMap, boundary.x - 0.001, boundary.y);
    const on = minimapTileAt(squareMap, boundary.x, boundary.y);
    const after = minimapTileAt(squareMap, boundary.x + squareLayout.scale + 0.001, boundary.y);
    return before?.x === -96 && on?.x === -95 && after?.x === -94;
  })(),
  `${minimapTileAt(squareMap, 14.0625, 14.0625)?.x}`,
);
check(
  "小地图点击在内容区外返回 null（面板外、信箱区与负数）",
  minimapTileAt(squareMap, -0.001, 0) === null &&
    minimapTileAt(squareMap, 0, -0.001) === null &&
    minimapTileAt(squareMap, 180, 90) === null &&
    minimapTileAt(wideMap, 90, 5) === null &&
    minimapTileAt(wideMap, 100, 100)?.x === 24 &&
    minimapTileAt(wideMap, 100, 100)?.y === 18 &&
    minimapCameraTarget(squareMap, -5, 0, 32) === null,
  JSON.stringify(minimapTileAt(wideMap, 100, 100)),
);
check(
  "小地图点击把落点格的格中心设为镜头中心",
  (() => {
    const target = minimapCameraTarget({ originX: 10, originY: 20, width: 2, height: 2 }, 45, 45, 32);
    return target !== null && near(target.x, 10.5 * 32) && near(target.y, 20.5 * 32);
  })(),
  JSON.stringify(
    minimapCameraTarget({ originX: 10, originY: 20, width: 2, height: 2 }, 45, 45, 32),
  ),
);
check(
  "小地图镜头跳转被夹在 ±500000 瓦片安全边界上",
  (() => {
    const target = minimapCameraTarget(
      { originX: 600_000, originY: -600_000, width: 2, height: 2 },
      45,
      45,
      32,
    );
    return (
      target !== null &&
      target.x === CAMERA_SAFETY_BOUND_TILES * 32 &&
      target.y === -CAMERA_SAFETY_BOUND_TILES * 32
    );
  })(),
  JSON.stringify(
    minimapCameraTarget({ originX: 600_000, originY: -600_000, width: 2, height: 2 }, 45, 45, 32),
  ),
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
check(
  "提示框显示清单名、加粗归属字段与生命百分比",
  unitTooltip.name === "普通" &&
    unitTooltip.ownerTag === "我" &&
    JSON.stringify(unitTooltip.lines) === JSON.stringify(["生命 72%"]),
  `${unitTooltip.name} / 归属：${unitTooltip.ownerTag} / ${unitTooltip.lines.join(" / ")}`,
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
check(
  "提示框为建筑追加尺寸与队列摘要（归属：敌 / 生命 42% / 尺寸 / 生产中）",
  buildingTooltip.name === "兵工厂 1 级" &&
    buildingTooltip.ownerTag === "敌" &&
    JSON.stringify(buildingTooltip.lines) ===
      JSON.stringify(["生命 42%", "尺寸：3×3", "生产中 ×2"]),
  `归属：${buildingTooltip.ownerTag} / ${buildingTooltip.lines.join(" / ")}`,
);
const noHealthTooltip = tooltipContent(
  "矿井",
  { id: 13, kind: "building.mine.t1", x: 0, y: 0, playerId: 1, footprint: { width: 3, height: 3 } },
  "mine",
);
check(
  "没有 healthRatio 的旧快照不编造生命值，建筑仍显示尺寸",
  noHealthTooltip.ownerTag === "我" &&
    JSON.stringify(noHealthTooltip.lines) === JSON.stringify(["尺寸：3×3"]),
  noHealthTooltip.lines.join(" / "),
);
const neutralTooltip = tooltipContent("worker", { id: 14, kind: "worker", x: 0, y: 0 }, "neutral");
check(
  "无主单位只有中立归属，queue=0 不显示队列摘要",
  neutralTooltip.ownerTag === "中立" &&
    JSON.stringify(neutralTooltip.lines) === JSON.stringify([]),
  neutralTooltip.lines.join(" / "),
);
check(
  "提示框默认边距 8px、光标间距 14px",
  TOOLTIP_MARGIN_PX === 8 && TOOLTIP_GAP_PX === 14,
  `${TOOLTIP_MARGIN_PX} / ${TOOLTIP_GAP_PX}`,
);
const flippedTooltip = clampTooltipPosition(
  { x: 900, y: 700 },
  { width: 200, height: 80 },
  { width: 1000, height: 800 },
);
check(
  "靠近右/下边缘时提示框翻到光标另一侧，仍夹在窗口内",
  JSON.stringify(flippedTooltip) === JSON.stringify({ x: 686, y: 606 }),
  JSON.stringify(flippedTooltip),
);
const edgeTooltip = clampTooltipPosition(
  { x: 995, y: 795 },
  { width: 200, height: 80 },
  { width: 1000, height: 800 },
);
check(
  "指针在右下角极限位置时提示框完整落在窗口内",
  edgeTooltip.x === 781 &&
    edgeTooltip.y === 701 &&
    edgeTooltip.x + 200 <= 1000 - TOOLTIP_MARGIN_PX &&
    edgeTooltip.y + 80 <= 800 - TOOLTIP_MARGIN_PX,
  JSON.stringify(edgeTooltip),
);
const tinyViewportTooltip = clampTooltipPosition(
  { x: 50, y: 50 },
  { width: 200, height: 100 },
  { width: 100, height: 50 },
);
check(
  "窗口比提示框还小时仍从左上边距开始，不返回负坐标",
  JSON.stringify(tinyViewportTooltip) === JSON.stringify({ x: 8, y: 8 }),
  JSON.stringify(tinyViewportTooltip),
);

// The thumbnail half of the hover tooltip: a fixed 48×48 square whose source is chosen by the same
// atlas lookup as the world renderer, with a neutral grey placeholder carrying the kind's first
// character when that lookup has no sprite.
const atlasThumbnail = tooltipThumbnail(true, "unit.melee.t1");
const placeholderThumbnail = tooltipThumbnail(false, "unit.melee.t1");
const blankThumbnail = tooltipThumbnail(false, "  ");
check(
  "缩略图：有图集帧时用图集，无图集帧时用占位符，两者都是固定 48×48",
  TOOLTIP_THUMBNAIL_SIZE_PX === 48 &&
    atlasThumbnail.kind === "atlas" &&
    atlasThumbnail.size === 48 &&
    placeholderThumbnail.kind === "placeholder" &&
    placeholderThumbnail.size === 48 &&
    placeholderThumbnail.label === "u",
  `${atlasThumbnail.kind}/${atlasThumbnail.size} / ${placeholderThumbnail.kind}/${placeholderThumbnail.size}`,
);
check(
  "缩略图占位符取 kind 的首个 Unicode 字符：空 kind 是 ?，代理对不会被截断",
  kindFirstCharacter("building.warehouse") === "b" &&
    kindFirstCharacter("  ") === "?" &&
    kindFirstCharacter("\u{1F600}unit") === "\u{1F600}" &&
    tooltipThumbnail(false, "").kind === "placeholder" &&
    blankThumbnail.kind === "placeholder" &&
    blankThumbnail.label === "?",
  `${kindFirstCharacter("building.warehouse")} / ${kindFirstCharacter("  ")}`,
);
check(
  "悬停提示在同一实体上停留过久会自行隐藏，换实体重新计时",
  TOOLTIP_IDLE_HIDE_MS === 6_000 &&
    !tooltipIdleExpired(1_000, 6_999) &&
    tooltipIdleExpired(1_000, 7_000) &&
    !tooltipIdleExpired(1_000, 20_000, 0) &&
    !tooltipIdleExpired(Number.NaN, 20_000),
  String(TOOLTIP_IDLE_HIDE_MS),
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
  return { id, kind, x: id, y: id, playerId, progress: 1 };
}

const emptyCommand = commandBarModel([], 1);
check(
  "未选中时命令栏只有一条提示，没有任何动作或芯片",
  emptyCommand.kind === "none" &&
    emptyCommand.mixed === false &&
    emptyCommand.actions.length === 0 &&
    emptyCommand.hint === EMPTY_SELECTION_HINT,
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
      `${STOP_ACTION_LABEL},${ATTACK_MOVE_ACTION_LABEL}`,
  `${unitCommand.kind} / ${unitCommand.actions.map((action) => action.kind).join(",")}`,
);

const engineerCommand = commandBarModel([commandUnit(3, "unit.engineer")], 1);
check(
  "选中工兵时在单位动作之外给出全部 BUILD 条目（右下角）",
  engineerCommand.kind === "engineer" &&
    engineerCommand.actions.slice(0, 2).map((action) => action.kind).join(",") ===
      "stop,attack-move" &&
    engineerCommand.actions.filter((action) => action.kind === "build").length ===
      BUILDING_PATHS.length &&
    engineerCommand.actions
      .filter((action) => action.kind === "build")
      .every((action) => action.zone === "context") &&
    engineerCommand.actions
      .filter((action) => action.kind === "build")
      .map((action) => action.path)
      .join(",") === BUILDING_PATHS.join(","),
  `${engineerCommand.kind} / ${engineerCommand.actions.length}`,
);

const buildingCommand = commandBarModel([commandBuilding(4)], 1, {
  labelForPath: (path) => (path === "unit.melee.t1" ? "普通" : path),
});
check(
  "选中自己的建筑时只给出 TRAIN 条目（右下角），没有单位动作",
  buildingCommand.kind === "building" &&
    buildingCommand.actions.length === UNIT_PATHS.length &&
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
    multiBuildingCommand.actions.length === UNIT_PATHS.length &&
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
    upgrade: { to: "building.armory.t2", gold, wood },
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
check(
  "自己的建筑有 upgrade 字段时出现升级条目，标签带目标显示名与两项成本",
  affordableUpgrade !== null &&
    affordableUpgrade.target === "building.armory.t2" &&
    affordableUpgrade.targetName === "兵营·强化" &&
    affordableUpgrade.gold === 140 &&
    affordableUpgrade.wood === 40 &&
    affordableUpgrade.affordable &&
    !affordableUpgrade.disabled &&
    affordableUpgrade.label ===
      `${UPGRADE_ACTION_LABEL} · 兵营·强化 · ${upgradeCostLabel(140, 40)}`,
  affordableUpgrade?.label ?? "<null>",
);
check(
  "恰好付得起（金 140、木 40）时升级条目可用",
  upgradeEntry(
    upgradeBuilding(41, 140, 40),
    1,
    { id: 1, name: "Player 1", gold: 140, wood: 40 },
    upgradeLabels,
  )?.affordable === true,
);
check(
  "缺金币或木材时升级条目仍出现但被禁用",
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
    )?.disabled === true,
);
check(
  "没有自己的玩家行（尚未 WHOAMI 或快照缺行）时升级条目被禁用",
  upgradeEntry(upgradeBuilding(44, 140, 40), 1, undefined, upgradeLabels)?.disabled === true,
);
check(
  "敌方建筑即使带 upgrade 字段也不出现升级条目",
  upgradeEntry(
    upgradeBuilding(45, 140, 40, 2),
    1,
    { id: 1, name: "Player 1", gold: 300, wood: 60 },
    upgradeLabels,
  ) === null,
);
const plainBuilding: EntityView = {
  id: 46,
  kind: "building.armory.t2",
  x: 46,
  y: 46,
  playerId: 1,
  progress: 1,
};
check(
  "没有 upgrade 字段的建筑（顶层或旧快照）不出现升级条目",
  upgradeEntry(plainBuilding, 1, { id: 1, name: "Player 1", gold: 999, wood: 999 }) ===
    null,
);
const upgradeUnit: EntityView = {
  id: 47,
  kind: "unit.melee.t1",
  x: 47,
  y: 47,
  playerId: 1,
  upgrade: { to: "building.armory.t2", gold: 140, wood: 40 },
};
check(
  "单位即使带 upgrade 字段也不出现升级条目（升级只属于建筑）",
  upgradeEntry(upgradeUnit, 1, { id: 1, name: "Player 1", gold: 999, wood: 999 }) ===
    null,
);
check(
  "UPGRADE 命令构建器生成 UPGRADE <id> 并拒绝非法 id",
  upgrade(12) === "UPGRADE 12" &&
    throwsChinese(() => upgrade(0)) &&
    throwsChinese(() => upgrade(1.5)) &&
    throwsChinese(() => upgrade(-3)),
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
      upgrade: { to: "oc:building.armory.t2", gold: 140, wood: 40 },
    },
    {
      id: 8,
      kind: "building.armory.t2",
      x: 1.5,
      y: 0.5,
      player: 1,
      upgrade: { to: "", gold: -1, wood: "none" },
    },
  ],
});
check(
  "快照解析 upgrade 对象并去掉命名空间；畸形对象整体忽略",
  upgradeSnapshot !== null &&
    upgradeSnapshot.entities[0]?.upgrade?.to === "building.armory.t2" &&
    upgradeSnapshot.entities[0]?.upgrade?.gold === 140 &&
    upgradeSnapshot.entities[0]?.upgrade?.wood === 40 &&
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
const twelveChips = selectionChipList(twelveUnits, 1, chipName);
check(
  "正好 12 个单位时显示 12 个芯片，没有 +N",
  twelveChips.chips.length === SELECTION_CHIP_LIMIT &&
    twelveChips.overflow === 0 &&
    twelveChips.overflowLabel === "",
  `${twelveChips.chips.length} / ${twelveChips.overflowLabel}`,
);

const fourteenChips = selectionChipList(
  [...twelveUnits, commandUnit(200, "unit.melee.t1"), commandUnit(201, "unit.melee.t1")],
  1,
  chipName,
);
check(
  "超过 12 个单位时截断为前 12 个芯片并追加 +N",
  fourteenChips.chips.length === SELECTION_CHIP_LIMIT &&
    fourteenChips.total === 14 &&
    fourteenChips.overflow === 2 &&
    fourteenChips.overflowLabel === "+2" &&
    fourteenChips.chips[0]?.entityId === 100 &&
    fourteenChips.chips[11]?.entityId === 111,
  `${fourteenChips.chips.length} / ${fourteenChips.overflowLabel}`,
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
check("生命条在 0% 时比例为 0", healthBarRatio(healthEntity(0)) === 0);
check("生命条在 50% 时比例为 0.5", healthBarRatio(healthEntity(0.5)) === 0.5);
check(
  "生命条在 100% 及未报告时比例为 1，越界值被夹紧",
  healthBarRatio(healthEntity(1)) === 1 &&
    healthBarRatio(healthEntity(undefined)) === 1 &&
    healthBarRatio(healthEntity(2)) === 1 &&
    healthBarRatio(healthEntity(-1)) === 0,
  `${healthBarRatio(healthEntity(1))} / ${healthBarRatio(healthEntity(undefined))}`,
);

// ---------------------------------------------------------------------------------------------
// Selection flash: a quarter-second achromatic halo for player-driven selection changes
// ---------------------------------------------------------------------------------------------

check(
  "选中闪烁时长是 250ms：够感觉到，又短到不会让选择拖影",
  FLASH_MS === 250,
  String(FLASH_MS),
);
check(
  "闪烁强度：开始时是 1，半程正好是 0.5",
  flashIntensity(1_000, 1_000, FLASH_MS) === 1 &&
    near(flashIntensity(1_000, 1_000 + FLASH_MS / 2, FLASH_MS), 0.5),
  `${flashIntensity(1_000, 1_000, FLASH_MS)} / ` +
    `${flashIntensity(1_000, 1_000 + FLASH_MS / 2, FLASH_MS)}`,
);
check(
  "闪烁强度：结束前仍然大于 0，到达和超过时长都是 0（非正时长也为 0）",
  flashIntensity(1_000, 1_000 + FLASH_MS - 1, FLASH_MS) > 0 &&
    flashIntensity(1_000, 1_000 + FLASH_MS, FLASH_MS) === 0 &&
    flashIntensity(1_000, 9_000, FLASH_MS) === 0 &&
    flashIntensity(1_000, 1_000, 0) === 0 &&
    flashIntensity(1_000, 1_000, -FLASH_MS) === 0,
);
check(
  "闪烁强度：负的流逝时间夹到 1（时钟回退也不会变暗）",
  flashIntensity(1_000, 900, FLASH_MS) === 1,
  String(flashIntensity(1_000, 900, FLASH_MS)),
);
const flashSamples = [-10_000, -1, 0, 1, FLASH_MS / 2, FLASH_MS - 1, FLASH_MS, 10_000];
check(
  "闪烁强度：所有采样都在 0..1 之间",
  flashSamples.every((elapsed) => {
    const value = flashIntensity(1_000, 1_000 + elapsed, FLASH_MS);
    return value >= 0 && value <= 1;
  }),
  flashSamples
    .map((elapsed) => flashIntensity(1_000, 1_000 + elapsed, FLASH_MS).toFixed(2))
    .join(","),
);

const flashMap = new Map<number, number>();
startFlash(flashMap, [1, 2], 1_000);
// 2 is passed again as newly selected (for example deselected and reselected quickly): it restarts,
// while 1 keeps the start it already had and no other entry is removed.
startFlash(flashMap, [2, 3], 1_200);
check(
  "startFlash：只写入传入的新选中 id，重启它们的时间，不动其它条目",
  flashMap.size === 3 &&
    flashMap.get(1) === 1_000 &&
    flashMap.get(2) === 1_200 &&
    flashMap.get(3) === 1_200,
  [...flashMap.entries()].map(([id, startedAt]) => `${id}@${startedAt}`).join(","),
);
const flashBeforeEmpty = new Map(flashMap);
startFlash(flashMap, [], 2_000);
check(
  "startFlash：空的新选中列表不改变已有条目（没有新增就不闪）",
  flashMap.size === flashBeforeEmpty.size &&
    [...flashBeforeEmpty].every(([id, startedAt]) => flashMap.get(id) === startedAt),
  [...flashMap.entries()].map(([id, startedAt]) => `${id}@${startedAt}`).join(","),
);

const pruningMap = new Map<number, number>([
  [1, 1_000], // exactly finished at now=1250
  [2, 1_100], // still active
  [3, 1_400], // future-dated by a clock step; keep until it has had its duration
]);
pruneFlashes(pruningMap, 1_250);
check(
  "pruneFlashes：删掉已经结束的条目，保留进行中与尚未到期的条目",
  pruningMap.size === 2 && !pruningMap.has(1) && pruningMap.has(2) && pruningMap.has(3),
  [...pruningMap.keys()].join(","),
);
check(
  "闪烁颜色是无彩色（纯白），与灰阶外壳一致且不与金色选择环抢色",
  isGreyscaleHex(FLASH_COLOR) && FLASH_COLOR === "#ffffff",
  FLASH_COLOR,
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

// BUG E: one or several own engineers offer all four BUILD entries, and the first engineer is the
// placement engineer. A mixed engineer + combat group must not silently pick one.
const engineerA: EntityView = { id: 41, kind: "unit.engineer", x: 5.5, y: 5.5, playerId: 3 };
const engineerB: EntityView = { id: 42, kind: "unit.engineer", x: 6.5, y: 5.5, playerId: 3 };
const engineerC: EntityView = { id: 43, kind: "unit.engineer", x: 7.5, y: 5.5, playerId: 3 };
const multiEngineerCommand = commandBarModel([engineerA, engineerB, engineerC], 3);
const engineerBuildActions = multiEngineerCommand.actions.filter((action) => action.kind === "build");
check(
  "选中多个工兵时仍显示全部四个 BUILD 条目（兵工厂/矿井/伐木场/仓库）",
  multiEngineerCommand.kind === "engineer" &&
    engineerBuildActions.length === BUILDING_PATHS.length &&
    engineerBuildActions.map((action) => action.path).join(",") === BUILDING_PATHS.join(",") &&
    engineerBuildActions.every((action) => action.zone === "context"),
  `${multiEngineerCommand.kind} / ${engineerBuildActions.map((action) => action.path).join(",")}`,
);
check(
  "多个工兵时用选中的第一个工兵放置，混合单位组不选工兵",
  engineerForPlacement([engineerA, engineerB, engineerC], 3)?.id === engineerA.id &&
    engineerForPlacement([engineerA, ownUnitFixture], 3) === undefined,
  String(engineerForPlacement([engineerA, engineerB], 3)?.id),
);
check(
  "工程师的 BUILD 目录与面板一致，四个路径都可以生成 BUILD 命令",
  BUILDING_PATHS.every((path) => build(1, path, 0, 0) === `BUILD 1 ${path} 0 0`),
  BUILDING_PATHS.join(","),
);

// BUG B: every classic building kind has its own content entry, its own train list and its own atlas
// cell. The old manifest pointed lumber camp t1/t2/t3 and the warehouse at the armory/mine cells, and the
// panel reused the armory's unit list for every building.
const classicBuildingSprites =
  classicAtlas.ok ? classicAtlas.data.entities : new Map<string, never>();
const buildingFrameSignatures = CLASSIC_BUILDING_KINDS.map((kind) => {
  const sprite = classicBuildingSprites.get(kind);
  return sprite === undefined
    ? ""
    : sprite.frames.map((frame) => `${frame.x},${frame.y},${frame.width},${frame.height}`).join(";");
});
check(
  "每个经典建筑 kind 都在图集里有自己的实体帧",
  buildingFrameSignatures.every((signature) => signature !== ""),
  buildingFrameSignatures.join(" | "),
);
check(
  "没有两个经典建筑共用同一个纹理格",
  new Set(buildingFrameSignatures).size === CLASSIC_BUILDING_KINDS.length,
  buildingFrameSignatures.join(" | "),
);
check(
  "每个经典建筑都有自己的内容条目，且 train 列表只属于正确 kind",
  CLASSIC_BUILDING_KINDS.every((kind) => {
    const entry = buildingContent(kind);
    const trainable = trainableUnitsForBuilding(kind);
    const producer = kind.startsWith("building.armory.");
    return (
      entry !== undefined &&
      entry.path === kind &&
      (producer ? trainable.join(",") === UNIT_PATHS.join(",") : trainable.length === 0)
    );
  }),
  CLASSIC_BUILDING_KINDS.map((kind) => `${kind}:${trainableUnitsForBuilding(kind).length}`).join(", "),
);
check(
  "伐木场、矿井和仓库不再显示 TRAIN 条目；兵工厂仍显示单位列表",
  trainableUnitsForBuilding("building.lumbercamp.t1").length === 0 &&
    trainableUnitsForBuilding("building.mine.t1").length === 0 &&
    trainableUnitsForBuilding("building.warehouse").length === 0 &&
    trainableUnitsForBuilding("building.armory.t1").join(",") === UNIT_PATHS.join(","),
);
check(
  "BUILDING_CONTENT 覆盖全部九个经典建筑",
  BUILDING_CONTENT.size === CLASSIC_BUILDING_KINDS.length &&
    CLASSIC_BUILDING_KINDS.every((kind) => BUILDING_CONTENT.has(kind)),
  String(BUILDING_CONTENT.size),
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
