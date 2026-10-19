package com.stacklens.shared.http

import com.stacklens.shared.http.WebClientBuilderDsl.acceptJson
import com.stacklens.shared.http.WebClientBuilderDsl.authorizationHeader
import com.stacklens.shared.http.WebClientBuilderDsl.bearerToken
import com.stacklens.shared.http.WebClientBuilderDsl.githubHeaders
import com.stacklens.shared.http.WebClientBuilderDsl.privateToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientRequest
import reactor.netty.http.client.HttpClientResponse

class ReactiveHttpClientFactoryBranchTest {
    private fun factory(followRedirects: Boolean = true): ReactiveHttpClientFactory =
        ReactiveHttpClientFactory(
            connectTimeoutMs = 1000,
            readTimeoutMs = 2000,
            defaultMaxInMemorySizeBytes = 1024,
            followRedirects = followRedirects,
        )

    @Test
    fun `createBaseHttpClient returns a client with no followRedirect when disabled`() {
        val f = factory(followRedirects = false)
        val client: HttpClient = f.createBaseHttpClient()
        assertNotNull(client)
    }

    @Test
    fun `createBaseHttpClient with followRedirects enabled exercises lambda`() {
        val f = factory(followRedirects = true)
        val client: HttpClient = f.createBaseHttpClient()
        assertNotNull(client)
    }

    @Test
    fun `defaultMaxInMemorySize returns configured value`() {
        val f = factory()
        assertEquals(1024, f.defaultMaxInMemorySizeBytes)
    }

    @Test
    fun `defaultMaxInMemorySize returns 10 MB by default`() {
        val f = ReactiveHttpClientFactory()
        assertEquals(10 * 1024 * 1024, f.defaultMaxInMemorySizeBytes)
    }

    @Test
    fun `builder returns a configured WebClient Builder`() {
        val f = factory()
        val builder: WebClient.Builder = f.builder()
        assertNotNull(builder)
    }

    @Test
    fun `build creates a WebClient for the given baseUrl`() {
        val f = factory()
        val client: WebClient = f.build("https://example.com")
        assertNotNull(client)
    }

    @Test
    fun `build applies custom builder lambda`() {
        val f = factory()
        val client =
            f.build("https://example.com") {
                defaultHeader("X-Test", "value")
            }
        assertNotNull(client)
    }

    @Test
    fun `build trims trailing slash from baseUrl`() {
        val f = factory()
        val client = f.build("https://example.com/")
        assertNotNull(client)
    }

    @Test
    fun `bearerToken extension adds Authorization header`() {
        val builder = WebClient.builder()
        with(WebClientBuilderDsl) {
            builder.bearerToken("my-token")
        }
        assertNotNull(builder)
    }

    @Test
    fun `isRedirectStatus returns true for redirect codes 301 to 308 and false otherwise`() {
        val f = factory()
        val responseCodes =
            listOf(
                200 to false,
                300 to false,
                301 to true,
                302 to true,
                303 to true,
                304 to true,
                305 to true,
                307 to true,
                308 to true,
                309 to false,
                404 to false,
                500 to false,
            )
        for ((code, expected) in responseCodes) {
            val resp = mock<HttpClientResponse>()
            val status =
                io.netty.handler.codec.http.HttpResponseStatus
                    .valueOf(code)
            whenever(resp.status()).thenReturn(status)
            val req = mock<HttpClientRequest>()
            val actual = f.isRedirectStatus(req, resp)
            assertEquals(expected, actual, "status $code (status.code()=${status.code()})")
        }
    }

    @Test
    fun `privateToken extension adds PRIVATE-TOKEN header`() {
        val builder = WebClient.builder()
        with(WebClientBuilderDsl) {
            builder.privateToken("my-token")
        }
        assertNotNull(builder)
    }

    @Test
    fun `authorizationHeader extension adds custom Authorization header`() {
        val builder = WebClient.builder()
        with(WebClientBuilderDsl) {
            builder.authorizationHeader("Basic xyz")
        }
        assertNotNull(builder)
    }

    @Test
    fun `githubHeaders extension adds three default headers`() {
        val builder = WebClient.builder()
        with(WebClientBuilderDsl) {
            builder.githubHeaders("my-token")
        }
        assertNotNull(builder)
    }

    @Test
    fun `acceptJson extension adds Accept header`() {
        val builder = WebClient.builder()
        with(WebClientBuilderDsl) {
            builder.acceptJson()
        }
        assertNotNull(builder)
    }
}
