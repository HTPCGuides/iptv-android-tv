package com.iptvtv

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * One dark scheme, deliberately: this is a living-room video app. Content is the
 * light source and the UI is the frame around it, so a light theme would fight the
 * picture in the room the app is actually used in.
 *
 * Every colour below is a Material role. Screens read roles, never raw hex, so the
 * palette can move in one place and contrast stays checked.
 */
private val Sky = Color(0xFF38BDF8)          // brand accent — focus, selection, links
private val Ink = Color(0xFF0B1120)          // page
private val InkRaised = Color(0xFF141E2C)    // resting card
private val InkLifted = Color(0xFF1D2A3B)    // focused card
private val Parchment = Color(0xFFE8EEF6)    // primary text
private val ParchmentDim = Color(0xFF9FB0C4) // secondary text, tinted from the surface hue
private val Amber = Color(0xFFFBBF24)        // advisory: geo-blocked, part-time
private val Coral = Color(0xFFF87171)        // failure

private val DarkScheme = darkColorScheme(
    primary = Sky,
    onPrimary = Color(0xFF04222E),
    primaryContainer = Color(0xFF0C4A63),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = ParchmentDim,
    onSecondary = Ink,
    tertiary = Amber,
    onTertiary = Color(0xFF2A1D00),
    background = Ink,
    onBackground = Parchment,
    surface = Ink,
    onSurface = Parchment,
    surfaceVariant = InkRaised,
    onSurfaceVariant = ParchmentDim,
    surfaceContainerLowest = Color(0xFF080D18),
    surfaceContainerLow = Color(0xFF101927),
    surfaceContainer = InkRaised,
    surfaceContainerHigh = Color(0xFF18242F),
    surfaceContainerHighest = InkLifted,
    outline = Color(0xFF3A4C63),
    outlineVariant = Color(0xFF243244),
    error = Coral,
    onError = Color(0xFF3A0A0A),
    scrim = Color(0xFF000000),
)

/*
 * Sized for the 10-foot case first. Both targets land on a similar dp canvas
 * (TV ≈ 960×540dp, the test phone ≈ 857×384dp landscape), so one scale serves
 * both — but the sofa sets the floor, which is why nothing here drops below 13sp.
 */
private val TvTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

private val TvShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/*
 * Legacy palette handles. New code reads MaterialTheme.colorScheme instead — these
 * stay so older call sites keep resolving to the same values, not a second palette.
 */
val Bg = Ink
val Card = InkRaised
val Accent = Sky
val Muted = ParchmentDim

/** 4dp rhythm — every gap in the app is one of these. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** True when the user has turned animations off system-wide; we then cut instead of move. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val reducedMotion = remember(ctx) {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = DarkScheme, typography = TvTypography, shapes = TvShapes, content = content)
    }
}
