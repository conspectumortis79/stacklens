package com.stacklens.adapter.out.knowledge.extraction

import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import org.springframework.stereotype.Component
import java.nio.charset.Charset

@Component
class PlainTextKnowledgeExtractor : DocumentTextExtractor {
    override fun supports(
        filename: String,
        content: ByteArray,
    ): Boolean = filename.lowercase().endsWith(".txt")

    override fun extract(
        filename: String,
        content: ByteArray,
    ): String {
        val probe = content.copyOfRange(0, content.size.coerceAtMost(64 * 1024))
        val charset = pickCharset(probe)
        val decoder =
            charset
                .newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
        val decoded = decoder.decode(java.nio.ByteBuffer.wrap(content)).toString()
        val stripped =
            when {
                decoded.isNotEmpty() && decoded[0] == '\uFEFF' -> decoded.substring(1)
                else -> decoded
            }
        return stripped.trim()
    }

    private fun pickCharset(probe: ByteArray): Charset =
        when {
            probe.size >= 3 && probe[0] == 0xEF.toByte() && probe[1] == 0xBB.toByte() && probe[2] == 0xBF.toByte() -> Charsets.UTF_8
            probe.size >= 2 && probe[0] == 0xFF.toByte() && probe[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            probe.size >= 2 && probe[0] == 0xFE.toByte() && probe[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
}
