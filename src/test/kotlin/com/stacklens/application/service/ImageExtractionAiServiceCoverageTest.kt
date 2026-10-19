package com.stacklens.application.service

import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.application.port.out.LocalOcrPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.image.InlineImagePreparationPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ImageExtractionAiServiceCoverageTest {
    private val copilot = mock<CopilotPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val localOcr = mock<LocalOcrPort>()
    private val preparer = mock<ImageInlinePreparer>()
    private val service = ImageExtractionAiService(copilot, promptLoader, localOcr, preparer)

    private fun stubPrepared(
        mimeType: String,
        scaledDown: Boolean,
    ) {
        whenever(copilot.resolveModel()).thenReturn("vision-model")
        whenever(preparer.prepareImageForInlineUse(any(), any())).thenReturn(
            InlineImagePreparationPort.PreparedImage(byteArrayOf(1, 2, 3), mimeType, scaledDown, 10, 20),
        )
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenReturn("extracted text")
    }

    @Test
    fun `reports the downscaled attachment when the preparer shrank the image`() {
        stubPrepared("image/jpeg", scaledDown = true)

        assertEquals("extracted text", service.extractTextFromImage("prompt", ByteArray(5000), "image/png"))
        verify(preparer).prepareImageForInlineUse(any(), eq("image/png"))
    }

    @Test
    fun `passes the untouched image through when no scaling happened`() {
        stubPrepared("image/png", scaledDown = false)

        assertEquals("extracted text", service.extractTextFromImage("prompt", ByteArray(10), "image/png"))
        verify(copilot).execute(eq("vision-model"), eq("prompt"), anyOrNull(), any())
    }
}
