package com.iptvtv.ui.player

import com.iptvtv.ui.player.CastController.Companion.castTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class CastTargetTest {

    @Test fun `an Ace Stream engine on this phone can't be reached by the TV`() {
        // The case behind "casting just loops": the TV was told to fetch 127.0.0.1,
        // which on the TV is the TV.
        assertEquals(CastTarget.OnThisDevice, castTarget("http://127.0.0.1:6878/ace/getstream?infohash=abc"))
        assertEquals(CastTarget.OnThisDevice, castTarget("http://localhost:6878/ace/manifest.m3u8?id=abc"))
        assertEquals(CastTarget.OnThisDevice, castTarget("http://[::1]:8080/live.m3u8"))
    }

    @Test fun `an Ace Stream engine elsewhere on the network is sent in its HLS form`() {
        assertEquals(
            CastTarget.Send("http://192.168.1.20:6878/ace/manifest.m3u8?infohash=abc", "application/x-mpegURL"),
            castTarget("http://192.168.1.20:6878/ace/getstream?infohash=abc"),
        )
    }

    @Test fun `raw MPEG-TS is refused before it reaches the TV`() {
        assertEquals(CastTarget.UnsupportedFormat, castTarget("http://example.com/live/channel.ts"))
    }

    @Test fun `HLS, DASH and extensionless streams are sent with their type`() {
        assertEquals(CastTarget.Send("https://cdn.example/a/index.m3u8", "application/x-mpegURL"), castTarget("https://cdn.example/a/index.m3u8"))
        assertEquals(CastTarget.Send("https://cdn.example/a/manifest.mpd", "application/dash+xml"), castTarget("https://cdn.example/a/manifest.mpd"))
        assertEquals(CastTarget.Send("https://cdn.example/live/123", "application/x-mpegURL"), castTarget("https://cdn.example/live/123"))
    }

    @Test fun `an unparseable URL is treated as unreachable, not sent`() {
        assertEquals(CastTarget.OnThisDevice, castTarget("not a url"))
    }
}
