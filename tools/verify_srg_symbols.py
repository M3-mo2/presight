#!/usr/bin/env python3
"""Verify a Minecraft mod jar against a version's SRG map.

This is the check `javac` cannot do. A mod is compiled against readable MCP names
(`addInformation`), but Forge executes vanilla under SRG names (`func_77624_a`). A jar
that ships with MCP names, or that names an SRG symbol which does not exist in that
version, builds cleanly and throws `NoSuchMethodError` the first time the class is
touched in game.

Usage:
    verify_srg_symbols.py <mod.jar> <deobf.srg> [--expect-mcp name ...]

The map file is the decompressed `deobfuscation_data-<version>.lzma` from Forge's
universal jar (text, latin-1, MD:/FD:/CL: prefixed lines; may arrive with CRLF).

Exit 0 = every referenced symbol exists and no readable name leaked.
Exit 1 = at least one failure, printed with the offending symbol.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
import zipfile
from pathlib import Path

# Names that must never appear as raw strings in shipped classes. Extend per version.
DEFAULT_MCP_NAMES = (
    "addInformation", "getTagCompound", "setTagCompound", "getDisplayName",
    "getCurrentItem", "stackSize", "loadedEntityList", "drawStringWithShadow",
    "getStringWidth", "setUnlocalizedName", "getEntityItem",
)


def load_map(path: Path) -> tuple[set[str], set[str]]:
    """Return (method names, field names) the version defines, in SRG form."""
    raw = path.read_bytes()
    if b"\xfd7zXZ" in raw[:16]:  # xz magic — still compressed
        import lzma
        raw = lzma.decompress(raw)
    text = raw.decode("latin-1")
    methods: set[str] = set()
    fields: set[str] = set()
    for line in text.replace("\r\n", "\n").split("\n"):
        p = line.split()
        if not p:
            continue
        if p[0] == "MD:" and len(p) == 5:
            methods.add(p[3].rsplit("/", 1)[-1])
        elif p[0] == "FD:" and len(p) == 3:
            fields.add(p[2].rsplit("/", 1)[-1])
    return methods, fields


def scan_jar(jar: Path, mcp_names: tuple[str, ...]) -> tuple[set[str], set[str], dict[str, int], set[str]]:
    used_m, used_f = set(), set()
    per_class: dict[str, int] = {}
    leaks: set[str] = set()
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if not name.endswith(".class"):
                continue
            data = z.read(name)
            out = subprocess.run(["strings", "-n", "4"], input=data,
                                 capture_output=True).stdout
            count = 0
            for s in (x.decode("latin-1") for x in out.split(b"\n")):
                if s.startswith("func_") and s.count("_") >= 2:
                    used_m.add(s)
                    count += 1
                elif s.startswith("field_") and s.count("_") >= 2:
                    used_f.add(s)
                    count += 1
                elif s in mcp_names:
                    leaks.add(s)
            per_class[name] = count
    return used_m, used_f, per_class, leaks


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("map")
    ap.add_argument("--expect-mcp", action="append", default=None,
                    help="extra readable names that must not appear (repeatable)")
    args = ap.parse_args()

    jar, mapfile = Path(args.jar), Path(args.map)
    for p in (jar, mapfile):
        if not p.exists():
            print(f"  missing: {p}")
            return 2

    known_m, known_f = load_map(mapfile)
    print(f"  map defines {len(known_m)} methods, {len(known_f)} fields")

    mcp_names = DEFAULT_MCP_NAMES + tuple(args.expect_mcp or ())
    used_m, used_f, per_class, leaks = scan_jar(jar, mcp_names)
    print(f"  jar references {len(used_m)} methods, {len(used_f)} fields"
          f" across {len(per_class)} classes")
    print()

    bad_m = sorted(m for m in used_m if m not in known_m)
    bad_f = sorted(f for f in used_f if f not in known_f)
    for sym in sorted(used_m):
        print(f"    {sym:34} {'ok' if sym in known_m else 'MISSING'}")
    for sym in sorted(used_f):
        print(f"    {sym:34} {'ok' if sym in known_f else 'MISSING'}")
    print()

    failed = False
    if leaks:
        print(f"  FAIL: readable names survived (reobf did not run?): {sorted(leaks)}")
        failed = True
    if bad_m or bad_f:
        print(f"  FAIL: not defined in this version — methods={bad_m} fields={bad_f}")
        failed = True
    if not used_m and not used_f:
        print("  FAIL: jar references no SRG symbols at all — wrong input, or an empty build")
        failed = True
    if not failed:
        print("  PASS: every referenced symbol exists, no readable name leaked")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
