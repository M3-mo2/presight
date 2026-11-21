#!/usr/bin/env python3
"""Check a mod jar against TWO Minecraft versions' SRG maps.

The player runs one version, the mapping chain may have been derived for another, and the
difference is invisible at compile time: `javac` validates against whichever readable jar
it was given. The 1.8 vs 1.8.9 split is the expensive case — the Tessellator vertex
builder and a handful of WorldRenderer/Entity members exist only in 1.8.9, so a jar built
for 1.8.9 compiles cleanly and throws NoSuchMethodError on the first draw call under 1.8.

Usage:  python3 check_both.py <jar> <deobf-A.srg> <deobf-B.srg>

Exit code is 0 only when every symbol the jar references exists in BOTH maps and no
readable MCP name survived the remap.
"""
import subprocess
import sys
import zipfile
from pathlib import Path

# Readable names that prove the reobf step did not run. Only names belonging to
# Minecraft/Forge: a name the mod declares itself is not a leak.
MCP_NAMES = {
    "addInformation", "getTagCompound", "setTagCompound", "getCurrentItem",
    "stackSize", "getDisplayName", "loadedEntityList", "playerEntities",
    "drawStringWithShadow", "getEntityItem", "registerCommand",
    "endVertex", "getWorldRenderer",
}


def srg_names(path: str) -> tuple[set[str], set[str]]:
    """(methods, fields) defined by one deobfuscation map."""
    methods: set[str] = set()
    fields: set[str] = set()
    text = Path(path).read_text(encoding="latin-1", errors="ignore")
    for line in text.split("\n"):
        parts = line.split()
        if parts[:1] == ["MD:"] and len(parts) == 5:
            methods.add(parts[3].rsplit("/", 1)[-1])
        elif parts[:1] == ["FD:"] and len(parts) == 3:
            fields.add(parts[2].rsplit("/", 1)[-1])
    return methods, fields


def jar_symbols(jar: str) -> tuple[set[str], set[str], set[str]]:
    """(methods, fields, leaked readable names) referenced by the jar's classes."""
    methods: set[str] = set()
    fields: set[str] = set()
    leaks: set[str] = set()
    with zipfile.ZipFile(jar) as zf:
        for name in zf.namelist():
            if not name.endswith(".class"):
                continue
            out = subprocess.run(["strings", "-n", "4"], input=zf.read(name),
                                 capture_output=True).stdout
            for raw in out.split(b"\n"):
                s = raw.decode("latin-1")
                if s.startswith("func_") and s.count("_") >= 2:
                    methods.add(s)
                elif s.startswith("field_") and s.count("_") >= 2:
                    fields.add(s)
                elif s in MCP_NAMES:
                    leaks.add(s)
    return methods, fields, leaks


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    jar, srg_a, srg_b = sys.argv[1], sys.argv[2], sys.argv[3]

    used_m, used_f, leaks = jar_symbols(jar)
    if not used_m and not used_f:
        # A scan that found nothing tested nothing. Never report that as a pass.
        print("  FAIL the jar references no srg symbols — the scan is broken, not the jar")
        return 1

    print(f"  jar: {len(used_m)} srg methods, {len(used_f)} fields")
    ok = True
    for label, path in (("A", srg_a), ("B", srg_b)):
        km, kf = srg_names(path)
        if not km:
            print(f"  FAIL {label}: {path} parsed to zero symbols")
            ok = False
            continue
        bad_m = sorted(x for x in used_m if x not in km)
        bad_f = sorted(x for x in used_f if x not in kf)
        if bad_m or bad_f:
            ok = False
            print(f"  FAIL {label} ({Path(path).name}): methods={bad_m} fields={bad_f}")
        else:
            print(f"  PASS {label} ({Path(path).name}): {len(km)} methods, every symbol present")

    if leaks:
        ok = False
        print(f"  FAIL readable MCP names survived the remap: {sorted(leaks)}")

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
