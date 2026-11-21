#!/usr/bin/env python3
"""Every vanilla call must be a real method that the owner can actually reach.

check_calls.py resolves Methodrefs against a jar, but the jars in tools/ are MCP-named, so
SRG names are simply absent there and every call reads as "no such method" — three false
positives in a row. The map is the authority on which methods exist: readable.srg pairs each
SRG name with its descriptor and its declaring class.

A call is legal in two cases, and only these:
  * the owner is the declaring class; or
  * the owner is a subclass of the declaring class (an inherited call — ordinary Java, and
    it is why `EntityPlayer.getHealth()` legitimately appears as a call on EntityPlayer).

Anything else is a name reached for on the wrong class: it survives the remap, compiles
cleanly, and throws NoSuchMethodError the first time the player triggers that path.

Usage: python3 check_ownership.py <mod.jar> <readable.srg> <readable.jar>
"""

import re
import subprocess
import sys
import zipfile
from collections import defaultdict

CALL = re.compile(r"// Method net/minecraft/([^.\s]+)\.([A-Za-z0-9_$]+):(\S+)")

# Methods that belong to java.lang / java.util interfaces the vanilla class implements, not to
# Minecraft. They are never remapped because they are not vanilla names at all, so seeing one in
# the bytecode is correct — CommandBase.compareTo is Comparable's, not an SRG method.
JDK_METHODS = {"compareTo", "equals", "hashCode", "toString", "clone",
               "apply", "accept", "test", "get", "run"}
SUPER = re.compile(r"^\S.*class\s+(\S+)(?:\s+extends\s+([^\s{]+))?")


def load_map(srg_path):
    """(srg name, descriptor) -> set of classes that declare it."""
    declared = defaultdict(set)
    with open(srg_path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if not line.startswith("MD: "):
                continue
            parts = line[4:].split()
            if len(parts) < 2:
                continue
            owner, _, name = parts[0].rpartition("/")
            declared[(name, parts[1])].add(owner.replace("net/minecraft/", "", 1))
    return declared


class Hierarchy:
    """Superclass chain, read lazily from the readable jar."""

    def __init__(self, jar):
        self.jar = jar
        self.cache = {}

    def parent(self, cls):
        if cls in self.cache:
            return self.cache[cls]
        # cls arrives as "client/entity/EntityPlayerSP"; javap needs the full name.
        fqcn = cls.replace("/", ".") if cls.startswith("net/minecraft/") \
            else "net.minecraft." + cls.replace("/", ".")
        out = subprocess.run(["javap", "-p", "-cp", self.jar, fqcn],
                             capture_output=True, text=True).stdout
        parent = None
        for line in out.splitlines():
            m = SUPER.match(line.strip())
            if m:
                if m.group(2):
                    parent = m.group(2).replace(".", "/").replace("net/minecraft/", "", 1)
                break
        self.cache[cls] = parent
        return parent

    def ancestors(self, cls):
        seen, cur = set(), cls
        while True:
            cur = self.parent(cur)
            if not cur or cur in seen:
                return seen
            seen.add(cur)


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        return 2
    jar, srg, cp = sys.argv[1], sys.argv[2], sys.argv[3]
    declared = load_map(srg)
    hier = Hierarchy(cp)

    names = [n[:-6] for n in zipfile.ZipFile(jar).namelist() if n.endswith(".class")]
    checked, bad = 0, []

    for cls in names:
        out = subprocess.run(["javap", "-p", "-c", "-cp", jar, cls.replace("/", ".")],
                             capture_output=True, text=True).stdout
        for owner, name, desc in CALL.findall(out):
            checked += 1
            ref = f"net/minecraft/{owner}.{name}"
            if name in JDK_METHODS:
                continue
            if not name.startswith(("func_", "field_")):
                bad.append((cls, ref, "readable name survived the remap"))
                continue
            where = declared.get((name, desc))
            if not where:
                bad.append((cls, ref, "no method with that name and descriptor in the map"))
                continue
            if owner in where or where & hier.ancestors(owner):
                continue
            bad.append((cls, ref, "declared on " + ", ".join(sorted(where))))

    print(f"  {len(names)} classes, {checked} vanilla method references")
    if checked == 0:
        print("  FAIL nothing was checked — the jar or the filter is wrong")
        return 1
    for cls, ref, why in sorted(set(bad)):
        print(f"  FAIL {cls}\n       {ref} -- {why}")
    if bad:
        return 1
    print("  PASS every vanilla call resolves on a class the owner inherits from")
    return 0


if __name__ == "__main__":
    sys.exit(main())
