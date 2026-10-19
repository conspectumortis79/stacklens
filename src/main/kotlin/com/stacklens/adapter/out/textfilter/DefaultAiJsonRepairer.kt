package com.stacklens.adapter.out.textfilter

import com.stacklens.application.port.out.AiJsonRepairerPort
import org.springframework.stereotype.Component

@Component
class DefaultAiJsonRepairer : AiJsonRepairerPort {
    override fun sanitizeAiJson(s: String): String {
        val sb = StringBuilder(s.length)
        var inString = false
        var escape = false
        for (c in s) {
            if (inString) {
                sb.append(c)
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> {
                    inString = true
                    sb.append(c)
                }
                '\uFF0C' -> sb.append(',')
                '\uFF1A' -> sb.append(':')
                '\uFF1B' -> sb.append(';')
                '\u201C', '\u201D', '\u201E', '\u201F' -> sb.append('"')
                '\u2018', '\u2019', '\u201A', '\u201B' -> sb.append('\'')
                '\u3000' -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    override fun repairUnbalancedQuotes(s: String): String {
        val sb = StringBuilder(s.length + 16)
        var inString = false
        var escape = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (!inString) {
                sb.append(c)
                if (c == '"') inString = true
                i++
                continue
            }
            if (escape) {
                sb.append(c)
                escape = false
                i++
                continue
            }
            if (c == '\\') {
                sb.append(c)
                escape = true
                i++
                continue
            }
            if (c == '"') {
                var j = i + 1
                while (j < s.length && s[j].isWhitespace()) j++
                val next = if (j < s.length) s[j] else '\u0000'
                val isTerminator = next == '\u0000' || next == ',' || next == ':' || next == '}' || next == ']'
                if (isTerminator) {
                    sb.append(c)
                    inString = false
                } else {
                    sb.append('\\').append('"')
                }
                i++
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    override fun tryRepairTruncatedJson(s: String): String? {
        val openStack = ArrayDeque<Char>()
        var inString = false
        var escape = false
        var stringStart = -1

        for (i in s.indices) {
            val c = s[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> {
                        inString = false
                        stringStart = -1
                    }
                }
            } else {
                when (c) {
                    '"' -> {
                        inString = true
                        stringStart = i
                    }
                    '{', '[' -> openStack.addLast(c)
                    '}' -> if (openStack.isNotEmpty() && openStack.last() == '{') openStack.removeLast()
                    ']' -> if (openStack.isNotEmpty() && openStack.last() == '[') openStack.removeLast()
                }
            }
        }

        if (!inString && openStack.isEmpty()) return null

        val sb = StringBuilder(s.length + 16)
        if (inString) {
            sb.append(s, 0, stringStart + 1)
            sb.append(s, stringStart + 1, s.length)
            sb.append('"')
        } else {
            sb.append(s, 0, s.length)
        }

        while (openStack.isNotEmpty()) {
            sb.append(if (openStack.removeLast() == '{') '}' else ']')
        }
        return sb.toString()
    }
}
