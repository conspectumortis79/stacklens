package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraCreateField
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TicketCreationNativeJiraAdapterCreateFieldsBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: TicketCreationNativeJiraAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = TicketCreationNativeJiraAdapter(JiraTicketHttpClient())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config() =
        JiraConfig(
            name = "test",
            baseUrl = server.url("").toString().removeSuffix("/"),
            projectKey = "PROJ",
            apiToken = "TOK",
            cloudMode = true,
            active = true,
            createdAt = LocalDateTime.now(),
        )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun issueTypePage(id: String = "10103") = """{"issueTypes":[{"id":"$id","name":"Bug"}]}"""

    @Test
    fun `requiredCreateFields treats a response without a body as an empty page`() {
        server.enqueue(MockResponse().setResponseCode(200))

        assertEquals(emptyList<JiraCreateField>(), adapter.requiredCreateFields(config(), "PROJ", "Bug"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `requiredCreateFields ignores issue type entries without a usable name`() {
        server.enqueue(
            json(
                """{"issueTypes":[{"id":"1"},{"id":"2","name":77},{"id":"3","name":"Story"},{"id":"4","name":"bug"}]}""",
            ),
        )
        server.enqueue(json("""{"total":0,"fields":[]}"""))

        assertEquals(emptyList<JiraCreateField>(), adapter.requiredCreateFields(config(), "PROJ", "Bug"))
        server.takeRequest()
        assertEquals("/rest/api/3/issue/createmeta/PROJ/issuetypes/4?startAt=0&maxResults=200", server.takeRequest().path)
    }

    @Test
    fun `requiredCreateFields stops on the first page that carries no entries`() {
        server.enqueue(json(issueTypePage()))
        server.enqueue(json("""{"total":99,"fields":[]}"""))

        assertEquals(emptyList<JiraCreateField>(), adapter.requiredCreateFields(config(), "PROJ", "Bug"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `requiredCreateFields gives up and warns after the page budget is spent`() {
        server.enqueue(json(issueTypePage()))
        repeat(20) {
            server.enqueue(
                json(
                    """{"total":500,"fields":[{"fieldId":"customfield_1","name":"A","required":true,"schema":{"type":"option"}}]}""",
                ),
            )
        }

        val fields = adapter.requiredCreateFields(config(), "PROJ", "Bug")

        assertEquals(List(20) { JiraCreateField("customfield_1", "A", multiple = false, allowedValues = emptyList()) }, fields)
        assertEquals(21, server.requestCount)
    }

    @Test
    fun `requiredCreateFields tolerates a page payload that is not a list`() {
        server.enqueue(json("""{"issueTypes":"not-a-list"}"""))

        assertEquals(emptyList<JiraCreateField>(), adapter.requiredCreateFields(config(), "PROJ", "Bug"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `requiredCreateFields tolerates a page payload without any known key`() {
        server.enqueue(json("""{"isLast":true}"""))

        assertEquals(emptyList<JiraCreateField>(), adapter.requiredCreateFields(config(), "PROJ", "Bug"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `requiredCreateFields keeps only required custom fields with an option schema`() {
        server.enqueue(json(issueTypePage()))
        server.enqueue(
            json(
                """
                {"total":10,"fields":[
                  "not-a-map",
                  {"fieldId":"project","required":true,"name":"Project","schema":{"type":"array","items":"option"}},
                  {"fieldId":"summary","required":true,"schema":{"type":"string"}},
                  {"required":true,"key":"customfield_1","schema":{"type":"option"}},
                  {"required":true,"key":7,"schema":{"type":"option"}},
                  {"fieldId":"customfield_2","required":true},
                  {"fieldId":"customfield_3","required":true,"schema":"nope"},
                  {"fieldId":"customfield_4","required":true,"schema":{"type":"array","items":"string"}},
                  {"fieldId":"customfield_5","required":true,"schema":{"type":"option"},"allowedValues":"nope"},
                  {"fieldId":"customfield_6","required":true,"schema":{"type":"option"},
                   "allowedValues":[3,{"id":9},null,{"value":"v"}]},
                  {"fieldId":"customfield_7","name":"Labels","required":true,"schema":{"type":"array","items":"option"},
                   "allowedValues":[{"value":42},{"value":"a"}]}
                ]}
                """.trimIndent(),
            ),
        )

        val fields = adapter.requiredCreateFields(config(), "PROJ", "Bug")

        assertEquals(
            listOf(
                JiraCreateField("customfield_1", "customfield_1", multiple = false, allowedValues = emptyList()),
                JiraCreateField("customfield_5", "customfield_5", multiple = false, allowedValues = emptyList()),
                JiraCreateField("customfield_6", "customfield_6", multiple = false, allowedValues = listOf("v")),
                JiraCreateField("customfield_7", "Labels", multiple = true, allowedValues = listOf("a")),
            ),
            fields,
        )
        assertTrue(server.takeRequest().path!!.endsWith("maxResults=200"))
    }
}
