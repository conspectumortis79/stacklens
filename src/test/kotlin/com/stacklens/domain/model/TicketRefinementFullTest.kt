package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TicketRefinementFullTest {
    @Test
    fun `TicketRefinement default values are correct`() {
        val a = TicketRefinement(parentTicketKey = "PROJ-1")
        assertEquals(0L, a.id)
        assertEquals("PROJ-1", a.parentTicketKey)
        assertNull(a.jiraConfigId)
        assertNull(a.templateOverride)
        assertTrue(a.singleTicketOnly)
        assertEquals("PENDING", a.status)
        assertNull(a.currentStep)
        assertNull(a.errorMessage)
        assertNull(a.parentTicketJson)
        assertNull(a.reviewsJson)
        assertNull(a.repoSelectionsJson)
        assertNull(a.databaseConfigId)
        assertNull(a.databaseConfigIds)
        assertNotNull(a.createdAt)
        assertNull(a.completedAt)
    }

    @Test
    fun `TicketRefinement supports mutating all var fields`() {
        val a = TicketRefinement(parentTicketKey = "PROJ-1")
        a.parentTicketKey = "PROJ-2"
        assertEquals("PROJ-2", a.parentTicketKey)
        a.jiraConfigId = 5L
        a.templateOverride = "custom"
        a.singleTicketOnly = false
        a.status = "RUNNING"
        a.currentStep = "step"
        a.errorMessage = "err"
        a.parentTicketJson = "{}"
        a.reviewsJson = "[]"
        a.repoSelectionsJson = "[]"
        a.databaseConfigId = 9L
        a.databaseConfigIds = "1,2"
        a.completedAt = java.time.LocalDateTime.now()
        assertEquals(5L, a.jiraConfigId)
        assertEquals("custom", a.templateOverride)
        assertEquals(false, a.singleTicketOnly)
        assertEquals("RUNNING", a.status)
        assertEquals("step", a.currentStep)
        assertEquals("err", a.errorMessage)
        assertEquals("{}", a.parentTicketJson)
        assertEquals("[]", a.reviewsJson)
        assertEquals("[]", a.repoSelectionsJson)
        assertEquals(9L, a.databaseConfigId)
        assertEquals("1,2", a.databaseConfigIds)
        assertNotNull(a.completedAt)
    }

    @Test
    fun `TicketReview default values are correct`() {
        val r =
            TicketReview(
                ticketKey = "PROJ-1",
                ticketUrl = "https://jira.example.com/PROJ-1",
                originalSummary = "summary",
                originalDescription = "desc",
                improvedDescription = "improved",
                openPoints = listOf("p1"),
                state = "OK",
            )
        assertEquals("PROJ-1", r.ticketKey)
        assertEquals("https://jira.example.com/PROJ-1", r.ticketUrl)
        assertEquals("summary", r.originalSummary)
        assertEquals("desc", r.originalDescription)
        assertEquals("improved", r.improvedDescription)
        assertEquals(listOf("p1"), r.openPoints)
        assertEquals("OK", r.state)
        assertNull(r.errorMessage)
        assertEquals(false, r.commentPosted)
        assertNull(r.originalDescriptionHtml)
        assertNull(r.improvedDescriptionHtml)
        assertEquals(emptyList<RepoAnalysis>(), r.repoAnalyses)
        assertEquals("", r.improvedNative)
        assertEquals("markdown", r.improvedFormat)
        assertEquals("", r.improvedSummary)
    }

    @Test
    fun `TicketReview supports all fields populated`() {
        val repo = RepoAnalysis(repoRef = "local:1", repoName = "r", repoType = "local", relevant = true, summary = "s")
        val r =
            TicketReview(
                ticketKey = "PROJ-1",
                ticketUrl = "u",
                originalSummary = "os",
                originalDescription = "od",
                improvedDescription = "id",
                openPoints = listOf("a", "b"),
                state = "NEEDS_REVIEW",
                errorMessage = "err",
                commentPosted = true,
                originalDescriptionHtml = "<p>od</p>",
                improvedDescriptionHtml = "<p>id</p>",
                repoAnalyses = listOf(repo),
                improvedNative = "{}",
                improvedFormat = "adf",
                improvedSummary = "is",
            )
        assertEquals("NEEDS_REVIEW", r.state)
        assertEquals("err", r.errorMessage)
        assertTrue(r.commentPosted)
        assertEquals("<p>od</p>", r.originalDescriptionHtml)
        assertEquals("<p>id</p>", r.improvedDescriptionHtml)
        assertEquals(listOf(repo), r.repoAnalyses)
        assertEquals("{}", r.improvedNative)
        assertEquals("adf", r.improvedFormat)
        assertEquals("is", r.improvedSummary)
    }

    @Test
    fun `RepoAnalysis default state is OK when relevant is true`() {
        val a = RepoAnalysis(repoRef = "x", repoName = "n", repoType = "gitlab", relevant = true, summary = "s")
        assertEquals("OK", a.state)
        assertEquals("", a.complexity)
        assertNull(a.errorMessage)
        assertEquals(emptyList<String>(), a.suggestedChanges)
        assertEquals(emptyList<String>(), a.dependencies)
        assertEquals(emptyList<String>(), a.hiddenDependencies)
    }

    @Test
    fun `RepoAnalysis default state is SKIPPED when not relevant`() {
        val a = RepoAnalysis(repoRef = "x", repoName = "n", repoType = "local", relevant = false, summary = "n/a")
        assertEquals("SKIPPED", a.state)
        assertEquals("n/a", a.summary)
    }

    @Test
    fun `RepoAnalysis supports all fields populated`() {
        val a =
            RepoAnalysis(
                repoRef = "gitlab:1:42",
                repoName = "service",
                repoType = "gitlab",
                relevant = true,
                summary = "core service",
                suggestedChanges = listOf("c1", "c2"),
                dependencies = listOf("d1"),
                hiddenDependencies = listOf("hd1"),
                complexity = "M",
                state = "OK",
                errorMessage = "warn",
            )
        assertEquals("gitlab:1:42", a.repoRef)
        assertEquals("service", a.repoName)
        assertEquals("gitlab", a.repoType)
        assertEquals(true, a.relevant)
        assertEquals("core service", a.summary)
        assertEquals(listOf("c1", "c2"), a.suggestedChanges)
        assertEquals(listOf("d1"), a.dependencies)
        assertEquals(listOf("hd1"), a.hiddenDependencies)
        assertEquals("M", a.complexity)
        assertEquals("OK", a.state)
        assertEquals("warn", a.errorMessage)
    }
}
