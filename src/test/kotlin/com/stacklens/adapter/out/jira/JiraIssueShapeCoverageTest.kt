package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.JiraConfig
import com.stacklens.testsupport.PrivateInvocation
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JiraIssueShapeCoverageTest {
    private lateinit var server: MockWebServer
    private val mapper = ObjectMapper()

    private lateinit var config: JiraConfig

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
        config =
            JiraConfig(
                id = 1L,
                name = "jira",
                baseUrl = server.url("/").toString().trimEnd('/'),
                username = "u",
                apiToken = "t",
            )
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun http() = JiraTicketHttpClient(connectTimeoutMs = 1000, readTimeoutMs = 1000)

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun configWithStoryPointsField(value: String): JiraConfig =
        JiraConfig(
            id = config.id,
            name = config.name,
            baseUrl = config.baseUrl,
            username = config.username,
            apiToken = config.apiToken,
            storyPointsFieldId = value,
        )

    @Test
    fun `whitespace labels never become ticket labels`() {
        val issue = mapper.readTree("""{"key":"P-1","fields":{"summary":"s","labels":["ok","","   ","x"]}}""")

        val ticket = PrivateInvocation.invoke(SprintIssueLoader(http()), "jsonToSprintTicket", issue, config)!!

        assertEquals(listOf("ok", "x"), PrivateInvocation.invoke(ticket, "getLabels"))
    }

    @Test
    fun `a configured story points field wins over any detection`() {
        val resolved =
            PrivateInvocation.invoke(
                SprintIssueLoader(http()),
                "resolveStoryPointsFieldId",
                configWithStoryPointsField("customfield_points"),
                "P-1",
            )

        assertEquals("customfield_points", resolved)
    }

    @Test
    fun `a blank story points field is treated as unconfigured`() {
        server.enqueue(json("""{"id":"customfield_99","name":"Story Points"}"""))

        runCatching {
            PrivateInvocation.invoke(SprintIssueLoader(http()), "resolveStoryPointsFieldId", configWithStoryPointsField("  "), "P-1")
        }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an empty story points field counts as unconfigured`() {
        server.enqueue(json("""{"id":"customfield_77","name":"Story points"}"""))

        PrivateInvocation.invoke(
            SprintIssueLoader(http()),
            "resolveStoryPointsFieldId",
            configWithStoryPointsField(""),
            "P-1",
        )

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a sprint that shows up in several issues is only resolved once`() {
        server.enqueue(
            json(
                """{"issues":[
                  {"key":"A-1","fields":{"project":{"key":"A"},"sprint":[{"id":10,"name":"Sprint One","state":"active"}]}},
                  {"key":"A-2","fields":{"project":{"key":"A"},"sprint":[{"id":10,"name":"Sprint One","state":"active"}]}},
                  {"key":"C-1","fields":{"project":{"key":"C"},"sprint":[{"id":11,"name":"Sprint Two","state":"active"}]}}
                ]}""",
            ),
        )

        val resolved = SprintResolver(http()).resolve(config, "sprint one")

        assertEquals(10L, resolved.id)
        assertEquals("Sprint One", resolved.name)
    }

    @Test
    fun `a full page keeps the key lister paging until the total is reached`() {
        server.enqueue(json("""{"total":4,"issues":[{"key":"P-1"},{"key":"P-2"}]}"""))
        server.enqueue(json("""{"total":4,"issues":[{"key":"P-3"},{"key":"P-4"}]}"""))
        val lister = JiraSprintIssueKeyLister(http())

        val keys = lister.listTopLevelIssueKeys(config, sprintId = 42L, pageSize = 2)

        assertEquals(listOf("P-1", "P-2", "P-3", "P-4"), keys)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `inline italics are rendered as em marks`() {
        val runs = JiraMarkdownConverter().inlineToAdf("_emphasis_ and **strong** and `code`")

        assertEquals(
            listOf("em", "strong", "code"),
            runs.mapNotNull { (it["marks"] as? List<*>)?.singleOrNull() }.map { (it as Map<*, *>)["type"] },
        )
    }
}
