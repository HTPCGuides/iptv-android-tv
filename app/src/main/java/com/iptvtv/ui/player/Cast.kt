package com.iptvtv.ui.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

/**
 * Casting, the way Plex and YouTube do it: find the receivers on the network,
 * hand one a URL, and get out of the way.
 *
 * The picker is drawn in Compose rather than using the framework's
 * `MediaRouteButton`. That button insists on an AppCompat theme, and this app's
 * theme is plain `android:Theme.Material` — adding AppCompat purely to host one
 * button would be a dependency for a widget we then have to restyle anyway.
 * `MediaRouter` does the discovery either way; only the dialog differs.
 *
 * **Cast needs Google Play services, which is the interesting caveat for this
 * app.** It is Android TV first, and plenty of TV boxes and every Fire TV stick
 * ship without them — and casting *from* the TV you are watching makes no sense
 * anyway. So every entry point here degrades to "unavailable" rather than
 * throwing, and the player simply doesn't draw a cast button on those devices.
 */
class CastController(context: Context) {

    /** Null when Play services are missing — the honest "no casting here" state. */
    private val castContext: CastContext? =
        runCatching { CastContext.getSharedInstance(context) }.getOrNull()

    private val router = MediaRouter.getInstance(context.applicationContext)
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(
            CastMediaControlIntent.categoryForCast(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
        .build()

    val available: Boolean get() = castContext != null

    /** Receivers discovered on the network, live. */
    val routes = mutableStateListOf<MediaRouter.RouteInfo>()

    var connecting by mutableStateOf(false)
        private set
    var deviceName by mutableStateOf<String?>(null)
        private set

    val casting: Boolean get() = deviceName != null

    /** What to play once a session actually starts. Set before connecting. */
    private var pending: Pair<String, String>? = null

    private val routerCallback = object : MediaRouter.Callback() {
        private fun refresh() {
            routes.clear()
            routes.addAll(router.routes.filter { it.matchesSelector(selector) && !it.isDefault })
        }
        override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            connecting = false
            deviceName = session.castDevice?.friendlyName
            pending?.let { (url, title) -> play(url, title) }
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            deviceName = session.castDevice?.friendlyName
        }
        override fun onSessionEnded(session: CastSession, error: Int) { deviceName = null; connecting = false }
        override fun onSessionStartFailed(session: CastSession, error: Int) { deviceName = null; connecting = false }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) { connecting = true }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) { deviceName = null }
    }

    fun start() {
        val cc = castContext ?: return
        // ACTIVE_SCAN is the difference between "a Chromecast appears in a few
        // seconds" and "a Chromecast appears eventually". It costs battery, so
        // it only runs while the picker or the player is on screen.
        router.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        cc.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        deviceName = cc.sessionManager.currentCastSession?.castDevice?.friendlyName
        routes.clear()
        routes.addAll(router.routes.filter { it.matchesSelector(selector) && !it.isDefault })
    }

    fun stop() {
        val cc = castContext ?: return
        router.removeCallback(routerCallback)
        cc.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }

    /**
     * Connect to [route] and queue [url] to start when the session is up.
     *
     * Selecting the route is what actually opens the session; the load has to
     * wait for `onSessionStarted`, because a RemoteMediaClient does not exist
     * until then. Doing it in the wrong order is the classic silent no-op.
     */
    fun connect(route: MediaRouter.RouteInfo, url: String, title: String) {
        pending = url to title
        connecting = true
        router.selectRoute(route)
    }

    /** Send a new stream to an already-connected receiver — used by channel up/down. */
    fun play(url: String, title: String) {
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)   // IPTV has no seekable timeline
            .setContentType(contentTypeFor(url))
            .setMetadata(meta)
            .build()
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
        pending = null
    }

    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
        router.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
        deviceName = null
    }

    companion object {
        /**
         * The receiver decides what it can play from the MIME type, so guessing
         * badly is the difference between a stream and a black TV.
         *
         * Raw MPEG-TS is the one to know about: a stock Chromecast cannot play
         * it, whatever we claim here. Those channels play locally and refuse to
         * cast, and the player says so rather than leaving someone staring at a
         * spinner on the television.
         */
        fun contentTypeFor(url: String): String {
            val u = url.substringBefore('?').lowercase()
            return when {
                u.endsWith(".m3u8") -> "application/x-mpegURL"
                u.endsWith(".mpd") -> "application/dash+xml"
                u.endsWith(".mp4") -> "video/mp4"
                u.endsWith(".webm") -> "video/webm"
                u.endsWith(".ts") -> "video/mp2t"
                // An extensionless IPTV URL is very nearly always HLS.
                else -> "application/x-mpegURL"
            }
        }

        /** True for streams a stock Chromecast receiver will refuse. */
        fun castable(url: String): Boolean = contentTypeFor(url) != "video/mp2t"
    }
}

/** A controller scoped to the composable that shows it, scanning only while visible. */
@Composable
fun rememberCastController(): CastController {
    val ctx = LocalContext.current
    val controller = remember { CastController(ctx) }
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.stop() }
    }
    return controller
}
