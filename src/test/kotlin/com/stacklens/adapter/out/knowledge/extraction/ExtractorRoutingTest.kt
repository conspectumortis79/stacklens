package com.stacklens.adapter.out.knowledge.extraction

import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExtractorRoutingTest {
    @Test
    fun `PDF extractor refuses non-pdf magic bytes`() {
        val notPdf = "Hello, world!".toByteArray()
        val extractor = PdfKnowledgeExtractor()
        assert(!extractor.supports("report.pdf", notPdf))
    }

    @Test
    fun `DOCX extractor refuses plain-text with docx extension`() {
        val notDocx = "Hello, world!".toByteArray()
        val extractor = DocxKnowledgeExtractor()
        assert(!extractor.supports("notes.docx", notDocx))
    }

    @Test
    fun `DOCX extractor rejects an empty byte array`() {
        val extractor = DocxKnowledgeExtractor()
        assert(!extractor.supports("notes.docx", ByteArray(0)))
    }

    @Test
    fun `legacy DOC extractor refuses non-ole2 magic bytes`() {
        val notDoc = "Hello, world!".toByteArray()
        val extractor = LegacyDocKnowledgeExtractor()
        assert(!extractor.supports("notes.doc", notDoc))
    }

    @Test
    fun `plain text extractor accepts txt file`() {
        val extractor = PlainTextKnowledgeExtractor()
        assertTrue(extractor.supports("notes.txt", "hello".toByteArray()))
        val text = extractor.extract("notes.txt", "hello\nworld".toByteArray())
        assert(text.contains("hello"))
        assert(text.contains("world"))
    }

    @Test
    fun `plain text extractor strips UTF-8 BOM`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val payload = bom + "hello".toByteArray()
        val text = PlainTextKnowledgeExtractor().extract("notes.txt", payload)
        assertTrue(text == "hello", "expected 'hello' but got '$text'")
    }

    @Test
    fun `markdown extractor accepts md file and strips code-fence markers`() {
        val md =
            """
            # Title

            ```kotlin
            fun main() { println("hi") }
            ```
            """.trimIndent().toByteArray()
        val text = MarkdownKnowledgeExtractor().extract("readme.md", md)
        assertTrue(text.contains("# Title"))
        assertTrue(!text.contains("```kotlin"))
    }

    @Test
    fun `unknown extension raises a clear error from the registry path`() {
        val ext = extractors().firstOrNull { it.supports("file.xyz", "x".toByteArray()) }
        assertTrue(ext == null)
    }

    @Test
    fun `large DOCX above 50MB is rejected before POI is touched`() {
        val huge = 51 * 1024 * 1024
        val docx = ByteArray(huge) { 0 }
        docx[0] = 'P'.code.toByte()
        docx[1] = 'K'.code.toByte()
        docx[2] = 0x03
        docx[3] = 0x04
        val extractor = DocxKnowledgeExtractor()
        val ex =
            assertThrows<IllegalArgumentException> {
                extractor.extract("huge.docx", docx)
            }
        assertTrue(ex.message!!.contains("50 MB"))
    }

    private fun extractors(): List<DocumentTextExtractor> =
        listOf(
            PdfKnowledgeExtractor(),
            DocxKnowledgeExtractor(),
            LegacyDocKnowledgeExtractor(),
            PlainTextKnowledgeExtractor(),
            MarkdownKnowledgeExtractor(),
        )
}
