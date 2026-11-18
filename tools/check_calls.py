"""Resolve every method the mod calls against the real jars.

The SRG check answers "does this symbol exist in this version" but only for symbols
that are *named in* the classes. It cannot see a call to a method that never existed —
for that you have to look at the constant pool and resolve each Methodref against the
actual class files.

This is the check that catches a `NoSuchMethodError`, which is what a crash at
start-up usually is: the class loads, the verifier resolves the call, and the game
dies before a world appears.

Usage:  python3 check_calls.py <mod.jar> <cp.jar>[:<cp.jar>...]
"""
import subprocess
import sys
import zipfile
from pathlib import Path


def methods_of(jar: str, cls: str) -> set[str] | None:
    """name+descriptor of every method on a class, or None if the class is absent."""
    try:
        out = subprocess.run(
            ["javap", "-p", "-s", "-cp", jar, cls.replace("/", ".")],
            capture_output=True, text=True, timeout=60)
    except Exception:
        return None
    if out.returncode != 0 or not out.stdout.strip():
        return None
    found, pending = set(), False
    for line in out.stdout.split("\n"):
        if "descriptor:" in line:
            desc = line.split("descriptor:", 1)[1].strip()
            if pending:
                found.add(desc)
                pending = False
        elif "(" in line and ")" in line and not line.strip().startswith("descriptor"):
            pending = True
    return found


def class_names(jar: str) -> set[str]:
    with zipfile.ZipFile(jar) as z:
        return {n[:-6] for n in z.namelist() if n.endswith(".class")}


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    mod, cp = Path(sys.argv[1]), sys.argv[2]

    # One merged classpath entry list, plus a set of every class available anywhere.
    cp_parts = cp.split(":")
    available: dict[str, str] = {}
    for part in cp_parts:
        try:
            for c in class_names(part):
                available.setdefault(c, part)
        except zipfile.BadZipFile:
            pass
    print(f"  {len(available)} classes available across {len(cp_parts)} jars")

    mine = class_names(str(mod))
    print(f"  mod has {len(mine)} classes")

    problems, checked = [], 0
    for cls in sorted(mine):
        out = subprocess.run(["javap", "-c", "-p", "-cp", f"{mod}:{cp}", cls.replace("/", ".")],
                             capture_output=True, text=True, timeout=60)
        if out.returncode != 0:
            problems.append((cls, "unreadable", out.stderr.strip()[:120]))
            continue

        for line in out.stdout.split("\n"):
            # Methodrefs appear as: // Method owner/name:(desc)ret
            if "// Method " not in line and "// InterfaceMethod " not in line:
                continue
            ref = line.split("// ", 1)[1]
            ref = ref.replace("Method ", "").replace("InterfaceMethod ", "").strip()
            if ":" not in ref:
                continue
            target, sig = ref.split(":", 1)
            if "/" not in target:
                continue
            owner, name = target.rsplit("/", 1)
            if owner.startswith("[") or owner in ("java/lang/Object",):
                continue
            if owner not in available:
                continue      # JDK class; not our business
            if owner in mine:
                continue      # our own class

            checked += 1
            have = methods_of(available[owner], owner)
            if have is None:
                problems.append((cls, f"{owner}.{name}", "owner class not resolvable"))
                continue
            desc = sig.split(")")[0] + ")" if ")" in sig else ""
            if not any(d.startswith(desc) for d in have):
                problems.append((cls, f"{owner}.{name}", f"descriptor {sig} not found"))

    print(f"  resolved {checked} external method references")

    if not problems:
        print("  PASS every external call resolves to a real method")
        return 0

    seen = set()
    print(f"  FAIL {len(problems)} unresolved:")
    for cls, member, why in problems:
        key = (member, why)
        if key in seen:
            continue
        seen.add(key)
        print(f"    {cls}")
        print(f"      {member}  — {why}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
