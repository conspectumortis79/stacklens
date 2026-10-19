package com.stacklens.application.service

import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.port.out.repo.RepoReader
import com.stacklens.application.port.out.repo.ShellCommandRunner
import com.stacklens.application.port.out.repo.ShellRepoReaderFactory
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory
import java.io.File

@ApplicationService
class RepoScanner(
    private val processPort: ProcessRunPort,
    private val directReader: RepoReader,
    private val fileSystem: FileAttributes,
    private val shellReaderFactory: ShellRepoReaderFactory,
) {
    private val log = LoggerFactory.getLogger(RepoScanner::class.java)

    fun shellRead(
        vararg cmd: String,
        timeoutSeconds: Long = 10,
    ): String? {
        val result =
            runCatching {
                processPort.run(
                    cmd = cmd.toList(),
                    timeoutSec = timeoutSeconds,
                )
            }

        return result
            .map { res -> if (res.exitCode != 0) null else res.stdout.ifBlank { res.stderr } }
            .getOrNull()
            ?.ifBlank { null }
    }

    fun scanRepoStructure(repoPath: String): String {
        val root = File(repoPath)

        if (!fileSystem.exists(root)) {
            return "(directory not found: $repoPath)"
        }

        if (!fileSystem.isDirectory(root)) {
            return "(not a directory: $repoPath)"
        }

        if (!fileSystem.canRead(root)) {
            val shellTest = shellRead("ls", repoPath)
            if (shellTest != null) {
                log.info("[scanRepo] JVM access denied for '{}' — using shell fallback", repoPath)
                return scanRepoStructureViaShell(repoPath)
            }
            return (
                "(access denied — StackLens cannot read '$repoPath'. On macOS grant 'Full Disk Access' " +
                    "to Java/IntelliJ in System Settings > Privacy & Security > Full Disk Access)"
            )
        }

        return scanRepoStructureDirect(root, repoPath)
    }

    fun scanRepoStructureDirect(
        root: File,
        repoPath: String,
    ): String = RepoSectionBuilder(directReader).build(root, repoPath)

    fun scanRepoStructureViaShell(repoPath: String): String =
        RepoSectionBuilder(
            shellReaderFactory.create(ShellCommandRunner { vararg -> shellRead(*vararg) }),
        ).build(
            root = File(repoPath),
            repoPath = repoPath,
            shellFallbackBanner = "(read via shell fallback — JVM direct access blocked by OS)",
        )
}
