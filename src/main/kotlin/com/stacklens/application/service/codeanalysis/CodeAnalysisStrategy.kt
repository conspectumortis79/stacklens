package com.stacklens.application.service.codeanalysis

import com.stacklens.application.port.out.analysis.CodeAnalysisRepoData
import com.stacklens.application.port.out.analysis.GithubRepoContentPort
import com.stacklens.application.port.out.analysis.GitlabRepoContentPort
import com.stacklens.application.port.out.analysis.LocalRepoContentPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.CodeAnalysis

data class CodeAnalysisLoadParams(
    val repoConfigId: Long? = null,
    val gitlabConfigId: Long? = null,
    val gitlabProjectId: Long? = null,
    val gitlabProjectPath: String? = null,
    val subPath: String? = null,
    val githubRepoConfigId: Long? = null,
) {
    val appName: String
        get() =
            when {
                !gitlabProjectPath.isNullOrBlank() -> "$gitlabProjectPath${subPath?.takeIf { it.isNotBlank() }?.let { "/$it" }.orEmpty()}"
                !subPath.isNullOrBlank() -> subPath
                repoConfigId != null -> "local-$repoConfigId"
                githubRepoConfigId != null -> "github-$githubRepoConfigId"
                else -> "unknown"
            }
}

interface CodeAnalysisStrategy {
    fun params(analysis: CodeAnalysis?): CodeAnalysisLoadParams

    fun load(params: CodeAnalysisLoadParams): CodeAnalysisRepoData?
}

@ApplicationService
class LocalCodeAnalysisStrategy(
    private val codeRepoPort: LocalRepoContentPort,
) : CodeAnalysisStrategy {
    override fun params(analysis: CodeAnalysis?): CodeAnalysisLoadParams = CodeAnalysisLoadParams(repoConfigId = analysis?.repoConfigId)

    override fun load(params: CodeAnalysisLoadParams): CodeAnalysisRepoData? {
        val repoConfigId = params.repoConfigId ?: return null
        return codeRepoPort.loadLocal(repoConfigId)
    }
}

@ApplicationService
class GitlabCodeAnalysisStrategy(
    private val codeRepoPort: GitlabRepoContentPort,
) : CodeAnalysisStrategy {
    override fun params(analysis: CodeAnalysis?): CodeAnalysisLoadParams =
        CodeAnalysisLoadParams(
            gitlabConfigId = analysis?.gitlabConfigId,
            gitlabProjectId = analysis?.gitlabProjectId,
            gitlabProjectPath = analysis?.gitlabProjectPath,
            subPath = analysis?.monorepoSubPath,
        )

    fun paramsFor(
        gitlabConfigId: Long,
        projectId: Long,
        projectPath: String,
        subPath: String,
    ): CodeAnalysisLoadParams =
        CodeAnalysisLoadParams(
            gitlabConfigId = gitlabConfigId,
            gitlabProjectId = projectId,
            gitlabProjectPath = projectPath,
            subPath = subPath,
        )

    override fun load(params: CodeAnalysisLoadParams): CodeAnalysisRepoData? {
        val configId = params.gitlabConfigId ?: return null
        val projectId = params.gitlabProjectId ?: return null
        val projectPath = params.gitlabProjectPath ?: return null
        return codeRepoPort.loadGitlab(configId, projectId, projectPath, params.subPath.orEmpty())
    }
}

@ApplicationService
class GithubCodeAnalysisStrategy(
    private val codeRepoPort: GithubRepoContentPort,
) : CodeAnalysisStrategy {
    override fun params(analysis: CodeAnalysis?): CodeAnalysisLoadParams = CodeAnalysisLoadParams(githubRepoConfigId = analysis?.githubRepoConfigId)

    fun paramsFor(githubRepoConfigId: Long): CodeAnalysisLoadParams = CodeAnalysisLoadParams(githubRepoConfigId = githubRepoConfigId)

    override fun load(params: CodeAnalysisLoadParams): CodeAnalysisRepoData? {
        val id = params.githubRepoConfigId ?: return null
        return codeRepoPort.loadGithub(id)
    }
}
