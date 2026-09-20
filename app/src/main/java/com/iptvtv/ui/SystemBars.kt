package com.iptvtv.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Android's status and navigation bars, hidden or shown to match [hidden]. While hidden a
 * swipe from the edge brings them back briefly. Whatever the state, leaving the composable
 * shows them: no screen should inherit another's immersion.
 */
@Composable
fun SystemBars(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowInsetsControllerCompat(it, view) }
        if (hidden) {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
