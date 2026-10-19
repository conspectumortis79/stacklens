package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TicketRefinementCoverageTest {
    @Test
    fun `TicketRefinement defaults are sensible`() {
        val t = TicketRefinement(parentTicketKey = "PROJ-1")
        assertEquals(0L, t.id)
        assertEquals("PROJ-1", t.parentTicketKey)
        assertNull(t.jiraConfigId)
        assertNull(t.templateOverride)
        assertTrue(t.singleTicketOnly)
        assertEquals("PENDING", t.status)
        assertNull(t.currentStep)
        assertNull(t.errorMessage)
        assertNull(t.parentTicketJson)
        assertNull(t.reviewsJson)
        assertNull(t.repoSelectionsJson)
        assertNull(t.databaseConfigId)
        assertNull(t.databaseConfigIds)
        assertNull(t.subtasksCreatedJson)
        assertNotNull(t.createdAt)
        assertNull(t.completedAt)
    }

    @Test
    fun `TicketRefinement stores provided values verbatim`() {
        val now = LocalDateTime.of(2025, 1, 1, 0, 0)
        val done = LocalDateTime.of(2025, 1, 2, 0, 0)
        val t =
            TicketRefinement(
                id = 9L,
                parentTicketKey = "PROJ-9",
                jiraConfigId = 3L,
                templateOverride = "tmpl",
                singleTicketOnly = false,
                status = "DONE",
                currentStep = "step",
                errorMessage = "err",
                parentTicketJson = "{\"k\":1}",
                reviewsJson = "[]",
                repoSelectionsJson = "[{\"type\":\"local\"}]",
                databaseConfigId = 1L,
                databaseConfigIds = "1,2",
                subtasksCreatedJson = "{\"PROJ-1\":[\"a\"]}",
                createdAt = now,
                completedAt = done,
            )
        assertEquals(9L, t.id)
        assertEquals("PROJ-9", t.parentTicketKey)
        assertEquals(3L, t.jiraConfigId)
        assertEquals("tmpl", t.templateOverride)
        assertFalse(t.singleTicketOnly)
        assertEquals("DONE", t.status)
        assertEquals("step", t.currentStep)
        assertEquals("err", t.errorMessage)
        assertEquals("{\"k\":1}", t.parentTicketJson)
        assertEquals("[]", t.reviewsJson)
        assertEquals("[{\"type\":\"local\"}]", t.repoSelectionsJson)
        assertEquals(1L, t.databaseConfigId)
        assertEquals("1,2", t.databaseConfigIds)
        assertEquals("{\"PROJ-1\":[\"a\"]}", t.subtasksCreatedJson)
        assertEquals(now, t.createdAt)
        assertEquals(done, t.completedAt)
    }

    @Test
    fun `TicketReview defaults are sensible`() {
        val r =
            TicketReview(
                ticketKey = "PROJ-1",
                ticketUrl = "u",
                originalSummary = "os",
                originalDescription = "od",
                improvedDescription = "id",
                openPoints = listOf("a"),
                state = "OK",
            )
        assertNull(r.errorMessage)
        assertFalse(r.commentPosted)
        assertNull(r.originalDescriptionHtml)
        assertNull(r.improvedDescriptionHtml)
        assertTrue(r.repoAnalyses.isEmpty())
        assertEquals("", r.improvedNative)
        assertEquals("markdown", r.improvedFormat)
        assertEquals("", r.improvedSummary)
        assertTrue(r.suggestedSubtasks.isEmpty())
    }

    @Test
    fun `TicketReview stores provided values verbatim`() {
        val ra =
            RepoAnalysis(
                repoRef = "local:1",
                repoName = "r",
                repoType = "local",
                relevant = true,
                summary = "s",
                suggestedChanges = listOf("x"),
                dependencies = listOf("y"),
                hiddenDependencies = listOf("z"),
                complexity = "M",
                state = "OK",
                errorMessage = null,
            )
        val sub =
            SubtaskSuggestion(
                summary = "sub",
                description = "d",
                issueType = "Subtask",
                priority = "Minor",
                labels = listOf("l"),
                existingKey = "PROJ-9",
            )
        val r =
            TicketReview(
                ticketKey = "PROJ-1",
                ticketUrl = "u",
                originalSummary = "os",
                originalDescription = "od",
                improvedDescription = "id",
                openPoints = listOf("a"),
                state = "OK",
                errorMessage = "err",
                commentPosted = true,
                originalDescriptionHtml = "<p>od</p>",
                improvedDescriptionHtml = "<p>id</p>",
                repoAnalyses = listOf(ra),
                improvedNative = "{\"native\":\"x\"}",
                improvedFormat = "adf",
                improvedSummary = "impr",
                suggestedSubtasks = listOf(sub),
            )
        assertEquals("err", r.errorMessage)
        assertTrue(r.commentPosted)
        assertEquals("<p>od</p>", r.originalDescriptionHtml)
        assertEquals("<p>id</p>", r.improvedDescriptionHtml)
        assertEquals(listOf(ra), r.repoAnalyses)
        assertEquals("{\"native\":\"x\"}", r.improvedNative)
        assertEquals("adf", r.improvedFormat)
        assertEquals("impr", r.improvedSummary)
        assertEquals(listOf(sub), r.suggestedSubtasks)
    }

    @Test
    fun `SubtaskSuggestion defaults are sensible`() {
        val s = SubtaskSuggestion(summary = "s")
        assertEquals("s", s.summary)
        assertEquals("", s.description)
        assertEquals("Subtask", s.issueType)
        assertEquals("Minor", s.priority)
        assertTrue(s.labels.isEmpty())
        assertEquals("", s.existingKey)
    }

    @Test
    fun `SubtaskSuggestion stores provided values verbatim`() {
        val s =
            SubtaskSuggestion(
                summary = "s",
                description = "d",
                issueType = "Task",
                priority = "High",
                labels = listOf("l"),
                existingKey = "PROJ-42",
            )
        assertEquals("Task", s.issueType)
        assertEquals("High", s.priority)
        assertEquals(listOf("l"), s.labels)
        assertEquals("PROJ-42", s.existingKey)
    }

    @Test
    fun `RepoAnalysis state defaults to SKIPPED when not relevant`() {
        val r =
            RepoAnalysis(
                repoRef = "local:1",
                repoName = "r",
                repoType = "local",
                relevant = false,
                summary = "s",
            )
        assertEquals("SKIPPED", r.state)
        assertEquals("", r.complexity)
        assertNull(r.errorMessage)
        assertTrue(r.suggestedChanges.isEmpty())
        assertTrue(r.dependencies.isEmpty())
        assertTrue(r.hiddenDependencies.isEmpty())
    }

    @Test
    fun `RepoAnalysis state defaults to OK when relevant`() {
        val r =
            RepoAnalysis(
                repoRef = "local:1",
                repoName = "r",
                repoType = "local",
                relevant = true,
                summary = "s",
            )
        assertEquals("OK", r.state)
    }

    @Test
    fun `RepoAnalysis stores provided values verbatim`() {
        val r =
            RepoAnalysis(
                repoRef = "gitlab:1:2",
                repoName = "name",
                repoType = "gitlab",
                relevant = true,
                summary = "summary",
                suggestedChanges = listOf("c1"),
                dependencies = listOf("d1"),
                hiddenDependencies = listOf("h1"),
                complexity = "X",
                state = "FAILED",
                errorMessage = "err",
            )
        assertEquals("gitlab:1:2", r.repoRef)
        assertEquals("name", r.repoName)
        assertEquals("gitlab", r.repoType)
        assertEquals("X", r.complexity)
        assertEquals("FAILED", r.state)
        assertEquals("err", r.errorMessage)
        assertEquals(listOf("c1"), r.suggestedChanges)
        assertEquals(listOf("d1"), r.dependencies)
        assertEquals(listOf("h1"), r.hiddenDependencies)
    }
}
