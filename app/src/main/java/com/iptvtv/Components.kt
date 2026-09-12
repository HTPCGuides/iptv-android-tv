package com.iptvtv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The app's single focus response, shared by every focusable surface: lift, warm the
 * fill, ring it in the accent, and settle with a slight overshoot. On a ten-foot
 * screen focus has to be readable across the room, so it moves as well as recolours —
 * unless the user has switched animations off, in which case it cuts.
 */
@Composable
private fun focusMotion(focused: Boolean, peak: Float): Float {
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (focused) peak else 1f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "focus-scale",
    )
    return scale
}

/** A focusable card. `peakScale` is trimmed for full-width rows so they don't clip at the edges. */
@Composable
fun TvSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    peakScale: Float = 1.05f,
    contentPadding: PaddingValues = PaddingValues(horizontal = Space.lg, vertical = Space.md),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusMotion(focused, peakScale)
    val cs = MaterialTheme.colorScheme
    Box(
        contentAlignment = contentAlignment,
        modifier =
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 14.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = cs.primary,
                spotColor = cs.primary,
            )
            .clip(shape)
            .background(if (focused) cs.surfaceContainerHighest else cs.surfaceContainer)
            .border(if (focused) 2.dp else 1.dp, if (focused) cs.primary else cs.outlineVariant, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(contentPadding),
    ) { content(focused) }
}

/**
 * Icon-only control. Stays transparent at rest so it never reads as a card inside a
 * card; the accent ring only appears on focus.
 */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusMotion(focused, 1.12f)
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused) cs.primary.copy(alpha = .16f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) cs.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint ?: cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}

/** Labelled action, optionally with a leading icon. Used for screen-level actions. */
@Composable
fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    TvSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        peakScale = 1.08f,
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
        // The label row is shorter than the 48dp touch minimum; without this it
        // hugs the top of the pill and the whole button looks misaligned.
        contentAlignment = Alignment.Center,
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            val fg = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

/** Filter pill. Selection is carried by the fill, not by a character prefix. */
@Composable
fun TvChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusMotion(focused, 1.08f)
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                when {
                    selected -> cs.primaryContainer
                    focused -> cs.surfaceContainerHighest
                    else -> cs.surfaceContainer
                },
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) cs.primary else if (selected) cs.primary.copy(alpha = .4f) else cs.outlineVariant,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) cs.onPrimaryContainer else cs.onSurface,
        )
    }
}
