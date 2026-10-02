package com.iptvtv.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.iptvtv.model.Channel
import com.iptvtv.model.Settings
import com.iptvtv.model.Source
import com.iptvtv.model.SourceKind
import com.iptvtv.ui.channels.ChannelsScreen
import com.iptvtv.ui.channels.ChannelsUiState
import com.iptvtv.ui.home.AceEngineState
import com.iptvtv.ui.home.HomeScreen
import com.iptvtv.ui.home.HomeUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen at every size the app ships to. There is no emulator on this box, and the
 * layout bugs that reach a real phone are the ones nobody rendered: pills crushed into a
 * letter-wide column in portrait, a title squeezed to nothing beside a row of buttons.
 *
 * `gradle recordRoborazziDebug` writes the PNGs under app/src/test/screenshots/;
 * `gradle verifyRoborazziDebug` fails when a screen changes, so a regression shows up
 * in review rather than in someone's hand.
 *
 * Loading states are left out on purpose: an indeterminate spinner never lets Compose go
 * idle, so the capture waits out its timeout instead of returning, and a centred spinner
 * has no layout to get wrong.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])   // Robolectric 4.13 has no Android 15 image
class ScreenshotTest {

    // ---- the device classes ----------------------------------------------------------
    // OnePlus 10 (1080x2412 @ ~450dpi), the OnePlus Pad, and a 1080p TV at xhdpi.
    private val devices = listOf(
        "phone-portrait" to "w384dp-h857dp-port-xxhdpi",
        "phone-landscape" to "w857dp-h384dp-land-xxhdpi",
        "tablet" to "w1280dp-h800dp-land-xhdpi",
        "tv" to "w960dp-h540dp-land-television-xhdpi",
    )

    /**
     * A scrolling screen is only judged by its first viewport unless it is also rendered
     * tall enough to show everything at once. The device keeps its real width: making a
     * landscape device taller than it is wide makes Robolectric swap the axes, which once
     * produced a 2600dp-wide "tablet" that no tablet has.
     */
    private fun tall(q: String) = q.replace(Regex("""h\d+dp"""), "h2600dp").replace("-land", "-port")

    private fun shoot(name: String, tall: Boolean = false, content: @Composable () -> Unit) {
        for ((device, qualifiers) in devices) {
            RuntimeEnvironment.setQualifiers("+${if (tall) tall(qualifiers) else qualifiers}")
            RuntimeEnvironment.setFontScale(1f)
            capture("$name-$device${if (tall) "-full" else ""}", content)
        }
        // Large text is where fixed layouts clip first; the phone in portrait is the worst case.
        val phone = devices.first().second
        RuntimeEnvironment.setQualifiers("+${if (tall) tall(phone) else phone}")
        RuntimeEnvironment.setFontScale(1.3f)
        capture("$name-phone-portrait-font130${if (tall) "-full" else ""}", content)
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun capture(file: String, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$file.png") {
            IptvTheme { Surface { content() } }
        }

    // ---- fixtures: real-shaped data, including the awkward cases --------------------
    private val custom = Source("c_1", "My provider — sports & news package", "https://example.com/p.m3u", SourceKind.URL)
    private val file = Source("f_1", "downloaded-list.m3u8", "downloaded-list.m3u8", SourceKind.FILE, "📄")
    private val sources = Source.BUILT_IN + custom + file

    private val channels = listOf(
        Channel("BBC One (1080p)", "https://x/1", group = "General", sourceId = "uk"),
        Channel("Red Bull TV (1080p)", "https://x/2", group = "Outdoor;Sports", sourceId = "uk"),
        Channel("Al Jazeera English Documentary Channel International (720p) [Not 24/7]", "https://x/3", group = "Documentary;News", sourceId = "uk"),
        Channel("ITV2 HD (1080p) [Geo-blocked]", "https://x/4", group = "Entertainment", sourceId = "uk"),
        Channel("Comedy Central (720p)", "https://x/5", group = "Comedy;Entertainment;Movies;Series", sourceId = "uk"),
        Channel("GB News", "https://x/6", group = "News", sourceId = "uk"),
        Channel("Sky News (1080p) [Geo-blocked]", "https://x/7", group = "News", sourceId = "uk"),
        Channel("Kids Zone", "https://x/8", group = "Kids;Animation", sourceId = "uk"),
    )
    private val uk = Source.BUILT_IN.first()

    private val homeReady = HomeUiState.Ready(
        sources = sources,
        favourites = channels.take(3),
        recents = channels.drop(2).take(5),
        settings = Settings(prebufferMs = 10_000),
        cacheBytes = 1_600_000,
        aceEngine = AceEngineState.STOPPED,   // the most buttons the engine card ever shows
    )

    private fun channelsState(
        query: String = "",
        group: String? = null,
        list: List<Channel> = channels,
        refreshing: Boolean = false,
    ) = ChannelsUiState.Success(
        source = uk, channels = list,
        groups = listOf("News", "Entertainment", "Sports", "Comedy", "Documentary", "Kids", "Movies", "Series", "General", "Outdoor", "Animation"),
        favourites = setOf(channels[1].favKey), query = query, group = group, refreshing = refreshing,
    )

    // ---- screens --------------------------------------------------------------------
    @Composable
    private fun Home(state: HomeUiState) = HomeScreen(
        uiState = state, onOpenSource = {}, onPlay = { _, _ -> }, onAddSource = { _, _ -> }, onRemoveSource = {},
        onHideGeoBlocked = {}, onChoosePrebuffer = {}, onClearCache = {},
    )

    @Composable
    private fun Channels(state: ChannelsUiState) = ChannelsScreen(
        uiState = state, onBack = {}, onPlay = { _, _ -> }, onToggleFavourite = {}, onQueryChange = {},
        onGroupChange = {}, onClearFilters = {}, onRefresh = {}, onRename = {},
    )

    @Test fun home() = shoot("home") { Home(homeReady) }
    @Test fun homeFull() = shoot("home", tall = true) { Home(homeReady) }
    @Test fun homeFirstRun() = shoot("home-first-run", tall = true) {
        Home(homeReady.copy(sources = Source.BUILT_IN, favourites = emptyList(), recents = emptyList(), aceEngine = AceEngineState.NOT_INSTALLED, cacheBytes = 0))
    }

    @Test fun channels() = shoot("channels") { Channels(channelsState()) }
    @Test fun channelsRefreshing() = shoot("channels-refreshing") { Channels(channelsState(refreshing = true)) }
    @Test fun channelsNoMatch() = shoot("channels-no-match") { Channels(channelsState(query = "zzz", list = emptyList())) }
    @Test fun channelsError() = shoot("channels-error") {
        Channels(ChannelsUiState.Error(uk, "Couldn't reach iptv-org.github.io — check your connection, then try again."))
    }
}
