package com.stacklens.application.service.review

import com.stacklens.application.port.out.JsonCodecPort
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class PptxQuestionExtractorParseQuestionIdCoverageTest {
    private val extractor = PptxQuestionExtractor(mock<JsonCodecPort>())

    @Test
    fun `an identifier outside the question scheme cannot be parsed`() {
        assertNull(parse("shape-2"))
    }

    private fun parse(id: String): Any? {
        val method = PptxQuestionExtractor::class.java.getDeclaredMethod("parseQuestionId", String::class.java)
        method.isAccessible = true
        return method.invoke(extractor, id)
    }
}
