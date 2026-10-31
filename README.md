# Spitify

A personal, ad-free music player for Android. Search YouTube Music, tap a song, and it plays
in the background with the screen off. No YouTube page, no video, no ads.

## How it works

There is no hidden browser. The app talks to YouTube Music's own API through
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), gets the direct audio
stream for a song, and plays just that audio in ExoPlayer inside a background media service.
Because YouTube's player never loads, there is nothing to show ads in.

```
Spitify UI (Compose) ──► MediaController ──► PlaybackService (ExoPlayer + MediaSession)
        │                                          │
        └── search / suggestions ──► NewPipeExtractor ◄── stream URL lookup at load time
```

## Features

- YouTube Music search (songs or videos), with suggestions and endless scrolling
- Background playback, with controls in the notification, on the lock screen and from headphones
- Autoplay radio: when the queue runs out it keeps going with related songs (toggle in Library)
- Liked songs, playlists, recently played and quick picks (stored only on the phone)
- Queue: play next, add to queue, jump to any song, remove
- Share any YouTube / YouTube Music link to Spitify to play it

## Install

Every push to `main` builds a signed APK with GitHub Actions and attaches it to the
**latest** release.

1. On your phone, open this repo's **Releases → latest** (signed in to GitHub, since the repo is private).
2. Download `spitify.apk` and open it. Allow "install unknown apps" for your browser when asked.
3. New builds install over the old one and keep your library, because every build is signed
   with the same key (stored in the repo's Actions secrets).

## Build locally (optional)

Open the folder in Android Studio (it sets up the SDK and Gradle wrapper), or with the
Android SDK and Gradle 8.11 installed:

```bash
gradle assembleRelease
```

`gradle testReleaseUnitTest` runs a live smoke test: search, resolve a stream and fetch its
first bytes.

## When it stops working

YouTube changes its internals every few weeks and extraction breaks for a while. The fix is
usually to bump the NewPipeExtractor version in `app/build.gradle.kts` to the
[latest release](https://github.com/TeamNewPipe/NewPipeExtractor/releases) and push.

## Note

For personal use only. This uses YouTube in a way its Terms of Service don't allow, so it's
not meant for publishing or distribution.
