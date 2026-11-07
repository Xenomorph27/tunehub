<div align="center">

<img src="docs/icon.png" width="96" alt="TuneHub icon">

# TuneHub

**Ad-free music for Android, from YouTube Music.**
Search anything, tap play, lock your phone. No ads, no account, no subscription.

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-6FF3FF?logo=android)
[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-FF3FB4)](LICENSE)

</div>

## Install

1. Transfer the signed `TuneHub-1.1.apk` from the release package to your Android phone.
2. Open the file, allow installation from your file manager if prompted, then tap **Install**.

Updates to this release use the same package ID and signing key. To move from an older preview,
use **Settings → Export backup** there, then **Settings → Restore from backup** here.

## What it does

- **Fast start**: one request to YouTube before audio begins, and songs start loading the
  moment your finger touches them.
- **Sound quality menu** with Hi-Res and Data Saver on the Now Playing screen, and **UltraTune**: one tap for deep bass,
  clear vocals, crisp highs, a wide 3D stage and extra loudness.
- **Built-in equalizer** with presets, per-band sliders, bass boost, 3D sound and loudness.

- **Search with filters**: All, Songs, Artists, Albums, Playlists, Videos. "All" gives a top
  result plus a few of each, with suggestions as you type, recent searches and genre tiles.
- **Artist pages** with popular songs and discography. Follow your favourites.
- **Albums and playlists**: save them, play, shuffle or download the whole thing.
- **Your library**: Liked Songs, Downloads, your own playlists, saved albums and followed
  artists, with filters and sorting. Everything stays on your phone.
- **Now Playing**: framed square cover and an amber segmented seek bar, swipe the
  cover to skip, time-synced lyrics, sleep timer, queue editing and a stream-quality badge.
- **Background playback** with lock-screen, notification and headphone controls.
- **Radio and autoplay**: start a radio from any song; when your queue ends it keeps going
  with related music. Home builds radio mixes from the artists you play.
- **Offline downloads** and a 512 MB streaming cache.
- **Import playlists** from Spotify or YouTube Music links, or from a CSV / text song list
  (Amazon Music, Apple Music and others via a converter such as TuneMyMusic). You review the
  matches before saving.
- **Backup & restore**: automatic backup to Download/TuneHub, plus export / restore anywhere.
- **Share to TuneHub**: share any YouTube or YouTube Music link to the app to play it.

## How it works

TuneHub never loads YouTube's player, which is where the ads live. It asks YouTube Music for
search results and the song's audio stream directly, using
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), and plays that audio
in ExoPlayer inside a background media service.

```
TuneHub UI (Jetpack Compose) ──► MediaController ──► PlaybackService (ExoPlayer + MediaSession)
        │                                                   │
        └── search, artists, albums ──► NewPipeExtractor ◄──┘ audio stream lookup at play time
```

Audio is the best stream YouTube serves without Premium, usually Opus at about 160 kbps.
TuneHub skips YouTube's dynamic-range-compressed copies of the same audio. Lyrics come from
[LRCLIB](https://lrclib.net).

## Known limits

- **YouTube changes things.** Every few weeks something on YouTube's side shifts and playback
  can break until NewPipeExtractor ships a fix. Updating the extractor version in
  `app/build.gradle.kts` and pushing is usually all it takes.
- **No account features.** There's no sign-in, so no YouTube Music history or Premium
  256 kbps audio.
- **Not on the Play Store**, and it won't be.

## Build it yourself

Open the project in Android Studio or use JDK 17, Android SDK Platform 35, and Gradle 8.11.1.

```bash
gradle assembleRelease lintRelease testReleaseUnitTest --tests 'com.tunehub.app.unit.*'
```

- See [release setup](docs/FORK-RELEASE.md) for signing and GitHub Actions configuration.
- `gradle testReleaseUnitTest --tests com.tunehub.app.ExtractionTest` runs live smoke tests against YouTube Music and LRCLIB.
- The **UI check** workflow (Actions tab → Run workflow) boots an emulator, clicks through
  the main screens and uploads screenshots.

## License and disclaimer

TuneHub is free software under the [GNU GPL v3](LICENSE), the same license as
NewPipeExtractor, which it builds on.

This build is based on [the original TuneHub project](https://github.com/Xenomorph27/tunehub).

It's a personal project, not affiliated with YouTube or Google. Using it may go against
YouTube's Terms of Service, so use it at your own discretion.
