#!/usr/bin/env python3
"""
Copy a small set of vanilla Minecraft model/texture support files into the
workspace so Blockbench can open Musubi models without missing-parent errors.

This is intentionally narrow: it only vendors the parent files and textures
we currently rely on for the self-contained model workflow.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import sys
import zipfile


MODEL_COPY_MAP = {
    "assets/minecraft/models/block/block.json": [
        "assets/minecraft/models/block/block.json",
        "assets/musubi/models/block/block.json",
    ],
    "assets/minecraft/models/block/cube.json": [
        "assets/minecraft/models/block/cube.json",
        "assets/musubi/models/block/cube.json",
    ],
    "assets/minecraft/models/block/cube_all.json": [
        "assets/minecraft/models/block/cube_all.json",
        "assets/musubi/models/block/cube_all.json",
    ],
    "assets/minecraft/models/block/orientable_with_bottom.json": [
        "assets/minecraft/models/block/orientable_with_bottom.json",
        "assets/musubi/models/block/orientable_with_bottom.json",
    ],
    "assets/minecraft/models/item/generated.json": [
        "assets/minecraft/models/item/generated.json",
        "assets/musubi/models/item/generated.json",
    ],
    "assets/minecraft/models/item/handheld.json": [
        "assets/minecraft/models/item/handheld.json",
        "assets/musubi/models/item/handheld.json",
    ],
}

TEXTURE_COPY_MAP = {
    "assets/minecraft/textures/block/red_wool.png": [
        "assets/minecraft/textures/block/red_wool.png",
    ],
    "assets/minecraft/textures/block/yellow_wool.png": [
        "assets/minecraft/textures/block/yellow_wool.png",
    ],
    "assets/minecraft/textures/block/blue_wool.png": [
        "assets/minecraft/textures/block/blue_wool.png",
    ],
    "assets/minecraft/textures/block/lime_wool.png": [
        "assets/minecraft/textures/block/lime_wool.png",
    ],
}


def find_client_jar() -> Path | None:
    """Find a Minecraft client jar that contains vanilla model assets."""

    candidates = []

    home = Path.home()
    candidates.extend([
        home / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar",
        home / ".gradle/caches/ng_execute",
    ])

    appdata = os.environ.get("APPDATA")
    if appdata:
        candidates.append(Path(appdata) / ".minecraft/versions")

    candidates.append(home / ".minecraft/versions")

    for candidate in candidates:
        if candidate.is_file() and candidate.suffix.lower() == ".jar":
            return candidate
        if candidate.is_dir():
            jars = sorted(candidate.rglob("client.jar"))
            if jars:
                return jars[0]
            jars = sorted(candidate.rglob("*.jar"))
            for jar in jars:
                if jar.name.lower().endswith(".jar"):
                    return jar
    return None

def copy_from_jar(jar: Path, src: str, targets: list[str], assets_root: Path, dry_run: bool) -> None:
    with zipfile.ZipFile(jar) as zf:
        try:
            data = zf.read(src)
        except KeyError:
            raise FileNotFoundError(f"Missing {src} in {jar}")

    for target in targets:
        target_path = assets_root / target.replace("/", os.sep)
        target_path.parent.mkdir(parents=True, exist_ok=True)

        if dry_run:
            print(f"[dry-run] {target_path}")
            continue

        existing = target_path.read_bytes() if target_path.exists() else None
        if existing != data:
            target_path.write_bytes(data)
            print(f"wrote {target_path}")
        else:
            print(f"unchanged {target_path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Sync Blockbench support assets from a Minecraft client jar.")
    parser.add_argument("--jar", type=Path, help="Path to a Minecraft client.jar to source assets from.")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be copied without writing files.")
    args = parser.parse_args()

    jar = args.jar or find_client_jar()
    if not jar:
        print("Could not find a Minecraft client jar. Pass --jar <path>.", file=sys.stderr)
        return 1
    if not jar.exists():
        print(f"Client jar not found: {jar}", file=sys.stderr)
        return 1

    assets_root = Path(__file__).resolve().parents[1] / "src" / "main" / "resources"
    print(f"Using client jar: {jar}")
    print(f"Assets root: {assets_root}")

    for src, targets in MODEL_COPY_MAP.items():
        copy_from_jar(jar, src, targets, assets_root, args.dry_run)

    for src, targets in TEXTURE_COPY_MAP.items():
        copy_from_jar(jar, src, targets, assets_root, args.dry_run)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
