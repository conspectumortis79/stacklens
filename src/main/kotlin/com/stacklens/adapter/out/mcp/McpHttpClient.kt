package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpPingResult
import com.stacklens.application.port.out.McpToolDescriptor
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.McpServerConfig
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Component
open class McpHttpClient(
    private val webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper,
    private val databaseConfigs: DatabaseConfigPort,
    private val circuitBreaker: McpCircuitBreaker,
) : McpGatewayPort {
    private val log = LoggerFactory.getLogger(McpHttpClient::class.java)

    private val firstFailureHintedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun listTools(config: McpServerConfig): List<McpToolDescriptor> {
        val body =
            client(config)
                .get()
                .uri("/tools")
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block(Duration.ofSeconds(15))
                ?: return emptyList()
        val arr = body.path("tools")
        if (!arr.isArray) return emptyList()
        return arr.map {
            McpToolDescriptor(
                name = it.path("name").asText(""),
                description = it.path("description").asText(""),
            )
        }
    }

    override fun callTool(
        config: McpServerConfig,
        tool: String,
        args: Map<String, Any?>,
    ): McpToolResult {
        val circuitKey = circuitBreaker.keyFor(config.id, tool)
        if (!circuitBreaker.isAllowed(circuitKey)) {
            log.debug("[MCP] circuit open for {} – skipping MCP, native fallback will be used", circuitKey)
            return McpToolResult(text = "MCP circuit open for $circuitKey", isError = true)
        }

        val payload = mapOf("tool" to tool, "args" to args)
        val result =
            try {
                val body =
                    client(config)
                        .post()
                        .uri("/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(payload))
                        .retrieve()
                        .bodyToMono(JsonNode::class.java)
                        .block(Duration.ofSeconds(120))
                        ?: run {
                            recordFailureAndMaybeHint(circuitKey, tool)
                            return McpToolResult("(empty)", isError = true)
                        }
                extractToolText(body)
            } catch (e: WebClientResponseException) {
                log.warn("[MCP] tool {} failed: HTTP {}: {}", tool, e.statusCode, e.responseBodyAsString)
                McpToolResult(
                    text = "MCP tool '$tool' HTTP ${e.statusCode.value()}: ${e.responseBodyAsString.take(500)}",
                    isError = true,
                )
            } catch (e: Exception) {
                log.warn("[MCP] tool {} failed: {}", tool, e.message)
                McpToolResult(text = "MCP tool '$tool' failed: ${e.message}", isError = true)
            }

        if (result.isError) {
            recordFailureAndMaybeHint(circuitKey, tool)
        } else {
            circuitBreaker.recordSuccess(circuitKey)
            firstFailureHintedKeys.remove(circuitKey)
        }
        return result
    }

    private fun recordFailureAndMaybeHint(
        circuitKey: String,
        tool: String,
    ) {
        circuitBreaker.recordFailure(circuitKey)
        if (firstFailureHintedKeys.add(circuitKey)) {
            val provider = circuitBreaker.providerOf(tool)
            log.warn(
                "[MCP] Native fallback engaged for provider '{}' (circuit '{}', tool '{}'). " +
                    "StackLens continues to work using the built-in adapter for this provider. " +
                    "Open Connections \u2192 MCP in the UI to start or reconfigure the bundled MCP server, " +
                    "or restart the Docker 'stacklens-mcp' container if you are running in Docker. " +
                    "This hint will be re-emitted after the circuit recovers and a new failure occurs.",
                provider,
                circuitKey,
                tool,
            )
        }
    }

    override fun ping(config: McpServerConfig): McpPingResult =
        try {
            val body =
                client(config)
                    .get()
                    .uri("/healthz")
                    .retrieve()
                    .bodyToMono(JsonNode::class.java)
                    .block(Duration.ofSeconds(10))
            val ok = body?.path("ok")?.asBoolean(false) == true
            val tools = if (ok) listTools(config).size else 0
            McpPingResult(
                ok = ok,
                message = if (ok) "OK – ${body.path("name").asText("mcp")}" else "unhealthy",
                toolCount = tools,
            )
        } catch (e: Exception) {
            McpPingResult(ok = false, message = e.message ?: e.javaClass.simpleName)
        }

    internal fun resolveBaseUrl(url: String): String =
        url
            .replace("://localhost:", "://127.0.0.1:")
            .replace("://[::1]:", "://127.0.0.1:")

    internal fun client(config: McpServerConfig): WebClient {
        val builder =
            webClientBuilder
                .clone()
                .baseUrl(resolveBaseUrl(config.baseUrl.trimEnd('/')))
                .defaultHeader("Accept", "application/json")
                .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
        if (config.authToken.isNotBlank()) {
            builder.defaultHeader("Authorization", "Bearer ${config.authToken}")
        }
        builder.defaultHeader("X-Stacklens-Config", buildConfigHeader(config))
        return builder.build()
    }

    internal fun buildConfigHeader(config: McpServerConfig): String {
        val databases =
            if (config.routeDatabase) {
                databaseConfigs.findByActiveTrue().map { d ->
                    mapOf(
                        "name" to d.name,
                        "type" to
                            when (d.dbType) {
                                DatabaseType.MYSQL -> "mysql"
                                DatabaseType.POSTGRESQL -> "postgresql"
                                DatabaseType.MSSQL -> "mssql"
                            },
                        "host" to d.host,
                        "port" to d.port,
                        "database" to d.databaseName,
                        "username" to d.username,
                        "password" to d.password,
                    )
                }
            } else {
                emptyList()
            }
        val payload =
            mapOf(
                "jira" to
                    mapOf(
                        "baseUrl" to config.jiraBaseUrl,
                        "username" to config.jiraUsername,
                        "apiToken" to config.jiraApiToken,
                        "cloudMode" to config.jiraCloudMode,
                    ),
                "confluence" to
                    mapOf(
                        "baseUrl" to config.confluenceBaseUrl,
                        "username" to config.confluenceUsername,
                        "apiToken" to config.confluenceApiToken,
                        "cloudMode" to config.confluenceCloudMode,
                    ),
                "gitlab" to
                    mapOf(
                        "baseUrl" to config.gitlabBaseUrl,
                        "accessToken" to config.gitlabAccessToken,
                    ),
                "github" to
                    mapOf(
                        "apiBase" to config.githubApiBase,
                        "token" to config.githubToken,
                    ),
                "grafana" to
                    mapOf(
                        "baseUrl" to config.grafanaBaseUrl,
                        "apiKey" to config.grafanaApiKey,
                    ),
                "databases" to databases,
            )
        val json = objectMapper.writeValueAsBytes(payload)
        return Base64.getEncoder().encodeToString(json)
    }

    internal fun extractToolText(node: JsonNode): McpToolResult {
        val result = node.path("result")
        val isError = result.path("isError").asBoolean(false)
        val content = result.path("content")
        if (content.isArray && content.size() > 0) {
            val text = content.joinToString("\n") { it.path("text").asText("") }
            if (text.isNotBlank() || !isError) {
                return McpToolResult(text = text, isError = isError)
            }
        }
        if (node.has("error")) {
            return McpToolResult(text = node.path("error").asText(""), isError = true)
        }
        return McpToolResult(text = node.toString(), isError = isError)
    }
}
