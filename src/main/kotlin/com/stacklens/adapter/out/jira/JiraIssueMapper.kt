package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.LocalDateTime

class JiraIssueMapper {
    fun mapIssueToResult(
        issue: JiraClient.JiraIssue,
        config: com.stacklens.domain.model.JiraConfig,
        keyword: String,
        sessionId: Long,
    ): SearchResult {
        val issueType = issue.fields.issuetype?.name ?: "Issue"
        val status = issue.fields.status?.name ?: ""
        val summary = issue.fields.summary ?: ""
        val descExcerpt = extractDescriptionExcerpt(issue.fields.description, keyword, 300)
        val projectName =
            config.projectKey.takeIf { it.isNotBlank() }
                ?: issue.key.substringBefore("-", issue.key)

        val fullDescription = flattenDescriptionNode(issue.fields.description).takeIf { it.isNotBlank() }

        return SearchResult(
            sessionId = sessionId,
            sourceType = SourceType.JIRA,
            resultType = resultTypeFromIssueType(issueType),
            keyword = keyword,
            repoName = projectName,
            configName = config.name,
            repoUrl = "${config.baseUrl.trimEnd('/')}/browse/${issue.key}",
            filePath = issue.key,
            branch = summary,
            className = issueType,
            functionName = status,
            codeSnippet = if (descExcerpt.isNotBlank()) "[$summary]\n\n$descExcerpt" else "[$summary]",
            dbHost = issue.fields.assignee?.displayName ?: issue.fields.assignee?.name,
            databaseName = issue.fields.priority?.name,
            schemaName = issue.fields.updated,
            ddlContext = fullDescription,
            createdAt = LocalDateTime.now(),
        )
    }

    fun extractDescriptionExcerpt(
        description: JsonNode?,
        keyword: String,
        maxLen: Int,
    ): String {
        if (description == null) return ""
        val text = flattenDescriptionNode(description)
        if (text.isBlank()) return ""
        val idx = text.indexOf(keyword, ignoreCase = true)
        if (idx < 0) return ""
        val start = maxOf(0, idx - 80)
        val end = minOf(text.length, idx + keyword.length + 120)
        val raw = text.substring(start, end).trim()
        return if (start > 0) "…$raw" else raw
    }

    fun flattenDescriptionNode(node: JsonNode?): String {
        if (node == null || node.isNull) return ""
        if (node.isTextual) return node.asText()
        return WHITESPACE_COLLAPSE.replace(flattenAdfNode(node), " ").trim()
    }

    private companion object {
        private val WHITESPACE_COLLAPSE = Regex("\\s+")
    }

    fun resultTypeFromIssueType(issueType: String): ResultType =
        when {
            issueType.equals("Bug", ignoreCase = true) -> ResultType.JIRA_BUG
            issueType.equals("Story", ignoreCase = true) -> ResultType.JIRA_STORY
            issueType.equals("Task", ignoreCase = true) || issueType.equals("Technical Task", ignoreCase = true) -> ResultType.JIRA_TASK
            issueType.equals("Epic", ignoreCase = true) -> ResultType.JIRA_EPIC
            else -> ResultType.JIRA_ISSUE
        }

    fun findDrawIoAttachmentResults(
        client: WebClient,
        issue: JiraClient.JiraIssue,
        config: com.stacklens.domain.model.JiraConfig,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val attachments = issue.fields.attachment ?: emptyList()
        val results = mutableListOf<SearchResult>()
        for (a in attachments) {
            val lowerName = a.filename.lowercase()
            val isDrawIo = lowerName.endsWith(".drawio") || lowerName.endsWith(".drawio.xml") || lowerName.contains("drawio")
            if (!isDrawIo) continue
            val filenameHit = lowerName.contains(keyword.lowercase())
            val contentHit =
                if (a.content.isNotBlank()) {
                    fetchAttachmentText(client, a.content).contains(keyword, ignoreCase = true)
                } else {
                    false
                }
            if (!filenameHit && !contentHit) continue
            val summary = if (contentHit) "draw.io diagram contains '$keyword'" else "draw.io filename contains '$keyword'"
            results +=
                SearchResult(
                    sessionId = sessionId,
                    sourceType = SourceType.JIRA,
                    resultType = ResultType.JIRA_ISSUE,
                    keyword = keyword,
                    repoName = config.projectKey,
                    configName = config.name,
                    repoUrl = "${config.baseUrl.trimEnd('/')}/browse/${issue.key}",
                    filePath = issue.key,
                    className = "draw.io",
                    functionName = issue.fields.status?.name,
                    codeSnippet = "[${issue.fields.summary ?: issue.key}]\n\n$summary: ${a.filename}",
                    createdAt = LocalDateTime.now(),
                )
        }
        return results
    }

    private fun fetchAttachmentText(
        client: WebClient,
        attachmentUrl: String,
    ): String =
        client
            .get()
            .uri(attachmentUrl)
            .retrieve()
            .bodyToMono(String::class.java)
            .onErrorResume {
                reactor.core.publisher.Mono
                    .just("")
            }.block() ?: ""
}
