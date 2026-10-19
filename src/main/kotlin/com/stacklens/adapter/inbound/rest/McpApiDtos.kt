package com.stacklens.adapter.inbound.rest

data class McpServerConfigDto(
    val id: Long? = null,
    val name: String = "",
    val baseUrl: String = "",
    val authToken: String = "",
    val authTokenSet: Boolean = false,
    val active: Boolean = false,
    val routeJira: Boolean = true,
    val routeConfluence: Boolean = true,
    val routeGitlab: Boolean = true,
    val routeGithub: Boolean = true,
    val routeGrafana: Boolean = true,
    val routeDatabase: Boolean = true,
    val jiraBaseUrl: String = "",
    val jiraUsername: String = "",
    val jiraApiToken: String = "",
    val jiraApiTokenSet: Boolean = false,
    val jiraCloudMode: Boolean = true,
    val confluenceBaseUrl: String = "",
    val confluenceUsername: String = "",
    val confluenceApiToken: String = "",
    val confluenceApiTokenSet: Boolean = false,
    val confluenceCloudMode: Boolean = true,
    val gitlabBaseUrl: String = "https://gitlab.com",
    val gitlabAccessToken: String = "",
    val gitlabAccessTokenSet: Boolean = false,
    val githubApiBase: String = "https://api.github.com",
    val githubToken: String = "",
    val githubTokenSet: Boolean = false,
    val grafanaBaseUrl: String = "",
    val grafanaApiKey: String = "",
    val grafanaApiKeySet: Boolean = false,
    val autoStartManaged: Boolean = false,
)

data class McpToolCallRequest(
    val tool: String = "",
    val args: Map<String, Any?> = emptyMap(),
    val mcpServerId: Long? = null,
)

data class McpToolCallResponse(
    val ok: Boolean,
    val text: String,
    val tool: String,
)
