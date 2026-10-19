package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeJobUseCase
import com.stacklens.domain.model.knowledge.KnowledgeIngestionJob
import com.stacklens.domain.model.knowledge.KnowledgeJobStatus
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class KnowledgeJobsControllerTest {
    private val jobUseCase: KnowledgeJobUseCase = mock()
    private val controller = KnowledgeJobsController(jobUseCase)
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(KnowledgeExceptionAdvice())
            .build()

    private fun job(
        id: Long,
        status: KnowledgeJobStatus = KnowledgeJobStatus.COMPLETED,
    ): KnowledgeIngestionJob = KnowledgeIngestionJob(id = id, sourceId = 1L, status = status, phase = "done")

    @Test
    fun `listJobs defaults the limit to 100 when no query param is given`() {
        whenever(jobUseCase.listJobs(100)).thenReturn(listOf(job(1L), job(2L)))

        mockMvc
            .perform(get("/api/knowledge/jobs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[1].id").value(2))
    }

    @Test
    fun `listJobs forwards a custom limit when supplied`() {
        whenever(jobUseCase.listJobs(5)).thenReturn(listOf(job(1L)))

        mockMvc
            .perform(get("/api/knowledge/jobs").param("limit", "5"))
            .andExpect(status().isOk)
        verify(jobUseCase).listJobs(5)
    }

    @Test
    fun `getJob returns the single job`() {
        whenever(jobUseCase.getJob(42L)).thenReturn(job(42L, KnowledgeJobStatus.INDEXING))

        mockMvc
            .perform(get("/api/knowledge/jobs/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(42))
    }

    @Test
    fun `cancelJob returns ok and delegates to the use case`() {
        mockMvc
            .perform(post("/api/knowledge/jobs/42/cancel"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
        verify(jobUseCase).cancelJob(42L)
    }

    @Test
    fun `IllegalStateException from the use case is mapped to 400`() {
        whenever(jobUseCase.getJob(eq(99L))).thenThrow(
            IllegalStateException("job has already finished"),
        )

        mockMvc
            .perform(get("/api/knowledge/jobs/99"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("job has already finished"))
    }
}
