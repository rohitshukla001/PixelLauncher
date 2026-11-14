# Engineering handbook

How Pixel Launcher is built and the decisions behind it. Read this before changing the architecture.

## What the app is

A single-activity home-screen replacement in Kotlin and Jetpack Compose. `MainActivity` registers as the Home activity and hosts everything through `LauncherScreen`. A second, non-exported `SettingsActivity` hosts settings behind its own task.

## Clean-room rule

The app is modelled on Yomo Launcher's publicly observable behaviour. None of Yomo's code, assets or branding is copied. Yomo's documented behaviour is the spec; anything it does not specify is decided in discussion, not guessed. The decompiled reference lives in `base/`, `jadx/`, `xapk/` and `manifest.txt`, is git-ignored, and is for reading only.

## Layering

```
MainActivity / SettingsActivity   (Android entry points, LauncherController impl)
        │
   ui/ · settings/ · search/ · wallpaper/   (Compose UI, one screen per concern)
        │
      data/   (repositories + plain-Kotlin domain types)
        │
   Jetpack DataStore (Preferences) · LauncherApps · WallpaperManager
```

- **UI never touches DataStore directly.** It calls a repository, or the `LauncherController` interface that `MainActivity` implements.
- **Domain logic lives in plain Kotlin** (`HomeLayout`, `GridSize`, `Cell`, `AppEntry`, `PatternHash`, `CropMath`) so it unit-tests on the JVM with no Android or Compose.
- **Repositories own persistence and Android services.** They expose `Flow`s the UI collects and `suspend` functions the UI calls.

## Key components

| Area | Entry point | Notes |
| --- | --- | --- |
| App list | `AppRepository` | Lists activities across all `LauncherApps` profiles, personal first, `onePerPackage`, work badge via `getBadgedIcon`. Registers a `LauncherApps.Callback` for install/remove, kept for process lifetime by design. |
| Home layout + dock | `HomeRepository` / `HomeLayout` | Pages, per-cell shortcuts and an ordered dock, encoded into DataStore. All logic (place/move/remove/dock/fit) is pure in `HomeLayout`; the repository adds persistence, grid validation and uninstall cleanup. |
| Grid | `GridSize` | Computed from measured size; `headerRows` reserves the clock/search rows on page 1. The repository remembers the last measured grid so Settings can edit pages with no home screen on screen. |
| Settings shortcut | `HomeRepository.seedSettingsShortcut` | Seeded once, top-right under the clock, as in Yomo; later removal is respected via a `SEEDED` flag. |
| Dock | `HomeRepository.seedDock` + dock ops | Seeded once from the device defaults. Fully user-managed: add, remove and reorder with no fixed slots beyond `MIN_DOCK_SLOTS`. |
| Hidden apps | `HiddenAppsRepository` + `PatternHash` | Hidden packages drop out of home, dock, drawer and search. Gated by a privacy pattern stored as a salted PBKDF2-HMAC-SHA256 hash. |
| Usage | `UsageRepository` | Launch counts feed the drawer's most-used Suggested row; `prune` drops counts for uninstalled apps. |
| Wallpaper | `wallpaper/` + `OwnWallpaperBackground` | Built-in code-drawn designs, bundled photos, or a user photo. On devices that block system wallpaper changes, the launcher draws the chosen wallpaper on its own home surface. |
| Search | `search/SearchActivity` | Results in an incognito WebView; same-domain main-frame pages stay in-app, external http/https go to the browser, other schemes are dropped. |
| Settings | `settings/` | Change wallpaper, hide apps, toggle the search bar, plus the footer credit. Relocks the hidden-apps section on `onPause`/`onStop`. |

## Conventions

- **Kotlin + Compose + Material3.** State hoisted to the caller; `remember` + `LaunchedEffect` for derived state (not `produceState`).
- **Immutability.** Domain types are `data class`es changed with `copy`; operations return a new value or `null` on rejection (for example `place` returns `null` when the cell is taken).
- **DataStore writes are transactional.** Multi-key writes happen inside one `store.edit {}`.
- **Uninstall safety.** Repository calls that take `installed` treat an empty set as "not loaded yet" (`takeIf { it.isNotEmpty() }`) so nothing is dropped while the app list is still loading. Pass `null` deliberately when there is nothing to validate against.
- **Grid validation.** Cell-placing calls reject cells outside the last measured grid, so a shortcut can never land on a header row or off-grid.
- **Icons.** `IconCache` is a byte-bounded `LruCache` (~1/8 of max heap) keyed by app key, sized by pixel area.

## Testing

- JVM unit tests cover the pure logic: `HomeLayoutTest`, `DockTest`, `OwnWallpaperTest`, `UsagePruneTest`, `Phase4Test`, `CellAtTest`, `MidpointDotTest` and others.
- UI and device behaviour are verified manually on a device from a built APK (no instrumentation suite).
- Run `./gradlew testDebugUnitTest lintDebug` before every commit.

## Git workflow

Work on `main` for this project. Bump `versionCode`/`versionName` for each test build so builds are distinguishable.

## Privacy and permissions

No ads, no accounts, no analytics. Permissions: internet (search results), set wallpaper, request uninstall, plus a `<queries>` entry for `CATEGORY_HOME` so the default-home prompt can fire. The search WebView runs incognito (no cookies, no cache).
