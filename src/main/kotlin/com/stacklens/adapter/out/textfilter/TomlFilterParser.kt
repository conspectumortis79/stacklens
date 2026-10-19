package com.stacklens.adapter.out.textfilter

import com.stacklens.domain.text.filter.FilterSpec
import com.stacklens.domain.text.filter.FilterTestCase
import com.stacklens.domain.text.filter.MatchOutputRule

internal class TomlFilterParser {
    private val tokenReader = TomlTokenReader()
    private val valueParser = TomlValueParser()

    fun parse(
        content: String,
        fileName: String,
    ): ParsedFile {
        val lines = content.lines()
        var filtersName: String? = null
        var filtersSection: MutableMap<String, Any?> = linkedMapOf()
        val tests: MutableList<FilterTestCase> = mutableListOf()

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = tokenReader.stripComment(raw).trim()
            i++

            if (line.isEmpty()) continue

            when {
                line.startsWith("[[") && line.endsWith("]]") -> {
                    val path = line.substring(2, line.length - 2).trim()
                    if (!path.startsWith("tests.")) {
                        i = skipUntilHeader(lines, i)
                        continue
                    }
                    val testName = path.removePrefix("tests.")
                    val (kvLines, consumed) = readUntilHeader(lines, i)
                    i = consumed
                    val kv = parseKeyValueBlock(kvLines, fileName, "tests.$testName")
                    val name =
                        kv["name"] as? String
                            ?: throw TomlParseException("$fileName: [[tests.$testName]] missing 'name'")
                    val input =
                        kv["input"] as? String
                            ?: throw TomlParseException("$fileName: [[tests.$testName]] missing 'input'")
                    val expected =
                        kv["expected"] as? String
                            ?: throw TomlParseException("$fileName: [[tests.$testName]] missing 'expected'")
                    tests += FilterTestCase(name = name, input = input, expected = expected)
                }

                line.startsWith("[") && line.endsWith("]") -> {
                    val path = line.substring(1, line.length - 1).trim()
                    if (path.startsWith("filters.")) {
                        filtersName = path.removePrefix("filters.")
                        filtersSection = linkedMapOf()
                    } else {
                        i = skipUntilHeader(lines, i)
                    }
                }

                line.contains("=") -> {
                    val (key, value, consumed) = readKeyValueMulti(lines, i - 1, fileName)
                    filtersSection[key] = value
                    i += consumed - 1
                }
            }
        }

        if (filtersName == null) {
            return ParsedFile(spec = null, tests = emptyList())
        }

