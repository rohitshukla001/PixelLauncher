<h1 align="center">Pixel Launcher</h1>

<p align="center"><b>A fast, minimal Android home screen in Kotlin and Jetpack Compose.</b></p>

<p align="center">
  <a href="https://github.com/rohitshukla001/PixelLauncher/raw/main/apk/PixelLauncher.apk"><b>⬇ Download the APK</b></a>
  &nbsp;·&nbsp; Android 8.0+ &nbsp;·&nbsp; No ads, no accounts, no analytics &nbsp;·&nbsp; Free
</p>

<!-- Screenshots go in docs/images/ and get linked below once added:
## Screenshots

| Home | App drawer | Wallpapers |
| --- | --- | --- |
| <img src="docs/images/home.png" width="240" alt="Home screen"> | <img src="docs/images/drawer.png" width="240" alt="App drawer"> | <img src="docs/images/wallpapers.png" width="240" alt="Wallpaper chooser"> |
-->

## Install in 3 steps

1. **Download** [`PixelLauncher.apk`](https://github.com/rohitshukla001/PixelLauncher/raw/main/apk/PixelLauncher.apk) on your Android phone or tablet.
2. **Install** it. If Android asks, allow installs from your browser or file manager. If Play Protect warns about an unknown app, tap **More details**, then **Install anyway**: Pixel Launcher is not on the Play Store.
3. **Set it as home.** Press the Home button and choose **Pixel Launcher**, or go to Settings → Apps → Default apps → Home app and pick it.

The full walkthrough is in the [user guide](docs/user-guide.md).

The APK is a debug build signed on the developer's computer. It works like any other APK; updates install over it as long as they come from this repo.

## What it does

Pixel Launcher is a home-screen replacement modelled on the features of Yomo Launcher. It uses none of Yomo's code, images or branding.

- **Home:** swipeable pages with dots, a fully editable dock (drag to add, remove and rearrange), and a clock with an optional search bar on page 1.
- **App drawer:** swipe up for an A–Z list of apps from every profile (work apps carry the work badge), accent- and case-insensitive search, a most-used Suggested row, and a "Search with …" fallback to the web.
- **Long-press and drag:** App Info and Uninstall, drag apps onto home, move them, a Remove area, and up to 9 pages.
- **Hidden apps:** locked behind a privacy pattern stored as a salted PBKDF2 hash. Hidden apps vanish from home, the dock, the drawer and search.
- **Wallpaper:** 16 original code-drawn designs, bundled photos, or your own photo cropped to fit. On devices that block wallpaper changes, the launcher draws the chosen wallpaper on its own home screen.

## Privacy

Pixel Launcher has **no ads, no accounts and no analytics**. Its permissions are internet (to show web search results), set wallpaper, and request uninstall. Search runs in a private window that keeps no cookies or history.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Android 8.0 (API 26) and later, compiled against SDK 35
- Gradle 8.11.1 with the Kotlin DSL and a version catalog (`gradle/libs.versions.toml`)
- Jetpack DataStore for settings, `LauncherApps` for the multi-profile app list

## Project structure

```
apk/                           Latest built APK (PixelLauncher.apk)
app/src/main/java/dev/rohitshukla/pixellauncher/
├── MainActivity.kt            Home activity; hosts LauncherScreen, implements LauncherController
├── data/                      App list, home layout + dock, settings, usage, hidden apps, pattern, icon cache
├── ui/                        Home screen, drawer, drag and drop, clock, header, wallpaper background
├── settings/                  Settings, hidden apps, pattern pad, language
├── search/                    In-app search results; external links open in the browser
└── wallpaper/                 Built-in wallpapers, bundled photos, chooser and crop screen
docs/                          User, developer, engineering and performance guides
```

## Build

You need JDK 17 and the Android SDK (platform 35). Android Studio installs both.

```bash
./gradlew assembleDebug
```

Each debug build copies the APK to `apk/PixelLauncher.apk`, replacing the old one. Release signing is covered in the [developer guide](docs/developer.md).

## Docs

- [User guide](docs/user-guide.md): install, home screen, dock, drawer, wallpaper, hidden apps.
- [Developer guide](docs/developer.md): build, test, run, project structure and release signing.
- [Engineering handbook](docs/engineering-handbook.md): architecture, the clean-room rule, conventions and testing.
- [Performance](docs/performance.md): what the launcher costs the device, and what is left unoptimised.

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
