/**
 * Offline checks for the pure render math.
 *
 * Nothing here touches the DOM. Node 24 strips the TypeScript types and runs the file directly, so
 * the check exercises the same `camera.ts`, `view.ts`, `status.ts`, `marquee.ts`, `toast.ts`, `atlas.ts`,
 * `fallback.ts` and `interpolate.ts` the browser imports - no duplicated formulas. `scripts/tsconfig.check.json`
 * is only used for type checking.
 *
 * Run: `node scripts/check-render-math.ts` (or `pnpm check:render`).
 */

import { readFileSync } from "node:fs";
import {
  BUILDING_PATHS,
  UNIT_PATHS,
  build,
  orderAttack,
  orderAttackMove,
  orderMove,
  orderStop,
  parseWhoAmI,
  train,
  whoAmI,
} from "../src/commands.ts";
import { HEARTBEAT_MS, shouldHeartbeat } from "../src/heartbeat.ts";
import { CAMERA_SAFETY_BOUND_TILES, Camera, MAX_ZOOM, MIN_ZOOM } from "../src/render/camera.ts";
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
  formatProgressPercent,
  progressBarRatio,
  queueMarkerText,
  sizeLabel,
} from "../src/render/status.ts";
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
  entityInMarquee,
  entityInWindow,
  isBuildingKind,
  isWorldTile,
  marqueeSelection,
  OWNERSHIP_ENEMY_COLOR,
  OWNERSHIP_MINE_COLOR,
  OWNERSHIP_NEUTRAL_COLOR,
  ownershipColor,
  ownershipOf,
  ownershipTag,
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
  "框选只选自己的单位：敌人、中立、自己的建筑都被排除",
  JSON.stringify(marqueeIds) === JSON.stringify([1, 2]),
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
    legacyEntity.queueTicks === undefined,
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
  "尺寸标签输出 尺寸：W×H，缺 footprint 时为 null",
  detailedWarehouse !== undefined &&
    sizeLabel(detailedWarehouse) === "尺寸：3×3" &&
    detailedWorker !== undefined &&
    sizeLabel(detailedWorker) === "尺寸：1×1" &&
    sizeLabel({ id: 9, kind: "warehouse", x: 0, y: 0 }) === null,
  detailedWarehouse === undefined ? "missing" : String(sizeLabel(detailedWarehouse)),
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
    { id: 3, name: "alice", gold: 300, wood: 60 },
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
const classicAtlas = parseAtlas(classicManifest, 96, 96);
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
    JSON.stringify(["building.armory.t1", "building.mine.t1", "building.lumbercamp.t1"]),
  BUILDING_PATHS.join(", "),
);
check(
  "建筑目录包含新的木材营地 building.lumbercamp.t1",
  BUILDING_PATHS.includes("building.lumbercamp.t1") &&
    build(1, "building.lumbercamp.t1", 5, 6) === "BUILD 1 building.lumbercamp.t1 5 6",
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
