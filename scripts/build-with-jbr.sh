#!/bin/zsh
# 免 Gradle 快速构建：用本机 IDEA 自带 JBR 的 javac 直接编译（约 2 秒）。
# 正式构建/校验请用 ./gradlew buildPlugin verifyPlugin（CI 就是这么跑的）。
# 产物：out/mcp-selection-bridge/（插件目录）和 out/mcp-selection-bridge.zip
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
    "$DIR"/src/main/java/life/irony/selectionbridge/*.java

cp -R "$DIR/src/main/resources/META-INF" "$OUT/classes/"

# JBR 不带 jar 命令；jar 即 zip 格式，用 zip 打包等效
mkdir -p "$OUT/mcp-selection-bridge/lib"
(cd "$OUT/classes" && zip -qr "$OUT/mcp-selection-bridge/lib/mcp-selection-bridge.jar" .)

(cd "$OUT" && zip -qr mcp-selection-bridge.zip mcp-selection-bridge)
echo "Built: $OUT/mcp-selection-bridge.zip"
