package com.stacklens.application.port.inbound.mcp

data class McpServerConfigChanges(
    val name: String,
    val baseUrl: String,
    val authToken: String,
    val active: Boolean,
    val routeJira: Boolean,
    val routeConfluence: Boolean,
    val routeGitlab: Boolean,
    val routeGithub: Boolean,
    val routeGrafana: Boolean,
    val routeDatabase: Boolean,
    val jiraBaseUrl: String,
    val jiraUsername: String,
    val jiraApiToken: String,
    val jiraCloudMode: Boolean,
    val confluenceBaseUrl: String,
    val confluenceUsername: String,
    val confluenceApiToken: String,
    val confluenceCloudMode: Boolean,
    val gitlabBaseUrl: String,
    val gitlabAccessToken: String,
    val githubApiBase: String,
    val githubToken: String,
    val grafanaBaseUrl: String,
    val grafanaApiKey: String,
    val autoStartManaged: Boolean,
)

data class McpSecretPresence(
    val authTokenSet: Boolean,
    val jiraApiTokenSet: Boolean,
    val confluenceApiTokenSet: Boolean,
    val gitlabAccessTokenSet: Boolean,
    val githubTokenSet: Boolean,
    val grafanaApiKeySet: Boolean,
)

data class McpServerConfigView(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val secrets: McpSecretPresence,
    val active: Boolean,
    val routeJira: Boolean,
    val routeConfluence: Boolean,
    val routeGitlab: Boolean,
    val routeGithub: Boolean,
    val routeGrafana: Boolean,
    val routeDatabase: Boolean,
    val jiraBaseUrl: String,
    val jiraUsername: String,
    val jiraCloudMode: Boolean,
    val confluenceBaseUrl: String,
    val confluenceUsername: String,
    val confluenceCloudMode: Boolean,
    val gitlabBaseUrl: String,
    val githubApiBase: String,
    val grafanaBaseUrl: String,
    val autoStartManaged: Boolean,
)

data class McpToolView(
    val name: String,
    val description: String,
)

data class McpConnectionTestView(
    val ok: Boolean,
    val message: String,
    val toolCount: Int,
    val tools: List<McpToolView>,
)

data class McpProcessStatusView(
    val running: Boolean,
    val pid: Long?,
    val startedAt: String?,
    val stoppedAt: String?,
    val exitCode: Int?,
    val message: String,
    val logs: List<String>,
)

interface McpServerConfigUseCase : McpServerCatalog {
    fun listServerConfigs(): List<McpServerConfigView>

    fun saveServerConfig(
        id: Long?,
        changes: McpServerConfigChanges,
    ): McpServerConfigView

    fun deleteServerConfig(id: Long): Boolean

    fun testServerConnection(id: Long): McpConnectionTestView?

    fun syncFromStacklens(): McpServerConfigView
}

interface McpProcessUseCase {
    fun startServer(id: Long): McpProcessStatusView?

    fun startFirstAvailableServer(): McpProcessStatusView?

    fun stopServer(): McpProcessStatusView

    fun restartServer(): McpProcessStatusView?

    fun serverProcessStatus(): McpProcessStatusView
}

interface McpServerManagementUseCase :
    McpServerConfigUseCase,
    McpProcessUseCase
