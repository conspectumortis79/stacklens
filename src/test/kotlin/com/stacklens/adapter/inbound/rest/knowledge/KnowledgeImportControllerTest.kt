package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeLocalFileImportUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class KnowledgeImportControllerTest {
    private val exportUseCase: KnowledgeExportUseCase = mock()
    private val importUseCase: KnowledgeImportUseCase = mock()
    private val bundleExportUseCase: KnowledgeBundleExportUseCase = mock()
    private val bundleImportUseCase: KnowledgeBundleImportUseCase = mock()
    private val localFileImportUseCase: KnowledgeLocalFileImportUseCase = mock()

    private val controller =
        KnowledgeImportController(
            exportUseCase = exportUseCase,
            importUseCase = importUseCase,
            bundleExportUseCase = bundleExportUseCase,
            bundleImportUseCase = bundleImportUseCase,
            localFileImportUseCase = localFileImportUseCase,
        )

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(KnowledgeExceptionAdvice())
            .build()

    @Test
    fun `exportBase delegates to the export use case with the body fields`() {
        whenever(
            exportUseCase.exportBase(
                knowledgeBaseId = any(),
                options = any(),
                password = any(),
            ),
        ).thenReturn(byteArrayOf(0x01, 0x02, 0x03, 0x04))

        mockMvc
            .perform(
                post("/api/knowledge/bases/1/export")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"password":"secret","includeVectors":false}"""),
            ).andExpect(status().isOk)

        verify(exportUseCase).exportBase(
            knowledgeBaseId = eq(1L),
            options = any(),
            password = eq("secret"),
        )
    }

    @Test
    fun `exportAll delegates to the bundle export use case`() {
        whenever(
            bundleExportUseCase.exportAll(
                options = any(),
                password = any(),
            ),
        ).thenReturn(byteArrayOf(0x10, 0x20, 0x30, 0x40))

        mockMvc
            .perform(
                post("/api/knowledge/export-all")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"password":"secret"}"""),
            ).andExpect(status().isOk)

        verify(bundleExportUseCase).exportAll(
            options = any(),
            password = eq("secret"),
        )
    }

    @Test
    fun `importBundleLocal rejects unsupported file extensions via the advice`() {
        whenever(localFileImportUseCase.importFromLocalFile(any(), any(), any()))
            .thenThrow(IllegalArgumentException("Ungültige Dateiendung: foo.zip"))

        mockMvc
            .perform(
                post("/api/knowledge/import-bundle-local")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"/tmp/foo.zip","password":"secret","mode":"NEW_COPY"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `importLocal rejects unsupported file extensions via the advice`() {
        whenever(localFileImportUseCase.importAggregatedFromLocalFile(any(), any(), any()))
            .thenThrow(IllegalArgumentException("Ungültige Dateiendung: foo.zip"))

        mockMvc
            .perform(
                post("/api/knowledge/import-local")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"/tmp/foo.zip","password":"secret","mode":"NEW_COPY"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `inspectImport delegates to the use case`() {
        whenever(exportUseCase.inspect(any(), any())).thenReturn(
            com.stacklens.application.port.model.KnowledgeImportPreview(
                knowledgeBaseName = "preview",
                sourceCount = 0,
                documentCount = 0,
                chunkCount = 0,
                containsVectors = false,
                embeddingProvider = null,
                embeddingModel = null,
                embeddingDimension = null,
            ),
        )

        val file =
            org.springframework.mock.web.MockMultipartFile(
                "file",
                "alpha.slknowledge",
                "application/octet-stream",
                byteArrayOf(0x53, 0x4C, 0x4B, 0x4E),
            )

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .multipart("/api/knowledge/import/inspect")
                    .file(file)
                    .part(
                        org.springframework.mock.web.MockPart(
                            "password",
                            "secret".toByteArray(),
                        ),
                    ),
            ).andExpect(status().isOk)
    }
}
