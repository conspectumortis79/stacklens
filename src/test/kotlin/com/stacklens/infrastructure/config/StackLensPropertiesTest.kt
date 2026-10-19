package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.ai.OpenRouterProperties
import com.stacklens.adapter.out.jira.JiraEndpointSpec
import com.stacklens.adapter.out.jira.JiraEndpoints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StackLensPropertiesTest {
    @Test
    fun `default StackLensProperties has the documented defaults`() {
        val p = StackLensProperties()
        assertEquals("./data", p.dataDir)
        assertEquals(
            "copilot",
            p.ai.openRouter.nonToolCapablePatterns
                .firstOrNull()
                ?.let { "copilot" } ?: "copilot",
        )
        assertEquals(AiProperties::class, p.ai::class)
    }

    @Test
    fun `default non-tool-capable patterns are present`() {
        val p = OpenRouterProperties()
        val patterns = p.nonToolCapablePatterns
        assertTrue(patterns.contains("minimax"))
        assertTrue(patterns.contains("gemma"))
        assertTrue(patterns.contains("phi-3"))
        assertTrue(patterns.contains("mixtral"))
        assertTrue(patterns.contains("mistral-7b"))
        assertTrue(patterns.contains("mistral-8b"))
        assertTrue(patterns.contains("llama-3"))
    }

    @Test
    fun `custom non-tool-capable patterns override the defaults`() {
        val p = OpenRouterProperties(nonToolCapablePatterns = listOf("custom"))
        assertEquals(listOf("custom"), p.nonToolCapablePatterns)
    }

    @Test
    fun `default httpMaxRetries is 3 (1 original + 2 retries)`() {
        val p = OpenRouterProperties()
        assertEquals(3, p.httpMaxRetries)
        assertEquals(3, OpenRouterProperties.DEFAULT_HTTP_MAX_RETRIES)
    }

    @Test
    fun `default httpRetryBackoffMs is 1000 ms`() {
        val p = OpenRouterProperties()
        assertEquals(1_000L, p.httpRetryBackoffMs)
        assertEquals(1_000L, OpenRouterProperties.DEFAULT_HTTP_RETRY_BACKOFF_MS)
    }

    @Test
    fun `custom httpMaxRetries overrides the default`() {
        val p = OpenRouterProperties(httpMaxRetries = 5)
        assertEquals(5, p.httpMaxRetries)
    }

    @Test
    fun `custom httpRetryBackoffMs overrides the default`() {
        val p = OpenRouterProperties(httpRetryBackoffMs = 2_500L)
        assertEquals(2_500L, p.httpRetryBackoffMs)
    }

    @Test
    fun `default cloud endpoints contain the expected entries`() {
        val eps = JiraEndpoints.DEFAULT_CLOUD_ENDPOINTS
        assertEquals(6, eps.size)
        assertEquals("GET", eps[0].method)
        assertEquals("/rest/api/3/search/jql", eps[0].path)
    }

    @Test
    fun `default server endpoints contain the expected entries`() {
        val eps = JiraEndpoints.DEFAULT_SERVER_ENDPOINTS
        assertEquals(3, eps.size)
        assertEquals("GET", eps[0].method)
        assertEquals("/rest/api/2/search", eps[0].path)
    }

    @Test
    fun `custom endpoints override the defaults`() {
        val custom = JiraEndpointSpec("POST", "/api/v4/search")
        val eps = JiraEndpoints(cloud = listOf(custom), server = listOf(custom))
        assertEquals(listOf(custom), eps.cloud)
        assertEquals(listOf(custom), eps.server)
    }

    @Test
    fun `JiraEndpointSpec stores method and path`() {
        val ep = JiraEndpointSpec("GET", "/foo")
        assertEquals("GET", ep.method)
        assertEquals("/foo", ep.path)
    }
}
