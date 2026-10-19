package com.stacklens.application.service.config
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraConnectionTestPort
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JiraConfigServiceTest {
    private val connectionTest: JiraConnectionTestPort = mock()
    private val jiraConfigRepo: JiraConfigPort = mock()

    private val service = JiraConfigService(jiraConfigRepo, PassthroughDemoConfigFilter.instance, connectionTest)

    private val jira =
        com.stacklens.domain.model.JiraConfig(
            name = "j",
            baseUrl = "u",
            username = "u",
            apiToken = "t",
        )

    @Test
    fun `listJiraConfigs returns every stored config`() {
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(listOf(jira))
        assertEquals(listOf(jira), service.listJiraConfigs())
    }

    @Test
    fun `findJiraConfig returns the config of the requested id`() {
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(jira)
        assertEquals(jira, service.findJiraConfig(1L))
    }

    @Test
    fun `saveJiraConfig stores and returns the config`() {
        whenever(jiraConfigRepo.saveJiraConfig(jira)).thenReturn(jira)
        assertEquals(jira, service.saveJiraConfig(jira))
    }

    @Test
    fun `deleteJiraConfig removes the config of the requested id`() {
        service.deleteJiraConfig(1L)
        verify(jiraConfigRepo).deleteJiraConfigById(1L)
    }

    @Test
    fun `deleteAllJiraConfigs empties the store`() {
        service.deleteAllJiraConfigs()
        verify(jiraConfigRepo).deleteAllJiraConfigs()
    }

    @Test
    fun `testJiraConnection forwards the stored config to the connection port`() {
        val config =
            com.stacklens.domain.model
                .JiraConfig(name = "j", baseUrl = "u", projectKey = "p", username = "u", apiToken = "t")
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(config)
        whenever(connectionTest.testConnection(config, null)).thenReturn(JiraConnectionTestPort.TestResult.Success(payload))

        assertEquals(payload, service.testJiraConnection(1L, null))
    }

    @Test
    fun `testJiraConnection reports a missing config as null`() {
        whenever(jiraConfigRepo.findJiraConfigById(99L)).thenReturn(null)

        assertNull(service.testJiraConnection(1L, null))
    }

    @Test
    fun `listVisibleJiraConfigs keeps every config when the test environment is on`() {
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(
            listOf(
                com.stacklens.domain.model
                    .JiraConfig(name = "j", baseUrl = "u", projectKey = "p", username = "u", apiToken = "t"),
            ),
        )

        assertEquals(1, service.listVisibleJiraConfigs().size)
    }

    private val payload: Map<String, Any> = mapOf("success" to true)
}
