#!/usr/bin/env python3
"""Dex-level diff between two Samsung Health APKs, focused on patch targets.

Standard APK-triage practice (apktool+diff / method-inventory diff) applied to
this repo's patch surface:

  Tier A — target set: every class matching a fingerprint defining class or a
           stubber heuristic package gets a per-method body hash; the report
           lists ADDED / REMOVED / BODY-CHANGED methods.
  Tier B — global inventory: class and method counts by top-level package
           (name+descriptor only, no body hashing) for orientation.

Pair with REA: for every BODY-CHANGED tier-A class the report prints the
`scripts/rea.sh inspect-android-class` command to run next.

Usage:
    scripts/dex_diff.py --old <apk> --new <apk> [--json-out PATH] [--md-out PATH]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "scripts"))
from preflight_fingerprints import (  # noqa: E402
    load_dex_index,
    parse_fingerprints,
)

# Stubber heuristic packages/classes beyond the fingerprint defining classes.
# Keep these as tight as the Kotlin predicates — broad substrings ("oobe")
# flood the report with routine model-class churn (equals/hashCode/toString).
HEURISTIC_TARGETS = (
    "promotion/discoverplus",
    "PromotionDiscoverCardViewModel",
    "HomeAppCloseActivity",
    "KnoxHandlerViewModel",
    "samsungaccount",
    "SamsungAccountDataSourceImpl",
    "sak/SakChecker",
)


def target_substrings() -> list[str]:
    subs = {fp.defining_class[1:-1] for fp in parse_fingerprints()}
    subs.update(HEURISTIC_TARGETS)
    return sorted(subs)


def method_body_hash(meth) -> str | None:
    """Stable hash of the instruction stream (opcode + operands)."""
    ref = meth.method_ref
    if ref is None or ref.get_instructions() is None:
        return None
    h = hashlib.sha256()
    for ins in ref.get_instructions():
        h.update(ins.get_name().encode())
        h.update((ins.get_output() or "").encode())
        h.update(b"\x00")
    return h.hexdigest()[:16]


def build_target_index(index: dict, subs: list[str]) -> dict:
    """{(class): {(name, descriptor): body_hash}} for target classes only."""
    out: dict[str, dict[tuple[str, str], str | None]] = {}
    for cls, methods in index.items():
        if not any(s in cls for s in subs):
            continue
        entry = {}
        for m in methods:
            entry[(m.name, m.descriptor)] = method_body_hash(m)
        out[cls] = entry
    return out


def package_of(cls: str) -> str:
    parts = cls.strip(";").strip("L").split("/")
    return "/".join(parts[:3]) if len(parts) > 3 else cls


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--old", required=True, type=Path)
    ap.add_argument("--new", required=True, type=Path)
    ap.add_argument("--json-out", type=Path, default=None)
    ap.add_argument("--md-out", type=Path, default=None)
    args = ap.parse_args()
    for p in (args.old, args.new):
        if not p.is_file():
            sys.exit(f"FATAL: APK not found: {p}")

    subs = target_substrings()
    print(f"Indexing OLD {args.old.name}…", file=sys.stderr)
    old_index, _ = load_dex_index(args.old)
    print(f"Indexing NEW {args.new.name}…", file=sys.stderr)
    new_index, _ = load_dex_index(args.new)

    old_t = build_target_index(old_index, subs)
    new_t = build_target_index(new_index, subs)

    rows = []  # class, method, descriptor, change
    for cls in sorted(set(old_t) | set(new_t)):
        old_m, new_m = old_t.get(cls, {}), new_t.get(cls, {})
        if cls not in new_t:
            rows.append((cls, "*", "*", "CLASS_REMOVED"))
            continue
        if cls not in old_t:
            rows.append((cls, "*", "*", "CLASS_ADDED"))
            continue
        for key in sorted(set(old_m) | set(new_m)):
            if key not in new_m:
                rows.append((cls, key[0], key[1], "REMOVED"))
            elif key not in old_m:
                rows.append((cls, key[0], key[1], "ADDED"))
            elif old_m[key] != new_m[key]:
                rows.append((cls, key[0], key[1], "BODY"))

    # Global orientation counts
    old_methods = sum(len(v) for v in old_index.values())
    new_methods = sum(len(v) for v in new_index.values())
    pkg_added, pkg_removed = {}, {}
    old_pkgs = {c: None for c in old_index}
    for cls in new_index:
        if cls not in old_pkgs:
            pkg_added[package_of(cls)] = pkg_added.get(package_of(cls), 0) + 1
    for cls in old_index:
        if cls not in new_index:
            pkg_removed[package_of(cls)] = pkg_removed.get(package_of(cls), 0) + 1

    old_v = args.old.stem.split("@")[-1]
    new_v = args.new.stem.split("@")[-1]

    md = [
        f"# Dex diff — {old_v} → {new_v}",
        "",
        f"Old: `{args.old}` ({len(old_index)} classes / {old_methods} methods)  ",
        f"New: `{args.new}` ({len(new_index)} classes / {new_methods} methods)  ",
        f"Target set: {len(subs)} matchers → {len(old_t)} old / {len(new_t)} new classes",
        "",
        "## Target-set changes",
        "",
    ]
    if rows:
        md += ["| change | class | method |", "|---|---|---|"]
        for cls, name, desc, change in rows:
            md.append(f"| {change} | `{cls}` | `{name}{desc}` |")
    else:
        md += ["_No target-set changes — fingerprints/heuristics surface is identical._"]
    md += ["", "## Global class deltas (top packages)", ""]
    for pkg, n in sorted(pkg_added.items(), key=lambda kv: -kv[1])[:15]:
        md.append(f"- +{n} added under `{pkg}`")
    for pkg, n in sorted(pkg_removed.items(), key=lambda kv: -kv[1])[:10]:
        md.append(f"- -{n} removed under `{pkg}`")
    md += ["", "## Next step (REA)", ""]
    changed_classes = sorted({r[0] for r in rows if r[3] != "CLASS_ADDED"})
    for cls in changed_classes[:20]:
        java = cls[1:-1].replace("/", ".")
        md.append(f"    scripts/rea.sh inspect-android-class <new.apk> {java}")
    if not changed_classes:
        md.append("_Nothing to inspect — patch surface unchanged between these versions._")

    md_path = args.md_out or REPO_ROOT / ".cache/apks" / f"dex-diff-{old_v}-to-{new_v}.md"
    md_path.parent.mkdir(parents=True, exist_ok=True)
    md_path.write_text("\n".join(md) + "\n")

    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps({
            "old": str(args.old), "new": str(args.new),
            "target_changes": [
                {"class": c, "method": n, "descriptor": d, "change": ch}
                for c, n, d, ch in rows
            ],
            "summary": {
                "old_classes": len(old_index), "new_classes": len(new_index),
                "old_methods": old_methods, "new_methods": new_methods,
            },
        }, indent=2))

    print(f"\nTarget-set changes: {len(rows)}")
    for cls, name, desc, change in rows[:40]:
        print(f"  {change:14} {cls} {name}{desc}")
    if len(rows) > 40:
        print(f"  … and {len(rows) - 40} more")
    print(f"\nReport: {md_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
