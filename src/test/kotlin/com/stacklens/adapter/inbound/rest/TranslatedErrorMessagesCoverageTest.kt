package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.analysis.CodeAnalysisUseCase
import com.stacklens.application.port.inbound.pipeline.PipelineAnalysisUseCase
import com.stacklens.application.port.inbound.platform.BrowserOpenResult
import com.stacklens.application.port.inbound.platform.ExternalBrowserUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.PipelineAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TranslatedErrorMessagesCoverageTest {
    private fun browserDenied(url: String?): ExternalBrowserUseCase {
        val browser = mock<ExternalBrowserUseCase>()
        whenever(browser.openUrl(anyOrNull())).thenAnswer { invocation ->
            val submitted = invocation.getArgument<String?>(0)
            if (submitted.isNullOrBlank()) {
                BrowserOpenResult(opened = false, errorKey = MISSING_URL_KEY)
            } else {
                BrowserOpenResult(opened = false, errorKey = INVALID_SCHEME_KEY)
            }
        }
        return browser
    }

    private fun i18nReturning(text: String): I18nUseCase {
        val i18n = mock<I18nUseCase>()
        whenever(i18n.t(any(), *emptyArray())).thenReturn(text)
        return i18n
    }

    @Test
    fun `the desktop controller reports the translated missing url error`() {
        val response = DesktopIntegrationController(browserDenied(null), i18nReturning("URL fehlt")).openUrl(emptyMap())

        assertEquals("URL fehlt", response.body?.get("error"))
    }

    @Test
    fun `the desktop controller reports the translated invalid scheme error`() {
        val response = DesktopIntegrationController(browserDenied("ftp://x"), i18nReturning("Schema falsch")).openUrl(mapOf("url" to "ftp://x"))

        assertEquals("Schema falsch", response.body?.get("error"))
    }

    @Test
    fun `the code analysis controller reports the translated mr hint`() {
        val service = mock<CodeAnalysisUseCase>()
        whenever(service.getAnalysis(1L)).thenReturn(CodeAnalysis(id = 1L))

        val response =
            CodeAnalysisApiController(service, mock<WorkspaceFileUseCase>(), i18nReturning("Bitte Projekt wählen"))
                .createMr(1L, CodeAnalysisApiController.CreateMrDto())

        assertEquals("Bitte Projekt wählen", response.body?.get("error"))
    }

    @Test
    fun `the pipeline analysis controller reports the translated provider error`() {
        val service = mock<PipelineAnalysisUseCase>()
        whenever(service.getAnalysis(1L)).thenReturn(PipelineAnalysis(id = 1L))
        whenever(service.createMrOrPr(any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException())

        val response = PipelineAnalysisApiController(service, i18nReturning("Provider nicht erreichbar")).createMrOrPr(1L, null)

        assertEquals("Provider nicht erreichbar", response.body?.get("error"))
    }

    private companion object {
        private const val MISSING_URL_KEY: String = "app.be_missing_url"
        private const val INVALID_SCHEME_KEY: String = "app.be_invalid_url_scheme"
    }
}
