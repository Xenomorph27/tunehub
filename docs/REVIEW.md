# TuneHub code review

Reviewed 1 November 2026 against commit `6b86613db31be5d05344cca55da1db2c026d3847` from `Xenomorph27/tunehub` (`main`).

Read `docs/CODEBASE.md` first, then every Kotlin source file under `app/src/main/java/com/tunehub/app/`, the manifest, Gradle configuration, ProGuard rules, tests, both workflows, and UI-check scripts. Application code is unchanged. No Android SDK, build, emulator, device reproduction, or live YouTube playback was available; no CI workflow was dispatched.

**Evidence labels:** **Verified (source)** means the implementation and a concrete failure path are visible in the code, not that the failure was reproduced on a device. **Suspected** means an additional runtime, OEM, scheduling, or build condition still needs validation. Severity reflects impact and reachability. No critical finding was established.

Paths below are repository-relative. In evidence for Kotlin files, `src/` abbreviates `app/src/main/java/com/tunehub/app/`. All suggested fixes preserve `applicationId = "com.spitify.app"`, the Aurora Glass appearance, and the existing backend-free model. Proposed Android changes still require compilation in GitHub Actions.

## High severity

### H1. A failed library read can become permanent library loss and delete all downloads

**Verified (source).** Evidence: `src/data/Library.kt:49-54,157-159`; `src/data/Downloads.kt:39-44`; `src/TuneHubApp.kt:45-46`; `src/playback/PlaybackService.kt:188-192`.

Every read or deserialization failure becomes a successful-looking empty `LibraryData`: malformed JSON, a transient read failure, or an incompatible schema are indistinguishable from a first installation. `Downloads` immediately interprets that empty library as authoritative and deletes every file in the downloads directory. The next like, setting change, search, or playback transition saves the empty-based state over the original JSON. A single bad nested song can therefore lose unrelated playlists, likes, settings, and offline files.

**Fix:** distinguish missing file, successful load, and failed load. On failure preserve/quarantine the original file, try a last-known-good backup, expose a recovery error, and disable automatic saves and destructive reconciliation until recovery succeeds. Never delete offline files merely because metadata could not be read. Add a schema version and explicit migrations; new optional fields should retain defaults. `ignoreUnknownKeys` already tolerates additive unknown fields; it does not tolerate missing required fields or changed types (`src/data/Models.kt:6-9,16-18`).

### H2. Library writes have no reliable failure handling or durability guarantee

**Verified (source).** Evidence: `src/data/Library.kt:51-52,152-165`; `src/ui/EqualizerScreen.kt:137-164`.

The mutex and reading `_data.value` inside it correctly prevent concurrent writers from interleaving and prevent old queued snapshots from overwriting newer state. However, `renameTo`'s Boolean result is ignored, writes have no exception handler, and no backup or sync is performed. A failed rename silently leaves the UI reporting unsaved changes; disk-full/write errors escape a root `launch` and can reach Android's uncaught exception handler. Process termination before queued saves run loses acknowledged mutations. Each EQ slider event also queues another complete serialization/write, even when many jobs will serialize the same latest state.

**Fix:** keep synchronous in-memory updates, but use one conflated writer with a dirty/version counter and explicit failure reporting/retry. Use Android `AtomicFile` with its start/finish/fail protocol, perform IO off main, retain recoverable prior data, and acknowledge a save only after successful completion. Coalesce slider writes and flush at meaningful edit boundaries. Do not rely on an Activity shutdown callback as the only durability mechanism.

### H3. Streaming cache keys identify a preference, not an immutable audio representation

**Verified (source); actual corruption requires different representations.** Evidence: `src/playback/PlaybackService.kt:68-75,150-169,214-219`; `src/data/StreamResolver.kt:97-146`; `src/data/Downloads.kt:79-112`.

All high-quality bytes for an ID share one key, and all low-quality bytes share another. Fast and full resolution can select different formats/tracks for that same key; fallback invalidates only the URL map, not cached bytes. Offline files are resolved *inside* the streaming cache: a file downloaded in high mode can fill holes in an existing low-mode cache after the user switches mode. Existing cached offsets, content length, and extractor seek maps can then describe a different file, causing truncated playback, parser errors, incorrect seeks, or repeated failure. Retrying can just reread the bad cached prefix.

**Fix:** carry an immutable rendition identity (video ID, itag/codec, track, DRC variant, and a cache schema version) into the request/cache key. Resolve identity before choosing cached bytes. Route verified offline files outside the streaming cache or give them their own identity; record their rendition in metadata. On a representation-changing retry, recreate the media source and seek map and evict/bypass the old representation. Migrate/invalidate existing preference-only keys. Media3's cache reuses byte spans and stored length for a key; it does not determine whether two resolutions contain identical bytes ([Media3 1.5.1 CacheDataSource](https://raw.githubusercontent.com/androidx/media/1.5.1/libraries/datasource/src/main/java/androidx/media3/datasource/cache/CacheDataSource.java)).

