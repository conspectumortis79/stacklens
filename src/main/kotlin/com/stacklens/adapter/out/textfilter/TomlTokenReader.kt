package com.stacklens.adapter.out.textfilter

internal class TomlTokenReader {
    fun stripComment(line: String): String {
        val hashIdx = findUnquotedHash(line)
        return if (hashIdx < 0) line else line.substring(0, hashIdx)
    }

    fun findUnquotedHash(line: String): Int {
        var inString = false
        var stringDelim = ' '
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inString) {
                when {
                    c == '\\' && i + 1 < line.length -> i++
                    c == stringDelim -> inString = false
                }
            } else {
                when (c) {
                    '"', '\'' -> {
                        inString = true
                        stringDelim = c
                    }
                    '#' -> return i
                }
            }
            i++
        }
        return -1
    }

    fun continuationOf(rest: String): ValueContinuation {
        if (rest.startsWith("[")) {
            return if (countUnquoted(rest, '[') != countUnquoted(rest, ']')) {
                ValueContinuation.ARRAY
            } else {
                ValueContinuation.NONE
            }
        }
        if (rest.startsWith(TRIPLE_LITERAL) || rest.startsWith(TRIPLE_BASIC)) {
            val triple = rest.substring(0, 3)
            return if (rest.indexOf(triple, 3) < 0) {
                ValueContinuation.MULTILINE_STRING
            } else {
                ValueContinuation.NONE
            }
        }
        return ValueContinuation.NONE
    }

    fun countUnquoted(
        text: String,
        ch: Char,
    ): Int {
        var count = 0
        var inString = false
        var stringDelim = ' '
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                when {
                    c == '\\' && i + 1 < text.length -> i++
                    c == stringDelim -> inString = false
                }
            } else {
                when (c) {
                    '"', '\'' -> {
                        inString = true
                        stringDelim = c
                    }
                    ch -> count++
                }
            }
            i++
        }
        return count
    }

    fun splitTopLevel(
        text: String,
        sep: Char,
        fileName: String,
        key: String,
    ): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var depthSquare = 0
        var depthCurly = 0
        var inString = false
        var stringDelim = ' '
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                sb.append(c)
                when {
                    c == '\\' && i + 1 < text.length -> {
                        sb.append(text[i + 1])
                        i += 2
                        continue
                    }
                    c == stringDelim -> inString = false
                }
            } else {
                when (c) {
                    '"', '\'' -> {
                        inString = true
                        stringDelim = c
                        sb.append(c)
                    }
                    '[' -> {
                        depthSquare++
                        sb.append(c)
                    }
                    ']' -> {
                        depthSquare--
                        sb.append(c)
                    }
                    '{' -> {
                        depthCurly++
                        sb.append(c)
                    }
                    '}' -> {
                        depthCurly--
                        sb.append(c)
                    }
                    sep -> {
                        if (depthSquare == 0 && depthCurly == 0) {
                            out += sb.toString()
                            sb.clear()
                        } else {
                            sb.append(c)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            i++
        }
        if (sb.isNotEmpty()) out += sb.toString()
        if (inString) throw TomlParseException("$fileName: unterminated string in array for '$key'")
        if (depthSquare != 0 || depthCurly != 0) {
            throw TomlParseException("$fileName: unbalanced brackets/braces in array for '$key'")
        }
        return out
    }

    private companion object {
        const val TRIPLE_LITERAL = "'''"

        const val TRIPLE_BASIC = "\"\"\""
    }
}

enum class ValueContinuation {
    NONE,
    ARRAY,
    MULTILINE_STRING,
}
