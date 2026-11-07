package com.iptvtv.ace

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.schema.CoreSchema
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class ImportError(message: String) : Exception(message)

data class ImportedConfig(
    val engineAddr: String,
    val profiles: List<Profile>,
    /** Playlists skipped as invalid, and valid ones that will produce nothing. */
    val problems: List<String>,
)

/**
 * Reads an m3u_gen_acestream YAML config (config/config.go) into profiles. Fields the
 * file leaves out take Go's zero values — except the five the tool fills in on load —
 * so an imported playlist builds exactly what the tool would build from the same file.
 */
object ConfigImport {

    fun parse(text: String): ImportedConfig {
        val root = try {
            load(text)
        } catch (e: YamlEngineException) {
            throw ImportError("This isn't valid YAML: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}")
        }
        val top = root as? Map<*, *> ?: throw ImportError("Expected a config with engineAddr and playlists.")
        if ("playlists" !in top) throw ImportError("No playlists in this file. Is it an m3u_gen_acestream config?")

        val engineAddr = str(top["engineAddr"], "engineAddr")
        val problems = ArrayList<String>()
        val profiles = list(top["playlists"], "playlists").mapIndexedNotNull { i, raw ->
            val where = "playlists[$i]"
            val m = raw as? Map<*, *> ?: throw ImportError("$where isn't a playlist definition.")
            val p = profile(i, m, where)
            try {
                Generator.compile(p)
            } catch (e: ProfileError) {
                problems += "${p.fileName} skipped. ${e.message}"
                return@mapIndexedNotNull null
            }
            if (p.statusFilter.isEmpty()) problems += "${p.fileName} has no statusFilter, so, as in the tool, it keeps nothing."
            p
        }
        return ImportedConfig(engineAddr, profiles, problems)
    }

    private fun profile(index: Int, m: Map<*, *>, where: String): Profile {
        fun s(key: String) = str(m[key], "$where.$key")
        fun strings(key: String) = list(m[key], "$where.$key").map { str(it, "$where.$key") }
        fun bool(key: String, default: Boolean) = when (val v = m[key]) {
            null -> default
            is Boolean -> v
            else -> throw ImportError("$where.$key must be true or false, not \"$v\".")
        }
        fun filter(prefix: String) = ListFilter(strings("${prefix}Filter"), bool("${prefix}FilterStrict", false), strings("${prefix}Blacklist"))

        val outputPath = s("outputPath")
        val entryTemplate = s("entryTemplate")
        val base = outputPath.substringAfterLast('/').substringAfterLast('\\')
        return Profile(
            id = "import:$index:$base",
            title = base.substringBeforeLast('.').ifBlank { "Playlist ${index + 1}" },
            description = "From your imported config · " + linkKind(entryTemplate),
            outputPath = outputPath,
            headerTemplate = s("headerTemplate"),
            entryTemplate = entryTemplate,
            categoryRxToCategoryMap = entries(m["categoryRxToCategoryMap"], "$where.categoryRxToCategoryMap")
                .map { (k, v) -> k to str(v, "$where.categoryRxToCategoryMap") },
            nameRxToCategoriesMap = entries(m["nameRxToCategoriesMap"], "$where.nameRxToCategoriesMap")
                .map { (k, v) -> k to list(v, "$where.nameRxToCategoriesMap").map { str(it, "$where.nameRxToCategoriesMap") } },
            nameRxFilter = strings("nameRxFilter"),
            nameRxBlacklist = strings("nameRxBlacklist"),
            categories = filter("categories"),
            languages = filter("languages"),
            countries = filter("countries"),
            statusFilter = list(m["statusFilter"], "$where.statusFilter").map { int(it, "$where.statusFilter") },
            availabilityThreshold = m["availabilityThreshold"]?.let { number(it, "$where.availabilityThreshold").toDouble() } ?: 0.0,
            availabilityUpdatedThreshold = m["availabilityUpdatedThreshold"]?.let { duration(it, "$where.availabilityUpdatedThreshold") } ?: Duration.ZERO,
            removeDeadSources = bool("removeDeadSources", false),
            useMpegTsAnalyzer = bool("useMpegTsAnalyzer", false),
            checkRespTimeout = m["checkRespTimeout"]?.let { duration(it, "$where.checkRespTimeout") } ?: 20.seconds,
            removeDeadLinkTemplate = m["removeDeadLinkTemplate"]?.let { str(it, "$where.removeDeadLinkTemplate") } ?: Profiles.LINK_MPEGTS,
            removeDeadWorkers = m["removeDeadWorkers"]?.let { int(it, "$where.removeDeadWorkers") } ?: 1,
        )
    }