### H4. Quality changes race both cache-key selection and URL-cache publication

**Verified (source).** Evidence: `src/data/StreamResolver.kt:38-55,68-76,155-157`; `src/playback/PlaybackService.kt:73,281-290`; `src/ui/PlayerConnection.kt:155-158`.

An old resolve can select high quality, then a toggle clears the map and sets low mode, then the old resolve publishes its high result into the ID-only cache. Low playback reuses it until expiry. Conversely, a load can choose a high cache key before resolution, then resolve using the newly changed low flag. `@Volatile` provides visibility, not a transaction across these operations. Resetting the queue does not cancel app-scoped prefetches or synchronize publication.

**Fix:** snapshot quality in each playback request. Key URL lookups and their locks by `(videoId, quality)` and pass that immutable mode into selection. Use a generation check before publishing if invalidating lookups globally. Make the media cache key derive from the same immutable request/rendition; never read a global setting inside its factory.

### H5. Stale radio/autoplay jobs modify whichever queue exists when they finish

**Verified (source).** Evidence: `src/ui/PlayerConnection.kt:50,99-106,132-142,145-147`; `src/playback/PlaybackService.kt:188-198,241-255`; `src/data/StreamResolver.kt:81-85`.

Start radio A, then play album B before A's request completes: A's related songs append to B. Service autoplay has the same race, and does not recheck autoplay/repeat settings after IO. Clearing upcoming tracks can be undone by an outstanding request. For a one-song radio, the UI and service independently start related lookups; the related cache has no per-ID in-flight sharing. If related results complete after a one-song queue has ended, merely appending items also needs an explicit decision about resuming playback.

**Fix:** give each queue replacement/clear operation a generation; cancel obsolete radio jobs and require generation, seed, relevant settings, and queue eligibility to match before appending. Own radio in the service, including explicit-radio commands, to remove competing implementations. Share in-flight related requests. Define and test what happens when results arrive after `STATE_ENDED`, without resuming a queue the user has replaced or paused.

### H6. AudioFx can crash the service despite its advertised exception isolation

**Verified (source): unguarded calls. Suspected: device-specific exception occurrence.** Evidence: `src/playback/AudioFx.kt:33-48`; `src/playback/PlaybackService.kt:97-100,184-185`.

Effect constructors are caught, but `bandLevelRange`, `numberOfBands`, and `getCenterFreq` run outside any `runCatching`. If an effect loses control or an OEM implementation throws during capability discovery, the exception escapes service creation or the main-thread player callback and can terminate the app. Attaching with session ID 0 is also attempted before a usable player audio session necessarily exists.

**Fix:** skip invalid/unset session IDs. Treat construction plus capability discovery as one guarded operation; on failure release the newly created effect, clear band state, and keep playback running. Catch expected runtime exceptions and retain diagnostic logging. If moving slow OEM calls off main, serialize attach/apply/release on one effect worker with a session generation; keep all player access on its application thread.

### H7. CI can publish an APK signed with an unrelated debug key as the latest update

**Verified (source); secret availability and current distributed certificate were not inspected.** Evidence: `.github/workflows/build.yml:34-50,63-72`; `app/build.gradle.kts:27-34,44-47`.

Missing `KEYSTORE_BASE64` only emits a warning, then release builds fall back to a debug signing key and are published with instructions promising an in-place update. On fresh hosted runners that identity is not the established release certificate. Android rejects updates signed differently; uninstalling to work around that loses local data. A valid-but-wrong release keystore is likewise not checked.

**Fix:** fail the publishing workflow when any signing secret is absent; verify the built APK with `apksigner` against the established certificate digest before upload/release. Keep debug fallback only for explicitly nonpublishing development/UI-check builds. Preserve and back up the existing signing key and keep `com.spitify.app`; changing either identity is not a migration fix.

## Medium severity

### M1. A single pending command loses user intent, including the radio seed

**Verified (source).** Evidence: `src/ui/PlayerConnection.kt:52,59-65,95-96,132-140`.

Two commands before connection overwrite each other. More subtly, if related results return before connection, `startRadio`'s pending queue replacement is replaced by its pending append action, so the tapped seed need never be played.

**Fix:** queue commands in order, or keep one complete playback intent with its seed and radio generation. Distinguish replacement commands from dependent edits. Drain only against a currently valid controller.

### M2. Controller connection failure or disconnection leaves the UI unable to recover

**Verified (source); stale completion timing is suspected until lifecycle-tested.** Evidence: `src/ui/PlayerConnection.kt:54-79`; `src/ui/MainActivity.kt:70-77`.

A failed `f.get()` returns without clearing `future` or reporting failure. Further `connect` calls are ignored until an Activity stop/start. No `MediaController.Listener.onDisconnected` is installed, so a dropped session can leave a non-null dead controller and stale mirrored state. The async completion also has no `future === f`/generation guard: a successful future whose listener is queued around `onStop` can publish a controller that was just released.

