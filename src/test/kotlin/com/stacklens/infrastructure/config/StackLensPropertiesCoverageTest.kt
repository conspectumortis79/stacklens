package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.ai.OpenRouterProperties
import com.stacklens.adapter.out.jira.JiraEndpointSpec
import com.stacklens.adapter.out.jira.JiraEndpoints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.http.HttpClient

class StackLensPropertiesCoverageTest {
    @Test
    fun `OpenRouterProperties defaults match documented defaults`() {
        val p = OpenRouterProperties()
        assertEquals(OpenRouterProperties.DEFAULT_NON_TOOL_CAPABLE_PATTERNS, p.nonToolCapablePatterns)
        assertEquals(OpenRouterProperties.DEFAULT_HTTP_VERSION, p.httpVersion)
        assertEquals(OpenRouterProperties.DEFAULT_HTTP_MAX_RETRIES, p.httpMaxRetries)
        assertEquals(OpenRouterProperties.DEFAULT_HTTP_RETRY_BACKOFF_MS, p.httpRetryBackoffMs)
    }

    @Test
    fun `OpenRouterProperties stores every provided field verbatim`() {
        val p =
            OpenRouterProperties(
                nonToolCapablePatterns = listOf("foo", "bar"),
                httpVersion = HttpClient.Version.HTTP_2,
                httpMaxRetries = 7,
                httpRetryBackoffMs = 2_500L,
            )
        assertEquals(listOf("foo", "bar"), p.nonToolCapablePatterns)
        assertEquals(HttpClient.Version.HTTP_2, p.httpVersion)
        assertEquals(7, p.httpMaxRetries)
        assertEquals(2_500L, p.httpRetryBackoffMs)
    }

    @Test
    fun `OpenRouterProperties DEFAULT_NON_TOOL_CAPABLE_PATTERNS is non-empty`() {
        assertTrue(OpenRouterProperties.DEFAULT_NON_TOOL_CAPABLE_PATTERNS.isNotEmpty())
        OpenRouterProperties.DEFAULT_NON_TOOL_CAPABLE_PATTERNS.forEach {
            assertTrue(it.isNotBlank())
        }
    }

    @Test
    fun `OpenRouterProperties default HTTP version is HTTP_1_1`() {
        assertEquals(HttpClient.Version.HTTP_1_1, OpenRouterProperties.DEFAULT_HTTP_VERSION)
    }

    @Test
    fun `OpenRouterProperties defaults are positive`() {
        assertTrue(OpenRouterProperties.DEFAULT_HTTP_MAX_RETRIES > 0)
        assertTrue(OpenRouterProperties.DEFAULT_HTTP_RETRY_BACKOFF_MS > 0)
    }

    @Test
    fun `AiProperties default carries default OpenRouterProperties`() {
        val p = AiProperties()
        assertNotNull(p.openRouter)
        assertEquals(OpenRouterProperties(), p.openRouter)
    }

    @Test
    fun `AiProperties stores supplied OpenRouterProperties verbatim`() {
        val inner = OpenRouterProperties(httpMaxRetries = 11)
        val p = AiProperties(openRouter = inner)
        assertEquals(inner, p.openRouter)
    }

    @Test
    fun `JiraEndpoints defaults are non-empty and well-formed`() {
        val eps = JiraEndpoints()
        assertEquals(JiraEndpoints.DEFAULT_CLOUD_ENDPOINTS, eps.cloud)
        assertEquals(JiraEndpoints.DEFAULT_SERVER_ENDPOINTS, eps.server)
        eps.cloud.forEach { assertTrue(it.method.isNotBlank() && it.path.startsWith("/")) }
        eps.server.forEach { assertTrue(it.method.isNotBlank() && it.path.startsWith("/")) }
    }

    @Test
    fun `JiraEndpoints DEFAULT_CLOUD_ENDPOINTS contains expected first entry`() {
        val first = JiraEndpoints.DEFAULT_CLOUD_ENDPOINTS.first()
        assertEquals("GET", first.method)
        assertEquals("/rest/api/3/search/jql", first.path)
    }

    @Test
    fun `JiraEndpoints stores supplied endpoint lists verbatim`() {
        val cloud = listOf(JiraEndpointSpec("GET", "/a"), JiraEndpointSpec("POST", "/b"))
        val server = listOf(JiraEndpointSpec("GET", "/c"))
        val eps = JiraEndpoints(cloud = cloud, server = server)
        assertEquals(cloud, eps.cloud)
        assertEquals(server, eps.server)
    }

    @Test
    fun `JiraEndpointSpec stores its two arguments verbatim`() {
        val spec = JiraEndpointSpec("PATCH", "/rest/api/3/x")
        assertEquals("PATCH", spec.method)
        assertEquals("/rest/api/3/x", spec.path)
    }

    @Test
    fun `JiraProperties default carries default JiraEndpoints`() {
        val p = JiraProperties()
        assertEquals(JiraEndpoints(), p.endpoints)
    }

    @Test
    fun `JiraProperties stores supplied endpoints verbatim`() {
        val eps = JiraEndpoints(cloud = emptyList(), server = emptyList())
        val p = JiraProperties(endpoints = eps)
        assertEquals(eps, p.endpoints)
    }

    @Test
    fun `StackLensProperties default values match documented defaults`() {
        val p = StackLensProperties()
        assertEquals("./data", p.dataDir)
        assertEquals(AiProperties(), p.ai)
        assertEquals(JiraProperties(), p.jira)
    }

    @Test
    fun `StackLensProperties stores supplied values verbatim`() {
        val p =
            StackLensProperties(
                dataDir = "/var/data",
                ai = AiProperties(openRouter = OpenRouterProperties(httpMaxRetries = 2)),
                jira = JiraProperties(),
            )
        assertEquals("/var/data", p.dataDir)
        assertEquals(2, p.ai.openRouter.httpMaxRetries)
    }
}
