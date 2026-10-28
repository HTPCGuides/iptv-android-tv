package com.iptvtv.ace

/** What the app knows about engine addresses without touching the network. */
object EngineAddress {
    const val DEFAULT_HOST = "127.0.0.1"
    const val DEFAULT_PORT = 6878
    const val LOCAL = "$DEFAULT_HOST:$DEFAULT_PORT"

    /** The tool's page size for `/search`; the last page is the first one shorter than this. */
    const val PAGE_SIZE = 200

    fun isLocal(addr: String) = addr.startsWith("127.") || addr.startsWith("localhost") || addr.startsWith("[::1]")

    /**
     * Accepts what people paste — `192.168.1.5`, `http://host:6878/`, `host:6878` —
     * and returns `host:port`, or null when it isn't an address.
     */
    fun normalize(input: String): String? {
        var s = input.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (s.isEmpty() || s.any { it.isWhitespace() || it == '/' || it == '?' }) return null
        if (s.startsWith("[")) {                       // [ipv6]:port
            val end = s.indexOf(']')
            if (end < 0) return null
            val rest = s.substring(end + 1)
            if (rest.isEmpty()) return "$s:$DEFAULT_PORT"
            val port = rest.removePrefix(":").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return "${s.substring(0, end + 1)}:$port"
        }
        val colon = s.lastIndexOf(':')
        if (colon < 0) return "$s:$DEFAULT_PORT"
        if (s.indexOf(':') != colon) return null        // bare ipv6 needs brackets
        val port = s.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        s = s.substring(0, colon)
        return if (s.isEmpty()) null else "$s:$port"
    }
}
