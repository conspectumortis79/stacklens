package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeJobUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/knowledge")
class KnowledgeJobsController(
    private val jobUseCase: KnowledgeJobUseCase,
) {
    @GetMapping("/jobs")
    fun listJobs(
        @RequestParam(name = "limit", required = false) limit: Int?,
    ) = jobUseCase.listJobs(limit ?: 100)

    @GetMapping("/jobs/{id}")
    fun getJob(
        @PathVariable id: Long,
    ) = jobUseCase.getJob(id)

    @PostMapping("/jobs/{id}/cancel")
    fun cancelJob(
        @PathVariable id: Long,
    ): Map<String, Any> {
        jobUseCase.cancelJob(id)
        return mapOf("ok" to true)
    }
}
