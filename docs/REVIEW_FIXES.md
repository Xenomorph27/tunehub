# Review fixes

How each finding in [REVIEW.md](REVIEW.md) (reviewed against commit `6b86613`) was handled.
Status: **Fixed**, **Partly fixed** (the risky part is fixed; the rest is noted), or
**Deferred** (with the reason).

## High

| # | Finding | Status | What changed |
|---|---|---|---|
| H1 | Failed library read → empty library → downloads deleted | **Fixed** | `LibraryStore.load()` returns Missing / Loaded / Failed. A damaged `library.json` is copied to `library.json.corrupt-<time>` and the last successfully loaded copy (`library.json.bak`) is used. After a failed load `Library.loadedCleanly` is false and `Downloads` deletes nothing. Every field has a default and the file carries a `version` for future migrations. |
| H2 | Writes unchecked, not durable, one write per slider event | **Fixed** | One conflated writer coroutine (150 ms batching, 3 retries with backoff). The temp file is `fsync`ed before the rename; a failed rename throws. Write failures appear as `saveError` (a toast). `flushBlocking()` for explicit flushes. |
| H3 | Cache keys named a preference, not the audio file | **Fixed** | The stream is resolved before the cache. The cache key is `v2:<id>:<itag>[d]` from the chosen rendition, so different renditions never share bytes. Downloaded files come back as `file://` and skip the streaming cache. A failed stream is retried by rebuilding the media source. The old preference-keyed cache folder is deleted. |
| H4 | Quality toggle races cache key and URL publication | **Fixed** | The resolver reads quality once per lookup and stores results under `<id>\|hi` / `<id>\|lo`, so a slow lookup can only publish into its own quality's slot. The media cache key comes from that resolved rendition, never from a global flag. |
| H5 | Stale radio / autoplay results land on a newer queue | **Fixed** | Radio is owned by the service: the UI marks the seed item, and the service extends any radio queue (or any queue when autoplay is on). The service checks that the queue generation, current song, repeat mode and settings still match before appending. Related lookups are shared while in flight. If the queue ended while waiting, playback continues. |
| H6 | AudioFx capability calls could crash the service | **Fixed** | Band discovery is guarded; on failure the effect is released and logged. Invalid audio session ids are skipped. Release clears the band info. |
| H7 | CI could publish a debug-signed APK | **Fixed** | The build fails if any signing secret is missing. `apksigner` checks the APK's certificate SHA-256 against the release certificate before anything is published. |

## Medium

| # | Finding | Status | What changed |
|---|---|---|---|
| M1 | One pending command | **Fixed** | Ordered pending queue; commands that replace the queue drop stale pending ones. |
| M2 | Controller connection failure / disconnect | **Fixed** | `onDisconnected` clears the handle and reconnects while the Activity is started. Failed connections reset so they can retry. Stale futures are released. |
| M3 | Downloads: truncation, duplication, whole body in RAM | **Fixed** | Bodies stream to disk in 64 KB pieces. Every 206 must start where the last ended (`Content-Range`). A 200 restarts the file from zero. A 416 is accepted only when the download is provably complete. Length is checked before the file is promoted. |
| M4 | Download cancellation races | **Fixed** | Removal cancels the coroutine and the in-flight OkHttp `Call`. Each attempt uses its own `.part` file. Registration is atomic (`putIfAbsent` with a lazy job). Promotion and removal are serialized per song. |
| M5 | Downloads don't survive process death | **Partly fixed** | Requested downloads are saved in the library (`pendingDownloads`) and resume, from the start, on the next launch. Not done: a WorkManager / foreground-notification download service with byte-offset resume. |
| M6 | Retry / skip consumes the queue during outages | **Fixed** | Network errors and HTTP 429 / 5xx pause and wait instead of skipping. After 3 failures in a row playback pauses with a message. The counter resets once anything plays. |
| M7 | File work on the main thread | **Partly fixed** | Download reconciliation, storage measurement and bulk removal run on IO. Not done: the library JSON is still read synchronously at app start; it's small, and making it async would need a loading state across every screen. |
| M8 | Navigation and playback not restored | **Partly fixed** | Navigation (tab, per-tab stacks, Now Playing) is saved with `rememberSaveable` and survives rotation and process recreation. Not done: restoring the exact queue and position after process death. |
| M9 | Waveform gestures used stale callbacks | **Fixed** | `rememberUpdatedState` for callbacks, handlers keyed by song, drag preview reset per song. |
| M10 | Unbounded caches and queue history | **Fixed** | `BoundedCache` (LRU) for stream, lock, related, fast-path, song, art-color and lyrics caches. The played history in the queue is trimmed to 50. The UI rebuilds the queue only when the timeline changes. Not done: scoping browse ViewModels to routes. |
| M11 | Lyrics sent automatically, weak matching | **Fixed** | A Settings toggle "Look up lyrics automatically" explains what's sent and where; when off, nothing is sent until the user taps. Results must match the title as well as the duration. |
| M12 | Exported inputs not validated | **Partly fixed** | Song ids are validated (11-character pattern) at the session, the resolver and the downloads folder. URIs are built with `Uri.Builder`. Shared links are accepted only from YouTube hosts. Deferred: a controller allow-list, because it risks breaking Bluetooth, the lock screen and Android Auto controls. |
| M13 | Backup policy | **Fixed** | `allowBackup` with explicit rules: `library.json` is included in cloud backup and device transfer; downloaded audio is excluded. The Settings text explains this. |
| M14 | Version codes not monotonic; re-runs not idempotent | **Fixed** | The build fails unless its number is higher than every published `build-N` release. Publishing runs only from `main`. A re-run of an existing build doesn't replace it. Builds queue instead of being cancelled mid-publish. |
| M15 | Verification can pass despite crashes | **Fixed** | Deterministic unit tests (`com.tunehub.app.unit`) block publishing. The live YouTube test stays advisory. The emulator check fails on a TuneHub crash or a failed install. |

## Low

| # | Finding | Status | What changed |
|---|---|---|---|
| L1 | Fast-path failure accounting | **Fixed** | Separate atomic counters for lookup failures (3 in a row) and playback failures (2 songs) pause the fast path for 10 minutes. A malformed format is skipped instead of failing the lookup. |
| L2 | Effect broadcasts ignore session changes | **Fixed** | The announced session is tracked: the old one is closed and the new one opened on every change and on destroy. |
| L3 | Transient lyrics errors cached forever | **Fixed** | Network errors aren't cached and show "tap to try again". Confirmed misses expire after 30 minutes. The search without the artist runs when the first search finds nothing usable. |
| L4 | Waveform not accessible | **Fixed** | Progress range, a percentage state description and a `setProgress` action. |
| L5 | Update link can crash | **Fixed** | `ActivityNotFoundException` is caught; the URL is shown instead. |
| L6 | "Recently added" not by date | **Fixed** | Playlists, albums and artists record `addedAt`; the library sorts by it, and older entries keep their order. |
| L7 | Back closes Now Playing over lyrics | **Fixed** | A local `BackHandler` closes full-screen lyrics first. |

## Tests added

`app/src/test/java/com/tunehub/app/unit/`: library load / recovery / migration / persistence,
download bookkeeping, lyrics parsing and title matching, stream choice (bitrate, Opus tie-break,
data saver), video id validation, share-link host checks, navigation state serialization,
and LRU eviction.
