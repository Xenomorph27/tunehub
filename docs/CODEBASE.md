# TuneHub codebase guide

Updated after the first code review; see [REVIEW_FIXES.md](REVIEW_FIXES.md) for what changed.

A complete map of the TuneHub Android app for code review: what every file does, how the
pieces talk to each other, how a tap turns into sound, and where the known weak spots are.
Paths are relative to the repo root. Kotlin package root: `com.tunehub.app`
(`app/src/main/java/com/tunehub/app/`).

## 1. What the app is

A single-activity Android app (Kotlin, Jetpack Compose, Material 3) that searches YouTube
Music and plays audio-only streams in the background, ad-free. It never loads YouTube's web
player. Search, artist, album and playlist data, plus stream URLs, come from
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (`v0.26.5`). Audio plays
in Media3 ExoPlayer inside a `MediaSessionService`. The user's library is a JSON file on the
device. There is no backend, no account and no analytics.

| Item | Value |
|---|---|
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| Kotlin / AGP / Gradle | 2.1.0 / 8.7.3 / 8.11.1 (no wrapper committed; CI pins the version) |
| UI | Compose BOM 2024.12.01, Material 3, Coil 2.7 for images |
| Playback | Media3 1.5.1 (ExoPlayer, Session, OkHttp data source, cache) |
| Network | OkHttp 4.12 (one shared client) |
| Persistence | kotlinx.serialization JSON file (`filesDir/library.json`), plus downloaded audio files |
| Application id | `com.spitify.app` (kept from the app's first name so installs update in place) |
| Namespace | `com.tunehub.app` |
| License | GPL-3.0 (required by NewPipeExtractor) |

## 2. Architecture

```
┌──────────────────────────── UI process (one process for everything) ───────────────────────────┐
│                                                                                                 │
│  MainActivity ── AppRoot (Compose)                                                              │
│     ├─ PaperBackground (follows the system appearance)                                        │
│     ├─ screen for Navigator.current route (Home / Search / Library / Artist / Album / …)        │
│     ├─ MiniPlayer + BottomTabs                                                          │
│     ├─ NowPlayingScreen (slide-up overlay)                                                      │
│     └─ SongActionsSheet (single global bottom sheet)                                            │
│           │                         ▲                                                           │
│           │ commands                │ Compose state (current song, isPlaying, queue…)           │
│           ▼                         │                                                           │
│  PlayerConnection (object) ── MediaController ──binder──► PlaybackService (MediaSessionService) │
│                                                              ├─ ExoPlayer                       │
│                                                              │    └─ ResolvingDataSource        │
│                                                              │         └─ DefaultDataSource     │
│                                                              │              ├─ file:// (offline)│
│                                                              │              └─ CacheDataSource  │
│                                                              │                   └─ OkHttp      │
│                                                              ├─ AudioFx (EQ, bass, 3D, loud)    │
│                                                              └─ SleepTimer                      │
│                                                                                                 │
│  data layer (objects / app singletons):                                                         │
│     YouTube ─ NewPipeExtractor ─ YtDownloader (OkHttp)     StreamResolver (stream URL lookup)   │
│     Library (JSON store, StateFlows)   Downloads (offline files)   Lyrics (LRCLIB)              │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

The UI and the service live in the same process. A few things rely on that, which is
deliberate but worth knowing:

- `PlaybackService.player` is a static reference, used by `SleepTimer`, the equalizer and
  `PlaybackService.reloadCurrent()`.
- `MediaItems.kt` keeps a `knownSongs` map so `MediaItem.toSong()` returns full `Song` objects
  without relying on metadata round trips.
- `StreamResolver` is a process-wide object shared by the service (loading audio) and the UI
  (prefetch, quality badge).

## 3. File by file

### App entry

| File | Responsibility |
|---|---|
| `TuneHubApp.kt` | `Application`. Creates the shared `OkHttpClient`, calls `NewPipe.init` with the device locale and country, creates `Library` and `Downloads`, mirrors `library.dataSaver` into `StreamResolver.dataSaver`. Also the Coil `ImageLoaderFactory`: shared OkHttp client, 25% memory cache, 200 MB disk cache, `respectCacheHeaders(false)` because artwork URLs never change. Static accessors: `TuneHubApp.library`, `.downloads`, `.http`. |
| `AndroidManifest.xml` | Permissions: INTERNET, FOREGROUND_SERVICE(+_MEDIA_PLAYBACK), WAKE_LOCK, MODIFY_AUDIO_SETTINGS (audio effects). `MainActivity` handles LAUNCHER and `ACTION_SEND text/plain` (shared YouTube links). `PlaybackService` is exported with the `androidx.media3.session.MediaSessionService` action, which the notification and other controllers need. |

### Data layer (`data/`)

| File | Responsibility |
|---|---|
| `Models.kt` | `@Serializable` `Song` (id, title, artist, durationSec, thumb, artistUrl), `Playlist`, `ArtistRef`, `AlbumRef`. `BrowseItem` sealed type for mixed search results. Helpers: `formatDuration`, `formatCount`, `totalDuration`, and `thumbSized(url, px)`, which rewrites googleusercontent `=wN-hN` / `=sN` size suffixes and `i.ytimg` `hqdefault→mqdefault` so list rows download small images. |
| `YtDownloader.kt` | NewPipe's `Downloader` implemented on OkHttp. Fixed Firefox user agent; maps HTTP 429 to `ReCaptchaException`. |
| `YouTube.kt` | All metadata calls through NewPipeExtractor. `Search(query, filter)` is a paged search (YouTube Music filters: songs, videos, artists, albums, playlists). `searchAll()` runs four filtered searches in parallel for the "All" tab. `artistPage()` fetches `ChannelInfo` in parallel with a song search by name, then the channel's ALBUMS tab (falls back to an album search) and VIDEOS tab. `albumPage()` loads `PlaylistInfo` plus up to 10 extra pages and strips the "Album – " title prefix. `trendingMusic()` uses the `trending_music` kiosk. Mappers `toSong()` / `toBrowseItem()` / `toAlbum()`; `upscale()` asks for 544 px art. Note: search-result subscriber counts are deliberately dropped because NewPipe misparses them (10× too high). |
| `StreamResolver.kt` | Turns a video id into a playable audio URL + headers. **Fast path:** one `YoutubeStreamHelper.getAndroidReelPlayerResponse` call (plus the visitor-data call it makes internally), reads `streamingData.adaptiveFormats` directly with nanojson, keeps `audio/*` formats that have a direct `url`, the default audio track and non-DRC copies, runs `getUrlWithThrottlingParameterDeobfuscated`, appends `&cpn=`. **Fallback:** full `StreamInfo.getInfo` (about four sequential requests). Picks the highest bitrate (Opus wins ties), or the lowest in data-saver mode. Results are cached until 5 minutes before the URL's `expire`. Per-id lock so concurrent callers share one lookup. `prefetch()` for touch-down. `invalidate()` marks a failed fast-path song as full-only; after 2 such failures the fast path is off for the session. `related()` lazily fetches related songs (full StreamInfo) for radio and autoplay. `quality` label such as `OPUS 160K`. `headersFor()` mirrors NewPipe: each client's URLs need that client's user agent; web URLs need Origin and Referer. |
| `Library.kt` | The user's data: liked songs, playlists, saved albums, followed artists, downloads, recent plays (≤100), recent searches (≤15), settings (autoplay, dataSaver, `EqSettings`). One `MutableStateFlow<LibraryData>`; every mutation writes the whole file on `Dispatchers.IO` under a mutex via temp-file-then-rename. Derived `likedIds` / `downloadedIds` StateFlows so long lists check membership in O(1). |
| `Downloads.kt` | Offline audio. Saves to `filesDir/downloads/<videoId>` via 1 MB ranged POST requests (same request shape as the player), max 2 at a time (semaphore), progress in a StateFlow. On startup reconciles the library's download list against the files on disk. Runs in an app-scoped coroutine, not WorkManager (see risks). |
| `Lyrics.kt` | LRCLIB (`lrclib.net/api/search`). Cleans titles ("Artist - Song (Official Video)" → "Song"), searches with the artist, then without. Prefers synced lyrics whose duration is within 15 s. Parses LRC timestamps. In-memory cache plus a miss set. |

### Playback (`playback/`)

| File | Responsibility |
|---|---|
| `PlaybackService.kt` | `MediaSessionService` that owns ExoPlayer. **Data source chain:** `CacheDataSource` (512 MB LRU `SimpleCache`, singleton per process, cache key = `tunehub://song/<id>` + `#hi` / `#lo` for quality) → `ResolvingDataSource` (swaps the placeholder URI for a downloaded `file://` or the resolved googlevideo URL, as POST with body `x\0`, `&rn=` request counter and client headers) → `DefaultDataSource` (file) / `OkHttpDataSource` (shared client). **Player config:** music audio attributes with focus handling, becoming-noisy pause, network wake lock, `DefaultLoadControl` starting playback after 500 ms of buffer. **Session callback** `onAddMediaItems` rebuilds URIs from media ids, because items from controllers arrive without them. **Listener:** records recent plays, prefetches the next song, autoplay radio (appends 10 related songs when near the end of the queue and repeat is off), error handling (network errors → toast and wait; first stream error → invalidate and re-prepare; second → toast and skip), sleep-timer end-of-song hook, reattaches `AudioFx` when the audio session id changes. Broadcasts open/close audio-effect session intents. `reloadCurrent()` re-sets the queue at the same position (used by the Hi-Res toggle). |
| `MediaItems.kt` | `Song ⇄ MediaItem` conversion. Placeholder URI scheme `tunehub://song/<id>`. Duration and artist URL stored in metadata extras; `knownSongs` map as the primary source. |
| `AudioFx.kt` | Android `Equalizer`, `BassBoost`, `Virtualizer`, `LoudnessEnhancer` on the player's audio session; each is optional (`runCatching`). Exposes band centres and level range as a StateFlow for the UI. `apply(EqSettings)`: UltraTune overrides manual settings with a frequency-shaped curve (bass +9 dB, low-mids −1.5, mids +1, presence +5, treble +7, clamped to the device range), bass 850, virtualizer 700, loudness +4.5 dB. Presets are frequency-shaped curves, so they work whatever bands the phone has. |
| `SleepTimer.kt` | Timer state flow; minutes (Handler post) or end of song (`pauseAtEndOfMediaItems`). |

### UI (`ui/`)

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Edge-to-edge activity. Connects / disconnects `PlayerConnection` in onStart / onStop. Handles `ACTION_SEND` (extracts a video id and starts a radio). `AppRoot`: Navigator, global song-menu state, CompositionLocals (`LocalNav`, `LocalSongMenu`, `LocalContentColor` = theme text), the paper background, the current route, the bottom dock, the Now Playing overlay, playback error toasts, and `BackHandler`. Screen content reserves the measured height of the bottom dock. |
| `Nav.kt` | `Route` includes Recently Played, Liked Songs, Downloads, Settings, Equalizer, imports, playlists, albums, artists, and genres. `Navigator` keeps a back stack per tab and restores it after recreation. |
| `PlayerConnection.kt` | Singleton wrapping `MediaController`, mirrored into Compose state (current song, isPlaying, buffering, queue, index, shuffle, repeat, duration, error). Commands: play / playNext / enqueue / startRadio / togglePlay / seek / jumpTo / move / removeAt / clearUpcoming / shuffle / repeat / setHiRes. `withController` queues one pending action while the controller connects. |
| `Theme.kt` | Paper and charcoal palettes, system appearance, Material color roles, and the bundled Source Serif 4, Inter, and Silkscreen fonts. `AppIcons`: hand-written vector paths, to avoid the ~10 MB material-icons-extended. |
| `Controls.kt` | Design primitives: `Modifier.panel()`, `PaperBackground`, `PanelIconButton`, `AccentCircleButton`, `FilterPill`, and `SegmentedSeekBar` (tap, drag, or accessibility seek). |
| `Components.kt` | Shared pieces: `Artwork` (sizes the thumbnail to its dp size), `PlaylistCover` (2×2 collage), Liked / Downloads covers, `rememberArtColor` (Palette vibrant color, cached), `prefetchOnTouch`, `SongRow` (long-press or ⋮ opens the menu), `SongActionsSheet` (like, add to playlist with inline picker, play next, queue, radio, download, go to artist, share, remove from playlist), `AlbumCard`, `ArtistCircle`, `BrowseRow`, `BackPage` (LazyColumn + pinned back button), `CollectionHeader`, `PlayCircle`, `DownloadAllButton`, dialogs, section titles, loading / error boxes. |
| `HomeScreen.kt` | Home opens with shortcuts to Liked Songs, Recently Played, and other local collections. The spotlight leads into personalized radio, Quick Picks, and Trending. `HomeViewModel` reads cached trending songs off the UI thread and refreshes them after 30 minutes on the next launch. |
| `SearchScreen.kt` | `SearchViewModel`: debounced suggestions (250 ms), filter (`SearchFilter` enum), "All" via `searchAll`, paged single-filter search with infinite scroll, dedupe by key. Screen: matte search field (hides the keyboard on submit), filter chips, suggestions, search home (recent searches + genre tiles), results (`topResults` for All: top-result card, songs, artist / album / playlist rows). `GenreScreen` reuses a keyed SearchViewModel with "<genre> songs". Tapping a song starts a radio. |
| `ArtistScreen.kt` | `ArtistViewModel` → `YouTube.artistPage`. Banner fading into the aurora, ringed avatar, name, Follow chip, shuffle / play, Popular (numbered), Discography row, Music videos. |
| `CollectionScreens.kt` | `AlbumViewModel` + `AlbumScreen` (YouTube album or playlist: save to library, download all, numbered tracks without art for albums), `LikedScreen`, `DownloadsScreen` (overall progress bar), `LocalPlaylistScreen` (rename, delete, remove songs via the menu). |
| `LibraryScreen.kt` | Filter chips (Playlists, Albums, Artists, Downloaded), sort (recently added / A–Z), pinned Liked Songs and Downloads, create-playlist dialog. |
| `NowPlaying.kt` | `MiniPlayer` has previous, play/pause, and next controls plus swipe to skip. `NowPlayingScreen` shows the resolved codec and bitrate in its header. Four playback options surround the central Share button. The header also opens full-screen lyrics with loading and retry states, auto-scroll, and tap to seek. |
| `EqualizerScreen.kt` | UltraTune card, equalizer on/off, presets, per-band sliders (from `AudioFx.bands`), bass / 3D / loudness sliders. Disabled while UltraTune is on. |
| `SettingsScreen.kt` | Autoplay, Hi-Res audio, Equalizer & UltraTune (→ Equalizer), storage info, clear history / searches, version, link to the latest release. |

### Resources, build and CI

| File | Responsibility |
|---|---|
| `res/font/outfit.ttf`, `space_grotesk.ttf` | Variable fonts (OFL). |
| `res/drawable/ic_launcher_foreground.xml`, `ic_tunehub_mono.xml` | Amber-to-terracotta play mark and its monochrome version for themed icons and playback notifications. The GitHub PNG and SVG are in `docs/`. |
| `app/build.gradle.kts` | Version code and name from the `BUILD_NUMBER` env var. Release: R8 minify + shrinkResources + `proguard-rules.pro`, signed with the CI keystore when `TUNEHUB_KEYSTORE*` env vars exist (else the debug key). Core library desugaring (NewPipe needs java.time / nio). Explicit `nanojson` dependency (same commit as the extractor) for the fast path. |
| `app/proguard-rules.pro` | NewPipe's rules (Rhino and its script engine, protobuf, timeago patterns), OkHttp warnings, `-dontobfuscate`, kotlinx.serialization keeps for our models. |
| `app/src/test/.../ExtractionTest.kt` | Live smoke tests against YouTube Music and LRCLIB: searchAll, artist page, album page, lyrics, stream fetch. The stream test fails on CI because YouTube blocks datacenter IPs, so CI marks the step continue-on-error. |
| `.github/workflows/build.yml` | Every push to main: JDK 17, Gradle 8.11.1, `BUILD_NUMBER = run_number + 15`, decode keystore from secrets, `assembleRelease`, smoke tests (non-blocking), upload artifact, `gh release create build-N tunehub.apk --latest`. One release per build so download counts accumulate. |
| `.github/workflows/ui-check.yml` + `ci/ui-check.sh` + `ci/find_node.py` | Manual: builds the release APK, boots an API 34 emulator, drives search → artist → album → play → Now Playing → library → new playlist → settings → equalizer with adb + uiautomator (retries dumps, dismisses system ANR dialogs), saves screenshots + logcat, flags `FATAL EXCEPTION`. |

### TuneHub design

TuneHub pairs editorial typography with warm paper and an amber playback display.
Source Serif 4 is used for headings and lyrics, Inter for controls and metadata,
and Silkscreen for the Home, Search, and Library labels and the LCD. The fonts and their SIL Open Font
License notices ship with the app.

The light palette starts at paper #FAF9F5 and the dark palette at charcoal #262624.
Terracotta #D97757 marks primary controls, with a separate contrast-adjusted text color in
each theme. The player uses amber #F0B35A on ink #1F1E1D. A shallow shadow separates the
artwork frame from the page. Navigation colors, the player transition, lyric highlights,
and mini-player track changes use short animations. The cover follows a swipe to skip tracks.

The Home spotlight uses the current track, listening history, or TuneHub's trending feed.
The design uses TuneHub's artwork and data throughout. The app follows Android's system
appearance, and the player scrolls on shorter screens and at larger text sizes.

## 4. Key flows

### Tap a song → sound
1. Finger down on a `SongRow` → `prefetchOnTouch` → `StreamResolver.prefetch(id)` starts the lookup on IO.
2. Tap → `PlayerConnection.play(...)` or `startRadio(song)` → `MediaController.setMediaItems` with `tunehub://song/<id>` items.
3. Session `onAddMediaItems` rebuilds URIs → ExoPlayer prepares → `CacheDataSource` checks the cache by key `tunehub://song/<id>#hi`.
4. Cache miss → `ResolvingDataSource.resolve(spec)`: a downloaded file? Use `file://`. Otherwise `StreamResolver.resolve(id)` returns the prefetched result or does the fast lookup (usually already done by step 1).
5. Rewritten DataSpec: googlevideo URL + `&rn=N`, POST body, client user agent → `OkHttpDataSource` streams it. Playback starts after 500 ms of buffer.
6. Listener: `addRecent`, prefetch the next song, maybe extend the queue with related songs (`StreamResolver.related` on IO).
7. `PlayerConnection` receives `onEvents` → Compose state → MiniPlayer / Now Playing update.

### Failure handling
Network failure → toast "No connection", stay. Stream failure → `StreamResolver.invalidate` (marks the song full-only, counts fast-path failures) → re-prepare at the same position; a second failure → toast and skip to the next song.

### Hi-Res toggle
`PlayerConnection.setHiRes(on)` → `library.setDataSaver(!on)` + `StreamResolver.dataSaver` (clears the URL cache) → `PlaybackService.reloadCurrent()` (same queue and position). The cache key suffix changes, so cached bytes of the other quality are never mixed in.

### Library persistence
UI calls a `Library` method → `_data.updateAndGet` (synchronous state change; Compose recomposes) → `save()` on IO under a mutex writes the latest state to a temp file, then renames it over `library.json`.

### Navigation
Tabs Home / Search / Library, each with its own route stack in `Navigator`. Tapping the active tab pops it to its root. Now Playing is an overlay flag rather than a route. System back: close Now Playing → pop → go to Home → leave the app.

## 5. Things a reviewer should look at

The first review's findings and their fixes are listed in [REVIEW_FIXES.md](REVIEW_FIXES.md).
Key design points that came out of it, and what is still open:

- **Library safety:** `LibraryStore` (plain JVM, unit-tested) loads as Missing / Loaded /
  Failed, quarantines damaged files, falls back to `library.json.bak`, and writes with
  temp-file + `fsync` + rename. `Library` batches writes through one conflated writer.
  `loadedCleanly` gates every destructive cleanup.
- **Stream identity:** the playback chain is Resolving → DefaultDataSource (file:// for
  downloads) → CacheDataSource → OkHttp. The resolver sets the DataSpec key
  `v2:<id>:<itag>[d]`, so cached bytes are always tied to one exact rendition. Lookups are
  cached per (video id, quality).
- **Radio:** owned by `PlaybackService`. The seed item carries a `radio` extra; appends are
  checked against a queue generation.
- **Still open:** exact queue restoration after process death; WorkManager-based downloads
  with byte-offset resume; a controller allow-list for the exported session; async library
  load at startup; browse ViewModels scoped to routes; a test suite that runs the minified
  APK's extraction paths (the emulator check covers the UI, but YouTube blocks audio from CI).

## 6. How to build and verify

```bash
gradle assembleRelease          # needs the Android SDK; CI does this on every push
gradle testReleaseUnitTest --tests "com.tunehub.app.unit.*"   # deterministic, blocks releases
gradle testReleaseUnitTest --tests "com.tunehub.app.ExtractionTest"   # live network, advisory
```

Manual UI check: GitHub → Actions → "UI check (emulator)" → Run workflow, then download the
`ui-shots` artifact (screenshots + logcat).
