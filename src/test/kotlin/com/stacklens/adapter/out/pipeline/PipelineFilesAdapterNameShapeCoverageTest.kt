package com.stacklens.adapter.out.pipeline

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class PipelineFilesAdapterNameShapeCoverageTest {
    private val adapter =
        PipelineFilesAdapter(
            localRepoConfigRepo = mock(),
            gitlabConfigRepo = mock(),
            githubRepoConfigRepo = mock(),
            scanService = mock(),
            gitlabService = mock(),
            githubBridge = mock(),
        )

    @Test
    fun `compose files are found under either prefix`() {
        assertTrue(adapter.looksLikePipelinePath("docker-compose.yml"))
        assertTrue(adapter.looksLikePipelinePath("compose.yml"))
        assertTrue(adapter.looksLikePipelinePath("sub/dir/compose.yaml"))
        assertTrue(adapter.looksLikePipelinePath("docker-compose.custom"))
        assertTrue(adapter.looksLikePipelinePath("compose.custom"))
        assertFalse(adapter.looksLikePipelinePath("notes/docker.txt"))
    }

    @Test
    fun `buildkite files only count while they are yaml`() {
        assertTrue(adapter.looksLikePipelinePath("buildkite.yml"))
        assertTrue(adapter.looksLikePipelinePath("notes/buildkite.yaml"))
        assertFalse(adapter.looksLikePipelinePath("buildkite.txt"))
        assertTrue(adapter.looksLikePipelinePath("buildkite.extra.yml"))
        assertTrue(adapter.looksLikePipelinePath("buildkite.extra.yaml"))
    }

    @Test
    fun `plain yaml outside a workflow folder is not a pipeline`() {
        assertFalse(adapter.looksLikePipelinePath("docs/config.yml"))
        assertTrue(adapter.looksLikePipelinePath(".github/workflows/ci.yaml"))
        assertTrue(adapter.looksLikePipelinePath(".circleci/config.yml"))
    }
}
