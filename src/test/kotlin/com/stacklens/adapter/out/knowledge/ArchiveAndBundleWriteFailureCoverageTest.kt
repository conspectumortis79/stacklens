package com.stacklens.adapter.out.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.knowledge.archive.EncryptedKnowledgeArchiveWriter
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord
import com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.zip.ZipException

class ArchiveAndBundleWriteFailureCoverageTest {
    private val mapper = ObjectMapper()

    private fun chunk(
        stableId: String,
        vector: ByteArray?,
    ) = KnowledgeArchiveRecord.Chunk(
        stableId = stableId,
        documentStableId = "doc",
        ordinal = 0,
        sectionTitle = null,
        content = "content",
        contentHash = "hash",
        tokenEstimate = 2,
        vectorBytes = vector,
        vectorDimension = if (vector == null) null else 2,
    )

    private fun snapshotWith(chunks: List<KnowledgeArchiveRecord.Chunk>) =
        KnowledgeBaseSnapshot(
            knowledgeBaseStableId = "kb",
            knowledgeBaseName = "KB",
            knowledgeBaseDescription = "",
            sources = emptyList(),
            documents = emptyList(),
            chunks = chunks,
            embeddingProvider = null,
            embeddingModel = null,
            embeddingDimension = null,
            embeddingProfile = null,
        )

    @Test
    fun `two chunks sharing one stable id collide on their vector entry name`() {
        val writer = EncryptedKnowledgeArchiveWriter(mapper)
        val duplicated =
            listOf(
                chunk("same", byteArrayOf(0, 0, 0, 1)),
                chunk("same", byteArrayOf(0, 0, 0, 2)),
            )

        assertThrows(ZipException::class.java) {
            writer.write(snapshotWith(duplicated), KnowledgeExportOptions(includeVectors = true), "pw")
        }
    }
}
