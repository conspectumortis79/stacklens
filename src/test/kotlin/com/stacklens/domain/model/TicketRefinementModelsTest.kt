package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TicketRefinementModelsTest {
    @Test
    fun `SubtaskSuggestion defaults`() {
        val s = SubtaskSuggestion(summary = "Sub 1")
        assertEquals("Sub 1", s.summary)
        assertEquals("", s.description)
        assertEquals("Subtask", s.issueType)
        assertEquals("Minor", s.priority)
        assertEquals(emptyList<String>(), s.labels)
        assertEquals("", s.existingKey)
    }

    @Test
    fun `SubtaskSuggestion with all fields`() {
        val s =
            SubtaskSuggestion(
                summary = "S",
                description = "D",
                issueType = "Task",
                priority = "Major",
                labels = listOf("a", "b"),
                existingKey = "PROJ-1",
            )
        assertEquals("S", s.summary)
        assertEquals("D", s.description)
        assertEquals("Task", s.issueType)
        assertEquals("Major", s.priority)
        assertEquals(listOf("a", "b"), s.labels)
        assertEquals("PROJ-1", s.existingKey)
    }

    @Test
    fun `SubtaskSuggestion equality`() {
        val a = SubtaskSuggestion("x")
        val b = SubtaskSuggestion("x")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `TicketReview defaults`() {
        val r =
            TicketReview(
                ticketKey = "K",
                ticketUrl = "U",
                originalSummary = "O",
                originalDescription = "OD",
                improvedDescription = "ID",
                openPoints = listOf("p1"),
                state = "OK",
            )
        assertEquals("K", r.ticketKey)
        assertEquals("U", r.ticketUrl)
        assertEquals("O", r.originalSummary)
        assertEquals("OD", r.originalDescription)
        assertEquals("ID", r.improvedDescription)
        assertEquals(listOf("p1"), r.openPoints)
        assertEquals("OK", r.state)
        assertEquals(null, r.errorMessage)
        assertEquals(false, r.commentPosted)
        assertEquals(null, r.originalDescriptionHtml)
        assertEquals(null, r.improvedDescriptionHtml)
        assertEquals(emptyList<RepoAnalysis>(), r.repoAnalyses)
        assertEquals("", r.improvedNative)
        assertEquals("markdown", r.improvedFormat)
        assertEquals("", r.improvedSummary)
        assertEquals(emptyList<SubtaskSuggestion>(), r.suggestedSubtasks)
    }

    @Test
    fun `TicketReview with all fields`() {
        val r =
            TicketReview(
                ticketKey = "K",
                ticketUrl = "U",
                originalSummary = "O",
                originalDescription = "OD",
                improvedDescription = "ID",
                openPoints = listOf("p1"),
                state = "NEEDS_REVIEW",
                errorMessage = "e",
                commentPosted = true,
                originalDescriptionHtml = "<p>OD</p>",
                improvedDescriptionHtml = "<p>ID</p>",
                repoAnalyses = emptyList(),
                improvedNative = "{\"x\":1}",
                improvedFormat = "adf",
                improvedSummary = "Better",
                suggestedSubtasks = listOf(SubtaskSuggestion(summary = "S1")),
            )
        assertEquals("NEEDS_REVIEW", r.state)
        assertEquals("e", r.errorMessage)
        assertEquals(true, r.commentPosted)
        assertEquals("<p>OD</p>", r.originalDescriptionHtml)
        assertEquals("<p>ID</p>", r.improvedDescriptionHtml)
        assertEquals("{\"x\":1}", r.improvedNative)
        assertEquals("adf", r.improvedFormat)
        assertEquals("Better", r.improvedSummary)
        assertEquals(1, r.suggestedSubtasks.size)
    }

    @Test
    fun `RepoAnalysis defaults`() {
        val a =
            RepoAnalysis(
                repoRef = "local:1",
                repoName = "name",
                repoType = "local",
                relevant = true,
                summary = "s",
            )
        assertEquals("local:1", a.repoRef)
        assertEquals("name", a.repoName)
        assertEquals("local", a.repoType)
        assertEquals(true, a.relevant)
        assertEquals("s", a.summary)
        assertEquals(emptyList<String>(), a.suggestedChanges)
        assertEquals(emptyList<String>(), a.dependencies)
        assertEquals(emptyList<String>(), a.hiddenDependencies)
        assertEquals("", a.complexity)
        assertEquals("OK", a.state)
        assertEquals(null, a.errorMessage)
    }

    @Test
    fun `RepoAnalysis with not relevant state uses SKIPPED`() {
        val a =
            RepoAnalysis(
                repoRef = "x",
                repoName = "n",
                repoType = "gitlab",
                relevant = false,
                summary = "no",
            )
        assertEquals("SKIPPED", a.state)
    }

    @Test
    fun `RepoAnalysis with all fields`() {
        val a =
            RepoAnalysis(
                repoRef = "gitlab:1:2",
                repoName = "g/r",
                repoType = "gitlab",
                relevant = true,
                summary = "yes",
                suggestedChanges = listOf("c1"),
                dependencies = listOf("d1"),
                hiddenDependencies = listOf("h1"),
                complexity = "M",
                state = "OK",
                errorMessage = "e",
            )
        assertEquals(listOf("c1"), a.suggestedChanges)
        assertEquals(listOf("d1"), a.dependencies)
        assertEquals(listOf("h1"), a.hiddenDependencies)
        assertEquals("M", a.complexity)
        assertEquals("e", a.errorMessage)
    }
}
