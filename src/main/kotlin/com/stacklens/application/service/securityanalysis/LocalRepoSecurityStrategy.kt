
package com.stacklens.application.service.securityanalysis

import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.file.FileReadWritePort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.domain.model.security.SecurityFixResult
import com.stacklens.domain.model.security.SecurityLocalFileChange
import java.io.File
import java.nio.file.Paths

@ApplicationService
class LocalRepoSecurityStrategy(
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val scanService: LocalRepoScanPort,
    private val i18n: I18nPort,
    private val githubBridge: GithubTicketBridgePort,
    private val directoryScan: LocalDirectoryScanPort,
    private val fileSystem: FileReadWritePort,
) : SecurityRepoStrategy {
    override fun supports(analysis: SecurityAnalysis): Boolean = analysis.repoConfigId != null

    override fun loadContext(
        analysis: SecurityAnalysis,
        onStep: (String) -> Unit,
    ): SecurityRepoContext {
        val repoConfigId =
            analysis.repoConfigId
                ?: throw IllegalStateException("repoConfigId fehlt in Analysis ${analysis.id}")
        val config =
            localRepoConfigRepo.findLocalRepoConfigById(repoConfigId)
                ?: throw IllegalStateException(i18n.t("code_analysis.be_repo_config_not_found", repoConfigId))
        onStep("Reading repository files…")
        val summary = scanService.buildCodeSummary(config.path, maxFiles = 60, maxPerFileSizeKb = 8)
        onStep("${summary.fileCount} files read (${summary.charCount / 1000} kB) – preparing analysis…")
        return SecurityRepoContext(config.name, config.path, summary.projectTree, summary.fileContents)
    }

    override fun loadFileContext(
        analysis: SecurityAnalysis,
        finding: SecurityFinding,
    ): SecurityFileContext {
        val repoConfigId =
            analysis.repoConfigId
                ?: throw IllegalStateException("repoConfigId fehlt in Analysis ${analysis.id}")
        val config =
            localRepoConfigRepo.findLocalRepoConfigById(repoConfigId)
                ?: throw IllegalStateException("Repo config $repoConfigId not found")
        val repoDir = File(config.path)
        val targetFile = File(repoDir, finding.file)
        val fileContent = if (fileSystem.exists(targetFile)) fileSystem.readText(targetFile).take(10_000) else ""
        val testContent = findLocalTestFileContent(repoDir, finding.file)
        val projectTree =
            directoryScan
                .paths(
                    root = repoDir,
                    accept = { scanned ->
                        val rel = scanned.relativePath
                        !rel.startsWith(".") && !rel.contains("build/") && !rel.contains("node_modules/")
                    },
                    limit = TREE_FILE_LIMIT,
                ).joinToString("\n") { it.relativePath }
                .take(2000)
        return SecurityFileContext(config.name, projectTree, fileContent, testContent)
    }

    override fun createFixResult(
        analysis: SecurityAnalysis,
        finding: SecurityFinding,
        parsedFix: ParsedFix,
    ): SecurityFixResult {
        val repoConfigId =
            analysis.repoConfigId
                ?: throw IllegalStateException("repoConfigId fehlt in Analysis ${analysis.id}")
        val config =
            localRepoConfigRepo.findLocalRepoConfigById(repoConfigId)
                ?: throw IllegalStateException("Repo config $repoConfigId not found")

        val githubConfig =
            runCatching { githubBridge.detectGithubRemote(Paths.get(config.path)) }.getOrNull()
        if (githubConfig != null && githubConfig.id != 0L) {
            val branchKey =
                "secfix-${analysis.id}-${finding.id.take(16).replace("[^a-zA-Z0-9-]".toRegex(), "-")}"
            val projectName = "${githubConfig.owner}/${githubConfig.repo}"
            val prUrl =
                githubBridge.createPullRequest(
                    config = githubConfig,
                    ticket = buildFixTicket(branchKey, finding, parsedFix.explanation),
                    codeImpl = buildCodeImpl(projectName, finding, parsedFix.changes),
                )
            return SecurityFixResult(
                prUrl = prUrl,
                branchName = branchKey,
                explanation = parsedFix.explanation,
                filesChanged = parsedFix.changes.map { it.path },
                isLocalRepo = false,
                localChanges = null,
            )
        }

        return SecurityFixResult(
            prUrl = null,
            branchName = null,
            explanation = parsedFix.explanation,
            filesChanged = parsedFix.changes.map { it.path },
            isLocalRepo = true,
            localChanges = parsedFix.changes.map { SecurityLocalFileChange(it.path, it.code, it.changeType) },
        )
    }

    private fun findLocalTestFileContent(
        repoDir: File,
        targetFilePath: String,
    ): String {
        val baseName = targetFilePath.substringAfterLast('/').substringBeforeLast('.')
        return directoryScan
            .textFiles(
                root = repoDir,
                accept = { scanned ->
                    isTestFile(scanned.file.path) && scanned.file.nameWithoutExtension.contains(baseName, ignoreCase = true)
                },
                maxContentChars = TEST_FILE_CONTENT_CHARS,
                limit = TEST_FILE_LIMIT,
            ).joinToString("\n\n") { "// FILE: ${it.path.relativePath}\n${it.content}" }
    }

    private companion object {
        const val TREE_FILE_LIMIT: Int = 80
        const val TEST_FILE_LIMIT: Int = 3
        const val TEST_FILE_CONTENT_CHARS: Int = 1_500
    }
}
