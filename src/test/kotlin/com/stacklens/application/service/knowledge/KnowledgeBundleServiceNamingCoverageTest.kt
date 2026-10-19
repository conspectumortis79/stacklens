package com.stacklens.application.service.knowledge

import com.stacklens.application.collaboration.KnowledgeBaseReader
import com.stacklens.application.collaboration.KnowledgeExporter
import com.stacklens.application.collaboration.KnowledgeImporter
import com.stacklens.application.port.model.KnowledgeImportPreview
import com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive
import com.stacklens.application.port.out.knowledge.KnowledgeBundlePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KnowledgeBundleServiceNamingCoverageTest {
    private val bundlePort: KnowledgeBundlePort = mock()
    private val baseUseCase: KnowledgeBaseReader = mock()
    private val singleBaseExport: KnowledgeExporter = mock()
    private val service = KnowledgeBundleService(bundlePort, baseUseCase, singleBaseExport, mock<KnowledgeImporter>())

    private val archiveBytes = "archive-bytes".toByteArray()

    private fun collectWritten(): MutableList<KnowledgeBundleArchive> {
        val written = mutableListOf<KnowledgeBundleArchive>()
        whenever(bundlePort.write(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            written += invocation.arguments[0] as List<KnowledgeBundleArchive>
            "bundle".toByteArray()
        }
        return written
    }

    private fun preview(name: String) =
        KnowledgeImportPreview(
            knowledgeBaseName = name,
            sourceCount = 1,
            documentCount = 2,
            chunkCount = 3,
            containsVectors = false,
            embeddingProvider = null,
            embeddingModel = null,
            embeddingDimension = null,
        )

    @Test
    fun `a blank archive name falls back to the base name and duplicates are numbered`() {
        whenever(singleBaseExport.exportBase(any(), any(), any())).thenReturn(archiveBytes)
        whenever(singleBaseExport.inspect(eq(archiveBytes), any())).thenReturn(preview("   "))
        whenever(baseUseCase.listBases()).thenReturn(
            listOf(
                KnowledgeBase(id = 1L, name = "Team Wissen"),
                KnowledgeBase(id = 2L, name = "Team Wissen"),
                KnowledgeBase(id = 3L, name = "Team Wissen"),
                KnowledgeBase(id = 4L, name = "!!"),
            ),
        )
        val written = collectWritten()

        service.exportAll(KnowledgeExportOptions(), "pw")

        assertEquals(
            listOf("bases/team-wissen.slknowledge", "bases/team-wissen-2.slknowledge", "bases/team-wissen-3.slknowledge", "bases/base.slknowledge"),
            written.map { it.entryName },
        )
        assertTrue(written.all { it.displayName.isNotBlank() })
        assertEquals("", written.last().knowledgeBaseDescription)
    }

    @Test
    fun `a named archive wins over the configured base name`() {
        whenever(singleBaseExport.exportBase(any(), any(), any())).thenReturn(archiveBytes)
        whenever(singleBaseExport.inspect(eq(archiveBytes), any())).thenReturn(preview("Archiv Name"))
        whenever(baseUseCase.listBases()).thenReturn(listOf(KnowledgeBase(id = 5L, name = "Base Name").apply { description = "text" }))
        val written = collectWritten()

        service.exportAll(KnowledgeExportOptions(), "pw")

        assertEquals("Archiv Name", written.single().displayName)
        assertEquals("bases/archiv-name.slknowledge", written.single().entryName)
        assertEquals("text", written.single().knowledgeBaseDescription)
    }
}
