package com.iptvtv.ace

import org.json.JSONArray
import org.json.JSONObject

/*
 * The engine's channel index, shaped like m3u_gen_acestream's acestream.SearchResult.
 * A Channel is one search result; each of its items is a *source* and becomes one
 * playlist entry. See docs/ENGINE_API.md §1.2.
 */

data class Icon(val url: String, val type: Int = 0)

data class Item(
    val name: String,
    val infohash: String,
    val status: Int = 0,
    val availability: Double = 0.0,
    val availabilityUpdatedAt: Long = 0,
    val categories: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val channelId: Int = 0,
)

data class Channel(
    val name: String,
    val icons: List<Icon> = emptyList(),
    val items: List<Item> = emptyList(),
)

val List<Channel>.sourceCount: Int get() = sumOf { it.items.size }

/** Decodes one `/search` page. Missing or null fields become Go's zero values. */
object SearchJson {
    fun parsePage(body: String): List<Channel> {
        val results = JSONObject(body).optJSONObject("result")?.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i -> results.optJSONObject(i)?.let(::channel) }
    }

    private fun channel(o: JSONObject) = Channel(
        // The engine sends `name` as a string or a number; both are kept as text.
        name = o.str("name"),
        icons = o.objects("icons").map { Icon(it.str("url"), it.optInt("type")) },
        items = o.objects("items").map(::item),
    )

    private fun item(o: JSONObject) = Item(
        name = o.str("name"),
        infohash = o.str("infohash"),
        status = o.optInt("status"),
        availability = o.optDouble("availability", 0.0),
        availabilityUpdatedAt = o.optLong("availability_updated_at"),
        categories = o.strings("categories"),
        languages = o.strings("languages"),
        countries = o.strings("countries"),
        channelId = o.optInt("channel_id"),
    )

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else opt(key)?.toString().orEmpty()

    private fun JSONObject.strings(key: String): List<String> {
        val a: JSONArray = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { (a.opt(it)?.takeUnless { v -> v == JSONObject.NULL } ?: "").toString() }
    }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val a: JSONArray = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
}

/** How many sources carry each value; `""` counts sources with none. Most common first. */
data class Facets(
    val categories: List<Pair<String, Int>>,
    val languages: List<Pair<String, Int>>,
    val countries: List<Pair<String, Int>>,
    val statuses: List<Pair<Int, Int>>,
) {
    companion object {
        fun of(channels: List<Channel>): Facets {
            val items = channels.flatMap { it.items }
            fun count(values: (Item) -> List<String>) = items
                .flatMap { values(it).ifEmpty { listOf("") }.distinct() }
                .groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key to it.value }
            return Facets(
                categories = count { it.categories },
                languages = count { it.languages },
                countries = count { it.countries },
                statuses = items.groupingBy { it.status }.eachCount().toSortedMap().map { it.key to it.value },
            )
        }
    }
}
