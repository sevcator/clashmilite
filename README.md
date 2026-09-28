# Clash Mi Lite

This is a GPL-3.0 fork of [KaringX/clashmi](https://github.com/KaringX/clashmi). Its proxy core is based on [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo) v1.19.31, pinned to commit `ab405bad5beeeac8b003bb01f60f134f6df54471`.

## Core changes

- The REALITY ClientHello advertises `26.3.27` by default so it passes the default `minClientVer` check on Xray-core v26.7.11 and later. A proxy can override the three version bytes with `reality-opts.client-version`, for example `client-version: "1.8.2"`. This value describes the handshake compatibility setting; it does not claim the core is Xray-core or that it has all Xray security fixes.
- Automatic and manual Geo database downloads are disabled in the patched core. Existing local Geo files can still be used by rules. Missing or invalid files produce an error instead of a network request.
- The Flutter source no longer initializes upstream remote configuration, provider notices, or automatic app update checks. It no longer adds device ID, locale, referral, or install date query parameters to links or sends failed subscription downloads through the upstream provider proxy. The purchase entry in the profile menu is removed. Default extension Geo URLs are cleared.

Build a core executable with PowerShell and Go 1.27:

```powershell
./core/build.ps1 -TargetOS windows -TargetArch amd64
```

The script fetches the pinned Mihomo revision, verifies it, applies [the fork patch](core/patches/mihomo-reality-client-version.patch), and writes the executable to `dist/`. The GitHub Actions workflow builds Windows, Linux, macOS, and Android **core executables**.

## Flutter app build status

The original Clash Mi source published on GitHub does not include `libclash-vpn-service`, `board-service`, `lib/app/private`, the generated build metadata, or the native core bindings. Both package URLs in `pubspec.yaml` return 404. [The upstream issue](https://github.com/KaringX/clashmi/issues/350) says `libclash-vpn-service` is closed source. The original Flutter app cannot be compiled from this repository as published.

### Android replacement app

`core/build-android-app.sh` builds an installable Android app using pinned GPL-3.0 sources from [oviron/FlClash](https://github.com/oviron/FlClash) and [oviron/libmihomo-android](https://github.com/oviron/libmihomo-android). It compiles the Android wrapper against the same patched Mihomo v1.19.31 source used for the CLI builds and applies `core/patches/flclash-android-lite.patch` to the Flutter app. The replacement has a different interface from the original Clash Mi app.

The app patch names the app Clash Mi Lite, removes the automatic Geo update paths and IP information requests, removes the Geo download and core replacement screens, clears Geo URLs in the generated core configuration, and stops generating an `x-hwid` device identifier for Happ-format subscriptions. The bundled local GeoSite file remains available for rules. Profile subscription updates still contact only the URL the user adds. Some Happ-format providers require `x-hwid`; those subscriptions may need an explicit user-supplied header. The core also blocks Geo database downloads. Its home layout takes [Happ 4.4.1](core/HAPP_UI_REFERENCE.md) as a visual reference, with different buttons and typography.

GitHub Actions `Build Android APK` produces split APKs for arm64, armv7, and x86_64 in its `clash-mi-lite-android-apks` artifact. These are installable preview builds signed with the standard Android debug key and use the package ID `org.sevcator.clashmilite.dev`; they are not production signed. To build locally, install Flutter 3.35.7, Go 1.27, Java 17 and Android SDK with NDK 28.0.13004108, then run `bash core/build-android-app.sh` on Linux. APKs appear in `dist/`.

The [Android preview release](https://github.com/sevcator/clashmilite/releases/tag/v0.1.0-android-preview) contains direct APK downloads. Most phones use the `arm64-v8a` file. Uninstall an earlier preview before installing a new one because each CI run has a different debug signing key; export any profiles first.

No installable Windows, Linux, macOS or iOS app package is currently built. The core executables for those platforms are command line tools only.
