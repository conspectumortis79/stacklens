package com.stacklens.adapter.out.demo

import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DemoJiraSimulationTest {
    private fun config(name: String) = JiraConfig(name = name, baseUrl = "http://localhost:8081", apiToken = "token")

    @Test
    fun `a config without a name is not a demo config`() {
        val config = mock<JiraConfig>()
        doReturn(null).whenever(config).name

        assertFalse(DemoJiraSimulation.isDemo(config))
    }

    @Test
    fun `a config whose name carries the demo marker is a demo config`() {
        assertTrue(DemoJiraSimulation.isDemo(config("Demo Jira")))
    }

    @Test
    fun `a config with an unrelated name is not a demo config`() {
        assertFalse(DemoJiraSimulation.isDemo(config("Production Jira")))
    }
}
