#!/usr/bin/env bash
set -euo pipefail

# Run on Linux with Flutter 3.35.7, Go 1.27, Java 17 and Android NDK 28.
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
work_root="$repo_root/core/_work/android-app"
app_rev=56a66d01e87fe7cfb11f46eda8808d094c65f568
wrapper_rev=d8cbe8c42acbe5c35e6b31ced324f0f928322fc2
core_rev=ab405bad5beeeac8b003bb01f60f134f6df54471

fetch_revision() {
  local remote="$1" revision="$2" target="$3"
  if [[ ! -d "$target/.git" ]]; then
    mkdir -p "$target"
    git -C "$target" init -q
    git -C "$target" remote add origin "$remote"
  fi
  git -C "$target" fetch --depth 1 origin "$revision"
  git -C "$target" checkout --force --detach FETCH_HEAD
  [[ "$(git -C "$target" rev-parse HEAD)" == "$revision" ]]
}

mkdir -p "$work_root" "$repo_root/dist"
fetch_revision https://github.com/oviron/FlClash.git "$app_rev" "$work_root/app"
fetch_revision https://github.com/oviron/libmihomo-android.git "$wrapper_rev" "$work_root/wrapper"
fetch_revision https://github.com/MetaCubeX/mihomo.git "$core_rev" "$work_root/mihomo"

git -C "$work_root/app" apply "$repo_root/core/patches/flclash-android-lite.patch"
git -C "$work_root/mihomo" apply "$repo_root/core/patches/mihomo-reality-client-version.patch"

for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  cp "$repo_root/android/app/src/main/res/mipmap-$density/ic_launcher.png" \
    "$work_root/app/android/app/src/main/res/mipmap-$density/ic_launcher_lite.png"
done

go -C "$work_root/wrapper/src/main/jni/core" mod edit \
  -replace "github.com/metacubex/mihomo=$work_root/mihomo"

: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
export ANDROID_NDK="${ANDROID_NDK:-$ANDROID_HOME/ndk/28.0.13004108}"
[[ -d "$ANDROID_NDK" ]]

(
  cd "$work_root/wrapper"
  bash build-native.sh
  ./gradlew assembleRelease --no-daemon
)

aar="$work_root/wrapper/build/outputs/aar/libmihomo-android-release.aar"
[[ -s "$aar" ]]
mkdir -p "$work_root/app/android/core/libs"
cp "$aar" "$work_root/app/android/core/libs/libmihomo-android-v0.3.5.aar"

(
  cd "$work_root/app"
  printf '{"APP_ENV":"pre"}\n' > env.json
  flutter pub get
  flutter build apk --release --split-per-abi \
    --target-platform android-arm,android-arm64,android-x64 \
    --dart-define-from-file=env.json
)

for abi in armeabi-v7a arm64-v8a x86_64; do
  apk="$work_root/app/build/app/outputs/flutter-apk/app-$abi-release.apk"
  [[ -s "$apk" ]]
  cp "$apk" "$repo_root/dist/Clash-Mi-Lite-android-$abi.apk"
done

sha256sum "$repo_root"/dist/Clash-Mi-Lite-android-*.apk
