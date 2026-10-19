package com.stacklens.domain.model

import com.stacklens.domain.enums.SessionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchSessionFullTest {
    @Test
    fun `SearchSession default values are correct`() {
        val s = SearchSession(keywords = "k1")
        assertEquals(0L, s.id)
        assertEquals("k1", s.keywords)
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
        assertEquals(false, s.includePipeline)
        assertEquals(false, s.includeConfluence)
        assertEquals(false, s.includeGrafana)
        assertEquals(false, s.includeJira)
        assertEquals(false, s.includeGithub)
        assertEquals(false, s.includeContextMap)
        assertEquals(false, s.crossReferenceEnabled)
        assertEquals(0, s.resultCount)
        assertNull(s.errorMessage)
        assertNotNull(s.createdAt)
        assertNull(s.finishedAt)
    }

    @Test
    fun `SearchSession keywordList splits comma-separated keywords`() {
        val s = SearchSession(keywords = "a,b,c")
        assertEquals(listOf("a", "b", "c"), s.keywordList())
    }

    @Test
    fun `SearchSession keywordList trims whitespace and filters empties`() {
        val s = SearchSession(keywords = " a , , b ,  ")
        assertEquals(listOf("a", "b"), s.keywordList())
    }

    @Test
    fun `SearchSession keywordList handles single keyword without comma`() {
        val s = SearchSession(keywords = "single")
        assertEquals(listOf("single"), s.keywordList())
    }

    @Test
    fun `SearchSession keywordList returns empty for empty string`() {
        val s = SearchSession(keywords = "")
        assertEquals(emptyList<String>(), s.keywordList())
    }

    @Test
    fun `SearchSession idList helpers parse comma-separated IDs`() {
        val s =
            SearchSession(
                keywords = "k",
                gitlabConfigIds = "1,2,3",
                databaseConfigIds = "10,20",
                confluenceConfigIds = "100",
                grafanaConfigIds = "5,6",
                jiraConfigIds = "7",
                githubConfigIds = "8,9",
            )
        assertEquals(listOf(1L, 2L, 3L), s.gitlabConfigIdList())
        assertEquals(listOf(10L, 20L), s.databaseConfigIdList())
        assertEquals(listOf(100L), s.confluenceConfigIdList())
        assertEquals(listOf(5L, 6L), s.grafanaConfigIdList())
        assertEquals(listOf(7L), s.jiraConfigIdList())
        assertEquals(listOf(8L, 9L), s.githubConfigIdList())
    }

    @Test
    fun `SearchSession idList helpers return empty for null IDs`() {
        val s = SearchSession(keywords = "k")
        assertEquals(emptyList<Long>(), s.gitlabConfigIdList())
        assertEquals(emptyList<Long>(), s.databaseConfigIdList())
        assertEquals(emptyList<Long>(), s.confluenceConfigIdList())
        assertEquals(emptyList<Long>(), s.grafanaConfigIdList())
        assertEquals(emptyList<Long>(), s.jiraConfigIdList())
        assertEquals(emptyList<Long>(), s.githubConfigIdList())
    }

    @Test
    fun `SearchSession idList helpers filter out invalid IDs`() {
        val s =
            SearchSession(
                keywords = "k",
                gitlabConfigIds = "1,abc,3",
            )
        assertEquals(listOf(1L, 3L), s.gitlabConfigIdList())
    }

    @Test
    fun `SearchSession stringList helpers parse comma-separated strings`() {
        val s =
            SearchSession(
                keywords = "k",
                gitlabRepositories = "repo1, repo2",
                jiraProjects = "PROJ,DEV",
                jiraComponents = "frontend,backend",
                jiraLabels = "bug,tech-debt",
                confluenceWebsites = "site1,site2",
                grafanaPages = "page1",
                pipelineFilters = "filter1,filter2",
            )
        assertEquals(listOf("repo1", "repo2"), s.gitlabRepositoryList())
        assertEquals(listOf("PROJ", "DEV"), s.jiraProjectList())
        assertEquals(listOf("frontend", "backend"), s.jiraComponentList())
        assertEquals(listOf("bug", "tech-debt"), s.jiraLabelList())
        assertEquals(listOf("site1", "site2"), s.confluenceWebsiteList())
        assertEquals(listOf("page1"), s.grafanaPageList())
        assertEquals(listOf("filter1", "filter2"), s.pipelineFilterList())
    }

    @Test
    fun `SearchSession stringList helpers return empty for null strings`() {
        val s = SearchSession(keywords = "k")
        assertEquals(emptyList<String>(), s.gitlabRepositoryList())
        assertEquals(emptyList<String>(), s.jiraProjectList())
        assertEquals(emptyList<String>(), s.jiraComponentList())
        assertEquals(emptyList<String>(), s.jiraLabelList())
        assertEquals(emptyList<String>(), s.confluenceWebsiteList())
        assertEquals(emptyList<String>(), s.grafanaPageList())
        assertEquals(emptyList<String>(), s.pipelineFilterList())
    }

    @Test
    fun `SearchSession stringList helpers filter out blanks`() {
        val s =
            SearchSession(
                keywords = "k",
                gitlabRepositories = " , , ",
            )
        assertEquals(emptyList<String>(), s.gitlabRepositoryList())
    }

    @Test
    fun `SearchSession supports mutating all var fields`() {
        val s = SearchSession(keywords = "k")
        s.keywords = "k2"
        assertEquals("k2", s.keywords)
        s.status = SessionStatus.RUNNING
        s.gitlabConfigIds = "1,2"
        s.databaseConfigIds = "3"
        s.confluenceConfigIds = "4"
        s.grafanaConfigIds = "5"
        s.jiraConfigIds = "6"
        s.githubConfigIds = "7"
        s.applicationName = "MyApp"
        s.gitlabRepositories = "r1,r2"
        s.jiraProjects = "PROJ"
        s.jiraComponents = "frontend"
        s.jiraLabels = "bug"
        s.confluenceWebsites = "site"
        s.grafanaPages = "page"
        s.pipelineFilters = "filter"
        s.includeGitlab = false
        s.includeDatabase = false
        s.includePipeline = true
        s.includeConfluence = true
        s.includeGrafana = true
        s.includeJira = true
        s.includeGithub = true
        s.includeContextMap = true
        s.crossReferenceEnabled = true
        s.resultCount = 42
        s.errorMessage = "err"
        s.finishedAt = java.time.LocalDateTime.now()
        assertEquals(SessionStatus.RUNNING, s.status)
        assertEquals("1,2", s.gitlabConfigIds)
        assertEquals("3", s.databaseConfigIds)
        assertEquals("4", s.confluenceConfigIds)
        assertEquals("5", s.grafanaConfigIds)
        assertEquals("6", s.jiraConfigIds)
        assertEquals("7", s.githubConfigIds)
        assertEquals("r1,r2", s.gitlabRepositories)
        assertEquals("PROJ", s.jiraProjects)
        assertEquals("frontend", s.jiraComponents)
        assertEquals("bug", s.jiraLabels)
        assertEquals("site", s.confluenceWebsites)
        assertEquals("page", s.grafanaPages)
        assertEquals("filter", s.pipelineFilters)
        assertEquals(false, s.includeGitlab)
        assertEquals(true, s.includePipeline)
        assertEquals(42, s.resultCount)
        assertEquals("err", s.errorMessage)
        assertNotNull(s.finishedAt)
    }
}
