#!/usr/bin/env python3
"""
Scan Blockbench .bbmodel files for likely coplanar-face z-fighting.

Default mode reports only small, high-risk exact overlaps:
- two axis-aligned elements
- touching on one axis
- identical projected rectangle on the other two axes

Use --seams to report any shared-plane face contact instead. That mode is noisier and is
best used as a manual review aid.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from itertools import combinations
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class Box:
    name: str
    minimum: tuple[float, float, float]
    maximum: tuple[float, float, float]


@dataclass(frozen=True)
class AllowRule:
    model: str
    a: str
    b: str
    axis: int


def _as_box(element: dict) -> Box | None:
    if "rotation" in element:
        return None

    minimum = tuple(float(v) for v in element.get("from", ()))
    maximum = tuple(float(v) for v in element.get("to", ()))
    if len(minimum) != 3 or len(maximum) != 3:
        return None
    if any(maximum[i] < minimum[i] for i in range(3)):
        return None
    return Box(name=element.get("name", "<unnamed>"), minimum=minimum, maximum=maximum)


def _interval_overlap(a0: float, a1: float, b0: float, b1: float) -> float:
    return min(a1, b1) - max(a0, b0)


def _same_plane(a: Box, b: Box, axis: int) -> float | None:
    if a.maximum[axis] == b.minimum[axis]:
        return a.maximum[axis]
    if b.maximum[axis] == a.minimum[axis]:
        return b.maximum[axis]
    return None


def _projected_rect(box: Box, axis: int) -> tuple[tuple[float, float], tuple[float, float]]:
    axes = [0, 1, 2]
    axes.remove(axis)
    x, y = axes
    return (box.minimum[x], box.maximum[x]), (box.minimum[y], box.maximum[y])


def _rects_intersect(a: Box, b: Box, axis: int) -> tuple[float, float] | None:
    axes = [0, 1, 2]
    axes.remove(axis)
    first = _interval_overlap(a.minimum[axes[0]], a.maximum[axes[0]], b.minimum[axes[0]], b.maximum[axes[0]])
    second = _interval_overlap(a.minimum[axes[1]], a.maximum[axes[1]], b.minimum[axes[1]], b.maximum[axes[1]])
    if first <= 0 or second <= 0:
        return None
    return first, second


def _should_report_default(overlap: tuple[float, float]) -> bool:
    # Keep the default pass conservative: we only flag small coplanar joins that are
    # more likely to produce visible z-fighting than intentional large structural seams.
    area = overlap[0] * overlap[1]
    return area <= 4.0


def _load_allowlist(path: Path) -> set[AllowRule]:
    if not path.exists():
        return set()

    with path.open("r", encoding="utf-8") as fh:
        raw = json.load(fh)

    rules: set[AllowRule] = set()
    for model, entries in raw.items():
        for entry in entries:
            rules.add(
                AllowRule(
                    model=model,
                    a=entry["a"],
                    b=entry["b"],
                    axis=int(entry["axis"]),
                )
            )
    return rules


def _is_allowed(model: str, a: str, b: str, axis: int, allowlist: set[AllowRule]) -> bool:
    if not allowlist:
        return False
    names = {a, b}
    for rule in allowlist:
        if rule.model == model and rule.axis == axis and names == {rule.a, rule.b}:
            return True
    return False


def scan_model(path: Path, seams: bool, allowlist: set[AllowRule]) -> list[str]:
    with path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)

    boxes = [box for box in (_as_box(e) for e in data.get("elements", [])) if box is not None]
    findings: list[str] = []

    for a, b in combinations(boxes, 2):
        for axis in range(3):
            plane = _same_plane(a, b, axis)
            if plane is None:
                continue
            overlap = _rects_intersect(a, b, axis)
            if overlap is None:
                continue

            if seams:
                findings.append(
                    f"{path.name}: {a.name} <-> {b.name} share plane axis={axis} @ {plane:g} "
                    f"with projected overlap {overlap[0]:g} x {overlap[1]:g}"
                )
                break

            if _is_allowed(path.name, a.name, b.name, axis, allowlist):
                continue

            if _should_report_default(overlap):
                findings.append(
                    f"{path.name}: {a.name} <-> {b.name} small coplanar overlap on axis={axis} @ {plane:g} "
                    f"({overlap[0]:g} x {overlap[1]:g})"
                )
                break

    return findings


def iter_targets(paths: list[str], default_root: Path) -> Iterable[Path]:
    if paths:
        for raw in paths:
            p = Path(raw)
            if p.is_dir():
                yield from sorted(p.rglob("*.bbmodel"))
            elif raw.endswith(".bbmodel"):
                yield p
    else:
        yield from sorted(default_root.rglob("*.bbmodel"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", help="bbmodel files or directories to scan")
    parser.add_argument(
        "--seams",
        action="store_true",
        help="report any shared-plane contact, not just exact face overlaps",
    )
    args = parser.parse_args()

    default_root = Path(__file__).resolve().parents[2]
    allowlist = _load_allowlist(Path(__file__).with_name("bbmodel_coplanar_allowlist.json"))
    any_findings = False
    total = 0
    for target in iter_targets(args.paths, default_root):
        if not target.exists() or target.suffix.lower() != ".bbmodel":
            continue
        total += 1
        findings = scan_model(target, seams=args.seams, allowlist=allowlist)
        if findings:
            any_findings = True
            for line in findings:
                print(line)

    if total == 0:
        print("No .bbmodel files found.")
        return 2

    if not any_findings:
        mode = "shared-plane seams" if args.seams else "small coplanar overlaps"
        print(f"No {mode} found in {total} model file(s).")
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main())
