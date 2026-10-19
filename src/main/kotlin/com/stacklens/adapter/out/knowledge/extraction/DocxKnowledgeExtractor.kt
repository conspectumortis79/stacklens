package com.stacklens.adapter.out.knowledge.extraction

import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.shared.sniffMagic
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

@Component
class DocxKnowledgeExtractor : DocumentTextExtractor {
    override fun supports(
        filename: String,
        content: ByteArray,
    ): Boolean {
        val lower = filename.lowercase()
        if (!lower.endsWith(".docx")) return false
        return sniffMagic(content) == "zip"
    }

    override fun extract(
        filename: String,
        content: ByteArray,
    ): String {
        val sizeCap = 50L * 1024L * 1024L
        if (content.size > sizeCap) {
            throw IllegalArgumentException(
                "DOCX '$filename' is ${content.size} bytes; the knowledge base caps document uploads at 50 MB.",
            )
        }
        val document = openDocument(filename, content)
        val sb = StringBuilder()
        try {
            document.paragraphs.forEach { p ->
                val text = p.text ?: ""
                if (text.isNotBlank()) sb.append(text).append('\n')
            }
            document.tables.forEach { table ->
                for (row in table.rows) {
                    val cells = row.tableCells.map { it.text ?: "" }
                    sb.append(cells.joinToString(" | ")).append('\n')
                }
                sb.append('\n')
            }
            return sb.toString().replace(Regex("[ \\t]+"), " ").trim()
        } finally {
            document.close()
        }
    }

    private fun openDocument(
        filename: String,
        content: ByteArray,
    ): XWPFDocument =
        try {
            XWPFDocument(ByteArrayInputStream(content))
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to extract text from DOCX '$filename': ${e.message}",
                e,
            )
        }
}
