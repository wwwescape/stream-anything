package com.wwwescape.streamanything.data.streams

/**
 * Evaluates the tiny subset of JavaScript that video hosts use to assemble a link at runtime so
 * it never appears verbatim in the page — string literals joined with `+`, optionally wrapped in
 * parentheses and trimmed with chained `.substring(start[, end])` calls, e.g.
 *
 * ```
 * '//streamta' + ('xcdpe.com/get_video?id=…').substring(1).substring(2)
 * ```
 *
 * Deliberately not a JS engine: anything outside that grammar returns `null` rather than a
 * guess, so a host changing its scheme surfaces as a clean "couldn't read this link" error.
 */
object JsStringExpression {

    fun evaluate(source: String): String? = runCatching {
        Parser(source).run {
            val value = parseConcat()
            skipWhitespace()
            if (pos == input.length) value else null
        }
    }.getOrNull()

    private class Parser(val input: String) {
        var pos = 0

        fun parseConcat(): String {
            val result = StringBuilder(parseTerm())
            while (true) {
                skipWhitespace()
                if (peek() != '+') return result.toString()
                pos++
                result.append(parseTerm())
            }
        }

        private fun parseTerm(): String {
            var value = parsePrimary()
            while (true) {
                skipWhitespace()
                if (!input.startsWith(".substring", pos)) return value
                pos += ".substring".length
                expect('(')
                val start = parseInt()
                skipWhitespace()
                val end = if (peek() == ',') {
                    pos++
                    parseInt()
                } else {
                    null
                }
                expect(')')
                value = jsSubstring(value, start, end)
            }
        }

        private fun parsePrimary(): String {
            skipWhitespace()
            return when (val c = peek()) {
                '\'', '"' -> parseStringLiteral(c)
                '(' -> {
                    pos++
                    val inner = parseConcat()
                    expect(')')
                    inner
                }
                else -> error("Unexpected '$c' at $pos")
            }
        }

        private fun parseStringLiteral(quote: Char): String {
            pos++
            val result = StringBuilder()
            while (true) {
                val c = input.getOrNull(pos) ?: error("Unterminated string")
                pos++
                when (c) {
                    quote -> return result.toString()
                    '\\' -> {
                        val escaped = input.getOrNull(pos) ?: error("Unterminated escape")
                        pos++
                        result.append(
                            when (escaped) {
                                'n' -> '\n'
                                't' -> '\t'
                                else -> escaped
                            },
                        )
                    }
                    else -> result.append(c)
                }
            }
        }

        private fun parseInt(): Int {
            skipWhitespace()
            val start = pos
            if (peek() == '-') pos++
            while (peek()?.isDigit() == true) pos++
            return input.substring(start, pos).toIntOrNull() ?: error("Expected a number at $start")
        }

        private fun expect(c: Char) {
            skipWhitespace()
            if (peek() != c) error("Expected '$c' at $pos")
            pos++
        }

        fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) pos++
        }

        private fun peek(): Char? = input.getOrNull(pos)
    }

    /** JavaScript's `String.prototype.substring`: clamps both indices into range and swaps them
     * if they're reversed, rather than throwing like Kotlin's. */
    private fun jsSubstring(value: String, start: Int, end: Int?): String {
        val from = start.coerceIn(0, value.length)
        val to = (end ?: value.length).coerceIn(0, value.length)
        return value.substring(minOf(from, to), maxOf(from, to))
    }
}
