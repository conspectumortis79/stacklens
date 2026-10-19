package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeSourceUseCase
import com.stacklens.domain.model.jira.JiraTicketKey
import com.stacklens.domain.model.knowledge.KnowledgeSource
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException

@RestController
@RequestMapping("/api/knowledge")
class KnowledgeSourcesController(
    private val sourceUseCase: KnowledgeSourceUseCase,
) {
    @PostMapping("/sources/manual")
    fun createManualSource(
        @RequestBody body: CreateManualSourceRequest,
    ): KnowledgeSource =
        sourceUseCase.createManualSource(
            knowledgeBaseId = body.knowledgeBaseId,
            name = body.name,
            content = body.content,
            externalReference = body.externalReference,
        )

    @PostMapping(value = ["/sources/file"], consumes = ["multipart/form-data"])
    fun uploadFileSource(
        @RequestParam("knowledgeBaseId") knowledgeBaseId: Long,
        @RequestPart("file") file: MultipartFile,
    ): KnowledgeSource {
        val original = file.originalFilename ?: throw IOException("uploaded file has no name")
        return sourceUseCase.createFileSource(knowledgeBaseId, original, file.bytes)
    }

    @PostMapping("/sources/file-local")
    fun uploadLocalFileSource(
        @RequestBody body: LocalFileSourceRequest,
    ): KnowledgeSource {
        val file = File(body.path)
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("Datei nicht gefunden: ${body.path}")
        }
        val lower = file.name.lowercase()
        val allowed = setOf("pdf", "doc", "docx", "txt", "md", "markdown")
        if (lower.substringAfterLast('.', "") !in allowed) {
            throw IllegalArgumentException(
                "Ungültige Dateiendung: ${file.name}. Erwartet: ${allowed.joinToString(", ")}",
            )
        }
        val bytes = file.readBytes()
        return sourceUseCase.createFileSource(body.knowledgeBaseId, file.name, bytes)
    }

    @PostMapping("/sources/confluence")
    fun createConfluenceSource(
        @RequestBody body: CreateConfluenceSourceRequest,
    ): KnowledgeSource =
        sourceUseCase.createConfluenceSource(
            knowledgeBaseId = body.knowledgeBaseId,
            confluenceConfigId = body.confluenceConfigId,
            rootPageUrl = body.rootPageUrl,
            includeChildren = body.includeChildren,
            maxDepth = body.maxDepth,
        )

    @PostMapping("/sources/jira")
    fun createJiraSource(
        @RequestBody body: CreateJiraSourceRequest,
    ): KnowledgeSource =
        sourceUseCase.createJiraSource(
            knowledgeBaseId = body.knowledgeBaseId,
            jiraConfigId = body.jiraConfigId,
            rootTicketKey = JiraTicketKey.of(body.rootTicketKey),
            includeChildren = body.includeChildren,
            includeComments = body.includeComments,
            maxDepth = body.maxDepth,
            includeLinkedTickets = body.includeLinkedTickets,
            includeParent = body.includeParent,
        )

    @PostMapping("/sources/jira-sprint")
    fun createJiraSprintSource(
        @RequestBody body: CreateJiraSprintSourceRequest,
    ): KnowledgeSource =
        sourceUseCase.createJiraSprintSource(
            knowledgeBaseId = body.knowledgeBaseId,
            jiraConfigId = body.jiraConfigId,
            sprintReference = body.sprintReference,
            includeChildren = body.includeChildren,
            includeComments = body.includeComments,
            maxDepth = body.maxDepth,
            includeLinkedTickets = body.includeLinkedTickets,
            includeParent = body.includeParent,
        )

    @GetMapping("/sources")
    fun listSources(
        @RequestParam(name = "knowledgeBaseId", required = false) knowledgeBaseId: Long?,
    ): List<KnowledgeSource> = sourceUseCase.listSources(knowledgeBaseId)

    @GetMapping("/sources/{id}")
    fun getSource(
        @PathVariable id: Long,
    ): KnowledgeSource? = sourceUseCase.getSource(id)

    @PostMapping("/sources/{id}/sync")
    fun syncSource(
        @PathVariable id: Long,
    ) = sourceUseCase.syncSource(id)

    @PostMapping("/sources/{id}/reindex")
    fun reindexSource(
        @PathVariable id: Long,
    ) = sourceUseCase.reindexSource(id)

    @DeleteMapping("/sources/{id}")
    fun deleteSource(
        @PathVariable id: Long,
    ): Map<String, Any> {
        sourceUseCase.deleteSource(id)
        return mapOf("ok" to true)
    }
}

data class CreateManualSourceRequest(
    val knowledgeBaseId: Long,
    val name: String,
    val content: String,
    val externalReference: String? = null,
)

data class LocalFileSourceRequest(
    val knowledgeBaseId: Long,
    val path: String,
)

data class CreateConfluenceSourceRequest(
    val knowledgeBaseId: Long,
    val confluenceConfigId: Long,
    val rootPageUrl: String,
    val includeChildren: Boolean = true,
    val maxDepth: Int = 10,
)

data class CreateJiraSourceRequest(
    val knowledgeBaseId: Long,
    val jiraConfigId: Long,
    val rootTicketKey: String,
    val includeChildren: Boolean = true,
    val includeComments: Boolean = true,
    val maxDepth: Int = 10,
    val includeLinkedTickets: Boolean = true,
    val includeParent: Boolean = false,
)

data class CreateJiraSprintSourceRequest(
    val knowledgeBaseId: Long,
    val jiraConfigId: Long,
    val sprintReference: String,
    val includeChildren: Boolean = true,
    val includeComments: Boolean = true,
    val maxDepth: Int = 1,
    val includeLinkedTickets: Boolean = true,
    val includeParent: Boolean = false,
)
