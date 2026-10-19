
package com.stacklens.application.service.analysis

import com.stacklens.application.port.out.ai.AiChatPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.DrawioSupport
import com.stacklens.application.service.MermaidSupport
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession

@ApplicationService
class AiAnalysisPromptBuilder(
    private val promptLoader: PromptReaderPort,
    private val copilot: AiChatPort,
) {
    private val perSourceLimits =
        mapOf(
            SourceType.GITLAB to 60,
            SourceType.PIPELINE to 25,
            SourceType.DATABASE to 50,
            SourceType.CONFLUENCE to 20,
            SourceType.JIRA to 20,
            SourceType.GRAFANA to 20,
        )

    fun buildPrompt(
        session: SearchSession,
        results: List<SearchResult>,
        includeMermaid: Boolean = false,
        includeDrawio: Boolean = false,
    ): String {
        val lang = copilot.resolveLanguage()
        val p = promptLoader.context(lang)
        val bySource = results.groupBy { it.sourceType }
        val keywords = session.keywordList().joinToString(", ") { "`$it`" }
        val cappedBySource =
            bySource.mapValues { (type, rows) ->
                val limit = perSourceLimits[type] ?: rows.size
                rows.take(limit)
            }
        val totalCapped = cappedBySource.values.sumOf { it.size }

        val activeGitlab = cappedBySource[SourceType.GITLAB].orEmpty()
        val activePipeline = cappedBySource[SourceType.PIPELINE].orEmpty()
        val activeDb = cappedBySource[SourceType.DATABASE].orEmpty()
        val activeConfluence = cappedBySource[SourceType.CONFLUENCE].orEmpty()
        val activeJira = cappedBySource[SourceType.JIRA].orEmpty()
        val activeGrafana = cappedBySource[SourceType.GRAFANA].orEmpty()

        val activeSources =
            buildList {
                if (activeGitlab.isNotEmpty()) add("GitLab/Code")
                if (activePipeline.isNotEmpty()) add("CI/CD Pipeline")
                if (activeDb.isNotEmpty()) add("Database")
                if (activeConfluence.isNotEmpty()) add("Confluence")
                if (activeJira.isNotEmpty()) add("Jira")
                if (activeGrafana.isNotEmpty()) add("Grafana")
            }

        return buildString {
            appendHeader(p, keywords, totalCapped, activeSources)
            appendPerSourceDetailSections(p, activeGitlab, activePipeline, activeDb, activeConfluence, activeJira, activeGrafana)
            appendCrossReference(p, activeGitlab, activePipeline, activeDb, activeConfluence, activeJira, activeGrafana)
            appendOutputFormatHeader(p, activeSources.size)
            appendDiagramSections(p, includeMermaid, includeDrawio)
            appendResponsibilities(p, keywords, activeSources, totalCapped)
        }
    }

    private fun StringBuilder.appendHeader(
        p: com.stacklens.application.port.out.prompt.PromptContext,
        keywords: String,
        resultCount: Int,
        activeSources: List<String>,
    ) {
        appendLine(p.get("search.systemRole"))
        appendLine(p.get("common.langInstruction"))
        appendLine(p.get("search.taskDescription"))
        appendLine()
        appendLine(p.get("search.contextHeader"))
        appendLine(p.get("search.keywordsLabel", mapOf("keywords" to keywords)))
        appendLine(p.get("search.hitsLabel", mapOf("count" to "$resultCount")))
        appendLine(p.get("search.activeSourcesLabel", mapOf("sources" to activeSources.joinToString(", "))))
        appendLine()
    }

    private fun StringBuilder.appendPerSourceDetailSections(
        p: com.stacklens.application.port.out.prompt.PromptContext,
        activeGitlab: List<SearchResult>,
        activePipeline: List<SearchResult>,
        activeDb: List<SearchResult>,
        activeConfluence: List<SearchResult>,
        activeJira: List<SearchResult>,
        activeGrafana: List<SearchResult>,
    ) {
        if (activeGitlab.isNotEmpty()) appendSourceSection(p, "search.gitlabHeader", activeGitlab, 60)
        if (activePipeline.isNotEmpty()) appendSourceSection(p, "search.pipelineHeader", activePipeline, 25)
        if (activeDb.isNotEmpty()) appendSourceSection(p, "search.dbHeader", activeDb, 50)
        if (activeConfluence.isNotEmpty()) appendSourceSection(p, "search.confluenceHeader", activeConfluence, 20)
        if (activeJira.isNotEmpty()) appendSourceSection(p, "search.jiraHeader", activeJira, 20)
        if (activeGrafana.isNotEmpty()) appendSourceSection(p, "search.grafanaHeader", activeGrafana, 20)
    }

    private fun StringBuilder.appendSourceSection(
        p: com.stacklens.application.port.out.prompt.PromptContext,
        headerKey: String,
        rows: List<SearchResult>,
        limit: Int,
    ) {
        appendLine(p.get(headerKey, mapOf("count" to "${rows.size}")))
        appendLine(detail(p, rows, limit))
        appendLine()
    }

    private fun detail(
        p: com.stacklens.application.port.out.prompt.PromptContext,
        rows: List<SearchResult>,
        limit: Int,
    ): String =
        rows.take(limit).joinToString("\n") { r ->
            val where =
                listOfNotNull(
                    r.repoName,
                    r.filePath,
                    r.tableName,
                    r.columnName,
                    r.className,
                    r.functionName,
                ).joinToString(" / ")
            "- **${r.keyword}**: ${where.ifBlank { p.get("search.noDetail") }}"
        }

    private fun StringBuilder.appendCrossReference(
        p: com.stacklens.application.port.out.prompt.PromptContext,
        activeGitlab: List<SearchResult>,
        activePipeline: List<SearchResult>,
        activeDb: List<SearchResult>,
        activeConfluence: List<SearchResult>,
        activeJira: List<SearchResult>,
        activeGrafana: List<SearchResult>,
    ) {
        appendLine(p.get("search.entitiesHeader"))
        appendLine(crossRef(p, activeGitlab, activePipeline, activeDb, activeConfluence, activeJira, activeGrafana))
        appendLine()
    }

    private data class Entity(
        val keyword: String,
        val label: String,
        val source: String,
    )

    private fun firstNonBlank(
        vararg candidates: String?,
        fallback: String,
    ): String = candidates.firstOrNull { !it.isNullOrBlank() } ?: fallback

    private fun crossRef(
        p: com.stacklens.application.port.out.prompt.PromptContext,
        activeGitlab: List<SearchResult>,
        activePipeline: List<SearchResult>,
        activeDb: List<SearchResult>,
        activeConfluence: List<SearchResult>,
        activeJira: List<SearchResult>,
        activeGrafana: List<SearchResult>,
    ): String {
        val entities = mutableListOf<Entity>()
        activeGitlab.take(40).forEach {
            entities +=
                Entity(
                    it.keyword,
                    listOfNotNull(it.className ?: it.filePath, it.functionName)
                        .filter { s -> s.isNotBlank() }
                        .joinToString("::")
                        .ifBlank { firstNonBlank(it.filePath, it.repoName, fallback = it.keyword) },
                    "GitLab",
                )
        }
        activePipeline.take(20).forEach {
            entities +=
                Entity(it.keyword, firstNonBlank(it.filePath, it.className, fallback = it.keyword), "Pipeline")
        }
        activeDb.take(40).forEach {
            entities +=
                Entity(
                    it.keyword,
                    listOfNotNull(it.tableName, it.columnName)
                        .filter { s -> s.isNotBlank() }
                        .joinToString(".")
                        .ifBlank { it.keyword },
                    "DB",
                )
        }
        activeConfluence.take(20).forEach {
            entities +=
                Entity(it.keyword, firstNonBlank(it.filePath, fallback = it.keyword), "Confluence")
        }
        activeJira.take(20).forEach {
            entities +=
                Entity(it.keyword, firstNonBlank(it.filePath, fallback = it.keyword), "Jira")
        }
        activeGrafana.take(20).forEach {
            entities +=
                Entity(it.keyword, firstNonBlank(it.repoName, it.filePath, fallback = it.keyword), "Grafana")
        }
        if (entities.isEmpty()) return p.get("search.noEntities")
        return entities.joinToString("\n") { "- [${it.source}] ${it.keyword} → ${it.label}" }
    }

    private fun StringBuilder.appendOutputFormatHeader(
        p: com.stacklens.application.port.out.prompt.PromptContext,
        activeSourceCount: Int,
    ) {
        appendLine(p.get("search.outputFormatHeader"))
        appendLine()
        appendLine(p.get("search.section1"))
        appendLine()
        appendLine(p.get("search.section2.header"))
        if (activeSourceCount >= 2) {
            appendLine(p.get("search.section2.multiSourceBullets"))
        } else {
            appendLine(p.get("search.section2.singleSource"))
        }
        appendLine()
    }

    private fun StringBuilder.appendDiagramSections(
        p: com.stacklens.application.port.out.prompt.PromptContext,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
    ) {
        var sectionNum = 3
        if (includeMermaid) {
            appendLine(p.get("search.mermaidSection", mapOf("sectionNum" to "$sectionNum")))
            appendLine()
            sectionNum++
        }
        if (includeDrawio) {
            appendLine(p.get("search.drawioSection", mapOf("sectionNum" to "$sectionNum")))
            appendLine()
            sectionNum++
        }
    }

    private fun StringBuilder.appendResponsibilities(
        p: com.stacklens.application.port.out.prompt.PromptContext,
        keywords: String,
        activeSources: List<String>,
        resultCount: Int,
    ) {
        var sectionNum = 3
        if (true) {
        }
        appendLine(
            p.get(
                "search.responsibilitiesSection",
                mapOf(
                    "sectionNum" to "$sectionNum",
                    "sources" to activeSources.joinToString(", "),
                ),
            ),
        )
        appendLine()
        appendLine(p.get("search.uncertaintiesSection", mapOf("sectionNum" to "${sectionNum + 1}")))
        appendLine()
        appendLine(p.get("search.importantNote"))
    }

    fun extractMermaid(text: String): String? = MermaidSupport.extract(text)

    fun extractDrawioXml(text: String): String? = DrawioSupport.extract(text)

    fun sanitizeDrawioXml(xml: String): String = DrawioSupport.sanitize(xml)
}
