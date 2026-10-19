package com.stacklens.adapter.out.persistence

import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.SprintReview
import org.junit.jupiter.api.Assertions.assertEquals
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

@DataJpaTest
class SprintReviewRepositoryTest {
    @Autowired private lateinit var sprintReviewRepo: SprintReviewRepository

    @Test
    fun `saveSprintReview delegates to save and returns entity with id`() {
        val review = SprintReview(sprintName = "Sprint 42", targetTicketKey = "PROJ-100")
        val saved = sprintReviewRepo.saveSprintReview(review)
        assertNotNull(saved.id)
    }

    @Test
    fun `findSprintReviewById returns saved entity`() {
        val review = SprintReview(sprintName = "Sprint 43", targetTicketKey = "PROJ-101")
        val saved = sprintReviewRepo.saveSprintReview(review)
        val found = sprintReviewRepo.findSprintReviewById(saved.id)
        assertNotNull(found)
        assertEquals("Sprint 43", found!!.sprintName)
    }

    @Test
    fun `findSprintReviewById returns null for non-existent id`() {
        val found = sprintReviewRepo.findSprintReviewById(999_999L)
        assertTrue(found == null)
    }

    @Test
    fun `findAllByOrderByCreatedAtDesc returns ordered list`() {
        sprintReviewRepo.saveSprintReview(SprintReview(sprintName = "First", targetTicketKey = "T1"))
        sprintReviewRepo.saveSprintReview(SprintReview(sprintName = "Second", targetTicketKey = "T2"))
        val all = sprintReviewRepo.findAllByOrderByCreatedAtDesc()
        assertTrue(all.size >= 2)
    }

    @Test
    fun `deleteSprintReviewById delegates to deleteById`() {
        val review = SprintReview(sprintName = "ToDelete", targetTicketKey = "T3")
        val saved = sprintReviewRepo.saveSprintReview(review)
        sprintReviewRepo.deleteSprintReviewById(saved.id)
        assertTrue(sprintReviewRepo.findById(saved.id).isEmpty)
    }

    @Test
    fun `deleteAllSprintReviews delegates to deleteAll`() {
        sprintReviewRepo.saveSprintReview(SprintReview(sprintName = "A", targetTicketKey = "T4"))
        sprintReviewRepo.saveSprintReview(SprintReview(sprintName = "B", targetTicketKey = "T5"))
        sprintReviewRepo.deleteAllSprintReviews()
        assertTrue(sprintReviewRepo.findAll().isEmpty())
    }

    @Test
    fun `saveSprintReview catches ObjectOptimisticLockingFailureException`() {
        val repo: SprintReviewRepository = mock(defaultAnswer = Answers.CALLS_REAL_METHODS)
        doThrow(ObjectOptimisticLockingFailureException(SprintReview::class.java, 1L))
            .whenever(repo)
            .save(any<SprintReview>())
        val entity = SprintReview(sprintName = "Sprint X", targetTicketKey = "PROJ-999")
        assertThrows(EntityConcurrentlyModifiedException::class.java) { repo.saveSprintReview(entity) }
    }

    @Test
    fun `saveSprintReview exception message contains entity id`() {
        val repo: SprintReviewRepository = mock(defaultAnswer = Answers.CALLS_REAL_METHODS)
        doThrow(ObjectOptimisticLockingFailureException(SprintReview::class.java, 42L))
            .whenever(repo)
            .save(any<SprintReview>())
        val entity = SprintReview(sprintName = "Sprint Y", targetTicketKey = "PROJ-888")
        val exception = assertThrows(EntityConcurrentlyModifiedException::class.java) { repo.saveSprintReview(entity) }
        assertTrue(exception.message!!.contains("42"))
    }

    @Test
    fun `saveSprintReview exception message contains SprintReview class name`() {
        val repo: SprintReviewRepository = mock(defaultAnswer = Answers.CALLS_REAL_METHODS)
        doThrow(ObjectOptimisticLockingFailureException(SprintReview::class.java, 1L))
            .whenever(repo)
            .save(any<SprintReview>())
        val entity = SprintReview(sprintName = "Sprint Z", targetTicketKey = "PROJ-777")
        val exception = assertThrows(EntityConcurrentlyModifiedException::class.java) { repo.saveSprintReview(entity) }
        assertTrue(exception.message!!.contains("SprintReview"))
    }

    @Test
    fun `saveSprintReview falls back to entity id when identifier is null`() {
        val repo: SprintReviewRepository = mock(defaultAnswer = Answers.CALLS_REAL_METHODS)
        val originalEx = ObjectOptimisticLockingFailureException(SprintReview::class.java, 99L)
        val ex = org.mockito.Mockito.spy(originalEx)
        org.mockito.Mockito
            .doReturn(null)
            .`when`(ex)
            .identifier
        doThrow(ex).whenever(repo).save(any<SprintReview>())
        val entity = SprintReview(id = 99L, sprintName = "Sprint", targetTicketKey = "T1")
        val exception = assertThrows(EntityConcurrentlyModifiedException::class.java) { repo.saveSprintReview(entity) }
        assertTrue(exception.message!!.contains("99"))
    }

    @Test
    fun `saveSprintReview with all fields populated`() {
        val review =
            SprintReview(
                sprintName = "Full Sprint",
                targetTicketKey = "PROJ-100",
                jiraConfigId = 5L,
                templateOverride = "custom template",
                includeImprovement = false,
                status = "COMPLETED",
                currentStep = "Done",
                errorMessage = null,
                sprintId = 123L,
                sprintState = "closed",
                totalIssues = 42,
                targetTicketUrl = "https://jira/browse/PROJ-100",
                sprintUrl = "https://jira/secure/SprintBoard.jspa?selectedIssue=PROJ-100",
                rowsJson = "[{\"key\":\"T1\",\"state\":\"BEARBEITET\"}]",
                tableAdfJson = "{\"type\":\"table\"}",
            )
        val saved = sprintReviewRepo.saveSprintReview(review)
        assertNotNull(saved.id)
        assertEquals("Full Sprint", saved.sprintName)
        assertEquals(false, saved.includeImprovement)
        assertEquals("COMPLETED", saved.status)
    }

    @Test
    fun `saveSprintReview with minimal fields`() {
        val review = SprintReview(sprintName = "Minimal", targetTicketKey = "T1")
        val saved = sprintReviewRepo.saveSprintReview(review)
        assertNotNull(saved.id)
        assertEquals("PENDING", saved.status)
    }

    @Test
    fun `findSprintReviewById with non-existent id returns null`() {
        val result = sprintReviewRepo.findSprintReviewById(-1L)
        assertTrue(result == null)
    }

    @Test
    fun `findAllByOrderByCreatedAtDesc returns empty when no reviews`() {
        sprintReviewRepo.deleteAllSprintReviews()
        assertTrue(sprintReviewRepo.findAllByOrderByCreatedAtDesc().isEmpty())
    }

    @Test
    fun `deleteSprintReviewById on non-existent id does not throw`() {
        sprintReviewRepo.deleteSprintReviewById(999_999L)
    }

    @Test
    fun `deleteAllSprintReviews on empty table does not throw`() {
        sprintReviewRepo.deleteAllSprintReviews()
    }
}
