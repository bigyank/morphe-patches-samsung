#!/usr/bin/env python3
"""Verify a patched Samsung Health APK: did the Morphe stubs actually land?

Single-loop stage 4: runs against the *patched* APK produced by
morphe-desktop CLI (or Morphe Manager on-device) and confirms, on the dex
level:

  1. Every hard fingerprint method now has a stub body (const/4 v0, 0x0 +
     return[-object], or return-void) — the Knox bypass landed.
  2. The 7.x account-fetch rewrite (SamsungAccountDataSourceImpl$fetchAccountId$2
     ->invokeSuspend) routes through AccountManager.getAccountsByType.
  3. No const-string in the patched dex still references com.osp.app.signin
     (all replaced with com.notsamsung.dummy — the login bypass landed).

Exit 0 = all checks green; exit 1 = a stub did not land.

Usage:
    python3 scripts/verify_patched.py --original <stock.apk> --patched <patched.apk> [--json-out <path>]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from preflight_fingerprints import (  # noqa: E402
    FINGERPRINTS_KT,
    mark_optional,
    parse_descriptor,
    parse_fingerprints,
)

SIGNIN = "com.osp.app.signin"
DUMMY = "com.notsamsung.dummy"


def is_stub_body(meth) -> tuple[bool, str]:
    """True when the method body is one of the stub shapes BytecodeStubUtils writes."""
    ref = meth.method_ref
    if ref is None or ref.get_instructions() is None:
        return False, "no instructions"
    ins = list(ref.get_instructions())
    if not ins:
        return False, "empty body"
    names = [i.get_name() for i in ins]
    if names[-1] not in ("return", "return-object", "return-void"):
        return False, f"ends in {names[-1]}"
    if len(ins) == 1:
        return (names[0] == "return-void"), "return-void"
    if len(ins) == 2 and names[0] == "const/4":
        # androguard prints the literal as "0" (decimal) or "0x0" depending on version
        if (ins[0].get_output() or "").split(",")[-1].strip() not in ("0", "0x0"):
            return False, f"const/4 = {ins[0].get_output()}"
        return True, "const/4 0 + return"
    return False, f"{len(ins)} instructions"


def method_refs(meth, needle: str) -> bool:
    ref = meth.method_ref
    if ref is None:
        return False
    return any(needle in (i.get_output() or "") for i in ref.get_instructions())


def const_string_hits(index: dict, needle: str) -> int:
    """Number of methods holding a const-string that contains `needle`."""
    hits = 0
    for methods in index.values():
        for m in methods:
            if m.method_ref is None:
                continue
            for ins in m.method_ref.get_instructions():
                if ins.get_name() in ("const-string", "const-string/jumbo") and needle in (ins.get_output() or ""):
                    hits += 1
                    break
    return hits


def promotion_check(patched_index: dict) -> tuple[str, str]:
    """hideHomePromotionPanelPatch: the promotion ViewModel's display path must
    be a 2-instruction sget-object/return-object stub (the log tag it carried
    is wiped with the body). Absent package = build without ads, OK."""
    tag = "SHEALTH#PromotionDiscoverCardViewModel"
    vm_classes = {
        cls: methods
        for cls, methods in patched_index.items()
        if "promotion/discoverplus" in cls and "PromotionDiscoverCardViewModel" in cls
    }
    if not vm_classes:
        return "OK", "promotion package absent (patch no-ops)"
    tag_survivors, stubs = [], []
    for cls, methods in vm_classes.items():
        for m in methods:
            if m.method_ref is None:
                continue
            ins = list(m.method_ref.get_instructions())
            names = [i.get_name() for i in ins]
            if any(
                n in ("const-string", "const-string/jumbo") and tag in (i.get_output() or "")
                for n, i in zip(names, ins)
            ):
                tag_survivors.append(f"{cls}->{m.name}")
            params, ret = parse_descriptor(m.descriptor)
            if (
                ret == "Ljava/lang/Object;" and len(params) == 2 and m.access_flags & 0x8
                and len(ins) == 2 and names[0] == "sget-object" and names[1] == "return-object"
            ):
                stubs.append(f"{cls}->{m.name}")
    if stubs:
        return "OK", f"stubbed: {stubs[0]}"
    if tag_survivors:
        return "FAIL", f"display path still live: {tag_survivors[0]}"
    return "FAIL", "ViewModel present but no display-path stub found"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--original", required=True, type=Path)
    ap.add_argument("--patched", required=True, type=Path)
    ap.add_argument("--json-out", type=Path, default=None)
    args = ap.parse_args()

    for p in (args.original, args.patched):
        if not p.is_file():
            sys.exit(f"FATAL: APK not found: {p}")

    from preflight_fingerprints import load_dex_index

    print(f"Verifying patched APK: {args.patched.name}")
    print(f"  original: {args.original.name}")

    print("Indexing patched dex…", file=sys.stderr)
    patched_index, _ = load_dex_index(args.patched)

    fingerprints = parse_fingerprints()
    mark_optional(fingerprints)

    hard_fail, results = [], []

    # 1. Hard fingerprint methods must now be stubs.
    stubbed = missed = 0
    for fp in fingerprints:
        if fp.optional:
            continue
        methods = patched_index.get(fp.defining_class, [])
        match = next(
            (
                m for m in methods
                if m.name == fp.method
                and parse_descriptor(m.descriptor) == (fp.parameters, fp.return_type)
            ),
            None,
        )
        if match is None:
            missed += 1
            results.append({"check": fp.name, "status": "MISSING", "detail": "not found in patched dex"})
            hard_fail.append(fp.name)
            continue
        ok, detail = is_stub_body(match)
        if ok:
            stubbed += 1
            results.append({"check": fp.name, "status": "STUBBED", "detail": detail})
        else:
            missed += 1
            results.append({"check": fp.name, "status": "NOT_STUBBED", "detail": detail})
            hard_fail.append(fp.name)
    print(f"\nFingerprint stubs: {stubbed} stubbed, {missed} not")
    for r in results:
        if r["status"] != "STUBBED":
            print(f"  {r['status']:12} {r['check']} — {r['detail']}")

    # 2. Account-fetch rewrite routes through AccountManager — either the 7.x
    #    SamsungAccountDataSourceImpl rewrite or the 6.x getSamsungAccountId redirect.
    fetch_ok = False
    fetch_where = []
    for cls, methods in patched_index.items():
        for marker, want in (
            ("SamsungAccountDataSourceImpl", "invokeSuspend"),
            ("SamsungAccountUtils", "getSamsungAccountId"),
        ):
            if marker not in cls:
                continue
            for m in methods:
                if m.name == want and m.method_ref and method_refs(m, "getAccountsByType"):
                    fetch_ok = True
                    fetch_where.append(f"{cls}->{m.name}")
    if fetch_ok:
        print(f"\nAccount fetch rewrite: OK ({'; '.join(fetch_where)})")
    else:
        print("\nAccount fetch rewrite: NOT FOUND — no AccountManager redirect in patched dex")
        hard_fail.append("account_provider_fetch_rewrite")

    # 3. Signin package replaced in dex const-strings.
    signin_patched = const_string_hits(patched_index, SIGNIN)
    dummy_patched = const_string_hits(patched_index, DUMMY)
    print(f"Signin strings in patched dex: {SIGNIN}={signin_patched}, {DUMMY}={dummy_patched}")
    if signin_patched > 0:
        hard_fail.append("signin_package_replace")
    if dummy_patched == 0:
        hard_fail.append("signin_package_replace")

    # 4. Promotion (ad) display path — hideHomePromotionPanelPatch.
    promo_status, promo_detail = promotion_check(patched_index)
    print(f"Promotion display path: {promo_status} — {promo_detail}")
    if promo_status == "FAIL":
        hard_fail.append("promotion_display_stub")
    results.append({"check": "promotion_display_path", "status": promo_status, "detail": promo_detail})

    report = {
        "original": str(args.original),
        "patched": str(args.patched),
        "fingerprint_stubs": results,
        "account_fetch_rewrite": fetch_ok,
        "signin_const_strings_patched": signin_patched,
        "dummy_const_strings_patched": dummy_patched,
        "promotion_display_path": {"status": promo_status, "detail": promo_detail},
        "hard_failures": hard_fail,
    }
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(report, indent=2))

    print()
    if hard_fail:
        print(f"RESULT: FAIL — {', '.join(hard_fail)}")
        return 1
    print("RESULT: PASS — Knox stubs, account-fetch rewrite, signin replacement, and promotion stub all landed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
