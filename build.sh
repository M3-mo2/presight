#!/bin/bash
# Build PreSight for Minecraft 1.8.9 + Forge, without Gradle.
#
# Gradle 2.14 (what the 2017 MDK ships) and 4.10.3 both SIGSEGV inside glibc's
# setenv from native-platform.so before any task runs, so ForgeGradle is not an
# option on a current host. The two translations it performs are done explicitly:
#
#   1. compile against deobfuscated Minecraft + Forge, using readable MCP names
#   2. remap the classes to SRG names — this is what ForgeGradle's "reobf" does and
#      what makes the jar load: Forge 1.8.9 runs vanilla under SRG names, so a jar
#      holding readable names throws NoSuchMethodError at runtime, not at build time.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
T="$ROOT/tools"
JAVA="$T/jdk8u512-b09/bin"
FART="$T/fart.jar"
MC="$T/mc-readable.jar"
FORGE="$T/forge-mcp.jar"
LWJGL="$T/lwjgl.jar"
LWJGL_UTIL="$T/lwjgl_util.jar"
REVERSE="$T/reverse.srg"

GUAVA="$T/guava.jar"
CP="$MC:$FORGE:$LWJGL:$LWJGL_UTIL:$GUAVA"
OUT="$ROOT/build"
CLASSES="$OUT/classes"
REOBF="$OUT/reobf"
STAGE="$OUT/stage"
JAR="$OUT/presight-1.13.2.jar"

rm -rf "$OUT"
mkdir -p "$CLASSES" "$REOBF" "$STAGE"

echo "== 1/3 compile (MCP names) =="
find "$ROOT/src/main/java" -name '*.java' > "$OUT/sources.txt"
"$JAVA/javac" -Xlint:-options -source 1.8 -target 1.8 \
    -cp "$CP" -d "$CLASSES" @"$OUT/sources.txt"
echo "   $(find "$CLASSES" -name '*.class' | wc -l) classes"

echo "== 2/3 remap to SRG names =="
# FART resolves the class hierarchy as it renames, and a method it cannot attribute
# to a class in its input throws NPE deep in EnhancedRemapper. So the mod is remapped
# inside a jar that also contains Minecraft, and only our classes are taken back out.
WORK="$OUT/work"
mkdir -p "$WORK"
cp "$MC" "$WORK/combined.jar"
( cd "$WORK" && "$JAVA/jar" uf combined.jar -C "$CLASSES" . )
"$JAVA/java" -Xmx1200M -jar "$FART" \
    --input "$WORK/combined.jar" --output "$OUT/reobf-all.jar" --map "$REVERSE" --threads 2 >/dev/null
( cd "$REOBF" && "$JAVA/jar" xf "$OUT/reobf-all.jar" com/mohamed )
echo "   $(find "$REOBF" -name '*.class' | wc -l) classes remapped"

echo "== 3/3 pack =="
cp -r "$REOBF"/. "$STAGE"/
cp -r "$ROOT/src/main/resources"/. "$STAGE"/
( cd "$STAGE" && "$JAVA/jar" cf "$JAR" . )
echo "   $(basename "$JAR") $(stat -c%s "$JAR") bytes"

echo
echo "== verify =="
if python3 "$ROOT/tools/verify_symbols.py" "$JAR" "$T/deobf.srg"; then
    :
else
    echo "   FAILED — see above"
    exit 1
fi
