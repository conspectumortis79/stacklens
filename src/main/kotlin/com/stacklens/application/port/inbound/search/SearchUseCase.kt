package com.stacklens.application.port.inbound.search

import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession

interface SearchUseCase {
    fun startSearch(request: SearchCommand): SearchSession
}

interface SearchHistoryUseCase {
    fun sessionExists(sessionId: Long): Boolean

    fun listSessions(): List<SearchSession>

    fun cancelSession(sessionId: Long): SearchSession

    fun deleteSession(sessionId: Long)

    fun deleteAllSessions(): Int

    fun getResults(
        sessionId: Long,
        sourceType: String?,
    ): List<SearchResult>

    fun findSession(sessionId: Long): SearchSession?

    fun getSessionResultCount(sessionId: Long): Long
}

data class SearchCommand
    constructor(
        val keywords: List<String>,
        val includeGitlab: Boolean = false,
        val includeDatabase: Boolean = false,
        val includePipeline: Boolean = false,
        val includeConfluence: Boolean = false,
        val includeGrafana: Boolean = false,
        val includeJira: Boolean = false,
        val includeContextMap: Boolean = false,
        val includeGithub: Boolean = false,
        val crossReferenceEnabled: Boolean = false,
        val gitlabConfigIds: List<Long> = emptyList(),
        val databaseConfigIds: List<Long> = emptyList(),
        val confluenceConfigIds: List<Long> = emptyList(),
        val grafanaConfigIds: List<Long> = emptyList(),
        val jiraConfigIds: List<Long> = emptyList(),
        val githubConfigIds: List<Long> = emptyList(),
        val gitlabRepositories: List<String> = emptyList(),
        val jiraProjects: List<String> = emptyList(),
        val jiraComponents: List<String> = emptyList(),
        val jiraLabels: List<String> = emptyList(),
        val confluenceWebsites: List<String> = emptyList(),
        val grafanaPages: List<String> = emptyList(),
        val pipelineFilters: List<String> = emptyList(),
    )
