package com.stacklens.adapter.out.knowledge.extraction

import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.shared.sniffMagic
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component

@Component
class PdfKnowledgeExtractor : DocumentTextExtractor {
    override fun supports(
        filename: String,
        content: ByteArray,
    ): Boolean {
        val lower = filename.lowercase()
        if (!lower.endsWith(".pdf")) return false
        return sniffMagic(content) == "pdf"
    }

    override fun extract(
        filename: String,
        content: ByteArray,
    ): String {
        var document: PDDocument? = null
        try {
            document = Loader.loadPDF(content)
            if (document.isEncrypted) {
                throw IllegalArgumentException(
                    "PDF '$filename' is password-protected. Encrypted PDFs are not supported by the knowledge base.",
                )
            }
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val raw: String = stripper.getText(document)
            return raw
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to extract text from PDF '$filename': ${e.message}",
                e,
            )
        } finally {
            document?.close()
        }
    }
}
