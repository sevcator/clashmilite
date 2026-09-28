# Android UI reference

The visual reference is the official [Happ Android 4.4.1 release](https://github.com/Happ-proxy/happ-android/releases/tag/4.4.1), asset `Happ.apk`. Its SHA-256 is `31457b085e9df2cfef846e28fbf39c6040baa4a1fee8328f085adeb9176ad9405`.

I decoded the APK's resources with Apktool 3.0.3 and inspected `res/layout/activity_main.xml` and the light/dark app themes. The home layout puts settings and add actions in the top bar, a roughly 217 dp power control near the upper center, and clipboard and QR import actions near the bottom when no configuration is present. Its light theme uses a pale neutral background with purple accents, including `#7c71ff`; the dark theme uses a deep indigo background around `#211f53`.

The Android app patch follows this screen structure. Its buttons have a new rounded rectangular treatment and the bundled JetBrains Mono font replaces the Happ font. The launcher icon is copied from this repository's original Clash Mi source. The original Happ code, images, and APK are not part of this repository. The existing FlClash navigation and proxy screens remain available so the app's functions stay accessible.
