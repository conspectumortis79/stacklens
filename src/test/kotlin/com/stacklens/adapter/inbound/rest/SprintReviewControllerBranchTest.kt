package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.SprintReviewUseCase
import com.stacklens.domain.model.SprintReview
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class SprintReviewControllerBranchTest {
    private val useCase: SprintReviewUseCase = mock()
    private val i18n = FakeI18n()
    private val controller = SprintReviewController(useCase, i18n)
    private val configId = argumentCaptor<Long>()
    private val template = argumentCaptor<String>()
    private val improvement = argumentCaptor<Boolean>()

    private fun stubStartAccepted() {
        whenever(useCase.firstActiveJiraConfigId()).thenReturn(ACTIVE_CONFIG_ID)
        whenever(useCase.jiraConfigExists(anyLong())) doReturn true
        whenever(
            useCase.startReview(
                anyString(),
                anyString(),
                nullable(Long::class.java),
                nullable(String::class.java),
                anyBoolean(),
            ),
        ) doReturn SprintReview(id = 3L, sprintName = "Sprint 3", targetTicketKey = "P-3")
    }

    private fun startedArguments(): Triple<Long?, String?, Boolean> {
        verify(useCase).startReview(anyString(), anyString(), configId.capture(), template.capture(), improvement.capture())
        return Triple(configId.lastValue, template.lastValue, improvement.lastValue)
    }

    @Test
    fun `a translated message is used instead of the raw key`() {
        val response = controller.start(mapOf("sprintName" to " ", "targetTicketKey" to "P-1"))

        assertEquals("i18n:sprint_review.be_sprint_name_required", response.body!!["error"])
    }

    @Test
    fun `an absent sprint name is rejected as blank`() {
        val response = controller.start(mapOf("targetTicketKey" to "P-1"))

        assertEquals("i18n:sprint_review.be_sprint_name_required", response.body!!["error"])
    }

    @Test
    fun `a sprint name of the wrong type is rejected as blank`() {
        val response = controller.start(mapOf("sprintName" to 42, "targetTicketKey" to "P-1"))

        assertEquals("i18n:sprint_review.be_sprint_name_required", response.body!!["error"])
    }

    @Test
    fun `an absent target ticket key is rejected as blank`() {
        val response = controller.start(mapOf("sprintName" to "Sprint 1"))

        assertEquals("i18n:sprint_review.be_target_ticket_key_required", response.body!!["error"])
    }

    @Test
    fun `a target ticket key of the wrong type is rejected as blank`() {
        val response = controller.start(mapOf("sprintName" to "Sprint 1", "targetTicketKey" to listOf("P-1")))

        assertEquals("i18n:sprint_review.be_target_ticket_key_required", response.body!!["error"])
    }

    @Test
    fun `a jira config id of the wrong type falls back to the first active config`() {
        stubStartAccepted()

        controller.start(mapOf("sprintName" to "S", "targetTicketKey" to "P", "jiraConfigId" to true))

        assertEquals(ACTIVE_CONFIG_ID, startedArguments().first)
    }

    @Test
    fun `an unparsable jira config id string falls back to the first active config`() {
        stubStartAccepted()

        controller.start(mapOf("sprintName" to "S", "targetTicketKey" to "P", "jiraConfigId" to "  n/a "))

        assertEquals(ACTIVE_CONFIG_ID, startedArguments().first)
    }

    @Test
    fun `a blank template override is stored as null`() {
        stubStartAccepted()

        controller.start(mapOf("sprintName" to "S", "targetTicketKey" to "P", "templateOverride" to "   "))

        assertNull(startedArguments().second)
    }

    @Test
    fun `a template override of the wrong type is stored as null`() {
        stubStartAccepted()

        controller.start(mapOf("sprintName" to "S", "targetTicketKey" to "P", "templateOverride" to 12))

        assertNull(startedArguments().second)
    }

    @Test
    fun `a missing template override is stored as null`() {
        stubStartAccepted()

        controller.start(mapOf("sprintName" to "S", "targetTicketKey" to "P"))

        assertNull(startedArguments().second)
    }

    @Test
    fun `a template override is passed through untouched`() {
        stubStartAccepted()

        controller.start(mapOf("sprintName" to "S", "targetTicketKey" to "P", "templateOverride" to "custom"))

        assertEquals("custom", startedArguments().second)
    }

    @Test
    fun `an improvement flag of the wrong type keeps the default`() {
        stubStartAccepted()

        controller.start(mapOf("sprintName" to "S", "targetTicketKey" to "P", "includeImprovement" to 1))

        assertEquals(true, startedArguments().third)
    }

    @Test
    fun `an upper case improvement flag enables the improvement section`() {
        stubStartAccepted()

        controller.start(mapOf("sprintName" to "S", "targetTicketKey" to "P", "includeImprovement" to "TRUE"))

        assertEquals(true, startedArguments().third)
    }

    @Test
    fun `a false improvement flag disables the improvement section`() {
        stubStartAccepted()

        controller.start(mapOf("sprintName" to "S", "targetTicketKey" to "P", "includeImprovement" to false))

        assertEquals(false, startedArguments().third)
    }

    @Test
    fun `an unfinished review is listed without a completion timestamp`() {
        whenever(useCase.getAllReviews()) doReturn listOf(SprintReview(id = 3L, sprintName = "S", targetTicketKey = "P", completedAt = null))

        val row = controller.list().single()

        assertEquals(3L, row["id"])
        assertNull(row["completedAt"])
    }

    @Test
    fun `a finished review is listed with its completion timestamp`() {
        val finished = LocalDateTime.of(2024, 3, 4, 5, 6)
        whenever(useCase.getAllReviews()) doReturn listOf(SprintReview(id = 3L, sprintName = "S", targetTicketKey = "P", completedAt = finished))

        assertEquals(finished.toString(), controller.list().single()["completedAt"])
    }

    @Test
    fun `an argument failure without a message gets a generic description`() {
        whenever(useCase.postAdfToJira(1L)).thenThrow(IllegalArgumentException())

        assertEquals("invalid request", controller.postToJira(1L).body!!["error"])
    }

    @Test
    fun `a state failure without a message gets a generic description`() {
        whenever(useCase.postAdfToJira(1L)).thenThrow(IllegalStateException())

        assertEquals("invalid state", controller.postToJira(1L).body!!["error"])
    }

    @Test
    fun `an unexpected failure without a message gets a generic description`() {
        whenever(useCase.postAdfToJira(1L)).thenThrow(RuntimeException())

        val response = controller.postToJira(1L)

        assertEquals(500, response.statusCode.value())
        assertEquals("unknown", response.body!!["error"])
    }

    companion object {
        private const val ACTIVE_CONFIG_ID = 7L
    }
}
