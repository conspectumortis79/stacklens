package com.stacklens.adapter.out.knowledge.extraction

import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.shared.sniffMagic
import org.apache.poi.hwpf.HWPFDocument
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

@Component
class LegacyDocKnowledgeExtractor : DocumentTextExtractor {
    override fun supports(
        filename: String,
        content: ByteArray,
    ): Boolean {
        val lower = filename.lowercase()
        if (!lower.endsWith(".doc")) return false
        return sniffMagic(content) == "ole2"
    }

    override fun extract(
        filename: String,
        content: ByteArray,
    ): String = readDocument(HWPFDocument(ByteArrayInputStream(content)), filename)

    internal fun readDocument(
        document: HWPFDocument,
        filename: String,
    ): String =
        try {
            normalise(plainText(document))
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to extract text from DOC '$filename': ${e.message}",
                e,
            )
        } finally {
            document.close()
        }

    internal fun plainText(document: HWPFDocument): String = document.text?.toString().orEmpty()

    internal fun normalise(text: String): String {
        val collapsed = text.replace(Regex("[ \\t]+"), " ").replace(Regex("\n{3,}"), "\n\n")
        return collapsed.trim()
    }
}
