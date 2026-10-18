/**
 * Procedural art used when no texture atlas is available.
 *
 * The fallback is not a debug placeholder: every terrain gets a deliberate colour and a small,
 * deterministic detail so an untextured map still reads as a coastline, a forest, a hill or a pond.
 * Entity shapes are equally simple but recognisable: a circle for a unit, a roofed square for a
 * building, both tinted by owner.
 *
 * The module is DOM-free apart from the Canvas 2D context it is handed, and its only import is a
 * type, which keeps it importable by the Node render math check.
 */

import type { Camera, TileRange } from "./camera";

/**
 * Base colours, keyed by the engine's terrain content path.
 *
 * The six terrains of the self-check scenario (`water`, `sand`, `grass`, `forest`, `hill`, `rock`)
 * come first; the rest are aliases and future kinds so an unknown scenario still renders sensibly.
 */
export const TERRAIN_COLORS: Readonly<Record<string, string>> = {
  water: "#2f5da8",
  deep_water: "#244a86",
  shallow_water: "#4b82c4",
  sand: "#d8c88a",
  desert: "#ddca8e",
  grass: "#5fa84f",
  grassland: "#67ad52",
  meadow: "#7bbb5e",
  forest: "#2e6b32",
  trees: "#356f38",
  hill: "#8a7a5c",
  hills: "#8a7a5c",
  mountain: "#7d6f58",
  rock: "#5a5a5a",
  rocky: "#666666",
  stone: "#707070",
  dirt: "#7a5f42",
  soil: "#6f5438",
  snow: "#e8eef2",
  ice: "#cfe6ef",
  road: "#9a8f7a",
  bridge: "#8c6a45",
  void: "#0b1016",
};

const PLAYER_COLORS: readonly string[] = [
  "#4f8fe8",
  "#e8695a",
  "#59c27a",
  "#b47ae8",
  "#e8a44a",
  "#5ec8d4",
];

const UNIT_HEAD_COLOR = "#f0d2b4";
const BUILDING_WALL_COLOR = "#a49b8c";
const BUILDING_DOOR_COLOR = "#4a3524";

/** The gold selection ring; kept unchanged so selection always reads the same. */
export const SELECTION_COLOR = "#ffd166";

/** Small deterministic hash for tile detail; not cryptographic, only stable. */
function hash01(x: number, y: number, salt: number): number {
  let value =
    (Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263) + Math.imul(salt | 0, 362437)) >>> 0;
  value = Math.imul(value ^ (value >>> 13), 1274126177) >>> 0;
  return ((value ^ (value >>> 16)) >>> 0) / 4294967296;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

/** String hash used to give an unknown terrain kind a stable, muted colour of its own. */
function stringHash(value: string): number {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619) >>> 0;
  }
  return hash >>> 0;
}

/** Colour of a terrain path; unknown kinds get a distinct auto-generated colour. */
export function terrainColor(terrain: string): string {
  const known = TERRAIN_COLORS[terrain];
  if (known !== undefined) {
    return known;
  }
  const hue = stringHash(terrain) % 360;
  const saturation = 24 + (stringHash(`${terrain}s`) % 18);
  const lightness = 30 + (stringHash(`${terrain}l`) % 14);
  return `hsl(${hue}, ${saturation}%, ${lightness}%)`;
}

/** Owner tint used by the fallback entity shapes. Player 1 is blue, player 2 red, and so on. */
export function playerColor(playerId?: number): string {
  if (playerId === undefined || !Number.isFinite(playerId) || playerId <= 0) {
    return "#c8ced6";
  }
  const index = ((Math.trunc(playerId) - 1) % PLAYER_COLORS.length + PLAYER_COLORS.length) %
    PLAYER_COLORS.length;
  return PLAYER_COLORS[index] ?? "#c8ced6";
}

function drawTileShade(
  context: CanvasRenderingContext2D,
  tileX: number,
  tileY: number,
  screenX: number,
  screenY: number,
  size: number,
): void {
  const shade = hash01(tileX, tileY, 5) * 0.055;
  context.fillStyle = `rgba(0, 0, 0, ${shade.toFixed(3)})`;
  context.fillRect(screenX, screenY, size + 0.5, size + 0.5);
}

