package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundlePreviewEntry
import com.stacklens.application.port.inbound.knowledge.KnowledgeExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeLocalFileImportUseCase
import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportPreview
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockPart
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class KnowledgeImportControllerBranchTest {
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

    private fun result(
        id: Long,
        name: String,
        documents: Int = 1,
        chunks: Int = 2,
        vectors: Int = 3,
    ) = KnowledgeImportResult(id, name, documents, chunks, vectors, KnowledgeImportMode.NEW_COPY)

    @Test
    fun `importBase passes includeVectors=true by default when the part is missing`() {
        whenever(importUseCase.import(any())).thenReturn(
            KnowledgeImportResult(
                knowledgeBaseId = 7L,
                knowledgeBaseName = "x",
                documentsImported = 1,
                chunksImported = 2,
                vectorsImported = 3,
                mode = KnowledgeImportMode.NEW_COPY,
            ),
        )

        mockMvc
            .perform(
                multipart("/api/knowledge/import")
                    .file(MockMultipartFile("file", "x.slknowledge", "application/octet-stream", "SLKNfake".toByteArray())),
            ).andExpect(status().isOk)

        verify(importUseCase).import(
            org.mockito.kotlin
                .argThat { req: KnowledgeImportRequest -> req.includeVectors == true && req.mode == KnowledgeImportMode.NEW_COPY },
        )
    }

    @Test
    fun `importBase honours includeVectors=false`() {
        whenever(importUseCase.import(any())).thenReturn(
            KnowledgeImportResult(
                knowledgeBaseId = 7L,
                knowledgeBaseName = "x",
                documentsImported = 0,
                chunksImported = 0,
                vectorsImported = 0,
                mode = KnowledgeImportMode.NEW_COPY,
            ),
        )

        val bytes = "SLKN".toByteArray()
        mockMvc
            .perform(
                multipart("/api/knowledge/import")
                    .file(MockMultipartFile("file", "x.slknowledge", "application/octet-stream", bytes))
                    .part(
                        MockPart(
                            "password",
                            "p".toByteArray(),
                        ),
                    ).part(
                        MockPart(
                            "includeVectors",
                            "false".toByteArray(),
                        ),
                    ).part(
                        MockPart(
                            "mode",
                            "REPLACE".toByteArray(),
                        ),
                    ),
            ).andExpect(status().isOk)

        verify(importUseCase).import(
            org.mockito.kotlin
                .argThat { req: KnowledgeImportRequest -> req.includeVectors == false && req.mode == KnowledgeImportMode.REPLACE },
        )
    }

    @Test
    fun `importBase accepts a target knowledge base id and a missing password`() {
    }

    @Test
    fun `importBase parses the lower-case mode string into the corresponding enum value`() {
        whenever(importUseCase.import(any())).thenReturn(
            KnowledgeImportResult(
                knowledgeBaseId = 7L,
                knowledgeBaseName = "x",
                documentsImported = 0,
                chunksImported = 0,
                vectorsImported = 0,
                mode = KnowledgeImportMode.MERGE,
            ),
        )

        mockMvc
            .perform(
                multipart("/api/knowledge/import")
                    .file(MockMultipartFile("file", "x.slknowledge", "application/octet-stream", "SLKN".toByteArray()))
                    .part(
                        MockPart(
                            "mode",
                            "merge".toByteArray(),
                        ),
                    ),
            ).andExpect(status().isOk)
    }

    @Test
    fun `importBase falls back to NEW_COPY when the mode is unknown`() {
        whenever(importUseCase.import(any())).thenReturn(
            KnowledgeImportResult(
                knowledgeBaseId = 7L,
                knowledgeBaseName = "x",
                documentsImported = 0,
                chunksImported = 0,
                vectorsImported = 0,
                mode = KnowledgeImportMode.NEW_COPY,
            ),
        )

        mockMvc
            .perform(
                multipart("/api/knowledge/import")
                    .file(MockMultipartFile("file", "x.slknowledge", "application/octet-stream", "SLKN".toByteArray()))
                    .part(
                        MockPart(
                            "mode",
                            "bogus".toByteArray(),
                        ),
                    ),
            ).andExpect(status().isOk)

        verify(importUseCase).import(
            org.mockito.kotlin
                .argThat { req: KnowledgeImportRequest -> req.mode == KnowledgeImportMode.NEW_COPY },
        )
    }

    @Test
    fun `exportBase uses includeVectors from the request`() {
        whenever(
            exportUseCase.exportBase(
                any(),
                org.mockito.kotlin.any(),
                any(),
            ),
        ).thenReturn(byteArrayOf(0x01))

        mockMvc
            .perform(
                post("/api/knowledge/bases/42/export")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"password":"p","includeVectors":false}"""),
            ).andExpect(status().isOk)

        verify(exportUseCase).exportBase(
            eq(42L),
            org.mockito.kotlin
                .argThat { opts: KnowledgeExportOptions -> opts.includeVectors == false },
            eq("p"),
        )
    }

    @Test
    fun `exportBase sets the Content-Disposition attachment filename`() {
        whenever(exportUseCase.exportBase(any(), any(), any()))
            .thenReturn(byteArrayOf(0x01))

        mockMvc
            .perform(
                post("/api/knowledge/bases/13/export")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"password":"p"}"""),
            ).andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("stacklens-knowledge-13")))
    }

    @Test
    fun `exportAll delegates to the bundle export use case`() {
        whenever(bundleExportUseCase.exportAll(any(), any())).thenReturn(byteArrayOf(0x01))

        mockMvc
            .perform(
                post("/api/knowledge/export-all")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"password":"p"}"""),
            ).andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("stacklens-knowledge-all")))
    }

    @Test
    fun `importBundle delegates to the bundle import use case`() {
        whenever(bundleImportUseCase.import(any(), any(), any()))
            .thenReturn(emptyList())

        mockMvc
            .perform(
                multipart("/api/knowledge/import-bundle")
                    .file(MockMultipartFile("file", "x.slbundle", "application/octet-stream", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x66, 0x61, 0x6B, 0x65)))
                    .part(MockPart("password", "p".toByteArray()))
                    .part(MockPart("mode", "REPLACE".toByteArray())),
            ).andExpect(status().isOk)
    }

    @Test
    fun `importBundle rejects missing password`() {
        mockMvc
            .perform(
                multipart("/api/knowledge/import-bundle")
                    .file(MockMultipartFile("file", "x.slbundle", "application/octet-stream", byteArrayOf(0x50, 0x4B, 0x03, 0x04))),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `importLocal reports a missing file through the advice`() {
        whenever(localFileImportUseCase.importAggregatedFromLocalFile(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalArgumentException("Datei nicht gefunden"))

        mockMvc
            .perform(
                post("/api/knowledge/import-local")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"/does/not/exist.slknowledge","password":null,"mode":null}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `importLocal reports an unsupported file extension through the advice`() {
        whenever(localFileImportUseCase.importAggregatedFromLocalFile(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalArgumentException("Ungültige Dateiendung"))

        mockMvc
            .perform(
                post("/api/knowledge/import-local")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"/tmp/archive.zip","password":"p","mode":null}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `importLocal maps the aggregated result to the response`() {
        whenever(localFileImportUseCase.importAggregatedFromLocalFile(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(listOf(result(7L, "x")))

        mockMvc
            .perform(
                post("/api/knowledge/import-local")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"/tmp/archive.slknowledge","password":"p","mode":"REPLACE"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.knowledgeBaseId").value(7))
            .andExpect(jsonPath("$.knowledgeBaseName").value("x"))

        verify(localFileImportUseCase).importAggregatedFromLocalFile(eq("/tmp/archive.slknowledge"), eq("p"), eq(KnowledgeImportMode.REPLACE))
    }

    @Test
    fun `importLocal returns the aggregated bundle totals from the use case`() {
        whenever(localFileImportUseCase.importAggregatedFromLocalFile(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(listOf(result(7L, "first, second", documents = 3, chunks = 3, vectors = 3)))

        mockMvc
            .perform(
                post("/api/knowledge/import-local")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"/tmp/bundle.slbundle","password":"p","mode":"REPLACE"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.documentsImported").value(3))
            .andExpect(jsonPath("$.knowledgeBaseName").value("first, second"))
    }

    @Test
    fun `importLocal with a bundle and no password returns BadRequest`() {
        whenever(localFileImportUseCase.importAggregatedFromLocalFile(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalArgumentException("Passwort ist erforderlich"))

        mockMvc
            .perform(
                post("/api/knowledge/import-local")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"/tmp/bundle.slbundle","password":null,"mode":null}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `importLocal with a non-archive blob throws IllegalArgumentException`() {
        whenever(localFileImportUseCase.importAggregatedFromLocalFile(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalArgumentException("Unbekanntes Archivformat"))

        mockMvc
            .perform(
                post("/api/knowledge/import-local")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"/tmp/archive.slknowledge","password":"p","mode":null}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `importBundleLocal delegates to the local file import use case`() {
        whenever(localFileImportUseCase.importFromLocalFile(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(listOf(result(7L, "x")))

        mockMvc
            .perform(
                post("/api/knowledge/import-bundle-local")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"/tmp/archive.slknowledge","password":"p","mode":null}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].knowledgeBaseId").value(7))
    }

    @Test
    fun `importBundleLocal requires a password for the bundle branch`() {
        whenever(localFileImportUseCase.importFromLocalFile(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalArgumentException("Passwort ist erforderlich"))

        mockMvc
            .perform(
                post("/api/knowledge/import-bundle-local")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"/tmp/bundle.slbundle","password":null,"mode":null}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `inspectImport delegates to the export use case and passes the password if present`() {
        whenever(exportUseCase.inspect(any(), any()))
            .thenReturn(
                KnowledgeImportPreview(
                    knowledgeBaseName = "x",
                    sourceCount = 1,
                    documentCount = 2,
                    chunkCount = 3,
                    containsVectors = true,
                    embeddingProvider = null,
                    embeddingModel = null,
                    embeddingDimension = null,
                ),
            )

        mockMvc
            .perform(
                multipart("/api/knowledge/import/inspect")
                    .file(MockMultipartFile("file", "x.slknowledge", "application/octet-stream", "SLKN".toByteArray()))
                    .part(
                        MockPart(
                            "password",
                            "p".toByteArray(),
                        ),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceCount").value(1))
    }

    @Test
    fun `inspectImport passes a null password when only blank or whitespace is provided`() {
        whenever(exportUseCase.inspect(any(), any())).thenReturn(
            KnowledgeImportPreview(
                knowledgeBaseName = "x",
                sourceCount = 0,
                documentCount = 0,
                chunkCount = 0,
                containsVectors = false,
                embeddingProvider = null,
                embeddingModel = null,
                embeddingDimension = null,
            ),
        )

        mockMvc
            .perform(
                multipart("/api/knowledge/import/inspect")
                    .file(MockMultipartFile("file", "x.slknowledge", "application/octet-stream", "SLKN".toByteArray()))
                    .part(
                        MockPart(
                            "password",
                            "   ".toByteArray(),
                        ),
                    ),
            ).andExpect(status().isOk)

        verify(exportUseCase).inspect(any(), eq(null as String?))
    }

    @Test
    fun `inspectBundleImport delegates to the bundle import use case`() {
        whenever(bundleImportUseCase.inspect(any()))
            .thenReturn(
                com.stacklens.application.port.inbound.knowledge.KnowledgeBundlePreview(
                    entryCount = 1,
                    bases =
                        listOf(
                            KnowledgeBundlePreviewEntry(
                                entryName = "bundle",
                                knowledgeBaseStableId = "kb1",
                                knowledgeBaseName = "kb1",
                                knowledgeBaseDescription = "desc",
                                sourceCount = 1,
                                documentCount = 2,
                                chunkCount = 3,
                                containsVectors = true,
                            ),
                        ),
                ),
            )

        mockMvc
            .perform(
                multipart("/api/knowledge/import-bundle/inspect")
                    .file(MockMultipartFile("file", "x.slbundle", "application/octet-stream", byteArrayOf(0x50, 0x4B, 0x03, 0x04))),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `parseMode falls back to NEW_COPY for an unknown mode`() {
        val method =
            KnowledgeImportController::class.java.declaredMethods.first { it.name == "parseMode" }
        method.isAccessible = true
        val mode = method.invoke(controller, "totally-bogus") as KnowledgeImportMode
        assertEquals(KnowledgeImportMode.NEW_COPY, mode)
    }

    @Test
    fun `parseMode matches case-insensitively`() {
        val method =
            KnowledgeImportController::class.java.declaredMethods.first { it.name == "parseMode" }
        method.isAccessible = true
        assertEquals(KnowledgeImportMode.NEW_COPY, method.invoke(controller, "new_copy"))
        assertEquals(KnowledgeImportMode.REPLACE, method.invoke(controller, "replace"))
    }

    @Test
    fun `requirePassword throws when blank`() {
        val method =
            KnowledgeImportController::class.java.declaredMethods.first { it.name == "requirePassword" }
        method.isAccessible = true
        val ex =
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
                try {
                    method.invoke(controller, "   ")
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw e.targetException
                }
            }
        assertNotNull(ex)
    }
}
