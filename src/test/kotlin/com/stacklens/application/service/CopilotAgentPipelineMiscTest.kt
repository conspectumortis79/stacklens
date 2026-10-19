package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

class CopilotAgentPipelineMiscTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `scanRepoStructureDirect includes repository path`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-direct").toFile()
        try {
            val result = svc.scanRepoStructureDirect(tempDir, tempDir.absolutePath)
            assertTrue(result.contains(tempDir.absolutePath), "Should contain repo path")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureDirect lists top-level files and dirs`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-listing").toFile()
        try {
            File(tempDir, "src").mkdir()
            File(tempDir, "README.md").writeText("hello")
            val result = svc.scanRepoStructureDirect(tempDir, tempDir.absolutePath)
            assertTrue(result.contains("src/"), "Should contain src/ dir")
            assertTrue(result.contains("README.md"), "Should contain README.md")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureDirect reads composer json`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-composer").toFile()
        try {
            File(tempDir, "composer.json").writeText("""{"name":"test/app","require":{}}""")
            val result = svc.scanRepoStructureDirect(tempDir, tempDir.absolutePath)
            assertTrue(result.contains("composer.json"))
            assertTrue(result.contains("test/app"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureDirect reads Makefile`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-make").toFile()
        try {
            File(tempDir, "Makefile").writeText("test:\n\tphpunit\n")
            val result = svc.scanRepoStructureDirect(tempDir, tempDir.absolutePath)
            assertTrue(result.contains("Makefile"))
            assertTrue(result.contains("phpunit"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureDirect reads docker-compose yml`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-docker").toFile()
        try {
            File(tempDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  app:\n    image: php\n")
            val result = svc.scanRepoStructureDirect(tempDir, tempDir.absolutePath)
            assertTrue(result.contains("docker-compose.yml"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureDirect includes PHP sample files`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-php").toFile()
        try {
            File(tempDir, "src").mkdir()
            File(tempDir, "src/Service.php").writeText("<?php class Service {}")
            val result = svc.scanRepoStructureDirect(tempDir, tempDir.absolutePath)
            assertTrue(result.contains("Service.php"))
            assertTrue(result.contains("<?php"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureDirect excludes vendor PHP files`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-vendor").toFile()
        try {
            File(tempDir, "vendor").mkdir()
            File(tempDir, "vendor/autoload.php").writeText("<?php // vendor file")
            File(tempDir, "src").mkdir()
            File(tempDir, "src/App.php").writeText("<?php class App {}")
            val result = svc.scanRepoStructureDirect(tempDir, tempDir.absolutePath)
            assertFalse(result.contains("autoload.php"), "Should NOT include vendor/autoload.php")
            assertTrue(result.contains("App.php"), "Should include src/App.php")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureDirect result is capped at 20000 chars`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-cap").toFile()
        try {
            File(tempDir, "Big.php").writeText("<?php\n" + "// line\n".repeat(5000))
            val result = svc.scanRepoStructureDirect(tempDir, tempDir.absolutePath)
            assertTrue(result.length <= 20000, "Result should be capped at 20000 chars")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureDirect detects src source tree`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-src").toFile()
        try {
            File(tempDir, "src/domain/model").mkdirs()
            File(tempDir, "src/application/service").mkdirs()
            File(tempDir, "src/infrastructure/persistence").mkdirs()
            val result = svc.scanRepoStructureDirect(tempDir, tempDir.absolutePath)
            assertTrue(result.contains("Source tree"))
            assertTrue(result.contains("domain"))
            assertTrue(result.contains("application"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureViaShell lists top-level entries`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-shell").toFile()
        try {
            File(tempDir, "src").mkdir()
            File(tempDir, "composer.json").writeText("""{"name":"shell/test"}""")
            val result = svc.scanRepoStructureViaShell(tempDir.absolutePath)
            assertTrue(result.contains(tempDir.absolutePath), "Should contain repo path")
            assertTrue(result.contains("src"), "Should list src directory")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureViaShell reads composer json via shell`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-shell-composer").toFile()
        try {
            File(tempDir, "composer.json").writeText("""{"name":"myvendor/myapp"}""")
            val result = svc.scanRepoStructureViaShell(tempDir.absolutePath)
            assertTrue(result.contains("composer.json"))
            assertTrue(result.contains("myvendor/myapp"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureViaShell reads PHP files via shell`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-shell-php").toFile()
        try {
            File(tempDir, "src").mkdir()
            File(tempDir, "src/MyClass.php").writeText("<?php class MyClass {}")
            val result = svc.scanRepoStructureViaShell(tempDir.absolutePath)
            assertTrue(result.contains("MyClass.php"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureViaShell result is capped at 20000 chars`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-shell-cap").toFile()
        try {
            File(tempDir, "Big.php").writeText("<?php\n" + "// line\n".repeat(5000))
            val result = svc.scanRepoStructureViaShell(tempDir.absolutePath)
            assertTrue(result.length <= 20000)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanRepoStructureViaShell contains shell fallback note`() {
        val svc = makeService()
        val tempDir = Files.createTempDirectory("scan-shell-note").toFile()
        try {
            val result = svc.scanRepoStructureViaShell(tempDir.absolutePath)
            assertTrue(result.contains("shell fallback"), "Should note shell fallback usage")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `evictOldRuns removes oldest DONE runs when over 200 limit`() {
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")

        for (i in 1L..202L) {
            val run = AgentPipelineRun(id = i, rawText = "x", repoPath = null, featureSlug = "x-$i", createdAt = System.currentTimeMillis())
            run.status = PipelineStatus.DONE
            svc.registry.put(i, run)
        }

        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        svc.start("trigger eviction", null)

        Thread.sleep(500)

        assertTrue(svc.allRuns().size <= 201, "Should have at most 201 runs after eviction (200 limit + 1 new)")

        assertNull(svc.get(1L), "Oldest run (id=1) should have been evicted")
        assertNull(svc.get(2L), "Second-oldest run (id=2) should have been evicted")

        assertNotNull(svc.get(202L), "Run 202 should not have been evicted")
    }

    @Test
    fun `multiple concurrent pipeline runs complete independently`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id1 = svc.start("Feature A", null)
        val id2 = svc.start("Feature B", null)

        val run1 = awaitRunDone(svc, id1, timeoutMs = 10_000)
        val run2 = awaitRunDone(svc, id2, timeoutMs = 10_000)

        assertEquals(PipelineStatus.DONE, run1.status, "Run 1 should be DONE")
        assertEquals(PipelineStatus.DONE, run2.status, "Run 2 should be DONE")

        assertFalse(id1 == id2, "Runs should have different IDs")

        assertNotNull(svc.get(id1))
        assertNotNull(svc.get(id2))
    }
}
