package com.stacklens.application.service.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeJobUseCase
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeIngestionJob
import com.stacklens.domain.model.knowledge.KnowledgeJobStatus

@ApplicationService
class KnowledgeJobService(
    private val jobPort: KnowledgeIngestionJobPort,
    private val clock: ClockPort,
) : KnowledgeJobUseCase {
    override fun listJobs(limit: Int): List<KnowledgeIngestionJob> = jobPort.findAllByOrderByCreatedAtDesc().take(limit.coerceAtLeast(1))

    override fun getJob(id: Long): KnowledgeIngestionJob? = jobPort.findById(id)

    override fun cancelJob(id: Long) {
        val job = jobPort.findById(id) ?: return
        if (job.status == KnowledgeJobStatus.COMPLETED || job.status == KnowledgeJobStatus.FAILED) return
        job.status = KnowledgeJobStatus.CANCELLED
        job.finishedAt = clock.localDateTime()
        jobPort.save(job)
    }
}
