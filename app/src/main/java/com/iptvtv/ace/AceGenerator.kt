package com.iptvtv.ace


import java.util.Locale
import java.util.regex.PatternSyntaxException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** An include list, its strict flag and an exclude list, as the tool applies them (ENGINE_API.md §2.6). */
data class ListFilter(
    val include: List<String> = emptyList(),
    val strict: Boolean = false,
    val exclude: List<String> = emptyList(),
) {
    fun keeps(values: List<String>): Boolean {
        val v = values.ifEmpty { listOf("") }
        if (include.isNotEmpty()) {
            val ok = if (strict) v.all { it in include } else v.any { it in include }
            if (!ok) return false
        }
        return exclude.isEmpty() || v.none { it in exclude }
    }
}

/**
 * One playlist definition — the tool's `config.Playlist`, field for field. Regex maps
 * are ordered lists because order decides which of several matching keys wins; in Go
 * that order is random (see ENGINE_API.md §2.1).
 */
data class Profile(
    val id: String,
    val title: String,
    val description: String = "",
    val outputPath: String,
    val headerTemplate: String,
    val entryTemplate: String,
    val categoryRxToCategoryMap: List<Pair<String, String>> = emptyList(),
    val nameRxToCategoriesMap: List<Pair<String, List<String>>> = emptyList(),
    val nameRxFilter: List<String> = emptyList(),
    val nameRxBlacklist: List<String> = emptyList(),
    val categories: ListFilter = ListFilter(),
    val languages: ListFilter = ListFilter(),
    val countries: ListFilter = ListFilter(),
    val statusFilter: List<Int> = listOf(2),
    val availabilityThreshold: Double = 1.0,
    val availabilityUpdatedThreshold: Duration = 36.hours,
    val removeDeadSources: Boolean = false,
    val useMpegTsAnalyzer: Boolean = false,
    val checkRespTimeout: Duration = 20.seconds,
    val removeDeadLinkTemplate: String = Profiles.LINK_MPEGTS,
    val removeDeadWorkers: Int = 1,
) {
    /** File name the playlist is saved under: the last segment of [outputPath]. */
    val fileName: String get() = outputPath.substringAfterLast('/').substringAfterLast('\\').ifBlank { "playlist.m3u8" }
}

class ProfileError(message: String) : Exception(message)

/** The playlist entry a template is executed against — the tool's `m3u.Entry`. */
data class Entry(
    val name: String,
    val infohash: String,
    val categories: String,
    val countries: String,
    val languages: String,
    val engineAddr: String,
    val tvgName: String,
    val iconUrl: String,
) {
    fun vars(): Map<String, String> = mapOf(
        "Name" to name, "Infohash" to infohash, "Categories" to categories, "Countries" to countries,
        "Languages" to languages, "EngineAddr" to engineAddr, "TVGName" to tvgName, "IconURL" to iconUrl,
    )
}

/** Sources removed at each stage, in pipeline order, for the result screen. */
data class Rejections(val stages: List<Pair<String, Int>>) {
    val total: Int get() = stages.sumOf { it.second }
}

data class Selection(val channels: List<Channel>, val sourcesIn: Int, val rejections: Rejections) {
    val sources: Int get() = channels.sourceCount
}

data class Playlist(
    val profile: Profile,
    val entries: List<Entry>,
    val text: String,
    val selection: Selection,
    /** Each entry's executed template, parallel to [entries]; [text] is the header plus these. */
    val entryTexts: List<String> = emptyList(),
)

/**
 * m3u_gen_acestream's `m3u.Generate`, split in two so dead-source checking (network,
 * slow) can sit between the pure halves: [select] remaps and filters, [render]
 * builds, sorts and writes entries.
 */
object Generator {

    /** A profile with its regexes and templates compiled; construction validates them like the tool's config load. */
    class Compiled(val profile: Profile) {
        val categoryMap = profile.categoryRxToCategoryMap.map { (rx, v) -> regex(rx, "categoryRxToCategoryMap") to v }
        val nameMap = profile.nameRxToCategoriesMap.map { (rx, v) -> regex(rx, "nameRxToCategoriesMap") to v }
        val nameFilter = profile.nameRxFilter.map { regex(it, "nameRxFilter") }
        val nameBlacklist = profile.nameRxBlacklist.map { regex(it, "nameRxBlacklist") }
        val entryTemplate = template(profile.entryTemplate, "entryTemplate")
        val deadLinkTemplate = template(profile.removeDeadLinkTemplate, "removeDeadLinkTemplate")

        private fun regex(rx: String, where: String) = try {
            Regex(rx)
        } catch (e: PatternSyntaxException) {
            throw ProfileError("Can't compile regular expression \"$rx\" in $where: ${e.description}")
        }

        private fun template(t: String, where: String) = try {
            Template.parse(t)
        } catch (e: TemplateError) {
            throw ProfileError("Can't parse $where: ${e.message}")
        }
    }

    fun compile(profile: Profile) = Compiled(profile)

