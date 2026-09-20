package com.iptvtv.ui

import com.iptvtv.data.AceStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AceLinkTest {
    @Test
    fun `engine urls give the acestream link`() {
        assertEquals("acestream://20db14f6", AceStream.link("http://127.0.0.1:6878/ace/getstream?infohash=20db14f6"))
        assertEquals("acestream://abc", AceStream.link("http://127.0.0.1:6878/ace/manifest.m3u8?id=abc&pid=1"))
        assertEquals("acestream://c1", AceStream.link("http://localhost:6878/ace/getstream?content_id=c1"))
    }

    @Test
    fun `ordinary streams and bare engine urls give nothing`() {
        assertNull(AceStream.link("https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8"))
        assertNull(AceStream.link("http://127.0.0.1:6878/ace/getstream"))
        assertNull(AceStream.link("http://127.0.0.1:6878/ace/getstream?infohash="))
    }

    @Test
    fun `getstream is played as hls, everything else as is`() {
        val ts = "http://127.0.0.1:6878/ace/getstream?infohash=20db14f6&pid=7"
        assertEquals("http://127.0.0.1:6878/ace/manifest.m3u8?infohash=20db14f6&pid=7", AceStream.hlsUrl(ts))
        assertTrue(AceStream.isProgressive(ts))
        val hls = "http://127.0.0.1:6878/ace/manifest.m3u8?infohash=20db14f6"
        assertEquals(hls, AceStream.hlsUrl(hls))
        assertFalse(AceStream.isProgressive(hls))
        val other = "http://example.com/live/getstream?x=1"
        assertEquals(other, AceStream.hlsUrl(other))
    }
}