**Fix:** install the controller-specific disconnection listener, clear failed/disconnected handles, surface connection state, and reconnect while the Activity is started. Guard completion with an owner generation and future identity; release stale results and detach listeners. Decide which pending commands survive reconnection. Media3 explicitly supports asynchronous future release, so cancelling an unfinished future is already handled; an unconditional leak on every rotation is **not** established ([MediaController 1.5.1](https://raw.githubusercontent.com/androidx/media/1.5.1/libraries/session/src/main/java/androidx/media3/session/MediaController.java)).

### M3. Downloads can finalize truncated or duplicated audio and allocate an entire response in memory

**Verified (source); triggered by the stated server responses.** Evidence: `src/data/Downloads.kt:81,86-112`.

Any successful status is accepted without checking `Content-Range`. A short 206 body ends the loop even if `position < clen`, finalizing a truncated file. A 200 response to a later range is appended to bytes already written, duplicating the prefix rather than replacing the partial file. An empty successful body can be finalized too. If `clen` is absent and length is an exact chunk multiple, a final 416 is treated as total failure. `body.bytes()` also reads a range-ignoring whole response into RAM, risking OOM for long recordings.

**Fix:** stream bounded reads to disk; validate 206 range start/end/total and final length. Accept a complete 200 only at offset zero, or explicitly restart from zero. Handle EOF/416 only when the established total proves completion. Keep an incomplete file incomplete; validate it before atomic promotion and metadata publication.

### M4. Download cancellation is not coupled to HTTP or final publication

**Verified (source), API-level hazard; current UI disables removal of in-progress downloads.** Evidence: `src/data/Downloads.kt:51-73,87-112`; `src/ui/Components.kt:330-333,623-630`.

`remove` cancels a coroutine, but blocking `execute()`/`bytes()` continue until they return. Cancellation is checked only at the next loop start. If it arrives during the last response, the job can rename the file and add the library entry after removal. Removing and restarting the same ID can also let an old job's `finally` remove the new job's map entry or its cleanup touch a shared `.part` file. The map check and insertion are not atomic, and a very fast job may finish before its handle is inserted.

**Fix:** connect cancellation to `Call.cancel()`, check cancellation before promotion, and serialize remove/finalize per ID. Use an attempt-specific temporary filename and compare-and-remove job handles. Register a lazy job atomically before starting it. Add a cancel action only once these semantics are reliable.

### M5. Downloads cannot survive process death or reliably finish in the background

**Verified (source).** Evidence: `src/data/Downloads.kt:30-31,39-44,54-65`; `app/src/main/AndroidManifest.xml:36-43`.

Jobs and progress exist only in process memory; the only foreground service is for media playback. Android can kill an otherwise idle app mid-download. Startup deletes `.part` files, with no persisted request or offset. There is also a window between file rename and durable library save in which a completed file becomes an orphan and is deleted next launch.

**Fix:** use Media3's download service/manager or a suitable persistent worker with a foreground progress notification where required. Persist requested song, immutable rendition, total, and verified offset; reconcile completed files conservatively. This needs CI compilation and device testing, but no external service or account.

### M6. Retry/skip treats broad outages as bad songs and can consume a queue

**Verified (source); actual outage classification depends on upstream exceptions.** Evidence: `src/playback/PlaybackService.kt:188-194,205-228,241-255`; `src/data/StreamResolver.kt:90-92`.

Only two Media3 network codes stop automatic advancement. HTTP 429/5xx, extractor failures caused by a blocked connection, and many generic IO errors follow invalidate → retry → skip. Every transition clears `retriedId`; autoplay can extend the sequence. This is not an unlimited immediate retry of one unchanged item, and the two explicitly handled network errors already wait for user action. The missing protection is a queue/session-wide failure budget and better error classification.

**Fix:** classify HTTP/rate-limit/temporary extraction failures as recoverable session problems; stop and offer retry/backoff after a small consecutive-failure budget. Reset the budget after successful playback. Limit fast-path invalidation to failures attributable to that rendition. Combine with H3 so cached parser failures can recover.

### M7. Startup and some UI operations perform filesystem work on main

**Verified (source); ANR/jank depends on library size/storage latency.** Evidence: `src/TuneHubApp.kt:33-46`; `src/data/Library.kt:54,157-158`; `src/data/Downloads.kt:39-44,47,70-76`; `src/ui/SettingsScreen.kt:40`; `src/ui/Components.kt:630`; `src/ui/NowPlaying.kt:213`.

Application startup synchronously reads/parses the whole JSON and scans/deletes download files. Opening Settings scans sizes in composition, and removing all downloads deletes files on the UI thread. Large libraries or slow storage can delay cold start, service startup, and input. Most blocking YouTube/LRCLIB calls are correctly dispatched to IO; the service resolver runs on a loader thread, not main.

**Fix:** expose an explicit loading state and initialize/reconcile on IO before admitting library mutations or destructive cleanup. Cache storage totals from an IO-maintained flow. Move batch deletes off main, coordinating with playback and in-flight downloads. Check with StrictMode/device traces in CI or a debug device build.

### M8. Navigation and playback state are not restored after their owners are destroyed

**Verified (source).** Evidence: `src/ui/MainActivity.kt:107-110`; `src/ui/Nav.kt:24-28`; `src/ui/SearchScreen.kt:86-106`; `src/playback/PlaybackService.kt:62-110,120-131`; `src/ui/PlayerConnection.kt:29-52`.

Rotation recreates the `remember` navigator, resetting tab, route stacks, and Now Playing; activity-scoped ViewModels usually retain search/browse data through that rotation. Process death also loses those ViewModels, the queue, current position, repeat/shuffle state, and timer. Recent songs are not a queue/position snapshot. Background playback survives an ordinary Activity stop while the service lives, so it is incorrect to describe every rotation as restarting audio.

**Fix:** save serializable navigation identifiers and overlay state through `rememberSaveable`/`SavedStateHandle`. Persist a small service-owned playback snapshot periodically and at significant changes; restore paused by default, or implement Media3 playback resumption with a clear user action. Do not restore playback merely because a controller binds.

### M9. Waveform gestures capture an obsolete duration and seek callback

**Verified (source).** Evidence: `src/ui/Glass.kt:193-217`; `src/ui/NowPlaying.kt:243,341-348`.

Both gesture coroutines use `pointerInput(Unit)` and capture `onSeek`. When the waveform remains in composition as duration changes from zero to known, the captured callback can continue seeing zero and ignore seeks. When a new song has another duration, it can seek using the previous song's duration. Changing `seed` redraws bars but does not restart these gesture handlers.

**Fix:** wrap callbacks in `rememberUpdatedState` and invoke their current values inside the gesture coroutine, or key handlers by the relevant callback/song state. Reset drag preview on song changes. Add progress/value/adjustment semantics as described in L4 without changing the waveform's appearance.

### M10. Long sessions retain growing caches, browse ViewModels, and the entire playback history queue

**Verified (source).** Evidence: `src/data/StreamResolver.kt:38-45,58-59,75,85,129`; `src/playback/MediaItems.kt:16,21`; `src/ui/Components.kt:175-187`; `src/data/Lyrics.kt:32-38,52,59`; `src/ui/ArtistScreen.kt:79`; `src/ui/CollectionScreens.kt:73`; `src/ui/SearchScreen.kt:493`; `src/playback/PlaybackService.kt:253-255`; `src/ui/PlayerConnection.kt:83-91`.

Expired URL entries are ignored but not evicted. Locks, related lists, full-only IDs, known songs, art colors, lyrics, and misses grow across a process session. Browse ViewModels are keyed by URL but scoped to the Activity, so popping a custom route does not clear them. Autoplay retains all previously played queue items, and every player event reconstructs the full UI queue on main. Long listening/browsing sessions increase memory and per-event work.

**Fix:** bound URL/related/art/lyrics caches by count/weight and TTL; avoid retaining expired misses forever. Use an in-flight lookup registry whose entries are removed safely after completion. Scope browse ViewModels to route lifetime or bound their retained pages. Trim old queue items outside a limited back-history window, and rebuild the mirrored queue only for timeline changes. Coil's image caches and the 512 MB audio cache already have explicit limits; they are not the unbounded structures.

### M11. Lyrics requests transmit listening metadata automatically when Now Playing opens

**Verified (source).** Evidence: `src/ui/NowPlaying.kt:199-203,245`; `src/data/Lyrics.kt:40-41,63-71`; `src/ui/SettingsScreen.kt:73-74`; `README.md` (“How it works”).

Lyrics load before the user presses the Lyrics chip. LRCLIB receives cleaned title, artist, IP address, and a TuneHub user agent; title-only fallback omits artist. Existing README/Settings credit LRCLIB, so disclosure is not completely absent, but they do not explain automatic title/artist transmission. Heuristic duration matching does not establish track identity and can show a wrong song's lyrics.

**Fix:** disclose the trigger and fields, and allow an opt-out or request lyrics on demand. Require stronger normalized title/artist matching before presenting synchronized text; retain duration as a secondary check. YouTube still necessarily receives search/suggestion text, video IDs, locale/country, and stream requests; artwork hosts receive image requests. GitHub is opened via an external browser only on the update action (`src/ui/SettingsScreen.kt:68-70`); no automatic GitHub update API or analytics upload was found.

### M12. Exported inputs are accepted without a controller policy or strict song-ID validation

**Verified (source): unrestricted acceptance and validation gaps. Suspected: end-to-end abuse beyond playback manipulation.** Evidence: `app/src/main/AndroidManifest.xml:19-32,36-42`; `src/playback/PlaybackService.kt:113,150-154,172-179`; `src/playback/MediaItems.kt:18`; `src/data/Downloads.kt:47`; `src/ui/MainActivity.kt:81-91`; `src/data/YouTube.kt:197-199`.

`onGetSession` returns the session to every caller and the callback inherits Media3's permissive `onConnect`. Another app can replace/control the queue and receive playback metadata. `onAddMediaItems` accepts arbitrary IDs and metadata. IDs become URI path text and decoded final segments become filenames without validating the expected 11-character alphabet or canonical containment. Encoded separators therefore merit an adversarial test for unintended local-file resolution; no arbitrary file deletion or external exfiltration exploit is proven here. Share parsing also matches `v=` anywhere in any text, without checking a YouTube host, so an unrelated URL can initiate playback/network work.

**Fix:** keep export only for intended external integrations and explicitly allow/restrict controllers and their commands using UID/trust policy. Rebuild sanitized items, validate IDs with an anchored pattern, construct URI segments with a builder, and enforce canonical download-directory containment. Parse shared URLs and allow only intended YouTube hosts/path forms. Do not replace the service's intended media controls with a blanket permission that breaks system integrations. Media3's default callback accepts accessible controllers; export is not a requirement to grant every app every command ([pinned MediaSession callback](https://raw.githubusercontent.com/androidx/media/1.5.1/libraries/session/src/main/java/androidx/media3/session/MediaSession.java)).

### M13. Default Android backup policy contradicts a strict “everything stays on your phone” claim

**Verified (source configuration); actual backup is conditional and the merged release manifest was not generated.** Evidence: `app/src/main/AndroidManifest.xml:11-17`; `src/data/Library.kt:49`; `src/data/Downloads.kt:29`; `README.md` (“Your library”).

The app specifies neither `allowBackup` nor backup/extraction rules. Under Android defaults, private files can be included in account-backed Auto Backup and device transfer. Downloaded audio can also exceed the backup quota, jeopardizing useful metadata backups. This is platform-managed backup, not an app-operated backend or evidence that a backup has occurred ([Android Auto Backup defaults and quota](https://developer.android.com/identity/data/autobackup)).

**Fix:** choose and document a backup policy. If retaining backups, include library metadata and exclude downloaded audio and temporary files with rules for supported Android versions. If promising device-only storage, explicitly configure that policy and disclose device-transfer/OEM caveats. Verify the merged manifest and restore behavior in CI/device testing. Platform backup does not replace H1/H2's local recovery protection.

### M14. Version numbering and release publication are neither portable nor safely rerunnable

**Verified (source).** Evidence: `.github/workflows/build.yml:11-13,30-32,68-72`; `app/build.gradle.kts:11,23-24`.

`run_number + 15` is workflow-local, rather than a durable application version sequence. A new/recreated workflow or fork can issue lower codes, blocking in-place upgrades. A rerun has the same run number and release tag, so `gh release create` fails if that release already exists. Manual dispatch from an older ref can also mark older source as latest, and concurrent cancellation can interrupt publishing. Merely editing/renaming a workflow does not prove a reset; verify the workflow identity/history before claiming one ([GitHub run-number semantics](https://docs.github.com/en/actions/reference/workflows-and-actions/variables)).

**Fix:** store an explicit monotonically increasing Android version code in version-controlled release configuration and validate it against the latest published APK/release metadata; do not assume GitHub run IDs fit Android's version-code bounds. Restrict publishing to the intended branch/ref. Make reruns idempotent with tag/commit/certificate checks, stage as draft, upload/verify assets, then promote latest. Never replace an existing version with different signed contents silently.

### M15. Release verification can report success despite crashes and does not exercise minified logic adequately

**Verified (source).** Evidence: `.github/workflows/build.yml:52-55,63-72`; `.github/workflows/ui-check.yml:4-5,23-38,51-67`; `ci/ui-check.sh:4,12-45,110-115`; `app/src/test/java/com/tunehub/app/ExtractionTest.kt:26-84`; `app/proguard-rules.pro:5-43`.

All extraction-test failures are nonblocking, including deterministic regressions unrelated to datacenter blocking. JVM `testReleaseUnitTest` is not execution of the installed R8 APK. The manual emulator script continues after missing nodes/install/interaction failures and ends with `exit 0`, even after logging `FATAL EXCEPTION`. Thus neither a green build nor a green UI-check job establishes working playback or release-safe reflection.

**Fix:** add small deterministic tests for resolver selection/cache identity, persistence failure/recovery/migration, queue generations, lyrics parsing, and navigation. Keep live-network tests separately advisory. Make required UI interactions/install/crash checks fail the emulator step while always uploading artifacts; filter crash evidence to the app process. Run targeted installed-release checks for fast/fallback extraction, serialization round trips including private lyrics DTOs, and effects failure handling. Require deterministic checks before publishing.

**R8 assessment:** no concrete release-only shrinker defect is proven. Rhino, script-engine classes, timeago patterns, protobuf fields, model serializers, and companion access have explicit rules; `-dontobfuscate` prevents renaming but still allows shrinking/optimization. Direct fast-path method calls are statically reachable. Do not add blanket keeps for the whole extractor without an observed failure. Inspect generated R8 configuration/mapping and the actual dependency consumer rules in CI, then test the installed APK; local source inspection cannot certify that output.

## Low severity

### L1. Fast-path failure suppression misses lookup failures and has a non-atomic counter

**Verified (source).** Evidence: `src/data/StreamResolver.kt:45-47,68-75,90-92,97-122`.

Exceptions during fast lookup fall back correctly but never increment `fastFailures`; if reel extraction breaks globally, every new song pays the failed fast lookup before full extraction. The session-disable threshold counts only distinct IDs whose cached fast result later fails playback. `fastFailures++` is not atomic, although normal player-error invalidations are currently on main. Mapping/deobfuscating every candidate can also make one bad candidate force a full fallback even when another is usable.

**Fix:** separately track extraction-path failures and actual stream-playback failures; add bounded session backoff for systemic reel failures and isolate individual malformed candidates. Serialize failure accounting or use an atomic counter if invalidation becomes concurrent. Keep the full fallback. The exact number of upstream requests is variable: the pinned reel helper fetches visitor data before the player response, and deobfuscation may require further work ([NewPipeExtractor v0.26.5 helper](https://raw.githubusercontent.com/TeamNewPipe/NewPipeExtractor/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeStreamHelper.java)).

### L2. Audio-effect broadcasts and band state do not follow the actual session lifecycle

**Verified (source).** Evidence: `src/playback/PlaybackService.kt:97-98,124,135-146,184-185`; `src/playback/AudioFx.kt:35-47,52-58`.

The open broadcast occurs during creation using the current ID, potentially unset. When the real audio-session ID changes, only in-app effects reattach: external equalizers are never told to open the new session or close the previous one. `release()` also leaves `_bands` populated, so the screen can display stale capabilities after teardown.

**Fix:** track the last valid announced ID, close that ID before switching, open the new valid ID, and close the same ID on destruction. Clear `_bands` on release/failure. Keep the effect lifecycle guarded as in H6.

### L3. Transient lyrics errors become permanent misses for the process session

**Verified (source).** Evidence: `src/data/Lyrics.kt:32-53,63-71`.

HTTP errors return an empty list and can populate `misses`, suppressing future attempts even after connectivity returns. Fallback without artist happens only when the raw first response is empty; unusable/instrumental/wrong-duration first results prevent a potentially valid fallback.

**Fix:** distinguish transport failure, parse failure, and a successful empty match; negative-cache only confirmed misses with a short TTL. Perform title-only fallback after filtering unusable candidates. Provide an explicit retry without restarting the app.

### L4. The waveform cannot be adjusted through accessibility services

**Verified (source); contrast remains unverified.** Evidence: `src/ui/Glass.kt:196-218`; `src/ui/NowPlaying.kt:341-348`.

The canvas exposes only “Seek bar,” without range/current value or a set-progress action. TalkBack and other non-touch input cannot perform the custom pointer gestures. Contrast on different artwork glows needs measurement; no contrast failure is established by this review.

**Fix:** add `progressBarRangeInfo`, an accessible time/state description, and `setProgress`/adjustment actions, preserving the existing drawing. Test with TalkBack, keyboard/switch input, and representative artwork/font scales. Include selected-state semantics for custom shuffle/repeat/chip controls where appropriate.

### L5. A device without an ACTION_VIEW handler can crash when opening the update page

**Verified (source), uncommon device configuration.** Evidence: `src/ui/SettingsScreen.kt:68-71`.

`startActivity` is unguarded. A restricted device or emulator without a handler throws `ActivityNotFoundException`.

**Fix:** catch that exception and show a copyable release URL or short error. Keep the destination fixed; no update account/backend is needed.

### L6. “Recently added” sorting is grouped by type, not by actual addition time

**Verified (source).** Evidence: `src/ui/LibraryScreen.kt:39,68-83`; `src/data/Library.kt:76,99-100,107-115`; `src/data/Models.kt:25-39`.

The recent view concatenates playlists, then albums, then artists. A newly followed artist stays below old playlists; album/artist records have no saved-at time. Playlist `updatedAt` is not used for this sort and is not an addition timestamp.

**Fix:** persist a backward-compatible `addedAt` field/default for saved entries and sort the unified list by it, with a stable migration order for existing entries. Alternatively label the current grouping accurately. No visual redesign is necessary.

### L7. System Back closes Now Playing instead of the expanded lyrics overlay first

**Verified (source).** Evidence: `src/ui/MainActivity.kt:124`; `src/ui/Nav.kt:43-47`; `src/ui/NowPlaying.kt:250,399-402,514-574`.

Expanded lyrics has no local `BackHandler`. Pressing Back reaches the root navigator and closes the entire Now Playing screen instead of returning to its underlying playback controls.

**Fix:** register a local back handler while expanded lyrics is visible, then let the existing navigator handle Back after it closes.

## Verdict on all 14 guide risks

These are cross-references, not additional findings. The numbered risks in `docs/CODEBASE.md:155-168` are assessed individually.

| Guide risk | Verdict and severity | Source evidence and conclusion |
|---|---|---|
| 1. Navigation not saved | Confirmed; medium, M8 | `src/ui/MainActivity.kt:107`; `src/ui/Nav.kt:25-28`. Rotation/configuration recreation resets routes; fixed Aurora dark styling does not guarantee every system dark-mode change recreates the Activity. Process death also loses playback state. |
| 2. Downloads not durable | Confirmed; medium, M5 | `src/data/Downloads.kt:30,39-44,54-65`. No durable worker, resume, or download foreground notification; additional finalization hazards in M3/M4. |
| 3. One pending action | Confirmed; medium, M1 | `src/ui/PlayerConnection.kt:52,64-65,95-96`. Includes a seed-loss race within `startRadio`, not just separate user taps. |
| 4. Unbounded maps | Confirmed; medium, M10 | `src/data/StreamResolver.kt:38-45`; `src/playback/MediaItems.kt:16`; `src/ui/Components.kt:175`. Also URL entries, lyrics caches, Activity-scoped browse ViewModels, and autoplay queue history. |
| 5. Global quality cache key | Confirmed and understated; high, H3/H4 | `src/playback/PlaybackService.kt:73`; `src/data/StreamResolver.kt:51-55,74-75`. The key is selected per cache open, not rewritten for each byte; whole incompatible ranges/lookup results can be reused. |
| 6. NewPipe internals | Confirmed dependency/maintenance risk; low, L1 | `src/data/StreamResolver.kt:11-13,99-122`; `app/build.gradle.kts:95-97`. Runtime exceptions invoke fallback; removed/changed method signatures can fail compilation on upgrade, which fallback cannot fix. No current API mismatch proven. |
| 7. Retry ID reset | Partly confirmed; medium, M6 | `src/playback/PlaybackService.kt:192,205-228`. Transitions reset it, but explicit network failure/timeout codes do not skip. Queue churn concerns other error categories and autoplay. |
| 8. Whole JSON and corrupt fallback | Confirmed and understated; high H1/H2, medium M7 | `src/data/Library.kt:152-165`; `src/data/Downloads.kt:39-44`. Writers are serialized correctly, but bad load can erase metadata and downloads. No backup/recovery or checked commit result. |
| 9. Lyrics privacy/matching | Confirmed with correction; medium, M11 | `src/data/Lyrics.kt:40-49,63-71`; `src/ui/NowPlaying.kt:245`. README and Settings already name LRCLIB, but automatic metadata transmission is not explained. |
| 10. AudioFx on main, everything caught | Partly confirmed; high H6, low L2 | `src/playback/PlaybackService.kt:97-100,184-185`; `src/playback/AudioFx.kt:40-47`. Main-thread work and swallowed errors exist; capability getters are **not** caught. OEM exception occurrence needs device testing. |
| 11. Exported service required/unrestricted | Control exposure confirmed; rationale overstated; medium, M12 | Manifest `:36-42`; service `:113,172-179`. External clients need access, but notification support is not a justification for unrestricted controller acceptance. Export and command authorization are separate decisions. |
| 12. No app-logic unit tests | Confirmed; medium, M15 | Only `app/src/test/java/com/tunehub/app/ExtractionTest.kt:26-84`, five live tests. No deterministic mutation, resolver-selection, persistence-failure, parsing, or navigator suite found. |
| 13. Workflow-local version code | Confirmed with qualification; medium, M14 | `.github/workflows/build.yml:32`; `app/build.gradle.kts:23`. New workflow numbering can regress; reruns reuse numbers. A rename alone is not proof of reset. |
| 14. Accessibility/contrast | Seek semantics confirmed; medium functional stale callback M9 and low accessibility L4 | `src/ui/Glass.kt:198-218`. Value/adjustment semantics absent. Contrast suspicion remains unproven without rendered/device measurements. |

## Where CODEBASE.md is inaccurate or incomplete

The package/application ID, dependency versions, file responsibilities, per-tab stacks, main data-source chain, buffer settings, mutex-based writes, and most UI descriptions match this commit. Corrections below should be made in a later documentation change; the guide itself was not modified.

| Guide location | Correction, supporting source, and consequence |
|---|---|
| `docs/CODEBASE.md:10-13,83` | Most metadata uses NewPipe, but the fast path manually parses player JSON through internal helpers. Do not imply all URL selection is performed by the full extractor. `src/data/StreamResolver.kt:97-146`. |
| `docs/CODEBASE.md:73` | Device locale is used, but an empty locale country falls back to `US` (`src/TuneHubApp.kt:38-43`). “Artwork URLs never change” is an assumption, not enforced behavior; channel/banner URLs and contents can change. `respectCacheHeaders(false)` trades freshness for reuse (`:53-59`). |
| `docs/CODEBASE.md:74,165` | Export enables external clients, but broad access is not mandatory for every notification/controller use. There is currently no trust/command policy (M12). |
| `docs/CODEBASE.md:81` | Firefox is a default UA, not an unconditional fixed UA: extractor request headers replace it (`src/data/YtDownloader.kt:18-22`). This is desirable for client-specific requests. |
| `docs/CODEBASE.md:83` | Non-DRC streams are preferred, not guaranteed: both paths fall back to DRC candidates if necessary (`src/data/StreamResolver.kt:111,137`). Fast-path disablement applies only to failed cached fast playback on distinct IDs, not lookup exceptions (`:74,90-92`). Request counts are estimates, not fixed guarantees (L1). |
| `docs/CODEBASE.md:85,134` | A filename's existence is the offline validity check; neither length nor integrity is checked (`src/data/Downloads.kt:47,109-112`). Reconciliation can delete files after a failed library load, not just clean stale entries (H1). |
| `docs/CODEBASE.md:91,143` | Quality suffixes do not prove bytes are never mixed; H3/H4 invalidate that absolute claim. Offline preference also occurs only on cache misses because the resolver is inside the cache. |
| `docs/CODEBASE.md:93,164` | Not all AudioFx operations are wrapped. Capability discovery can throw (H6), and external open/close broadcasts do not track changed session IDs (L2). |
| `docs/CODEBASE.md:111` | The Library exposes a recent-sort label but does not globally sort by saved time (L6). |
| `docs/CODEBASE.md:122` | Signing selection checks only `TUNEHUB_KEYSTORE != null`; it does not verify that all password/alias variables exist or that the certificate matches existing installs (`app/build.gradle.kts:12,28-33,44-47`). |
| `docs/CODEBASE.md:124` | Source shows a live test and nonblocking workflow step, but does not prove every current CI stream failure is caused by datacenter blocking. Logs/runtime evidence would be needed. JVM release tests do not validate installed R8 behavior (M15). |
| `docs/CODEBASE.md:125-126` | One release per run is the intention; reruns are not idempotent. UI-check emits a crash annotation but explicitly exits successfully (`ci/ui-check.sh:110-115`), so “flags” must not be read as “fails the job.” |
| `docs/CODEBASE.md:140,161` | Failure handling only waits for two specific network codes; generic IO/extractor/HTTP outage cases can retry/skip across the queue (M6). |
| `docs/CODEBASE.md:146` | The normal save sequence is accurate, but rename success is unchecked, writes are not acknowledged/durable at mutation return, and load failures are destructive (H1/H2). |
| `docs/CODEBASE.md:149` | Root Back behavior matches, but expanded lyrics lacks its own Back handling (L7). Navigator state and service playback restoration remain distinct (M8). |

## Verification priorities and limits

The source demonstrates proper IO dispatch for the principal network flows, a single process-wide audio cache, service-scope cancellation on destruction, audio-focus/noisy handling, and release of the player/session/effects on the normal destruction path. Those are useful existing protections. A process-scoped coroutine is not automatically a leak; the identified problems concern obsolete work, unbounded retained data, or missing durability.

No proven R8-only crash, arbitrary library-write exploit from another app, universal contrast failure, or guaranteed OEM AudioFx exception is claimed. Verify these conditional concerns using the built release APK. Useful CI/device scenarios are: corrupt-library startup without deleting originals/downloads; failed write/rename; process death between file promotion and metadata save; delayed high/low resolutions; fast-to-full rendition changes with a partly cached song; radio A completing after album B; delayed connection around stop/start; range-ignoring/short HTTP responses; and effect capability failures. Use deterministic fakes/local fixtures where possible so YouTube availability does not decide correctness.

## Top 5 fixes to do first

1. Protect the library and downloaded files: explicit load failures, recoverable atomic writes, checked commits, and conservative reconciliation (H1/H2).
2. Make stream identity and quality immutable across resolution and caching; isolate offline files and invalidate incompatible cached bytes (H3/H4).
3. Give radio/queue requests generations and one service-owned implementation; prevent stale results and pending-command loss (H5/M1).
4. Guard AudioFx capability discovery and attach only to valid sessions so unsupported effects cannot take down playback (H6).
5. Make publishing fail closed on signing identity, enforce monotonic versions, and gate releases on deterministic and meaningful installed-release checks (H7/M14/M15).
