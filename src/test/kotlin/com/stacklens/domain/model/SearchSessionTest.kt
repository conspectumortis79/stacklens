package com.stacklens.domain.model

import com.stacklens.domain.enums.SessionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchSessionTest {
    @Test
    fun `keywordList splits comma-separated keywords and trims whitespace`() {
        val session = SearchSession(keywords = " foo , bar , baz ")
        assertEquals(listOf("foo", "bar", "baz"), session.keywordList())
    }

    @Test
    fun `keywordList returns single keyword when no comma present`() {
        val session = SearchSession(keywords = "myKeyword")
        assertEquals(listOf("myKeyword"), session.keywordList())
    }

    @Test
    fun `keywordList filters out blank entries`() {
        val session = SearchSession(keywords = "foo,,  ,bar")
        assertEquals(listOf("foo", "bar"), session.keywordList())
    }

    @Test
    fun `gitlabConfigIdList parses valid ids`() {
        val session = SearchSession(keywords = "k", gitlabConfigIds = "1,2,3")
        assertEquals(listOf(1L, 2L, 3L), session.gitlabConfigIdList())
    }

    @Test
    fun `gitlabConfigIdList returns empty list when null`() {
        val session = SearchSession(keywords = "k", gitlabConfigIds = null)
        assertTrue(session.gitlabConfigIdList().isEmpty())
    }

    @Test
    fun `gitlabConfigIdList skips non-numeric tokens`() {
        val session = SearchSession(keywords = "k", gitlabConfigIds = "1,abc,2")
        assertEquals(listOf(1L, 2L), session.gitlabConfigIdList())
    }

    @Test
    fun `databaseConfigIdList parses valid ids`() {
        val session = SearchSession(keywords = "k", databaseConfigIds = "10,20")
        assertEquals(listOf(10L, 20L), session.databaseConfigIdList())
    }

    @Test
    fun `databaseConfigIdList returns empty list when null`() {
        val session = SearchSession(keywords = "k", databaseConfigIds = null)
        assertTrue(session.databaseConfigIdList().isEmpty())
    }

    @Test
    fun `databaseConfigIdList skips invalid and blank tokens`() {
        val session = SearchSession(keywords = "k", databaseConfigIds = "10, ,oops,20")
        assertEquals(listOf(10L, 20L), session.databaseConfigIdList())
    }

    @Test
    fun `confluenceConfigIdList parses valid ids`() {
        val session = SearchSession(keywords = "k", confluenceConfigIds = "5,6")
        assertEquals(listOf(5L, 6L), session.confluenceConfigIdList())
    }

    @Test
    fun `confluenceConfigIdList returns empty list when null`() {
        val session = SearchSession(keywords = "k", confluenceConfigIds = null)
        assertTrue(session.confluenceConfigIdList().isEmpty())
    }

    @Test
    fun `confluenceConfigIdList skips invalid tokens`() {
        val session = SearchSession(keywords = "k", confluenceConfigIds = "5,abc,,6")
        assertEquals(listOf(5L, 6L), session.confluenceConfigIdList())
    }

    @Test
    fun `grafanaConfigIdList parses valid ids`() {
        val session = SearchSession(keywords = "k", grafanaConfigIds = "7,8")
        assertEquals(listOf(7L, 8L), session.grafanaConfigIdList())
    }

    @Test
    fun `grafanaConfigIdList returns empty list when null`() {
        val session = SearchSession(keywords = "k", grafanaConfigIds = null)
        assertTrue(session.grafanaConfigIdList().isEmpty())
    }

    @Test
    fun `grafanaConfigIdList skips invalid tokens`() {
        val session = SearchSession(keywords = "k", grafanaConfigIds = "7,broken,8")
        assertEquals(listOf(7L, 8L), session.grafanaConfigIdList())
    }

    @Test
    fun `jiraConfigIdList parses valid ids`() {
        val session = SearchSession(keywords = "k", jiraConfigIds = "11,12")
        assertEquals(listOf(11L, 12L), session.jiraConfigIdList())
    }

    @Test
    fun `jiraConfigIdList returns empty list when null`() {
        val session = SearchSession(keywords = "k", jiraConfigIds = null)
        assertTrue(session.jiraConfigIdList().isEmpty())
    }

    @Test
    fun `jiraConfigIdList skips invalid tokens`() {
        val session = SearchSession(keywords = "k", jiraConfigIds = "11,nope,12")
        assertEquals(listOf(11L, 12L), session.jiraConfigIdList())
    }

    @Test
    fun `gitlabRepositoryList returns trimmed non-blank values`() {
        val session = SearchSession(keywords = "k", gitlabRepositories = " group/repo1 , group/repo2 ")
        assertEquals(listOf("group/repo1", "group/repo2"), session.gitlabRepositoryList())
    }

    @Test
    fun `gitlabRepositoryList returns empty list when null`() {
        val session = SearchSession(keywords = "k", gitlabRepositories = null)
        assertTrue(session.gitlabRepositoryList().isEmpty())
    }

    @Test
    fun `gitlabRepositoryList filters blank entries`() {
        val session = SearchSession(keywords = "k", gitlabRepositories = "repo1,,  ")
        assertEquals(listOf("repo1"), session.gitlabRepositoryList())
    }

    @Test
    fun `jiraProjectList returns trimmed non-blank values`() {
        val session = SearchSession(keywords = "k", jiraProjects = "PROJ,TEAM")
        assertEquals(listOf("PROJ", "TEAM"), session.jiraProjectList())
    }

    @Test
    fun `jiraProjectList returns empty list when null`() {
        val session = SearchSession(keywords = "k", jiraProjects = null)
        assertTrue(session.jiraProjectList().isEmpty())
    }

    @Test
    fun `jiraProjectList filters blank tokens`() {
        val session = SearchSession(keywords = "k", jiraProjects = "PROJ,  ,TEAM,")
        assertEquals(listOf("PROJ", "TEAM"), session.jiraProjectList())
    }

    @Test
    fun `jiraComponentList returns trimmed non-blank values`() {
        val session = SearchSession(keywords = "k", jiraComponents = "backend, frontend")
        assertEquals(listOf("backend", "frontend"), session.jiraComponentList())
    }

    @Test
    fun `jiraComponentList returns empty list when null`() {
        val session = SearchSession(keywords = "k", jiraComponents = null)
        assertTrue(session.jiraComponentList().isEmpty())
    }

    @Test
    fun `jiraComponentList filters blank tokens`() {
        val session = SearchSession(keywords = "k", jiraComponents = "backend,, ,frontend")
        assertEquals(listOf("backend", "frontend"), session.jiraComponentList())
    }

    @Test
    fun `jiraLabelList returns trimmed non-blank values`() {
        val session = SearchSession(keywords = "k", jiraLabels = "bug,feature")
        assertEquals(listOf("bug", "feature"), session.jiraLabelList())
    }

    @Test
    fun `jiraLabelList returns empty list when null`() {
        val session = SearchSession(keywords = "k", jiraLabels = null)
        assertTrue(session.jiraLabelList().isEmpty())
    }

    @Test
    fun `jiraLabelList filters blank tokens`() {
        val session = SearchSession(keywords = "k", jiraLabels = "bug, ,feature,")
        assertEquals(listOf("bug", "feature"), session.jiraLabelList())
    }

    @Test
    fun `confluenceWebsiteList returns trimmed non-blank values`() {
        val session = SearchSession(keywords = "k", confluenceWebsites = "https://a.com,https://b.com")
        assertEquals(listOf("https://a.com", "https://b.com"), session.confluenceWebsiteList())
    }

    @Test
    fun `confluenceWebsiteList returns empty list when null`() {
        val session = SearchSession(keywords = "k", confluenceWebsites = null)
        assertTrue(session.confluenceWebsiteList().isEmpty())
    }

    @Test
    fun `confluenceWebsiteList filters blank tokens`() {
        val session = SearchSession(keywords = "k", confluenceWebsites = "https://a.com, ,https://b.com,")
        assertEquals(listOf("https://a.com", "https://b.com"), session.confluenceWebsiteList())
    }

    @Test
    fun `grafanaPageList returns trimmed non-blank values`() {
        val session = SearchSession(keywords = "k", grafanaPages = "/d/abc123,/d/xyz456")
        assertEquals(listOf("/d/abc123", "/d/xyz456"), session.grafanaPageList())
    }

    @Test
    fun `grafanaPageList returns empty list when null`() {
        val session = SearchSession(keywords = "k", grafanaPages = null)
        assertTrue(session.grafanaPageList().isEmpty())
    }

    @Test
    fun `grafanaPageList filters blank tokens`() {
        val session = SearchSession(keywords = "k", grafanaPages = "/d/a,, ,/d/b")
        assertEquals(listOf("/d/a", "/d/b"), session.grafanaPageList())
    }

    @Test
    fun `pipelineFilterList returns trimmed non-blank values`() {
        val session = SearchSession(keywords = "k", pipelineFilters = "deploy,test")
        assertEquals(listOf("deploy", "test"), session.pipelineFilterList())
    }

    @Test
    fun `pipelineFilterList returns empty list when null`() {
        val session = SearchSession(keywords = "k", pipelineFilters = null)
        assertTrue(session.pipelineFilterList().isEmpty())
    }

    @Test
    fun `pipelineFilterList filters blank tokens`() {
        val session = SearchSession(keywords = "k", pipelineFilters = "deploy, ,test,")
        assertEquals(listOf("deploy", "test"), session.pipelineFilterList())
    }

    @Test
    fun `keywordList returns empty list for commas and whitespace only`() {
        val session = SearchSession(keywords = " ,  , ")
        assertTrue(session.keywordList().isEmpty())
    }

    @Test
    fun `default status is PENDING`() {
        val session = SearchSession(keywords = "k")
        assertEquals(SessionStatus.PENDING, session.status)
    }

    @Test
    fun `default boolean flags are set correctly`() {
        val session = SearchSession(keywords = "k")
        assertTrue(session.includeGitlab)
        assertTrue(session.includeDatabase)
    }
}
