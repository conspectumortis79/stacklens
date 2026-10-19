package com.stacklens.adapter.out.review.pptx

import com.stacklens.domain.model.PptxShapeDto
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.awt.geom.Rectangle2D

class PptxStructureExtractorFailingRunCoverageTest {
    private val extractor = PptxStructureExtractor()

    private fun <T> invoke(
        name: String,
        vararg args: Any?,
    ): T? = ReflectionTestUtils.invokeMethod(extractor, name, *args)

    @Test
    fun `a run whose text layer cannot be read drops the whole shape`() {
        val run = mock<XSLFTextRun>()
        whenever(run.rawText).thenThrow(IllegalStateException("text layer destroyed"))
        val paragraph = mock<XSLFTextParagraph>()
        whenever(paragraph.isBullet).thenReturn(false)
        whenever(paragraph.textRuns).thenReturn(listOf(run))
        val shape = mock<XSLFTextShape>()
        whenever(shape.anchor).thenReturn(Rectangle2D.Double(1.0, 2.0, 3.0, 4.0))
        whenever(shape.textParagraphs).thenReturn(listOf(paragraph))

        assertNull(invoke<PptxShapeDto>("mapShapeOrNull", shape))
    }
}
