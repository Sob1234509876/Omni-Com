/**
 * Offline checks for the pure render math.
 *
 * Nothing here touches the DOM. Node 24 strips the TypeScript types and runs the file directly, so
 * the check exercises the same `camera.ts`, `view.ts`, `atlas.ts` and `fallback.ts` the browser
 * imports - no duplicated formulas. `scripts/tsconfig.check.json` is only used for type checking.
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
import { Camera, MAX_ZOOM, MIN_ZOOM } from "../src/render/camera.ts";
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
import { TERRAIN_COLORS, terrainColor } from "../src/render/fallback.ts";
import {
  demoWorld,
  isBuildingKind,
  snapshotOrDemo,
  TERRAIN_IDS,
  terrainAt,
  worldViewFromSnapshot,
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

/** A JSON object as a string-keyed table, or null for every other JSON value. */
function recordTable(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
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
check(
  "实体越出右边界时拒绝",
  worldViewFromSnapshot({
    ...validSnapshot,
    entities: [{ id: 1, kind: "worker", x: 2, y: 1 }],
  }) === null,
);
check(
  "实体越出下边界时拒绝",
  worldViewFromSnapshot({
    ...validSnapshot,
    entities: [{ id: 1, kind: "worker", x: 1, y: 2 }],
  }) === null,
);
check(
  "实体坐标为负时拒绝",
  worldViewFromSnapshot({
    ...validSnapshot,
    entities: [{ id: 1, kind: "worker", x: -0.1, y: 1 }],
  }) === null,
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
  JSON.stringify(BUILDING_PATHS) === JSON.stringify(["building.armory.t1", "building.mine.t1"]),
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