function drawWaterDetail(
  context: CanvasRenderingContext2D,
  tileX: number,
  tileY: number,
  screenX: number,
  screenY: number,
  size: number,
): void {
  context.strokeStyle = "rgba(255, 255, 255, 0.24)";
  context.lineWidth = Math.max(1, size * 0.035);
  for (let streak = 0; streak < 2; streak += 1) {
    const start = hash01(tileX, tileY, 11 + streak);
    const length = 0.18 + hash01(tileX, tileY, 21 + streak) * 0.24;
    const y = screenY + size * (0.3 + streak * 0.34 + hash01(tileX, tileY, 31 + streak) * 0.1);
    const x = screenX + size * (0.14 + start * 0.5);
    context.beginPath();
    context.moveTo(x, y);
    context.lineTo(x + size * length, y);
    context.stroke();
  }
}

function drawGrassDetail(
  context: CanvasRenderingContext2D,
  tileX: number,
  tileY: number,
  screenX: number,
  screenY: number,
  size: number,
): void {
  context.strokeStyle = "rgba(20, 62, 24, 0.32)";
  context.lineWidth = Math.max(1, size * 0.03);
  for (let blade = 0; blade < 3; blade += 1) {
    const x = screenX + size * (0.2 + hash01(tileX, tileY, 41 + blade) * 0.6);
    const y = screenY + size * (0.58 + hash01(tileX, tileY, 51 + blade) * 0.26);
    const height = size * (0.09 + hash01(tileX, tileY, 61 + blade) * 0.08);
    context.beginPath();
    context.moveTo(x, y);
    context.lineTo(x + size * 0.04 - hash01(tileX, tileY, 71 + blade) * size * 0.08, y - height);
    context.stroke();
  }
}

function drawForestDetail(
  context: CanvasRenderingContext2D,
  tileX: number,
  tileY: number,
  screenX: number,
  screenY: number,
  size: number,
): void {
  const canopyRadius = size * 0.16;
  for (let canopy = 0; canopy < 3; canopy += 1) {
    const x = screenX + size * (0.24 + hash01(tileX, tileY, 81 + canopy) * 0.5);
    const y = screenY + size * (0.34 + hash01(tileX, tileY, 91 + canopy) * 0.38);
    context.fillStyle = canopy === 0 ? "rgba(15, 46, 20, 0.62)" : "rgba(31, 78, 36, 0.72)";
    context.beginPath();
    context.arc(x, y, canopyRadius, 0, Math.PI * 2);
    context.fill();
    context.fillStyle = "rgba(150, 200, 120, 0.13)";
    context.beginPath();
    context.arc(x - canopyRadius * 0.28, y - canopyRadius * 0.3, canopyRadius * 0.4, 0, Math.PI * 2);
    context.fill();
  }
}

function drawHillDetail(
  context: CanvasRenderingContext2D,
  tileX: number,
  tileY: number,
  screenX: number,
  screenY: number,
  size: number,
): void {
  context.strokeStyle = "rgba(60, 46, 26, 0.35)";
  context.lineWidth = Math.max(1, size * 0.032);
  const ridgeY = screenY + size * (0.44 + hash01(tileX, tileY, 101) * 0.16);
  context.beginPath();
  context.moveTo(screenX + size * 0.14, ridgeY + size * 0.16);
  context.quadraticCurveTo(
    screenX + size * 0.5,
    ridgeY - size * 0.2,
    screenX + size * 0.86,
    ridgeY + size * 0.16,
  );
  context.stroke();
  context.strokeStyle = "rgba(255, 255, 255, 0.1)";
  context.beginPath();
  context.moveTo(screenX + size * 0.22, ridgeY + size * 0.05);
  context.quadraticCurveTo(
    screenX + size * 0.5,
    ridgeY - size * 0.27,
    screenX + size * 0.78,
    ridgeY + size * 0.05,
  );
  context.stroke();
}

function drawRockDetail(
  context: CanvasRenderingContext2D,
  tileX: number,
  tileY: number,
  screenX: number,
  screenY: number,
  size: number,
): void {
  const x = screenX + size * (0.22 + hash01(tileX, tileY, 111) * 0.3);
  const y = screenY + size * (0.3 + hash01(tileX, tileY, 121) * 0.3);
  const width = size * (0.28 + hash01(tileX, tileY, 131) * 0.16);
  context.fillStyle = "rgba(30, 30, 30, 0.3)";
  context.beginPath();
  context.moveTo(x, y + width * 0.4);
  context.lineTo(x + width * 0.35, y);
  context.lineTo(x + width, y + width * 0.28);
  context.lineTo(x + width * 0.6, y + width * 0.62);
  context.closePath();
  context.fill();
  context.strokeStyle = "rgba(235, 235, 235, 0.16)";
  context.lineWidth = Math.max(1, size * 0.03);
  context.beginPath();
  context.moveTo(x + width * 0.35, y);
  context.lineTo(x + width * 0.6, y + width * 0.62);
  context.stroke();
}

