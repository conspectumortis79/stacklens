package com.stacklens.adapter.inbound.rest.knowledge
import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeMaintenanceUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeReindexUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class KnowledgeOperationsControllerCoverageTest {
    private val maintenance = mock<KnowledgeMaintenanceUseCase>()
    private val reindexService = mock<KnowledgeReindexUseCase>()
    private val baseUseCase = mock<KnowledgeBaseUseCase>()
    private val controller = KnowledgeOperationsController(maintenance, reindexService)

    private fun result(
        id: Long,
        chunks: Int,
    ) = KnowledgeReindexUseCase.ReindexResult(knowledgeBaseId = id, knowledgeBaseName = "base-$id", chunkCount = chunks)

    @Test
    fun `reindexAll aggregates the chunk counts of every base`() {
        whenever(reindexService.reindexAll()).thenReturn(listOf(result(1L, 12), result(2L, 5)))

        val response = controller.reindexAll()

        assertEquals(true, response["ok"])
        assertEquals(2, response["baseCount"])
        assertEquals(17, response["totalChunks"])
        assertEquals(mapOf(1L to 12, 2L to 5), response["results"])
    }

    @Test
    fun `reindexAll reports zero totals when no base exists`() {
        whenever(reindexService.reindexAll()).thenReturn(emptyList())

        val response = controller.reindexAll()

        assertEquals(0, response["baseCount"])
        assertEquals(0, response["totalChunks"])
        assertEquals(emptyMap<Long, Int>(), response["results"])
    }

    @Test
    fun `resetKnowledge reports the reset timestamp of the use case`() {
        whenever(maintenance.resetKnowledge()).thenReturn(Instant.ofEpochMilli(1_700_000_000_000L))

        val response = controller.resetKnowledge()

        assertEquals(true, response["ok"])
        assertEquals("2023-11-14T22:13:20Z", response["resetAt"])
    }
}
