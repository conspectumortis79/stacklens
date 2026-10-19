package com.stacklens.adapter.out.textfilter

internal class TomlValueParser {
    fun parseValue(
        text: String,
        fileName: String,
        key: String,
    ): Any? {
        val t = text.trim()
        return when {
            t.startsWith("\"\"\"") || t.startsWith("'''") -> parseMultilineString(t, fileName, key)
            t.startsWith("\"") || t.startsWith("'") -> parseSingleLineString(t, fileName, key)
            t.startsWith("[") -> parseArray(t, fileName, key)
            t.startsWith("{") -> parseInlineTable(t, fileName, key)
            t == "true" -> true
            t == "false" -> false
            t.matches(Regex("-?\\d+")) -> t.toLong()
            t.matches(Regex("-?\\d+L")) -> t.dropLast(1).toLong()
            else -> throw TomlParseException(
                "$fileName: unsupported value for '$key': $t",
            )
        }
    }

    private fun parseSingleLineString(
        text: String,
        fileName: String,
        key: String,
    ): String {
        val quote = text[0]
        var i = 1
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && quote == '"' && i + 1 < text.length) {
                i += 2
                continue
            }
            if (c == quote) {
                val raw = text.substring(1, i)
                return if (quote == '"') unescapeString(raw, fileName, key) else raw
            }
            i++
        }
        throw TomlParseException("$fileName: unterminated string for '$key'")
    }

    private fun parseMultilineString(
        text: String,
        fileName: String,
        key: String,
    ): String {
        val triple = text.substring(0, 3)
        val closeIdx = text.indexOf(triple, 3)
        if (closeIdx < 0) {
            throw TomlParseException(
                "$fileName: unterminated multi-line string for '$key' (no closing $triple)",
            )
        }
        val body = text.substring(3, closeIdx)
        if (triple != "\"\"\"") return body
        val stripped = if (body.startsWith("\n")) body.drop(1) else body
        return unescapeString(stripped, fileName, key)
    }

    private fun unescapeString(
        raw: String,
        fileName: String,
        key: String,
    ): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val n = raw[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    '\'' -> sb.append('\'')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        if (i + 5 < raw.length) {
                            val hex = raw.substring(i + 2, i + 6)
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        } else {
                            throw TomlParseException(
                                "$fileName: malformed \\u escape in '$key'",
                            )
                        }
                    }
                    else -> throw TomlParseException(
                        "$fileName: unknown escape '\\$n' in '$key'",
                    )
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun parseArray(
        text: String,
        fileName: String,
        key: String,
    ): List<Any?> {
        if (!text.endsWith("]")) {
            throw TomlParseException("$fileName: unterminated array for '$key'")
        }
        val inner = text.substring(1, text.length - 1)
        if (inner.isBlank()) return emptyList()

        val parts =
            splitTopLevel(inner, ',', fileName, key)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        return parts.map { p -> parseValue(p, fileName, key) }
    }

    private fun parseInlineTable(
        text: String,
        fileName: String,
        key: String,
    ): Map<String, Any?> {
        require(text.startsWith("{")) {
            "parseInlineTable called with non-table text: $text"
        }
        val sb = StringBuilder()
        var depth = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '{' -> {
                    depth++
                    if (depth > 1) sb.append(c)
                    i++
                }
                c == '}' -> {
                    depth--
                    if (depth == 0) {
                        val entries =
                            splitTopLevel(sb.toString(), ',', fileName, key)
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                        val map = linkedMapOf<String, Any?>()
                        for (entry in entries) {
                            val (k, v) = readKeyValue(entry, fileName)
                            map[k] = v
                        }
                        return map
                    }
                    sb.append(c)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        throw TomlParseException("$fileName: unterminated inline table for '$key'")
    }

    private fun readKeyValue(
        line: String,
        fileName: String,
    ): Pair<String, Any?> {
        val eq = line.indexOf('=')
        if (eq < 0) throw TomlParseException("$fileName: malformed line: '$line'")
        val key = line.substring(0, eq).trim()
        val rest = line.substring(eq + 1).trim()
        val value = parseValue(rest, fileName, key)
        return key to value
    }

    private fun splitTopLevel(
        text: String,
        sep: Char,
        fileName: String,
        key: String,
    ): List<String> = SHARED_TOKEN_READER.splitTopLevel(text, sep, fileName, key)

    fun unescapeForFilterParser(
        raw: String,
        fileName: String,
        key: String,
    ): String = unescapeString(raw, fileName, key)

    private companion object {
        val SHARED_TOKEN_READER = TomlTokenReader()
    }
}
