package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoGrafanaEndpointResolver
import com.stacklens.adapter.out.demo.DemoHostResolver
import com.stacklens.adapter.out.demo.DemoRuntimeMode
import com.stacklens.application.port.out.platform.DemoGrafanaEndpointPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DemoGrafanaEndpointResolverTest {
    private var ambientDockerFlag: String? = null

    @BeforeEach
    fun setUp() {
        ambientDockerFlag = System.getProperty(DOCKER_FLAG_PROPERTY)
        System.setProperty(DOCKER_FLAG_PROPERTY, "false")
    }

    @AfterEach
    fun tearDown() {
        val ambient = ambientDockerFlag
        if (ambient == null) {
            System.clearProperty(DOCKER_FLAG_PROPERTY)
        } else {
            System.setProperty(DOCKER_FLAG_PROPERTY, ambient)
        }
    }

    private fun inDockerContainer() {
        System.setProperty(DOCKER_FLAG_PROPERTY, "true")
    }

    private fun resolver(port: String? = null) =
        DemoGrafanaEndpointResolver(DemoHostResolver()) {
            if (it == DemoGrafanaEndpointResolver.GRAFANA_DEMO_PORT_ENV) port else null
        }

    @Test
    fun `the base url falls back to the default grafana port outside a container`() {
        assertEquals("http://localhost:3000", resolver().baseUrl())
    }

    @Test
    fun `the base url follows the configured demo port`() {
        assertEquals("http://localhost:3001", resolver("3001").baseUrl())
    }

    @Test
    fun `the base url uses the docker host inside a container`() {
        inDockerContainer()

        assertEquals("http://host.docker.internal:3001", resolver("3001").baseUrl())
    }

    @Test
    fun `a stale demo url is moved to the configured demo port`() {
        assertEquals("http://localhost:3001", resolver("3001").alignedUrl("http://localhost:3000"))
    }

    @Test
    fun `a stale demo url is moved to the docker host and demo port inside a container`() {
        inDockerContainer()

        assertEquals(
            "http://host.docker.internal:3001",
            resolver("3001").alignedUrl("http://localhost:3000"),
        )
    }

    @Test
    fun `an already aligned url is returned unchanged`() {
        assertEquals("http://localhost:3001", resolver("3001").alignedUrl("http://localhost:3001"))
    }

    @Test
    fun `urls without an explicit port keep their form while the default port applies`() {
        assertEquals("http://localhost", resolver().alignedUrl("http://localhost"))
    }

    @Test
    fun `a missing port is completed with the configured demo port`() {
        assertEquals("http://localhost:3001", resolver("3001").alignedUrl("http://localhost"))
    }

    @Test
    fun `non demo hosts are never moved to the demo port`() {
        assertEquals(
            "https://grafana.example.com:3000",
            resolver("3001").alignedUrl("https://grafana.example.com:3000"),
        )
    }

    @Test
    fun `urls without a host are returned unchanged`() {
        listOf("/grafana/dashboards", "mailto:ops@example.com").forEach { url ->
            assertEquals(url, resolver("3001").alignedUrl(url), url)
        }
    }

    @Test
    fun `a demo host written in mixed case is aligned to the canonical host and port`() {
        assertEquals("http://localhost:3001", resolver("3001").alignedUrl("http://LocalHost:3000"))
    }

    @Test
    fun `unparsable urls are returned unchanged`() {
        assertEquals("not a url", resolver("3001").alignedUrl("not a url"))
    }

    @Test
    fun `blank or invalid port values fall back to the default port`() {
        listOf("", "abc", "0", "70000").forEach { value ->
            assertEquals("http://localhost:3000", resolver(value).baseUrl(), value)
        }
    }

    @Test
    fun `the resolver fulfils the demo grafana endpoint port`() {
        val endpoint: DemoGrafanaEndpointPort = resolver("3001")

        assertEquals(endpoint.baseUrl(), endpoint.alignedUrl(endpoint.baseUrl()))
    }

    private companion object {
        const val DOCKER_FLAG_PROPERTY = DemoRuntimeMode.DOCKER_FLAG_PROPERTY
    }
}
