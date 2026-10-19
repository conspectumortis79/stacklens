package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KnowledgeSourceListerTest {
    private val sourcePort: KnowledgeSourcePort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val sut = KnowledgeSourceLister(sourcePort, chunkPort)

    @Test
    fun `listSources returns all sources when knowledgeBaseId is null`() {
        val all = listOf(source(1L), source(2L))
        whenever(sourcePort.findAllByOrderByCreatedAtAsc()).thenReturn(all)
        whenever(chunkPort.sumContentLengthBySourceIds(listOf(1L, 2L))).thenReturn(mapOf(1L to 100L, 2L to 200L))

        val result = sut.listSources(knowledgeBaseId = null)

        assertThat(result).hasSize(2)
        assertThat(result[0].totalBytes).isEqualTo(100L)
        assertThat(result[1].totalBytes).isEqualTo(200L)
    }

    @Test
    fun `listSources filters by knowledgeBaseId when provided`() {
        val base = listOf(source(3L))
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(base)
        whenever(chunkPort.sumContentLengthBySourceIds(listOf(3L))).thenReturn(mapOf(3L to 50L))

        val result = sut.listSources(knowledgeBaseId = 7L)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(3L)
        assertThat(result[0].totalBytes).isEqualTo(50L)
    }

    @Test
    fun `listSources returns empty list without touching chunkPort when no sources`() {
        whenever(sourcePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())

        val result = sut.listSources(knowledgeBaseId = null)

        assertThat(result).isEmpty()
        org.mockito.kotlin
            .verify(chunkPort, org.mockito.kotlin.never())
            .sumContentLengthBySourceIds(any())
    }

    @Test
    fun `listSourcesByType returns only sources of the requested type`() {
        val confluence = listOf(source(1L, KnowledgeSourceType.CONFLUENCE), source(2L, KnowledgeSourceType.CONFLUENCE))
        val jira = listOf(source(3L, KnowledgeSourceType.JIRA))
        whenever(sourcePort.findAllByOrderByCreatedAtAsc()).thenReturn(confluence + jira)
        whenever(chunkPort.sumContentLengthBySourceIds(listOf(1L, 2L))).thenReturn(mapOf(1L to 10L, 2L to 20L))

        val result = sut.listSourcesByType(KnowledgeSourceType.CONFLUENCE)

        assertThat(result).hasSize(2)
        assertThat(result).allMatch { it.type == KnowledgeSourceType.CONFLUENCE }
    }

    @Test
    fun `getSource returns null when source does not exist`() {
        whenever(sourcePort.findById(99L)).thenReturn(null)
        assertThat(sut.getSource(99L)).isNull()
    }

    @Test
    fun `getSource populates totalBytes from chunkPort`() {
        val s = source(11L)
        whenever(sourcePort.findById(11L)).thenReturn(s)
        whenever(chunkPort.sumContentLengthBySourceIds(eq(listOf(11L)))).thenReturn(mapOf(11L to 333L))

        val result = sut.getSource(11L)

        assertThat(result).isNotNull()
        assertThat(result!!.totalBytes).isEqualTo(333L)
    }

    private fun source(
        id: Long,
        type: KnowledgeSourceType = KnowledgeSourceType.MANUAL,
    ): KnowledgeSource =
        KnowledgeSource(
            id = id,
            knowledgeBaseId = 10L,
            name = "s$id",
            type = type,
        )
}
