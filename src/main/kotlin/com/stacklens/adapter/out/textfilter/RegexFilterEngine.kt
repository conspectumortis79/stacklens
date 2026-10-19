package com.stacklens.adapter.out.textfilter

import com.stacklens.domain.text.filter.FilterResult
import com.stacklens.domain.text.filter.FilterSpec
import org.slf4j.LoggerFactory

class RegexFilterEngine {
    private val log = LoggerFactory.getLogger(RegexFilterEngine::class.java)

    fun apply(
        spec: FilterSpec,
        rawOutput: String,
    ): FilterResult {
        val originalChars = rawOutput.length

        for (rule in spec.matchOutput) {
            if (!rule.pattern.containsMatchIn(rawOutput)) continue
            if (rule.unless != null && rule.unless.containsMatchIn(rawOutput)) continue
            return FilterResult(
                text = rule.message,
                originalChars = originalChars,
                filteredChars = rule.message.length,
                specName = spec.name,
                matchedShortCircuit = true,
            )
        }

        var text = if (spec.stripAnsi) AnsiStripper.strip(rawOutput) else rawOutput
        val lines = text.split('\n')
        val keptLines =
            if (spec.isWhitelist) {
                lines.filter { line -> spec.keepLinesMatching.any { it.containsMatchIn(line) } }
            } else {
                lines
            }
        val filteredLines =
            if (spec.stripLinesMatching.isNotEmpty()) {
                keptLines.filterNot { line -> spec.stripLinesMatching.any { it.containsMatchIn(line) } }
            } else {
                keptLines
            }

        val tailedLines =
            spec.tailLines?.let { tail ->
                if (filteredLines.size > tail) filteredLines.takeLast(tail) else filteredLines
            } ?: filteredLines

        val truncatedLines =
            spec.truncateLinesAt?.let { max ->
                tailedLines.map { line ->
                    if (line.length > max) line.take(max) + "…" else line
                }
            } ?: tailedLines

        val cappedLines =
            spec.maxLines?.let { max ->
                if (truncatedLines.size > max) {
                    truncatedLines.take(max) + "… [${truncatedLines.size - max} more lines suppressed]"
                } else {
                    truncatedLines
                }
            } ?: truncatedLines

        val joined = cappedLines.joinToString("\n")
        val finalText = joined.ifBlank { spec.onEmpty.orEmpty() }

        if (finalText.isNotEmpty() && originalChars > 0) {
            val saved = originalChars - finalText.length
            val pct = (saved * 100) / originalChars
            log.debug(
                "[Filter:{}] {} → {} chars ({}% saved)",
                spec.name,
                originalChars,
                finalText.length,
                pct,
            )
        }

        return FilterResult(
            text = finalText,
            originalChars = originalChars,
            filteredChars = finalText.length,
            specName = spec.name,
        )
    }
}
