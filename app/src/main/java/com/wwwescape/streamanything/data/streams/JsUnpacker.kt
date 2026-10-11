package com.wwwescape.streamanything.data.streams

/**
 * Unpacks Dean Edwards' P.A.C.K.E.R. — the `eval(function(p,a,c,k,e,d){…}('payload',62,123,
 * 'word|list'.split('|')))` wrapper most video hosts use to hide their player config. The
 * payload is the original script with every identifier replaced by its index into the word
 * list, written in base [radix]; unpacking substitutes them back.
 */
object JsUnpacker {

    private val PACKED_START = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,""")
    private val ARGS = Regex("""\}\s*\(\s*'(.*?)'\s*,\s*(\d+|\[\])\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\('\|'\)""", RegexOption.DOT_MATCHES_ALL)
    private val WORD = Regex("""\b\w+\b""")

    private const val ALPHABET_62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val ALPHABET_95 =
        " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"

    fun isPacked(source: String): Boolean = PACKED_START.containsMatchIn(source)

    /** Unpacks every packed script in [html], concatenated; empty if there are none. */
    fun unpackAll(html: String): String {
        val starts = PACKED_START.findAll(html).map { it.range.first }.toList()
        if (starts.isEmpty()) return ""
        return starts.mapIndexedNotNull { i, start ->
            val end = starts.getOrNull(i + 1) ?: html.length
            unpack(html.substring(start, end))
        }.joinToString("\n")
    }

    /** Unpacks a single packed script, or `null` if it isn't one / is malformed. */
    fun unpack(source: String): String? {
        val match = ARGS.find(source) ?: return null
        val (payloadRaw, radixRaw, countRaw, symtabRaw) = match.destructured
        val radix = radixRaw.toIntOrNull() ?: 36
        val count = countRaw.toIntOrNull() ?: return null
        val symtab = symtabRaw.split('|')
        if (symtab.size != count) return null
        val unbase = unbaser(radix) ?: return null
        val payload = payloadRaw.replace("\\\\", "\\").replace("\\'", "'")
        return WORD.replace(payload) { word ->
            val index = unbase(word.value)
            symtab.getOrNull(index ?: -1)?.takeIf { it.isNotEmpty() } ?: word.value
        }
    }

    private fun unbaser(radix: Int): ((String) -> Int?)? = when {
        radix in 2..36 -> { s -> s.toIntOrNull(radix) }
        radix <= 95 -> {
            val alphabet = if (radix <= 62) ALPHABET_62.take(radix) else ALPHABET_95.take(radix)
            val lookup = alphabet.withIndex().associate { (i, c) -> c to i };
            { s ->
                var value = 0
                var ok = true
                for (c in s) {
                    val digit = lookup[c]
                    if (digit == null) {
                        ok = false
                        break
                    }
                    value = value * radix + digit
                }
                if (ok) value else null
            }
        }
        else -> null
    }
}
