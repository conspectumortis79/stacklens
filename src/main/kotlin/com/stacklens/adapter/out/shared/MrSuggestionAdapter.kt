package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.MrSuggestionPort
import org.springframework.beans.factory.annotation.Value
import java.net.URI

class MrSuggestionAdapter(
    @Value("\${webclient.connect-timeout:30000}") connectTimeoutMs: Int = 30_000,
    @Value("\${webclient.read-timeout:120000}") readTimeoutMs: Int = 120_000,
    internal var githubApiBaseUrl: String = "https://api.github.com",
) : MrSuggestionPort {
    private val gitlabSuggestions = GitlabMrSuggestionAdapter(connectTimeoutMs, readTimeoutMs)
    private val githubSuggestions = GithubPrSuggestionAdapter(connectTimeoutMs, readTimeoutMs)

    override fun postSuggestion(request: MrSuggestionPort.PostRequest): String {
        val uri = URI.create(request.mrUrl.trim())
        val host = uri.host?.lowercase() ?: error("Cannot parse host from mrUrl: ${request.mrUrl}")
        return when (MrProvider.resolve(host, uri.path)) {
            MrProvider.GITHUB -> githubSuggestions.post(request, uri, githubApiBaseUrl)
            MrProvider.GITLAB -> gitlabSuggestions.post(request, uri)
            null -> error("Unsupported MR provider for host: $host")
        }
    }
}
