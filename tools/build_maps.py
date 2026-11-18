"""Build the mapping a 1.8.9 mod needs, without ForgeGradle.

Forge 1.8.9 runs vanilla classes under their SRG names (func_77624_a), while mods
are written against MCP names (addInformation). ForgeGradle normally bridges the
two; on this host it cannot, because Gradle 2.14/4.10 SIGSEGV inside glibc's
setenv (see hs_err logs) before any task runs.

So the bridge is built by hand, from data Forge already ships:

  deobfuscation_data-1.8.9.lzma   obf  -> srg      (from forge-*-universal.jar)
  mcp_stable-22-1.8.9/methods.csv srg  -> mcp      (the names people actually write)

Two maps come out of it:

  readable.srg   obf -> mcp   for decompiling/reference (FART --map)
  reverse.srg    mcp -> srg   for compiling a readable mod and shipping it

Descriptors have to be remapped too: a descriptor carries class names
(La; -> Lnet/minecraft/util/EnumChatFormatting;) and FART matches on the whole
key, so leaving them alone silently produces no matches at all.
"""
import csv
import json
import lzma
import re
import subprocess
import sys
from pathlib import Path

T = Path("/home/zerops/minecraft-mod/tools")


def read_csv(name: str) -> dict[str, str]:
    """searge -> readable, from an MCP csv. Tolerates the CRLF/quote soup."""
    path = T / "mcp" / name
    out: dict[str, str] = {}
    with path.open(encoding="latin-1", errors="ignore", newline="") as fh:
        for row in csv.DictReader(fh):
            s, n = (row.get("searge") or "").strip(), (row.get("name") or "").strip()
            if s and n and n != s:
                out[s] = n
    return out


def unzip_member(jar: Path, member: str, dest: Path) -> bytes:
    raw = subprocess.run(
        ["unzip", "-p", str(jar), member], capture_output=True, check=True).stdout
    dest.write_bytes(raw)
    return raw


