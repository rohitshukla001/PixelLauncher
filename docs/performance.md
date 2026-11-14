# Performance notes

Where Pixel Launcher spends resources and how it keeps the home screen smooth. The launcher is always running, so memory and jank matter more than raw throughput.

## Icons

- **One byte-bounded cache.** `IconCache` is an `LruCache` capped at about 1/8 of the max heap, with `sizeOf` returning each bitmap's true pixel cost (`sizePx × sizePx × 4`). This bounds memory by bytes, not entry count, so large icons cannot blow the budget.
- **Decode off the main thread.** Icons load in coroutines and are cached by app key; work-profile icons use `getBadgedIcon` once and are cached like any other.

## Wallpapers

- **User photos are downsampled.** `decodeDownsampled` reads the image bounds first and picks an `inSampleSize` so the decoded bitmap is close to the screen size, never the full camera resolution. This is the main defence against OutOfMemory on large photos.
- **Centre-crop and recycle.** Photo wallpapers are centre-cropped to the screen; intermediate bitmaps are recycled rather than left for the GC.
- **EXIF rotation** is applied on decode so portrait photos are not drawn sideways.
- **Apply is fault-tolerant.** `WallpaperActivity.apply()` catches `Throwable`, recycles bitmaps in a `finally`, and reports honestly instead of leaking a half-applied state.
- **Built-in designs cost almost nothing.** The 16 built-in wallpapers are drawn with Canvas gradients and paths — no image files to decode or cache.

## Home screen rendering

- **Lazy where it counts.** The app drawer uses `LazyVerticalGrid` / lazy lists so only visible rows are composed. Home pages use a `HorizontalPager`.
- **State is hoisted and keyed.** Derived UI state uses `remember` + `LaunchedEffect` rather than recomputing on every recomposition.
- **Drag is bounded.** The drag-and-drop edge-flip loop advances pages with a `withTimeoutOrNull(NEW_PAGE_TIMEOUT_MS)` guard so a held drag cannot spin forever, and the dragged icon is alpha-hidden rather than recomposed from scratch.

## Persistence

- **DataStore, read as a Flow.** `HomeRepository.layout` applies `distinctUntilChanged`, so an identical layout write does not re-emit and re-render.
- **Batched writes.** Related keys (layout + grid, dock) are written in a single `store.edit {}` transaction, so there is one disk write per logical change.
- **No write when nothing changed.** Update paths compare `next != stored` before writing.

## Build

- **R8 in release.** Release builds shrink and minify with R8, which trims unused code and shrinks the APK. Debug builds skip it for faster iteration.

## What is deliberately not optimised

Per YAGNI, there is no custom view recycling pool, no disk icon cache and no background prefetch. The in-memory `LruCache` plus Compose's own laziness keep the launcher responsive on low-end tablets, which is the target; adding more would be speculative complexity.
