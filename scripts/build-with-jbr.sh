#!/bin/zsh
# 免 Gradle 快速构建：用本机 IDEA 自带 JBR 的 javac 直接编译（约 2 秒）。
# 正式构建/校验请用 ./gradlew buildPlugin verifyPlugin（CI 就是这么跑的）。
# 产物：out/selection-bridge-for-codex/（插件目录）和 out/selection-bridge-for-codex.zip
set -euo pipefail

IDEA_HOME="${IDEA_HOME:-$HOME/Applications/IntelliJ IDEA.app/Contents}"
JBR_BIN="$IDEA_HOME/jbr/Contents/Home/bin"
DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$DIR/out"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

"$JBR_BIN/javac" -encoding UTF-8 \
    -cp "$IDEA_HOME/lib/*:$IDEA_HOME/lib/modules/*" \
    -d "$OUT/classes" \
    "$DIR"/src/main/java/life/irony/codexbridge/*.java

cp -R "$DIR/src/main/resources/META-INF" "$OUT/classes/"

# JBR 不带 jar 命令；jar 即 zip 格式，用 zip 打包等效
mkdir -p "$OUT/selection-bridge-for-codex/lib"
(cd "$OUT/classes" && zip -qr "$OUT/selection-bridge-for-codex/lib/selection-bridge-for-codex.jar" .)

(cd "$OUT" && zip -qr selection-bridge-for-codex.zip selection-bridge-for-codex)
echo "Built: $OUT/selection-bridge-for-codex.zip"
