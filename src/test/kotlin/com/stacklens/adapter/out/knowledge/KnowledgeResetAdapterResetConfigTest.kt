package com.stacklens.adapter.out.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexGenerationPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.nio.file.Files
import java.nio.file.Paths

class KnowledgeResetAdapterResetConfigTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val jobPort: KnowledgeIngestionJobPort = mock()
    private val indexGenerationPort: KnowledgeIndexGenerationPort = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val vectorIndexPort: KnowledgeIndexPort = mock()

    private fun buildAdapter(indexDir: String? = null) =
        KnowledgeResetAdapter(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            jobPort = jobPort,
            indexGenerationPort = indexGenerationPort,
            vectorIndexPort = vectorIndexPort,
            indexDir = indexDir ?: "/tmp/reset-config-test-${System.nanoTime()}",
        )

    @Test
    fun `resetAllKnowledgeData does NOT delete the embedding config`() {
        buildAdapter().resetAllKnowledgeData()

        verify(configPort, never()).delete()
    }

    @Test
    fun `resetAllKnowledgeData still deletes chunks, documents, sources, jobs, generations and bases in correct order`() {
        buildAdapter().resetAllKnowledgeData()

        val ordered = inOrder(chunkPort, documentPort, sourcePort, jobPort, indexGenerationPort, basePort)
        ordered.verify(chunkPort).deleteAll()
        ordered.verify(documentPort).deleteAll()
        ordered.verify(sourcePort).deleteAll()
        ordered.verify(jobPort).deleteAll()
        ordered.verify(indexGenerationPort).deleteAll()
        ordered.verify(basePort).deleteAll()
    }

    @Test
    fun `resetAllKnowledgeData skips index dir delete when it does not exist`() {
        val missingDir = "/tmp/does-not-exist-${System.nanoTime()}"
        buildAdapter(indexDir = missingDir).resetAllKnowledgeData()

        assert(!Files.exists(Paths.get(missingDir)))
    }

    @Test
    fun `resetAllKnowledgeData best-effort deletes existing index dir`() {
        val tmpDir = Files.createTempDirectory("knowledge-reset-test-")
        Files.createFile(tmpDir.resolve("a.txt"))
        Files.createDirectory(tmpDir.resolve("sub"))
        Files.createFile(tmpDir.resolve("sub").resolve("b.txt"))

        buildAdapter(indexDir = tmpDir.toString()).resetAllKnowledgeData()

        assert(!Files.exists(tmpDir))
    }

    @Test
    fun `clearKnowledgeBase deletes per-base entries and clears the vector index`() {
        val base = KnowledgeBase(id = 42L, name = "b")

        buildAdapter().clearKnowledgeBase(base)

        verify(chunkPort).deleteByKnowledgeBaseId(42L)
        verify(documentPort).deleteByKnowledgeBaseId(42L)
        verify(sourcePort).deleteByKnowledgeBaseId(42L)
        verify(indexGenerationPort).deleteByKnowledgeBaseId(42L)
        verify(vectorIndexPort).clearKnowledgeBase(42L)
    }
}
