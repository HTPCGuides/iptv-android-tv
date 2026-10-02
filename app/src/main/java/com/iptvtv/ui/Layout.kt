package com.iptvtv.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** True on Android TV / Google TV, where the screen is across a room and may overscan. */
@Composable
fun isTelevision(): Boolean =
    LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

/**
 * The outer margin of a full-screen page. On a TV it is the overscan-safe area Android TV
 * asks for — 48dp at the sides, 27dp top and bottom — because many sets still crop the
 * picture's edges, and a 24dp margin put titles and the first card where they get cut.
 */
fun Modifier.screenPadding(): Modifier = composed {
    if (isTelevision()) padding(horizontal = 48.dp, vertical = 27.dp)
    else padding(horizontal = Space.xl, vertical = Space.lg)
}

/**
 * The widest a block of settings or explanatory text should run. Past this, a description
 * becomes a 130-character line and a card's value (a cache size, a toggle) drifts to the
 * far edge, away from the label it belongs to.
 */
val ReadableWidth = 720.dp
