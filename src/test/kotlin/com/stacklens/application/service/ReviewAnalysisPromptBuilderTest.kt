package com.stacklens.application.service

import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ReviewAnalysisPromptBuilderTest {
    private val promptLoader: PromptReaderPort = mock()
    private val builder = ReviewAnalysisPromptBuilder(promptLoader, clock = com.stacklens.adapter.out.time.SystemClockPort)

    private fun stubI18n() {
        whenever(promptLoader.context(any<String>())).thenAnswer {
            PromptContext(emptyMap())
        }
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenAnswer {
            "[${it.arguments[1]}]"
        }
    }

    private fun sprintInfo(
        name: String = "Sprint 42",
        state: String = "active",
        startDate: String? = "2025-01-01",
        endDate: String? = "2025-01-14",
        goal: String? = "Deliver MVP",
    ): SprintInfo =
        SprintInfo(
            id = 1L,
            name = name,
            state = state,
            startDate = startDate,
            endDate = endDate,
            goal = goal,
        )

    private fun issue(
        key: String = "PROJ-1",
        summary: String = "Test issue",
        type: String = "Story",
        status: String = "Open",
        priority: String? = "High",
        assignee: String? = "alice",
        createdAt: String? = "2025-01-02",
        startedAt: String? = "2025-01-03",
        resolvedAt: String? = null,
        qaLoopCount: Int = 0,
        labels: List<String> = listOf("backend"),
        description: String = "Test description",
        url: String = "https://example.com/PROJ-1",
        comments: String = "",
    ): SprintIssue =
        SprintIssue(
            key = key,
            summary = summary,
            issueType = type,
            status = status,
            priority = priority,
            assignee = assignee,
            createdAt = createdAt,
            startedAt = startedAt,
            resolvedAt = resolvedAt,
            qaLoopCount = qaLoopCount,
            labels = labels,
            description = description,
            url = url,
            comments = comments,
        )

    @Test
    fun `buildPrompt includes analysis instructions and separator`() {
        stubI18n()
        val prompt =
            builder.buildPrompt(
                lang = "en",
                sprintInfo = sprintInfo(),
                issues = listOf(issue()),
                analysisInstructions = "Please review this sprint.",
            )
        assertTrue(prompt.contains("Please review this sprint."))
        assertTrue(prompt.contains("---"))
    }

    @Test
    fun `buildPrompt includes dashboard instruction`() {
        stubI18n()
        val prompt =
            builder.buildPrompt(
                lang = "en",
                sprintInfo = sprintInfo(),
                issues = emptyList(),
                analysisInstructions = "Analyze.",
            )
        assertTrue(prompt.contains("[review.dashboardInstruction]"))
        assertTrue(prompt.contains("[review.dashboardLangDirective]"))
    }

    @Test
    fun `buildAgentInput includes sprint block`() {
        stubI18n()
        val input = builder.buildAgentInput("en", sprintInfo(), emptyList())
        assertTrue(input.contains("Sprint 42"))
        assertTrue(input.contains("active"))
        assertTrue(input.contains("2025-01-01"))
        assertTrue(input.contains("2025-01-14"))
        assertTrue(input.contains("Deliver MVP"))
    }

    @Test
    fun `buildAgentInput handles null sprint goal`() {
        stubI18n()
        val input =
            builder.buildAgentInput(
                lang = "en",
                sprintInfo = sprintInfo(goal = null),
                issues = emptyList(),
            )
        assertTrue(input.contains("review.agentSprintNoGoal"))
    }

    @Test
    fun `buildAgentInput handles null sprint dates`() {
        stubI18n()
        val input =
            builder.buildAgentInput(
                lang = "en",
                sprintInfo = sprintInfo(startDate = null, endDate = null),
                issues = emptyList(),
            )
        assertTrue(input.contains("review.agentSprintPeriodLabel"))
    }

    @Test
    fun `buildAgentInput includes tickets block when issues present`() {
        stubI18n()
        val input =
            builder.buildAgentInput(
                lang = "en",
                sprintInfo = sprintInfo(),
                issues =
                    listOf(
                        issue(key = "PROJ-1", summary = "First issue"),
                        issue(key = "PROJ-2", summary = "Second issue"),
                    ),
            )
        assertTrue(input.contains("PROJ-1"))
        assertTrue(input.contains("PROJ-2"))
        assertTrue(input.contains("First issue"))
        assertTrue(input.contains("Second issue"))
    }

    @Test
    fun `buildAgentInput handles empty issues list`() {
        stubI18n()
        val input = builder.buildAgentInput("en", sprintInfo(), emptyList())
        assertTrue(input.contains("review.agentTicketsHeader"))
        assertTrue(!input.contains("PROJ-"))
    }

    @Test
    fun `buildAgentInput truncates long descriptions to 300 chars`() {
        stubI18n()
        val longDesc = "x".repeat(500)
        val input =
            builder.buildAgentInput(
                lang = "en",
                sprintInfo = sprintInfo(),
                issues = listOf(issue(description = longDesc)),
            )
        assertTrue(!input.contains(longDesc))
        assertTrue(input.contains("xxxxxx"))
    }

    @Test
    fun `buildAgentInput includes labels when present`() {
        stubI18n()
        val input =
            builder.buildAgentInput(
                lang = "en",
                sprintInfo = sprintInfo(),
                issues = listOf(issue(labels = listOf("frontend", "urgent"))),
            )
        assertTrue(input.contains("frontend"))
        assertTrue(input.contains("urgent"))
    }

    @Test
    fun `buildAgentInput handles issue with empty key`() {
        stubI18n()
        val input =
            builder.buildAgentInput(
                lang = "en",
                sprintInfo = sprintInfo(),
                issues = listOf(issue(key = "")),
            )
        assertTrue(input.contains("review.agentFieldNoKey"))
    }

    @Test
    fun `buildAgentInput handles issue with empty summary`() {
        stubI18n()
        val input =
            builder.buildAgentInput(
                lang = "en",
                sprintInfo = sprintInfo(),
                issues = listOf(issue(summary = "")),
            )
        assertTrue(input.contains("review.agentFieldNoSummary"))
    }

    @Test
    fun `buildAgentInput handles issue with null priority`() {
        stubI18n()
        val input =
            builder.buildAgentInput(
                lang = "en",
                sprintInfo = sprintInfo(),
                issues = listOf(issue(priority = null)),
            )
        assertTrue(input.contains("review.agentFieldNoPriority"))
    }

    @Test
    fun `buildAgentInput handles issue with null assignee`() {
        stubI18n()
        val input =
            builder.buildAgentInput(
                lang = "en",
                sprintInfo = sprintInfo(),
                issues = listOf(issue(assignee = null)),
            )
        assertTrue(input.contains("review.agentFieldNoAssignee"))
    }

    @Test
    fun `buildAgentInput skips description when blank`() {
        stubI18n()
        val input =
            builder.buildAgentInput(
                lang = "en",
                sprintInfo = sprintInfo(),
                issues = listOf(issue(description = "")),
            )
        assertTrue(!input.contains("review.agentFieldDescription"))
    }

    @Test
    fun `buildAgentInput includes comments for Story issues`() {
        stubI18n()
        val issues =
            listOf(
                issue(type = "Story", comments = "Some story comment"),
            )
        val input = builder.buildAgentInput("en", sprintInfo(), issues)
        assertTrue(input.contains("Some story comment"))
    }

    @Test
    fun `buildAgentInput includes comments for Bug issues`() {
        stubI18n()
        val issues =
            listOf(
                issue(type = "Bug", comments = "Some bug comment"),
            )
        val input = builder.buildAgentInput("en", sprintInfo(), issues)
        assertTrue(input.contains("Some bug comment"))
    }

    @Test
    fun `buildAgentInput returns no-comments message when no Story or Bug with comments`() {
        stubI18n()
        val issues =
            listOf(
                issue(type = "Task", comments = "some comment"),
            )
        val input = builder.buildAgentInput("en", sprintInfo(), issues)
        assertTrue(input.contains("review.agentNoComments"))
    }

    @Test
    fun `buildAgentInput includes fetchedAt timestamp`() {
        stubI18n()
        val input = builder.buildAgentInput("en", sprintInfo(), emptyList())
        assertTrue(input.contains("review.agentFetchedAt"))
    }

    @Test
    fun `buildAgentInput includes total tickets count`() {
        stubI18n()
        val issues = listOf(issue(), issue(key = "PROJ-2"))
        val input = builder.buildAgentInput("en", sprintInfo(), issues)
        assertTrue(input.contains("review.agentTotalTickets"))
    }
}
