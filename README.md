# Pixel Launcher

A minimal Android home screen in Kotlin and Jetpack Compose, modelled on the features of Yomo Launcher.

Yomo's documented behaviour (its FAQ, on-screen text and observable behaviour) is the spec. None of Yomo's code, images or branding is used. The decompiled Yomo reference in `base/`, `jadx/`, `xapk/` and `manifest.txt` is for reading only and is excluded from git.

> "Pixel" is a trademark of Google LLC; this project is unrelated to Google's own Pixel Launcher. On a Pixel phone, Android's Default home app list shows both, and this one has the blue grid icon. Pick a distinct name before publishing to an app store.

## Features

- **Home:** pages with dots, a fully editable dock (drag to add, remove and rearrange), and a clock (left, centre or right) with an optional search bar on page 1.
- **App drawer:** swipe up. A–Z list of apps from every profile (work apps carry the work badge), search that ignores case and accents, a most-used Suggested row, and "Search with *engine*" when nothing matches.
- **Long-press and drag:** an App Info and Uninstall menu, dragging apps onto home, moving them, a Remove area, and up to 9 pages.
- **Hidden apps:** hidden behind a privacy pattern, which is stored as a salted PBKDF2 hash. Hidden apps disappear from home, the dock, the drawer and search.
- **Wallpaper:** 16 original code-drawn designs, bundled photos, or your own photo cropped to fit. On devices that block wallpaper changes, the launcher draws the chosen wallpaper on its own home screen.
- **Settings:** open them from the Pixel Launcher icon — change wallpaper, hide apps, and toggle the search bar.

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

## Disclaimer

Pixel Launcher is an independent project. It is not affiliated with, endorsed by or sponsored by Google or Samsung. "Pixel" is a trademark of Google LLC, and this project is unrelated to Google's own Pixel Launcher. The project is modelled on the publicly observable features of Yomo Launcher but uses none of its code, images or branding, and is not affiliated with its developers.

On devices whose administrator blocks wallpaper changes, Pixel Launcher shows your chosen wallpaper on its own home screen. It does not remove or bypass device-management policies, results can vary by device, and the lock screen and other system screens may stay as they are. The software is provided "as is", without warranty of any kind; see the [License](LICENSE).

## License

Copyright 2026 Rohit Shukla

Pixel Launcher is licensed under [Creative Commons Attribution-NoDerivatives 4.0 International (CC BY-ND 4.0)](https://creativecommons.org/licenses/by-nd/4.0/). See [LICENSE](LICENSE).

- You may download, install, use and share Pixel Launcher, including the APK, as long as you credit Rohit Shukla and link to the license.
- You may not share modified versions.

Pixel Launcher is source-available, not open source: you can read the code, but the license does not allow sharing changed copies.

The wallpaper images in `app/src/main/assets/wallpapers/` remain the property of their respective owners and are not covered by this license.

---

Design and Developed by Rohit Shukla
