# Pixel Launcher

A minimal Android home screen in Kotlin and Jetpack Compose, modelled on the features of Yomo Launcher.

Yomo's documented behaviour (its FAQ, on-screen text and observable behaviour) is the spec. None of Yomo's code, images or branding is used. The decompiled Yomo reference in `base/`, `jadx/`, `xapk/` and `manifest.txt` is for reading only and is excluded from git.

> The name is a working title. Google's own launcher on Pixel phones is also called "Pixel Launcher", and "Pixel" is a Google trademark. Pick a different name before publishing.

## Features

- **Home:** pages with dots, a dock with the phone's default dialer, messages, browser and camera, and a clock (left, centre or right) with an optional search bar on page 1.
- **App drawer:** swipe up. A–Z list of apps from every profile (work apps carry the work badge), search that ignores case and accents, a most-used Suggested row, and "Search with *engine*" when nothing matches.
- **Long-press and drag:** an App Info and Uninstall menu, dragging apps onto home, moving them, a Remove area, and up to 9 pages.
- **Hidden apps:** hidden behind a privacy pattern, which is stored as a salted PBKDF2 hash. Hidden apps disappear from home, the dock, the drawer and search.
- **Settings:** open them from the Pixel Launcher icon. Wallpaper (8 original designs or your own photo, cropped), add or remove pages, search bar, suggestions, search engine (Bing, Yahoo or Google) and language (English, Hinglish, Hindi).

The app uses no ads, no accounts and no analytics. Its permissions are internet (search results), set wallpaper and request uninstall.

## Build

Requirements: JDK 17 and the Android SDK (platform 35). Set the SDK path in `local.properties`.

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Each debug build copies the APK to `apk/PixelLauncher.apk`.

## Release signing

Release builds shrink and minify with R8. They are signed only when `keystore.properties` exists at the project root. That file and `*.jks` are excluded from git.

1. Create a keystore. `keytool` asks for a password; keep it somewhere safe.

   ```bash
   keytool -genkeypair -v -keystore pixellauncher-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias pixellauncher
   ```

2. Create `keystore.properties`:

   ```properties
   storeFile=pixellauncher-release.jks
   storePassword=your-store-password
   keyAlias=pixellauncher
   keyPassword=your-key-password
   ```

3. Build:

   ```bash
   ./gradlew assembleRelease
   ```

## Project layout

| Path | Contents |
| --- | --- |
| `data/` | App list (all profiles), home layout, settings, launch counts, hidden apps and pattern hashing |
| `ui/` | Home screen, drawer, drag and drop, clock and search bar |
| `settings/` | Settings, hidden apps, pattern pad, language |
| `search/` | Search screen with in-app results; tapped links open in the browser |
| `wallpaper/` | Built-in wallpapers, the wallpaper chooser and the photo crop screen |
