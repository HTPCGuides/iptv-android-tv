package com.iptvtv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One glyph and one tint per playlist group. Material's rounded set, so nothing is
 * bundled and every glyph shares a stroke with the rest of the UI. Matching is by
 * keyword, since providers spell groups their own way ("Sport", "Sports", "Deportes").
 */
private val GROUP_ICONS: List<Pair<List<String>, ImageVector>> = listOf(
    listOf("sport", "deport", "football", "soccer", "golf", "racing") to Icons.Rounded.SportsSoccer,
    listOf("news", "noticias", "nyheder") to Icons.Rounded.Newspaper,
    listOf("movie", "film", "cinema") to Icons.Rounded.Movie,
    listOf("music", "musik", "radio") to Icons.Rounded.MusicNote,
    listOf("kid", "child", "børn", "cartoon") to Icons.Rounded.ChildCare,
    listOf("anim") to Icons.Rounded.Animation,
    listOf("comedy") to Icons.Rounded.SentimentVerySatisfied,
    listOf("entertain", "variety") to Icons.Rounded.TheaterComedy,
    listOf("series", "drama", "novela") to Icons.Rounded.LiveTv,
    listOf("relig", "church", "faith", "gospel", "islam", "christ") to Icons.Rounded.Church,
    listOf("document", "nature", "science", "space") to Icons.Rounded.Science,
    listOf("educat", "learn", "school") to Icons.Rounded.School,
    listOf("cook", "food", "recipe") to Icons.Rounded.Restaurant,
    listOf("lifestyle", "health", "relax", "wellness", "fashion") to Icons.Rounded.Spa,
    listOf("auto", "motor", "car") to Icons.Rounded.DirectionsCar,
    listOf("outdoor", "fishing", "hunting", "adventure") to Icons.Rounded.Terrain,
    listOf("travel") to Icons.Rounded.Flight,
    listOf("culture", "art") to Icons.Rounded.Palette,
    listOf("legislat", "parliament", "government", "politic") to Icons.Rounded.Gavel,
    listOf("public", "community", "local") to Icons.Rounded.AccountBalance,
    listOf("business", "econom", "finance") to Icons.Rounded.BusinessCenter,
    listOf("shop") to Icons.Rounded.ShoppingBag,
    listOf("weather") to Icons.Rounded.Cloud,
    listOf("classic", "retro") to Icons.Rounded.History,
    listOf("family") to Icons.Rounded.FamilyRestroom,
    listOf("general", "internat", "world") to Icons.Rounded.Public,
)

val AllGroupsIcon: ImageVector = Icons.Rounded.Apps

fun groupIcon(group: String): ImageVector {
    val g = group.lowercase()
    if (g == "other" || g == "undefined" || g.isBlank()) return Icons.Rounded.Category
    return GROUP_ICONS.firstOrNull { (keys, _) -> keys.any { g.contains(it) } }?.second ?: Icons.Rounded.Tv
}

/**
 * Tints for the logo placeholder: eight hues that sit on the ink surfaces at the same
 * lightness as the brand sky, chosen by name so a group keeps its colour everywhere.
 */
private val GROUP_TINTS = listOf(
    Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFF34D399), Color(0xFFFBBF24),
    Color(0xFFF472B6), Color(0xFFFB923C), Color(0xFF60A5FA), Color(0xFF2DD4BF),
)

fun groupTint(group: String): Color = GROUP_TINTS[(group.lowercase().hashCode() and 0x7fffffff) % GROUP_TINTS.size]
