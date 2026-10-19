package com.stacklens.application.service

import com.stacklens.application.port.inbound.platform.MakeRunResult
import com.stacklens.application.port.inbound.platform.MakeRunnerUseCase
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.process.MakefileCommandPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory
import java.io.File

@ApplicationService
class MakeRunnerService(
    private val processPort: MakefileCommandPort,
    private val fileSystem: FileAttributes,
) : MakeRunnerUseCase {
    private val log = LoggerFactory.getLogger(MakeRunnerService::class.java)

    override val allowedTargets: Set<String> =
        setOf(
            "test",
            "tests",
            "unit-test",
            "unit-tests",
            "unittest",
            "unittests",
            "integration-test",
            "integration-tests",
            "functional-test",
            "functional-tests",
            "system-test",
            "system-tests",
            "coverage",
            "sniff",
            "beautify",
            "phpunit",
            "lint",
            "phpstan",
            "phpcs",
            "psalm",
            "deptrac",
            "ci",
            "build",
            "install",
            "update",
            "composer-install",
            "composer-update",
            "fmt",
            "format",
        )

    override fun run(
        repoPath: String,
        target: String,
        onLine: (String) -> Unit,
    ): MakeRunResult {
        val repo = File(repoPath.trim())
        val resolvedTarget = target.trim().ifBlank { "test" }

        if (!fileSystem.isDirectory(repo)) {
            return MakeRunResult(exitCode = -1, timedOut = false, errorMessage = "Repository path does not exist: ${repo.path}")
        }
        if (resolvedTarget !in allowedTargets) {
            return MakeRunResult(
                exitCode = -1,
                timedOut = false,
                errorMessage = "Target '$resolvedTarget' is not allowed. Allowed: ${allowedTargets.sorted().joinToString(", ")}",
            )
        }
        if (!fileSystem.isFile(File(repo, "Makefile"))) {
            return MakeRunResult(exitCode = -1, timedOut = false, errorMessage = "No Makefile in ${repo.path}")
        }

        return try {
            val makeBin = processPort.resolveMakeBinary()
            val exitCode =
                processPort.runStreaming(
                    cmd = listOf(makeBin, resolvedTarget),
                    workDir = repo,
                    timeoutSec = 600L,
                    onLine = onLine,
                )
            MakeRunResult(exitCode = exitCode, timedOut = false)
        } catch (e: Exception) {
            log.error("MakeRunner failed for target '{}' in '{}': {}", resolvedTarget, repoPath, e.message)
            MakeRunResult(exitCode = -1, timedOut = false, errorMessage = e.message)
        }
    }
}
