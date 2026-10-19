package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraCreateField
import com.stacklens.domain.search.KeywordContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component("ticketCreationNativeJiraAdapter")
class TicketCreationNativeJiraAdapter(
    private val jiraTicketHttp: JiraTicketHttpClient,
    private val markdownConverter: JiraMarkdownConverter = JiraMarkdownConverter(),
    private val apiVersionResolver: JiraApiVersionResolver = DefaultJiraApiVersionResolver(),
) : TicketCreationJiraPort {
    private val log = LoggerFactory.getLogger(TicketCreationNativeJiraAdapter::class.java)

    private val fieldsFilledByStacklens =
        setOf("project", "summary", "issuetype", "description", "labels", "priority", "parent", "reporter")

    override fun createTicket(
        config: JiraConfig,
        projectKey: String,
        summary: String,
        description: String,
        issueType: String,
        labels: List<String>,
        priority: String,
        customFields: Map<String, Any>,
    ): CreatedJiraTicket {
        val apiVersion = apiVersionResolver.apiVersion(config)
        val client = jiraTicketHttp.buildClient(config)
        val fields = buildFieldsMap(projectKey, summary, description, issueType, labels, priority, config.cloudMode)
        customFields.forEach { (fieldId, value) -> fields[fieldId] = toOptionPayload(value) }

        log.info(
            "[TicketCreation] Creating Jira issue in project={}, type={}, cloudMode={}",
            projectKey,
            issueType,
            config.cloudMode,
        )

        val response =
            client
                .post()
                .uri("/rest/api/$apiVersion/issue")
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(mapOf("fields" to fields)))
                .retrieve()
                .bodyToMono<Map<*, *>>()
                .block() ?: throw IllegalStateException("Jira returned no response.")

        val key = response["key"] as? String ?: ""
        val id = response["id"]?.toString() ?: ""
        val url = if (key.isNotBlank()) "${config.baseUrl.trimEnd('/')}/browse/$key" else ""
        return CreatedJiraTicket(key = key, id = id, url = url)
    }

    override fun createSubtask(
        config: JiraConfig,
        parentKey: String,
        projectKey: String,
        summary: String,
        description: String,
        issueType: String,
        labels: List<String>,
        priority: String,
    ): CreatedJiraTicket {
        val apiVersion = apiVersionResolver.apiVersion(config)
        val client = jiraTicketHttp.buildClient(config)
        val fields = buildSubtaskFieldsMap(parentKey, projectKey, summary, description, issueType, labels, priority, config.cloudMode)

        log.info(
            "[TicketCreation] Creating Jira subtask under parent={}, project={}, type={}, cloudMode={}",
            parentKey,
            projectKey,
            issueType,
            config.cloudMode,
        )

        val response =
            try {
                postIssue(client, apiVersion, fields, "subtask under $parentKey")
            } catch (e: WebClientResponseException) {
                if (!config.cloudMode || e.statusCode.value() != 400) throw e
                val issueTypeId = resolveCloudSubtaskIssueTypeId(client, projectKey, issueType) ?: throw e
                log.info(
                    "[TicketCreation] Retrying Jira subtask with project-specific issueTypeId={} after name '{}' was rejected",
                    issueTypeId,
                    issueType,
                )
                fields["issuetype"] = mapOf("id" to issueTypeId)
                postIssue(client, apiVersion, fields, "subtask under $parentKey")
            }

        val key = response["key"] as? String ?: ""
        val id = response["id"]?.toString() ?: ""
        val url = if (key.isNotBlank()) "${config.baseUrl.trimEnd('/')}/browse/$key" else ""
        return CreatedJiraTicket(key = key, id = id, url = url)
    }

    override fun requiredCreateFields(
        config: JiraConfig,
        projectKey: String,
        issueType: String,
    ): List<JiraCreateField> {
        val apiVersion = apiVersionResolver.apiVersion(config)
        val client = jiraTicketHttp.buildClient(config)
        val base = "/rest/api/$apiVersion/issue/createmeta/${KeywordContext.encode(projectKey)}/issuetypes"

        val issueTypes = pageEntries(getJson(client, "$base?maxResults=$CREATEMETA_PAGE_SIZE"), "issueTypes")
        val issueTypeId =
            issueTypes
                .firstOrNull { (it["name"] as? String)?.equals(issueType, ignoreCase = true) == true }
                ?.get("id")
                ?.toString()
                ?: return emptyList()

        val fields = mutableListOf<Map<*, *>>()
        var startAt = 0
        repeat(CREATEMETA_MAX_PAGES) {
            val page = getJson(client, "$base/$issueTypeId?startAt=$startAt&maxResults=$CREATEMETA_PAGE_SIZE")
            val entries = pageEntries(page, "fields")
            fields += entries
            startAt += entries.size
            val total = (page["total"] as? Number)?.toInt()
            val more = if (total != null) startAt < total else page["isLast"] == false
            if (entries.isEmpty() || !more) return fields.mapNotNull(::toCreateField)
        }
        log.warn("[TicketCreation] createmeta for {} / {} exceeded {} pages", projectKey, issueType, CREATEMETA_MAX_PAGES)
        return fields.mapNotNull(::toCreateField)
    }

    private fun getJson(
        client: WebClient,
        uri: String,
    ): Map<*, *> =
        client
            .get()
            .uri(uri)
            .retrieve()
            .bodyToMono<Map<*, *>>()
            .block() ?: emptyMap<String, Any>()

    private fun pageEntries(
        page: Map<*, *>,
        cloudKey: String,
    ): List<Map<*, *>> = ((page[cloudKey] ?: page["values"]) as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

    private fun toCreateField(raw: Map<*, *>): JiraCreateField? {
        if (raw["required"] != true) return null
        val fieldId = raw["fieldId"] as? String ?: raw["key"] as? String ?: return null
        if (fieldId in fieldsFilledByStacklens) return null
        val schema = raw["schema"] as? Map<*, *> ?: return null
        val multiple =
            when {
                schema["type"] == "option" -> false
                schema["type"] == "array" && schema["items"] == "option" -> true
                else -> return null
            }
        val allowedValues =
            (raw["allowedValues"] as? List<*>)
                ?.mapNotNull { (it as? Map<*, *>)?.get("value") as? String }
                .orEmpty()
        return JiraCreateField(fieldId, raw["name"] as? String ?: fieldId, multiple, allowedValues)
    }

    private fun toOptionPayload(value: Any): Any =
        when (value) {
            is List<*> -> value.map { mapOf("value" to it.toString()) }
            else -> mapOf("value" to value.toString())
        }

    internal fun buildFieldsMap(
        projectKey: String,
        summary: String,
        description: String,
        issueType: String,
        labels: List<String>,
        priority: String,
        cloudMode: Boolean,
    ): MutableMap<String, Any> {
        val fields =
            mutableMapOf<String, Any>(
                "project" to mapOf("key" to projectKey),
                "summary" to summary,
                "issuetype" to mapOf("name" to issueType),
            )
        if (description.isNotBlank()) {
            fields["description"] =
                if (cloudMode) {
                    markdownConverter.markdownToAdf(description)
                } else {
                    markdownConverter.markdownToJiraWiki(description)
                }
        }
        if (labels.isNotEmpty()) fields["labels"] = labels
        if (priority.isNotBlank()) fields["priority"] = mapOf("name" to priority)
        return fields
    }

    internal fun buildSubtaskFieldsMap(
        parentKey: String,
        projectKey: String,
        summary: String,
        description: String,
        issueType: String,
        labels: List<String>,
        priority: String,
        cloudMode: Boolean,
        issueTypeId: String? = null,
    ): MutableMap<String, Any> {
        val issueTypeReference =
            if (issueTypeId.isNullOrBlank()) {
                mapOf("name" to issueType)
            } else {
                mapOf("id" to issueTypeId)
            }
        val fields =
            mutableMapOf<String, Any>(
                "project" to mapOf("key" to projectKey),
                "summary" to summary,
                "issuetype" to issueTypeReference,
                "parent" to mapOf("key" to parentKey),
            )
        if (description.isNotBlank()) {
            fields["description"] =
                if (cloudMode) {
                    markdownToAdf(description)
                } else {
                    markdownToWikiMarkup(description)
                }
        }
        if (labels.isNotEmpty()) fields["labels"] = labels
        if (priority.isNotBlank()) fields["priority"] = mapOf("name" to priority)
        return fields
    }

    private fun postIssue(
        client: WebClient,
        apiVersion: String,
        fields: Map<String, Any>,
        responseContext: String,
    ): Map<*, *> =
        client
            .post()
            .uri("/rest/api/$apiVersion/issue")
            .header("Content-Type", "application/json")
            .body(BodyInserters.fromValue(mapOf("fields" to fields)))
            .retrieve()
            .bodyToMono<Map<*, *>>()
            .block() ?: throw IllegalStateException("Jira returned no response for $responseContext.")

    private fun resolveCloudSubtaskIssueTypeId(
        client: WebClient,
        projectKey: String,
        requestedName: String,
    ): String? {
        val metadata =
            runCatching {
                client
                    .get()
                    .uri("/rest/api/3/issue/createmeta/${KeywordContext.encode(projectKey)}/issuetypes?maxResults=100")
                    .retrieve()
                    .bodyToMono<Map<*, *>>()
                    .block()
            }.onFailure {
                log.warn("[TicketCreation] Could not resolve Jira subtask issue type for project {}: {}", projectKey, it.message)
            }.getOrNull() ?: return null
        return findSubtaskIssueTypeId(metadata, requestedName)
    }

    internal fun findSubtaskIssueTypeId(
        metadata: Map<*, *>,
        requestedName: String,
    ): String? {
        val subtaskTypes =
            (metadata["issueTypes"] as? List<*>)
                ?.mapNotNull { it as? Map<*, *> }
                ?.filter { it["subtask"] == true }
                .orEmpty()
        val selected =
            subtaskTypes.firstOrNull { (it["name"] as? String)?.equals(requestedName, ignoreCase = true) == true }
                ?: subtaskTypes.firstOrNull()
        return selected?.get("id")?.toString()?.takeIf { it.isNotBlank() }
    }

    internal fun markdownToAdf(markdown: String): Map<String, Any> = markdownConverter.markdownToAdf(markdown)

    internal fun inlineToAdf(text: String): List<Map<String, Any>> = markdownConverter.inlineToAdf(text)

    internal fun markdownToWikiMarkup(markdown: String): String = markdownConverter.markdownToJiraWiki(markdown)

    private companion object {
        const val CREATEMETA_PAGE_SIZE = 200
        const val CREATEMETA_MAX_PAGES = 20
    }
}
