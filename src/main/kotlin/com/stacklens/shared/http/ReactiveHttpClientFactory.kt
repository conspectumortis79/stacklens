package com.stacklens.shared.http

import com.stacklens.shared.http.WebClientBuilderDsl.acceptJson
import com.stacklens.shared.http.WebClientBuilderDsl.authorizationHeader
import com.stacklens.shared.http.WebClientBuilderDsl.privateToken
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Component
class ReactiveHttpClientFactory(
    @Value("\${webclient.connect-timeout:30000}")
    private val connectTimeoutMs: Int = 30_000,
    @Value("\${webclient.read-timeout:180000}")
    private val readTimeoutMs: Int = 180_000,
    @Value("\${webclient.max-in-memory-size:10485760}")
    internal val defaultMaxInMemorySizeBytes: Int = 10 * 1024 * 1024,
    @Value("\${webclient.follow-redirects:true}")
    private val followRedirects: Boolean = true,
) {
    fun builder(): WebClient.Builder =
        WebClient
            .builder()
            .clientConnector(ReactorClientHttpConnector(createBaseHttpClient()))
            .codecs { it.defaultCodecs().maxInMemorySize(defaultMaxInMemorySizeBytes) }

    fun createBaseHttpClient(): HttpClient {
        val base =
            HttpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .doOnConnected { conn -> conn.addHandlerLast(ReadTimeoutHandler(readTimeoutMs / 1000)) }
        return if (followRedirects) {
            base.followRedirect(::isRedirectStatus)
        } else {
            base
        }
    }

    fun isRedirectStatus(
        @Suppress("UNUSED_PARAMETER") req: reactor.netty.http.client.HttpClientRequest,
        res: reactor.netty.http.client.HttpClientResponse,
    ): Boolean = res.status().code() in 301..308

    fun build(
        baseUrl: String,
        builder: WebClient.Builder.() -> Unit = {},
    ): WebClient =
        builder()
            .baseUrl(baseUrl.trimEnd('/'))
            .apply(builder)
            .build()

    fun buildBasicAuth(
        baseUrl: String,
        username: String,
        password: String,
        additional: WebClient.Builder.() -> Unit = {},
    ): WebClient {
        val credentials =
            java.util.Base64
                .getEncoder()
                .encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        return builder()
            .baseUrl(baseUrl.trimEnd('/'))
            .defaultHeader("Authorization", "Basic $credentials")
            .acceptJson()
            .apply(additional)
            .build()
    }

    fun buildAuthHeader(
        baseUrl: String,
        authHeaderValue: String,
        additional: WebClient.Builder.() -> Unit = {},
    ): WebClient =
        builder()
            .baseUrl(baseUrl.trimEnd('/'))
            .authorizationHeader(authHeaderValue)
            .acceptJson()
            .apply(additional)
            .build()

    fun buildGitlab(
        baseUrl: String,
        token: String,
    ): WebClient =
        builder()
            .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
            .baseUrl(baseUrl.trimEnd('/'))
            .privateToken(token)
            .build()
}

object WebClientBuilderDsl {
    fun WebClient.Builder.bearerToken(token: String): WebClient.Builder =
        defaultHeader(
            "Authorization",
            "Bearer $token",
        )

    fun WebClient.Builder.privateToken(token: String): WebClient.Builder =
        defaultHeader(
            "PRIVATE-TOKEN",
            token,
        )

    fun WebClient.Builder.authorizationHeader(value: String): WebClient.Builder =
        defaultHeader(
            "Authorization",
            value,
        )

    fun WebClient.Builder.githubHeaders(token: String): WebClient.Builder =
        apply {
            defaultHeader("Authorization", "Bearer $token")
            defaultHeader("Accept", "application/vnd.github+json")
            defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        }

    fun WebClient.Builder.acceptJson(): WebClient.Builder = defaultHeader("Accept", "application/json")
}
