#!/usr/bin/env python3
"""Snapshot and verify the classic game plugin's material PNGs.

Usage:
    python verify_assets.py snapshot   # record sha256 + dimensions of every
                                       # manifest-referenced file (run BEFORE
                                       # redraw_assets.py)
    python verify_assets.py verify     # check existence, dimensions, manifest
                                       # frame geometry, content sanity, and
                                       # that every file changed since the
                                       # snapshot
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

from PIL import Image

TOOLS = Path(__file__).resolve().parent
ASSETS = TOOLS.parent / "src" / "main" / "resources" / "assets"
SNAPSHOT_PATH = TOOLS / "before_hashes.json"


def load_manifest() -> dict:
    return json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))


def manifest_images(manifest: dict) -> list[str]:
    images = [spec["image"] for spec in manifest["tiles"].values()]
    images += [spec["image"] for spec in manifest["entities"].values()]
    return sorted(images)


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def pixel_tuples(img: Image.Image) -> list[tuple[int, int, int, int]]:
    """RGBA pixel list without the deprecated Image.getdata()."""
    data = img.convert("RGBA").tobytes()
    return [tuple(data[i : i + 4]) for i in range(0, len(data), 4)]  # type: ignore[misc]


def snapshot() -> None:
    manifest = load_manifest()
    out = {}
    for rel in manifest_images(manifest):
        path = ASSETS / rel
        if not path.exists():
            sys.exit(f"snapshot error: missing file {rel}")
        with Image.open(path) as img:
            width, height = img.size
        out[rel] = {"sha256": hash_file(path), "width": width, "height": height}
    SNAPSHOT_PATH.write_text(json.dumps(out, indent=2, sort_keys=True), encoding="utf-8")
    print(f"snapshot: recorded {len(out)} files -> {SNAPSHOT_PATH.name}")


def expected_dimensions(key: str, spec: dict, tile_pixels: int) -> tuple[int, int]:
    """Expected pixel dimensions from the manifest alone."""
    frames = int(spec["frames"])
    if key.startswith("building."):
        return 2 * tile_pixels, 2 * tile_pixels  # single-frame square building sheet
    return tile_pixels, tile_pixels * frames  # unit strip: square frames


def verify() -> None:
    if not SNAPSHOT_PATH.exists():
        sys.exit("verify error: no snapshot found; run 'verify_assets.py snapshot' first")
    snapshot_data = json.loads(SNAPSHOT_PATH.read_text(encoding="utf-8"))
    manifest = load_manifest()
    tile_pixels = int(manifest["tilePixels"])

    errors: list[str] = []
    checked = 0

    referenced = set(manifest_images(manifest))

    # 1. Every manifest-referenced file exists; no unexpected extra files.
    for sub in ("tiles", "entities"):
        actual = {f"{sub}/{p.name}" for p in (ASSETS / sub).iterdir() if p.is_file()}
        for extra in sorted(actual - referenced):
            errors.append(f"unexpected extra file in assets/{sub}: {extra}")
    for rel in sorted(referenced):
        if not (ASSETS / rel).exists():
            errors.append(f"missing manifest file: {rel}")

    # 2/3. Dimensions + frame geometry.
    for kind_group, entries in (("tiles", manifest["tiles"]), ("entities", manifest["entities"])):
        for key, spec in entries.items():
            rel = spec["image"]
            path = ASSETS / rel
            if not path.exists():
                continue
            checked += 1
            with Image.open(path) as img:
                width, height = img.size
                mode = img.mode
            if kind_group == "tiles":
                want = (tile_pixels, tile_pixels)
            else:
                want = expected_dimensions(key, spec, tile_pixels)
            if (width, height) != want:
                errors.append(f"{rel}: dimensions {width}x{height}, expected {want[0]}x{want[1]}")
            if kind_group == "entities":
                frames = int(spec["frames"])
                if height % frames != 0:
                    errors.append(f"{rel}: height {height} not divisible by frames {frames}")
                else:
                    frame_h = height // frames
                    if frame_h != width:
                        errors.append(f"{rel}: frame {width}x{frame_h} is not square")

    # 4. Every file changed vs the snapshot; dimensions unchanged.
    for rel, before in sorted(snapshot_data.items()):
        path = ASSETS / rel
        if not path.exists():
            errors.append(f"{rel}: existed at snapshot time but is now missing")
            continue
        checked += 1
        now_hash = hash_file(path)
        if now_hash == before["sha256"]:
            errors.append(f"{rel}: unchanged since snapshot (art did not change)")
        with Image.open(path) as img:
            now_dims = img.size
        if (before["width"], before["height"]) != now_dims:
            errors.append(
                f"{rel}: dimensions changed from "
                f"{before['width']}x{before['height']} to {now_dims[0]}x{now_dims[1]}"
            )

    # 5. Content sanity.
    for key, spec in manifest["tiles"].items():
        rel = spec["image"]
        path = ASSETS / rel
        if not path.exists():
            continue
        with Image.open(path) as img:
            rgba_img = img.convert("RGBA")
        pixels = pixel_tuples(rgba_img)
        alphas = {p[3] for p in pixels}
        if alphas and max(alphas) != 255:
            errors.append(f"{rel}: terrain tile must be fully opaque, max alpha {max(alphas)}")
        # "void" is flat by design: drawTerrainTile returns before any detail
        # and paints exactly the palette colour #0b1016.
        if key == "void":
            if {p[:3] for p in pixels} != {(11, 16, 22)}:
                errors.append(f"{rel}: void tile must be the single palette colour #0b1016")
        elif len({p[:3] for p in pixels}) < 6:
            errors.append(f"{rel}: tile looks flat (<6 distinct colours)")

    for key, spec in manifest["entities"].items():
        rel = spec["image"]
        path = ASSETS / rel
        if not path.exists():
            continue
        with Image.open(path) as img:
            rgba_img = img.convert("RGBA")
            width, height = img.size
        pixels = pixel_tuples(rgba_img)
        if len({p[:3] for p in pixels if p[3] > 0}) < 4:
            errors.append(f"{rel}: sprite looks flat (<4 distinct visible colours)")
        frames = int(spec["frames"])
        frame_h = height // frames
        opaque = sum(1 for p in pixels if p[3] > 200)
        if opaque == 0:
            errors.append(f"{rel}: no opaque pixels at all")
        if opaque > len(pixels) * 0.95:
            errors.append(f"{rel}: sprite fills the whole canvas; transparent background lost")
        # corners of every frame must be transparent
        for frame in range(frames):
            y0 = frame * frame_h
            for cx, cy in ((0, y0), (width - 1, y0), (0, y0 + frame_h - 1), (width - 1, y0 + frame_h - 1)):
                if rgba_img.getpixel((cx, cy))[3] != 0:
                    errors.append(f"{rel}: frame {frame} corner ({cx},{cy}) is not transparent")
                    break
        # frames must actually differ
        if frames >= 2:
            frame0 = rgba_img.crop((0, 0, width, frame_h))
            frame1 = rgba_img.crop((0, frame_h, width, 2 * frame_h))
            if frame0.tobytes() == frame1.tobytes():
                errors.append(f"{rel}: animation frames 0 and 1 are identical")

    total = len(referenced)
    print(f"files referenced by manifest : {total}")
    print(f"files on disk                : {len(list((ASSETS / 'tiles').iterdir())) + len(list((ASSETS / 'entities').iterdir()))}")
    print(f"snapshot entries compared    : {len(snapshot_data)}")
    print(f"checks run                   : {checked}")
    if errors:
        print(f"FAIL: {len(errors)} problem(s)")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    print("OK: all manifest files exist, dimensions and frame geometry match,")
    print("    every file differs from the pre-redraw snapshot, content sane.")


def main() -> None:
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "snapshot":
        snapshot()
    elif command == "verify":
        verify()
    else:
        sys.exit("usage: verify_assets.py snapshot|verify")


if __name__ == "__main__":
    main()
