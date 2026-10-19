package com.stacklens.adapter.out.git

import com.stacklens.application.port.out.DiffHunkParserPort
import com.stacklens.domain.util.DiffHunk
import org.springframework.stereotype.Component

@Component
class DefaultDiffHunkParser : DiffHunkParserPort {
    private val hunkHeaderRe = Regex("""^@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@(.*)$""")

    override fun hunks(unifiedDiff: String): List<DiffHunk> {
        if (unifiedDiff.isBlank()) return emptyList()
        val result = mutableListOf<DiffHunk>()
        for (line in unifiedDiff.lineSequence()) {
            val match = hunkHeaderRe.matchEntire(line) ?: continue
            val oldStart = match.groupValues[1].toInt()
            val oldCount = match.groupValues[2].toIntOrNull() ?: 1
            val newStart = match.groupValues[3].toInt()
            val newCount = match.groupValues[4].toIntOrNull() ?: 1
            result +=
                DiffHunk(
                    oldStart = oldStart,
                    oldCount = oldCount,
                    newStart = newStart,
                    newCount = newCount,
                    header = line,
                )
        }
        return result
    }

    override fun lastNewLine(hunks: List<DiffHunk>): Int = hunks.maxOfOrNull { it.newStart + (it.newCount - 1).coerceAtLeast(0) } ?: 0

    override fun hunkContaining(
        hunks: List<DiffHunk>,
        newLine: Int,
    ): DiffHunk? {
        for (h in hunks) {
            val end = h.newStart + (h.newCount - 1).coerceAtLeast(0)
            if (newLine in h.newStart..end) return h
        }
        return null
    }

    override fun summarise(
        unifiedDiff: String,
        perFileContextLines: Int,
        maxBodyLines: Int,
    ): String {
        val hunks = hunks(unifiedDiff)
        if (hunks.isEmpty()) return unifiedDiff
        val sb = StringBuilder()
        val body = StringBuilder()
        var currentHunk: DiffHunk? = null
        var contextRemaining = 0
        var emittedForCurrentHunk = 0
        for (raw in unifiedDiff.lineSequence()) {
            val match = hunkHeaderRe.matchEntire(raw)
            if (match != null) {
                currentHunk = hunks.first { it.header == raw }
                sb.append(raw).append('\n')
                contextRemaining = perFileContextLines
                emittedForCurrentHunk = 0
                continue
            }
            if (currentHunk == null) continue
            val isContext = raw.startsWith(' ')
            val isPlus = raw.startsWith('+') && !raw.startsWith("+++")
            val isMinus = raw.startsWith('-') && !raw.startsWith("---")
            if (isMinus) continue
            if (emittedForCurrentHunk >= maxBodyLines) continue
            val include =
                when {
                    isContext -> {
                        if (contextRemaining > 0) {
                            contextRemaining -= 1
                            true
                        } else {
                            false
                        }
                    }
                    isPlus -> true
                    else -> false
                }
            if (include) {
                body.append(raw).append('\n')
                emittedForCurrentHunk += 1
            }
        }
        if (body.isNotEmpty()) {
            sb.append(body)
        }
        return sb.toString()
    }

    override fun isPlausibleLine(
        hunks: List<DiffHunk>,
        newLine: Int,
    ): Boolean = hunkContaining(hunks, newLine) != null
}
