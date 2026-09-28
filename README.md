# Mini Clash

Mini Clash is a small Android VPN and proxy manager built around a patched [Mihomo](https://github.com/MetaCubeX/mihomo) core. The Android interface is new Kotlin code. The old Clash Mi Flutter application and the temporary FlClash interface have been removed from this repository.

## Android app

- Import a Clash YAML/JSON profile, a common Xray JSON outbound profile, or a plain/Base64 subscription of VLESS, VMess, Trojan, Shadowsocks, Hysteria2, SOCKS5, HTTP proxy, and WireGuard links.
- Add and refresh URL subscriptions. Show provider title, description, traffic usage, expiration, and support URL when supplied by subscription headers or body metadata.
- Choose a server and add extra hops on the home screen. `GLOBAL → VLESS → WARP` configures WARP with `dialer-proxy: VLESS` in Mihomo.
- Start Android TUN with system VPN consent, or run only the local mixed HTTP/SOCKS proxy.
- Settings: randomized local port and proxy credentials when left blank, IPv6, profile key overwrite, log level (off by default), User Agent, optional HWID headers, and TUN.
- Profiles and settings are stored locally. Subscription requests go to URLs the user imports.

The HWID default is Android's `ANDROID_ID`, matching Happ 4.4.1's method. Android scopes this ID to each app signing key, so Mini Clash usually reads a **different value** from Happ on the same device. For a subscription already tied to Happ, copy the HWID shown in Happ into Mini Clash's override field. Mini Clash sends it only when **Send HWID to subscription** is enabled. The app sends `X-HWID`, `X-Device-ID`, and device headers; it does not reproduce Happ's private `X-Credential` token.

Current import scope: standard link lists, Clash YAML/JSON, and VLESS, VMess, Trojan, and Shadowsocks outbounds from Xray JSON. Plain `happ://add/` and `incy://add/` links and INCY's public `incy://crypt1` format are accepted. Encrypted `happ://crypt*` links, advanced Xray JSON routing, and AmneziaWG links need separate support and are not accepted yet. Do not expect providers that require those formats or Happ's private credential token to work. No Android device or emulator connection run has been completed yet.

## Build Android APK

The build requires Java 17, Go 1.27, Android SDK platform 36, NDK 28.0.13004108, and CMake 3.22.1. From Linux:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
bash core/build-android-app.sh
```

The script fetches pinned [`libmihomo-android`](https://github.com/oviron/libmihomo-android) and Mihomo revisions, applies the core patch, builds the native library and Android app, then writes `dist/Mini-Clash-android-universal.apk`. The APK is a preview signed with the standard Android debug key. The [GitHub Actions workflow](.github/workflows/build-android-app.yml) runs the same build and uploads the APK as an artifact.

## Core

The core is pinned to Mihomo v1.19.31 commit `ab405bad5beeeac8b003bb01f60f134f6df54471`. The [patch](core/patches/mihomo-reality-client-version.patch) sets the default REALITY client handshake version to `26.3.27`, lets profiles override it, and disables Geo database downloads. It does not claim that Mihomo is Xray-core or inherits Xray's security fixes. Existing local Geo data can still be used.

Build a standalone core executable with PowerShell and Go 1.27:

```powershell
./core/build.ps1 -TargetOS windows -TargetArch amd64
```

The [core workflow](.github/workflows/build-core.yml) builds Windows, Linux, macOS, and Android core binaries. Mini Clash currently has an Android interface only.

## Research and license

Subscription metadata and HWID headers were checked against the [Happ integration docs](https://github.com/HappDev/happ_su/tree/main/dev-docs), [INCY integration docs](https://docs.incy.cc/en/subscription-format/), and the official Android APKs. Chain behavior was compared with [NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid). Proprietary app code and assets are not included. Mini Clash, the Mihomo core, and `libmihomo-android` are distributed under GPL-3.0; see [LICENSE](LICENSE).
