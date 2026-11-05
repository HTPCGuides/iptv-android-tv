package com.iptvtv.ace

import com.iptvtv.data.AceBuild
import com.iptvtv.data.AcePlaylistBuilder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortugalProfileTest {

    private val now = System.currentTimeMillis() / 1000

    /** A source the tool's own filters keep: active, fully available, recently checked. */
    private fun source(name: String, countries: List<String> = emptyList(), languages: List<String> = emptyList()) =
        Item(
            name = name, infohash = name.lowercase().replace(Regex("[^a-z0-9]"), ""), status = 2,
            availability = 1.0, availabilityUpdatedAt = now, countries = countries, languages = languages,
        )

    private fun index(vararg items: Item) = items.map { Channel(it.name, items = listOf(it)) }

    private fun kept(vararg items: Item): List<String> {
        val compiled = Generator.compile(Profiles.PORTUGAL)
        return Generator.select(index(*items), compiled, now).channels.flatMap { ch -> ch.items.map { it.name } }
    }

    @Test
    fun `Portuguese channels are kept by name, whatever their tags`() {
        val names = listOf(
            "SPORT TV 1", "Sport TV3 HD", "sport tv +", "SPORT TV 7", "BTV", "Benfica TV", "Eleven Sports 1 PT",
            "DAZN 1 Portugal", "Canal 11", "RTP1", "RTP 3", "SIC Notícias", "TVI24", "TVI", "CMTV", "Porto Canal",
        )
        assertEquals(names, kept(*names.map { source(it, countries = listOf("gb")) }.toTypedArray()))
    }

    @Test
    fun `look-alike names stay out`() {
        assertEquals(
            emptyList<String>(),
            kept(
                source("Eurosport 1"), source("Sport TV"), source("Polsat Sport"), source("Eleven Sports 1 BE"),
                source("DAZN 1 DE"), source("Sicilia Channel"), source("TVING"), source("Music Box"),
            ),
        )
    }

    @Test
    fun `tagged Portugal or Portuguese is kept, Brazil is not`() {
        assertEquals(
            listOf("Canal Q", "Rede Local"),
            kept(
                source("Canal Q", countries = listOf("pt")),
                source("Rede Local", languages = listOf("por")),
                source("Globo", countries = listOf("br"), languages = listOf("por")),
                source("SporTV", countries = listOf("br")),
                source("RTP Brasil", countries = listOf("br")),   // a Portuguese name, but a Brazilian source
            ),
        )
    }

    @Test
    fun `entries are grouped as Portugal, linked through the engine as MPEG-TS`() {
        val compiled = Generator.compile(Profiles.PORTUGAL)
        val selection = Generator.select(index(source("SPORT TV 1")), compiled, now)
        val text = Generator.render(selection, compiled, "192.168.1.20:6878").text
        assertTrue(text, text.contains("#EXTINF:-1 group-title=\"Portugal\",SPORT TV 1\n"))
        assertTrue(text, text.contains("http://192.168.1.20:6878/ace/getstream?infohash=sporttv1\n"))
    }

    @Test
    fun `the tool's profiles ignore the app-only filter`() {
        val compiled = Generator.compile(Profiles.MPEGTS_ALL)
        val sel = Generator.select(index(source("Eurosport 1"), source("SPORT TV 1")), compiled, now)
        assertEquals(2, sel.sources)
        assertTrue(sel.rejections.stages.none { it.first == "match" })
    }

    @Test
    fun `an unknown saved choice falls back to everything`() {
        assertEquals(Profiles.PORTUGAL, Profiles.choice("portugal"))
        assertEquals(Profiles.MPEGTS_ALL, Profiles.choice("gone"))
    }

    private class FakeEngine(private val channels: List<Channel>) : EngineClient {
        override suspend fun info(addr: String) = EngineInfo("3.1.80", "android", 1)
        override suspend fun searchAll(addr: String, onPage: (Int, Int, Int) -> Unit) = SearchOutcome(channels, null)
    }

    @Test
    fun `nothing Portuguese in the index says so, rather than looking broken`() = runTest {
        val builder = AcePlaylistBuilder(FakeEngine(index(source("Eurosport 1"), source("Globo", countries = listOf("br")))), StandardTestDispatcher(testScheduler))
        val r = builder.build(profile = Profiles.PORTUGAL)
        assertEquals(AceBuild.Failed(Profiles.PORTUGAL.emptyMessage!!), r)
        assertTrue(r.toString(), (r as AceBuild.Failed).message.startsWith("No Portuguese channels are on Ace Stream right now"))
    }

    @Test
    fun `a Portuguese index builds`() = runTest {
        val builder = AcePlaylistBuilder(FakeEngine(index(source("SPORT TV 1"), source("Eurosport 1"))), StandardTestDispatcher(testScheduler))
        val r = builder.build(profile = Profiles.PORTUGAL) as AceBuild.Built
        assertEquals(1, r.sources)
        assertEquals(2, r.channels)
    }
}
