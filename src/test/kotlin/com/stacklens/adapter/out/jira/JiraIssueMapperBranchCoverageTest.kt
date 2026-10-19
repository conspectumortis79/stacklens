package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class JiraIssueMapperBranchCoverageTest {
    private lateinit var server: MockWebServer

    private val mapper = JiraIssueMapper()

    private val json = ObjectMapper()

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun client(): WebClient =
        WebClient
            .builder()
            .baseUrl(server.url("").toString().removeSuffix("/"))
            .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofSeconds(5))))
            .build()

    private fun issue(
        key: String = "PROJ-1",
        summary: String? = "Login flow",
        status: String? = "In Progress",
        attachments: List<JiraClient.JiraAttachment>? = emptyList(),
    ): JiraClient.JiraIssue =
        JiraClient.JiraIssue(
            key = key,
            fields =
                JiraClient.JiraFields(
                    summary = summary,
                    status = status?.let { JiraClient.JiraNamedValue(name = it) },
                    attachment = attachments,
                ),
        )

    private fun config(): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "primary",
            baseUrl = "https://jira.example.com/",
            username = "u",
            apiToken = "t",
            projectKey = "PROJ",
        )

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setBody(body).setResponseCode(code))
    }

    @Test
    fun `excerpt prefixes an ellipsis when the match starts deep in the text`() {
        val short = json.getNodeFactory().textNode("${"lead ".repeat(30)}needle in the description")
        val excerpt = mapper.extractDescriptionExcerpt(short, "needle", 200)
        assertTrue(excerpt.startsWith("…"))
        assertTrue(excerpt.contains("needle"))

        val early = json.getNodeFactory().textNode("needle at the very start")
        assertEquals("needle at the very start", mapper.extractDescriptionExcerpt(early, "needle", 200))

        assertEquals("", mapper.extractDescriptionExcerpt(null, "needle", 200))
        assertEquals("", mapper.extractDescriptionExcerpt(json.getNodeFactory().textNode("   "), "needle", 200))
        assertEquals("", mapper.extractDescriptionExcerpt(json.getNodeFactory().textNode("nothing here"), "needle", 200))
    }

    @Test
    fun `flatten walks adf trees with mixed children`() {
        assertEquals("", mapper.flattenDescriptionNode(null))
        assertEquals("", mapper.flattenDescriptionNode(json.readTree("null")))
        assertEquals("plain", mapper.flattenDescriptionNode(json.readTree("\"plain\"")))
        assertEquals(
            "first second",
            mapper.flattenDescriptionNode(
                json.readTree("""{"text":{"nested":1},"content":[{"text":"first"},{"blank":true},{"content":[{"text":"second"}]}]}"""),
            ),
        )
        assertEquals("", mapper.flattenDescriptionNode(json.readTree("""{"type":"doc"}""")))
    }

    @Test
    fun `draw io attachments are matched by name and by content`() {
        val client = client()
        enqueue("this diagram mentions the keyword login somewhere")
        enqueue("unrelated content")
        server.enqueue(MockResponse().setResponseCode(200))
        enqueue("anything with LOGIN inside")

        val target =
            issue(
                attachments =
                    listOf(
                        JiraClient.JiraAttachment(id = "1", filename = "flow.drawio", content = "/rest/content/1"),
                        JiraClient.JiraAttachment(id = "2", filename = "ARCH.DRAWIO.XML", content = "/rest/content/2"),
                        JiraClient.JiraAttachment(id = "3", filename = "photo.png", content = "/rest/content/3"),
                        JiraClient.JiraAttachment(id = "4", filename = "login-drawio-tools.txt", content = "/rest/content/4"),
                        JiraClient.JiraAttachment(id = "5", filename = "empty.drawio", content = "   "),
                    ),
            )

        val results = mapper.findDrawIoAttachmentResults(client, target, config(), "login", 9L)
        assertEquals(2, results.size)
        assertEquals("PROJ-1", results.first().filePath)
        assertEquals("In Progress", results.first().functionName)
        assertEquals("https://jira.example.com/browse/PROJ-1", results.first().repoUrl)
        assertTrue(results.first().codeSnippet?.contains("flow.drawio") == true)

        val withoutStatusAndSummary =
            issue(summary = null, status = null, attachments = listOf(JiraClient.JiraAttachment(id = "6", filename = "login.drawio", content = "  ")))
        val fallbackResults = mapper.findDrawIoAttachmentResults(client, withoutStatusAndSummary, config(), "login", 9L)
        assertEquals(1, fallbackResults.size)
        assertNull(fallbackResults.single().functionName)
        assertTrue(fallbackResults.single().codeSnippet?.contains("PROJ-1") == true)

        enqueue("no match inside")
        assertTrue(mapper.findDrawIoAttachmentResults(client, issue(attachments = null), config(), "login", 9L).isEmpty())
    }

    @Test
    fun `draw io content lookups survive error responses`() {
        val client = client()
        enqueue("boom", code = 500)
        val target = issue(attachments = listOf(JiraClient.JiraAttachment(id = "1", filename = "diagram.drawio", content = "/rest/content/1")))
        assertTrue(mapper.findDrawIoAttachmentResults(client, target, config(), "login", 9L).isEmpty())
    }
}
