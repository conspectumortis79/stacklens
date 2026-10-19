
package com.stacklens.application.service.securityanalysis

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.analysis.sourceFilePriority
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.domain.model.security.SecurityFixResult

@ApplicationService
class GithubSecurityStrategy(
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val githubBridge: GithubTicketBridgePort,
) : SecurityRepoStrategy {
    override fun supports(analysis: SecurityAnalysis): Boolean = analysis.githubRepoConfigId != null

    override fun loadContext(
        analysis: SecurityAnalysis,
        onStep: (String) -> Unit,
    ): SecurityRepoContext {
        val githubRepoConfigId =
            analysis.githubRepoConfigId
                ?: throw IllegalStateException("GitHub repo config ID missing in analysis ${analysis.id}")
        val config =
            githubRepoConfigRepo.findGithubRepoConfigById(githubRepoConfigId)
                ?: throw IllegalStateException("GitHub repository configuration not found (id=$githubRepoConfigId)")
        onStep("Fetching GitHub repository…")
        val ctx =
            githubBridge.fetchRepoContext(config)
                ?: throw IllegalStateException("GitHub repository ${config.owner}/${config.repo} is not accessible.")
        val sorted = ctx.files.sortedBy { sourceFilePriority(it.path) }
        val tree = sorted.joinToString("\n") { it.path }
        val contents = sorted.joinToString("\n\n") { "=== ${it.path} ===\n${it.content.take(2048)}" }
        onStep("${ctx.files.size} files fetched from GitHub – preparing analysis…")
        val appName = "${config.owner}/${config.repo}"
        return SecurityRepoContext(appName, appName, tree, contents)
    }

    override fun loadFileContext(
        analysis: SecurityAnalysis,
        finding: SecurityFinding,
    ): SecurityFileContext {
        val githubRepoConfigId =
            analysis.githubRepoConfigId
                ?: throw IllegalStateException("GitHub repo config ID missing in analysis ${analysis.id}")
        val config =
            githubRepoConfigRepo.findGithubRepoConfigById(githubRepoConfigId)
                ?: throw IllegalStateException("GitHub repository configuration not found (id=$githubRepoConfigId)")
        val ctx =
            githubBridge.fetchRepoContext(config)
                ?: throw IllegalStateException("GitHub repository ${config.owner}/${config.repo} is not accessible.")
        val tree = ctx.files.joinToString("\n") { it.path }.take(2000)
        val fileContent =
            ctx.files
                .firstOrNull { it.path == finding.file }
                ?.content
                ?.take(10_000) ?: ""
        val testContent =
            ctx.files
                .filter { isTestFile(it.path) && isRelatedToFile(it.path, finding.file) }
                .take(3)
                .joinToString("\n\n") { "// FILE: ${it.path}\n${it.content.take(1500)}" }
        val projectName = "${config.owner}/${config.repo}"
        return SecurityFileContext(
            projectName,
            tree,
            fileContent,
            testContent,
            allFiles = ctx.files.map { it.path to it.content },
        )
    }

    override fun createFixResult(
        analysis: SecurityAnalysis,
        finding: SecurityFinding,
        parsedFix: ParsedFix,
    ): SecurityFixResult {
        if (parsedFix.changes.isEmpty()) throw IllegalStateException("AI could not generate a code fix.")
        val githubRepoConfigId =
            analysis.githubRepoConfigId
                ?: throw IllegalStateException("GitHub repo config ID missing in analysis ${analysis.id}")
        val config =
            githubRepoConfigRepo.findGithubRepoConfigById(githubRepoConfigId)
                ?: throw IllegalStateException("GitHub repository configuration not found (id=$githubRepoConfigId)")
        val branchKey = "secfix-${analysis.id}-${finding.id.take(16).replace("[^a-zA-Z0-9-]".toRegex(), "-")}"
        val projectName = "${config.owner}/${config.repo}"
        val prUrl =
            githubBridge.createPullRequest(
                config = config,
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
}
