package com.stacklens.adapter.out.health

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.health.ConnectionProbeResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class DefaultGithubConnectionProbeTest {
    private lateinit var server: MockWebServer
    private lateinit var probe: DefaultGithubConnectionProbe

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        probe =
            DefaultGithubConnectionProbe(
                org.springframework.web.reactive.function.client.WebClient
                    .builder(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `probe returns Ok when the API returns 200 with a non-empty id`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"id\": 12345, \"name\": \"repo\"}"),
        )
        val r = probe.probe("me", "repo", "ghp_token", server.url("/").toString().trimEnd('/'))
        assertTrue(r is ConnectionProbeResult.Ok, "expected Ok, got $r")
    }

    @Test
    fun `probe returns Failure when the API returns 200 with an id of 0`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"id\": 0}"),
        )
        val r = probe.probe("me", "repo", "ghp_token", server.url("/").toString().trimEnd('/'))
        assertTrue(r is ConnectionProbeResult.Failure)
        assertEquals("empty response from GitHub", (r as ConnectionProbeResult.Failure).errorMessage)
    }

    @Test
    fun `probe returns Failure when the API returns 200 with an empty body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val r = probe.probe("me", "repo", "ghp_token", server.url("/").toString().trimEnd('/'))
        assertTrue(r is ConnectionProbeResult.Failure)
        assertEquals("empty response from GitHub", (r as ConnectionProbeResult.Failure).errorMessage)
    }

    @Test
    fun `probe returns Failure with HTTP 401 when the API returns Unauthorized`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"message\":\"Bad credentials\"}"),
        )
        val r = probe.probe("me", "repo", "ghp_bad", server.url("/").toString().trimEnd('/'))
        assertTrue(r is ConnectionProbeResult.Failure)
        val msg = (r as ConnectionProbeResult.Failure).errorMessage
        assertTrue(msg.startsWith("HTTP 401"), "expected HTTP 401 prefix, got: $msg")
    }

    @Test
    fun `probe returns Failure with HTTP 404 when the API returns Not Found`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("{\"message\":\"Not Found\"}"),
        )
        val r = probe.probe("me", "missing", "ghp_token", server.url("/").toString().trimEnd('/'))
        assertTrue(r is ConnectionProbeResult.Failure)
        val msg = (r as ConnectionProbeResult.Failure).errorMessage
        assertTrue(msg.startsWith("HTTP 404"), "expected HTTP 404 prefix, got: $msg")
    }

    @Test
    fun `probe omits the Authorization header when the token is null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(ObjectMapper().writeValueAsString(mapOf("id" to 1))),
        )
        probe.probe("me", "repo", null, server.url("/").toString().trimEnd('/'))
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertEquals(null, recorded!!.getHeader("Authorization"))
    }

    @Test
    fun `probe omits the Authorization header when the token is blank`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(ObjectMapper().writeValueAsString(mapOf("id" to 1))),
        )
        probe.probe("me", "repo", "   ", server.url("/").toString().trimEnd('/'))
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertEquals(null, recorded!!.getHeader("Authorization"))
    }

    @Test
    fun `probe sends Bearer Authorization when the token is non-blank`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(ObjectMapper().writeValueAsString(mapOf("id" to 1))),
        )
        probe.probe("me", "repo", "ghp_token_xyz", server.url("/").toString().trimEnd('/'))
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertEquals("Bearer ghp_token_xyz", recorded!!.getHeader("Authorization"))
    }

    @Test
    fun `probe sends Accept and X-GitHub-Api-Version headers`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(ObjectMapper().writeValueAsString(mapOf("id" to 1))),
        )
        probe.probe("me", "repo", "ghp_token", server.url("/").toString().trimEnd('/'))
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertEquals("application/vnd.github+json", recorded!!.getHeader("Accept"))
        assertEquals("2022-11-28", recorded.getHeader("X-GitHub-Api-Version"))
    }

    @Test
    fun `probe strips the trailing slash from the api base before calling`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(ObjectMapper().writeValueAsString(mapOf("id" to 1))),
        )
        probe.probe(
            "me",
            "repo",
            "ghp_token",
            server.url("/").toString().trimEnd('/') + "/",
        )
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertTrue(
            recorded!!.path!!.startsWith("/repos/"),
            "expected /repos/... path, got ${recorded.path}",
        )
    }

    @Test
    fun `probe returns Failure when the API endpoint is unreachable`() {
        server.shutdown()
        val r = probe.probe("me", "repo", "ghp_token", "http://127.0.0.1:1")
        assertTrue(r is ConnectionProbeResult.Failure)
    }
}
