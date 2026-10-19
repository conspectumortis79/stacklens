package com.stacklens.application.service.logging

internal object ProposedFileParser {
    private val FILE_MARKER = Regex("^### FILE:\\s*(.+)$", RegexOption.MULTILINE)
    private val DESC_LINE_MARKER = Regex("^### DESCRIPTION:\\s*(.+)$", RegexOption.MULTILINE)
    private val DESC_BLOCK_MARKER = Regex("^### DESCRIPTION:\\s*$", RegexOption.MULTILINE)

    fun parse(output: String): List<ProposedFile> {
        val matches = FILE_MARKER.findAll(output).toList()
        return matches.mapIndexed { index, match ->
            val start = match.range.last + 1
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else output.length
            val (description, content) = splitDescription(output.substring(start, end).trim())
            ProposedFile(
                path = match.groupValues[1].trim(),
                content = content,
                description = description,
            )
        }
    }

    fun splitDescription(block: String): Pair<String?, String> {
        val lineMatch = DESC_LINE_MARKER.find(block)
        val blockMatch = DESC_BLOCK_MARKER.find(block)
        if (lineMatch != null) {
            val description = lineMatch.groupValues[1].trim()
            return (description.ifBlank { null }) to block.substring(lineMatch.range.last + 1).trim()
        }
        if (blockMatch != null) {
            val description = block.substring(0, blockMatch.range.first).trim()
            return (description.ifBlank { null }) to block.substring(blockMatch.range.last + 1).trim()
        }
        return null to block
    }
}
