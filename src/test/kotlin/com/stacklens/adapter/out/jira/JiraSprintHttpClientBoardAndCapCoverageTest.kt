package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraSprintIssueFieldResolver
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.sprintIdFromRegex
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JiraSprintHttpClientBoardAndCapCoverageTest {
    private lateinit var server: MockWebServer

    private lateinit var client: JiraSprintHttpClient

    private val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = JiraSprintHttpClient(JiraSprintIssueFieldResolver(), 5_000, 5_000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config() =
        JiraConfig(
            id = 1L,
            name = "jira",
            baseUrl = baseUrl,
            username = "u",
            apiToken = "t",
        )

    private fun route(handler: (String) -> MockResponse) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = handler(request.path.orEmpty())
            }
    }

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `a board answer without a body reports the missing active sprint`() {
        route { MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json") }

        val failure = assertThrows(IllegalStateException::class.java) { client.fetchSprintInfo(config(), "$baseUrl/boards/7") }

        assertTrue(failure.message!!.contains("No active sprint found for board 7"), failure.message)
    }

    @Test
    fun `a board answer with an empty sprint list reports the missing active sprint`() {
        route { path -> if (path.contains("/sprint?")) json("""{"values":[]}""") else json("{}") }

        assertThrows(IllegalStateException::class.java) { client.fetchSprintInfo(config(), "$baseUrl/boards/7") }
    }

    @Test
    fun `the first listed sprint wins the lookup`() {
        route { path ->
            when {
                path.contains("/sprint?") -> json("""{"values":[{"id":42,"name":"Sprint 42","goal":"g","state":"active"}]}""")
                else -> json("""{"id":42,"name":"Sprint 42","state":"active"}""")
            }
        }

        assertEquals(42L, client.fetchSprintInfo(config(), "$baseUrl/boards/7").id)
    }

    @Test
    fun `pagination stops at the five hundred issue cap`() {
        fun page(startAt: Int): String {
            val issues =
                (0 until 100).joinToString(",") { index ->
                    """{"key":"K${startAt + index}","fields":{"summary":"s $index"}}"""
                }
            return """{"total":1000,"startAt":$startAt,"issues":[$issues]}"""
        }
        route { path -> json(page(path.substringAfter("startAt=").substringBefore("&").toInt())) }

        val issues = client.fetchSprintIssues(config(), "$baseUrl/sprints/42")

        assertEquals(500, issues.size)
    }

    @Test
    fun `a matcher without a capture group yields no sprint id`() {
        val regex = Regex("""/boards/""")

        assertTrue(
            sprintIdFromRegex(regex, "https://x/boards/7") == null,
        )
        assertTrue(
            sprintIdFromRegex(Regex("""/boards/(\d+)"""), "https://x/boards/7") == 7L,
        )
        assertTrue(
            sprintIdFromRegex(regex, "https://x/nothing") == null,
        )
    }

    @Test
    fun `a description node that is neither text, array nor object flattens to nothing`() {
        route { path ->
            when {
                "/issue" in path ->
                    json(
                        """
                        {"total":1,"issues":[{"key":"K1","fields":{"summary":"s","description":42,
                        "comment":{"comments":[{"author":{"displayName":"A"},"body":7}]}}}]}
                        """.trimIndent(),
                    )
                else -> json("""{"id":42,"name":"Sprint 42","state":"active"}""")
            }
        }

        val issues = client.fetchSprintIssues(config(), "$baseUrl/sprints/42")

        assertEquals(1, issues.size)
        assertEquals("", issues.single().description)
        assertTrue(issues.single().comments.contains("A:"))
    }
}
