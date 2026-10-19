package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.DiffHunkParserPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import java.net.URI

class MrFetchAdapter(
    diffHunkParser: DiffHunkParserPort,
    @Value("\${webclient.connect-timeout:30000}") connectTimeoutMs: Int = 30_000,
    @Value("\${webclient.read-timeout:120000}") readTimeoutMs: Int = 120_000,
    internal var githubApiBaseUrl: String = "https://api.github.com",
) : MrFetchPort {
    private val log = LoggerFactory.getLogger(MrFetchAdapter::class.java)
    private val gitlabFetcher = GitlabMrFetchAdapter(MrDiffTextBuilder(diffHunkParser), connectTimeoutMs, readTimeoutMs)
    private val githubFetcher = GithubPrFetchAdapter(MrDiffTextBuilder(diffHunkParser), connectTimeoutMs, readTimeoutMs)

    override fun fetchMrData(
        mrUrl: String,
        gitlabConfigs: List<GitlabConfig>,
        githubConfigs: List<GithubRepoConfig>,
    ): MrFetchPort.MrFetchResult =
        try {
            val uri = URI.create(mrUrl.trim())
            val host = uri.host?.lowercase() ?: return MrFetchPort.MrFetchResult.Failure("Invalid MR URL: no host")
            val path = uri.path?.takeIf { it.isNotBlank() } ?: return MrFetchPort.MrFetchResult.Failure("Invalid MR URL: no path")

            val data =
                when (MrProvider.resolve(host, path)) {
                    MrProvider.GITHUB ->
                        githubFetcher.fetch(mrUrl, path, githubConfigs, githubApiBaseUrl)
                    MrProvider.GITLAB ->
                        gitlabFetcher.fetch(mrUrl, uri, host, path, gitlabConfigs)
                    null -> {
                        log.warn("[MrFetch] Unknown MR URL: {}", mrUrl)
                        null
                    }
                }
            if (data != null) MrFetchPort.MrFetchResult.Success(data) else MrFetchPort.MrFetchResult.Failure("MR not found: $mrUrl")
        } catch (e: Exception) {
            log.error("[MrFetch] Error loading MR data: {}", e.message, e)
            val isConnRefused =
                generateSequence(e as Throwable?) { it.cause }
                    .any { it is java.net.ConnectException }
            if (isConnRefused) {
                throw StackLensFailureException(
                    StackLensFailure.MergeRequestConnectionRefused(refusedServerHost(mrUrl)),
                    e,
                )
            }
            MrFetchPort.MrFetchResult.Failure(e.message ?: "error")
        }

    internal fun refusedServerHost(mrUrl: String): String =
        runCatching {
            val uri = URI.create(mrUrl.trim())
            "${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
        }.getOrDefault(mrUrl)
}