function drawSpeckles(
  context: CanvasRenderingContext2D,
  tileX: number,
  tileY: number,
  screenX: number,
  screenY: number,
  size: number,
  color: string,
): void {
  context.fillStyle = color;
  for (let speck = 0; speck < 5; speck += 1) {
    const x = screenX + size * (0.16 + hash01(tileX, tileY, 141 + speck) * 0.68);
    const y = screenY + size * (0.16 + hash01(tileX, tileY, 151 + speck) * 0.68);
    const radius = size * (0.02 + hash01(tileX, tileY, 161 + speck) * 0.025);
    context.beginPath();
    context.arc(x, y, radius, 0, Math.PI * 2);
    context.fill();
  }
}

function drawRoadDetail(
  context: CanvasRenderingContext2D,
  screenX: number,
  screenY: number,
  size: number,
): void {
  context.strokeStyle = "rgba(235, 225, 195, 0.35)";
  context.lineWidth = Math.max(1, size * 0.05);
  context.setLineDash([size * 0.16, size * 0.14]);
  context.beginPath();
  context.moveTo(screenX, screenY + size * 0.5);
  context.lineTo(screenX + size, screenY + size * 0.5);
  context.stroke();
  context.setLineDash([]);
}

function drawBridgeDetail(
  context: CanvasRenderingContext2D,
  screenX: number,
  screenY: number,
  size: number,
): void {
  context.strokeStyle = "rgba(45, 30, 16, 0.4)";
  context.lineWidth = Math.max(1, size * 0.035);
  for (let plank = 1; plank < 4; plank += 1) {
    const y = screenY + (size * plank) / 4;
    context.beginPath();
    context.moveTo(screenX, y);
    context.lineTo(screenX + size, y);
    context.stroke();
  }
}

/**
 * Draws one fallback terrain tile.
 *
 * `screenX`/`screenY` are the tile's top-left corner in CSS pixels, `size` its on-screen edge.
 * Detail positions are hashed from the tile coordinate, so the map is stable across frames.
 */
export function drawTerrainTile(
  context: CanvasRenderingContext2D,
  terrain: string,
  tileX: number,
  tileY: number,
  screenX: number,
  screenY: number,
  size: number,
): void {
  context.fillStyle = terrainColor(terrain);
  context.fillRect(screenX, screenY, size + 0.5, size + 0.5);

  if (terrain === "void") {
    return;
  }
  // At 0.5× a 32 px tile covers 16 px; per-tile detail would cost more than it shows.
  if (size < 20) {
    return;
  }
  drawTileShade(context, tileX, tileY, screenX, screenY, size);

  switch (terrain) {
    case "water":
    case "deep_water":
    case "shallow_water":
      drawWaterDetail(context, tileX, tileY, screenX, screenY, size);
      break;
    case "grass":
    case "grassland":
    case "meadow":
      drawGrassDetail(context, tileX, tileY, screenX, screenY, size);
      break;
    case "forest":
    case "trees":
      drawForestDetail(context, tileX, tileY, screenX, screenY, size);
      break;
    case "hill":
    case "hills":
    case "mountain":
      drawHillDetail(context, tileX, tileY, screenX, screenY, size);
      break;
    case "rock":
    case "rocky":
    case "stone":
      drawRockDetail(context, tileX, tileY, screenX, screenY, size);
      break;
    case "sand":
    case "desert":
      drawSpeckles(context, tileX, tileY, screenX, screenY, size, "rgba(120, 96, 50, 0.2)");
      break;
    case "dirt":
    case "soil":
      drawSpeckles(context, tileX, tileY, screenX, screenY, size, "rgba(35, 25, 12, 0.22)");
      break;
    case "snow":
    case "ice":
      drawSpeckles(context, tileX, tileY, screenX, screenY, size, "rgba(255, 255, 255, 0.65)");
      break;
    case "road":
      drawRoadDetail(context, screenX, screenY, size);
      break;
    case "bridge":
      drawBridgeDetail(context, screenX, screenY, size);
      break;
    default:
      drawSpeckles(context, tileX, tileY, screenX, screenY, size, "rgba(255, 255, 255, 0.08)");
      break;
  }
}

