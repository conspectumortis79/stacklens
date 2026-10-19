package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/knowledge")
class KnowledgeBasesController(
    private val baseUseCase: KnowledgeBaseUseCase,
) {
    @GetMapping("/bases")
    fun listBases() = baseUseCase.listBases()

    @PostMapping("/bases")
    fun createBase(
        @RequestBody body: CreateBaseRequest,
    ) = baseUseCase.createBase(body.name, body.description ?: "", body.includeInGlobalChat ?: true)

    @PutMapping("/bases/{id}")
    fun updateBase(
        @PathVariable id: Long,
        @RequestBody body: UpdateBaseRequest,
    ) = baseUseCase.updateBase(
        id,
        body.name,
        body.description,
        body.active,
        body.includeInGlobalChat,
    )

    @PostMapping("/bases/{id}/clear")
    fun clearBase(
        @PathVariable id: Long,
    ): Map<String, Any> {
        baseUseCase.clearBase(id)
        return mapOf("ok" to true)
    }

    @DeleteMapping("/bases/{id}")
    fun deleteBase(
        @PathVariable id: Long,
    ): Map<String, Any> {
        baseUseCase.deleteBase(id)
        return mapOf("ok" to true)
    }
}

data class CreateBaseRequest(
    val name: String,
    val description: String? = null,
    val includeInGlobalChat: Boolean? = null,
)

data class UpdateBaseRequest(
    val name: String? = null,
    val description: String? = null,
    val active: Boolean? = null,
    val includeInGlobalChat: Boolean? = null,
)
