package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.AdfCommentPreparer
import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.search.KeywordContext
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class AdfDescriptionUpdater(
    private val jiraTicketHttp: JiraTicketHttpClient,
    private val objectMapper: ObjectMapper,
    private val mdConverter: JiraMarkdownConverter,
    private val apiVersionResolver: JiraApiVersionResolver = DefaultJiraApiVersionResolver(),
) {
    private val adfCommentPreparer = AdfCommentPreparer(objectMapper)

    fun prependGreeting(
        adfJson: String,
        greeting: String,
        openPointsLabel: String,
        openPoints: List<String>,
    ): String = adfCommentPreparer.prependGreeting(adfJson, greeting, openPointsLabel, openPoints)

    fun append(
        config: JiraConfig,
        targetKey: String,
        adfDoc: Map<String, Any?>,
    ): String {
        val client = jiraTicketHttp.buildClient(config)
        val v = apiVersionResolver.apiVersion(config)

        val current = fetchCurrentDescription(client, v, targetKey)

        if (config.cloudMode) {
            val existingContent: List<Map<String, Any?>> = current.adfContent
            val newContent: List<Map<String, Any?>> = adfContentAsMapList(adfDoc["content"])
            val merged =
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to existingContent + newContent,
                )
            val payload = mapOf("fields" to mapOf("description" to merged))
            putDescription(client, v, targetKey, payload)
            return objectMapper.writeValueAsString(merged)
        } else {
            val wikiMd = adfToMarkdown(adfDoc)
            val wikiAppend = mdConverter.markdownToJiraWiki(wikiMd)
            val separator = if (current.wiki.isBlank()) "" else "\n\n----\n\n"
            val merged = "${current.wiki}$separator$wikiAppend"
            val payload = mapOf("fields" to mapOf("description" to merged))
            putDescription(client, v, targetKey, payload)
            return merged
        }
    }

    private fun fetchCurrentDescription(
        client: WebClient,
        v: String,
        targetKey: String,
    ): CurrentDescription {
        val node =
            runCatching {
                client
                    .get()
                    .uri("/rest/api/$v/issue/${KeywordContext.encode(targetKey)}?fields=description")
                    .retrieve()
                    .bodyToMono<JsonNode>()
                    .block()
            }.getOrNull() ?: return CurrentDescription(adfContent = emptyList(), wiki = "")
        val descNode = node.path("fields").path("description")
        if (descNode.isMissingNode || descNode.isNull) return CurrentDescription(emptyList(), "")
        return if (descNode.isObject) {
            val content = adfContentAsMapList(descNode.path("content"))
            CurrentDescription(adfContent = content, wiki = "")
        } else {
            CurrentDescription(adfContent = emptyList(), wiki = descNode.asText(""))
        }
    }

    private fun adfContentAsMapList(raw: Any?): List<Map<String, Any?>> =
        when (raw) {
            null -> emptyList()
            is List<*> -> raw.filterIsInstance<Map<String, Any?>>()
            is JsonNode -> adfContentAsMapListFromArrayNode(raw)
            else -> emptyList()
        }

    private fun adfContentAsMapListFromArrayNode(node: JsonNode): List<Map<String, Any?>> {
        if (!node.isArray) return emptyList()
        val out = mutableListOf<Map<String, Any?>>()
        for (child in node) {
            if (!child.isObject) continue
            val converted =
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    objectMapper.treeToValue(child, Map::class.java) as Map<String, Any?>
                }.getOrNull() ?: continue
            out += converted
        }
        return out
    }

    private fun putDescription(
        client: WebClient,
        v: String,
        targetKey: String,
        payload: Map<String, Any?>,
    ) {
        try {
            client
                .put()
                .uri("/rest/api/$v/issue/${KeywordContext.encode(targetKey)}")
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block()
        } catch (e: WebClientResponseException) {
            throw IllegalStateException(
                "Jira antwortete mit HTTP ${e.statusCode.value()} beim Aktualisieren der Beschreibung von $targetKey: " +
                    e.responseBodyAsString.take(200),
                e,
            )
        }
    }

    private fun adfToMarkdown(doc: Map<String, Any?>): String {
        val sb = StringBuilder()
        val content = (doc["content"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
        for (node in content) {
            renderAdfNode(node, sb)
            sb.append('\n')
        }
        return sb.toString().trim()
    }

    private fun renderAdfNode(
        node: Map<String, Any?>,
        sb: StringBuilder,
    ) {
        when (node["type"]) {
            "heading" -> {
                val level = (node["attrs"] as? Map<*, *>)?.get("level") as? Number ?: 2
                sb.append("#".repeat((level.toInt()).coerceIn(1, 3))).append(' ')
                appendInline(node, sb)
                sb.append("\n\n")
            }
            "paragraph" -> {
                appendInline(node, sb)
                sb.append("\n\n")
            }
            "rule" -> sb.append("---\n\n")
            "bulletList" -> {
                val items = (node["content"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
                for (item in items) {
                    sb.append("- ")
                    val firstPara =
                        ((item["content"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>().firstOrNull())
                    if (firstPara != null) {
                        appendInline(firstPara, sb)
                    }
                    sb.append('\n')
                }
                sb.append('\n')
            }
            "table" -> {
                val rows = (node["content"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
                if (rows.isEmpty()) return
                for (r in rows) {
                    val cells = (r["content"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
                    val rowTexts =
                        cells.joinToString(" | ") { cell ->
                            (
                                (cell["content"] as? List<*>)
                                    .orEmpty()
                                    .filterIsInstance<Map<String, Any?>>()
                                    .joinToString(" ") { inner -> flattenInline(inner) }
                            )
                        }
                    sb.append("| ").append(rowTexts).append(" |\n")
                }
                sb.append('\n')
            }
            else -> {
                val inner = (node["content"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
                for (c in inner) renderAdfNode(c, sb)
            }
        }
    }

    private fun appendInline(
        node: Map<String, Any?>,
        sb: StringBuilder,
    ) {
        val inline = (node["content"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
        for (n in inline) appendInlineNode(n, sb)
    }

    private fun appendInlineNode(
        node: Map<String, Any?>,
        sb: StringBuilder,
    ) {
        when (node["type"]) {
            "text" -> {
                val text = node["text"]?.toString().orEmpty()
                val marks = (node["marks"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
                var s = text
                for (m in marks) {
                    when (m["type"]) {
                        "strong" -> s = "**$s**"
                        "em" -> s = "_${s}_"
                        "link" -> {
                            val href = (m["attrs"] as? Map<*, *>)?.get("href")?.toString().orEmpty()
                            if (href.isNotBlank()) s = "[$s]($href)"
                        }
                    }
                }
                sb.append(s)
            }
            "hardBreak" -> sb.append('\n')
            else -> appendInline(node, sb)
        }
    }

    private fun flattenInline(node: Map<String, Any?>): String {
        val sb = StringBuilder()
        appendInline(node, sb)
        return sb.toString().trim()
    }

    private data class CurrentDescription(
        val adfContent: List<Map<String, Any?>>,
        val wiki: String,
    )
}
