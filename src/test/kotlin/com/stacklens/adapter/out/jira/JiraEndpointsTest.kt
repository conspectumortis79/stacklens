package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class JiraEndpointsTest {
    @Test
    fun `JiraEndpointSpec equals null and wrong type`() {
        val a = JiraEndpointSpec("GET", "/api")
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
    }

    @Test
    fun `JiraEndpointSpec per-field equals branches`() {
        val a = JiraEndpointSpec("GET", "/api")
        assertNotEquals(a, a.copy(method = "POST"))
        assertNotEquals(a, a.copy(path = "/other"))
        assertEquals(a, a.copy())
    }

    @Test
    fun `JiraEndpointSpec destructuring covers componentN`() {
        val s = JiraEndpointSpec("POST", "/rest/api/3/search")
        val (method, path) = s
        assertEquals("POST", method)
        assertEquals("/rest/api/3/search", path)
    }

    @Test
    fun `JiraEndpointSpec toString and hashCode`() {
        val a = JiraEndpointSpec("GET", "/api")
        assertNotNull(a.toString())
        assertEquals(a.hashCode(), a.copy().hashCode())
    }

    @Test
    fun `JiraEndpoints equals null and wrong type`() {
        val a = JiraEndpoints()
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
    }

    @Test
    fun `JiraEndpoints per-field equals branches`() {
        val a = JiraEndpoints()
        assertNotEquals(a, a.copy(cloud = emptyList()))
        assertNotEquals(a, a.copy(server = emptyList()))
        assertEquals(a, a.copy())
    }

    @Test
    fun `JiraEndpoints destructuring covers componentN`() {
        val e = JiraEndpoints()
        val (cloud, server) = e
        assertEquals(JiraEndpoints.DEFAULT_CLOUD_ENDPOINTS, cloud)
        assertEquals(JiraEndpoints.DEFAULT_SERVER_ENDPOINTS, server)
    }

    @Test
    fun `JiraEndpoints toString and hashCode`() {
        val a = JiraEndpoints()
        assertNotNull(a.toString())
        assertEquals(a.hashCode(), a.copy().hashCode())
    }

    @Test
    fun `JiraEndpoints DEFAULT_CLOUD_ENDPOINTS contains expected endpoints`() {
        val cloud = JiraEndpoints.DEFAULT_CLOUD_ENDPOINTS
        assertEquals(6, cloud.size)
        assertEquals("GET", cloud[0].method)
        assertEquals("/rest/api/3/search/jql", cloud[0].path)
    }

    @Test
    fun `JiraEndpoints DEFAULT_SERVER_ENDPOINTS contains expected endpoints`() {
        val server = JiraEndpoints.DEFAULT_SERVER_ENDPOINTS
        assertEquals(3, server.size)
        assertEquals("GET", server[0].method)
        assertEquals("/rest/api/2/search", server[0].path)
    }

    @Test
    fun `JiraEndpoints getters cover cloud and server property accessors`() {
        val e = JiraEndpoints()
        assertEquals(JiraEndpoints.DEFAULT_CLOUD_ENDPOINTS, e.cloud)
        assertEquals(JiraEndpoints.DEFAULT_SERVER_ENDPOINTS, e.server)
    }

    @Test
    fun `JiraEndpointSpec getters cover method and path property accessors`() {
        val s = JiraEndpointSpec("POST", "/api/search")
        assertEquals("POST", s.method)
        assertEquals("/api/search", s.path)
    }

    @Test
    fun `default endpoint lists are built from the centralized path constants`() {
        val cloudPaths = JiraEndpoints.DEFAULT_CLOUD_ENDPOINTS.map { it.path }.toSet()
        val serverPaths = JiraEndpoints.DEFAULT_SERVER_ENDPOINTS.map { it.path }.toSet()

        assertEquals(
            setOf(
                JiraEndpoints.SEARCH_PATH_V3,
                JiraEndpoints.SEARCH_PATH_V2,
                JiraEndpoints.SEARCH_PATH_V3_LEGACY,
            ),
            cloudPaths,
        )
        assertEquals(setOf(JiraEndpoints.SEARCH_PATH_V2, JiraEndpoints.SEARCH_PATH_V3_LEGACY), serverPaths)
    }

    @Test
    fun `default resolver searchPath matches the centralized endpoint constants`() {
        val resolver = DefaultJiraApiVersionResolver()

        assertEquals(JiraEndpoints.SEARCH_PATH_V3, resolver.searchPath(config(cloudMode = true)))
        assertEquals(JiraEndpoints.SEARCH_PATH_V2, resolver.searchPath(config(cloudMode = false)))
    }

    private fun config(cloudMode: Boolean) =
        JiraConfig(
            name = "jira",
            baseUrl = "https://jira.example.com",
            username = "u",
            apiToken = "t",
            cloudMode = cloudMode,
        )
}
