package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
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

class TicketCreationNativeJiraAdapterCreateFieldsTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: TicketCreationNativeJiraAdapter
    private val mapper = ObjectMapper()

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

    private fun config(cloudMode: Boolean) =
        JiraConfig(
            name = "test",
            baseUrl = server.url("").toString().removeSuffix("/"),
            projectKey = "PROJ",
            apiToken = "TOK",
            cloudMode = cloudMode,
            active = true,
            createdAt = LocalDateTime.now(),
        )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private val cloudIssueTypes =
        """{"issueTypes":[{"id":"10100","name":"Story"},{"id":"10103","name":"Bug"}]}"""

    private val cloudBugFields =
        """
        {"startAt":0,"maxResults":50,"total":6,"fields":[
          {"fieldId":"summary","name":"Zusammenfassung","required":true,"schema":{"type":"string","system":"summary"}},
          {"fieldId":"issuetype","name":"Vorgangstyp","required":true,"schema":{"type":"issuetype","system":"issuetype"}},
          {"fieldId":"customfield_10108","name":"Bug Environment","required":true,
           "schema":{"type":"array","items":"option","custom":"com.atlassian.jira.plugin.system.customfieldtypes:multiselect"},
           "allowedValues":[{"id":"1","value":"Live"},{"id":"2","value":"Staging"}]},
          {"fieldId":"customfield_10120","name":"Department","required":true,
           "schema":{"type":"option","custom":"com.atlassian.jira.plugin.system.customfieldtypes:select"},
           "allowedValues":[{"id":"3","value":"Shop Platform"},{"id":"4","value":"Tax"}]},
          {"fieldId":"customfield_10999","name":"Optional Select","required":false,
           "schema":{"type":"option"},"allowedValues":[{"id":"5","value":"X"}]},
          {"fieldId":"customfield_10500","name":"Required Text","required":true,"schema":{"type":"string"}}
        ]}
        """.trimIndent()

    @Test
    fun `requiredCreateFields returns required option fields StackLens does not fill itself`() {
        server.enqueue(json(cloudIssueTypes))
        server.enqueue(json(cloudBugFields))

        val fields = adapter.requiredCreateFields(config(cloudMode = true), "PROJ", "bug")

        assertEquals(
            listOf(
                JiraCreateField("customfield_10108", "Bug Environment", multiple = true, allowedValues = listOf("Live", "Staging")),
                JiraCreateField("customfield_10120", "Department", multiple = false, allowedValues = listOf("Shop Platform", "Tax")),
            ),
            fields,
        )
        assertEquals("/rest/api/3/issue/createmeta/PROJ/issuetypes?maxResults=200", server.takeRequest().path)
        assertEquals("/rest/api/3/issue/createmeta/PROJ/issuetypes/10103?startAt=0&maxResults=200", server.takeRequest().path)
    }

    @Test
    fun `requiredCreateFields follows pagination until all fields are read`() {
        server.enqueue(json(cloudIssueTypes))
        server.enqueue(
            json(
                """{"startAt":0,"maxResults":1,"total":2,"fields":[
                  {"fieldId":"customfield_1","name":"A","required":true,"schema":{"type":"option"},"allowedValues":[{"value":"a"}]}]}""",
            ),
        )
        server.enqueue(
            json(
                """{"startAt":1,"maxResults":1,"total":2,"fields":[
                  {"fieldId":"customfield_2","name":"B","required":true,"schema":{"type":"option"},"allowedValues":[{"value":"b"}]}]}""",
            ),
        )

        val fields = adapter.requiredCreateFields(config(cloudMode = true), "PROJ", "Bug")

        assertEquals(listOf("customfield_1", "customfield_2"), fields.map { it.fieldId })
        server.takeRequest()
        server.takeRequest()
        assertEquals("/rest/api/3/issue/createmeta/PROJ/issuetypes/10103?startAt=1&maxResults=200", server.takeRequest().path)
    }

    @Test
    fun `requiredCreateFields returns empty list when the issue type does not exist in the project`() {
        server.enqueue(json(cloudIssueTypes))

        assertEquals(emptyList<JiraCreateField>(), adapter.requiredCreateFields(config(cloudMode = true), "PROJ", "Epic"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `requiredCreateFields reads the values key of Server DC responses via API v2`() {
        server.enqueue(json("""{"values":[{"id":"7","name":"Bug"}]}"""))
        server.enqueue(
            json(
                """{"isLast":true,"values":[
                  {"fieldId":"customfield_7","name":"Team","required":true,"schema":{"type":"option"},"allowedValues":[{"value":"T1"}]}]}""",
            ),
        )

        val fields = adapter.requiredCreateFields(config(cloudMode = false), "PROJ", "Bug")

        assertEquals(listOf(JiraCreateField("customfield_7", "Team", multiple = false, allowedValues = listOf("T1"))), fields)
        assertTrue(server.takeRequest().path!!.startsWith("/rest/api/2/issue/createmeta/PROJ/issuetypes"))
        assertTrue(server.takeRequest().path!!.startsWith("/rest/api/2/issue/createmeta/PROJ/issuetypes/7"))
    }

    @Test
    fun `createTicket sends single and multi select custom fields as Jira option objects`() {
        server.enqueue(json("""{"key":"PROJ-1","id":"1"}"""))

        adapter.createTicket(
            config = config(cloudMode = true),
            projectKey = "PROJ",
            summary = "S",
            description = "",
            issueType = "Bug",
            labels = emptyList(),
            priority = "Minor",
            customFields = mapOf("customfield_10108" to listOf("Live", "Staging"), "customfield_10120" to "Tax"),
        )

        val fields = mapper.readTree(server.takeRequest().body.readUtf8()).path("fields")
        assertEquals("""[{"value":"Live"},{"value":"Staging"}]""", fields.path("customfield_10108").toString())
        assertEquals("""{"value":"Tax"}""", fields.path("customfield_10120").toString())
    }
}
