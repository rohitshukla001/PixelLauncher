# Developer guide

How to build, run and work on Pixel Launcher.

## Requirements

- JDK 17
- Android SDK with platform 35 (compile/target SDK 35, min SDK 26)
- An emulator or device running Android 8.0+ for manual testing

Set the SDK location in `local.properties` (not committed):

```properties
sdk.dir=/path/to/Android/sdk
```

## Build and test

```bash
./gradlew assembleDebug        # debug APK, also copied to apk/PixelLauncher.apk
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew lintDebug            # Android lint
```

Every `assembleDebug` copies the APK to `apk/PixelLauncher.apk` (an `exportApk` task), so the `apk/` folder always holds the latest debug build.

## Run on a device

1. `./gradlew assembleDebug`
2. `adb install -r apk/PixelLauncher.apk`
3. Press Home and choose Pixel Launcher, or set it in Settings → Apps → Default apps → Home app.

## Project structure

| Path | Contents |
| --- | --- |
| `MainActivity.kt` | The home-screen activity: registers as Home, wires repositories to `LauncherScreen`, implements `LauncherController`. |
| `data/` | App list (all profiles), home layout + dock, settings, launch counts, hidden apps, pattern hashing, icon cache. Plain Kotlin where possible so logic is unit-testable. |
| `ui/` | Compose home screen, app drawer, drag-and-drop, clock, home header, wallpaper background. |
| `settings/` | Settings screens, hidden-apps flow, pattern pad, per-app language. |
| `search/` | In-app search results screen (WebView), with external links sent to the browser. |
| `wallpaper/` | Built-in code-drawn wallpapers, bundled photo wallpapers, the chooser and the crop screen. |

Repositories are created once per process in `data/AppData.kt` and persist with Jetpack DataStore (Preferences).

## Adding wallpapers

Drop image files (`.jpg`, `.png`, `.webp`) into `app/src/main/assets/wallpapers/`. They appear in the chooser before the code-drawn designs, in alphabetical order, centre-cropped to the screen. Use portrait images at least ~1080×2400. Only add images you have the right to distribute.

## Release signing

Release builds shrink with R8 and are signed only when `keystore.properties` exists at the project root (that file and `*.jks` are git-ignored).

```bash
keytool -genkeypair -v -keystore pixellauncher-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias pixellauncher
```

```properties
# keystore.properties
storeFile=pixellauncher-release.jks
storePassword=your-store-password
keyAlias=pixellauncher
keyPassword=your-key-password
```

```bash
./gradlew assembleRelease
```

## Versioning

Bump `versionCode`/`versionName` in `app/build.gradle.kts` for each build handed out for testing, so a tester can tell builds apart. The version is not shown in the UI.

## The decompiled reference

`base/`, `jadx/`, `xapk/` and `manifest.txt` hold a decompiled copy of Yomo Launcher, used only as a feature reference. They are git-ignored and must never be committed. None of that code, its assets or branding is used in this app.
