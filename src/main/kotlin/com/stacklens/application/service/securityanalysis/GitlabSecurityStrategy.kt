
package com.stacklens.application.service.securityanalysis

import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GitlabTicketBridgePort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.domain.model.security.SecurityFixResult

@ApplicationService
class GitlabSecurityStrategy(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val gitlabService: GitlabPort,
    private val gitlabTicketBridge: GitlabTicketBridgePort,
) : SecurityRepoStrategy {
    override fun supports(analysis: SecurityAnalysis): Boolean = analysis.gitlabConfigId != null

    override fun loadContext(
        analysis: SecurityAnalysis,
        onStep: (String) -> Unit,
    ): SecurityRepoContext {
        val gitlabConfigId =
            analysis.gitlabConfigId
                ?: throw IllegalStateException("GitLab config ID missing in analysis ${analysis.id}")
        val config =
            gitlabConfigRepo.findGitlabConfigById(gitlabConfigId)
                ?: throw IllegalStateException("GitLab configuration not found (id=$gitlabConfigId)")
        val projectId =
            analysis.gitlabProjectId
                ?: throw IllegalStateException("GitLab project ID missing in analysis ${analysis.id}")
        onStep("Fetching GitLab project files…")
        val files = gitlabService.fetchProjectFiles(config, projectId, maxFiles = 25, maxPerFileSizeBytes = 2048)
        val tree = files.joinToString("\n") { it.first }
        val contents = files.joinToString("\n\n") { "=== ${it.first} ===\n${it.second}" }
        onStep("${files.size} files fetched from GitLab – preparing analysis…")
        val appName = analysis.gitlabProjectPath?.substringAfterLast("/") ?: "project"
        return SecurityRepoContext(appName, analysis.gitlabProjectPath ?: "", tree, contents)
    }

    override fun loadFileContext(
        analysis: SecurityAnalysis,
        finding: SecurityFinding,
    ): SecurityFileContext {
        val gitlabConfigId =
            analysis.gitlabConfigId
                ?: throw IllegalStateException("GitLab config ID missing in analysis ${analysis.id}")
        val config =
            gitlabConfigRepo.findGitlabConfigById(gitlabConfigId)
                ?: throw IllegalStateException("GitLab configuration not found (id=$gitlabConfigId)")
        val projectId =
            analysis.gitlabProjectId
                ?: throw IllegalStateException("GitLab project ID missing in analysis ${analysis.id}")
        val files = gitlabService.fetchProjectFiles(config, projectId, maxFiles = 50, maxPerFileSizeBytes = 8192)
        val tree = files.joinToString("\n") { it.first }.take(2000)
        val fileContent = files.firstOrNull { it.first == finding.file }?.second?.take(10_000) ?: ""
        val testContent =
            files
                .filter { (path, _) -> isTestFile(path) && isRelatedToFile(path, finding.file) }
                .take(3)
                .joinToString("\n\n") { (path, content) -> "// FILE: $path\n${content.take(1500)}" }
        val projectName = analysis.gitlabProjectPath?.substringAfterLast("/") ?: "project"
        return SecurityFileContext(projectName, tree, fileContent, testContent, allFiles = files)
    }

    override fun createFixResult(
        analysis: SecurityAnalysis,
        finding: SecurityFinding,
        parsedFix: ParsedFix,
    ): SecurityFixResult {
        if (parsedFix.changes.isEmpty()) throw IllegalStateException("AI could not generate a code fix.")
        val gitlabConfigId =
            analysis.gitlabConfigId
                ?: throw IllegalStateException("GitLab config ID missing in analysis ${analysis.id}")
        val config =
            gitlabConfigRepo.findGitlabConfigById(gitlabConfigId)
                ?: throw IllegalStateException("GitLab configuration not found (id=$gitlabConfigId)")
        val branchKey = "secfix-${analysis.id}-${finding.id.take(16).replace("[^a-zA-Z0-9-]".toRegex(), "-")}"
        val repoPath = analysis.gitlabProjectPath ?: ""
        val projectId =
            analysis.gitlabProjectId
                ?: throw IllegalStateException("GitLab project ID missing in analysis ${analysis.id}")
        val mrUrl =
            gitlabTicketBridge.createMergeRequest(
                config = config,
                repoName = repoPath,
                ticket = buildFixTicket(branchKey, finding, parsedFix.explanation),
                codeImpl = buildCodeImpl(repoPath, finding, parsedFix.changes),
                gitlabProjectId = projectId,
            )
        return SecurityFixResult(
            prUrl = mrUrl,
            branchName = branchKey,
            explanation = parsedFix.explanation,
            filesChanged = parsedFix.changes.map { it.path },
            isLocalRepo = false,
            localChanges = null,
        )
    }
}
