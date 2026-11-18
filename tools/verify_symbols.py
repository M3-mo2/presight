"""Every Minecraft symbol a mod jar references must exist in its own version.

Forge 1.8.9 executes vanilla under SRG names (func_77624_a). A mod jar that refers
to an SRG symbol which is not in that version's deobfuscation map is a
NoSuchMethodError the instant the class loads — and javac never warns, because it
only ever saw readable MCP names.

Usage:  python3 verify_symbols.py <jar> <deobf.srg>
"""
import subprocess
import sys
import zipfile
from pathlib import Path

# Readable names that would prove the remap did not run.
MCP_NAMES = {
    "addInformation", "getTagCompound", "setTagCompound", "EnumChatFormatting",
    "getCurrentItem", "stackSize", "getDisplayName", "loadedEntityList",
    "playerEntities", "drawStringWithShadow", "glPushMatrix", "getEntityItem",
    "getRenderItem", "renderItemAndEffectIntoGUI", "getScaledWidth",
    "objectMouseOver", "getBoundingBox", "getEntityBoundingBox",
}


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    jar, srg = Path(sys.argv[1]), Path(sys.argv[2])

    known_methods, known_fields = set(), set()
    for line in srg.read_text(encoding="latin-1").split("\n"):
        p = line.split()
        if p[:1] == ["MD:"] and len(p) == 5:
            known_methods.add(p[3].rsplit("/", 1)[-1])
        elif p[:1] == ["FD:"] and len(p) == 3:
            known_fields.add(p[2].rsplit("/", 1)[-1])
    print(f"   {srg.name}: {len(known_methods)} methods, {len(known_fields)} fields")

    used_m, used_f, leaks, classes = set(), set(), set(), 0
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if not name.endswith(".class"):
                continue
            classes += 1
            out = subprocess.run(["strings", "-n", "4"], input=z.read(name),
                                 capture_output=True).stdout
            for s in (x.decode("latin-1") for x in out.split(b"\n")):
                if s.startswith("func_") and s.count("_") >= 2:
                    used_m.add(s)
                elif s.startswith("field_") and s.count("_") >= 2:
                    used_f.add(s)
                elif s in MCP_NAMES:
                    leaks.add(s)

    print(f"   jar: {classes} classes, {len(used_m)} srg methods, {len(used_f)} fields")

    bad_m = sorted(m for m in used_m if m not in known_methods)
    bad_f = sorted(f for f in used_f if f not in known_fields)

    failed = False
    if bad_m:
        print(f"   FAIL unknown SRG methods: {bad_m}")
        failed = True
    if bad_f:
        print(f"   FAIL unknown SRG fields: {bad_f}")
        failed = True
    if leaks:
        print(f"   FAIL readable MCP names leaked (remap did not run): {sorted(leaks)}")
        failed = True

    if not failed:
        print("   PASS every srg symbol exists in this version, no MCP name leaked")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
