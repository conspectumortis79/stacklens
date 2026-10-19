package com.stacklens.application.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.application.port.model.pipeline.PipelineStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

class CopilotAgentPipelineDiskPersistenceTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `loadRunsFromDisk skips non-existing directory`() {
        val svc = makeService(dataDir = "/nonexistent/path/abc123xyz")

        svc.heal()
        assertTrue(svc.allRuns().isEmpty())
    }

    @Test
    fun `loadRunsFromDisk loads valid json file`() {
        val tempDir = Files.createTempDirectory("pipeline-test").toFile()
        val runsDir = File(tempDir, "agent-pipeline/runs")
        runsDir.mkdirs()
        val mapper = jacksonObjectMapper()
        val snap =
            mapOf(
                "id" to 42L,
                "rawText" to "test raw",
                "repoPath" to null,
                "featureSlug" to "test-slug",
                "status" to "DONE",
                "createdAt" to System.currentTimeMillis(),
                "stages" to emptyMap<String, Any>(),
            )
        mapper.writeValue(File(runsDir, "42.json"), snap)
        try {
            val svc = makeService(dataDir = tempDir.absolutePath)
            svc.loadRunsFromDisk()
            assertNotNull(svc.get(42L))
            assertEquals("test-slug", svc.get(42L)!!.featureSlug)
            assertEquals(PipelineStatus.DONE, svc.get(42L)!!.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `loadRunsFromDisk restores repoPath`() {
        val tempDir = Files.createTempDirectory("pipeline-test-repo").toFile()
        val runsDir = File(tempDir, "agent-pipeline/runs")
        runsDir.mkdirs()
        val mapper = jacksonObjectMapper()
        val snap =
            mapOf(
                "id" to 43L,
                "rawText" to "test",
                "repoPath" to "/some/repo/path",
                "featureSlug" to "slug",
                "status" to "DONE",
                "createdAt" to System.currentTimeMillis(),
                "stages" to emptyMap<String, Any>(),
            )
        mapper.writeValue(File(runsDir, "43.json"), snap)
        try {
            val svc = makeService(dataDir = tempDir.absolutePath)
            svc.loadRunsFromDisk()
            assertEquals("/some/repo/path", svc.get(43L)!!.repoPath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `loadRunsFromDisk skips invalid json file`() {
        val tempDir = Files.createTempDirectory("pipeline-test-bad").toFile()
        val runsDir = File(tempDir, "agent-pipeline/runs")
        runsDir.mkdirs()
        File(runsDir, "999.json").writeText("not valid json {{{")
        try {
            val svc = makeService(dataDir = tempDir.absolutePath)
            svc.loadRunsFromDisk()
            assertNull(svc.get(999L))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `loadRunsFromDisk marks running runs as ERROR`() {
        val tempDir = Files.createTempDirectory("pipeline-test-stale").toFile()
        val runsDir = File(tempDir, "agent-pipeline/runs")
        runsDir.mkdirs()
        val mapper = jacksonObjectMapper()
        val snap =
            mapOf(
                "id" to 55L,
                "rawText" to "stale",
                "repoPath" to null,
                "featureSlug" to "stale-run",
                "status" to "RUNNING",
                "createdAt" to System.currentTimeMillis(),
                "stages" to emptyMap<String, Any>(),
            )
        mapper.writeValue(File(runsDir, "55.json"), snap)
        try {
            val svc = makeService(dataDir = tempDir.absolutePath)
            svc.loadRunsFromDisk()
            val run = svc.get(55L)
            assertNotNull(run)
            assertEquals(PipelineStatus.ERROR, run!!.status)
            assertTrue(run.errorMessage?.contains("unterbrochen") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `loadRunsFromDisk marks pending runs as ERROR`() {
        val tempDir = Files.createTempDirectory("pipeline-test-stale2").toFile()
        val runsDir = File(tempDir, "agent-pipeline/runs")
        runsDir.mkdirs()
        val mapper = jacksonObjectMapper()
        val snap =
            mapOf(
                "id" to 56L,
                "rawText" to "stale",
                "repoPath" to null,
                "featureSlug" to "stale-pending",
                "status" to "PENDING",
                "createdAt" to System.currentTimeMillis(),
                "stages" to emptyMap<String, Any>(),
            )
        mapper.writeValue(File(runsDir, "56.json"), snap)
        try {
            val svc = makeService(dataDir = tempDir.absolutePath)
            svc.loadRunsFromDisk()
            assertEquals(PipelineStatus.ERROR, svc.get(56L)!!.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `loadRunsFromDisk sets counter to max loaded id`() {
        val tempDir = Files.createTempDirectory("pipeline-test-counter").toFile()
        val runsDir = File(tempDir, "agent-pipeline/runs")
        runsDir.mkdirs()
        val mapper = jacksonObjectMapper()
        listOf(10L, 20L, 15L).forEach { id ->
            val snap =
                mapOf(
                    "id" to id,
                    "rawText" to "x",
                    "repoPath" to null,
                    "featureSlug" to "x",
                    "status" to "DONE",
                    "createdAt" to System.currentTimeMillis(),
                    "stages" to emptyMap<String, Any>(),
                )
            mapper.writeValue(File(runsDir, "$id.json"), snap)
        }
        try {
            val svc = makeService(dataDir = tempDir.absolutePath)
            svc.loadRunsFromDisk()

            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            val newId = svc.start("test", null)
            assertTrue(newId > 20L, "Expected new id > 20, was $newId")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