    fun select(channels: List<Channel>, profile: Compiled, nowUnixSec: Long = System.currentTimeMillis() / 1000): Selection =
        filter(remap(channels, profile), profile, nowUnixSec)

    /** Pipeline steps 1–2, the category and name remaps. The filter form counts values after this. */
    fun remap(channels: List<Channel>, profile: Compiled): List<Channel> {
        var list = channels

        if (profile.categoryMap.isNotEmpty()) {
            list = list.mapItems { item ->
                item.copy(categories = item.categories.ifEmpty { listOf("") }.map { category ->
                    // Every matching key applies, each tested against the original value; the last one wins.
                    var out = category
                    for ((rx, value) in profile.categoryMap) if (rx.containsMatchIn(category)) out = value
                    out
                })
            }
        }
        if (profile.nameMap.isNotEmpty()) {
            list = list.mapItems { item ->
                var cats = item.categories
                for ((rx, value) in profile.nameMap) if (rx.containsMatchIn(item.name)) cats = value
                item.copy(categories = cats)
            }
        }
        return list
    }

    /** Pipeline steps 3–7 — status, availability, lists, names — on an already remapped index. */
    fun filter(remapped: List<Channel>, profile: Compiled, nowUnixSec: Long = System.currentTimeMillis() / 1000): Selection {
        val p = profile.profile
        var list = remapped
        val sourcesIn = list.sourceCount
        val stages = ArrayList<Pair<String, Int>>()
        fun stage(label: String, keep: (Item) -> Boolean) {
            val before = list.sourceCount
            list = list.map { ch -> ch.copy(items = ch.items.filter(keep)) }
            stages += label to (before - list.sourceCount)
        }
        val updatedWithinSec = p.availabilityUpdatedThreshold.inWholeSeconds
        stage("status") { it.status in p.statusFilter }
        stage("availability") { it.availability >= p.availabilityThreshold }
        stage("availability age") { nowUnixSec - it.availabilityUpdatedAt <= updatedWithinSec }
        stage("category") { p.categories.keeps(it.categories) }
        stage("language") { p.languages.keeps(it.languages) }
        stage("country") { p.countries.keeps(it.countries) }
        stage("name") { item ->
            (profile.nameFilter.isEmpty() || profile.nameFilter.any { it.containsMatchIn(item.name) }) &&
                profile.nameBlacklist.none { it.containsMatchIn(item.name) }
        }
        return Selection(list, sourcesIn, Rejections(stages))
    }

    /** Builds the playlist text. [deadInfohashes] come from a dead-source check, when the profile asks for one. */
    fun render(selection: Selection, profile: Compiled, engineAddr: String, deadInfohashes: Set<String> = emptySet()): Playlist {
        var sel = selection
        if (profile.profile.removeDeadSources) {
            val before = sel.sources
            val kept = sel.channels.map { ch -> ch.copy(items = ch.items.filter { it.infohash !in deadInfohashes }) }
            sel = Selection(kept, sel.sourcesIn, Rejections(sel.rejections.stages + ("no response" to before - kept.sourceCount)))
        }

        val sorted = entries(sel, engineAddr)
        val text = StringBuilder(profile.profile.headerTemplate)   // the header is written verbatim, not executed
        val entryTexts = sorted.map { e -> profile.entryTemplate.execute(e.vars()).also { text.append(it) } }
        return Playlist(profile.profile, sorted, text.toString(), sel, entryTexts)
    }

    /** One entry per remaining source, in playlist order. The builder's live list shows exactly these. */
    fun entries(selection: Selection, engineAddr: String): List<Entry> =
        selection.channels.flatMap { ch ->
            val icon = ch.icons.firstOrNull()?.url.orEmpty()
            ch.items.map { item ->
                Entry(
                    name = item.name,
                    infohash = item.infohash,
                    categories = joined(item.categories),
                    countries = joined(item.countries),
                    languages = joined(item.languages),
                    engineAddr = engineAddr,
                    tvgName = item.name.replace(" ", "_"),
                    iconUrl = icon,
                )
            }
        }.sortedWith(compareBy<Entry, String>(GoStringOrder) { it.categories }.thenBy(GoStringOrder) { it.name })
    // ^ stable: equal to the tool's stable sort by name followed by a stable sort by categories.

    /** Lower-case, de-duplicate, drop empty, sort, join with `;`. */
    private fun joined(values: List<String>): String =
        values.map { it.lowercase(Locale.ROOT) }.distinct().filter { it.isNotEmpty() }.sortedWith(GoStringOrder).joinToString(";")

    private inline fun List<Channel>.mapItems(crossinline f: (Item) -> Item) = map { ch -> ch.copy(items = ch.items.map { f(it) }) }
}

/** Go's `strings.Compare`: UTF-8 byte order, which is code point order (not UTF-16 unit order). */
val GoStringOrder: Comparator<String> = Comparator { a, b ->
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a.codePointAt(i)
        val cb = b.codePointAt(j)
        if (ca != cb) return@Comparator ca.compareTo(cb)
        i += Character.charCount(ca)
        j += Character.charCount(cb)
    }
    (a.length - i).compareTo(b.length - j)
}
