package com.stacklens.adapter.out.pipeline

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PipelineFilesAdapterBranchesTest {
    private fun check(path: String): Boolean {
        val inst =
            PipelineFilesAdapter(
                localRepoConfigRepo = org.mockito.kotlin.mock(),
                gitlabConfigRepo = org.mockito.kotlin.mock(),
                githubRepoConfigRepo = org.mockito.kotlin.mock(),
                scanService = org.mockito.kotlin.mock(),
                gitlabService = org.mockito.kotlin.mock(),
                githubBridge = org.mockito.kotlin.mock(),
            )
        return inst.looksLikePipelinePath(path)
    }

    @Test
    fun `matches basename in PIPELINE_BASENAMES (dockerfile)`() {
        assertTrue(check("src/Dockerfile"))
        assertTrue(check("path/to/Dockerfile.dev"))
    }

    @Test
    fun `matches basename that starts with dockerfile but is not in the set`() {
        assertTrue(check("Dockerfile.something"))
    }

    @Test
    fun `matches basename starting with docker-compose dot`() {
        assertTrue(check("docker-compose.yml"))
        assertTrue(check("docker-compose.override.yaml"))
    }

    @Test
    fun `matches basename starting with compose dot`() {
        assertTrue(check("compose.dev.yaml"))
    }

    @Test
    fun `matches buildkite file with yml extension`() {
        assertTrue(check("buildkite.yml"))
        assertTrue(check("buildkite.yaml"))
    }

    @Test
    fun `buildkite file without yml extension does not match`() {
        assertFalse(check("buildkite.txt"))
    }

    @Test
    fun `matches yml under github workflows`() {
        assertTrue(check(".github/workflows/build.yml"))
        assertTrue(check(".github/workflows/test.yaml"))
    }

    @Test
    fun `yml under another directory does not match the github branch`() {
        assertFalse(check("config/build.yml"))
    }

    @Test
    fun `matches config yml under circleci directory`() {
        assertTrue(check(".circleci/config.yml"))
        assertTrue(check(".circleci/config.yaml"))
    }

    @Test
    fun `config yml under another directory does not match the circleci branch`() {
        assertFalse(check("config.yml"))
    }

    @Test
    fun `unrelated filename does not match`() {
        assertFalse(check("README.md"))
        assertFalse(check("src/main.kt"))
    }
}