        val spec = buildSpec(filtersName, filtersSection, fileName)
        return ParsedFile(spec = spec, tests = tests)
    }

    data class ParsedFile(
        val spec: FilterSpec?,
        val tests: List<FilterTestCase>,
    )

    private fun readUntilHeader(
        lines: List<String>,
        startIdx: Int,
    ): Pair<List<String>, Int> {
        val out = mutableListOf<String>()
        var j = startIdx
        while (j < lines.size) {
            val l = lines[j].trim()
            if (l.startsWith("[") && l.endsWith("]")) {
                return out to j
            }
            out += lines[j]
            j++
        }
        return out to j
    }

    private fun skipUntilHeader(
        lines: List<String>,
        startIdx: Int,
    ): Int {
        var j = startIdx
        while (j < lines.size) {
            val l = lines[j].trim()
            if ((l.startsWith("[") && l.endsWith("]")) && !l.startsWith("[[")) return j
            if (l.startsWith("[[") && l.endsWith("]]")) return j
            j++
        }
        return j
    }

    private fun parseKeyValueBlock(
        rawLines: List<String>,
        fileName: String,
        context: String,
    ): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        val meaningful =
            rawLines
                .map { tokenReader.stripComment(it) }
                .filter { it.isNotBlank() }

        var i = 0
        while (i < meaningful.size) {
            val line = meaningful[i].trim()
            i++

            val mMulti = Regex("""^([\w\-]+)\s*=\s*['"]{3}""").find(line)
            if (mMulti != null) {
                val key = mMulti.groupValues[1]
                val triple = line.substring(line.indexOf("=") + 1).trim().substring(0, 3)
                val rest = line.substring(line.indexOf(triple) + 3)
                val closeSameLine = rest.indexOf(triple)
                if (closeSameLine >= 0) {
                    val body = rest.substring(0, closeSameLine)
                    out[key] = if (triple == "\"\"\"") unescapeString(body, fileName, key) else body
                } else {
                    val sb = StringBuilder()
                    sb.append(rest.removePrefix("\n"))
                    var closed = false
                    while (i < meaningful.size) {
                        val next = meaningful[i]
                        val closeAt = next.indexOf(triple)
                        if (closeAt >= 0) {
                            sb.append('\n').append(next.substring(0, closeAt))
                            i++
                            closed = true
                            break
                        } else {
                            sb.append('\n').append(next)
                            i++
                        }
                    }
                    if (!closed) {
                        throw TomlParseException(
                            "$fileName: $context: unterminated multi-line string for '$key'",
                        )
                    }
                    val body = sb.toString()
                    out[key] =
                        if (triple == "\"\"\"") {
                            val stripped = if (body.startsWith("\n")) body.drop(1) else body
                            unescapeString(stripped, fileName, key)
                        } else {
                            body
                        }
                }
                continue
            }

            val (key, value) = readKeyValue(line, fileName)
            out[key] = value
        }
        return out
    }

    private fun readKeyValue(
        line: String,
        fileName: String,
    ): Pair<String, Any?> {
        val eq = line.indexOf('=')
        if (eq < 0) throw TomlParseException("$fileName: malformed line: '$line'")
        val key = line.substring(0, eq).trim()
        val rest = line.substring(eq + 1).trim()
        val value = valueParser.parseValue(rest, fileName, key)
        return key to value
    }

    private fun readKeyValueMulti(
        lines: List<String>,
        startIdx: Int,
        fileName: String,
    ): Triple<String, Any?, Int> {
        val firstLine = tokenReader.stripComment(lines[startIdx]).trim()
        val eq = firstLine.indexOf('=')
        val key = firstLine.substring(0, eq).trim()
        val firstRest = firstLine.substring(eq + 1).trim()

        return when (tokenReader.continuationOf(firstRest)) {
            ValueContinuation.NONE -> Triple(key, valueParser.parseValue(firstRest, fileName, key), 1)
            ValueContinuation.ARRAY -> readMultiLineArray(lines, startIdx, fileName, key, firstRest)
            ValueContinuation.MULTILINE_STRING ->
                readMultiLineString(lines, startIdx, fileName, key, firstRest, firstRest.substring(0, 3))
        }
    }

    private fun readMultiLineArray(
        lines: List<String>,
        startIdx: Int,
        fileName: String,
        key: String,
        firstRest: String,
    ): Triple<String, Any?, Int> {
        val sb = StringBuilder(firstRest)
        var depthSquare =
            tokenReader.countUnquoted(firstRest, '[') - tokenReader.countUnquoted(firstRest, ']')
        var depthCurly =
            tokenReader.countUnquoted(firstRest, '{') - tokenReader.countUnquoted(firstRest, '}')
        var j = startIdx + 1
        while (j < lines.size) {
            val l = lines[j].trim()
            if (l.startsWith("[[") || (l.startsWith("[") && l.endsWith("]"))) {
                throw TomlParseException(
                    "$fileName: unterminated array for '$key' (hit next section '$l' before closing ']')",
                )
            }
            val clean = tokenReader.stripComment(lines[j])
            sb.append('\n').append(clean)
            depthSquare += tokenReader.countUnquoted(clean, '[') - tokenReader.countUnquoted(clean, ']')
            depthCurly += tokenReader.countUnquoted(clean, '{') - tokenReader.countUnquoted(clean, '}')
            if (depthSquare <= 0 && depthCurly <= 0) {
                j++
                break
            }
            j++
        }
        if (depthSquare > 0 || depthCurly > 0) {
            throw TomlParseException("$fileName: unterminated array for '$key'")
        }
        val value = valueParser.parseValue(sb.toString().trim(), fileName, key)
        return Triple(key, value, j - startIdx)
    }

    private fun readMultiLineString(
        lines: List<String>,
        startIdx: Int,
        fileName: String,
        key: String,
        firstRest: String,
        triple: String,
    ): Triple<String, Any?, Int> {
        val sb = StringBuilder()
        sb.append(firstRest.removePrefix(triple))
        var closed = false
        var j = startIdx + 1
        while (j < lines.size) {
            val l = lines[j]
            val closeAt = l.indexOf(triple)
            if (closeAt >= 0) {
                sb.append('\n').append(l.substring(0, closeAt))
                closed = true
                j++
                break
            } else {
                sb.append('\n').append(l)
                j++
            }
        }
        if (!closed) {
            throw TomlParseException(
                "$fileName: unterminated multi-line string for '$key'",
            )
        }
        val body = sb.toString()
        val finalValue =
            if (triple == "\"\"\"") {
                val stripped = if (body.startsWith("\n")) body.drop(1) else body
                unescapeString(stripped, fileName, key)
            } else {
                body
            }
        return Triple(key, finalValue, j - startIdx)
    }

    private fun buildSpec(
        name: String,
        raw: Map<String, Any?>,
        fileName: String,
    ): FilterSpec {
        val description =
            raw["description"] as? String
                ?: throw TomlParseException("$fileName: 'description' missing or not a string")

        fun regex(key: String): Regex? =
            (raw[key] as? String)?.let {
                runCatching { Regex(it) }.getOrElse { e ->
                    throw TomlParseException("$fileName: invalid regex in '$key': ${e.message}")
                }
            }

        fun regexList(key: String): List<Regex> =
            (raw[key] as? List<*>)?.mapIndexed { idx, item ->
                (item as? String)?.let {
                    runCatching { Regex(it) }.getOrElse { e ->
                        throw TomlParseException("$fileName: invalid regex in '$key[$idx]': ${e.message}")
                    }
                } ?: throw TomlParseException(
                    "$fileName: '$key[$idx]' must be a string (got ${item?.javaClass?.simpleName})",
                )
            } ?: emptyList()

        fun intOrNull(key: String): Int? = (raw[key] as? Long)?.toInt() ?: (raw[key] as? Int)

        val matchOutput: List<MatchOutputRule> =
            (raw["match_output"] as? List<*>)?.mapIndexed { idx, item ->
                val map =
                    item as? Map<*, *>
                        ?: throw TomlParseException(
                            "$fileName: 'match_output[$idx]' must be an inline table",
                        )
                val pattern =
                    map["pattern"] as? String
                        ?: throw TomlParseException("$fileName: 'match_output[$idx].pattern' missing")
                val message =
                    map["message"] as? String
                        ?: throw TomlParseException("$fileName: 'match_output[$idx].message' missing")
                val unless =
                    (map["unless"] as? String)?.let {
                        runCatching { Regex(it) }.getOrElse { e ->
                            throw TomlParseException(
                                "$fileName: invalid regex in 'match_output[$idx].unless': ${e.message}",
                            )
                        }
                    }
                MatchOutputRule(
                    pattern =
                        runCatching { Regex(pattern) }.getOrElse { e ->
                            throw TomlParseException(
                                "$fileName: invalid regex in 'match_output[$idx].pattern': ${e.message}",
                            )
                        },
                    message = message,
                    unless = unless,
                )
            } ?: emptyList()

        return FilterSpec(
            name = name,
            description = description,
            matchCommand = regex("match_command"),
            matchOutput = matchOutput,
            stripAnsi = (raw["strip_ansi"] as? Boolean) ?: true,
            filterStderr = (raw["filter_stderr"] as? Boolean) ?: false,
            stripLinesMatching = regexList("strip_lines_matching"),
            keepLinesMatching = regexList("keep_lines_matching"),
            maxLines = intOrNull("max_lines"),
            tailLines = intOrNull("tail_lines"),
            truncateLinesAt = intOrNull("truncate_lines_at"),
            onEmpty = raw["on_empty"] as? String,
        )
    }

    private fun unescapeString(
        raw: String,
        fileName: String,
        key: String,
    ): String = UNESCAPE_FORWARDER.unescape(raw, fileName, key)

    private class UnescapeForwarder {
        private val parser = TomlValueParser()

        fun unescape(
            raw: String,
            fileName: String,
            key: String,
        ): String = parser.unescapeForFilterParser(raw, fileName, key)
    }

    private companion object {
        val UNESCAPE_FORWARDER = UnescapeForwarder()
    }
}
