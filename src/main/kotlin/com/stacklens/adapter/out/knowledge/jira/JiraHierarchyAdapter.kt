package com.stacklens.adapter.out.knowledge.jira

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.application.port.out.knowledge.JiraHierarchyPort
import com.stacklens.application.port.out.knowledge.JiraIssueSnapshot
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.text.AcceptanceCriteriaExtractor
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.bearerOrBasicAuthHeader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import java.util.ArrayDeque

@Component
class JiraHierarchyAdapter(
    private val httpFactory: ReactiveHttpClientFactory,
    private val apiVersionResolver: JiraApiVersionResolver,
) : JiraHierarchyPort {
    private val log = LoggerFactory.getLogger(JiraHierarchyAdapter::class.java)

    override fun ensureHostMatches(
        config: JiraConfig,
        rootTicketKeyOrUrl: String,
    ) {
        extractTicketKey(rootTicketKeyOrUrl) ?: error("Ticket key is not a valid Jira key: $rootTicketKeyOrUrl")
        if (rootTicketKeyOrUrl.startsWith("http", ignoreCase = true)) {
            val baseHost = hostOf(config.baseUrl) ?: error("Jira base URL is not a valid absolute URL: ${config.baseUrl}")
            val pageHost = hostOf(rootTicketKeyOrUrl) ?: error("Jira ticket URL is not a valid absolute URL: $rootTicketKeyOrUrl")
            require(baseHost.equals(pageHost, ignoreCase = true)) {
                "Jira ticket host '$pageHost' does not match the configured base URL host '$baseHost'. " +
                    "Pick a ticket that lives on the configured Jira instance."
            }
        }
    }

    override fun walkHierarchy(
        config: JiraConfig,
        rootTicketKeyOrUrl: String,
        includeChildren: Boolean,
        includeComments: Boolean,
        maxDepth: Int,
        maxIssues: Int,
        includeLinkedTickets: Boolean,
        includeParent: Boolean,
    ): Sequence<JiraIssueSnapshot> =
        sequence {
            val baseUrl = config.baseUrl.trimEnd('/')
            val apiVersion = apiVersionResolver.apiVersion(config)
            val client =
                httpFactory
                    .builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Accept", "application/json")
                    .defaultHeader("Authorization", bearerOrBasicAuthHeader(config.username, config.apiToken))
                    .build()

            val rootKey = extractTicketKey(rootTicketKeyOrUrl) ?: return@sequence
            val queue: ArrayDeque<IssueCursor> = ArrayDeque()
            queue.add(IssueCursor(key = rootKey, depth = 0))
            val seen = HashSet<String>()
            var emitted = 0

            while (queue.isNotEmpty()) {
                if (emitted >= maxIssues) {
                    log.warn("Jira walk hit maxIssues={}, stopping", maxIssues)
                    break
                }
                val cursor = queue.removeFirst()
                if (!seen.add(cursor.key)) continue
                val issue = loadIssue(client, apiVersion, cursor.key) ?: continue
                val snapshot = toSnapshot(config, issue, includeComments, cursor.depth)
                yield(snapshot)
                emitted++

                val canEnqueueChildren = includeChildren && cursor.depth < maxDepth
                val canEnqueueLinks = includeLinkedTickets && cursor.depth < maxDepth
                val canEnqueueParent = includeParent && cursor.depth < maxDepth

                if (!canEnqueueChildren && !canEnqueueLinks && !canEnqueueParent) continue

                if (canEnqueueChildren) {
                    for (child in issue.childKeys) {
                        if (child.isNotBlank() && child != issue.key) {
                            queue.addLast(IssueCursor(key = child, depth = cursor.depth + 1))
                        }
                    }
                }
                if (canEnqueueLinks) {
                    for (linked in issue.linkedKeys) {
                        if (linked != issue.key) {
                            queue.addLast(IssueCursor(key = linked, depth = cursor.depth + 1))
                        }
                    }
                }
                if (canEnqueueParent) {
                    val parent = issue.parentKey
                    if (parent != null && parent.isNotBlank() && parent != issue.key) {
                        queue.addLast(IssueCursor(key = parent, depth = cursor.depth + 1))
                    }
                }
            }
        }

    private fun loadIssue(
        client: WebClient,
        apiVersion: String,
        key: String,
    ): IssueResponse? =
        try {
            val fields =
                "summary,description,issuetype,status,priority,labels,comment,subtasks,parent,issuelinks"
            client
                .get()
                .uri("/rest/api/$apiVersion/issue/$key?fields=$fields")
                .retrieve()
                .bodyToMono<JsonNode>()
                .block(Duration.ofSeconds(30))
                ?.let { IssueResponse.from(it) }
        } catch (e: WebClientResponseException) {
            log.warn(
                "Jira loadIssue({}) failed: HTTP {} {}",
                key,
                e.statusCode.value(),
                e.statusText,
            )
            null
        } catch (e: Exception) {
            log.warn("Jira loadIssue({}) failed: {}: {}", key, e.javaClass.simpleName, e.message)
            null
        }

    private fun toSnapshot(
        config: JiraConfig,
        issue: IssueResponse,
        includeComments: Boolean,
        depth: Int,
    ): JiraIssueSnapshot {
        val descriptionText = flattenAdf(issue.description)
        val commentsText =
            if (includeComments) {
                issue.comments.joinToString(separator = "\n---\n") { c ->
                    val author = c.author
                    val body = flattenAdf(c.body).take(2000)
                    "$author: $body"
                }
            } else {
                ""
            }
        val ac = AcceptanceCriteriaExtractor.extract(descriptionText)
        val sb = StringBuilder()
        sb.append("# ${issue.key} – ${issue.summary}\n\n")
        sb.append("Type: ${issue.issueType.ifBlank { "Issue" }}\n")
        if (issue.status.isNotBlank()) sb.append("Status: ${issue.status}\n")
        if (issue.priority.isNotBlank()) sb.append("Priority: ${issue.priority}\n")
        if (issue.labels.isNotEmpty()) sb.append("Labels: ${issue.labels.joinToString(", ")}\n")
        if (issue.parentKey != null) sb.append("Parent: ${issue.parentKey}\n")
        if (issue.childKeys.isNotEmpty()) sb.append("Subtasks: ${issue.childKeys.joinToString(", ")}\n")
        if (issue.linkedKeys.isNotEmpty()) {
            sb.append("Linked: ${issue.linkedKeys.joinToString(", ")}\n")
        }
        sb.append("\n## Description\n")
        if (descriptionText.isNotBlank()) sb.append(descriptionText.trim()) else sb.append("(no description)")
        if (ac.isNotEmpty()) {
            sb.append("\n\n## Acceptance Criteria\n")
            ac.forEach { sb.append("- ").append(it).append('\n') }
        }
        if (commentsText.isNotBlank()) {
            sb.append("\n## Comments\n").append(commentsText)
        }
        return JiraIssueSnapshot(
            key = issue.key,
            title = "${issue.key} – ${issue.summary}",
            sourceUri = "${config.baseUrl.trimEnd('/')}/browse/${issue.key}",
            plainText = sb.toString().trim(),
            depth = depth,
        )
    }

    private fun flattenAdf(node: JsonNode?): String {
        if (node == null || node.isNull) return ""
        if (node.isTextual) return node.asText()
        val out = StringBuilder()
        walkAdf(node, out)
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun walkAdf(
        node: JsonNode,
        out: StringBuilder,
    ) {
        if (node.isTextual) {
            out.append(node.asText())
            return
        }
        val textNode = node.get("text")
        if (textNode != null && textNode.isTextual) {
            out.append(textNode.asText())
        }
        val content = node.get("content")
        if (content != null && content.isArray) {
            content.forEachIndexed { i, child ->
                if (i > 0 && (child.path("type").asText("") in listOf("paragraph", "heading", "listItem", "blockquote"))) {
                    out.append(' ')
                }
                walkAdf(child, out)
                if (child.path("type").asText("") == "paragraph") out.append('\n')
            }
        }
    }

    internal fun extractTicketKey(input: String): String? {
        val match = Regex("([A-Z][A-Z0-9_]+-\\d+)", RegexOption.IGNORE_CASE).find(input.trim()) ?: return null
        return match.groupValues[1].uppercase()
    }

    private fun hostOf(url: String): String? =
        try {
            URI(url).host?.lowercase()
        } catch (_: URISyntaxException) {
            null
        }

    private data class IssueCursor(
        val key: String,
        val depth: Int,
    )

    private data class CommentBlock(
        val author: String,
        val body: JsonNode,
    )

    private data class IssueResponse(
        val key: String,
        val summary: String,
        val issueType: String,
        val status: String,
        val priority: String,
        val labels: List<String>,
        val description: JsonNode?,
        val comments: List<CommentBlock>,
        val parentKey: String?,
        val childKeys: List<String>,
        val linkedKeys: List<String>,
    ) {
        companion object {
            fun from(node: JsonNode): IssueResponse {
                val fields = node.path("fields")
                val comments = fields.path("comment").path("comments")
                val commentList =
                    if (comments.isArray) {
                        comments.mapNotNull { c ->
                            val author =
                                c.path("author").path("displayName").asText(null)
                                    ?: c.path("author").path("name").asText(null)
                                    ?: "Unbekannt"
                            val body = c.path("body")
                            if (body.isMissingNode || body.isNull) null else CommentBlock(author, body)
                        }
                    } else {
                        emptyList()
                    }
                val parent = fields.path("parent").path("key").asText(null)
                val subtasks = fields.path("subtasks")
                val childKeys =
                    if (subtasks.isArray) {
                        subtasks.mapNotNull { it.path("key").asText(null) }
                    } else {
                        emptyList()
                    }
                val linkedKeys = parseLinkedKeys(fields.path("issuelinks"))
                return IssueResponse(
                    key = node.path("key").asText(""),
                    summary = fields.path("summary").asText(""),
                    issueType = fields.path("issuetype").path("name").asText(""),
                    status = fields.path("status").path("name").asText(""),
                    priority = fields.path("priority").path("name").asText(""),
                    labels =
                        fields.path("labels").let { l ->
                            if (l.isArray) l.mapNotNull { it.asText(null) } else emptyList()
                        },
                    description = fields.path("description").takeIf { !it.isMissingNode && !it.isNull },
                    comments = commentList,
                    parentKey = parent,
                    childKeys = childKeys,
                    linkedKeys = linkedKeys,
                )
            }

            private fun parseLinkedKeys(node: JsonNode): List<String> {
                if (!node.isArray) return emptyList()
                val seen = LinkedHashSet<String>()
                for (link in node) {
                    val outward = link.path("outwardIssue").path("key").asText(null)
                    val inward = link.path("inwardIssue").path("key").asText(null)
                    if (!outward.isNullOrBlank()) seen.add(outward)
                    if (!inward.isNullOrBlank()) seen.add(inward)
                }
                return seen.toList()
            }
        }
    }
}
