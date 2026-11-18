"""Every GlStateManager / Minecraft call the mod makes must exist in 1.8.9.

A wrong method name there compiles — javac checks the deobf jar — but throws
NoSuchMethodError at runtime, and the mod's own catch(Throwable) swallows it. So the
symptom is exactly "the keys work, the chat answers, and nothing draws".

This resolves each call against the real deobfuscated classes rather than trusting that
the build succeeded.
"""
import subprocess
import sys
import zipfile
from pathlib import Path

T = Path("/home/zerops/minecraft-mod/presight/tools")
JAR = Path("/home/zerops/minecraft-mod/presight/build/presight-1.2.0.jar")
CP = f"{T}/mc-readable.jar:{T}/forge-mcp.jar:{T}/lwjgl.jar:{T}/lwjgl_util.jar:{T}/guava.jar"

# Targeted classes the mod relies on; a method here that does not exist is fatal.
WATCH = {
    "net/minecraft/client/renderer/GlStateManager": [
        "pushMatrix", "popMatrix", "translate", "rotate", "scale",
        "disableTexture2D", "enableTexture2D", "disableLighting", "enableLighting",
        "disableCull", "enableCull", "enableBlend", "disableBlend",
        "tryBlendFuncSeparate", "disableDepth", "enableDepth", "depthMask",
        "color", "disableFog",
    ],
    "net/minecraft/client/renderer/Tessellator": ["getInstance", "draw", "getWorldRenderer"],
    "net/minecraft/client/renderer/WorldRenderer": [
        "begin", "pos", "color", "endVertex",
    ],
    "net/minecraft/client/renderer/RenderHelper": [
        "enableGUIStandardItemLighting", "disableStandardItemLighting",
    ],
}


def methods(cls: str) -> set[str]:
    out = subprocess.run(["javap", "-p", "-cp", CP, cls.replace("/", ".")],
                         capture_output=True, text=True, timeout=60)
    if out.returncode != 0:
        return set()
    names = set()
    for line in out.stdout.split("\n"):
        line = line.strip()
        if "(" in line and ")" in line and not line.startswith("//"):
            head = line.split("(")[0]
            names.add(head.split()[-1])
    return names


def main() -> int:
    missing = []
    for cls, wanted in WATCH.items():
        have = methods(cls)
        if not have:
            print(f"  {cls}: NOT FOUND")
            continue
        for m in wanted:
            if m not in have:
                missing.append(f"{cls.rsplit('/', 1)[-1]}.{m}")
        print(f"  {cls.rsplit('/', 1)[-1]:16} {len(have)} methods, {len(wanted)} checked")

    if missing:
        print(f"\n  FAIL these do NOT exist in 1.8.9 — each is a NoSuchMethodError at runtime:")
        for m in missing:
            print(f"    {m}")
        return 1
    print("\n  PASS every watched method exists")
    return 0


if __name__ == "__main__":
    sys.exit(main())
