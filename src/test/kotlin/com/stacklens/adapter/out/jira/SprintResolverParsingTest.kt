package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.JiraConfig
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

class SprintResolverParsingTest {
    private val mapper = ObjectMapper()
    private lateinit var server: MockWebServer

    @BeforeEach fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach fun stopServer() {
        server.shutdown()
    }

    private fun resolver(): SprintResolver = SprintResolver(jiraTicketHttp = JiraTicketHttpClient(connectTimeoutMs = 5000, readTimeoutMs = 10000))

    private fun config(cloudMode: Boolean = true): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "cfg",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "DEV",
            username = "user",
            apiToken = "tok",
            cloudMode = cloudMode,
        )

    private fun serve(body: String): SprintResolver {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
            }
        return resolver()
    }

    private fun serveStatus(status: Int): SprintResolver {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(status)
            }
        return resolver()
    }

    private fun extractSprints(issue: JsonNode): List<Parsed> {
        val raw: List<*> = ReflectionTestUtils.invokeMethod<List<*>>(resolver(), "extractSprintsFromIssue", issue) ?: emptyList<Any>()
        return raw.map { entry -> asParsed(requireNotNull(entry)) }
    }

    private fun asParsed(target: Any): Parsed =
        Parsed(
            id = requireNotNull(ReflectionTestUtils.getField(target, "id")) as Long,
            name = requireNotNull(ReflectionTestUtils.getField(target, "name")) as String,
            state = requireNotNull(ReflectionTestUtils.getField(target, "state")) as String,
            boardId = ReflectionTestUtils.getField(target, "boardId") as Long?,
        )

    private data class Parsed(
        val id: Long,
        val name: String,
        val state: String,
        val boardId: Long?,
    )

    private fun parseBlob(blob: String): Parsed? {
        val parsed =
            ReflectionTestUtils.invokeMethod<Any>(resolver(), "parseLegacySprintBlob", blob)
                ?: return null
        return asParsed(parsed)
    }

    @Test fun `fields that are not an object yield no sprints`() {
        assertEquals(emptyList<Any>(), extractSprints(mapper.readTree("""{"fields":[]}""")))
        assertEquals(emptyList<Any>(), extractSprints(mapper.readTree("""{}""")))
    }

    @Test fun `non array and empty array custom fields are skipped`() {
        val issue =
            mapper.readTree(
                """
                {"fields":{"customfield_a":[],"customfield_b":{"nested":1},"customfield_c":"text","customfield_d":[
                  {"id":0,"name":"Zero"},
                  {"id":7,"name":"  "},
                  {"id":5},
                  {"name":"No id"},
                  {"id":7,"name":"Sprint Seven","state":"ACTIVE","boardId":0},
                  {"id":8,"name":"Sprint Eight","boardId":12}
                ]}}
                """.trimIndent(),
            )

        val sprints = extractSprints(issue)

        assertEquals(2, sprints.size)
        assertEquals("Sprint Seven", sprints[0].name)
        assertEquals("ACTIVE", sprints[0].state)
        assertNull(sprints[0].boardId)
        assertEquals(12L, sprints[1].boardId)
    }

    @Test fun `textual sprint entries are parsed as legacy blobs`() {
        val issue =
            mapper.readTree(
                """
                {"fields":{"customfield_sprint":["com.atlassian.greenhopper.service.sprint.Sprint@abc[id=42,rapidViewId=5,state=CLOSED,name=Sprint 42,goal=ship]","garbage"]}
                }
                """.trimIndent(),
            )

        val sprints = extractSprints(issue)

        assertEquals(1, sprints.size)
        assertEquals(42L, sprints.single().id)
        assertEquals("closed", sprints.single().state)
        assertEquals(5L, sprints.single().boardId)
    }

    @Test fun `legacy blobs without brackets or id attribute are rejected`() {
        assertNull(parseBlob("Sprint 42"))
        assertNull(parseBlob("Sprint [name=42]"))
        assertNull(parseBlob("id= [  ]"))
    }

    @Test fun `legacy blobs need a numeric id and a named sprint`() {
        assertNull(parseBlob("com.atlassian.Sprint@1[id=abc,name=Sprint 1]"))
        assertNull(parseBlob("com.atlassian.Sprint@1[id=7]"))
        assertNull(parseBlob("com.atlassian.Sprint@1[id=0,name=Sprint 1]"))
        assertNull(parseBlob("com.atlassian.Sprint@1[id=-3,name=Sprint 1]"))
        assertNull(parseBlob("com.atlassian.Sprint@1[id=7,name= ]"))
    }

    @Test fun `legacy blobs ignore segments without a key and missing optional attributes`() {
        val parsed = parseBlob("com.atlassian.Sprint@1[,id=7,broken,name=Sprint 7]")

        assertEquals(Parsed(7L, "Sprint 7", "unknown", null), parsed)
    }

    @Test fun `legacy blobs need the id attribute outside of another value`() {
        assertNull(parseBlob("com.atlassian.Sprint@1[sprint=id=1,name=Sprint 1]"))
        assertEquals(Parsed(7L, "Sprint 7", "unknown", null), parseBlob("com.atlassian.Sprint@1[id=7,=x,name=Sprint 7]"))
    }

    @Test fun `legacy blobs report no board when the view id is not positive`() {
        val parsed = parseBlob("com.atlassian.Sprint@1[id=7,name=Sprint 7,rapidViewId=0]")

        assertEquals(7L, parsed?.id)
        assertNull(parsed?.boardId)
    }

    @Test fun `legacy blobs report no board when the view id is not numeric`() {
        val parsed = parseBlob("com.atlassian.Sprint@1[id=7,name=Sprint 7,rapidViewId=nope,state=Future]")

        assertEquals(7L, parsed?.id)
        assertNull(parsed?.boardId)
        assertEquals("future", parsed?.state)
    }

    @Test fun `resolve reports not found when jira answers without a body`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                serveStatus(204).resolve(config(), "Sprint 42")
            }

        assertTrue(failure.message!!.contains("nicht gefunden"))
    }

    @Test fun `resolve reports not found when the issues node is not an array`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                serve("""{"issues":{"oops":true}}""").resolve(config(), "Sprint 42")
            }

        assertTrue(failure.message!!.contains("nicht gefunden"))
    }

    @Test fun `resolve ignores duplicate sprint ids and other projects`() {
        val payload =
            """
            {"issues":[
              {"key":"DEV-1","fields":{"project":{"key":"DEV"},"customfield_10020":[{"id":42,"name":"Sprint 42","state":"ACTIVE","boardId":5}]}},
              {"key":"DEV-2","fields":{"project":{"key":"DEV"},"customfield_10020":[{"id":42,"name":"Sprint 42","state":"ACTIVE","boardId":5}]}},
              {"key":"DEV-3","fields":{"project":{"key":"DEV"},"customfield_10020":[{"id":41,"name":"Sprint 41","state":"ACTIVE"}]}}
            ],"total":3}
            """.trimIndent()

        val sprint = serve(payload).resolve(config(), "  SprInT 42  ")

        assertEquals(42L, sprint.id)
        assertEquals("Sprint 42", sprint.name)
    }

    @Test fun `resolve keeps the server backlog url for on premise tenants`() {
        val payload =
            """
            {"issues":[{"key":"DEV-1","fields":{"project":{"key":"DEV"},"customfield_10020":[{"id":42,"name":"Sprint 42","state":"ACTIVE"}]}}]}
            """.trimIndent()

        val sprint = serve(payload).resolve(config(cloudMode = false), "Sprint 42")

        assertTrue(sprint.url.endsWith("/secure/RapidBoard.jspa"))
        assertEquals(null, sprint.boardId)
    }

    @Test fun `resolve reports not found when jira rejects the search`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                serveStatus(500).resolve(config(), "Sprint 42")
            }

        assertTrue(failure.message!!.contains("nicht gefunden"))
    }
}