/**
 * Draws the subtle one-device-pixel grid, but only when tiles are large enough to read.
 *
 * The renderer scales the context by `devicePixelRatio`, so the line width is divided by it to stay
 * one physical pixel instead of growing with the density.
 */
export function drawTileGrid(
  context: CanvasRenderingContext2D,
  camera: Camera,
  range: TileRange,
  devicePixelRatio: number,
): void {
  const tileOnScreen = camera.effectiveTileSize;
  if (tileOnScreen < 40) {
    return;
  }
  const pixelRatio = Math.max(1, devicePixelRatio);
  const tileSize = camera.tileSize;
  const left = camera.worldToScreen(range.minX * tileSize, 0).x;
  const right = camera.worldToScreen((range.maxX + 1) * tileSize, 0).x;
  const top = camera.worldToScreen(0, range.minY * tileSize).y;
  const bottom = camera.worldToScreen(0, (range.maxY + 1) * tileSize).y;

  context.save();
  context.strokeStyle = "rgba(8, 18, 14, 0.2)";
  context.lineWidth = 1 / pixelRatio;
  context.beginPath();
  for (let x = range.minX; x <= range.maxX + 1; x += 1) {
    const screenX = camera.worldToScreen(x * tileSize, 0).x;
    const snapped = Math.round(screenX * pixelRatio) / pixelRatio + 0.5 / pixelRatio;
    context.moveTo(snapped, top);
    context.lineTo(snapped, bottom);
  }
  for (let y = range.minY; y <= range.maxY + 1; y += 1) {
    const screenY = camera.worldToScreen(0, y * tileSize).y;
    const snapped = Math.round(screenY * pixelRatio) / pixelRatio + 0.5 / pixelRatio;
    context.moveTo(left, snapped);
    context.lineTo(right, snapped);
  }
  context.stroke();
  context.restore();
}

/** Soft radial ground shadow under an entity's feet. */
export function drawGroundShadow(
  context: CanvasRenderingContext2D,
  screenX: number,
  screenY: number,
  tilePixels: number,
  building: boolean,
): void {
  const radiusX = tilePixels * (building ? 0.72 : 0.34);
  const radiusY = radiusX * (building ? 0.4 : 0.42);
  if (radiusX < 1) {
    return;
  }
  context.save();
  context.translate(screenX, screenY + tilePixels * 0.04);
  context.scale(1, radiusY / radiusX);
  const gradient = context.createRadialGradient(0, 0, 0, 0, 0, radiusX);
  gradient.addColorStop(0, "rgba(0, 0, 0, 0.32)");
  gradient.addColorStop(0.65, "rgba(0, 0, 0, 0.16)");
  gradient.addColorStop(1, "rgba(0, 0, 0, 0)");
  context.fillStyle = gradient;
  context.beginPath();
  context.arc(0, 0, radiusX, 0, Math.PI * 2);
  context.fill();
  context.restore();
}

/** Fallback unit: a tinted circle body with a head and a rim highlight. */
export function drawUnitShape(
  context: CanvasRenderingContext2D,
  screenX: number,
  screenY: number,
  tilePixels: number,
  color: string,
): void {
  const radius = Math.max(3.5, tilePixels * 0.2);
  const bodyY = screenY - radius * 1.15;
  const outline = Math.max(1, tilePixels * 0.045);
  context.save();
  context.lineWidth = outline;
  context.strokeStyle = "rgba(8, 14, 18, 0.78)";
  context.fillStyle = color;
  context.beginPath();
  context.arc(screenX, bodyY, radius, 0, Math.PI * 2);
  context.fill();
  context.stroke();

  context.fillStyle = UNIT_HEAD_COLOR;
  context.beginPath();
  context.arc(screenX, bodyY - radius * 1.15, radius * 0.62, 0, Math.PI * 2);
  context.fill();
  context.stroke();

  context.strokeStyle = "rgba(255, 255, 255, 0.5)";
  context.lineWidth = Math.max(1, radius * 0.16);
  context.beginPath();
  context.arc(screenX, bodyY, radius * 0.72, Math.PI * 1.12, Math.PI * 1.6);
  context.stroke();
  context.restore();
}

