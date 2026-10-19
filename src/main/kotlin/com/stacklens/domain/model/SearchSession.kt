package com.stacklens.domain.model
import com.stacklens.domain.enums.SessionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "search_sessions")
class SearchSession(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, columnDefinition = "TEXT")
    var keywords: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SessionStatus = SessionStatus.PENDING,
    @Column(columnDefinition = "TEXT")
    var gitlabConfigIds: String? = null,
    @Column(columnDefinition = "TEXT")
    var databaseConfigIds: String? = null,
    @Column(columnDefinition = "TEXT")
    var confluenceConfigIds: String? = null,
    @Column(columnDefinition = "TEXT")
    var grafanaConfigIds: String? = null,
    @Column(columnDefinition = "TEXT")
    var jiraConfigIds: String? = null,
    @Column(columnDefinition = "TEXT")
    var githubConfigIds: String? = null,
    @Column(columnDefinition = "TEXT")
    var applicationName: String? = null,
    @Column(columnDefinition = "TEXT")
    var gitlabRepositories: String? = null,
    @Column(columnDefinition = "TEXT")
    var jiraProjects: String? = null,
    @Column(columnDefinition = "TEXT")
    var jiraComponents: String? = null,
    @Column(columnDefinition = "TEXT")
    var jiraLabels: String? = null,
    @Column(columnDefinition = "TEXT")
    var confluenceWebsites: String? = null,
    @Column(columnDefinition = "TEXT")
    var grafanaPages: String? = null,
    @Column(columnDefinition = "TEXT")
    var pipelineFilters: String? = null,
    var includeGitlab: Boolean = true,
    var includeDatabase: Boolean = true,
    var includePipeline: Boolean = false,
    var includeConfluence: Boolean = false,
    var includeGrafana: Boolean = false,
    var includeJira: Boolean = false,
    var includeGithub: Boolean = false,
    var includeContextMap: Boolean = false,
    var crossReferenceEnabled: Boolean = false,
    var resultCount: Int = 0,
    var errorMessage: String? = null,
    @Column(name = "database_connection_errors", columnDefinition = "TEXT")
    var databaseConnectionErrors: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var finishedAt: LocalDateTime? = null,
) {
    fun keywordList(): List<String> = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun gitlabConfigIdList(): List<Long> = gitlabConfigIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

    fun databaseConfigIdList(): List<Long> = databaseConfigIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

    fun confluenceConfigIdList(): List<Long> = confluenceConfigIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

    fun grafanaConfigIdList(): List<Long> = grafanaConfigIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

    fun jiraConfigIdList(): List<Long> = jiraConfigIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

    fun githubConfigIdList(): List<Long> = githubConfigIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

    fun gitlabRepositoryList(): List<String> = gitlabRepositories?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun jiraProjectList(): List<String> = jiraProjects?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun jiraComponentList(): List<String> = jiraComponents?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun jiraLabelList(): List<String> = jiraLabels?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun confluenceWebsiteList(): List<String> = confluenceWebsites?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun grafanaPageList(): List<String> = grafanaPages?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun pipelineFilterList(): List<String> = pipelineFilters?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
}
