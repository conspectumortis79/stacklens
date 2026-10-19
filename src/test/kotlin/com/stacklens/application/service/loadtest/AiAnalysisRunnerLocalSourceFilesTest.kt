package com.stacklens.application.service.loadtest

import com.stacklens.adapter.out.filesystem.LocalDirectoryScanAdapter
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.TokenTrackingCopilotPortInterface
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.loadtest.LoadTestAnalysisAiPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path

class AiAnalysisRunnerLocalSourceFilesTest {
    private val runner =
        testAiAnalysisRunner(
            analysisRepo = mock<LoadTestAnalysisPort>(),
            gitlabConfigRepo = mock<GitlabConfigPort>(),
            copilotPort = mock<TokenTrackingCopilotPortInterface>(),
            gitlabService = mock<GitlabPort>(),
            i18n = mock<I18nPort>(),
            aiService = mock<LoadTestAnalysisAiPort>(),
            localRepoConfigRepo = mock<LocalRepoConfigPort>(),
            githubRepoConfigRepo = mock<GithubRepoConfigPort>(),
            scanService = mock<LocalRepoScanPort>(),
            githubBridge = mock<GithubTicketBridgePort>(),
            copilotConfig = mock<CopilotConfigPort>(),
            localDirectoryScan = LocalDirectoryScanAdapter(),
            asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
            parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun write(
        root: Path,
        relative: String,
        content: String,
    ): File =
        root.resolve(relative).toFile().apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    @Test
    fun `local sources keep the absolute path for matching and the relative path for display`(
        @TempDir root: Path,
    ) {
        write(root, "app/Dockerfile.prod", "FROM alpine")
        write(root, "src/main/kotlin/UserController.kt", "class UserController")

        val scanned = runner.localSourceFiles(root.toFile().absolutePath)

        val docker = scanned.first { it.displayPath == "app/Dockerfile.prod" }
        assertEquals(root.resolve("app/Dockerfile.prod").toFile().absolutePath, docker.matchPath)
        assertEquals("Dockerfile.prod", docker.name)
        assertEquals("FROM alpine", docker.content)
    }

    @Test
    fun `local sources drop dependencies and git internals and truncate long content`(
        @TempDir root: Path,
    ) {
        val long = write(root, "big.js", "x".repeat(4_000))
        write(root, "node_modules/leftpad/index.js", "dep")
        write(root, ".git/hooks/pre-commit", "hook")

        val scanned = runner.localSourceFiles(root.toFile().absolutePath)

        assertEquals(listOf("big.js"), scanned.map { it.displayPath })
        assertEquals(3_072, scanned.first().content.length)
        assertTrue(long.length() > 3_072)
    }

    @Test
    fun `local sources of an empty repository yield nothing`(
        @TempDir root: Path,
    ) {
        assertEquals(emptyList<LoadTestSourceFile>(), runner.localSourceFiles(root.toFile().absolutePath))
    }
}
