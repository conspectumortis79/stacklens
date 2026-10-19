package com.stacklens.adapter.out.pipeline

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PipelineFilesAdapterCompanionTest {
    @Test
    fun `PIPELINE_BASENAMES includes dockerfile and docker-compose yml`() {
        val set = PipelineFilesAdapter.PIPELINE_BASENAMES
        assertTrue("dockerfile" in set)
        assertTrue("docker-compose.yml" in set)
    }

    @Test
    fun `PIPELINE_BASENAMES does not contain random text`() {
        val set = PipelineFilesAdapter.PIPELINE_BASENAMES
        assertFalse("random-text" in set)
        assertFalse("not-a-pipeline-file" in set)
    }
}
