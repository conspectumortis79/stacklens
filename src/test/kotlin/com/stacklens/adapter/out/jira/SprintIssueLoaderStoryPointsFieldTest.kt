package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class SprintIssueLoaderStoryPointsFieldTest {
    private val server = MockWebServer()
    private val http: JiraTicketHttpClient = mock()
    private val versionResolver: JiraApiVersionResolver = mock()
    private val loader = SprintIssueLoader(http, versionResolver)

    @BeforeEach
    fun start() {
        server.start()
        whenever(versionResolver.apiVersion(any())).thenReturn("3")
        whenever(http.buildClient(any()))
            .thenReturn(WebClient.builder().baseUrl(server.url("/").toString().trimEnd('/')).build())
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun config() =
        JiraConfig(
            name = "jira",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
            active = true,
        )

    private fun detect(field: String): String {
        val method =
            SprintIssueLoader::class.java.getDeclaredMethod(
                "detectStoryPointsFieldId",
                JiraConfig::class.java,
                String::class.java,
            )
        method.isAccessible = true
        return method.invoke(loader, config(), field) as String
    }

    @Test
    fun `an empty field answer keeps the default story points field`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        assertEquals("customfield_10016", detect("PROJ-1"))
    }

    @Test
    fun `a matching numeric field wins over the default`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """[{"id":"customfield_10014","name":"Story point estimate","schema":{"type":"number"}}]""",
                ),
        )

        assertNotEquals("customfield_10016", detect("PROJ-2"))
    }

    @Test
    fun `a failing field lookup falls back to the default id`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))

        assertEquals("customfield_10016", detect("PROJ-3"))
    }

    @Test
    fun `fields without a numeric schema are ignored`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """[{"id":"customfield_1","name":"Story Points","schema":{"type":"string"}},"not-a-field"]""",
                ),
        )

        assertEquals("customfield_10016", detect("PROJ-4"))
    }
}
