#!/usr/bin/env python3
"""Regenerate every material PNG of the classic game plugin in the round, soft
procedural art style of the web client's built-in demo world.

The style source of truth is ``web-client/src/render/fallback.ts``: geometry,
colours and detail placement mirror ``drawUnitShape``, ``drawBuildingShape``
and ``drawTerrainTile`` (plus the ``terrainColor`` palette). Everything is
drawn at 4x supersampling and downscaled with LANCZOS, which reproduces the
anti-aliased look of the Canvas 2D renderer.

``assets/manifest.json`` is the single source of truth: every file referenced
there is regenerated in place with its existing pixel dimensions; nothing else
in the assets tree is touched. The output is deterministic: the only
"randomness" is the ported ``hash01()`` tile hash from fallback.ts.
"""

from __future__ import annotations

import json
import math
import re
from pathlib import Path

from PIL import Image, ImageDraw

TOOLS = Path(__file__).resolve().parent
ASSETS = TOOLS.parent / "src" / "main" / "resources" / "assets"
SS = 4  # supersampling factor; every CSS-px coordinate is multiplied by this


# --------------------------------------------------------------------------
# Colours (ported from fallback.ts constants)
# --------------------------------------------------------------------------
def hx(value: str) -> tuple[int, int, int]:
    """``#rrggbb`` -> ``(r, g, b)``."""
    return tuple(int(value[i : i + 2], 16) for i in (1, 3, 5))  # type: ignore[return-value]


def rgba(r: int, g: int, b: int, a: float) -> tuple[int, int, int, int]:
    """CSS-style rgba() -> PIL RGBA tuple."""
    return (r, g, b, int(round(a * 255)))


def solid(value: str) -> tuple[int, int, int, int]:
    return hx(value) + (255,)


UNIT_HEAD_COLOR = solid("#f0d2b4")
UNIT_BODY_OUTLINE = rgba(8, 14, 18, 0.78)  # drawUnitShape stroke
HIGHLIGHT = rgba(255, 255, 255, 0.5)  # white top-left highlight arc
BUILDING_WALL_COLOR = solid("#a49b8c")
BUILDING_DOOR_COLOR = solid("#4a3524")
BUILDING_OUTLINE = rgba(10, 14, 18, 0.8)  # drawBuildingShape stroke
EAVE_LINE = rgba(255, 255, 255, 0.4)  # roof eave highlight line
WINDOW_COLOR = rgba(240, 225, 170, 0.75)  # cream windows
STONE_DARK = solid("#8a8175")  # slightly darker stone for chimneys/merlons

PLAYER1_BLUE = "#4f8fe8"  # standard troops, like the demo-world player 1
HELL_RED = "#e8695a"  # hostile hell-denizen family
HELMET_COLOR = solid("#e8a44a")  # engineer hard hat (palette orange)
WOOD = solid("#8c6a45")  # bow / wood tone (palette bridge colour)
BOWSTRING = rgba(235, 225, 195, 0.35)
HORN_COLOR = solid("#4a201a")
EAR_COLOR = solid("#8a3a30")
TURRET_COLOR = solid("#5a7ea0")  # gray-blue turret / watchtower tops
PORTAL_GLOW = (solid("#e25822"), solid("#ff9a3c"), solid("#ffd166"))

# terrainColor() palette of fallback.ts (the subset the tiles need)
TERRAIN_COLORS = {
    "water": "#2f5da8",
    "sand": "#d8c88a",
    "grass": "#5fa84f",
    "forest": "#2e6b32",
    "trees": "#356f38",
    "hill": "#8a7a5c",
    "mountain": "#7d6f58",
    "rock": "#5a5a5a",
    "rocky": "#666666",
    "stone": "#707070",
    "dirt": "#7a5f42",
    "soil": "#6f5438",
    "snow": "#e8eef2",
    "bridge": "#8c6a45",
    "hell_ground": "#5a1e1e",
    "hell_ash": "#6e5a66",
    "hell_lava": "#e25822",
    "hell_rock": "#2c2c34",
    "void": "#0b1016",
}


# --------------------------------------------------------------------------
# Port of the deterministic tile hash from fallback.ts (Math.imul semantics)
# --------------------------------------------------------------------------
_MASK = 0xFFFFFFFF


def _imul(a: int, b: int) -> int:
    return (a * b) & _MASK


def hash01(x: int, y: int, salt: int) -> float:
    value = (
        _imul(int(x) & _MASK, 374761393)
        + _imul(int(y) & _MASK, 668265263)
        + _imul(int(salt) & _MASK, 362437)
    ) & _MASK
    value = _imul((value ^ (value >> 13)) & _MASK, 1274126177)
    return ((value ^ (value >> 16)) & _MASK) / 4294967296.0


