# What TuneHub still lacks

Compared with similar open-source YouTube Music clients (Metrolist, InnerTune / OuterTune,
SimpMusic), mainstream apps (Spotify, YouTube Music, Apple Music) and lossless players such as
Monochrome. Checked November 2026. Effort: S = an hour or two, M = a day or so, L = several days.

## Worth doing next (high value, low effort)

| Feature | Who has it | Effort | Notes |
|---|---|---|---|
| **In-app update check** | common in sideloaded apps | S | Ask the GitHub releases API on launch and show "Build N is available". The biggest win for friends who installed once. |
| **Audio normalization** | Metrolist, InnerTune, OuterTune, Spotify | S | YouTube reports each track's loudness; apply a gain so songs play at the same volume. |
| **Skip silence** | Metrolist, InnerTune | S | One ExoPlayer setting. |
| **Tempo and pitch control** | Metrolist, OuterTune | S | `PlaybackParameters`; a sheet with two sliders. |
| **Reorder playlist songs and the queue by dragging** | everyone | M | `Library.moveInPlaylist` already exists; needs drag handles in the UI. |
| **Backup and restore the library** | Metrolist, OuterTune | S | Export / import `library.json` through the system file picker. |
| **Home screen widget** | Metrolist, InterTune | M | Glance widget with art, title, play/pause, next. |
| **Light and pure-black (AMOLED) themes** | Metrolist and most apps | M | Today there's only Aurora dark. |

## Big features (more work, clear value)

| Feature | Who has it | Effort | Notes |
|---|---|---|---|
| **Android Auto** | SimpMusic, OuterTune, InnerTune, Metrolist | M | Switch to `MediaLibraryService` and expose a browse tree. Sideloaded apps need "Unknown sources" in Android Auto's developer settings. |
| **Real YouTube Music browse: charts, moods & genres, podcasts** | SimpMusic, Metrolist | M | Today genres are searches and Home has a trending kiosk. |
| **Durable downloads** | everyone | M | WorkManager with a progress notification, resume, and a "download over Wi-Fi only" option. |
| **Crossfade** | Spotify, Apple Music | M | Needs two players or a custom audio processor. |
| **Lyrics: more sources and translation** | SimpMusic (LRCLIB, YouTube transcripts, AI translation), Metrolist (AI translation) | M | Add a second source when LRCLIB misses; translation needs an API. |
| **Listening stats** | Metrolist ("stats"), Spotify Wrapped | M | Count plays per song and artist locally; a stats screen. |
| **Import playlists** | Metrolist | M | Paste a YouTube Music playlist link (works with today's code); Spotify links would need matching songs by name. |
| **Local music files** | OuterTune | M | Play MP3 / FLAC files from the phone alongside streaming. |

## Account and social (heavier, riskier)

| Feature | Who has it | Effort | Notes |
|---|---|---|---|
| **YouTube Music account login and sync** | Metrolist, InnerTune, OuterTune | L | Liked songs, history and personal recommendations from the user's account. Handling Google cookies safely is the hard part. |
| **Listen together** | Metrolist | L | Needs a small relay server for synced sessions. |
| **Discord Rich Presence** | SimpMusic | M | Shows "Listening on TuneHub" in Discord; needs a Discord login flow. |
| **Scrobbling (Last.fm / ListenBrainz)** | Pano Scrobbler (Metrolist recommends it) | S–M | Or rely on Pano Scrobbler reading the media session, which already works. |
| **SponsorBlock / Return YouTube Dislike** | SimpMusic | S | Skips non-music intros on music videos. |
| **Chromecast** | Spotify, YouTube Music | M | Media3 has a Cast extension. |

## Audio quality

| Feature | Who has it | Effort | Notes |
|---|---|---|---|
| **Lossless / hi-res source** | Monochrome (TIDAL) | L | TuneHub already plays the best YouTube serves without Premium (Opus ~160 kbps, non-DRC). Lossless would need a TIDAL-backed source through unofficial third-party servers: frequent outages and a much grayer legal area. |
| **256 kbps AAC** | YouTube Music Premium | L | Only for logged-in Premium accounts. |

## Already on par

Ad-free playback, background play with lock-screen controls, filtered search with
suggestions, artist / album / playlist pages, liked songs, playlists, saved albums and followed
artists, offline downloads, streaming cache, synced lyrics, sleep timer, queue editing, radio
and autoplay, equalizer with presets, UltraTune, Hi-Res toggle, share-to-play, data saver.

## Sources

- [Metrolist (MetrolistGroup/Metrolist)](https://github.com/MetrolistGroup/Metrolist)
- [SimpMusic on F-Droid](https://f-droid.org/packages/com.maxrave.simpmusic)
- [SimpMusic on AlternativeTo](https://www.alternativeto.net/software/simpmusic/about/)
- [OuterTune on F-Droid](https://f-droid.org/ko/packages/com.dd3boh.outertune/)
- [OuterTune on AlternativeTo](https://alternativeto.net/software/outertune/about)
- [InnerTune (HiSubway/InnerTune)](https://github.com/HiSubway/InnerTune)
- [InterTune on F-Droid](https://f-droid.org/packages/dev.skye.intertune/)
