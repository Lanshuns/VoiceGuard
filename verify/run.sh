#!/usr/bin/env bash
# Runs the AutoMuteService logic checks outside Minecraft.
# Usage: ./gradlew :core:classes && verify/run.sh
set -euo pipefail

cd "$(dirname "$0")/.."

CLASSES="core/build/classes/java/main"
if [ ! -d "$CLASSES" ]; then
  echo "Build first:  ./gradlew :core:classes" >&2
  exit 1
fi

# Everything the addon compiles against: the LabyMod API and the resolved VoiceChat addon.
CP="$CLASSES"
while IFS= read -r jar; do
  CP="$CP:$jar"
# All of net.labymod.labymod4, not just api-*: the config now references types (Key,
# VersionComparison) that live in the util-* artifacts.
done < <(find ~/.gradle/caches -name "*.jar" \
  \( -path "*net.labymod.labymod4*" -o -path "*flint*voicechat*" -o -name "voicechat*.jar" \
     -o -name "gson-*.jar" \) \
  -not -name "*sources*" 2>/dev/null | sort -u)

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

javac -nowarn -proc:none -cp "$CP" -d "$OUT" verify/AutoMuteVerification.java
java -cp "$CP:$OUT" AutoMuteVerification
