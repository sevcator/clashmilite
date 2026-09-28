#!/usr/bin/env bash
set -euo pipefail

# Build Mini Clash for Android with the patched Mihomo core.
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
work_root="$repo_root/core/_work/mini-clash"
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

mkdir -p "$work_root" "$repo_root/mini-clash/app/libs" "$repo_root/dist"
fetch_revision https://github.com/oviron/libmihomo-android.git "$wrapper_rev" "$work_root/wrapper"
fetch_revision https://github.com/MetaCubeX/mihomo.git "$core_rev" "$work_root/mihomo"

git -C "$work_root/mihomo" apply "$repo_root/core/patches/mihomo-reality-client-version.patch"

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
cp "$aar" "$repo_root/mini-clash/app/libs/libmihomo-android.aar"

(
  cd "$repo_root/mini-clash"
  ./gradlew :app:assembleRelease --no-daemon
)

apk="$repo_root/mini-clash/app/build/outputs/apk/release/app-release.apk"
[[ -s "$apk" ]]
cp "$apk" "$repo_root/dist/Mini-Clash-android-universal.apk"
sha256sum "$repo_root/dist/Mini-Clash-android-universal.apk"
