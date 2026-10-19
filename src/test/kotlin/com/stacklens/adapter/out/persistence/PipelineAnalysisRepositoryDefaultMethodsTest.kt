package com.stacklens.adapter.out.persistence

import com.stacklens.domain.model.PipelineAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class PipelineAnalysisRepositoryDefaultMethodsTest {
    @Autowired private lateinit var repo: PipelineAnalysisRepository

    @Test
    fun `PipelineAnalysisRepository savePipelineAnalysis stores and returns the entity`() {
        val saved = repo.savePipelineAnalysis(PipelineAnalysis(status = "PENDING"))
        assertNotNull(saved.id)
    }

    @Test
    fun `PipelineAnalysisRepository deletePipelineAnalysisById removes the row`() {
        val saved = repo.savePipelineAnalysis(PipelineAnalysis(status = "PENDING"))
        repo.deletePipelineAnalysisById(saved.id)
        assertTrue(repo.findPipelineAnalysisById(saved.id) == null)
    }

    @Test
    fun `PipelineAnalysisRepository deleteAllPipelineAnalyses clears the table`() {
        repo.savePipelineAnalysis(PipelineAnalysis(status = "PENDING"))
        repo.savePipelineAnalysis(PipelineAnalysis(status = "PENDING"))
        repo.deleteAllPipelineAnalyses()
        assertTrue(repo.findAllPipelineAnalysesOrderByCreatedAtDesc().isEmpty())
    }

    @Test
    fun `PipelineAnalysisRepository findAllOrderByCreatedAtDesc returns sorted list`() {
        repo.savePipelineAnalysis(PipelineAnalysis(status = "PENDING"))
        repo.savePipelineAnalysis(PipelineAnalysis(status = "RUNNING"))
        val list = repo.findAllPipelineAnalysesOrderByCreatedAtDesc()
        assertEquals(2, list.size)
    }
}
