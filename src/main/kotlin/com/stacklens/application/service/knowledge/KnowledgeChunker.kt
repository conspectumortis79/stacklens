package com.stacklens.application.service.knowledge
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class KnowledgeChunker {
    fun chunk(
        text: String,
        sectionTitle: String? = null,
        chunkSize: Int = 1600,
        chunkOverlap: Int = 200,
    ): List<RawChunk> {
        if (text.isBlank()) return emptyList()
        require(chunkSize > 0) { "chunkSize must be > 0" }
        require(chunkOverlap in 0 until chunkSize) { "chunkOverlap must be in 0..<chunkSize" }

        val paragraphs = text.split(Regex("\\n\\s*\\n"))
        val chunks = mutableListOf<RawChunk>()
        val buffer = StringBuilder()
        var ordinal = 0

        fun flush() {
            val payload = buffer.toString().trim()
            if (payload.isNotBlank()) {
                chunks += RawChunk(ordinal = ordinal, content = payload, sectionTitle = sectionTitle)
                ordinal++
            }
            buffer.setLength(0)
        }
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isBlank()) continue
            if (trimmed.length > chunkSize) {
                flush()
                for (sentence in trimmed.split(Regex("(?<=[.!?。！？])\\s+"))) {
                    if (buffer.length + sentence.length + 1 > chunkSize) flush()
                    if (buffer.isNotEmpty()) buffer.append(' ')
                    buffer.append(sentence)
                }
                flush()
                continue
            }
            if (buffer.length + trimmed.length + 2 > chunkSize) flush()
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append(trimmed)
        }
        flush()

        if (chunkOverlap <= 0 || chunks.size == 1) return chunks
        val overlapped = mutableListOf<RawChunk>()
        for (i in chunks.indices) {
            val original = chunks[i]
            val prefix =
                if (i == 0) {
                    ""
                } else {
                    val prev = chunks[i - 1].content
                    val start = (prev.length - chunkOverlap).coerceAtLeast(0)
                    prev.substring(start)
                }
            val combined = if (prefix.isBlank()) original.content else "$prefix\n\n${original.content}"
            val finalContent = if (combined.length > chunkSize * 2) original.content else combined
            overlapped += original.copy(content = finalContent)
        }
        return overlapped
    }
}

data class RawChunk(
    val ordinal: Int,
    val content: String,
    val sectionTitle: String?,
)
