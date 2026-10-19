package com.stacklens.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "mcp_server_configs")
class McpServerConfig(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    @Column(nullable = false)
    override var name: String = "",
    @Column(nullable = false)
    var baseUrl: String = "",
    @Column(columnDefinition = "TEXT")
    var authToken: String = "",
    override var active: Boolean = false,
    var routeJira: Boolean = true,
    var routeConfluence: Boolean = true,
    var routeGitlab: Boolean = true,
    var routeGithub: Boolean = true,
    @Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT TRUE")
    var routeGrafana: Boolean = true,
    var routeDatabase: Boolean = true,
    @Column(columnDefinition = "TEXT") var jiraBaseUrl: String = "",
    @Column(columnDefinition = "TEXT") var jiraUsername: String = "",
    @Column(columnDefinition = "TEXT") var jiraApiToken: String = "",
    var jiraCloudMode: Boolean = true,
    @Column(columnDefinition = "TEXT") var confluenceBaseUrl: String = "",
    @Column(columnDefinition = "TEXT") var confluenceUsername: String = "",
    @Column(columnDefinition = "TEXT") var confluenceApiToken: String = "",
    var confluenceCloudMode: Boolean = true,
    @Column(columnDefinition = "TEXT") var gitlabBaseUrl: String = "https://gitlab.com",
    @Column(columnDefinition = "TEXT") var gitlabAccessToken: String = "",
    @Column(columnDefinition = "TEXT") var githubApiBase: String = "https://api.github.com",
    @Column(columnDefinition = "TEXT") var githubToken: String = "",
    @Column(columnDefinition = "TEXT NOT NULL DEFAULT ''") var grafanaBaseUrl: String = "",
    @Column(columnDefinition = "TEXT NOT NULL DEFAULT ''") var grafanaApiKey: String = "",
    @Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    var autoStartManaged: Boolean = false,
    @Column(
        name = "consecutive_failures",
        nullable = false,
        columnDefinition = "INTEGER NOT NULL DEFAULT 0",
    )
    override var consecutiveFailures: Int = 0,
    @Column(name = "last_error_at")
    override var lastErrorAt: LocalDateTime? = null,
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    override var lastErrorMessage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) : ConnectionEntity