def main() -> int:
    methods = read_csv("methods.csv")
    fields = read_csv("fields.csv")
    print(f"  mcp csv: {len(methods)} methods, {len(fields)} fields")

    # ---- obf -> srg, straight from the lzma Forge ships -------------------
    lz = T / "deobfuscation_data-1.8.9.lzma"
    if not lz.exists():
        unzip_member(T / "forge-universal.jar", lz.name, lz)
    srg_text = lzma.open(lz, "rb").read().decode("latin-1")

    class_map: dict[str, str] = {}                     # obf class -> srg class
    srg_md: list[tuple[str, str, str, str, str]] = []  # owner, name, desc, newOwner, newName
    srg_fd: list[tuple[str, str, str, str]] = []       # owner, name, newOwner, newName

    for line in (l.strip() for l in srg_text.split("\n")):
        if not line:
            continue
        p = line.split()
        if p[0] == "CL:" and len(p) == 3:
            class_map[p[1]] = p[2]
        elif p[0] == "MD:" and len(p) == 5:
            so, sn = p[1].rsplit("/", 1) if "/" in p[1] else ("", p[1])
            do, dn = p[3].rsplit("/", 1) if "/" in p[3] else ("", p[3])
            srg_md.append((so, sn, p[2], do, dn))
        elif p[0] == "FD:" and len(p) == 3:
            so, sn = p[1].rsplit("/", 1) if "/" in p[1] else ("", p[1])
            do, dn = p[2].rsplit("/", 1) if "/" in p[2] else ("", p[2])
            srg_fd.append((so, sn, do, dn))

    print(f"  srg: {len(class_map)} classes, {len(srg_md)} methods, {len(srg_fd)} fields")

    classre = re.compile(r"L([^;]+);")

    def remap_desc(desc: str) -> str:
        return classre.sub(lambda m: "L" + class_map.get(m.group(1), m.group(1)) + ";", desc)

    # ---- the two output maps --------------------------------------------
    readable: list[str] = []   # obf -> mcp   (for FART on vanilla)
    reverse: list[str] = []    # mcp -> srg   (for FART on the finished mod)

    for obf, srg in sorted(class_map.items()):
        readable.append(f"CL: {obf} {srg}")
    # No `CL: srg srg` lines here on purpose: a name mapped to itself makes FART's
    # EnhancedRemapper build a node for a class it then cannot resolve, and the whole
    # remap dies with an NPE deep inside computeClass. Identity mappings are the
    # absence of a mapping, not a mapping.

    matched_m = matched_f = 0
    for so, sn, sdesc, do, dn in srg_md:
        srg_owner = class_map.get(so, so)
        mcp_name = methods.get(dn)
        desc = remap_desc(sdesc)
        # readable view of the source, for the decompile map
        readable.append(f"MD: {class_map.get(so, so)}/{mcp_name or sn} {desc} "
                        f"{srg_owner}/{dn} {remap_desc(sdesc)}")
        if mcp_name:
            matched_m += 1
            # the reverse map must be keyed on what the compiler will emit
            reverse.append(f"MD: {srg_owner}/{mcp_name} {desc} {srg_owner}/{dn} {desc}")

    for so, sn, do, dn in srg_fd:
        srg_owner = class_map.get(so, so)
        mcp_name = fields.get(dn)
        if mcp_name:
            matched_f += 1
            reverse.append(f"FD: {srg_owner}/{mcp_name} {srg_owner}/{dn}")

    print(f"  renamed on the way out: {matched_m} methods, {matched_f} fields")
    (T / "reverse.srg").write_text("\n".join(reverse) + "\n")

    # A readable map for FART --input vanilla --output readable.
    # The source is mc-deobf.jar, which already carries SRG names (func_175744_a),
    # so the map must be keyed on the SRG name -- not on the obfuscated one. Keying
    # it on the obf name silently matches nothing and leaves every method unreadable.
    parser: list[str] = []
    for so, sn, sdesc, do, dn in srg_md:
        mcp_name = methods.get(dn)
        if mcp_name:
            o = class_map.get(so, so)
            parser.append(f"MD: {o}/{dn} {remap_desc(sdesc)} {o}/{mcp_name} {remap_desc(sdesc)}")
    for so, sn, do, dn in srg_fd:
        mcp_name = fields.get(dn)
        if mcp_name:
            o = class_map.get(so, so)
            parser.append(f"FD: {o}/{dn} {o}/{mcp_name}")
    for obf, srg in sorted(class_map.items()):
        parser.append(f"CL: {obf} {srg}")
    (T / "readable.srg").write_text("\n".join(parser) + "\n")

    print(f"  wrote reverse.srg ({len(reverse)} lines), readable.srg ({len(parser)} lines)")

    # A map for the Forge jar itself. Forge ships obfuscated (field `zx itemStack`)
    # and FML remaps it at runtime; to compile against it, remap it to MCP once.
    obf2mcp: list[str] = []
    for so, sn, sdesc, do, dn in srg_md:
        mcp_name = methods.get(dn)
        if mcp_name:
            o = class_map.get(so, so)
            obf2mcp.append(f"MD: {so}/{sn} {sdesc} {o}/{mcp_name} {remap_desc(sdesc)}")
    for so, sn, do, dn in srg_fd:
        mcp_name = fields.get(dn)
        if mcp_name:
            obf2mcp.append(f"FD: {so}/{sn} {class_map.get(so, so)}/{mcp_name}")
    for obf, srg in sorted(class_map.items()):
        obf2mcp.append(f"CL: {obf} {srg}")
    (T / "obf2mcp.srg").write_text("\n".join(obf2mcp) + "\n")
    print(f"  wrote obf2mcp.srg ({len(obf2mcp)} lines) for the forge jar")
    return 0


if __name__ == "__main__":
    sys.exit(main())