    /** What kind of links a template writes, in words: the chip's description. */
    private fun linkKind(entryTemplate: String) = when {
        "/ace/getstream" in entryTemplate -> "MPEG-TS links"
        "/ace/manifest.m3u8" in entryTemplate -> "HLS links"
        "127.0.0.1:8000" in entryTemplate -> "HTTPAceProxy links"
        else -> "its own link template"
    }

    private var memo: Pair<String, List<Profile>>? = null

    /**
     * The playlists of a config stored earlier, or none if there is none (or it no longer
     * reads — a stored config was valid when imported, so that would be a newer parser's
     * doing). Remembers the last text, since Home asks on every recomposition.
     */
    @Synchronized
    fun storedProfiles(yaml: String): List<Profile> {
        if (yaml.isBlank()) return emptyList()
        memo?.let { (text, profiles) -> if (text == yaml) return profiles }
        val profiles = runCatching { parse(yaml).profiles }.getOrDefault(emptyList())
        memo = yaml to profiles
        return profiles
    }

    // ---- scalar conversions, Go-style ----

    private fun str(v: Any?, where: String): String = when (v) {
        null -> ""
        is String -> v
        is Number, is Boolean -> v.toString()
        else -> throw ImportError("$where must be text.")
    }

    private fun list(v: Any?, where: String): List<Any?> = when (v) {
        null -> emptyList()
        is List<*> -> v
        else -> throw ImportError("$where must be a list.")
    }

    /** Mapping entries in file order — the order the app applies regex keys in. */
    private fun entries(v: Any?, where: String): List<Pair<String, Any?>> = when (v) {
        null -> emptyList()
        is Map<*, *> -> v.entries.map { (k, value) -> str(k, where) to value }
        else -> throw ImportError("$where must be a mapping.")
    }

    private fun number(v: Any, where: String): Number = v as? Number ?: throw ImportError("$where must be a number, not \"$v\".")

    private fun int(v: Any?, where: String): Int = when (v) {
        is Int -> v
        is Long -> v.toInt()
        is java.math.BigInteger -> v.toInt()
        else -> throw ImportError("$where must be whole numbers, not \"$v\".")
    }

    /** Go's time.Duration from YAML: a ParseDuration string, or an integer of nanoseconds. */
    private fun duration(v: Any, where: String): Duration = when (v) {
        is String -> parseGoDuration(v) ?: throw ImportError("$where: \"$v\" isn't a duration like 36h0m0s.")
        is Int, is Long -> (v as Number).toLong().nanoseconds
        else -> throw ImportError("$where must be a duration like 36h0m0s.")
    }

    private val DURATION_PART = Regex("""(\d+\.?\d*|\.\d+)(ns|us|µs|μs|ms|s|m|h)""")
    private val UNIT_NANOS = mapOf("ns" to 1.0, "us" to 1e3, "µs" to 1e3, "μs" to 1e3, "ms" to 1e6, "s" to 1e9, "m" to 6e10, "h" to 3.6e12)

    /** Go's `time.ParseDuration`: "36h0m0s", "1.5h", "300ms", "-2m". */
    fun parseGoDuration(text: String): Duration? {
        var rest = text.trim()
        var negative = false
        if (rest.startsWith("-") || rest.startsWith("+")) {
            negative = rest[0] == '-'
            rest = rest.substring(1)
        }
        if (rest == "0") return Duration.ZERO
        if (rest.isEmpty()) return null
        var pos = 0
        var nanos = 0.0
        while (pos < rest.length) {
            val m = DURATION_PART.matchAt(rest, pos) ?: return null
            nanos += m.groupValues[1].toDouble() * UNIT_NANOS.getValue(m.groupValues[2])
            pos = m.range.last + 1
        }
        return (if (negative) -nanos else nanos).toLong().nanoseconds
    }

    /**
     * YAML 1.2 core schema, as go-yaml reads it: only true/false are booleans, so a country
     * code like `no` stays text (YAML 1.1 parsers turn it into `false`). Mappings keep file
     * order, and a duplicate key is an error.
     */
    private fun load(text: String): Any? {
        val settings = LoadSettings.builder()
            .setSchema(CoreSchema())
            .setAllowDuplicateKeys(false)
            .build()
        return Load(settings).loadFromString(text)
    }
}
