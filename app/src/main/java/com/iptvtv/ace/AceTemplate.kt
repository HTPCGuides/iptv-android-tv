package com.iptvtv.ace


class TemplateError(message: String) : Exception(message)

/**
 * The subset of Go `text/template` the tool's templates use: literal text, `{{.Field}}`,
 * `{{/* comments */}}` and the `{{-` / `-}}` whitespace trim markers. Anything else
 * (`if`, `range`, pipelines) is rejected when the template is parsed, rather than
 * producing a playlist that differs from the tool's. No escaping, as in `text/template`.
 */
class Template private constructor(private val parts: List<Part>) {

    private sealed interface Part {
        data class Text(val text: String) : Part
        data class Field(val name: String) : Part
    }

    fun execute(vars: Map<String, String>, out: StringBuilder) {
        for (p in parts) when (p) {
            is Part.Text -> out.append(p.text)
            is Part.Field -> out.append(vars[p.name].orEmpty())
        }
    }

    fun execute(vars: Map<String, String>): String = buildString { execute(vars, this) }

    companion object {
        /** The fields of the tool's m3u.Entry — the only names a template may use. */
        val ENTRY_FIELDS = listOf("Name", "Infohash", "Categories", "Countries", "Languages", "EngineAddr", "TVGName", "IconURL")

        private val FIELD = Regex("""\.([A-Za-z_][A-Za-z0-9_]*)""")

        fun parse(text: String, fields: Collection<String> = ENTRY_FIELDS): Template {
            val parts = ArrayList<Part>()
            var pending = StringBuilder()   // text not yet committed; a following `{{-` may trim it
            var trimNextLeading = false
            var i = 0
            while (i < text.length) {
                val open = text.indexOf("{{", i)
                val chunk = if (open < 0) text.substring(i) else text.substring(i, open)
                pending.append(if (trimNextLeading) chunk.trimStart() else chunk)
                trimNextLeading = false
                if (open < 0) break
                val close = text.indexOf("}}", open + 2)
                if (close < 0) throw TemplateError("Unclosed action starting at \"${text.substring(open).take(20)}\"")
                var action = text.substring(open + 2, close)
                // Go: "{{- " and " -}}" need the space; "{{-3}}" is a number, not a trim.
                if (action.length >= 2 && action[0] == '-' && action[1].isWhitespace()) {
                    pending = StringBuilder(pending.toString().trimEnd())
                    action = action.substring(1)
                }
                if (action.length >= 2 && action.last() == '-' && action[action.length - 2].isWhitespace()) {
                    trimNextLeading = true
                    action = action.dropLast(1)
                }
                if (pending.isNotEmpty()) { parts += Part.Text(pending.toString()); pending = StringBuilder() }
                val inner = action.trim()
                when {
                    inner.startsWith("/*") && inner.endsWith("*/") -> Unit
                    FIELD.matches(inner) -> {
                        val name = inner.substring(1)
                        if (name !in fields) {
                            throw TemplateError("Unknown variable {{.$name}} — available: ${fields.joinToString { "{{.$it}}" }}")
                        }
                        parts += Part.Field(name)
                    }
                    else -> throw TemplateError("Unsupported template action {{$inner}} — only {{.Variable}} is supported")
                }
                i = close + 2
            }
            if (pending.isNotEmpty()) parts += Part.Text(pending.toString())
            return Template(parts)
        }
    }
}
