package com.stacklens.adapter.out.persistence

import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.domain.model.ImageExtractionRecord
import com.stacklens.domain.model.JiraTicketImplementation
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.SearchSession
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.domain.model.SprintReview
import com.stacklens.domain.model.SqlAnalysisRecord
import com.stacklens.domain.model.TicketCreationRecord
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.orm.ObjectOptimisticLockingFailureException

class RecentAnalysesRepositoriesTest {
    private inline fun <reified T> realDefaults(): T = Mockito.mock(T::class.java, Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS))

    private fun <T> pageOf(vararg items: T): PageImpl<T> = PageImpl(listOf(*items))

    @Test
    fun `load test analysis repository recent and full list`() {
        val repo: LoadTestAnalysisRepository = realDefaults()
        val analysis = mock<LoadTestAnalysis>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(analysis))
        assertEquals(listOf(analysis), repo.findRecentLoadTestAnalyses(5))
        val sortable = mock<LoadTestAnalysis>()
        whenever(repo.findAll(any<Sort>())).thenReturn(listOf(sortable))
        assertEquals(listOf(sortable), repo.findAllLoadTestAnalysesOrderByCreatedAtDesc())
        whenever(repo.save(analysis)).thenReturn(analysis)
        assertEquals(analysis, repo.saveLoadTestAnalysis(analysis))
        doNothing().whenever(repo).deleteById(any())
        repo.deleteLoadTestAnalysisById(1L)
        verify(repo).deleteById(1L)
        doNothing().whenever(repo).deleteAll()
        repo.deleteAllLoadTestAnalyses()
        verify(repo).deleteAll()
    }

    @Test
    fun `ticket creation record repository recent and full list`() {
        val repo: TicketCreationRecordRepository = realDefaults()
        val record = mock<TicketCreationRecord>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(record))
        assertEquals(listOf(record), repo.findRecentTicketCreationRecords(5))
        whenever(repo.findAll(any<Sort>())).thenReturn(listOf(record))
        assertEquals(listOf(record), repo.findAllTicketCreationRecordsByCreatedAtDesc())
        whenever(repo.save(record)).thenReturn(record)
        assertEquals(record, repo.saveTicketCreationRecord(record))
        doNothing().whenever(repo).deleteById(any())
        repo.deleteTicketCreationRecordById(1L)
        verify(repo).deleteById(1L)
    }

    @Test
    fun `security analysis repository recent and full list`() {
        val repo: SecurityAnalysisRepository = realDefaults()
        val analysis = mock<SecurityAnalysis>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(analysis))
        assertEquals(listOf(analysis), repo.findRecentSecurityAnalyses(5))
        whenever(repo.findAll(any<Sort>())).thenReturn(listOf(analysis))
        assertEquals(listOf(analysis), repo.findAllSecurityAnalysesOrderByCreatedAtDesc())
        whenever(repo.save(analysis)).thenReturn(analysis)
        assertEquals(analysis, repo.saveSecurityAnalysis(analysis))
        doNothing().whenever(repo).deleteById(any())
        repo.deleteSecurityAnalysisById(1L)
        verify(repo).deleteById(1L)
        doNothing().whenever(repo).deleteAll()
        repo.deleteAllSecurityAnalyses()
        verify(repo).deleteAll()
    }

    @Test
    fun `pipeline analysis repository recent and full list`() {
        val repo: PipelineAnalysisRepository = realDefaults()
        val analysis = mock<PipelineAnalysis>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(analysis))
        assertEquals(listOf(analysis), repo.findRecentPipelineAnalyses(5))
        whenever(repo.findAll(any<Sort>())).thenReturn(listOf(analysis))
        assertEquals(listOf(analysis), repo.findAllPipelineAnalysesOrderByCreatedAtDesc())
        whenever(repo.save(analysis)).thenReturn(analysis)
        assertEquals(analysis, repo.savePipelineAnalysis(analysis))
        doNothing().whenever(repo).deleteById(any())
        repo.deletePipelineAnalysisById(1L)
        verify(repo).deleteById(1L)
        doNothing().whenever(repo).deleteAll()
        repo.deleteAllPipelineAnalyses()
        verify(repo).deleteAll()
    }

    @Test
    fun `code analysis repository recent and full list`() {
        val repo: CodeAnalysisRepository = realDefaults()
        val analysis = mock<CodeAnalysis>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(analysis))
        assertEquals(listOf(analysis), repo.findRecentCodeAnalyses(5))
        whenever(repo.findAll(any<Sort>())).thenReturn(listOf(analysis))
        assertEquals(listOf(analysis), repo.findAllCodeAnalysesOrderByCreatedAtDesc())
        whenever(repo.save(analysis)).thenReturn(analysis)
        assertEquals(analysis, repo.saveCodeAnalysis(analysis))
        doNothing().whenever(repo).deleteById(any())
        repo.deleteCodeAnalysisById(1L)
        verify(repo).deleteById(1L)
        doNothing().whenever(repo).deleteAll()
        repo.deleteAllCodeAnalyses()
        verify(repo).deleteAll()
    }

    @Test
    fun `ticket refinement repository recent list`() {
        val repo: TicketRefinementRepository = realDefaults()
        val entity = mock<TicketRefinement>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(entity))
        assertEquals(listOf(entity), repo.findRecentTicketRefinements(5))
        whenever(repo.save(entity)).thenReturn(entity)
        assertEquals(entity, repo.saveTicketRefinement(entity))
        doNothing().whenever(repo).deleteById(any())
        repo.deleteTicketRefinementById(1L)
        verify(repo).deleteById(1L)
        doNothing().whenever(repo).deleteAll()
        repo.deleteAllTicketRefinements()
        verify(repo).deleteAll()
    }

    @Test
    fun `image extraction repository recent list and crud`() {
        val repo: ImageExtractionRepository = realDefaults()
        val record = mock<ImageExtractionRecord>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(record))
        assertEquals(listOf(record), repo.findRecentImageExtractions(5))
        whenever(repo.save(record)).thenReturn(record)
        assertEquals(record, repo.saveImageExtraction(record))
        doNothing().whenever(repo).deleteById(any())
        repo.deleteImageExtractionById(1L)
        verify(repo).deleteById(1L)
        doNothing().whenever(repo).deleteAll()
        repo.deleteAllImageExtractions()
        verify(repo).deleteAll()
    }

    @Test
    fun `review analysis repository recent list`() {
        val repo: ReviewAnalysisRepository = realDefaults()
        val entity = mock<ReviewAnalysis>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(entity))
        assertEquals(listOf(entity), repo.findRecentReviewAnalyses(5))
    }

    @Test
    fun `sql analysis repository recent list`() {
        val repo: SqlAnalysisRepository = realDefaults()
        val entity = mock<SqlAnalysisRecord>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(entity))
        assertEquals(listOf(entity), repo.findRecentSqlAnalyses(5))
    }

    @Test
    fun `jira ticket summary repository recent list`() {
        val repo: JiraTicketSummaryRepository = realDefaults()
        val entity = mock<JiraTicketSummary>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(entity))
        assertEquals(listOf(entity), repo.findRecentJiraTicketSummaries(5))
        whenever(repo.save(entity)).thenReturn(entity)
        assertEquals(entity, repo.saveJiraTicketSummary(entity))
        doNothing().whenever(repo).deleteById(any())
        repo.deleteJiraTicketSummaryById(1L)
        verify(repo).deleteById(1L)
        doNothing().whenever(repo).deleteAll()
        repo.deleteAllJiraTicketSummaries()
        verify(repo).deleteAll()
    }

    @Test
    fun `jira ticket implementation repository recent list`() {
        val repo: JiraTicketImplementationRepository = realDefaults()
        val entity = mock<JiraTicketImplementation>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(entity))
        assertEquals(listOf(entity), repo.findRecentJiraTicketImplementations(5))
        whenever(repo.save(entity)).thenReturn(entity)
        assertEquals(entity, repo.saveJiraTicketImplementation(entity))
        doNothing().whenever(repo).deleteById(any())
        repo.deleteJiraTicketImplementationById(1L)
        verify(repo).deleteById(1L)
        doNothing().whenever(repo).deleteAll()
        repo.deleteAllJiraTicketImplementations()
        verify(repo).deleteAll()
    }

    @Test
    fun `confluence tool repository recent list`() {
        val repo: ConfluenceToolRepository = realDefaults()
        val tool = mock<ConfluenceTool>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(tool))
        assertEquals(listOf(tool), repo.findRecentConfluenceTools(5))
        whenever(repo.save(tool)).thenReturn(tool)
        assertEquals(tool, repo.saveConfluenceTool(tool))
        doNothing().whenever(repo).delete(tool)
        repo.deleteConfluenceTool(tool)
        verify(repo).delete(tool)
        doNothing().whenever(repo).deleteAll()
        repo.deleteAllConfluenceTools()
        verify(repo).deleteAll()
    }

    @Test
    fun `search session repository recent list and exists`() {
        val repo: SearchSessionRepository = realDefaults()
        val session = mock<SearchSession>()
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(session))
        assertEquals(listOf(session), repo.findRecentSearchSessions(5))
        whenever(repo.existsById(1L)).thenReturn(true)
        assertTrue(repo.sessionExistsById(1L))
        whenever(repo.save(session)).thenReturn(session)
        assertEquals(session, repo.saveSession(session))
        doNothing().whenever(repo).deleteById(any())
        repo.deleteSessionById(1L)
        verify(repo).deleteById(1L)
    }

    @Test
    fun `search result repository capped per session groups by source type`() {
        val repo: SearchResultRepository = realDefaults()
        whenever(repo.findCappedBySessionIdAndSourceType(any(), any(), any())).thenReturn(listOf(mock()))
        val result = repo.findCappedBySessionId(7L, 10)
        assertEquals(SourceType.entries.size, result.size)
        verify(repo, org.mockito.kotlin.times(SourceType.entries.size)).findCappedBySessionIdAndSourceType(any(), any(), any())
    }

    @Test
    fun `knowledge embedding config repository get existing and missing`() {
        val repo: KnowledgeEmbeddingConfigRepository = realDefaults()
        val config = mock<KnowledgeEmbeddingConfig>()
        org.mockito.kotlin
            .doReturn(java.util.Optional.of(config))
            .whenever(repo)
            .findById(1L)
        assertEquals(config, repo.get())
        org.mockito.kotlin
            .doReturn(java.util.Optional.empty<KnowledgeEmbeddingConfig>())
            .whenever(repo)
            .findById(1L)
        val fallback = repo.get()
        assertNotSame(config, fallback)
        assertEquals("nomic-embed-text", fallback.model)
        doNothing().whenever(repo).deleteById(1L)
        repo.delete()
        verify(repo).deleteById(1L)
    }

    @Test
    fun `knowledge chunk repository sums content length per source`() {
        val repo: KnowledgeChunkRepository = realDefaults()
        assertEquals(emptyMap<Long, Long>(), repo.sumContentLengthBySourceIds(emptyList()))
        val rowWithNull = arrayOfNulls<Any>(2)
        rowWithNull[0] = 6L
        @Suppress("UNCHECKED_CAST")
        val nullRow = rowWithNull as Array<Any>
        org.mockito.kotlin
            .doReturn(listOf(arrayOf<Any>(5L, 42L), nullRow, arrayOf<Any>(7, 8)))
            .whenever(repo)
            .aggregateContentLengthBySourceIdsRaw(any())
        val result = repo.sumContentLengthBySourceIds(listOf(5L, 6L, 7L))
        assertEquals(42L, result[5L])
        assertEquals(0L, result[6L])
        assertEquals(8L, result[7L])
    }

    @Test
    fun `local repo config finds first matching path and scope`() {
        val repo: LocalRepoConfigRepository = realDefaults()
        val matching = LocalRepoConfig(name = "m", path = "/a", scope = "user")
        val nonMatchingScope = LocalRepoConfig(name = "s", path = "/a", scope = "other")
        val nonMatchingPath = LocalRepoConfig(name = "p", path = "/b", scope = "user")
        whenever(repo.findAll()).thenReturn(emptyList())
        assertNull(repo.findFirstByPathAndScope("/a", "user"))
        whenever(repo.findAll()).thenReturn(listOf(nonMatchingPath, matching, nonMatchingScope))
        assertEquals(matching, repo.findFirstByPathAndScope("/a", "user"))
        assertNull(repo.findFirstByPathAndScope("/zzz", "user"))
    }

    @Test
    fun `sprint review repository save handles optimistic locking`() {
        val repo: SprintReviewRepository = realDefaults()
        val entity = mock<SprintReview>()
        whenever(repo.save(entity)).thenReturn(entity)
        assertEquals(entity, repo.saveSprintReview(entity))
        val conflict = mock<ObjectOptimisticLockingFailureException>()
        whenever(conflict.identifier).thenReturn("99")
        whenever(repo.save(entity)).thenThrow(conflict)
        assertThrows(com.stacklens.domain.EntityConcurrentlyModifiedException::class.java) {
            repo.saveSprintReview(entity)
        }
        whenever(repo.findAll(any<Pageable>())).thenReturn(pageOf(entity))
        assertEquals(listOf(entity), repo.findRecentSprintReviews(5))
        doNothing().whenever(repo).deleteById(any())
        repo.deleteSprintReviewById(1L)
        verify(repo).deleteById(1L)
        doNothing().whenever(repo).deleteAll()
        repo.deleteAllSprintReviews()
        verify(repo).deleteAll()
    }
}
