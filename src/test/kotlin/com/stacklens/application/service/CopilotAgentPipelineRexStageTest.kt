package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CopilotAgentPipelineRexStageTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `pipeline Rex no repoPath outputs skip message`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(StageStatus.DONE, run.rex.status)
        assertTrue(run.rex.output.contains("Kein Repository"), "Rex should note no-repo skip")
    }

    @Test
    fun `pipeline Rex with valid empty repo - all make targets skipped`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-test").toFile()
        try {
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(10L)).thenReturn(
                LocalRepoConfig(id = 10L, name = "empty-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 10L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)

            assertTrue(run.rex.output.contains("übersprungen"), "Rex should skip all targets")
            assertTrue(run.rex.output.contains("Rex abgeschlossen"), "Rex should complete")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex non-writable repo outputs error`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-nowrite").toFile()
        try {
            tempDir.setReadable(false)
            tempDir.setWritable(false)

            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(11L)).thenReturn(
                LocalRepoConfig(id = 11L, name = "locked-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 11L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)

            assertTrue(
                run.rex.output.contains("nicht beschreibbar") || run.rex.output.contains("übersprungen"),
                "Rex output should reflect inaccessible repo: ${run.rex.output}",
            )
        } finally {
            tempDir.setReadable(true)
            tempDir.setWritable(true)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex with sniff target that passes - success message emitted`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-sniff-pass").toFile()
        try {
            File(tempDir, "Makefile").writeText("sniff:\n\t@echo \"no violations\"\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(20L)).thenReturn(
                LocalRepoConfig(id = 20L, name = "sniff-pass-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 20L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            assertTrue(
                run.rex.output.contains("erfolgreich"),
                "Rex output should report sniff success: ${run.rex.output}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex with all Makefile targets passing - all success messages emitted`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-all-pass").toFile()
        try {
            File(tempDir, "Makefile").writeText(
                "sniff:\n\t@echo \"clean\"\n\n" +
                    "test:\n\t@echo \"OK\"\n\n" +
                    "integration-test:\n\t@echo \"OK\"\n\n" +
                    "system-test:\n\t@echo \"OK\"\n\n" +
                    "coverage:\n\t@echo \"Lines : 100%\"\n",
            )
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(21L)).thenReturn(
                LocalRepoConfig(id = 21L, name = "all-pass-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 21L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            val out = run.rex.output
            assertTrue(out.contains("erfolgreich"), "Rex output should report sniff success: $out")
            assertTrue(out.contains("Rex abgeschlossen"), "Rex output should report completion: $out")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex cancel during sniff AI fix - ERROR and aborted message`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-cancel").toFile()
        try {
            File(tempDir, "Makefile").writeText("sniff:\n\t@exit 1\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(localRepoConfigPort.findLocalRepoConfigById(23L)).thenReturn(
                LocalRepoConfig(id = 23L, name = "cancel-rex-repo", path = tempDir.absolutePath),
            )

            val callCount = AtomicInteger(0)
            val reached = CountDownLatch(1)
            val release = CountDownLatch(1)
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
                if (callCount.incrementAndGet() == 12) {
                    reached.countDown()
                    release.await(3, TimeUnit.SECONDS)
                }
                "ok"
            }

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Fix lint", localRepoConfigId = 23L)
            reached.await(3, TimeUnit.SECONDS)

            svc.get(id)!!.cancelled = true
            release.countDown()
            val run = awaitRunDone(svc, id, timeoutMs = 10_000)

            assertEquals(PipelineStatus.ERROR, run.status)
            assertEquals("Pipeline abgebrochen.", run.errorMessage)
            assertEquals(StageStatus.ERROR, run.rex.status)
            assertTrue(
                run.rex.output.contains("abgebrochen") || run.rex.output.contains("Rex"),
                "Rex output should mention cancellation: ${run.rex.output}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex unit-test target passes - success message emitted`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-unit-pass").toFile()
        try {
            File(tempDir, "Makefile").writeText("test:\n\t@echo \"All tests passed\"\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(40L)).thenReturn(
                LocalRepoConfig(id = 40L, name = "unit-pass-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 40L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            assertTrue(run.rex.output.contains("erfolgreich"), "Rex output should report unit-test success: ${run.rex.output}")
            assertTrue(run.rex.output.contains("Rex abgeschlossen"), "Rex should complete: ${run.rex.output}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex unit-test succeeds after 1 AI fix attempt`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-unit-aifix").toFile()
        try {
            File(tempDir, "Makefile").writeText(
                "test:\n\t@if [ -f .fixed ]; then echo 'All tests OK'; else echo 'FAIL'; exit 1; fi\n",
            )
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")

            val fourArgCount = AtomicInteger(0)
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
                if (inv.arguments[3] == null && fourArgCount.incrementAndGet() <= 4) "ok" else "### .fixed\n```txt\nfixed\n```\n"
            }
            whenever(localRepoConfigPort.findLocalRepoConfigById(42L)).thenReturn(
                LocalRepoConfig(id = 42L, name = "unit-aifix-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Fix failing test", localRepoConfigId = 42L)
            val run = awaitRunDone(svc, id, timeoutMs = 15_000)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            assertTrue(
                run.rex.output.contains("erfolgreich nach KI-Fix"),
                "Rex output should report success after AI fix: ${run.rex.output}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex integration-test passes - success message emitted`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-integration-pass").toFile()
        try {
            File(tempDir, "Makefile").writeText("integration-test:\n\t@echo \"Integration OK\"\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(43L)).thenReturn(
                LocalRepoConfig(id = 43L, name = "integration-pass-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 43L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            assertTrue(
                run.rex.output.contains("erfolgreich"),
                "Rex output should report integration-test success: ${run.rex.output}",
            )
            assertTrue(run.rex.output.contains("Rex abgeschlossen"), "Rex should complete: ${run.rex.output}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex system-test passes - success message emitted`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-system-pass").toFile()
        try {
            File(tempDir, "Makefile").writeText("system-test:\n\t@echo \"System tests OK\"\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(44L)).thenReturn(
                LocalRepoConfig(id = 44L, name = "system-pass-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 44L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            assertTrue(
                run.rex.output.contains("erfolgreich"),
                "Rex output should report system-test success: ${run.rex.output}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex coverage 100 percent immediately - no AI fix loop entered`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-cov100").toFile()
        try {
            File(tempDir, "Makefile").writeText("coverage:\n\t@echo \"Lines : 100.0%\"\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            val fourArgCount = AtomicInteger(0)
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
                if (inv.arguments[3] == null) fourArgCount.incrementAndGet()
                "ok"
            }
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(45L)).thenReturn(
                LocalRepoConfig(id = 45L, name = "cov100-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 45L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            assertTrue(
                run.rex.output.contains("100% Code Coverage"),
                "Rex should report 100% coverage immediately: ${run.rex.output}",
            )

            assertEquals(3, fourArgCount.get(), "No AI fix calls expected for 100% coverage; got: ${fourArgCount.get()}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex coverage below 100 percent - reaches 100 after AI fix`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-cov-improve").toFile()
        try {
            File(tempDir, "Makefile").writeText(
                "coverage:\n\t@if [ -f .coverage-fixed ]; then echo \"Lines : 100.0%\"; else echo \"Lines : 80.0%\"; fi\n",
            )
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            val fourArgCount = AtomicInteger(0)
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
                if (fourArgCount.incrementAndGet() <= 4) {
                    "ok"
                } else {
                    "### .coverage-fixed\n```txt\nfixed\n```\n"
                }
            }
            whenever(localRepoConfigPort.findLocalRepoConfigById(46L)).thenReturn(
                LocalRepoConfig(id = 46L, name = "cov-improve-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 46L)
            val run = awaitRunDone(svc, id, timeoutMs = 15_000)

            assertEquals(PipelineStatus.DONE, run.status)
            assertTrue(
                run.rex.output.contains("100% Coverage erreicht"),
                "Rex should report 100% after AI fix: ${run.rex.output}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex coverage make exits non-zero - no AI fix loop entered`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-cov-fail").toFile()
        try {
            File(tempDir, "Makefile").writeText("coverage:\n\t@echo \"coverage tool crashed\"\n\t@exit 1\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            val fourArgCount = AtomicInteger(0)
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
                if (inv.arguments[3] == null) fourArgCount.incrementAndGet()
                "ok"
            }
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(47L)).thenReturn(
                LocalRepoConfig(id = 47L, name = "cov-fail-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 47L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertTrue(
                run.rex.output.contains("fehlgeschlagen"),
                "Rex should report coverage failure: ${run.rex.output}",
            )

            assertEquals(3, fourArgCount.get(), "No AI fix calls expected when coverage exits non-zero; got: ${fourArgCount.get()}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex beautify passes then sniff passes - beautify code path executed`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-beautify-pass").toFile()
        try {
            File(tempDir, "Makefile").writeText(
                "sniff:\n\t@if [ -f .beautified ]; then echo 'clean'; else echo 'violations'; exit 1; fi\n" +
                    "beautify:\n\t@touch .beautified\n",
            )
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(48L)).thenReturn(
                LocalRepoConfig(id = 48L, name = "beautify-pass-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 48L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            assertTrue(
                run.rex.output.contains("beautify"),
                "Rex output should mention beautify: ${run.rex.output}",
            )
            assertTrue(
                run.rex.output.contains("erfolgreich nach beautify"),
                "Rex output should report sniff success after beautify: ${run.rex.output}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex phpcs alias target detected and run`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-phpcs").toFile()
        try {
            File(tempDir, "Makefile").writeText("phpcs:\n\t@echo 'phpcs passed'\n")
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
            whenever(localRepoConfigPort.findLocalRepoConfigById(50L)).thenReturn(
                LocalRepoConfig(id = 50L, name = "phpcs-repo", path = tempDir.absolutePath),
            )

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 50L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            assertTrue(
                run.rex.output.contains("phpcs"),
                "Rex output should reference phpcs alias target: ${run.rex.output}",
            )
            assertTrue(
                run.rex.output.contains("erfolgreich"),
                "Rex output should report phpcs success: ${run.rex.output}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex sniff-and-unit-test both in Makefile - both execute in order`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-sniff-unit").toFile()
        try {
            File(tempDir, "Makefile").writeText(
                "sniff:\n\t@echo 'clean'\nunit-test:\n\t@echo 'Tests: 5, Failures: 0'\n",
            )
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("4arg-ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("5arg-ok")
            whenever(
                localRepoConfigPort.findLocalRepoConfigById(74L),
            ).thenReturn(LocalRepoConfig(id = 74L, name = "sniff-unit-repo", path = tempDir.absolutePath))

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 74L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertEquals(StageStatus.DONE, run.rex.status)
            assertTrue(
                run.rex.output.contains("sniff") || run.rex.output.contains("unit-test"),
                "Rex output must mention sniff or unit-test: ${run.rex.output.take(300)}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pipeline Rex with integration-test target - executes without AI fix`() {
        val tempDir = Files.createTempDirectory("pipeline-rex-integration").toFile()
        try {
            File(tempDir, "Makefile").writeText(
                "integration-test:\n\t@echo 'Integration Tests: 3, Failures: 0'\n",
            )
            whenever(copilot.resolveModel()).thenReturn("gpt-4")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("4arg-ok")
            whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), any<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("5arg-ok")
            whenever(
                localRepoConfigPort.findLocalRepoConfigById(75L),
            ).thenReturn(LocalRepoConfig(id = 75L, name = "integ-repo", path = tempDir.absolutePath))

            val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
            val id = svc.start("Feature", localRepoConfigId = 75L)
            val run = awaitRunDone(svc, id)

            assertEquals(PipelineStatus.DONE, run.status)
            assertTrue(
                run.rex.output.contains("integration") || run.rex.output.contains("Integration"),
                "Rex output must mention integration: ${run.rex.output.take(300)}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
