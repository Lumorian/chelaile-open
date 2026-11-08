#!/usr/bin/env bash
# 手动打包脚本：不依赖 Gradle/AGP，只用 Android SDK 的 aapt2 / javac / d8 / zipalign / apksigner。
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT="build"
VERSION_NAME="0.2.2"
VERSION_CODE="4"

if [ ! -x "$BT/aapt2" ]; then
  echo "找不到 Android SDK：$SDK（需要 build-tools 与 platforms;android-34）" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/res"

echo "==> 使用 build-tools: $(basename "$BT")"
echo "==> 编译资源"
"$BT/aapt2" compile --dir res -o "$OUT/res/resources.zip"
"$BT/aapt2" link   -o "$OUT/base.apk"   -I "$PLATFORM"   --manifest AndroidManifest.xml   --java "$OUT/gen"   --min-sdk-version 24   --target-sdk-version 34   --version-code "$VERSION_CODE"   --version-name "$VERSION_NAME"   "$OUT/res/resources.zip"

echo "==> 编译 Java"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 11 -encoding UTF-8 -classpath "$PLATFORM" -d "$OUT/classes" @"$OUT/sources.txt"

echo "==> dex"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" @"$OUT/classes.txt"

echo "==> 组装 APK"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KEYSTORE="keystore/debug.keystore"
mkdir -p keystore
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/chelaile-open-$VERSION_NAME.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT/chelaile-open-$VERSION_NAME.apk" | head -3
echo "OK: $OUT/chelaile-open-$VERSION_NAME.apk"
