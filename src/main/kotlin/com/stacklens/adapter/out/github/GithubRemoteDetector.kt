package com.stacklens.adapter.out.github

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.shared.gitRemoteUrl
import org.slf4j.LoggerFactory
import java.nio.file.Path

class GithubRemoteDetector(
    private val githubRepoConfigPort: GithubRepoConfigPort,
) {
    private val log = LoggerFactory.getLogger(GithubRemoteDetector::class.java)

    fun detectGithubRemote(localPath: Path): GithubRepoConfig? =
        try {
            gitRemoteUrl(localPath.toFile())
                ?.let { remoteUrl ->
                    parseGithubRemoteUrl(remoteUrl).also {
                        if (it != null) {
                            log.debug("[GithubBridge] GitHub remote detected for {}: {}/{}", localPath, it.owner, it.repo)
                        }
                    }
                }
        } catch (e: Exception) {
            log.debug("[GithubBridge] GitHub remote detection failed for {}: {}", localPath, e.message)
            null
        }

    fun parseGithubRemoteUrl(url: String): GithubRepoConfig? {
        val httpsMatch = Regex("""https://github\.com/([^/]+)/([^/]+?)(?:\.git)?$""").find(url)
        if (httpsMatch != null) {
            val owner = httpsMatch.groupValues[1]
            val repo = httpsMatch.groupValues[2]
            return storedConfigFor(owner, repo)
                ?: GithubRepoConfig(name = "$owner/$repo", owner = owner, repo = repo)
        }
        val sshMatch = Regex("""git@github\.com:([^/]+)/([^/]+?)(?:\.git)?$""").find(url)
        if (sshMatch != null) {
            val owner = sshMatch.groupValues[1]
            val repo = sshMatch.groupValues[2]
            return storedConfigFor(owner, repo)
                ?: GithubRepoConfig(name = "$owner/$repo", owner = owner, repo = repo)
        }
        return null
    }

    fun resolveToken(config: GithubRepoConfig): String? =
        config.accessToken?.takeIf { it.isNotBlank() }
            ?: githubRepoConfigPort
                .findAllGithubRepoConfigs()
                .firstOrNull { it.owner.equals(config.owner, ignoreCase = true) && it.repo.equals(config.repo, ignoreCase = true) }
                ?.accessToken
                ?.takeIf { it.isNotBlank() }

    private fun storedConfigFor(
        owner: String,
        repo: String,
    ): GithubRepoConfig? =
        githubRepoConfigPort
            .findAllGithubRepoConfigs()
            .firstOrNull { it.owner.equals(owner, ignoreCase = true) && it.repo.equals(repo, ignoreCase = true) }
}
