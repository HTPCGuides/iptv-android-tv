package com.iptvtv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AceLinkTest {
    @Test
    fun `engine urls give the acestream link`() {
        assertEquals("acestream://20db14f6", aceLink("http://127.0.0.1:6878/ace/getstream?infohash=20db14f6"))
        assertEquals("acestream://abc", aceLink("http://127.0.0.1:6878/ace/manifest.m3u8?id=abc&pid=1"))
        assertEquals("acestream://c1", aceLink("http://localhost:6878/ace/getstream?content_id=c1"))
    }

    @Test
    fun `ordinary streams and bare engine urls give nothing`() {
        assertNull(aceLink("https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8"))
        assertNull(aceLink("http://127.0.0.1:6878/ace/getstream"))
        assertNull(aceLink("http://127.0.0.1:6878/ace/getstream?infohash="))
    }
}
