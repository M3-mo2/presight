"""Build once, verify against BOTH 1.8 and 1.8.9.

The mod has to run on 1.8 (what the player uses) and must not silently be broken on
1.8.9. The two versions differ in the Tessellator/WorldRenderer vertex API, which is
exactly the kind of difference that compiles fine and throws NoSuchMethodError at
runtime — so the jar is checked against each version's own SRG map.

Usage: python3 check_both.py <jar> <deobf18.srg> <deobf189.srg>
"""
import subprocess
import sys
import zipfile
from pathlib import Path


def srg_names(path: str) -> tuple[set[str], set[str]]:
    methods, fields = set(), set()
    for line in Path(path).read_text(encoding="latin-1").split("\n"):
        p = line.split()
        if p[:1] == ["MD:"] and len(p) == 5:
            methods.add(p[3].rsplit("/", 1)[-1])
        elif p[:1] == ["FD:"] and len(p) == 3:
            fields.add(p[2].rsplit("/", 1)[-1])
    return methods, fields


def jar_symbols(jar: str) -> tuple[set[str], set[str], set[str]]:
    methods, fields, leaks = set(), set(), set()
    # Only names that belong to Minecraft/Forge. A name the mod declares itself is
    # not a leak — "begin" here is BoxBatch.begin(float), which is ours.
    readable = {
        "addInformation", "getTagCompound", "setTagCompound", "getCurrentItem",
        "stackSize", "getDisplayName", "loadedEntityList", "playerEntities",
        "drawStringWithShadow", "getEntityItem", "registerCommand",
        "endVertex", "getWorldRenderer",
    }
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if not name.endswith(".class"):
                continue
            out = subprocess.run(["strings", "-n", "4"], input=z.read(name),
                                 capture_output=True).stdout
            for s in (x.decode("latin-1") for x in out.split(b"\n")):
                if s.startswith("func_") and s.count("_") >= 2:
                    methods.add(s)
                elif s.startswith("field_") and s.count("_") >= 2:
                    fields.add(s)
                elif s in readable:
                    leaks.add(s)
    return methods, fields, leaks


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    jar, s18, s189 = sys.argv[1], sys.argv[2], sys.argv[3]

    m18, f18 = srg_names(s18)
    m189, f189 = srg_names(s189)
    used_m, used_f, leaks = jar_symbols(jar)

    print(f"  jar: {len(used_m)} srg methods, {len(used_f)} fields")
    print(f"  1.8 map: {len(m18)} methods   |   1.8.9 map: {len(m189)} methods")

    ok = True
    for label, mm, ff in (("1.8", m18, f18), ("1.8.9", m189, f189)):
        bad_m = sorted(x for x in used_m if x not in mm)
        bad_f = sorted(x for x in used_f if x not in ff)
        if bad_m or bad_f:
            ok = False
            print(f"  FAIL {label}: methods={bad_m} fields={bad_f}")
        else:
            print(f"  PASS {label}: every symbol present")

    if leaks:
        ok = False
        print(f"  FAIL readable names survived the remap: {sorted(leaks)}")

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
