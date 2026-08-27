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

**Home**
- Favourites and Recently watched, resolved back to live channels from the cached playlists
- Clear cached data, with the current cache size shown

## Building

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleSlim           # debug-signed but R8-minified, ~5 MB — for slow installs
```

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
