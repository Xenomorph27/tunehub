# TuneHub

A personal, ad-free music player for Android. Search YouTube Music, tap a song, and it plays
in the background with the screen off. No YouTube page, no video, no ads.

## How it works

There is no hidden browser. The app talks to YouTube Music's own API through
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), gets the direct audio
stream for a song, and plays just that audio in ExoPlayer inside a background media service.
Because YouTube's player never loads, there is nothing to show ads in.

```
TuneHub UI (Compose) ──► MediaController ──► PlaybackService (ExoPlayer + MediaSession)
        │                                          │
        └── search / suggestions ──► NewPipeExtractor ◄── stream URL lookup at load time
```

## Features

- **Search** YouTube Music with filters: All, Songs, Artists, Albums, Playlists, Videos.
  "All" shows a top result plus a few of each. Suggestions while typing, recent searches,
  genre tiles to browse.
- **Artist pages**: banner, popular songs, discography, music videos, Follow.
- **Album and playlist pages**: save to your library, play, shuffle, download all.
- **Your Library**: Liked Songs, Downloads, your playlists (collage covers), saved albums and
  playlists, followed artists, with filter chips and A–Z / recent sorting.
- **Aurora Glass design**: deep-space background whose glow follows the album art,
  frosted-glass cards, floating pill navigation.
- **Now Playing**: round cover inside a live progress ring, waveform seek bar, swipe the
  cover to skip, time-synced lyrics (from LRCLIB), sleep timer, share, queue (reorder,
  remove, clear), and a badge showing the stream quality.
- **Song menu** (⋮ or long-press anywhere): like, add to playlist, play next, add to queue,
  song radio, download, go to artist, share.
- **Background playback** with notification, lock-screen and headphone controls.
- **Autoplay radio**: when the queue runs out it keeps going with related songs.
- **Offline downloads**, a 512 MB streaming cache, data-saver quality, equalizer shortcut.
- **Best available audio**: the highest-bitrate original stream YouTube offers without
  Premium (usually Opus ~160 kbps), skipping the compressed "DRC" copies.
- **Share** any YouTube / YouTube Music link to TuneHub to play it.

Everything you save stays on your phone.

## Install

Every push to `main` builds a signed APK with GitHub Actions and attaches it to the
**latest** release.

1. On your phone, open this repo's **Releases → latest**.
2. Download `tunehub.apk` and open it. Allow "install unknown apps" for your browser when asked.
3. New builds install over the old one and keep your library, because every build is signed
   with the same key (stored in the repo's Actions secrets).

## Build locally (optional)

Open the folder in Android Studio (it sets up the SDK and Gradle wrapper), or with the
Android SDK and Gradle 8.11 installed:

```bash
gradle assembleRelease
```

`gradle testReleaseUnitTest` runs live smoke tests: search, artist and album pages, lyrics,
and a stream fetch (which YouTube blocks from CI servers, so CI only warns on it).

The **UI check** workflow (Actions tab → Run workflow) boots an emulator, clicks through
search, an artist, an album, playback, the library and settings, and uploads screenshots.

## When it stops working

YouTube changes its internals every few weeks and extraction breaks for a while. The fix is
usually to bump the NewPipeExtractor version in `app/build.gradle.kts` to the
[latest release](https://github.com/TeamNewPipe/NewPipeExtractor/releases) and push.

## Note

For personal use only. This uses YouTube in a way its Terms of Service don't allow, so it's
not meant for publishing or distribution.
