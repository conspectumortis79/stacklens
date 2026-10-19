package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.ClaudeCodeCredential
import com.stacklens.testsupport.FixedClockPort
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.http.HttpClient
import java.net.http.HttpResponse

class AnthropicOAuthTokenAdapterTest {
    private lateinit var server: MockWebServer

    private val mapper = ObjectMapper()

    private val clock = FixedClockPort(1_000_000L)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(clientId: String? = "client-from-settings") =
        AnthropicOAuthTokenAdapter(
            settings = ClaudeCodeTestSupport.settings(oauthTokenUrl = server.url("/v1/oauth/token").toString(), oauthClientId = clientId),
            objectMapper = mapper,
            clock = clock,
            timeoutSeconds = 5L,
        )

    private fun credential(
        accessToken: String = "sk-ant-oat01-old",
        refreshToken: String? = "sk-ant-ort01-old",
        clientId: String? = null,
        scopes: List<String> = emptyList(),
    ) = ClaudeCodeCredential(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMillis = 1L,
        scopes = scopes,
        clientId = clientId,
    )

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun requestBody(): String = server.takeRequest().body.readUtf8()

    private fun adapterResponding(
        status: Int,
        body: String,
    ): AnthropicOAuthTokenAdapter {
        val response = mock<HttpResponse<String>>()
        whenever(response.statusCode()).thenReturn(status)
        whenever(response.body()).thenReturn(body)
        val client = mock<HttpClient>()
        whenever(client.send(any(), any<HttpResponse.BodyHandler<String>>())).thenAnswer { response }
        return AnthropicOAuthTokenAdapter(
            settings = ClaudeCodeTestSupport.settings(),
            objectMapper = mapper,
            clock = clock,
            timeoutSeconds = 5L,
            httpClient = client,
        )
    }

    @Test
    fun `posts a refresh grant with the stored refresh token and the configured client id`() {
        server.enqueue(json("""{"access_token":"new","expires_in":3600}"""))

        adapter().refresh(credential())

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/oauth/token", request.path)
        assertEquals("application/json", request.getHeader("Content-Type"))
        val body = mapper.readTree(request.body.readUtf8())
        assertEquals("refresh_token", body.path("grant_type").asText())
        assertEquals("sk-ant-ort01-old", body.path("refresh_token").asText())
        assertEquals("client-from-settings", body.path("client_id").asText())
        assertTrue(body.path("scope").isMissingNode)
    }

    @Test
    fun `the client id stored with the credential wins over the configuration`() {
        server.enqueue(json("""{"access_token":"new","expires_in":60}"""))

        adapter().refresh(credential(clientId = "client-from-store"))

        assertTrue(requestBody().contains("\"client_id\":\"client-from-store\""))
    }

    @Test
    fun `requested scopes are sent along`() {
        server.enqueue(json("""{"access_token":"new","expires_in":60}"""))

        adapter().refresh(credential(scopes = listOf("user:inference", "user:profile")))

        assertTrue(requestBody().contains("\"scope\":\"user:inference user:profile\""))
    }

