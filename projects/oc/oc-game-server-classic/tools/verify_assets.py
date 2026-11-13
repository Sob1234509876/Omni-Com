#!/usr/bin/env python3
"""Verify the classic game plugin's material SVGs against assets/manifest.json.

Usage:
    python verify_assets.py verify

Every manifest-referenced file must be a well-formed SVG whose root carries
width/height attributes equal to its viewBox (the explicit intrinsic size the
browser needs to decode the file into an image element):
    * terrain tiles  : 16 x 16, an opaque full-cover base rect;
    * unit strips    : 16 wide, height = frames x 16, each frame a clipped
      16 px band and the frames not identical;
    * building sheets: 32 x 32.
No pixel hashing: SVG is vector text, so the checks validate structure and
geometry instead of a snapshot. (The old PIL snapshot flow is gone together
with the PNG assets; tools/before_hashes.json was deleted.)
"""

from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
ASSETS = TOOLS.parent / "src" / "main" / "resources" / "assets"
SVG_NS = "{http://www.w3.org/2000/svg}"

SHAPE_TAGS = {"rect", "circle", "ellipse", "path", "line", "polygon", "polyline"}


def load_manifest() -> dict:
    return json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))


def manifest_images(manifest: dict) -> list[str]:
    images = [spec["image"] for spec in manifest["tiles"].values()]
    images += [spec["image"] for spec in manifest["entities"].values()]
    return sorted(images)


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1] if "}" in tag else tag


def attr_of(element: ET.Element, name: str) -> str | None:
    """Reads an attribute regardless of namespace prefix."""
    value = element.get(name)
    if value is not None:
        return value
    for key, candidate in element.attrib.items():
        if local_name(key) == name:
            return candidate
    return None


def parse_svg(path: Path) -> tuple[ET.Element, float, float, tuple[float, float, float, float] | None] | None:
    """Parses one SVG file; returns (root, width, height, viewBox) or None."""
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError):
        return None
    if local_name(root.tag) != "svg":
        return None
    try:
        width = float(attr_of(root, "width") or "")
        height = float(attr_of(root, "height") or "")
    except ValueError:
        return None
    if width <= 0 or height <= 0:
        return None
    view_box: tuple[float, float, float, float] | None = None
    raw_view_box = attr_of(root, "viewBox")
    if raw_view_box:
        parts = raw_view_box.replace(",", " ").split()
        if len(parts) == 4:
            try:
                view_box = tuple(float(part) for part in parts)  # type: ignore[assignment]
            except ValueError:
                view_box = None
    return root, width, height, view_box


def iter_shapes(element: ET.Element):
    for child in element.iter():
        name = local_name(child.tag)
        if name in SHAPE_TAGS:
            yield name, child


