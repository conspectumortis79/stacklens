package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.enums.CodeHostingPlatform
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.ConnectionEntity
import java.net.URI

class IntegrationCloneUrlBuilder(
    private val githubConfigPort: GithubRepoConfigPort,
    private val gitlabConfigPort: GitlabConfigPort,
) {
    fun buildCloneUrl(
        mrData: MrFetchPort.MrData,
        analysis: CodeQaAnalysis,
    ): String? =
        when (CodeHostingPlatform.fromWire(mrData.platform)) {
            is CodeHostingPlatform.Github -> buildGithubCloneUrl(mrData.mrUrl, analysis.gitConfigId)
            is CodeHostingPlatform.Gitlab -> buildGitlabCloneUrl(mrData.mrUrl, analysis.gitConfigId)
            is CodeHostingPlatform.Unknown -> null
        }

    private fun buildGithubCloneUrl(
        mrUrl: String,
        preferredConfigId: Long?,
    ): String? {
        val match =
            Regex("https?://github\\.com/([^/]+)/([^/]+)/pull/", RegexOption.IGNORE_CASE).find(mrUrl)
                ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val configs = githubConfigPort.findAllGithubRepoConfigs().filter { it.active }
        val cfg =
            if (preferredConfigId != null) {
                configWithId(configs, preferredConfigId)
            } else {
                configs.find { it.owner.equals(owner, ignoreCase = true) && it.repo.equals(repo, ignoreCase = true) }
            }
        val token = cfg?.accessToken?.takeIf { it.isNotBlank() }
        return if (token != null) {
            "https://$token@github.com/$owner/$repo.git"
        } else {
            "https://github.com/$owner/$repo.git"
        }
    }

    private fun buildGitlabCloneUrl(
        mrUrl: String,
        preferredConfigId: Long?,
    ): String? {
        val baseRepoUrl = mrUrl.replace(Regex("/-/merge_requests/\\d+.*"), "").trimEnd('/')
        return try {
            val uri = URI(baseRepoUrl)
            val host = "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
            val configs = gitlabConfigPort.findByActiveTrue()
            val cfg =
                if (preferredConfigId != null) {
                    configWithId(configs, preferredConfigId)
                } else {
                    configs.find { cfg ->
                        runCatching {
                            val cfgUri = URI(cfg.baseUrl)
                            val cfgHost = "${cfgUri.scheme}://${cfgUri.host}${if (cfgUri.port > 0) ":${cfgUri.port}" else ""}"
                            cfgHost.equals(host, ignoreCase = true)
                        }.getOrDefault(false)
                    }
                }
            val token = cfg?.accessToken?.takeIf { it.isNotBlank() }
            val repoPath = "${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}${uri.path}.git"
            if (token != null) {
                "${uri.scheme}://oauth2:$token@$repoPath"
            } else {
                "$baseRepoUrl.git"
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun <T : ConnectionEntity> configWithId(
        configs: List<T>,
        preferredConfigId: Long,
    ): T? = configs.find { it.id == preferredConfigId }
}