    @Test
    fun `without any client id no request is made`() {
        assertNull(adapter(clientId = null).refresh(credential()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `without a refresh token no request is made`() {
        assertNull(adapter().refresh(credential(refreshToken = null)))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a refreshed credential carries the new expiry and rotated refresh token`() {
        server.enqueue(json("""{"access_token":"sk-ant-oat01-new","refresh_token":"sk-ant-ort01-new","expires_in":3600,"scope":"user:inference"}"""))

        val refreshed = adapter().refresh(credential(scopes = listOf("old:scope")))

        assertEquals("sk-ant-oat01-new", refreshed?.accessToken)
        assertEquals("sk-ant-ort01-new", refreshed?.refreshToken)
        assertEquals(1_000_000L + 3_600_000L, refreshed?.expiresAtMillis)
        assertEquals(listOf("user:inference"), refreshed?.scopes)
    }

    @Test
    fun `a response without rotation keeps the previous refresh token and scopes`() {
        server.enqueue(json("""{"access_token":"sk-ant-oat01-new","expires_in":60}"""))

        val refreshed = adapter().refresh(credential(scopes = listOf("user:inference")))

        assertEquals("sk-ant-ort01-old", refreshed?.refreshToken)
        assertEquals(listOf("user:inference"), refreshed?.scopes)
    }

    @Test
    fun `a response without an expiry is treated as long lived`() {
        server.enqueue(json("""{"access_token":"sk-ant-oat01-new"}"""))

        assertNull(adapter().refresh(credential())?.expiresAtMillis)
    }

    @Test
    fun `a rejected grant yields nothing`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"type":"invalid_grant","message":"refresh token revoked"}}"""))

        assertNull(adapter().refresh(credential()))
    }

    @Test
    fun `a payload without an access token yields nothing`() {
        server.enqueue(json("""{"expires_in":60}"""))

        assertNull(adapter().refresh(credential()))
    }

    @Test
    fun `a non json payload yields nothing`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>captive portal</html>"))

        assertNull(adapter().refresh(credential()))
    }

    @Test
    fun `an unreachable token endpoint yields nothing`() {
        val unreachable =
            AnthropicOAuthTokenAdapter(
                settings = ClaudeCodeTestSupport.settings(oauthTokenUrl = "http://127.0.0.1:1/v1/oauth/token"),
                objectMapper = mapper,
                clock = clock,
                timeoutSeconds = 2L,
            )

        assertNull(unreachable.refresh(credential()))
    }

    @Test
    fun `the built in clock, timeout and http client keep the adapter usable`() {
        val host =
            AnthropicOAuthTokenAdapter(
                settings = ClaudeCodeTestSupport.settings(oauthTokenUrl = server.url("/v1/oauth/token").toString()),
                objectMapper = mapper,
                clock = clock,
            )

        assertNull(host.refresh(credential(refreshToken = null)))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a refresh token that is only whitespace is no refresh token`() {
        assertNull(adapter().refresh(credential(refreshToken = "")))
        assertNull(adapter().refresh(credential(refreshToken = "   ")))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a blank client id in the credential blocks the configured client id`() {
        assertNull(adapter().refresh(credential(clientId = "   ")))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the stored client id is trimmed before it is sent`() {
        server.enqueue(json("""{"access_token":"new","expires_in":60}"""))

        assertEquals("new", adapter(clientId = null).refresh(credential(clientId = "  client-from-store  "))?.accessToken)
        assertTrue(requestBody().contains("\"client_id\":\"client-from-store\""))
    }

    @Test
    fun `a blank client id in the configuration is no client id at all`() {
        assertNull(adapter(clientId = "   ").refresh(credential()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a response below the success range is rejected`() {
        assertNull(adapterResponding(199, """{"access_token":"new"}""").refresh(credential()))
    }

    @Test
    fun `a failure without a message is reported by its type`() {
        val client = mock<HttpClient>()
        whenever(client.send(any(), any<HttpResponse.BodyHandler<String>>())).thenThrow(IllegalStateException())
        val silent =
            AnthropicOAuthTokenAdapter(
                settings = ClaudeCodeTestSupport.settings(),
                objectMapper = mapper,
                clock = clock,
                timeoutSeconds = 5L,
                httpClient = client,
            )

        assertNull(silent.refresh(credential()))
    }

    @Test
    fun `an interrupted refresh restores the interrupt flag`() {
        val client = mock<HttpClient>()
        whenever(client.send(any(), any<HttpResponse.BodyHandler<String>>())).thenThrow(InterruptedException())
        val interrupted =
            AnthropicOAuthTokenAdapter(
                settings = ClaudeCodeTestSupport.settings(),
                objectMapper = mapper,
                clock = clock,
                timeoutSeconds = 5L,
                httpClient = client,
            )
        try {
            assertNull(interrupted.refresh(credential()))
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `a json payload that holds no object yields nothing`() {
        server.enqueue(json("[]"))

        assertNull(adapter().refresh(credential()))
    }

    @Test
    fun `an access token that is only whitespace is unusable`() {
        server.enqueue(json("""{"access_token":"   ","expires_in":60}"""))

        assertNull(adapter().refresh(credential()))
    }

    @Test
    fun `a rotated refresh token that is only whitespace keeps the previous one`() {
        server.enqueue(json("""{"access_token":"new","refresh_token":"   ","expires_in":60}"""))

        assertEquals("sk-ant-ort01-old", adapter().refresh(credential())?.refreshToken)
    }

    @Test
    fun `an expiry that is not a number is treated as long lived`() {
        server.enqueue(json("""{"access_token":"new","expires_in":"3600"}"""))

        assertNull(adapter().refresh(credential())?.expiresAtMillis)
    }

    @Test
    fun `an empty scope in the payload keeps the requested scopes`() {
        server.enqueue(json("""{"access_token":"new","scope":""}"""))

        assertEquals(listOf("user:inference"), adapter().refresh(credential(scopes = listOf("user:inference")))?.scopes)
    }

    @Test
    fun `a whitespace scope in the payload keeps the requested scopes`() {
        server.enqueue(json("""{"access_token":"new","scope":"   "}"""))

        assertEquals(listOf("user:inference"), adapter().refresh(credential(scopes = listOf("user:inference")))?.scopes)
    }

    @Test
    fun `repeated separators in the granted scope are collapsed`() {
        server.enqueue(json("""{"access_token":"new","scope":"user:inference   user:profile"}"""))

        assertEquals(listOf("user:inference", "user:profile"), adapter().refresh(credential())?.scopes)
    }
}
