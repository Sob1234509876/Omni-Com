/**
 * Offline checks for the pure render math.
 *
 * Nothing here touches the DOM. Node 24 strips the TypeScript types and runs the file directly, so
 * the check exercises the same `camera.ts`, `view.ts`, `atlas.ts` and `fallback.ts` the browser
 * imports - no duplicated formulas. `scripts/tsconfig.check.json` is only used for type checking.
 *
 * Run: `node scripts/check-render-math.ts` (or `pnpm check:render`).
 */

import { Camera, MAX_ZOOM, MIN_ZOOM } from "../src/render/camera.ts";
import { parseAtlas } from "../src/render/atlas.ts";
import { TERRAIN_COLORS, terrainColor } from "../src/render/fallback.ts";
import {
  demoWorld,
  isBuildingKind,
  TERRAIN_IDS,
  terrainAt,
  type WorldView,
} from "../src/render/view.ts";

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

// ---------------------------------------------------------------------------------------------
// Camera
// ---------------------------------------------------------------------------------------------

const camera = new Camera(40, 30, 32, 1);
camera.setViewport(800, 600);
camera.reset();
check(
  "复位后镜头位于地图中心",
  near(camera.centerX, 640) && near(camera.centerY, 480),
  `${camera.centerX}, ${camera.centerY}`,
);

camera.pan(1_000_000, 1_000_000);
check(
  "向右下平移不会露出地图外",
  near(camera.centerX, 880) && near(camera.centerY, 660),
  `${camera.centerX}, ${camera.centerY}`,
);

camera.pan(-2_000_000, -2_000_000);
check(
  "向左上平移不会露出地图外",
  near(camera.centerX, 400) && near(camera.centerY, 300),
  `${camera.centerX}, ${camera.centerY}`,
);

camera.reset();
const visible = camera.visibleTileRange();
check(
  "可见瓦片范围被夹在 0..39 / 0..29",
  visible.minX >= 0 &&
    visible.minY >= 0 &&
    visible.maxX <= 39 &&
    visible.maxY <= 29 &&
    visible.minX <= visible.maxX &&
    visible.minY <= visible.maxY,
  `${visible.minX}..${visible.maxX} x ${visible.minY}..${visible.maxY}`,
);
check(
  "1× 时只画可见的一部分瓦片",
  visible.maxX - visible.minX + 1 < 40 && visible.maxY - visible.minY + 1 < 30,
  `${visible.maxX - visible.minX + 1} x ${visible.maxY - visible.minY + 1}`,
);

const roundTrip = camera.screenToWorld(camera.worldToScreen(512, 640).x, camera.worldToScreen(512, 640).y);
check(
  "世界坐标与屏幕坐标可逆",
  near(roundTrip.x, 512) && near(roundTrip.y, 640),
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
  "复位同时恢复默认缩放",
  camera.zoom === 1 && near(camera.centerX, 640) && near(camera.centerY, 480),
  `${camera.zoom}, ${camera.centerX}, ${camera.centerY}`,
);

const smallCamera = new Camera(5, 4, 32, 1);
smallCamera.setViewport(800, 600);
check(
  "地图小于视口时保持居中",
  near(smallCamera.centerX, 80) && near(smallCamera.centerY, 64),
  `${smallCamera.centerX}, ${smallCamera.centerY}`,
);
const smallRange = smallCamera.visibleTileRange();
check(
  "地图小于视口时可见范围是整张图",
  smallRange.minX === 0 && smallRange.maxX === 4 && smallRange.minY === 0 && smallRange.maxY === 3,
  `${smallRange.minX}..${smallRange.maxX} x ${smallRange.minY}..${smallRange.maxY}`,
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
