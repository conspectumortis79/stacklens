package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.ai.OpenRouterProperties
import com.stacklens.adapter.out.jira.JiraEndpointSpec
import com.stacklens.adapter.out.jira.JiraEndpoints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.http.HttpClient

class StackLensPropertiesFullTest {
    @Test
    fun `AiProperties has expected defaults`() {
        val props = AiProperties()
        assertEquals(OpenRouterProperties(), props.openRouter)
        assertTrue(props.openRouter.nonToolCapablePatterns.isNotEmpty())
    }

    @Test
    fun `OpenRouterProperties has expected defaults`() {
        val props = OpenRouterProperties()
        assertEquals(OpenRouterProperties.DEFAULT_NON_TOOL_CAPABLE_PATTERNS, props.nonToolCapablePatterns)
        assertEquals(OpenRouterProperties.DEFAULT_HTTP_VERSION, props.httpVersion)
        assertEquals(OpenRouterProperties.DEFAULT_HTTP_MAX_RETRIES, props.httpMaxRetries)
        assertEquals(OpenRouterProperties.DEFAULT_HTTP_RETRY_BACKOFF_MS, props.httpRetryBackoffMs)
    }

    @Test
    fun `OpenRouterProperties DEFAULT_NON_TOOL_CAPABLE_PATTERNS contains known patterns`() {
        val patterns = OpenRouterProperties.DEFAULT_NON_TOOL_CAPABLE_PATTERNS
        assertTrue(patterns.any { it.contains("minimax", ignoreCase = true) })
        assertTrue(patterns.any { it.contains("gemma", ignoreCase = true) })
        assertTrue(patterns.any { it.contains("phi-3", ignoreCase = true) })
    }

    @Test
    fun `OpenRouterProperties DEFAULT_HTTP_VERSION is HTTP_1_1`() {
        assertEquals(HttpClient.Version.HTTP_1_1, OpenRouterProperties.DEFAULT_HTTP_VERSION)
    }

    @Test
    fun `OpenRouterProperties DEFAULT_HTTP_MAX_RETRIES is 3`() {
        assertEquals(3, OpenRouterProperties.DEFAULT_HTTP_MAX_RETRIES)
    }

    @Test
    fun `OpenRouterProperties DEFAULT_HTTP_RETRY_BACKOFF_MS is 1000`() {
        assertEquals(1_000L, OpenRouterProperties.DEFAULT_HTTP_RETRY_BACKOFF_MS)
    }

    @Test
    fun `OpenRouterProperties custom values override defaults`() {
        val props =
            OpenRouterProperties(
                nonToolCapablePatterns = listOf("custom-pattern"),
                httpVersion = HttpClient.Version.HTTP_2,
                httpMaxRetries = 7,
                httpRetryBackoffMs = 5_000L,
            )
        assertEquals(listOf("custom-pattern"), props.nonToolCapablePatterns)
        assertEquals(HttpClient.Version.HTTP_2, props.httpVersion)
        assertEquals(7, props.httpMaxRetries)
        assertEquals(5_000L, props.httpRetryBackoffMs)
    }

    @Test
    fun `JiraEndpoints default to cloud and server endpoint lists`() {
        val endpoints = JiraEndpoints()
        assertEquals(JiraEndpoints.DEFAULT_CLOUD_ENDPOINTS, endpoints.cloud)
        assertEquals(JiraEndpoints.DEFAULT_SERVER_ENDPOINTS, endpoints.server)
        assertTrue(endpoints.cloud.isNotEmpty())
        assertTrue(endpoints.server.isNotEmpty())
    }

    @Test
    fun `JiraEndpoints DEFAULT_CLOUD_ENDPOINTS contains expected entries`() {
        val cloud = JiraEndpoints.DEFAULT_CLOUD_ENDPOINTS
        assertTrue(cloud.any { it.method == "GET" && it.path.contains("/search") })
    }

    @Test
    fun `JiraEndpoints DEFAULT_SERVER_ENDPOINTS contains expected entries`() {
        val server = JiraEndpoints.DEFAULT_SERVER_ENDPOINTS
        assertTrue(server.any { it.method == "GET" && it.path.contains("/search") })
    }

    @Test
    fun `JiraEndpoints custom lists override defaults`() {
        val custom = JiraEndpointSpec("PATCH", "/custom/path")
        val endpoints =
            JiraEndpoints(
                cloud = listOf(custom),
                server = listOf(custom),
            )
        assertEquals(listOf(custom), endpoints.cloud)
        assertEquals(listOf(custom), endpoints.server)
    }

    @Test
    fun `JiraProperties has expected defaults`() {
        val props = JiraProperties()
        assertEquals(JiraEndpoints(), props.endpoints)
    }

    @Test
    fun `StackLensProperties default dataDir`() {
        val props = StackLensProperties()
        assertEquals("./data", props.dataDir)
    }

    @Test
    fun `StackLensProperties custom dataDir`() {
        val props = StackLensProperties(dataDir = "/custom/dir")
        assertEquals("/custom/dir", props.dataDir)
    }

    @Test
    fun `JiraEndpointSpec stores method and path`() {
        val spec = JiraEndpointSpec("POST", "/foo/bar")
        assertEquals("POST", spec.method)
        assertEquals("/foo/bar", spec.path)
    }
}