def verify() -> None:
    manifest = load_manifest()
    tile_pixels = int(manifest["tilePixels"])

    errors: list[str] = []
    checked = 0

    referenced = set(manifest_images(manifest))

    # 1. Every manifest-referenced file exists, is an .svg, and there are no
    #    unexpected extra files (the old PNGs must be gone too).
    for sub in ("tiles", "entities"):
        actual = {f"{sub}/{p.name}" for p in (ASSETS / sub).iterdir() if p.is_file()}
        for extra in sorted(actual - referenced):
            errors.append(f"unexpected extra file in assets/{sub}: {extra}")
        stale = sorted(name for name in actual - referenced if name.endswith(".png"))
        for name in stale:
            errors.append(f"stale PNG left in assets/{sub}: {name}")
    for rel in sorted(referenced):
        if not rel.endswith(".svg"):
            errors.append(f"manifest image is not an .svg: {rel}")
        if not (ASSETS / rel).exists():
            errors.append(f"missing manifest file: {rel}")

    # 2/3. Root element: width/height == viewBox, and the dims the manifest needs.
    for kind_group, entries in (("tiles", manifest["tiles"]), ("entities", manifest["entities"])):
        for key, spec in entries.items():
            rel = spec["image"]
            path = ASSETS / rel
            if not path.exists():
                continue
            checked += 1
            parsed = parse_svg(path)
            if parsed is None:
                errors.append(f"{rel}: not a well-formed SVG with numeric width/height")
                continue
            root, width, height, view_box = parsed
            if view_box is None or view_box != (0, 0, width, height):
                errors.append(
                    f"{rel}: viewBox does not match width/height {width}x{height}"
                )
                continue
            shapes = list(iter_shapes(root))
            if not shapes:
                errors.append(f"{rel}: SVG has no drawing content")
                continue
            frames = int(spec.get("frames", 1))
            if kind_group == "tiles":
                want = (float(tile_pixels), float(tile_pixels))
            elif key.startswith("building."):
                want = (2.0 * tile_pixels, 2.0 * tile_pixels)  # square building sheet
            else:
                want = (float(tile_pixels), float(tile_pixels) * frames)  # unit strip
            if (width, height) != want:
                errors.append(
                    f"{rel}: dimensions {fmt_dim(width, height)}, expected {fmt_dim(want[0], want[1])}"
                )
            if kind_group == "entities" and frames >= 2:
                # each frame must be its own clipped band; frames must differ
                groups = [
                    child
                    for child in root
                    if local_name(child.tag) == "g" and attr_of(child, "clip-path")
                ]
                if len(groups) != frames:
                    errors.append(f"{rel}: expected {frames} clipped frame groups, found {len(groups)}")
                else:
                    texts = [ET.tostring(group, encoding="unicode") for group in groups]
                    if len(set(texts)) != frames:
                        errors.append(f"{rel}: animation frames are identical")

    # 4. Content sanity.
    for key, spec in manifest["tiles"].items():
        rel = spec["image"]
        path = ASSETS / rel
        if not path.exists():
            continue
        parsed = parse_svg(path)
        if parsed is None:
            continue
        root, width, height, _view_box = parsed
        shapes = list(iter_shapes(root))
        if not shapes:
            continue
        # terrain tiles must be fully opaque: the first shape is the base and
        # must cover the whole tile with a solid, untransparent fill.
        name, base = shapes[0]
        fill = attr_of(base, "fill")
        fill_opacity = attr_of(base, "fill-opacity") or "1"
        covers = False
        try:
            covers = (
                name == "rect"
                and float(attr_of(base, "x") or "0") <= 0
                and float(attr_of(base, "y") or "0") <= 0
                and float(attr_of(base, "width") or "0") >= width
                and float(attr_of(base, "height") or "0") >= height
            )
        except ValueError:
            covers = False
        if not covers or fill in (None, "none") or float(fill_opacity) < 1.0:
            errors.append(f"{rel}: terrain tile must start with an opaque full-cover base rect")
        # "void" is flat by design: drawTerrainTile returns before any detail
        # and paints exactly the palette colour #0b1016.
        if key == "void":
            if len(shapes) != 1 or (fill or "").lower() != "#0b1016":
                errors.append(f"{rel}: void tile must be a single #0b1016 rect")
        elif len(shapes) < 2:
            errors.append(f"{rel}: tile looks flat (only {len(shapes)} shape(s))")

    for key, spec in manifest["entities"].items():
        rel = spec["image"]
        path = ASSETS / rel
        if not path.exists():
            continue
        parsed = parse_svg(path)
        if parsed is None:
            continue
        root, width, height, _view_box = parsed
        shapes = list(iter_shapes(root))
        inks = {
            value.lower()
            for _name, child in shapes
            for attr in ("fill", "stroke")
            for value in (attr_of(child, attr),)
            if value not in (None, "none")
        }
        if len(inks) < 4:
            errors.append(f"{rel}: sprite looks flat (<4 distinct ink colours)")
        # sprites must not paint their whole canvas: no full-cover opaque rect
        for _name, child in shapes:
            if local_name(child.tag) != "rect" or (attr_of(child, "fill") or "") == "none":
                continue
            try:
                full = (
                    float(attr_of(child, "x") or "0") <= 0
                    and float(attr_of(child, "y") or "0") <= 0
                    and float(attr_of(child, "width") or "0") >= width
                    and float(attr_of(child, "height") or "0") >= height
                    and float(attr_of(child, "fill-opacity") or "1") >= 1.0
                )
            except ValueError:
                full = False
            if full:
                errors.append(f"{rel}: sprite fills the whole canvas; transparent background lost")
                break
        # frames must actually differ (strips); single-frame buildings skip this
        if int(spec.get("frames", 1)) >= 2:
            groups = [
                child
                for child in root
                if local_name(child.tag) == "g" and attr_of(child, "clip-path")
            ]
            texts = [ET.tostring(group, encoding="unicode") for group in groups]
            if len(texts) >= 2 and texts[0] == texts[1]:
                errors.append(f"{rel}: animation frames 0 and 1 are identical")

    total = len(referenced)
    on_disk = len(list((ASSETS / "tiles").iterdir())) + len(list((ASSETS / "entities").iterdir()))
    print(f"files referenced by manifest : {total}")
    print(f"files on disk                : {on_disk}")
    print(f"checks run                   : {checked}")
    if errors:
        print(f"FAIL: {len(errors)} problem(s)")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    print("OK: all manifest files exist as well-formed SVGs, viewBox/width/height")
    print("    geometry matches the manifest frame math, content sane.")


def fmt_dim(width: float, height: float) -> str:
    def part(value: float) -> str:
        return str(int(value)) if float(value).is_integer() else str(value)

    return f"{part(width)}x{part(height)}"


def main() -> None:
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "verify":
        verify()
    else:
        sys.exit("usage: verify_assets.py verify")


if __name__ == "__main__":
    main()
