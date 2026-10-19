package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.McpServerConfigDto
import com.stacklens.application.port.inbound.mcp.McpConnectionTestView
import com.stacklens.application.port.inbound.mcp.McpProcessStatusView
import com.stacklens.application.port.inbound.mcp.McpProcessUseCase
import com.stacklens.application.port.inbound.mcp.McpServerConfigChanges
import com.stacklens.application.port.inbound.mcp.McpServerConfigUseCase
import com.stacklens.application.port.inbound.mcp.McpServerConfigView
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/config/mcp")
class McpServerConfigApiController(
    private val serverConfig: McpServerConfigUseCase,
    private val process: McpProcessUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @GetMapping
    fun list(): List<McpServerConfigDto> = serverConfig.listServerConfigs().map(::toDto)

    @PostMapping
    fun save(
        @RequestBody dto: McpServerConfigDto,
    ): McpServerConfigDto = toDto(serverConfig.saveServerConfig(dto.id, dto.toChanges()))

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> =
        if (serverConfig.deleteServerConfig(id)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }

    @PostMapping("/{id}/test")
    fun test(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> {
        val result = serverConfig.testServerConnection(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toTestMap(result))
    }

    @PostMapping("/sync-from-stacklens")
    fun syncFromStacklens(): McpServerConfigDto = toDto(serverConfig.syncFromStacklens())

    @PostMapping("/{id}/process/start")
    fun processStart(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val status = process.startServer(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(statusToMap(status))
    }

    @PostMapping("/process/start")
    fun processStartAuto(): ResponseEntity<Map<String, Any?>> =
        process
            .startFirstAvailableServer()
            ?.let { ResponseEntity.ok(statusToMap(it)) }
            ?: ResponseEntity.badRequest().body(mapOf("error" to tr("mcp.be_no_config")))

    @PostMapping("/process/stop")
    fun processStop(): Map<String, Any?> = statusToMap(process.stopServer())

    @PostMapping("/process/restart")
    fun processRestart(): ResponseEntity<Map<String, Any?>> =
        process
            .restartServer()
            ?.let { ResponseEntity.ok(statusToMap(it)) }
            ?: ResponseEntity.badRequest().body(mapOf("error" to tr("mcp.be_no_config")))

    @GetMapping("/process/status")
    fun processStatus(): Map<String, Any?> = statusToMap(process.serverProcessStatus())

    private fun toTestMap(result: McpConnectionTestView): Map<String, Any> =
        mapOf(
            "ok" to result.ok,
            "message" to result.message,
            "toolCount" to result.toolCount,
            "tools" to result.tools.map { mapOf("name" to it.name, "description" to it.description) },
        )

    private fun statusToMap(s: McpProcessStatusView): Map<String, Any?> =
        mapOf(
            "running" to s.running,
            "pid" to s.pid,
            "startedAt" to s.startedAt,
            "stoppedAt" to s.stoppedAt,
            "exitCode" to s.exitCode,
            "message" to s.message,
            "logs" to s.logs,
        )

    private fun toDto(c: McpServerConfigView): McpServerConfigDto =
        McpServerConfigDto(
            id = c.id,
            name = c.name,
            baseUrl = c.baseUrl,
            authToken = "",
            authTokenSet = c.secrets.authTokenSet,
            active = c.active,
            routeJira = c.routeJira,
            routeConfluence = c.routeConfluence,
            routeGitlab = c.routeGitlab,
            routeGithub = c.routeGithub,
            routeGrafana = c.routeGrafana,
            routeDatabase = c.routeDatabase,
            jiraBaseUrl = c.jiraBaseUrl,
            jiraUsername = c.jiraUsername,
            jiraApiToken = "",
            jiraApiTokenSet = c.secrets.jiraApiTokenSet,
            jiraCloudMode = c.jiraCloudMode,
            confluenceBaseUrl = c.confluenceBaseUrl,
            confluenceUsername = c.confluenceUsername,
            confluenceApiToken = "",
            confluenceApiTokenSet = c.secrets.confluenceApiTokenSet,
            confluenceCloudMode = c.confluenceCloudMode,
            gitlabBaseUrl = c.gitlabBaseUrl,
            gitlabAccessToken = "",
            gitlabAccessTokenSet = c.secrets.gitlabAccessTokenSet,
            githubApiBase = c.githubApiBase,
            githubToken = "",
            githubTokenSet = c.secrets.githubTokenSet,
            grafanaBaseUrl = c.grafanaBaseUrl,
            grafanaApiKey = "",
            grafanaApiKeySet = c.secrets.grafanaApiKeySet,
            autoStartManaged = c.autoStartManaged,
        )

    private fun McpServerConfigDto.toChanges(): McpServerConfigChanges =
        McpServerConfigChanges(
            name = name,
            baseUrl = baseUrl,
            authToken = authToken,
            active = active,
            routeJira = routeJira,
            routeConfluence = routeConfluence,
            routeGitlab = routeGitlab,
            routeGithub = routeGithub,
            routeGrafana = routeGrafana,
            routeDatabase = routeDatabase,
            jiraBaseUrl = jiraBaseUrl,
            jiraUsername = jiraUsername,
            jiraApiToken = jiraApiToken,
            jiraCloudMode = jiraCloudMode,
            confluenceBaseUrl = confluenceBaseUrl,
            confluenceUsername = confluenceUsername,
            confluenceApiToken = confluenceApiToken,
            confluenceCloudMode = confluenceCloudMode,
            gitlabBaseUrl = gitlabBaseUrl,
            gitlabAccessToken = gitlabAccessToken,
            githubApiBase = githubApiBase,
            githubToken = githubToken,
            grafanaBaseUrl = grafanaBaseUrl,
            grafanaApiKey = grafanaApiKey,
            autoStartManaged = autoStartManaged,
        )
}
