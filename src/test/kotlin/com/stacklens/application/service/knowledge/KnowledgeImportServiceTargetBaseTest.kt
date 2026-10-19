package com.stacklens.application.service.knowledge

import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveManifest
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveReaderPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class KnowledgeImportServiceTargetBaseTest {
    private val basePort: KnowledgeBasePort = mock()
    private val service =
        knowledgeImportService(
            basePort = basePort,
            sourcePort = mock<KnowledgeSourcePort>(),
            documentPort = mock<KnowledgeDocumentPort>(),
            chunkPort = mock<KnowledgeChunkPort>(),
            readerPort = mock<KnowledgeArchiveReaderPort>(),
            indexPort = mock<KnowledgeIndexPort>(),
            hashing = mock<com.stacklens.application.port.out.knowledge.KnowledgeHashingPort>(),
            clock = com.stacklens.testsupport.FixedClockPort(1_750_000_000_000L),
            fileReadWritePort = mock(),
            bundlePort = mock(),
        )

    private fun manifest(name: String) =
        KnowledgeArchiveManifest(
            format = "slkn",
            version = 1,
            exportedAt = "2025-01-01T00:00:00Z",
            stacklensVersion = "1.0",
            knowledgeBaseStableId = "stable",
            knowledgeBaseName = name,
            knowledgeBaseDescription = "desc",
            sourceCount = 0,
            documentCount = 0,
            chunkCount = 0,
            containsVectors = false,
            embeddingProvider = null,
            embeddingModel = null,
            embeddingDimension = null,
            embeddingProfile = null,
        )

    private fun resolve(
        request: KnowledgeImportRequest,
        name: String,
    ): KnowledgeBase {
        val method =
            KnowledgeArchivePersistence::class.java.getDeclaredMethod(
                "resolveTargetBase",
                KnowledgeImportRequest::class.java,
                KnowledgeArchiveManifest::class.java,
            )
        method.isAccessible = true
        return method.invoke(service.persistence, request, manifest(name)) as KnowledgeBase
    }

    @Test
    fun `a new copy takes the manifest name when it is still free`() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(KnowledgeBase(name = "other")))
        whenever(basePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeBase }

        val base = resolve(KnowledgeImportRequest(byteArrayOf(1), null), "Docs")

        assertEquals("Docs", base.name)
        assertEquals("desc", base.description)
    }

    @Test
    fun `a merge import targets the existing base by name`() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(KnowledgeBase(name = "Docs")))
        whenever(basePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeBase }

        val base = resolve(KnowledgeImportRequest(byteArrayOf(1), null, KnowledgeImportMode.MERGE), "Docs")

        assertEquals("Docs", base.name)
    }

    @Test
    fun `an unnamed archive of an unnamed base gets the import suffix`() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(KnowledgeBase(name = "")))
        whenever(basePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeBase }

        val base = resolve(KnowledgeImportRequest(byteArrayOf(1), null), "")

        assertEquals(" (Import)", base.name)
    }

    @Test
    fun `a replace import prefers the targeted id and then the name`() {
        val existing = KnowledgeBase(id = 7L, name = "Target", updatedAt = LocalDateTime.now())
        whenever(basePort.findById(7L)).thenReturn(existing)
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(existing))

        val byId = resolve(KnowledgeImportRequest(byteArrayOf(1), null, KnowledgeImportMode.REPLACE, 7L), "Docs")
        val byName = resolve(KnowledgeImportRequest(byteArrayOf(1), null, KnowledgeImportMode.REPLACE, null, "Target"), "Docs")

        assertEquals(7L, byId.id)
        assertEquals(7L, byName.id)
    }

    @Test
    fun `an import without any existing base creates one`() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeBase }

        val base = resolve(KnowledgeImportRequest(byteArrayOf(1), null, KnowledgeImportMode.SKIP_EXISTING, null, "   "), "  ")

        assertEquals("   ", base.name)
    }
}
