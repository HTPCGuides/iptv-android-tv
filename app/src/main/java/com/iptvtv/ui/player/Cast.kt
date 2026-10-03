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
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.iptvtv.data.AceStream
import java.net.URI

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

    /**
     * What the TV says it is doing with the stream we sent. Without this the phone said
     * "Playing on TV" while the TV sat on a spinner forever — the loop that was reported.
     */
    var receiver by mutableStateOf(Receiver.IDLE)
        private set
    /** Why the TV gave up, in its own words where it gives any. Set with [Receiver.FAILED]. */
    var receiverError by mutableStateOf<String?>(null)
        private set

    private var client: RemoteMediaClient? = null
    private val clientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status = client?.mediaStatus ?: return
            when (status.playerState) {
                MediaStatus.PLAYER_STATE_PLAYING, MediaStatus.PLAYER_STATE_PAUSED -> receiver = Receiver.PLAYING
                MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> receiver = Receiver.LOADING
                MediaStatus.PLAYER_STATE_IDLE -> when (status.idleReason) {
                    MediaStatus.IDLE_REASON_ERROR -> fail("The TV couldn't open the stream.")
                    // A live channel has no end; "finished" means the stream dropped out.
                    MediaStatus.IDLE_REASON_FINISHED -> fail("The stream stopped on the TV.")
                    else -> Unit   // a new load replacing the last one, or a stop we asked for
                }
            }
        }
    }

    private fun fail(why: String) {
        receiver = Receiver.FAILED
        receiverError = why
    }

    private fun attach(session: CastSession) {
        val c = session.remoteMediaClient ?: return
        if (c === client) return
        client?.unregisterCallback(clientCallback)
        client = c.also { it.registerCallback(clientCallback) }
    }

    private fun detach() {
        client?.unregisterCallback(clientCallback)
        client = null
        receiver = Receiver.IDLE
        receiverError = null
    }

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
        // Starting the stream is the player's job, not this callback's: the player reacts to
        // [casting] turning true and sends the current channel. Doing it here as well loaded
        // every cast twice, the second load cutting off the first.
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            connecting = false
            attach(session)
            deviceName = session.castDevice?.friendlyName
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            attach(session)
            deviceName = session.castDevice?.friendlyName
        }
        override fun onSessionEnded(session: CastSession, error: Int) { detach(); deviceName = null; connecting = false }
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
        cc.sessionManager.currentCastSession?.let(::attach)
        deviceName = cc.sessionManager.currentCastSession?.castDevice?.friendlyName
        routes.clear()
        routes.addAll(router.routes.filter { it.matchesSelector(selector) && !it.isDefault })
    }

    fun stop() {
        val cc = castContext ?: return
        router.removeCallback(routerCallback)
        cc.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
        client?.unregisterCallback(clientCallback)
        client = null
    }

    /**
     * Connect to [route]. Selecting the route opens the session; the stream is sent once
     * it is up (see onSessionStarted), because a RemoteMediaClient does not exist before.
     */
    fun connect(route: MediaRouter.RouteInfo) {
        connecting = true
        router.selectRoute(route)
    }

    /** Send [target] to the connected receiver — on connect, and on every channel change. */
    fun play(target: CastTarget.Send, title: String) {
        val session = castContext?.sessionManager?.currentCastSession ?: return
        attach(session)
        val client = client ?: return
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        val info = MediaInfo.Builder(target.url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)   // IPTV has no seekable timeline
            .setContentType(target.contentType)
            .setMetadata(meta)
            .build()
        receiver = Receiver.LOADING
        receiverError = null
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
            .setResultCallback { result ->
                if (!result.status.isSuccess) fail("The TV refused the stream (error ${result.status.statusCode}).")
            }
    }

    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
        router.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
        detach()
        deviceName = null
        connecting = false
    }

    /** What the TV is doing with the stream, as far as it has told us. */
    enum class Receiver { IDLE, LOADING, PLAYING, FAILED }

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

        /**
         * What a TV can do with a channel. Decided before anything is sent, because a TV
         * handed a stream it can't use doesn't say so — it shows a spinner indefinitely.
         */
        fun castTarget(url: String): CastTarget {
            // A stream served from this phone — an Ace Stream engine on 127.0.0.1 is the
            // common one — is an address that, on the TV, means the TV itself.
            val host = runCatching { URI(url).host }.getOrNull()?.lowercase()?.trim('[', ']')
            if (host == null || host == "localhost" || host == "0.0.0.0" || host == "::1" || host.startsWith("127.")) {
                return CastTarget.OnThisDevice
            }
            // An engine elsewhere on the network serves getstream as raw MPEG-TS; its HLS
            // form is the same channel in a shape the receiver can play.
            val send = AceStream.hlsUrl(url)
            val type = contentTypeFor(send)
            return if (type == "video/mp2t") CastTarget.UnsupportedFormat else CastTarget.Send(send, type)
        }
    }
}

/** What casting a channel can come to. */
sealed interface CastTarget {
    /** Send [url] to the TV, declared as [contentType]. */
    data class Send(val url: String, val contentType: String) : CastTarget
    /** The stream is served from this phone, so the TV can't reach it. */
    data object OnThisDevice : CastTarget
    /** Raw MPEG-TS, which the default receiver can't play. */
    data object UnsupportedFormat : CastTarget
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
