"""Resolve every external method a mod jar calls against the real jars.

This is the check that predicts NoSuchMethodError. A symbol-existence scan cannot
replace it: a call to a method that never existed is not in the constant pool of a
class that never compiled against it, so the scan is silent about exactly the
failure it is usually credited with catching.

javap prints a Methodref as:

    // Method net/minecraft/client/Minecraft.func_71410_x:()Lnet/minecraft/...;

Note the class and the method are joined by a DOT, while packages use SLASHES.
Splitting on the last '/' therefore yields the package, not the class, and every
lookup misses -- which silently reports "0 references checked, PASS". Split on the
last '/' first, then on the last '.' of the remainder.

Usage:  python3 check_calls.py <mod.jar> <cp1.jar>:<cp2.jar>:...
"""
import subprocess
import sys
import zipfile


def members_of(jar: str, cls: str):
    """{name: {descriptors}} for a class, or None when the class is not there."""
    try:
        out = subprocess.run(
            ["javap", "-p", "-s", "-cp", jar, cls.replace("/", ".")],
            capture_output=True, text=True, timeout=90)
    except Exception:
        return None
    if out.returncode != 0 or not out.stdout.strip():
        return None

    result = {}
    pending = None
    for raw in out.stdout.split("\n"):
        line = raw.strip()
        if line.startswith("descriptor:"):
            if pending:
                result.setdefault(pending, set()).add(line.split("descriptor:", 1)[1].strip())
                pending = None
            continue
        # A member signature line looks like: `public void foo(int);`
        if "(" in line and line.endswith(";") and not line.startswith("//"):
            head = line.split("(", 1)[0]
            parts = head.split()
            name = parts[-1] if parts else None
            # A constructor prints as `pkg.Cls(...)`; keep just the simple name.
            if name and "." in name:
                name = name.rsplit(".", 1)[1]
            pending = name
        elif not line:
            pending = None
    return result


def class_names(jar):
    try:
        with zipfile.ZipFile(jar) as z:
            return {n[:-6] for n in z.namelist() if n.endswith(".class")}
    except zipfile.BadZipFile:
        return set()


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    mod = sys.argv[1]
    cp_parts = sys.argv[2].split(":")

    available = {}
    for part in cp_parts:
        for c in class_names(part):
            available.setdefault(c, part)
    print("  %d classes across %d jars" % (len(available), len(cp_parts)))

    mine = class_names(mod)
    print("  mod has %d classes" % len(mine))

    checked, problems = 0, []
    for cls in sorted(mine):
        out = subprocess.run(
            ["javap", "-c", "-p", "-cp", ":".join([mod] + cp_parts), cls.replace("/", ".")],
            capture_output=True, text=True, timeout=90)
        if out.returncode != 0:
            problems.append((cls, "<class>", "not decompilable"))
            continue

        for line in out.stdout.split("\n"):
            marker = None
            if "// Method " in line:
                marker = "// Method "
            elif "// InterfaceMethod " in line:
                marker = "// InterfaceMethod "
            if not marker:
                continue
            ref = line.split(marker, 1)[1].strip()
            if ":" not in ref:
                continue
            target, sig = ref.split(":", 1)
            if "/" not in target:
                continue
            pkg, rest = target.rsplit("/", 1)
            if "." not in rest:
                continue
            owner = pkg + "/" + rest.rsplit(".", 1)[0]
            name = rest.rsplit(".", 1)[1]

            if owner.startswith("[") or owner not in available or owner in mine:
                continue      # JDK, an array type, or our own class

            checked += 1
            have = members_of(available[owner], owner)
            if have is None:
                problems.append((cls, "%s.%s" % (owner, name), "owner not resolvable"))
            elif name not in have:
                problems.append((cls, "%s.%s%s" % (owner, name, sig), "no such method on that class"))

    print("  resolved %d external method references" % checked)
    if checked == 0:
        print("  WARNING: zero references checked -- the parser is not matching, do not trust this pass")
        return 1
    if not problems:
        print("  PASS every external call resolves")
        return 0

    print("  FAIL %d unresolved (deduped):" % len(problems))
    seen = set()
    for cls, member, why in problems:
        if (member, why) in seen:
            continue
        seen.add((member, why))
        print("    %s\n      %s -- %s" % (cls, member, why))
    return 1


if __name__ == "__main__":
    sys.exit(main())
