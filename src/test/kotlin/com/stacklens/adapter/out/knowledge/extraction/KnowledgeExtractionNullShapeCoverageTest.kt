package com.stacklens.adapter.out.knowledge.extraction

import com.stacklens.testsupport.PrivateInvocation
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.apache.poi.xwpf.usermodel.XWPFTableRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KnowledgeExtractionNullShapeCoverageTest {
    @Test
    fun `paragraphs and cells without text read as empty lines`() {
        val paragraph = mock<XWPFParagraph>()
        Mockito.doReturn(null).`when`(paragraph).text
        val first = mock<XWPFTableCell>()
        Mockito.doReturn(null).`when`(first).text
        val second = mock<XWPFTableCell>()
        Mockito.doReturn(null).`when`(second).text
        val row = mock<XWPFTableRow>()
        whenever(row.tableCells).thenReturn(listOf(first, second))
        val table = mock<XWPFTable>()
        whenever(table.rows).thenReturn(listOf(row))

        Mockito
            .mockConstruction(XWPFDocument::class.java) { doc, _ ->
                whenever(doc.paragraphs).thenReturn(listOf(paragraph))
                whenever(doc.tables).thenReturn(listOf(table))
            }.use {
                assertEquals("|", DocxKnowledgeExtractor().extract("review.docx", byteArrayOf(1, 2, 3)))
            }
    }

    @Test
    fun `every byte order mark shape picks its own charset`() {
        val extractor = PlainTextKnowledgeExtractor()
        val probes =
            listOf(
                ByteArray(0),
                byteArrayOf(0x00),
                byteArrayOf(0xEF.toByte(), 0x00, 0x00),
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0x00),
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
                byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
                byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
                byteArrayOf(0xFE.toByte(), 0xFE.toByte()),
                byteArrayOf(0x41, 0x42),
            )

        val names = probes.map { PrivateInvocation.invoke(extractor, "pickCharset", it).toString() }

        assertEquals(
            listOf("UTF-8", "UTF-8", "UTF-8", "UTF-8", "UTF-8", "UTF-16LE", "UTF-8", "UTF-16BE", "UTF-8", "UTF-8"),
            names,
        )
    }
}
