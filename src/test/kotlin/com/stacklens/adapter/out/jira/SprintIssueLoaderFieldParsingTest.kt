package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.testsupport.CompanionAccessor
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class SprintIssueLoaderFieldParsingTest {
    private lateinit var server: MockWebServer
    private lateinit var loader: SprintIssueLoader
    private val mapper = ObjectMapper()

    @BeforeEach fun setUp() {
        server = MockWebServer()
        server.start()
        loader = SprintIssueLoader(JiraTicketHttpClient(connectTimeoutMs = 1000, readTimeoutMs = 1000))
    }

    @AfterEach fun tearDown() {
        server.shutdown()
    }

    private fun config(storyPointsFieldId: String = "customfield_10016"): JiraConfig =
        JiraConfig(
            id = 3L,
            name = "banana",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "DEV",
            username = "user",
            apiToken = "tok",
            cloudMode = true,
            storyPointsFieldId = storyPointsFieldId,
        )

    private fun route(
        search: MockResponse,
        fields: MockResponse = MockResponse().setResponseCode(200).setBody("[]"),
    ) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path.orEmpty().endsWith("/field")) {
                        fields
                    } else {
                        search
                    }
            }
    }

    private fun json(body: String): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun issue(
        key: String,
        fields: String,
    ): String = """{"key":"$key","fields":{$fields}}"""

    private fun searchPage(
        vararg issues: String,
        total: Int = issues.size,
    ): String = """{"issues":[${issues.joinToString(",")}],"total":$total}"""

    private fun parseSingleNode(body: String): Double? = ReflectionTestUtils.invokeMethod<Double?>(loader, "parseSingleNode", mapper.readTree(body))

    private fun parseTicket(
        body: String,
        cfg: JiraConfig = config(),
    ): SprintReviewTicket = ReflectionTestUtils.invokeMethod(loader, "jsonToSprintTicket", mapper.readTree(body), cfg)!!

    @Test fun `a failing jql search reports the http status`() {
        route(MockResponse().setResponseCode(500).setBody("""{"errorMessages":["boom"]}"""))

        val failure =
            assertThrows(IllegalStateException::class.java) { loader.fetch(config(), 42L) }

        assertTrue(failure.message!!.contains("HTTP 500"))
    }

    @Test fun `a transport error without a status code is reported as zero`() {
        server.shutdown()

        val failure =
            assertThrows(IllegalStateException::class.java) { loader.fetch(config(), 42L) }

        assertTrue(failure.message!!.contains("HTTP 0"))
    }

    @Test fun `an empty search response stops the pagination loop`() {
        route(MockResponse().setResponseCode(204))

        assertTrue(loader.fetch(config(), 42L).isEmpty())
    }

    @Test fun `a search response without an issue array stops the loop`() {
        route(json("""{"issues":{"nope":true}}"""))

        assertTrue(loader.fetch(config(), 42L).isEmpty())
    }

    @Test fun `pages are requested until the total is reached`() {
        val first = (1..50).map { issue("DEV-$it", """"summary":"s"""") }
        val second = (51..60).map { issue("DEV-$it", """"summary":"s"""") }
        val queue =
            mutableListOf(
                json(searchPage(*first.toTypedArray(), total = 60)),
                json(searchPage(*second.toTypedArray(), total = 60)),
            )
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path.orEmpty().endsWith("/field")) {
                        json("[]")
                    } else {
                        queue.removeAt(0)
                    }
            }

        val tickets = loader.fetch(config(), 42L)

        assertEquals(60, tickets.size)
    }

    @Test fun `sub tasks are filtered out while short pages end the loop`() {
        route(
            json(
                searchPage(
                    issue("DEV-1", """"summary":"story","issuetype":{"name":"Story"}"""),
                    issue("DEV-2", """"summary":"child","issuetype":{"name":"Sub-task","subtask":true}"""),
                    total = 99,
                ),
            ),
        )

        val tickets = loader.fetch(config(), 42L)

        assertEquals(listOf("DEV-1"), tickets.map { it.key })
    }

    @Test fun `ticket fields fall back to blanks and labels drop empty entries`() {
        val ticket =
            parseTicket(
                issue(
                    "DEV-9",
                    """
                    "summary":"Suche",
                    "labels":["kb",null,"  ","import"],
                    "assignee":{"displayName":"  "},
                    "priority":{"name":""},
                    "customfield_10016":5,
                    "customfield_10099":"nicht numerisch",
                    "customfield_10098":null
                    """.trimIndent(),
                ),
            )

        assertEquals("DEV-9", ticket.key)
        assertEquals(listOf("kb", "import"), ticket.labels)
        assertNull(ticket.assignee)
        assertNull(ticket.priority)
        assertEquals(5.0, ticket.storyPoints)
        assertEquals(listOf("customfield_10016: 5.0"), ticket.numericCustomFields)
        assertEquals("${config().baseUrl}/browse/DEV-9", ticket.url)
    }

    @Test fun `an explicit story points field id is used without any metadata lookup`() {
        val resolved =
            ReflectionTestUtils.invokeMethod<String>(
                loader,
                "resolveStoryPointsFieldId",
                config(storyPointsFieldId = "customfield_7"),
                "DEV-1",
            ) as String

        assertEquals("customfield_7", resolved)
    }

    @Test fun `a blank story points field id triggers the metadata lookup`() {
        route(
            json(searchPage(issue("DEV-1", """"summary":"s","customfield_55":3.5"""))),
            json("""[{"id":"customfield_55","name":"Story points","schema":{"type":"number"}}]"""),
        )

        val tickets = loader.fetch(config(storyPointsFieldId = "  "), 42L)

        assertEquals(3.5, tickets.single().storyPoints)
    }

    @Test fun `field metadata that is unreadable falls back to the default id`() {
        route(json(searchPage(issue("DEV-1", """"summary":"s""""))), MockResponse().setResponseCode(500))

        assertEquals(1, loader.fetch(config(storyPointsFieldId = "  "), 42L).size)
    }

    @Test fun `field metadata without a usable name falls back to the default id`() {
        val detected =
            ReflectionTestUtils.invokeMethod<String>(
                loader,
                "detectStoryPointsFieldId",
                config(storyPointsFieldId = "  "),
                "DEV-1",
            ) as String

        assertEquals("customfield_10016", detected)
    }

    @Test fun `numeric field metadata is matched by its display name`() {
        route(
            MockResponse().setResponseCode(204),
            json(
                """
                [
                  {"id":"customfield_1","name":"Feld","schema":{"type":"string"}},
                  {"id":"customfield_2","name":"Sprint","schema":{"type":"date"}},
                  {"id":"customfield_3","name":"Story Point Estimate","schema":{"type":"number"}},
                  {"id":"customfield_4","name":"SP Estimate","schema":{"type":"integer"}},
                  {"id":"customfield_5","name":"Effort points","schema":{"type":"double"}},
                  {"id":"customfield_6","name":"Points","schema":{"type":"number"}},
                  {"id":"customfield_7","name":"Story Point Size","schema":{"type":"number"}}
                ]
                """.trimIndent(),
            ),
        )

        assertEquals("customfield_3", detectFieldIdWithoutConfig())
    }

    private fun detectFieldIdWithoutConfig(): String =
        ReflectionTestUtils.invokeMethod<String>(
            loader,
            "detectStoryPointsFieldId",
            config(storyPointsFieldId = ""),
            "DEV-1",
        )!!

    @Test fun `story points values are read from numbers objects and text`() {
        assertEquals(3.0, parseSingleNode("3"))
        assertEquals(3.5, parseSingleNode("3.5"))
        assertEquals(4.25, parseSingleNode("""{"value":"4,25"}"""))
        assertEquals(6.0, parseSingleNode("""{"amount":6}"""))
        assertEquals(7.0, parseSingleNode("""{"value":" ","estimate":7}"""))
        assertEquals(8.0, parseSingleNode("""{"number":"8"}"""))
        assertEquals(9.5, parseSingleNode(""""9,5""""))
        assertNull(parseSingleNode("""{"value":"x"}"""))
        assertNull(parseSingleNode("""{"estimate":{"nested":1}}"""))
        assertNull(parseSingleNode("{}"))
        assertNull(parseSingleNode("null"))
        assertNull(parseSingleNode("""[""]"""))
        assertNull(parseSingleNode("true"))
        assertNull(parseSingleNode(""""text""""))
        assertEquals(1.0, parseSingleNode("""{"value":1,"amount":2}"""))
    }

    @Test fun `story points fall back through the known custom fields`() {
        val fields = mapper.readTree("""{"customfield_10016":null,"customfield_10026":"2,5"}""")
        val value =
            ReflectionTestUtils.invokeMethod(loader, "parseStoryPointsValue", fields, "customfield_10016") as Double?

        assertEquals(2.5, value)
    }

    @Test fun `the fallback ids and name patterns are the documented ones`() {
        val fallbacks =
            CompanionAccessor.read(SprintIssueLoader::class.java, "getDEFAULT_STORY_POINTS_FALLBACK_IDS") as List<*>
        val patterns =
            CompanionAccessor.read(SprintIssueLoader::class.java, "getSTORY_POINTS_NAME_PATTERNS") as List<*>

        assertEquals(
            listOf(
                "customfield_10016",
                "customfield_10026",
                "customfield_10004",
                "customfield_10002",
                "customfield_10028",
            ),
            fallbacks,
        )
        assertEquals(6, patterns.size)
        assertTrue(patterns.all { it is Regex })
    }
}
