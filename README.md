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

The original Clash Mi source published on GitHub does not include `libclash-vpn-service`, `board-service`, `lib/app/private`, the generated build metadata, or the native core bindings. Both package URLs in `pubspec.yaml` return 404. [The upstream issue](https://github.com/KaringX/clashmi/issues/350) says `libclash-vpn-service` is closed source. The Android native library in the published source is also absent. Consequently the Flutter app cannot currently be compiled from this repository on any target. The core executables above are not installable Flutter apps or mobile VPN packages.

To make installable apps, these components need open replacements with equivalent Flutter and native APIs, followed by platform builds and signing on their respective toolchains. No release is claimed until those builds succeed.
