package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementSubtask
import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.search.KeywordContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.bodyToMono

@Component("sprintPrepJiraPort")
class TicketRefinementNativeJiraAdapter(
    private val jiraTicketHttp: JiraTicketHttpClient,
    private val objectMapper: ObjectMapper,
    private val adfToHtml: AdfToHtmlConverter,
    private val mdConverter: JiraMarkdownConverter,
    private val apiVersionResolver: JiraApiVersionResolver = DefaultJiraApiVersionResolver(),
    private val adfDescriptionUpdater: AdfDescriptionUpdater =
        AdfDescriptionUpdater(jiraTicketHttp, objectMapper, mdConverter, apiVersionResolver),
) : TicketRefinementJiraPort {
    private val log = LoggerFactory.getLogger(TicketRefinementNativeJiraAdapter::class.java)

    override fun fetchParentWithLinkedTickets(
        config: JiraConfig?,
        parentKey: String,
    ): List<TicketRefinementTicket> {
        requireNotNull(config) { "JiraConfig is required for native source mode" }
        val client = jiraTicketHttp.buildClient(config)
        val v = apiVersion(config)
        val parentNode =
            client
                .get()
                .uri(
                    "/rest/api/$v/issue/${KeywordContext.encode(parentKey)}?fields=summary,description,comment,issuelinks,subtasks,labels",
                ).retrieve()
                .bodyToMono(JsonNode::class.java)
                .block()
                ?: throw IllegalStateException("Jira returned no response for ticket '$parentKey'.")
        val parent = jsonToTicket(parentNode, config)
        val linkedKeys = linkedKeysFrom(parentNode, parent.description, parent.summary, parentKey)
        val linkedTickets =
            linkedKeys.filter { it != parent.key }.distinct().mapNotNull { key ->
                runCatching { fetchSingle(client, v, key, config) }.getOrNull()
            }
        return listOf(parent) + linkedTickets
    }

    override fun postComment(
        config: JiraConfig?,
        ticketKey: String,
        body: String,
        format: String,
        fallbackMarkdown: String,
        greeting: String,
        openPointsLabel: String,
        openPoints: List<String>,
    ): String {
        requireNotNull(config) { "JiraConfig is required for native source mode" }
        val effectiveBody =
            if (format == "adf" && greeting.isNotBlank()) {
                adfDescriptionUpdater.prependGreeting(body, greeting, openPointsLabel, openPoints)
            } else {
                body
            }
        val client = jiraTicketHttp.buildClient(config)
        val v = apiVersion(config)
        val basePayload: Map<String, Any> =
            try {
                if (config.cloudMode) {
                    val adfNode: JsonNode =
                        when (format) {
                            "adf" -> objectMapper.readTree(effectiveBody).also { require(it.path("type").asText() == "doc") }
                            else ->
                                objectMapper.valueToTree(
                                    mdConverter.markdownToAdf(
                                        if (format ==
                                            "markdown"
                                        ) {
                                            effectiveBody
                                        } else {
                                            fallbackMarkdown
                                        },
                                    ),
                                )
                        }
                    mapOf("body" to adfNode)
                } else {
                    val wiki =
                        when (format) {
                            "wiki" -> effectiveBody
                            "markdown" -> mdConverter.markdownToJiraWiki(effectiveBody)
                            else -> mdConverter.markdownToJiraWiki(fallbackMarkdown)
                        }
                    mapOf("body" to wiki)
                }
            } catch (e: Exception) {
                log.warn(
                    "Native body for {} (format={}) was rejected: {} — falling back to Markdown pipeline.",
                    ticketKey,
                    format,
                    e.message,
                )
                if (config.cloudMode) {
                    mapOf("body" to mdConverter.markdownToAdf(fallbackMarkdown))
                } else {
                    mapOf("body" to mdConverter.markdownToJiraWiki(fallbackMarkdown))
                }
            }
        val payload =
            if (config.commentVisibilityRole.isNotBlank()) {
                basePayload +
                    ("visibility" to mapOf("type" to "role", "value" to config.commentVisibilityRole.trim()))
            } else {
                basePayload
            }
        val response =
            client
                .post()
                .uri("/rest/api/$v/issue/${KeywordContext.encode(ticketKey)}/comment")
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono<JsonNode>()
                .block() ?: throw IllegalStateException("Jira returned no comment for $ticketKey")
        return response.path("id").asText("")
    }

    override fun renderAdfToHtml(adfJson: String?): String? = adfToHtml.render(adfJson)

    internal fun fetchSingle(
        client: org.springframework.web.reactive.function.client.WebClient,
        v: String,
        key: String,
        config: JiraConfig,
    ): TicketRefinementTicket {
        val node =
            client
                .get()
                .uri("/rest/api/$v/issue/${KeywordContext.encode(key)}?fields=summary,description,subtasks,labels")
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw IllegalStateException("no response for $key")
        val ticket = jsonToTicket(node, config)
        val subtaskKeys =
            node
                .path("fields")
                .path("subtasks")
                .mapNotNull {
                    it.path("key").asText("").takeIf { k ->
                        k.isNotBlank()
                    }
                }.take(10)
        val subtasks = subtaskKeys.mapNotNull { runCatching { fetchSubtaskDetails(client, v, it) }.getOrNull() }
        val excludedKeys = setOf(key) + subtaskKeys.toSet()
        val referencedKeys =
            MENTION_REGEX
                .findAll(ticket.summary + "\n" + ticket.description)
                .map {
                    it.value
                }.filter { it !in excludedKeys }
                .distinct()
                .take(5)
                .toList()
        val referencedTickets = referencedKeys.mapNotNull { runCatching { fetchSubtaskDetails(client, v, it) }.getOrNull() }
        return ticket.copy(subtasks = subtasks, referencedTickets = referencedTickets)
    }

    internal fun fetchSubtaskDetails(
        client: org.springframework.web.reactive.function.client.WebClient,
        v: String,
        key: String,
    ): TicketRefinementSubtask {
        val node =
            client
                .get()
                .uri("/rest/api/$v/issue/${KeywordContext.encode(key)}?fields=summary,description")
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw IllegalStateException("no response for subtask $key")
        val fields = node.path("fields")
        return TicketRefinementSubtask(
            key = key,
            summary = fields.path("summary").asText(""),
            description = jiraTicketHttp.flattenDescriptionNode(fields.path("description")),
        )
    }

    internal fun jsonToTicket(
        node: JsonNode,
        config: JiraConfig,
    ): TicketRefinementTicket {
        val key = node.path("key").asText("")
        val fields = node.path("fields")
        val summary = fields.path("summary").asText("")
        val descriptionNode = fields.path("description")
        val description = jiraTicketHttp.flattenDescriptionNode(descriptionNode)
        val descriptionHtml =
            if (config.cloudMode && !descriptionNode.isMissingNode && !descriptionNode.isNull) {
                runCatching { adfToHtml.render(objectMapper.writeValueAsString(descriptionNode)) }.getOrNull()
            } else {
                null
            }
        val (descriptionNative, descriptionFormat) =
            when {
                config.cloudMode && descriptionNode.isObject -> objectMapper.writeValueAsString(descriptionNode) to "adf"
                !config.cloudMode && descriptionNode.isTextual -> {
                    val raw = descriptionNode.asText("")
                    if (mdConverter.looksLikeMarkdown(raw)) "" to "text" else raw to "wiki"
                }
                else -> "" to "text"
            }
        val labels = fields.path("labels").mapNotNull { it.asText("").takeIf { l -> l.isNotBlank() } }
        return TicketRefinementTicket(
            key = key,
            summary = summary,
            description = description,
            url = "${config.baseUrl.trimEnd('/')}/browse/$key",
            descriptionHtml = descriptionHtml,
            descriptionNative = descriptionNative,
            descriptionFormat = descriptionFormat,
            labels = labels,
        )
    }

    internal fun linkedKeysFrom(
        parentNode: JsonNode,
        description: String,
        summary: String,
        parentKey: String,
    ): List<String> {
        val out = LinkedHashSet<String>()
        parentNode.path("fields").path("issuelinks").forEach { link ->
            link
                .path("inwardIssue")
                .path("key")
                .asText("")
                .takeIf { it.isNotBlank() }
                ?.let(out::add)
            link
                .path("outwardIssue")
                .path("key")
                .asText("")
                .takeIf { it.isNotBlank() }
                ?.let(out::add)
        }
        parentNode.path("fields").path("subtasks").forEach { st ->
            st
                .path("key")
                .asText("")
                .takeIf { it.isNotBlank() }
                ?.let(out::add)
        }
        MENTION_REGEX
            .findAll(summary + "\n" + description)
            .map { it.value }
            .filter { it != parentKey }
            .forEach(out::add)
        return out.toList()
    }

    internal fun apiVersion(config: JiraConfig): String = apiVersionResolver.apiVersion(config)

    companion object {
        private val MENTION_REGEX = Regex("[A-Z][A-Z0-9]+-\\d+")
    }
}
