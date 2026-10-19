package com.stacklens.adapter.out.persistence

import com.stacklens.domain.EntityConcurrentlyDeletedException
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.domain.model.TicketRefinement
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.orm.ObjectOptimisticLockingFailureException

class RepositoryExceptionCoverageTest {
    @Test
    fun `saveTicketRefinement catches ObjectOptimisticLockingFailureException`() {
        val repo: TicketRefinementRepository = mock(defaultAnswer = Answers.CALLS_REAL_METHODS)
        doThrow(ObjectOptimisticLockingFailureException(TicketRefinement::class.java, 1L))
            .whenever(repo)
            .save(any<TicketRefinement>())
        val entity = TicketRefinement(parentTicketKey = "P-1")
        assertThrows(EntityConcurrentlyModifiedException::class.java) {
            repo.saveTicketRefinement(entity)
        }
    }

    @Test
    fun `saveConfluenceTool catches ObjectOptimisticLockingFailureException`() {
        val repo: ConfluenceToolRepository = mock(defaultAnswer = Answers.CALLS_REAL_METHODS)
        doThrow(ObjectOptimisticLockingFailureException(ConfluenceTool::class.java, 1L))
            .whenever(repo)
            .save(any<ConfluenceTool>())
        val tool = ConfluenceTool(name = "t", confluencePageUrl = "https://page")
        assertThrows(EntityConcurrentlyDeletedException::class.java) {
            repo.saveConfluenceTool(tool)
        }
    }

    @Test
    fun `saveJiraTicketSummary catches ObjectOptimisticLockingFailureException`() {
        val repo: JiraTicketSummaryRepository = mock(defaultAnswer = Answers.CALLS_REAL_METHODS)
        doThrow(ObjectOptimisticLockingFailureException(JiraTicketSummary::class.java, 1L))
            .whenever(repo)
            .save(any<JiraTicketSummary>())
        val entity = JiraTicketSummary(ticketKey = "J-1")
        assertThrows(EntityConcurrentlyModifiedException::class.java) {
            repo.saveJiraTicketSummary(entity)
        }
    }

    @Test
    fun `saveSecurityAnalysis catches ObjectOptimisticLockingFailureException`() {
        val repo: SecurityAnalysisRepository = mock(defaultAnswer = Answers.CALLS_REAL_METHODS)
        doThrow(ObjectOptimisticLockingFailureException(SecurityAnalysis::class.java, 1L))
            .whenever(repo)
            .save(any<SecurityAnalysis>())
        val analysis = SecurityAnalysis(id = 1L, totalIterations = 1)
        assertThrows(EntityConcurrentlyModifiedException::class.java) {
            repo.saveSecurityAnalysis(analysis)
        }
    }
}

@DataJpaTest
class RepositoryHappyPathCoverageTest {
    @Autowired private lateinit var jiraTicketSummaryRepo: JiraTicketSummaryRepository

    @Autowired private lateinit var localRepoConfigRepo: LocalRepoConfigRepository

    @Test
    fun `saveJiraTicketSummary delegates to save`() {
        val saved = jiraTicketSummaryRepo.saveJiraTicketSummary(JiraTicketSummary(ticketKey = "J-2"))
        assertNotNull(saved.id)
    }

    @Test
    fun `deleteJiraTicketSummaryById delegates to deleteById`() {
        val saved = jiraTicketSummaryRepo.saveJiraTicketSummary(JiraTicketSummary(ticketKey = "J-3"))
        jiraTicketSummaryRepo.deleteJiraTicketSummaryById(saved.id)
        assertTrue(jiraTicketSummaryRepo.findById(saved.id).isEmpty)
    }

    @Test
    fun `deleteAllJiraTicketSummaries delegates to deleteAll`() {
        jiraTicketSummaryRepo.saveJiraTicketSummary(JiraTicketSummary(ticketKey = "J-4"))
        jiraTicketSummaryRepo.deleteAllJiraTicketSummaries()
        assertTrue(jiraTicketSummaryRepo.findAll().isEmpty())
    }

    @Test
    fun `deleteLocalRepoConfigById delegates to deleteById`() {
        val saved = localRepoConfigRepo.saveLocalRepoConfig(LocalRepoConfig(name = "l", path = "/tmp"))
        localRepoConfigRepo.deleteLocalRepoConfigById(saved.id)
        assertTrue(localRepoConfigRepo.findById(saved.id).isEmpty)
    }
}
