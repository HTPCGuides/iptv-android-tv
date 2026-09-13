package com.iptvtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun `parses attributes, title and url`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1" tvg-logo="https://x/bbc.png" group-title="General",BBC One (720p)
            https://example.com/bbc1.m3u8
        """.trimIndent()
        val channels = M3uParser.parse(text, "uk")
        assertEquals(1, channels.size)
        with(channels[0]) {
            assertEquals("BBC One (720p)", name)
            assertEquals("BBC One", clean)
            assertEquals("720p", quality)
            assertEquals("bbc1", tvgId)
            assertEquals("https://x/bbc.png", logo)
            assertEquals(listOf("General"), groups)
            assertEquals("https://example.com/bbc1.m3u8", url)
            assertEquals("uk", sourceId)
        }
    }

    @Test
    fun `title comma is found after the last quoted attribute`() {
        val text = """
            #EXTINF:-1 group-title="News, Politics",Channel A, the sequel
            http://a
        """.trimIndent()
        val c = M3uParser.parse(text, "s").single()
        assertEquals("Channel A, the sequel", c.name)
        assertEquals(listOf("News, Politics"), c.groups)
    }

    @Test
    fun `annotations drive geo-blocked, part-time and the favourite key`() {
        val text = """
            #EXTINF:-1,Red Bull TV (1080p) [Geo-blocked] [Not 24/7]
            http://a
            #EXTINF:-1,Red Bull TV (720p)
            http://b
        """.trimIndent()
        val (a, b) = M3uParser.parse(text, "sports")
        assertTrue(a.geoBlocked)
        assertTrue(a.partTime)
        assertEquals("Red Bull TV", a.clean)
        // Two feeds of one channel share a favourite key but not a channel key.
        assertEquals(a.favKey, b.favKey)
        assertEquals("sports|red bull tv", a.favKey)
        assertTrue(a.key != b.key)
    }

    @Test
    fun `packed groups split, and empty groups fall back to Other`() {
        val text = """
            #EXTINF:-1 group-title="Comedy;Movies; Sports",X
            http://x
            #EXTINF:-1 group-title="",Y
            http://y
        """.trimIndent()
        val (x, y) = M3uParser.parse(text, "s")
        assertEquals(listOf("Comedy", "Movies", "Sports"), x.groups)
        assertEquals(listOf("Other"), y.groups)
    }

    @Test
    fun `unnamed entries, missing urls and other directives`() {
        val text = """
            #EXTM3U
            #EXT-X-SOMETHING
            #EXTINF:-1 tvg-name="From attribute",
            http://named
            #EXTINF:-1,
            http://unnamed
            #EXTINF:-1,Dangling entry without a url
        """.trimIndent()
        val channels = M3uParser.parse(text, "s")
        assertEquals(listOf("From attribute", "Unnamed"), channels.map { it.name })
        assertNull(channels[0].logo)
    }

    @Test
    fun `empty input gives no channels`() {
        assertTrue(M3uParser.parse("", "s").isEmpty())
        assertTrue(M3uParser.parse("#EXTM3U\n", "s").isEmpty())
    }
}
