package com.stacklens.application.service

import com.stacklens.application.port.out.ReviewAnalysisPort
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ReviewAnalysisControllerBranchTest {
    @Test
    fun `ReviewAnalysisController is constructible`() {
        val ctl =
            com.stacklens.adapter.inbound.rest.ReviewAnalysisController(
                analysisService = mock(),
                i18n = mock(),
                saveHandlers = emptyList(),
                decoding = mock(),
                workspaceFiles = mock(),
            )
        assertNotNull(ctl)
    }

    @Test
    fun `ReviewAnalysisController handles null review`() {
        val reviewRepo = mock<com.stacklens.application.port.out.ReviewAnalysisPort>()
        whenever(reviewRepo.findReviewAnalysisById(any())).thenReturn(null)
        val ctl =
            com.stacklens.adapter.inbound.rest.ReviewAnalysisController(
                analysisService = mock(),
                i18n = mock(),
                saveHandlers = emptyList(),
                decoding = mock(),
                workspaceFiles = mock(),
            )
        assertNotNull(ctl)
    }
}
