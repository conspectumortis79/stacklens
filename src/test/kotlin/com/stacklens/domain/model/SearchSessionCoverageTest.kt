package com.stacklens.domain.model

import com.stacklens.domain.enums.SessionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SearchSessionCoverageTest {
    @Test
    fun `SearchSession defaults are sensible`() {
        val s = SearchSession(keywords = "kw")
        assertEquals(0L, s.id)
        assertEquals("kw", s.keywords)
        assertEquals(SessionStatus.PENDING, s.status)
        assertNull(s.gitlabConfigIds)
        assertNull(s.databaseConfigIds)
        assertNull(s.confluenceConfigIds)
        assertNull(s.grafanaConfigIds)
        assertNull(s.jiraConfigIds)
        assertNull(s.githubConfigIds)
        assertNull(s.applicationName)
        assertNull(s.gitlabRepositories)
        assertNull(s.jiraProjects)
        assertNull(s.jiraComponents)
        assertNull(s.jiraLabels)
        assertNull(s.confluenceWebsites)
        assertNull(s.grafanaPages)
        assertNull(s.pipelineFilters)
        assertTrue(s.includeGitlab)
        assertTrue(s.includeDatabase)
        assertFalse(s.includePipeline)
        assertFalse(s.includeConfluence)
        assertFalse(s.includeGrafana)
        assertFalse(s.includeJira)
        assertFalse(s.includeGithub)
        assertFalse(s.includeContextMap)
        assertFalse(s.crossReferenceEnabled)
        assertEquals(0, s.resultCount)
        assertNull(s.errorMessage)
        assertTrue(s.createdAt <= LocalDateTime.now().plusSeconds(2))
        assertNull(s.finishedAt)
    }

    @Test
    fun `SearchSession stores provided values verbatim`() {
        val now = LocalDateTime.of(2025, 1, 1, 0, 0)
        val done = LocalDateTime.of(2025, 1, 1, 1, 0)
        val s =
            SearchSession(
                id = 7L,
                keywords = "k1,k2",
                status = SessionStatus.COMPLETED,
                gitlabConfigIds = "1,2",
                databaseConfigIds = "3",
                confluenceConfigIds = "4",
                grafanaConfigIds = "5",
                jiraConfigIds = "6",
                githubConfigIds = "7",
                applicationName = "app",
                gitlabRepositories = "r1,r2",
                jiraProjects = "PROJ",
                jiraComponents = "ui",
                jiraLabels = "frontend",
                confluenceWebsites = "site",
                grafanaPages = "dash",
                pipelineFilters = "main",
                includeGitlab = false,
                includeDatabase = false,
                includePipeline = true,
                includeConfluence = true,
                includeGrafana = true,
                includeJira = true,
                includeGithub = true,
                includeContextMap = true,
                crossReferenceEnabled = true,
                resultCount = 42,
                errorMessage = "err",
                createdAt = now,
                finishedAt = done,
            )
        assertEquals(7L, s.id)
        assertEquals("k1,k2", s.keywords)
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertFalse(s.includeGitlab)
        assertTrue(s.crossReferenceEnabled)
        assertEquals(42, s.resultCount)
        assertEquals(now, s.createdAt)
        assertEquals(done, s.finishedAt)
    }

    @Test
    fun `keywordList parses comma-separated and trims whitespace`() {
        val s = SearchSession(keywords = "a, b ,c")
        assertEquals(listOf("a", "b", "c"), s.keywordList())
    }

    @Test
    fun `keywordList returns empty list for blank keywords`() {
        assertEquals(emptyList<String>(), SearchSession(keywords = "").keywordList())
    }

    @Test
    fun `gitlabConfigIdList returns empty list when null`() {
        assertEquals(emptyList<Long>(), SearchSession(keywords = "k").gitlabConfigIdList())
    }

    @Test
    fun `gitlabConfigIdList parses longs and skips malformed entries`() {
        val s = SearchSession(keywords = "k", gitlabConfigIds = "1,xx,3")
        assertEquals(listOf(1L, 3L), s.gitlabConfigIdList())
    }

    @Test
    fun `databaseConfigIdList handles null and well-formed input`() {
        assertEquals(emptyList<Long>(), SearchSession(keywords = "k").databaseConfigIdList())
        assertEquals(listOf(2L), SearchSession(keywords = "k", databaseConfigIds = "2").databaseConfigIdList())
    }

    @Test
    fun `confluenceConfigIdList handles null and well-formed input`() {
        assertEquals(emptyList<Long>(), SearchSession(keywords = "k").confluenceConfigIdList())
        assertEquals(listOf(3L), SearchSession(keywords = "k", confluenceConfigIds = "3").confluenceConfigIdList())
    }

    @Test
    fun `grafanaConfigIdList handles null and well-formed input`() {
        assertEquals(emptyList<Long>(), SearchSession(keywords = "k").grafanaConfigIdList())
        assertEquals(listOf(4L), SearchSession(keywords = "k", grafanaConfigIds = "4").grafanaConfigIdList())
    }

    @Test
    fun `jiraConfigIdList handles null and well-formed input`() {
        assertEquals(emptyList<Long>(), SearchSession(keywords = "k").jiraConfigIdList())
        assertEquals(listOf(5L), SearchSession(keywords = "k", jiraConfigIds = "5").jiraConfigIdList())
    }

    @Test
    fun `githubConfigIdList handles null and well-formed input`() {
        assertEquals(emptyList<Long>(), SearchSession(keywords = "k").githubConfigIdList())
        assertEquals(listOf(6L), SearchSession(keywords = "k", githubConfigIds = "6").githubConfigIdList())
    }

    @Test
    fun `gitlabRepositoryList handles null blank and well-formed input`() {
        assertEquals(emptyList<String>(), SearchSession(keywords = "k").gitlabRepositoryList())
        assertEquals(emptyList<String>(), SearchSession(keywords = "k", gitlabRepositories = "").gitlabRepositoryList())
        assertEquals(emptyList<String>(), SearchSession(keywords = "k", gitlabRepositories = "  ").gitlabRepositoryList())
        assertEquals(listOf("a", "b"), SearchSession(keywords = "k", gitlabRepositories = "a, b").gitlabRepositoryList())
    }

    @Test
    fun `jiraProjectList handles null blank and well-formed input`() {
        assertEquals(emptyList<String>(), SearchSession(keywords = "k").jiraProjectList())
        assertEquals(emptyList<String>(), SearchSession(keywords = "k", jiraProjects = "").jiraProjectList())
        assertEquals(listOf("P"), SearchSession(keywords = "k", jiraProjects = "P").jiraProjectList())
    }

    @Test
    fun `jiraComponentList handles null blank and well-formed input`() {
        assertEquals(emptyList<String>(), SearchSession(keywords = "k").jiraComponentList())
        assertEquals(emptyList<String>(), SearchSession(keywords = "k", jiraComponents = "").jiraComponentList())
        assertEquals(listOf("ui"), SearchSession(keywords = "k", jiraComponents = "ui").jiraComponentList())
    }

    @Test
    fun `jiraLabelList handles null blank and well-formed input`() {
        assertEquals(emptyList<String>(), SearchSession(keywords = "k").jiraLabelList())
        assertEquals(emptyList<String>(), SearchSession(keywords = "k", jiraLabels = "").jiraLabelList())
        assertEquals(listOf("l"), SearchSession(keywords = "k", jiraLabels = "l").jiraLabelList())
    }

    @Test
    fun `confluenceWebsiteList handles null blank and well-formed input`() {
        assertEquals(emptyList<String>(), SearchSession(keywords = "k").confluenceWebsiteList())
        assertEquals(emptyList<String>(), SearchSession(keywords = "k", confluenceWebsites = "").confluenceWebsiteList())
        assertEquals(listOf("site"), SearchSession(keywords = "k", confluenceWebsites = "site").confluenceWebsiteList())
    }

    @Test
    fun `grafanaPageList handles null blank and well-formed input`() {
        assertEquals(emptyList<String>(), SearchSession(keywords = "k").grafanaPageList())
        assertEquals(emptyList<String>(), SearchSession(keywords = "k", grafanaPages = "").grafanaPageList())
        assertEquals(listOf("dash"), SearchSession(keywords = "k", grafanaPages = "dash").grafanaPageList())
    }

    @Test
    fun `pipelineFilterList handles null blank and well-formed input`() {
        assertEquals(emptyList<String>(), SearchSession(keywords = "k").pipelineFilterList())
        assertEquals(emptyList<String>(), SearchSession(keywords = "k", pipelineFilters = "").pipelineFilterList())
        assertEquals(listOf("main"), SearchSession(keywords = "k", pipelineFilters = "main").pipelineFilterList())
    }
}
