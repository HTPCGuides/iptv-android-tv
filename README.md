# IPTV TV

An Android TV / Android IPTV player for M3U playlists, built with Jetpack Compose and
Media3. It ships with four free-to-air playlists from [iptv-org](https://github.com/iptv-org/iptv)
and takes any M3U or M3U8 URL you point it at.

Built for the remote first — every screen is navigable by D-pad — but it installs and
works the same on a phone or tablet.

## What it does

**Channels**
- Four built-in iptv-org sources: United Kingdom, Denmark, United States, Sports
- Add your own playlists by pasting an M3U/M3U8 URL; remove them again from the home screen
- Search, and filter by group — playlists that pack several groups into one attribute
  (`group-title="Comedy;Movies;Sports"`) are split into individual chips
- Optionally hide geo-blocked channels, which only play from inside their own country

**Watching**
- HLS, DASH and progressive streams via Media3 / ExoPlayer
- Immersive full-screen playback; the screen won't sleep mid-stream
- Channel up/down zapping through the list you came from, by remote or on-screen buttons
- Subtitle toggle, remembered across channels and restarts
- Cast to a Chromecast or Google TV
- Dead and geo-blocked streams get a clear message and a retry rather than a black screen —
  public IPTV lists are full of them

- Pre-buffer setting (1 s to 60 s) for patchy connections; a stream that drops out is
  restarted from live a few times before the error screen appears
- Press and hold a channel to copy its stream address (acestream ids included)

**Home**
- Favourites and Recently watched, resolved back to live channels
- Playlist files handed over by other apps (acestream-m3u, a file manager) are stored as
  their own source; opening the same file again keeps its name and favourites
- Clear cached data, with the current cache size shown

## Architecture

Laid out on Google's architecture guidance (the NowInAndroid patterns), at the scale of a
single-module app:

- `model/` — pure Kotlin domain models: `Channel`, `Source`, `Settings`.
- `data/` — `IptvRepository` (an interface; everything exposed as `Flow`) implemented by
  `OfflineFirstIptvRepository`. Room (`data/db`) is the source of truth: playlists are parsed
  into a `channels` table as they are downloaded or handed over, and search, group and
  geo-blocked filters run in SQL. Settings live in Preferences DataStore. `PlaybackSession`
  holds what is playing, app-wide. `LegacyMigration` carries pre-Room data over once.
- `ui/` — each screen is a `Route` (ViewModel wiring) over a stateless `Screen` that takes a
  sealed `UiState` and callbacks: `home/`, `channels/`, `player/`. Navigation is
  navigation-compose with type-safe routes. ViewModels are built from a hand-wired
  `AppContainer`; there is no DI framework.
- `app/src/test` — the M3U parser, and the repository against an in-memory Room database
  with a fake network (Robolectric, no mocking library).

## Building

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleSlim           # debug-signed but R8-minified, ~5 MB — for slow installs
./gradlew testDebugUnitTest      # parser + repository tests
```

Debug builds are signed with the key committed at `app/debug.keystore`, so every machine
produces the same signature and updates install over each other.

Install to a connected device or emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`minSdk` is 23, so it covers older Android TV boxes and Fire TV sticks. The app declares
`LEANBACK_LAUNCHER`, so on Android TV it appears on the home row; leanback is not marked
*required*, so it still installs on phones and tablets.

## Notes

- Nothing is bundled with the app but the playlist URLs — no streams are hosted or
  redistributed here. What you get is whatever iptv-org currently lists, or whatever
  playlist you supply.
- Geo-blocked channels need an exit in the right country to play. The app flags them
  rather than hiding the reason.