def h(salt: int) -> float:
    """Tile detail hash with the fixed material coordinate (0, 0)."""
    return hash01(0, 0, salt)


# --------------------------------------------------------------------------
# Tiny Canvas2D-like shim over PIL; coordinates are CSS px, scaled by SS.
#
# PIL's ImageDraw with translucent ink would *replace* pixel values, so every
# primitive is drawn onto its own empty layer and then alpha_composite()d onto
# the canvas. That gives exact Canvas2D "source-over" blending: translucent
# strokes darken what is already beneath them instead of erasing it.
# --------------------------------------------------------------------------
class Canvas:
    def __init__(self, width: float, height: float):
        self.w = float(width)
        self.h = float(height)
        self.size = (int(self.w * SS), int(self.h * SS))
        self.img = Image.new("RGBA", self.size, (0, 0, 0, 0))

    def _draw(self, paint) -> None:
        layer = Image.new("RGBA", self.size, (0, 0, 0, 0))
        paint(ImageDraw.Draw(layer))
        self.img = Image.alpha_composite(self.img, layer)

    def _lw(self, line_width: float) -> int:
        return max(1, int(round(line_width * SS)))

    def fill_rect(self, x, y, w, h, color):
        self._draw(lambda d: d.rectangle([x * SS, y * SS, (x + w) * SS, (y + h) * SS], fill=color))

    def stroke_rect(self, x, y, w, h, color, lw):
        self._draw(lambda d: d.rectangle(
            [x * SS, y * SS, (x + w) * SS, (y + h) * SS], outline=color, width=self._lw(lw)
        ))

    def fill_circle(self, cx, cy, r, color):
        self._draw(lambda d: d.ellipse(
            [(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], fill=color
        ))

    def circle_outline(self, cx, cy, r, color, lw):
        self._draw(lambda d: d.ellipse(
            [(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS],
            outline=color, width=self._lw(lw),
        ))

    def fill_ellipse(self, cx, cy, rx, ry, color):
        self._draw(lambda d: d.ellipse(
            [(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS], fill=color
        ))

    def ellipse_outline(self, cx, cy, rx, ry, color, lw):
        self._draw(lambda d: d.ellipse(
            [(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS],
            outline=color, width=self._lw(lw),
        ))

    def pie(self, cx, cy, r, a0, a1, color):
        self._draw(lambda d: d.pieslice(
            [(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS],
            start=a0, end=a1, fill=color,
        ))

    def arc(self, cx, cy, r, a0, a1, color, lw):
        """Angles in degrees, canvas convention (clockwise from 3 o'clock)."""
        self._draw(lambda d: d.arc(
            [(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS],
            start=a0, end=a1, fill=color, width=self._lw(lw),
        ))

    def line(self, x0, y0, x1, y1, color, lw):
        self._draw(lambda d: d.line(
            [x0 * SS, y0 * SS, x1 * SS, y1 * SS], fill=color, width=self._lw(lw)
        ))

    def polyline(self, points, color, lw, close=False):
        pts = [(x * SS, y * SS) for x, y in points]
        if close:
            pts = pts + [pts[0]]
        width = self._lw(lw)
        joint = "curve" if len(pts) > 2 else None
        self._draw(lambda d: d.line(pts, fill=color, width=width, joint=joint))

    def quad(self, p0, p1, p2, color, lw):
        """Quadratic bezier stroke, mirroring quadraticCurveTo."""
        steps = 24
        pts = []
        for i in range(steps + 1):
            t = i / steps
            u = 1 - t
            x = u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0]
            y = u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1]
            pts.append((x, y))
        self.polyline(pts, color, lw)

    def polygon(self, points, color):
        pts = [(x * SS, y * SS) for x, y in points]
        self._draw(lambda d: d.polygon(pts, fill=color))

    def finish(self, opaque=False):
        img = self.img.resize((int(self.w), int(self.h)), Image.Resampling.LANCZOS)
        return img.convert("RGB") if opaque else img


# --------------------------------------------------------------------------
# Terrain tiles: port of drawTerrainTile and its detail helpers (tile 0, 0)
# --------------------------------------------------------------------------
def draw_tile_shade(cv: Canvas, s: float) -> None:
    shade = h(5) * 0.055
    cv.fill_rect(0, 0, s + 0.5, s + 0.5, rgba(0, 0, 0, shade))


def water_detail(cv: Canvas, s: float) -> None:
    lw = max(1, s * 0.035)
    for streak in range(2):
        start = h(11 + streak)
        length = 0.18 + h(21 + streak) * 0.24
        y = s * (0.3 + streak * 0.34 + h(31 + streak) * 0.1)
        x = s * (0.14 + start * 0.5)
        cv.line(x, y, x + s * length, y, rgba(255, 255, 255, 0.24), lw)


def grass_detail(cv: Canvas, s: float) -> None:
    lw = max(1, s * 0.03)
    for blade in range(3):
        x = s * (0.2 + h(41 + blade) * 0.6)
        y = s * (0.58 + h(51 + blade) * 0.26)
        height = s * (0.09 + h(61 + blade) * 0.08)
        x2 = x + s * 0.04 - h(71 + blade) * s * 0.08
        cv.line(x, y, x2, y - height, rgba(20, 62, 24, 0.32), lw)


def forest_detail(cv: Canvas, s: float) -> None:
    canopy_r = s * 0.16
    for canopy in range(3):
        x = s * (0.24 + h(81 + canopy) * 0.5)
        y = s * (0.34 + h(91 + canopy) * 0.38)
        color = rgba(15, 46, 20, 0.62) if canopy == 0 else rgba(31, 78, 36, 0.72)
        cv.fill_circle(x, y, canopy_r, color)
        cv.fill_circle(x - canopy_r * 0.28, y - canopy_r * 0.3, canopy_r * 0.4, rgba(150, 200, 120, 0.13))


def hill_detail(cv: Canvas, s: float) -> None:
    lw = max(1, s * 0.032)
    ridge_y = s * (0.44 + h(101) * 0.16)
    cv.quad(
        (s * 0.14, ridge_y + s * 0.16),
        (s * 0.5, ridge_y - s * 0.2),
        (s * 0.86, ridge_y + s * 0.16),
        rgba(60, 46, 26, 0.35),
        lw,
    )
    cv.quad(
        (s * 0.22, ridge_y + s * 0.05),
        (s * 0.5, ridge_y - s * 0.27),
        (s * 0.78, ridge_y + s * 0.05),
        rgba(255, 255, 255, 0.1),
        lw,
    )


def rock_detail(cv: Canvas, s: float) -> None:
    x = s * (0.22 + h(111) * 0.3)
    y = s * (0.3 + h(121) * 0.3)
    width = s * (0.28 + h(131) * 0.16)
    cv.polygon(
        [
            (x, y + width * 0.4),
            (x + width * 0.35, y),
            (x + width, y + width * 0.28),
            (x + width * 0.6, y + width * 0.62),
        ],
        rgba(30, 30, 30, 0.3),
    )
    cv.line(
        x + width * 0.35,
        y,
        x + width * 0.6,
        y + width * 0.62,
        rgba(235, 235, 235, 0.16),
        max(1, s * 0.03),
    )


def speckles(cv: Canvas, s: float, color, base_salt: int = 141) -> None:
    for speck in range(5):
        x = s * (0.16 + h(base_salt + speck) * 0.68)
        y = s * (0.16 + h(base_salt + 10 + speck) * 0.68)
        r = s * (0.02 + h(base_salt + 20 + speck) * 0.025)
        cv.fill_circle(x, y, r, color)


def cobble_detail(cv: Canvas, s: float, mossy: bool = False) -> None:
    if mossy:
        for patch in range(3):
            x = s * (0.16 + h(301 + patch) * 0.64)
            y = s * (0.16 + h(311 + patch) * 0.64)
            r = s * (0.13 + h(321 + patch) * 0.09)
            cv.fill_circle(x, y, r, rgba(95, 168, 79, 0.4))
    for pebble in range(5):
        x = s * (0.16 + h(201 + pebble) * 0.68)
        y = s * (0.16 + h(211 + pebble) * 0.68)
        r = s * (0.1 + h(221 + pebble) * 0.05)
        tone = 196 + pebble * 10
        cv.fill_circle(x, y, r, (tone, tone, tone - 2, 150))
        cv.circle_outline(x, y, r, rgba(25, 25, 25, 0.45), max(1, s * 0.03))
        cv.arc(x, y, r * 0.62, 201.6, 288, rgba(255, 255, 255, 0.35), max(1, s * 0.03))


def gravel_detail(cv: Canvas, s: float) -> None:
    for pebble in range(7):
        x = s * (0.12 + h(241 + pebble) * 0.74)
        y = s * (0.12 + h(251 + pebble) * 0.74)
        r = s * (0.045 + h(261 + pebble) * 0.04)
        if pebble % 2 == 0:
            cv.fill_circle(x, y, r, rgba(45, 36, 24, 0.4))
        else:
            cv.fill_circle(x, y, r, rgba(235, 230, 220, 0.28))
            cv.circle_outline(x, y, r, rgba(40, 34, 24, 0.35), max(1, s * 0.03))


def planks_detail(cv: Canvas, s: float) -> None:
    lw = max(1, s * 0.035)
    for plank in range(1, 4):
        y = s * plank / 4
        cv.line(0, y, s, y, rgba(45, 30, 16, 0.4), lw)
    for grain in range(3):
        x = s * (0.1 + h(271 + grain) * 0.5)
        y = s * (0.08 + h(281 + grain) * 0.8)
        cv.line(x, y, x + s * 0.22, y, rgba(45, 30, 16, 0.28), lw)


def log_detail(cv: Canvas, s: float) -> None:
    cv.fill_rect(s * 0.3, 0, s * 0.16, s, rgba(235, 225, 195, 0.14))
    lw = max(1, s * 0.04)
    for streak in range(4):
        x = s * (0.12 + h(291 + streak) * 0.76)
        cv.line(x, s * 0.06, x + s * 0.03, s * 0.94, rgba(35, 25, 12, 0.4), lw)


def clay_detail(cv: Canvas, s: float) -> None:
    speckles(cv, s, rgba(60, 30, 20, 0.25))
    cv.line(0, s * 0.42, s, s * 0.42, rgba(255, 255, 255, 0.08), max(1, s * 0.03))
    cv.line(0, s * 0.68, s, s * 0.68, rgba(0, 0, 0, 0.11), max(1, s * 0.03))


def ore_detail(cv: Canvas, s: float, fill, rim, glint) -> None:
    rock_detail(cv, s)
    for blob in range(3):
        x = s * (0.2 + h(401 + blob) * 0.6)
        y = s * (0.24 + h(411 + blob) * 0.55)
        r = s * (0.1 + h(421 + blob) * 0.06)
        cv.fill_circle(x, y, r, fill)
        cv.circle_outline(x, y, r, rim, max(1, s * 0.03))
        if glint:
            cv.fill_circle(x - r * 0.3, y - r * 0.3, r * 0.22, glint)


def hell_cracks(cv: Canvas, s: float, color, base_salt: int) -> None:
    lw = max(1, s * 0.045)
    for crack in range(2):
        x0 = s * (0.15 + h(base_salt + crack) * 0.4)
        y0 = s * (0.15 + h(base_salt + 3 + crack) * 0.3)
        pts = [(x0, y0)]
        for seg in range(3):
            last = pts[-1]
            pts.append(
                (
                    last[0] + s * (0.08 + h(base_salt + 6 + crack * 4 + seg) * 0.16),
                    last[1] + s * (0.1 + h(base_salt + 12 + crack * 4 + seg) * 0.14),
                )
            )
        cv.polyline(pts, color, lw)


def lava_detail(cv: Canvas, s: float) -> None:
    for patch in range(2):
        x = s * (0.2 + h(501 + patch) * 0.6)
        y = s * (0.2 + h(511 + patch) * 0.6)
        cv.fill_circle(x, y, s * (0.14 + h(521 + patch) * 0.08), rgba(60, 20, 10, 0.4))
    lw = max(1, s * 0.05)
    cv.quad(
        (s * 0.08, s * 0.3),
        (s * 0.45, s * 0.14),
        (s * 0.92, s * 0.4),
        rgba(255, 190, 80, 0.65),
        lw,
    )
    cv.quad(
        (s * 0.06, s * 0.78),
        (s * 0.5, s * 0.62),
        (s * 0.9, s * 0.86),
        rgba(255, 190, 80, 0.65),
        lw,
    )
    for speck in range(4):
        x = s * (0.16 + h(531 + speck) * 0.68)
        y = s * (0.16 + h(541 + speck) * 0.68)
        cv.fill_circle(x, y, s * 0.03, rgba(255, 220, 120, 0.8))


TILE_STYLES = {
    "grass": (TERRAIN_COLORS["grass"], grass_detail),
    "dirt": (TERRAIN_COLORS["dirt"], lambda cv, s: speckles(cv, s, rgba(35, 25, 12, 0.22))),
    "stone": (TERRAIN_COLORS["stone"], rock_detail),
    "cobblestone": (TERRAIN_COLORS["rocky"], lambda cv, s: cobble_detail(cv, s)),
    "sand": (TERRAIN_COLORS["sand"], lambda cv, s: speckles(cv, s, rgba(120, 96, 50, 0.2))),
    "gravel": (TERRAIN_COLORS["mountain"], gravel_detail),
    "water": (TERRAIN_COLORS["water"], water_detail),
    "bedrock": ("#3d3f44", lambda cv, s: (rock_detail(cv, s), speckles(cv, s, rgba(255, 255, 255, 0.06)))),
    "planks": (TERRAIN_COLORS["bridge"], planks_detail),
    "log": ("#5c4630", log_detail),
    "leaves": (TERRAIN_COLORS["trees"], forest_detail),
    "snow": (TERRAIN_COLORS["snow"], lambda cv, s: speckles(cv, s, rgba(255, 255, 255, 0.65))),
    "clay": ("#a8624e", clay_detail),
    "coal_ore": (TERRAIN_COLORS["stone"], lambda cv, s: ore_detail(cv, s, rgba(22, 22, 26, 0.9), rgba(8, 14, 18, 0.5), rgba(200, 200, 210, 0.55))),
    "iron_ore": (TERRAIN_COLORS["stone"], lambda cv, s: ore_detail(cv, s, rgba(216, 176, 138, 0.92), rgba(120, 80, 40, 0.5), None)),
    "mossy_cobblestone": ("#5f6b52", lambda cv, s: cobble_detail(cv, s, mossy=True)),
    "forest": (TERRAIN_COLORS["forest"], forest_detail),
    "hill": (TERRAIN_COLORS["hill"], hill_detail),
    "rock": (TERRAIN_COLORS["rock"], rock_detail),
    "void": (TERRAIN_COLORS["void"], None),  # fallback returns before any detail
    "hell_ground": (TERRAIN_COLORS["hell_ground"], lambda cv, s: (speckles(cv, s, rgba(255, 255, 255, 0.08)), hell_cracks(cv, s, rgba(10, 4, 4, 0.5), 561))),
    "hell_ash": (TERRAIN_COLORS["hell_ash"], lambda cv, s: (speckles(cv, s, rgba(255, 255, 255, 0.1)), speckles(cv, s, rgba(20, 16, 20, 0.3), 171))),
    "hell_lava": (TERRAIN_COLORS["hell_lava"], lava_detail),
    "hell_rock": (TERRAIN_COLORS["hell_rock"], rock_detail),
}


def redraw_tile(key: str, path: Path, tile_pixels: int) -> None:
    base, detail = TILE_STYLES[key]
    cv = Canvas(tile_pixels, tile_pixels)
    cv.fill_rect(0, 0, tile_pixels + 0.5, tile_pixels + 0.5, solid(base))
    if detail is not None:  # "void" returns early in drawTerrainTile
        draw_tile_shade(cv, tile_pixels)
        detail(cv, float(tile_pixels))
    cv.finish(opaque=True).save(path)


# --------------------------------------------------------------------------
# Units: port of drawUnitShape (tilePixels 16 -> radius 3.5)
# --------------------------------------------------------------------------
def highlight_arc(cv: Canvas, cx: float, body_y: float, r: float) -> None:
    # canvas arc angles PI*1.12 .. PI*1.6 == 201.6 .. 288 degrees
    cv.arc(cx, body_y, r * 0.72, 201.6, 288, HIGHLIGHT, max(1, r * 0.16))


def draw_bow(cv: Canvas, cx: float, body_y: float, r: float) -> None:
    """Small bow arc ornament held to the right side of the body."""
    bow_x = cx + r * 0.95
    bow_r = r * 0.7
    cv.arc(bow_x, body_y, bow_r, -55, 55, WOOD, 1)
    ang = math.radians(55)
    tip_x = bow_x + bow_r * math.cos(ang)
    cv.line(tip_x, body_y - bow_r * math.sin(ang), tip_x, body_y + bow_r * math.sin(ang), BOWSTRING, 0.75)


def unit_figure(cv: Canvas, ox: float, oy: float, color: str, tier: int, bob: float,
                hat: bool = False, bow: bool = False) -> None:
    tp = 16.0
    r = 3.5 * (1.0 + 0.06 * (tier - 1))
    ground = oy + tp * 0.9 + bob
    cx = ox + tp * 0.5
    body_y = ground - r * 1.15
    if bow:
        draw_bow(cv, cx, body_y, r)
    cv.fill_circle(cx, body_y, r, solid(color))
    cv.circle_outline(cx, body_y, r, UNIT_BODY_OUTLINE, 1)
    head_r = r * 0.62
    head_y = body_y - r * 1.15
    cv.fill_circle(cx, head_y, head_r, UNIT_HEAD_COLOR)
    cv.circle_outline(cx, head_y, head_r, UNIT_BODY_OUTLINE, 1)
    if hat:  # engineer hard hat: palette-orange dome over the top of the head
        cv.pie(cx, head_y, head_r, 180, 360, HELMET_COLOR)
        cv.arc(cx, head_y, head_r, 180, 360, UNIT_BODY_OUTLINE, 1)
        cv.line(cx - head_r * 1.18, head_y - 0.1, cx + head_r * 1.18, head_y - 0.1, UNIT_BODY_OUTLINE, 0.8)
    highlight_arc(cv, cx, body_y, r)


def hell_hound_figure(cv: Canvas, ox: float, oy: float, bob: float) -> None:
    """Hell denizen: a low, long round body with a forward head and ears."""
    tp = 16.0
    r = 3.5
    ground = oy + tp * 0.9 + bob
    cx = ox + tp * 0.5
    cy = ground - r * 0.95
    rx, ry = r * 1.3, r * 0.8
    cv.arc(cx - rx * 0.95, cy - ry * 0.6, r * 0.5, 150, 260, solid(HELL_RED), 1)  # tail
    cv.fill_ellipse(cx, cy, rx, ry, solid(HELL_RED))
    cv.ellipse_outline(cx, cy, rx, ry, UNIT_BODY_OUTLINE, 1)
    head_x = cx + rx * 0.78
    head_y = cy - ry * 0.45
    head_r = r * 0.6
    for sign in (-1, 1):
        pts = [
            (head_x + sign * head_r * 0.15, head_y - head_r * 0.7),
            (head_x + sign * head_r * 0.7, head_y - head_r * 1.3),
            (head_x + sign * head_r * 0.85, head_y - head_r * 0.35),
        ]
        cv.polygon(pts, EAR_COLOR)
        cv.polyline(pts, UNIT_BODY_OUTLINE, 0.75, close=True)
    cv.fill_circle(head_x, head_y, head_r, solid(HELL_RED))
    cv.circle_outline(head_x, head_y, head_r, UNIT_BODY_OUTLINE, 1)
    # highlight hugs the elliptical back instead of using the wide rx
    cv.arc(cx, cy, ry * 0.66, 201.6, 288, HIGHLIGHT, 1)


def abyss_brute_figure(cv: Canvas, ox: float, oy: float, bob: float) -> None:
    """Hell denizen: oversized round body with dark horns."""
    tp = 16.0
    r = 3.5 * 1.18
    ground = oy + tp * 0.9 + bob
    cx = ox + tp * 0.5
    body_y = ground - r * 1.15
    head_r = r * 0.62
    head_y = body_y - r * 1.15
    for sign in (-1, 1):
        pts = [
            (cx + sign * head_r * 0.35, head_y - head_r * 0.55),
            (cx + sign * head_r * 1.15, head_y - head_r * 1.5),
            (cx + sign * head_r * 0.9, head_y - head_r * 0.1),
        ]
        cv.polygon(pts, HORN_COLOR)
        cv.polyline(pts, UNIT_BODY_OUTLINE, 0.75, close=True)
    cv.fill_circle(cx, body_y, r, solid(HELL_RED))
    cv.circle_outline(cx, body_y, r, UNIT_BODY_OUTLINE, 1)
    cv.fill_circle(cx, head_y, head_r, UNIT_HEAD_COLOR)
    cv.circle_outline(cx, head_y, head_r, UNIT_BODY_OUTLINE, 1)
    highlight_arc(cv, cx, body_y, r)


UNIT_FAMILIES = {
    "melee": {"color": PLAYER1_BLUE},
    "engineer": {"color": PLAYER1_BLUE, "hat": True},
    "ranged": {"color": PLAYER1_BLUE, "bow": True},
    "hell": {"color": HELL_RED},
}


def redraw_unit(key: str, path: Path, frames: int, tile_pixels: int) -> None:
    name = key[len("unit.") :]
    cv = Canvas(tile_pixels, tile_pixels * frames)
    frame_h = tile_pixels
    for frame in range(frames):
        ox = 0.0
        oy = frame * frame_h
        bob = 1.0 if frame % 2 == 1 else 0.0  # subtle ~1px walk bob
        if name == "hell_hound":
            hell_hound_figure(cv, ox, oy, bob)
        elif name == "abyss_brute":
            abyss_brute_figure(cv, ox, oy, bob)
        else:
            match = re.fullmatch(r"([a-z]+)(?:\.t(\d+))?", name)
            if match is None:
                raise SystemExit(f"unrecognised unit key: {key}")
            family, tier_text = match.group(1), match.group(2)
            if family not in UNIT_FAMILIES:
                raise SystemExit(f"unrecognised unit family in key: {key}")
            opts = UNIT_FAMILIES[family]
            unit_figure(
                cv, ox, oy, opts["color"], int(tier_text) if tier_text else 1, bob,
                hat=opts.get("hat", False), bow=opts.get("bow", False),
            )
    cv.finish().save(path)


# --------------------------------------------------------------------------
# Buildings: port of drawBuildingShape on a 32x32 canvas (tilePixels 16)
# --------------------------------------------------------------------------
def building_frame(cv: Canvas, roof: str, tier: int = 1, width_factor: float = 1.0,
                   windows: bool = True, chimney: bool = False) -> None:
    tp = 16.0
    cx = tp  # 32 px canvas, horizontal centre
    ground = tp * 1.8  # anchor y = 0.9 * 32
    s = 1.0 + 0.06 * (tier - 1)
    width = tp * 1.5 * s * width_factor
    height = tp * 0.95 * s
    left = cx - width / 2
    bottom = ground
    wall_top = bottom - height
    roof_peak = wall_top - tp * 0.34 * s
    outline = 1.0

    cv.fill_rect(left, wall_top, width, height, BUILDING_WALL_COLOR)
    cv.stroke_rect(left, wall_top, width, height, BUILDING_OUTLINE, outline)

    eave = tp * 0.06 * s
    roof_pts = [(left - eave, wall_top + outline), (cx, roof_peak), (left + width + eave, wall_top + outline)]
    cv.polygon(roof_pts, solid(roof))
    cv.polyline(roof_pts, BUILDING_OUTLINE, outline, close=True)

    cv.line(
        left - tp * 0.02, wall_top + outline,
        left + width + tp * 0.02, wall_top + outline,
        EAVE_LINE, max(1, tp * 0.028),
    )

    door_w = width * 0.18
    door_h = height * 0.42
    cv.fill_rect(cx - door_w / 2, bottom - door_h, door_w, door_h, BUILDING_DOOR_COLOR)

    if windows:
        win = width * 0.12
        wy = wall_top + height * 0.22
        cv.fill_rect(left + width * 0.18, wy, win, win, WINDOW_COLOR)
        cv.fill_rect(left + width * 0.7, wy, win, win, WINDOW_COLOR)

    if chimney:
        ch_x = left + width * 0.72
        cv.fill_rect(ch_x, roof_peak + 1.4, 2.6, 4.4, STONE_DARK)
        cv.stroke_rect(ch_x, roof_peak + 1.4, 2.6, 4.4, BUILDING_OUTLINE, 0.8)


def turret_frame(cv: Canvas, tier: int) -> None:
    """Watchtower: stone base, gray-blue round turret tower on top."""
    tp = 16.0
    cx = tp
    ground = tp * 1.8
    s = 1.0 + 0.06 * (tier - 1)
    width = tp * 1.5 * 0.62 * s
    height = tp * 0.95 * s
    left = cx - width / 2
    wall_top = ground - height

    cv.fill_rect(left, wall_top, width, height, BUILDING_WALL_COLOR)
    cv.stroke_rect(left, wall_top, width, height, BUILDING_OUTLINE, 1)
    door_w = width * 0.24
    door_h = height * 0.34
    cv.fill_rect(cx - door_w / 2, ground - door_h, door_w, door_h, BUILDING_DOOR_COLOR)

    shaft_w = width * 0.52
    shaft_top = wall_top - tp * 0.22 * s
    cv.fill_rect(cx - shaft_w / 2, shaft_top, shaft_w, wall_top - shaft_top + 1, BUILDING_WALL_COLOR)
    cv.stroke_rect(cx - shaft_w / 2, shaft_top, shaft_w, wall_top - shaft_top + 1, BUILDING_OUTLINE, 1)

    drum_r = tp * 0.3 * s
    drum_cy = shaft_top
    cv.fill_circle(cx, drum_cy, drum_r, TURRET_COLOR)
    cv.circle_outline(cx, drum_cy, drum_r, BUILDING_OUTLINE, 1)
    cv.arc(cx, drum_cy, drum_r * 0.72, 201.6, 288, HIGHLIGHT, 1)
    cv.fill_circle(cx, drum_cy + drum_r * 0.15, drum_r * 0.24, WINDOW_COLOR)
    if tier >= 2:  # gun barrel appears from the reinforced tiers on
        cv.line(cx + drum_r * 0.8, drum_cy, cx + drum_r * 0.8 + 3.4 * s, drum_cy, BUILDING_DOOR_COLOR, 1.4)
    if tier >= 4:  # war-king turret: hostile red band
        cv.circle_outline(cx, drum_cy, drum_r * 0.55, solid(HELL_RED), 1.2)


def hell_gate_frame(cv: Canvas) -> None:
    """Stone gate frame with a glowing orange portal opening instead of a door."""
    tp = 16.0
    cx = tp
    ground = tp * 1.8
    width = tp * 1.5
    height = tp * 0.95
    left = cx - width / 2
    wall_top = ground - height

    cv.fill_rect(left, wall_top, width, height, BUILDING_WALL_COLOR)
    cv.stroke_rect(left, wall_top, width, height, BUILDING_OUTLINE, 1)
    for merlon_x in (left + 1.0, cx - 1.6, left + width - 4.2):
        cv.fill_rect(merlon_x, wall_top - 2.2, 3.2, 2.2, BUILDING_WALL_COLOR)
        cv.stroke_rect(merlon_x, wall_top - 2.2, 3.2, 2.2, BUILDING_OUTLINE, 0.8)

    portal_r = 4.8
    portal_top = wall_top + 1.2
    center_y = portal_top + portal_r
    bottom_y = ground - 0.8

    def arch(r: float, pad: float, color) -> None:
        top = wall_top + pad
        cy = top + r
        cv.fill_rect(cx - r, cy, 2 * r, bottom_y - cy, color)
        cv.pie(cx, cy, r, 180, 360, color)

    arch(portal_r, 1.2, solid("#2a1214"))
    arch(portal_r - 0.9, 2.0, PORTAL_GLOW[0])
    arch(portal_r - 2.0, 2.9, PORTAL_GLOW[1])
    arch(portal_r - 3.1, 3.8, PORTAL_GLOW[2])
    cv.fill_circle(cx - 1.2, center_y + 2.2, 0.5, solid("#fff2c0"))
    cv.fill_circle(cx + 1.6, center_y + 4.0, 0.4, solid("#fff2c0"))
    # dark frame outline around the arch
    cv.arc(cx, center_y, portal_r, 180, 360, BUILDING_OUTLINE, 1)
    cv.line(cx - portal_r, center_y, cx - portal_r, bottom_y, BUILDING_OUTLINE, 1)
    cv.line(cx + portal_r, center_y, cx + portal_r, bottom_y, BUILDING_OUTLINE, 1)
    cv.line(cx - portal_r, bottom_y, cx + portal_r, bottom_y, BUILDING_OUTLINE, 1)


BUILDING_ROOFS = {
    "armory": "#e8695a",  # military red
    "mine": "#e8a44a",  # ore orange
    "lumbercamp": "#59c27a",  # timber green
    "warehouse": "#4f8fe8",  # storage blue
    "purgatory": "#b47ae8",  # infernal factory purple
}
BUILDING_WIDTH_FACTORS = {
    "armory": 1.0,
    "mine": 0.95,
    "lumbercamp": 1.0,
    "warehouse": 1.12,
    "purgatory": 1.0,
}
BUILDING_CHIMNEYS = {"purgatory"}


def redraw_building(key: str, path: Path, tile_pixels: int) -> None:
    name = key[len("building.") :]
    match = re.fullmatch(r"([a-z_]+)(?:\.t(\d+))?", name)
    if match is None:
        raise SystemExit(f"unrecognised building key: {key}")
    kind, tier_text = match.group(1), match.group(2)
    tier = int(tier_text) if tier_text else 1
    cv = Canvas(tile_pixels * 2, tile_pixels * 2)
    if kind == "turret":
        turret_frame(cv, tier)
    elif kind == "hell_gate":
        hell_gate_frame(cv)
    elif kind in BUILDING_ROOFS:
        building_frame(
            cv,
            BUILDING_ROOFS[kind],
            tier=tier,
            width_factor=BUILDING_WIDTH_FACTORS[kind],
            chimney=kind in BUILDING_CHIMNEYS,
        )
    else:
        raise SystemExit(f"unrecognised building kind in key: {key}")
    cv.finish().save(path)


# --------------------------------------------------------------------------
# Driver: the manifest is the single source of truth
# --------------------------------------------------------------------------
def main() -> None:
    manifest = json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))
    tile_pixels = int(manifest["tilePixels"])

    for key, spec in manifest["tiles"].items():
        path = ASSETS / spec["image"]
        redraw_tile(key, path, tile_pixels)
        print(f"tile     {key:22s} -> {path.name}")

    for key, spec in manifest["entities"].items():
        path = ASSETS / spec["image"]
        frames = int(spec["frames"])
        if key.startswith("unit."):
            redraw_unit(key, path, frames, tile_pixels)
        elif key.startswith("building."):
            redraw_building(key, path, tile_pixels)
        else:
            raise SystemExit(f"unrecognised entity key: {key}")
        print(f"entity   {key:22s} -> {path.name} ({frames} frame(s))")

    print(f"done: {len(manifest['tiles'])} tiles + {len(manifest['entities'])} entities regenerated.")


if __name__ == "__main__":
    main()
