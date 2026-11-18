#!/bin/bash
# Build PreSight for Minecraft 1.8 + Forge — the version the owner actually plays.
#
# 1.8 and 1.8.9 are not a patch bump: their SRG maps differ, so a jar built against one
# throws NoSuchMethodError on the other. This script exists so the 1.8 artefact is produced
# by the same three steps as the 1.8.9 one, against 1.8 inputs:
#
#   compile : against mc-readable18.jar (1.8 deobfuscated to MCP names) + forge18
#   remap   : MCP -> SRG for 1.8, with FART
#   verify  : every SRG symbol must exist in 1.8's own map
#
# Gradle is not used: the ForgeGradle of this era SIGSEGVs on a current glibc.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
T="$ROOT/tools"
V18="$T/v18"
JAVA="$T/jdk8u512-b09/bin"
FART="$T/fart.jar"

MC="$V18/mc-readable18.jar"
FORGE="$V18/forge18-universal.jar"
LWJGL="$T/lwjgl.jar"
LWJGL_UTIL="$T/lwjgl_util.jar"
GUAVA="$T/guava.jar"
REVERSE18="$V18/reverse18.srg"

CP="$MC:$FORGE:$LWJGL:$LWJGL_UTIL:$GUAVA"
OUT="$ROOT/build18"
CLASSES="$OUT/classes"
REOBF="$OUT/reobf"
STAGE="$OUT/stage"
JAR="$OUT/presight-${VERSION:-1.6.1}-1.8.jar"

for f in "$MC" "$FORGE" "$REVERSE18"; do
    [ -f "$f" ] || { echo "missing input: $f"; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$CLASSES" "$REOBF" "$STAGE"

echo "== 1/3 compile against 1.8 (MCP names) =="
find "$ROOT/src/main/java" -name '*.java' > "$OUT/sources.txt"
"$JAVA/javac" -Xlint:-options -source 1.8 -target 1.8 \
    -cp "$CP" -d "$CLASSES" @"$OUT/sources.txt"
echo "   $(find "$CLASSES" -name '*.class' | wc -l) classes"

echo "== 2/3 remap MCP -> SRG for 1.8 =="
# FART resolves the class hierarchy as it renames; a method it cannot attribute throws NPE
# in EnhancedRemapper. Remapping inside a jar that also holds Minecraft avoids that, and
# only our own package is taken back out afterwards.
WORK="$OUT/work"
mkdir -p "$WORK"
cp "$MC" "$WORK/combined.jar"
( cd "$WORK" && "$JAVA/jar" uf combined.jar -C "$CLASSES" . )
"$JAVA/java" -Xmx1500M -jar "$FART" \
    --input "$WORK/combined.jar" --output "$OUT/reobf-all.jar" \
    --map "$REVERSE18" --threads 2 >/dev/null
( cd "$REOBF" && "$JAVA/jar" xf "$OUT/reobf-all.jar" com/mohamed )
echo "   $(find "$REOBF" -name '*.class' | wc -l) classes remapped"

echo "== 3/3 pack =="
cp -r "$REOBF"/. "$STAGE"/
cp -r "$ROOT/src/main/resources"/. "$STAGE"/
( cd "$STAGE" && "$JAVA/jar" cf "$JAR" . )
echo "   $(basename "$JAR") $(stat -c%s "$JAR") bytes"

echo
echo "== verify against BOTH 1.8 and 1.8.9 maps =="
python3 "$ROOT/tools/check_both.py" "$JAR" "$V18/deobf18.srg" "$T/deobf.srg" || {
    echo "   FAILED — see above"
    exit 1
}
