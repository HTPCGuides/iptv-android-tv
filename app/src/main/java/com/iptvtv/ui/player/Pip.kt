package com.iptvtv.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import com.iptvtv.R
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** The buttons inside the picture-in-picture window, as in YouTube and MX Player. */
enum class PipAction { PREVIOUS, PLAY_PAUSE, NEXT }

/**
 * Picture-in-picture, between the player and the activity that owns the window.
 *
 * The player says what it is showing — whether a local picture is playing, its shape and
 * where it sits on screen — and this turns that into the platform's PiP parameters. On
 * Android 12 and later the system then enters PiP by itself when the user goes home, the
 * way YouTube does; before 12 the activity calls [onUserLeaveHint] to do the same.
 *
 * Absent below Android 8 and on devices without the feature — many TV boxes — where the
 * player simply draws no PiP button.
 */
class PipController(private val activity: Activity) {

    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** True while the activity is shown in a PiP window: the player hides all its chrome. */
    var inPip by mutableStateOf(false)
        internal set

    private val _actions = MutableSharedFlow<PipAction>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Taps on the window's buttons, for the player to act on. */
    val actions: SharedFlow<PipAction> = _actions

    /** Whether a local picture is playing now — the only time PiP makes sense. */
    private var eligible = false
    /** The last parameters handed to the platform, reused when entering by hand. */
    private var lastParams: PictureInPictureParams? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = PipAction.entries.getOrNull(intent.getIntExtra(EXTRA_ACTION, -1)) ?: return
            _actions.tryEmit(action)
        }
    }

    init {
        if (supported) {
            // Not exported: only our own PendingIntents, built below, may drive the player.
            ContextCompat.registerReceiver(activity, receiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    /**
     * What the player is showing. [videoWidth]/[videoHeight] give the window its shape;
     * [bounds] is where the picture sits, so the animation into PiP starts from it.
     */
    fun update(eligible: Boolean, playing: Boolean, videoWidth: Int, videoHeight: Int, bounds: Rect?) {
        this.eligible = eligible && supported
        if (!supported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val p = params(playing, videoWidth, videoHeight, bounds)
        lastParams = p
        runCatching { activity.setPictureInPictureParams(p) }
    }

    /** Enter PiP now — the player's PiP button. False when it isn't possible. */
    fun enter(): Boolean {
        if (!eligible || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val p = lastParams ?: PictureInPictureParams.Builder().build()
        return runCatching { activity.enterPictureInPictureMode(p) }.getOrDefault(false)
    }

    /** Before Android 12 there is no auto-enter; leaving the app while playing enters PiP here. */
    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enter()
    }

    fun release() {
        if (supported) runCatching { activity.unregisterReceiver(receiver) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun params(playing: Boolean, w: Int, h: Int, bounds: Rect?): PictureInPictureParams {
        val (num, den) = pipAspect(w, h)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(num, den))
            .setActions(
                listOf(
                    action(PipAction.PREVIOUS, R.drawable.ic_pip_previous, "Previous channel"),
                    if (playing) action(PipAction.PLAY_PAUSE, R.drawable.ic_pip_pause, "Pause")
                    else action(PipAction.PLAY_PAUSE, R.drawable.ic_pip_play, "Play"),
                    action(PipAction.NEXT, R.drawable.ic_pip_next, "Next channel"),
                ),
            )
        bounds?.let { builder.setSourceRectHint(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(eligible)
            // Video: let the system crossfade on resize rather than stretch live frames.
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun action(which: PipAction, @DrawableRes icon: Int, title: String): RemoteAction {
        val intent = Intent(ACTION_PIP).setPackage(activity.packageName).putExtra(EXTRA_ACTION, which.ordinal)
        val pending = PendingIntent.getBroadcast(activity, which.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return RemoteAction(Icon.createWithResource(activity, icon), title, title, pending)
    }

    companion object {
        private const val ACTION_PIP = "com.iptvtv.PIP_ACTION"
        private const val EXTRA_ACTION = "action"
    }
}

/**
 * The PiP window's shape for a [width]×[height] video, as a ratio Android will accept.
 *
 * The platform throws on anything flatter than 2.39:1 or taller than 1:2.39, and a stream
 * can report odd sizes — 0×0 before its first frame, an ultra-wide letterbox, a vertical
 * feed — so the ratio is clamped, and an unknown size falls back to 16:9.
 */
fun pipAspect(width: Int, height: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 16 to 9
    val ratio = width.toDouble() / height
    return when {
        ratio > MAX_PIP_RATIO -> 239 to 100
        ratio < 1 / MAX_PIP_RATIO -> 100 to 239
        else -> width to height
    }
}

private const val MAX_PIP_RATIO = 2.39

/** The activity's [PipController], for the player; null where none is provided (previews, tests). */
val LocalPip = staticCompositionLocalOf<PipController?> { null }
