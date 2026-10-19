package com.stacklens.application.port.out.health

import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig

sealed class ConnectionProbeResult {
    abstract val errorMessage: String?

    data class Ok(
        val toolCount: Int = 0,
    ) : ConnectionProbeResult() {
        override val errorMessage: String? get() = null
    }

    data class Failure(
        override val errorMessage: String,
    ) : ConnectionProbeResult()
}

interface DatabaseConnectionProbePort {
    fun probe(config: DatabaseConfig): ConnectionProbeResult
}

interface JiraConnectionProbePort {
    fun probe(config: JiraConfig): ConnectionProbeResult
}

interface ConfluenceConnectionProbePort {
    fun probe(config: ConfluenceConfig): ConnectionProbeResult
}

interface GitlabConnectionProbePort {
    fun probe(config: GitlabConfig): ConnectionProbeResult
}

interface GrafanaConnectionProbePort {
    fun probe(config: GrafanaConfig): ConnectionProbeResult
}

interface GithubConnectionProbePort {
    fun probe(
        owner: String,
        repo: String,
        token: String?,
        apiBase: String,
    ): ConnectionProbeResult
}

interface McpConnectionProbePort {
    fun probe(config: McpServerConfig): ConnectionProbeResult
}
