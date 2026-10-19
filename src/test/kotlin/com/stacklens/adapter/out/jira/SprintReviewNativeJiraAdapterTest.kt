package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ResolvedSprint
import com.stacklens.application.port.out.SprintReviewAdfDocument
import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SprintReviewRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SprintReviewNativeJiraAdapterTest {
    private val resolver: SprintResolver = mock()
    private val issueLoader: SprintIssueLoader = mock()
    private val adfUpdater: AdfDescriptionUpdater = mock()
    private val tableBuilder: SprintReviewTableBuilder = mock()
    private val mapper = ObjectMapper()

    private fun adapter() =
        SprintReviewNativeJiraAdapter(
            sprintResolver = resolver,
            sprintIssueLoader = issueLoader,
            adfDescriptionUpdater = adfUpdater,
            tableBuilder = tableBuilder,
            objectMapper = mapper,
        )

    private val cloudConfig =
        JiraConfig(
            id = 7L,
            name = "banana",
            baseUrl = "https://banana.atlassian.net",
            apiToken = "tok",
            cloudMode = true,
        )

    @Test
    fun `resolveSprintByName delegates to the SprintResolver`() {
        val expected =
            ResolvedSprint(
                id = 42L,
                name = "Sprint 42",
                state = "active",
                boardId = 1L,
                boardName = null,
                url = "https://banana.atlassian.net/backlog",
            )
        whenever(resolver.resolve(cloudConfig, "Sprint 42")).thenReturn(expected)

        val result = adapter().resolveSprintByName(cloudConfig, "Sprint 42")
        assertEquals(expected, result)
        verify(resolver).resolve(cloudConfig, "Sprint 42")
    }

    @Test
    fun `resolveSprintByName throws when config is null`() {
        assertThrows(IllegalArgumentException::class.java) {
            adapter().resolveSprintByName(null, "Sprint 42")
        }
    }

    @Test
    fun `resolveSprintByName rethrows the resolver's IllegalArgumentException`() {
        whenever(resolver.resolve(eq(cloudConfig), any()))
            .thenThrow(IllegalArgumentException("Sprint not found"))

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                adapter().resolveSprintByName(cloudConfig, "Sprint 42")
            }
        assertNotNull(ex.message)
    }

    @Test
    fun `fetchSprintTickets delegates to the SprintIssueLoader`() {
        val expected =
            listOf(
                SprintReviewTicket(
                    key = "PROJ-1",
                    summary = "Story",
                    description = "",
                    url = "https://banana.atlassian.net/browse/PROJ-1",
                    issueType = "Story",
                    status = "Open",
                ),
            )
        whenever(issueLoader.fetch(cloudConfig, 42L)).thenReturn(expected)

        val result = adapter().fetchSprintTickets(cloudConfig, 42L)
        assertEquals(expected, result)
        verify(issueLoader).fetch(cloudConfig, 42L)
    }

    @Test
    fun `fetchSprintTickets throws when config is null`() {
        assertThrows(IllegalArgumentException::class.java) {
            adapter().fetchSprintTickets(null, 42L)
        }
    }

    @Test
    fun `renderAdfDocument serializes the built ADF document`() {
        val rows = emptyList<SprintReviewRow>()
        whenever(tableBuilder.buildAdfDocument(rows, true, "Sprint 1")).thenReturn(mapOf("type" to "doc"))

        val document = adapter().renderAdfDocument(rows, true, "Sprint 1")

        assertEquals("""{"type":"doc"}""", document.json)
        verify(tableBuilder).buildAdfDocument(rows, true, "Sprint 1")
    }

    @Test
    fun `appendAdfToDescription delegates to the AdfDescriptionUpdater`() {
        val adfDoc = mapOf("type" to "doc", "version" to 1, "content" to emptyList<Map<String, Any?>>())
        val document = SprintReviewAdfDocument(mapper.writeValueAsString(adfDoc))
        whenever(adfUpdater.append(cloudConfig, "PROJ-1", adfDoc)).thenReturn("merged")

        val result = adapter().appendAdfToDescription(cloudConfig, "PROJ-1", document)
        assertEquals("merged", result)
        verify(adfUpdater).append(cloudConfig, "PROJ-1", adfDoc)
    }

    @Test
    fun `appendAdfToDescription throws when config is null`() {
        assertThrows(IllegalArgumentException::class.java) {
            adapter().appendAdfToDescription(null, "PROJ-1", SprintReviewAdfDocument("{}"))
        }
    }
}
