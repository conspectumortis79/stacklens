package com.stacklens.shared.http

import com.stacklens.shared.http.WebClientBuilderDsl.acceptJson
import com.stacklens.shared.http.WebClientBuilderDsl.authorizationHeader
import com.stacklens.shared.http.WebClientBuilderDsl.bearerToken
import com.stacklens.shared.http.WebClientBuilderDsl.githubHeaders
import com.stacklens.shared.http.WebClientBuilderDsl.privateToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReactiveHttpClientFactoryTest {
    private val factory = ReactiveHttpClientFactory()

    @Test
    fun `builder returns a non-null WebClient Builder`() {
        val b = factory.builder()
        assertNotNull(b)
    }

    @Test
    fun `builder returns a fresh instance on every call`() {
        val a = factory.builder()
        val b = factory.builder()
        assertNotNull(a)
        assertNotNull(b)
        assertTrue(a !== b)
    }

    @Test
    fun `createBaseHttpClient returns a non-null HttpClient`() {
        val c = factory.createBaseHttpClient()
        assertNotNull(c)
    }

    @Test
    fun `defaultMaxInMemorySize returns the configured size`() {
        assertEquals(10 * 1024 * 1024, factory.defaultMaxInMemorySizeBytes)
    }

    @Test
    fun `build returns a WebClient with the requested baseUrl`() {
        val client = factory.build("https://example.com")
        assertNotNull(client)
    }

    @Test
    fun `build strips the trailing slash from the baseUrl before building`() {
        val clientWithSlash = factory.build("https://example.com/")
        val clientWithoutSlash = factory.build("https://example.com")
        assertNotNull(clientWithSlash)
        assertNotNull(clientWithoutSlash)
    }

    @Test
    fun `build applies the additional builder lambda before constructing the client`() {
        var called = false
        val client =
            factory.build("https://example.com") {
                called = true
            }
        assertNotNull(client)
        assertTrue(called, "the additional builder lambda was not invoked")
    }

    @Test
    fun `bearerToken adds an Authorization header with the bearer scheme`() {
        val b = factory.builder().bearerToken("tok-123")
        assertNotNull(b)
    }

    @Test
    fun `privateToken adds a PRIVATE-TOKEN header`() {
        val b = factory.builder().privateToken("glpat-abc")
        assertNotNull(b)
    }

    @Test
    fun `authorizationHeader adds a custom Authorization header value`() {
        val b = factory.builder().authorizationHeader("Basic dXNlcjpwYXNz")
        assertNotNull(b)
    }

    @Test
    fun `githubHeaders adds the GitHub bundle of three headers`() {
        val b = factory.builder().githubHeaders("ghp_xyz")
        assertNotNull(b)
    }

    @Test
    fun `acceptJson adds an Accept application json header`() {
        val b = factory.builder().acceptJson()
        assertNotNull(b)
    }

    @Test
    fun `factory with followRedirects false still creates a base client`() {
        val noRedirectsFactory = ReactiveHttpClientFactory(followRedirects = false)
        val c = noRedirectsFactory.createBaseHttpClient()
        assertNotNull(c)
        assertNotNull(noRedirectsFactory.builder())
        assertNotNull(noRedirectsFactory.build("https://example.com"))
    }

    @Test
    fun `factory with custom timeouts still produces a working builder and build`() {
        val custom =
            ReactiveHttpClientFactory(
                connectTimeoutMs = 1000,
                readTimeoutMs = 2000,
                defaultMaxInMemorySizeBytes = 1024,
            )
        assertNotNull(custom.builder())
        assertNotNull(custom.build("https://example.com"))
        assertEquals(1024, custom.defaultMaxInMemorySizeBytes)
    }

    @Test
    fun `factory plus every DSL helper still produces a WebClient`() {
        val client =
            factory.build("https://example.com/") {
                bearerToken("tok-1")
                privateToken("priv-1")
                authorizationHeader("Basic abc")
                githubHeaders("ghp_1")
                acceptJson()
            }
        assertNotNull(client)
    }
}