/** Fallback building: stone walls, a tinted roof, a roof line, a door and two windows. */
export function drawBuildingShape(
  context: CanvasRenderingContext2D,
  screenX: number,
  screenY: number,
  tilePixels: number,
  color: string,
): void {
  const width = tilePixels * 1.5;
  const height = tilePixels * 0.95;
  const left = screenX - width / 2;
  const bottom = screenY;
  const wallTop = bottom - height;
  const roofPeak = wallTop - tilePixels * 0.34;
  const outline = Math.max(1, tilePixels * 0.04);

  context.save();
  context.lineWidth = outline;
  context.strokeStyle = "rgba(10, 14, 18, 0.8)";

  context.fillStyle = BUILDING_WALL_COLOR;
  context.fillRect(left, wallTop, width, height);
  context.strokeRect(left, wallTop, width, height);

  context.fillStyle = color;
  context.beginPath();
  context.moveTo(left - tilePixels * 0.06, wallTop + outline);
  context.lineTo(screenX, roofPeak);
  context.lineTo(left + width + tilePixels * 0.06, wallTop + outline);
  context.closePath();
  context.fill();
  context.stroke();

  context.strokeStyle = "rgba(255, 255, 255, 0.4)";
  context.lineWidth = Math.max(1, tilePixels * 0.028);
  context.beginPath();
  context.moveTo(left - tilePixels * 0.02, wallTop + outline);
  context.lineTo(left + width + tilePixels * 0.02, wallTop + outline);
  context.stroke();

  const doorWidth = width * 0.18;
  const doorHeight = height * 0.42;
  context.fillStyle = BUILDING_DOOR_COLOR;
  context.fillRect(screenX - doorWidth / 2, bottom - doorHeight, doorWidth, doorHeight);

  context.fillStyle = "rgba(240, 225, 170, 0.75)";
  const windowSize = width * 0.12;
  context.fillRect(left + width * 0.18, wallTop + height * 0.22, windowSize, windowSize);
  context.fillRect(left + width * 0.7, wallTop + height * 0.22, windowSize, windowSize);
  context.restore();
}

/**
 * Ownership ring drawn on the ground under an entity.
 *
 * It is the same ellipse as {@link drawSelectionRing} but thinner and without the halo, so the gold
 * selection ring stays readable on top of it. The colour comes from {@link ownershipColor}.
 */
export function drawOwnershipRing(
  context: CanvasRenderingContext2D,
  screenX: number,
  screenY: number,
  tilePixels: number,
  building: boolean,
  color: string,
): void {
  const radiusX = tilePixels * (building ? 0.85 : 0.42);
  const radiusY = radiusX * 0.42;
  context.save();
  context.strokeStyle = color;
  context.lineWidth = Math.max(1, tilePixels * 0.03);
  context.globalAlpha = 0.9;
  context.beginPath();
  context.ellipse(screenX, screenY, radiusX, radiusY, 0, 0, Math.PI * 2);
  context.stroke();
  context.restore();
}

/** Selection ring drawn on the ground under an entity. */
export function drawSelectionRing(
  context: CanvasRenderingContext2D,
  screenX: number,
  screenY: number,
  tilePixels: number,
  building: boolean,
): void {
  const radiusX = tilePixels * (building ? 0.85 : 0.42);
  const radiusY = radiusX * 0.42;
  context.save();
  context.strokeStyle = "rgba(255, 209, 102, 0.28)";
  context.lineWidth = Math.max(2, tilePixels * 0.09);
  context.beginPath();
  context.ellipse(screenX, screenY, radiusX * 1.12, radiusY * 1.12, 0, 0, Math.PI * 2);
  context.stroke();
  context.strokeStyle = SELECTION_COLOR;
  context.lineWidth = Math.max(1.5, tilePixels * 0.05);
  context.beginPath();
  context.ellipse(screenX, screenY, radiusX, radiusY, 0, 0, Math.PI * 2);
  context.stroke();
  context.restore();
}

/** Small health bar; `topY` is its top edge, `width` its full width. */
export function drawHealthBar(
  context: CanvasRenderingContext2D,
  screenX: number,
  topY: number,
  width: number,
  ratio: number,
  tilePixels: number,
): void {
  const safeRatio = clamp(ratio, 0, 1);
  const height = Math.max(3, tilePixels * 0.09);
  const left = screenX - width / 2;
  context.save();
  context.fillStyle = "rgba(4, 8, 12, 0.82)";
  context.fillRect(left - 1, topY - 1, width + 2, height + 2);
  context.fillStyle =
    safeRatio > 0.55 ? "#62d28a" : safeRatio > 0.28 ? "#e8c46b" : "#ff7b7b";
  context.fillRect(left, topY, width * safeRatio, height);
  context.strokeStyle = "rgba(255, 255, 255, 0.2)";
  context.lineWidth = 1;
  context.strokeRect(left, topY, width, height);
  context.restore();
}
