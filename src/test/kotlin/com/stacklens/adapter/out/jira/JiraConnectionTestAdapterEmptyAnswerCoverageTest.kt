package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.JiraConnectionTestPort.TestResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.stubbing.Answer
import org.springframework.web.reactive.function.client.WebClient

class JiraConnectionTestAdapterEmptyAnswerCoverageTest {
    private val i18n: I18nPort =
        mock(
            defaultAnswer =
                Answer { invocation ->
                    when {
                        invocation.method.returnType == String::class.java && invocation.arguments.isNotEmpty() ->
                            invocation.arguments[0] as? String ?: ""
                        else -> null
                    }
                },
        )

    private val adapter = JiraConnectionTestAdapter(ObjectMapper(), i18n, WebClient.builder())

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun serverJiraConfig() =
        JiraConfig(
            id = 1L,
            name = "Test Jira",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "PROJ",
            username = "user@example.com",
            apiToken = "mytoken",
            cloudMode = false,
            active = true,
        )

    @Test
    fun `a search answer without any body leaves no issue count`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = adapter.testConnection(serverJiraConfig(), null)

        val payload = (result as TestResult.Success).payload
        assertEquals(0, payload["totalAccessibleIssues"])
        assertEquals("config.be_jira_conn_success", payload["message"])
        assertTrue(payload.containsKey("apiVersion"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a redirection answer is not a usable search result`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://jira.example.com/login")
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[]}"""),
        )

        val result = adapter.testConnection(serverJiraConfig(), null)

        assertEquals(0, (result as TestResult.Success).payload["totalAccessibleIssues"])
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a json answer without an issues array is no usable search result`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"noise":true}"""),
        )

        val result = adapter.testConnection(serverJiraConfig(), null)

        assertEquals(0, (result as TestResult.Success).payload["totalAccessibleIssues"])
        assertEquals(1, server.requestCount)
    }
}
