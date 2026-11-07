package com.iptvtv.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.iptvtv.data.db.IptvDatabase
import com.iptvtv.model.Source
import com.iptvtv.model.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The repository against a real in-memory Room database and a fake network, so the SQL
 * that does the filtering is what is under test — no mocking, per the architecture guide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])   // Robolectric 4.13 has no Android 15 image yet; the SQL under test is the same
class OfflineFirstIptvRepositoryTest {

    private val playlists = mutableMapOf<String, String>()
    private var fetches = 0
    private var clock = 1_000_000L

    private lateinit var db: IptvDatabase
    private lateinit var tmp: File
    private lateinit var repo: OfflineFirstIptvRepository

    private val uk = Source.BUILT_IN.first { it.id == "uk" }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), IptvDatabase::class.java)
            .allowMainThreadQueries().build()
        tmp = File.createTempFile("iptv", "").apply { delete(); mkdirs() }
        val store = PreferenceDataStoreFactory.create { File(tmp, "settings.preferences_pb") }
        repo = OfflineFirstIptvRepository(
            dao = db.dao(),
            fetcher = { url -> fetches++; playlists[url] ?: error("no route to $url") },
            settingsStore = SettingsDataSource(store),
            cacheDirs = listOf(File(tmp, "cache").apply { mkdirs() }),
            now = { clock },
        )
        repo.seedBuiltIns()
        playlists[uk.url] = """
            #EXTINF:-1 group-title="General",BBC One (720p)
            http://bbc1
            #EXTINF:-1 group-title="General;News",BBC News (1080p) [Geo-blocked]
            http://bbcnews
            #EXTINF:-1 group-title="Music",Kiss TV
            http://kiss
        """.trimIndent()
    }

    @After
    fun tearDown() {
        db.close()
        tmp.deleteRecursively()
    }

    @Test
    fun `built-ins are seeded once, in order`() = runTest {
        repo.seedBuiltIns()   // a second call must not duplicate or reorder
        assertEquals(Source.BUILT_IN.map { it.id }, repo.sources.first().map { it.id })
    }

    @Test
    fun `refresh fetches when stale and is a no-op while fresh`() = runTest {
        assertEquals(RefreshResult.Updated, repo.refresh(uk))
        assertEquals(3, repo.channels(uk.id, ChannelFilter()).first().size)

        clock += 60 * 60 * 1000
        assertEquals(RefreshResult.Fresh, repo.refresh(uk))
        assertEquals(1, fetches)

        clock += 24 * 60 * 60 * 1000
        assertEquals(RefreshResult.Updated, repo.refresh(uk))
        assertEquals(2, fetches)
    }

    @Test
    fun `a failed fetch keeps what was stored`() = runTest {
        repo.refresh(uk)
        playlists.remove(uk.url)
        val result = repo.refresh(uk, force = true)
        assertTrue(result is RefreshResult.Failed)
        assertEquals(3, repo.channels(uk.id, ChannelFilter()).first().size)
    }

    @Test
    fun `filters run in SQL - search, group and geo-blocked`() = runTest {
        repo.refresh(uk)
        suspend fun names(f: ChannelFilter) = repo.channels(uk.id, f).first().map { it.clean }
        assertEquals(listOf("BBC One", "BBC News"), names(ChannelFilter(query = "bbc")))
        assertEquals(listOf("Kiss TV"), names(ChannelFilter(query = "music")))   // group text matches too
        assertEquals(listOf("BBC News"), names(ChannelFilter(group = "News")))
        assertEquals(listOf("BBC One", "BBC News"), names(ChannelFilter(group = "General")))
        assertEquals(listOf("BBC One", "Kiss TV"), names(ChannelFilter(hideGeoBlocked = true)))
        assertEquals(listOf("General", "News", "Music"), repo.groups(uk.id).first())
    }

    @Test
    fun `favourites are by name and resolve to one channel for Home`() = runTest {
        playlists[uk.url] += "\n#EXTINF:-1,BBC One (1080p)\nhttp://bbc1-hd"
        repo.refresh(uk)
        val bbc = repo.channels(uk.id, ChannelFilter(query = "BBC One")).first()
        assertEquals(2, bbc.size)

        repo.toggleFavourite(bbc[0])
        assertEquals(setOf("uk|bbc one"), repo.favourites.first())
        assertTrue(bbc[1].favKey in repo.favourites.first())   // the other feed counts as the same favourite
        assertEquals(listOf("BBC One"), repo.favouriteChannels.first().map { it.clean })

        repo.toggleFavourite(bbc[1])
        assertTrue(repo.favourites.first().isEmpty())
    }

    @Test
    fun `recents are most recent first and capped`() = runTest {
        repo.refresh(uk)
        val all = repo.channels(uk.id, ChannelFilter()).first()
        all.forEach { clock++; repo.noteWatched(it) }
        clock++; repo.noteWatched(all[0])
        assertEquals(listOf("BBC One", "Kiss TV", "BBC News"), repo.recentChannels.first().map { it.clean })
    }

    @Test
    fun `imported files are stored, never fetched, and re-import keeps the row`() = runTest {
        val text = "#EXTINF:-1,Ace Channel\nhttp://127.0.0.1:6878/ace/getstream?id=1"
        val source = repo.importFile("ace.test", text)!!
        assertEquals(SourceKind.FILE, source.kind)
        assertEquals(RefreshResult.Fresh, repo.refresh(source, force = true))
        assertEquals(0, fetches)

        repo.renameSource(source.id, "My Ace list")
        repo.toggleFavourite(repo.channels(source.id, ChannelFilter()).first().single())
        val again = repo.importFile("ace.test", "$text\n#EXTINF:-1,Second\nhttp://127.0.0.1:6878/ace/getstream?id=2")!!
        assertEquals(source.id, again.id)
        assertEquals("My Ace list", repo.source(source.id).first()!!.name)
        assertEquals(2, repo.channels(source.id, ChannelFilter()).first().size)
        assertEquals(1, repo.favouriteChannels.first().size)

        assertNull(repo.importFile("empty", "#EXTM3U\n"))
        assertEquals(1, repo.sources.first().count { it.isFile })
    }

    @Test
    fun `removing a source takes its channels, favourites and recents with it`() = runTest {
        val source = repo.addUrlSource("Mine", "http://mine/list.m3u")
        playlists[source.url] = "#EXTINF:-1,Only\nhttp://only"
        repo.refresh(source)
        val only = repo.channels(source.id, ChannelFilter()).first().single()
        repo.toggleFavourite(only); repo.noteWatched(only)

        repo.removeSource(source.id)
        assertTrue(repo.sources.first().none { it.id == source.id })
        assertTrue(repo.channels(source.id, ChannelFilter()).first().isEmpty())
        assertTrue(repo.favourites.first().isEmpty())
        assertTrue(repo.recentChannels.first().isEmpty())
    }

    @Test
    fun `clear cache drops downloaded channels but not imported ones`() = runTest {
        repo.refresh(uk)
        val file = repo.importFile("f", "#EXTINF:-1,Kept\nhttp://kept")!!
        repo.clearCache()
        assertTrue(repo.channels(uk.id, ChannelFilter()).first().isEmpty())
        assertEquals(1, repo.channels(file.id, ChannelFilter()).first().size)
        assertEquals(RefreshResult.Updated, repo.refresh(uk))   // stale again, so it re-downloads
    }

    @Test
    fun `settings round-trip`() = runTest {
        assertEquals(2_500, repo.settings.first().prebufferMs)
        repo.setPrebufferMs(60_000); repo.setSubtitlesOn(true); repo.setHideGeoBlocked(true)
        val s = repo.settings.first()
        assertEquals(60_000, s.prebufferMs)
        assertTrue(s.subtitlesOn && s.hideGeoBlocked)
    }

    private val aceText = """
        #EXTM3U
        #EXTINF:-1 group-title="sport",Ace One
        http://127.0.0.1:6878/ace/getstream?infohash=aaa
        #EXTINF:-1 group-title="movies",Ace Two
        http://127.0.0.1:6878/ace/getstream?infohash=bbb
    """.trimIndent()

    @Test
    fun `each Ace build is a new dated version, and favourites carry across versions`() = runTest {
        val v1 = repo.saveAceVersion(aceText)!!
        clock += 60_000
        val v2 = repo.saveAceVersion(aceText)!!
        assertTrue(v1.id != v2.id)
        assertEquals(SourceKind.ACE, v2.kind)
        assertTrue(v2.name.startsWith("Ace Stream · "))
        assertTrue(v2.url.matches(Regex("""ace-stream-\d{4}-\d{2}-\d{2}-\d{4}\.m3u8""")))
        assertEquals(2, repo.sources.first().count { it.isAce })

        // Starred in the old version, still starred in the new one.
        repo.toggleFavourite(repo.channels(v1.id, ChannelFilter(query = "Ace One")).first().single())
        assertTrue(repo.channels(v2.id, ChannelFilter(query = "Ace One")).first().single().favKey in repo.favourites.first())
        assertEquals(listOf("Ace One"), repo.favouriteChannels.first().map { it.clean })

        assertEquals(RefreshResult.Fresh, repo.refresh(v1, force = true))   // a version never changes
        assertNull(repo.saveAceVersion("#EXTM3U\n"))
    }

    @Test
    fun `a Portugal build is named for its profile, and the choice is remembered`() = runTest {
        val v = repo.saveAceVersion(aceText, label = "Portugal")!!
        assertTrue(v.name, v.name.startsWith("Ace Stream · Portugal · "))
        assertTrue(v.url, v.url.matches(Regex("""ace-stream-portugal-\d{4}-\d{2}-\d{2}-\d{4}\.m3u8""")))
        assertEquals("mpegts_all", repo.settings.first().aceProfile)
        repo.setAceProfile("portugal")
        assertEquals("portugal", repo.settings.first().aceProfile)
    }

    @Test
    fun `keep newest 10 prunes older Ace versions, keep all does not`() = runTest {
        repeat(12) { clock += 60_000; repo.saveAceVersion(aceText) }
        assertEquals(12, repo.sources.first().count { it.isAce })
        repo.setAceKeepVersions(10)
        val left = repo.sources.first().filter { it.isAce }
        assertEquals(10, left.size)
        clock += 60_000
        val newest = repo.saveAceVersion(aceText)!!
        assertEquals(10, repo.sources.first().count { it.isAce })
        assertTrue(repo.sources.first().any { it.id == newest.id })
    }

    @Test
    fun `several sources merge into one list, a feed in both shown once`() = runTest {
        repo.refresh(uk)
        val v1 = repo.saveAceVersion(aceText)!!
        clock += 60_000
        val v2 = repo.saveAceVersion(aceText)!!
        val merged = repo.channels(listOf(uk.id, v1.id, v2.id), ChannelFilter()).first()
        assertEquals(3 + 2, merged.size)
        assertTrue(repo.groups(listOf(uk.id, v1.id)).first().containsAll(listOf("General", "sport")))
        repo.removeSources(listOf(v1.id, v2.id))
        assertTrue(repo.sources.first().none { it.isAce })
    }

    @Test
    fun `removed playlists come back as they were, favourites and place included`() = runTest {
        repo.refresh(uk)
        val v1 = repo.saveAceVersion(aceText)!!
        val before = repo.sources.first().map { it.id }
        repo.toggleFavourite(repo.channels(uk.id, ChannelFilter()).first().first())

        val removed = repo.removeSourcesUndoably(listOf(uk.id, v1.id))
        assertEquals(1, removed.count)   // built-ins aren't removable
        assertTrue(repo.sources.first().none { it.id == v1.id })

        repo.restoreSources(removed)
        assertEquals(before, repo.sources.first().map { it.id })
        assertEquals(2, repo.channels(v1.id, ChannelFilter()).first().size)
        assertEquals(1, repo.favourites.first().size)
    }

    @Test
    fun `channels leave every version shown and come back on undo, in order`() = runTest {
        val v1 = repo.saveAceVersion(aceText)!!
        clock += 60_000
        val v2 = repo.saveAceVersion(aceText)!!
        val ids = listOf(v1.id, v2.id)
        val first = repo.channels(ids, ChannelFilter()).first().first()

        val removed = repo.removeChannels(ids, listOf(first.url))
        assertEquals(1, removed.count)
        assertTrue(repo.channels(ids, ChannelFilter()).first().none { it.url == first.url })

        repo.restoreChannels(removed)
        assertEquals(first, repo.channels(v1.id, ChannelFilter()).first().first())
    }

    @Test
    fun `a playlist exports as m3u that reads back the same`() = runTest {
        repo.refresh(uk)
        val text = repo.playlistText(listOf(uk.id))
        val back = M3uParser.parse(text, uk.id)
        assertEquals(repo.channels(uk.id, ChannelFilter()).first(), back)
    }
}
