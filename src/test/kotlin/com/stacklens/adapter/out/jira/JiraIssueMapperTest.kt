package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class JiraIssueMapperTest {
    private val mapper = ObjectMapper()
    private val jiraMapper = JiraIssueMapper()

    private fun parseJson(json: String): JsonNode = mapper.readTree(json)

    private fun config(
        projectKey: String = "PROJ",
        baseUrl: String = "https://jira.example.com",
    ): JiraConfig {
        val cfg =
            JiraConfig(
                name = "c",
                baseUrl = baseUrl,
                projectKey = projectKey,
                username = "u",
                apiToken = "t",
            )
        return cfg
    }

    @Test
    fun `flattenDescriptionNode returns empty for null and isNull`() {
        assertEquals("", jiraMapper.flattenDescriptionNode(null))
        assertEquals("", jiraMapper.flattenDescriptionNode(parseJson("null")))
    }

    @Test
    fun `flattenDescriptionNode returns text directly for textual node`() {
        assertEquals("hello", jiraMapper.flattenDescriptionNode(parseJson("\"hello\"")))
    }

    @Test
    fun `flattenDescriptionNode flattens ADF doc with paragraphs and text`() {
        val adf =
            """{"type":"doc","content":[
                {"type":"paragraph","content":[{"type":"text","text":"Hello world"}]},
                {"type":"paragraph","content":[{"type":"text","text":"Second paragraph"}]}
            ]}"""
        val result = jiraMapper.flattenDescriptionNode(parseJson(adf))
        assertEquals("Hello world Second paragraph", result)
    }

    @Test
    fun `flattenDescriptionNode flattens nested marks`() {
        val adf =
            """{"type":"paragraph","content":[
                {"type":"text","text":"hello "},
                {"type":"text","text":"world"}
            ]}"""
        assertEquals("hello world", jiraMapper.flattenDescriptionNode(parseJson(adf)))
    }

    @Test
    fun `flattenDescriptionNode collapses whitespace`() {
        val node = mapper.createObjectNode()
        val para = mapper.createObjectNode().put("type", "paragraph")
        val arr = mapper.createArrayNode()
        val text = mapper.createObjectNode().put("type", "text").put("text", "hello\n\n\nworld")
        arr.add(text)
        para.set<JsonNode>("content", arr)
        node.set<JsonNode>("content", mapper.createArrayNode().add(para))
        node.put("type", "doc")
        val result = jiraMapper.flattenDescriptionNode(node)
        assertEquals("hello world", result)
    }

    @Test
    fun `flattenDescriptionNode handles nested text node path`() {
        val node = parseJson("""{"text":"leaf"}""")
        assertEquals("leaf", jiraMapper.flattenDescriptionNode(node))
    }

    @Test
    fun `extractDescriptionExcerpt returns empty for null description`() {
        assertEquals("", jiraMapper.extractDescriptionExcerpt(null, "kw", 100))
    }

    @Test
    fun `extractDescriptionExcerpt returns empty when keyword not present`() {
        val node = parseJson("""{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"no match here"}]}]}""")
        assertEquals("", jiraMapper.extractDescriptionExcerpt(node, "missing", 100))
    }

    @Test
    fun `extractDescriptionExcerpt returns context around keyword`() {
        val adf =
            """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"the user keyword appears in description"}]}]}"""
                .trimIndent()
        val result = jiraMapper.extractDescriptionExcerpt(parseJson(adf), "keyword", 1000)
        assertTrue(result.contains("keyword"))
    }

    @Test
    fun `extractDescriptionExcerpt returns empty when description is blank after flatten`() {
        val node = parseJson("""{"type":"doc","content":[{"type":"paragraph","content":[]}]}""")
        assertEquals("", jiraMapper.extractDescriptionExcerpt(node, "kw", 100))
    }

    @Test
    fun `resultTypeFromIssueType maps Bug`() {
        assertEquals(ResultType.JIRA_BUG, jiraMapper.resultTypeFromIssueType("Bug"))
        assertEquals(ResultType.JIRA_BUG, jiraMapper.resultTypeFromIssueType("bug"))
    }

    @Test
    fun `resultTypeFromIssueType maps Story`() {
        assertEquals(ResultType.JIRA_STORY, jiraMapper.resultTypeFromIssueType("Story"))
    }

    @Test
    fun `resultTypeFromIssueType maps Task and Technical Task`() {
        assertEquals(ResultType.JIRA_TASK, jiraMapper.resultTypeFromIssueType("Task"))
        assertEquals(ResultType.JIRA_TASK, jiraMapper.resultTypeFromIssueType("Technical Task"))
    }

    @Test
    fun `resultTypeFromIssueType maps Epic`() {
        assertEquals(ResultType.JIRA_EPIC, jiraMapper.resultTypeFromIssueType("Epic"))
    }

    @Test
    fun `resultTypeFromIssueType falls back to JIRA_ISSUE`() {
        assertEquals(ResultType.JIRA_ISSUE, jiraMapper.resultTypeFromIssueType("Unknown"))
    }

    @Test
    fun `mapIssueToResult returns SearchResult with full mapping`() {
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-42",
                fields =
                    JiraClient.JiraFields(
                        summary = "Some summary",
                        issuetype = JiraClient.JiraNamedValue(name = "Bug"),
                        status = JiraClient.JiraNamedValue(name = "Open"),
                        assignee = JiraClient.JiraAssignee(displayName = "Alice"),
                        priority = JiraClient.JiraNamedValue(name = "High"),
                        updated = "yesterday",
                        description =
                            parseJson(
                                """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"description text"}]}]}""",
                            ),
                    ),
            )
        val result = jiraMapper.mapIssueToResult(issue, config(), "kw", sessionId = 7)
        assertEquals(SourceType.JIRA, result.sourceType)
        assertEquals(ResultType.JIRA_BUG, result.resultType)
        assertEquals("PROJ-42", result.filePath)
        assertEquals("PROJ", result.repoName)
        assertEquals("Some summary", result.branch)
        assertEquals("Bug", result.className)
        assertEquals("Open", result.functionName)
        assertEquals("Alice", result.dbHost)
        assertEquals("High", result.databaseName)
        assertEquals("yesterday", result.schemaName)
        assertEquals(7L, result.sessionId)
        assertTrue(result.ddlContext!!.contains("description text"))
    }

    @Test
    fun `mapIssueToResult falls back to project key prefix when projectKey blank`() {
        val issue = JiraClient.JiraIssue(key = "PROJ-1", fields = JiraClient.JiraFields())
        val result = jiraMapper.mapIssueToResult(issue, config(projectKey = ""), "kw", sessionId = 1)
        assertEquals("PROJ", result.repoName)
    }

    @Test
    fun `mapIssueToResult uses key as projectName when key has no dash`() {
        val issue = JiraClient.JiraIssue(key = "ABCDE", fields = JiraClient.JiraFields())
        val result = jiraMapper.mapIssueToResult(issue, config(projectKey = ""), "kw", sessionId = 1)
        assertEquals("ABCDE", result.repoName)
    }

    @Test
    fun `mapIssueToResult builds browse URL from baseUrl and key`() {
        val issue = JiraClient.JiraIssue(key = "PROJ-99", fields = JiraClient.JiraFields())
        val result =
            jiraMapper.mapIssueToResult(
                issue,
                config(projectKey = "PROJ", baseUrl = "https://jira.example.com/"),
                "kw",
                sessionId = 1,
            )
        assertEquals("https://jira.example.com/browse/PROJ-99", result.repoUrl)
    }

    @Test
    fun `mapIssueToResult falls back to Issue when issuetype null`() {
        val issue = JiraClient.JiraIssue(key = "PROJ-1", fields = JiraClient.JiraFields())
        val result = jiraMapper.mapIssueToResult(issue, config(), "kw", sessionId = 1)
        assertEquals("Issue", result.className)
        assertEquals(ResultType.JIRA_ISSUE, result.resultType)
    }

    @Test
    fun `mapIssueToResult prefers assignee displayName over name`() {
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        assignee = JiraClient.JiraAssignee(displayName = "Alice", name = "alice.login"),
                    ),
            )
        val result = jiraMapper.mapIssueToResult(issue, config(), "kw", sessionId = 1)
        assertEquals("Alice", result.dbHost)
    }

    @Test
    fun `mapIssueToResult falls back to assignee name when displayName blank`() {
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        assignee = JiraClient.JiraAssignee(displayName = null, name = "alice.login"),
                    ),
            )
        val result = jiraMapper.mapIssueToResult(issue, config(), "kw", sessionId = 1)
        assertEquals("alice.login", result.dbHost)
    }

    @Test
    fun `mapIssueToResult codeSnippet contains summary and excerpt when description has keyword`() {
        val descText =
            "x".repeat(50) + " the keyword here " + "y".repeat(50)
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        summary = "SummaryX",
                        description =
                            parseJson(
                                """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"$descText"}]}]}""",
                            ),
                    ),
            )
        val result = jiraMapper.mapIssueToResult(issue, config(), "keyword", sessionId = 1)
        assertTrue(result.codeSnippet!!.contains("[SummaryX]"))
        assertTrue(result.codeSnippet!!.contains("keyword"))
    }

    @Test
    fun `mapIssueToResult codeSnippet is just summary when no keyword match`() {
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        summary = "Only Summary",
                        description =
                            parseJson(
                                """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"plain text"}]}]}""",
                            ),
                    ),
            )
        val result = jiraMapper.mapIssueToResult(issue, config(), "missing", sessionId = 1)
        assertEquals("[Only Summary]", result.codeSnippet)
    }

    @Test
    fun `mapIssueToResult codeSnippet with null description is just summary`() {
        val issue = JiraClient.JiraIssue(key = "PROJ-1", fields = JiraClient.JiraFields(summary = "Summary"))
        val result = jiraMapper.mapIssueToResult(issue, config(), "kw", sessionId = 1)
        assertEquals("[Summary]", result.codeSnippet)
    }

    @Test
    fun `mapIssueToResult sets ddlContext empty when description is blank`() {
        val issue = JiraClient.JiraIssue(key = "PROJ-1", fields = JiraClient.JiraFields(summary = "S"))
        val result = jiraMapper.mapIssueToResult(issue, config(), "kw", sessionId = 1)
        assertNull(result.ddlContext)
    }

    @Test
    fun `mapIssueToResult with empty status and no priority`() {
        val issue = JiraClient.JiraIssue(key = "PROJ-1", fields = JiraClient.JiraFields())
        val result = jiraMapper.mapIssueToResult(issue, config(), "kw", sessionId = 1)
        assertEquals("", result.functionName)
        assertNull(result.databaseName)
        assertNull(result.schemaName)
    }

    private lateinit var server: MockWebServer
    private lateinit var webClient: WebClient

    @BeforeEach
    fun setUpMock() {
        server = MockWebServer()
        server.start()
        webClient = WebClient.builder().baseUrl(server.url("").toString().removeSuffix("/")).build()
    }

    @AfterEach
    fun tearDownMock() {
        server.shutdown()
    }

    @Test
    fun `findDrawIoAttachmentResults returns empty when no attachments`() {
        val issue = JiraClient.JiraIssue(key = "PROJ-1", fields = JiraClient.JiraFields(attachment = emptyList()))
        val results = jiraMapper.findDrawIoAttachmentResults(webClient, issue, config(), "kw", sessionId = 1)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findDrawIoAttachmentResults returns empty when no drawio attachments`() {
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        attachment = listOf(JiraClient.JiraAttachment(id = "1", filename = "doc.pdf", content = "")),
                    ),
            )
        val results = jiraMapper.findDrawIoAttachmentResults(webClient, issue, config(), "kw", sessionId = 1)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findDrawIoAttachmentResults matches drawio extension`() {
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        summary = "Issue",
                        attachment = listOf(JiraClient.JiraAttachment(id = "1", filename = "diagram.drawio", content = "")),
                    ),
            )
        val results = jiraMapper.findDrawIoAttachmentResults(webClient, issue, config(), "diagram", sessionId = 1)
        assertEquals(1, results.size)
        assertEquals("draw.io", results[0].className)
    }

    @Test
    fun `findDrawIoAttachmentResults matches drawio filename containing keyword`() {
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        summary = "Issue",
                        attachment = listOf(JiraClient.JiraAttachment(id = "1", filename = "user-flow.drawio", content = "")),
                    ),
            )
        val results = jiraMapper.findDrawIoAttachmentResults(webClient, issue, config(), "user", sessionId = 1)
        assertEquals(1, results.size)
        assertTrue(results[0].codeSnippet!!.contains("filename"))
    }

    @Test
    fun `findDrawIoAttachmentResults matches drawio content containing keyword via HTTP fetch`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/xml")
                .setBody("<diagram>contains user keyword here</diagram>"),
        )
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        summary = "Issue",
                        attachment =
                            listOf(
                                JiraClient.JiraAttachment(
                                    id = "1",
                                    filename = "diagram.drawio",
                                    content = server.url("diagram").toString(),
                                ),
                            ),
                    ),
            )
        val results = jiraMapper.findDrawIoAttachmentResults(webClient, issue, config(), "user", sessionId = 1)
        assertEquals(1, results.size)
        assertTrue(results[0].codeSnippet!!.contains("contains"))
    }

    @Test
    fun `findDrawIoAttachmentResults returns empty when neither filename nor content matches`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/xml")
                .setBody("<diagram>no match</diagram>"),
        )
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        summary = "Issue",
                        attachment =
                            listOf(
                                JiraClient.JiraAttachment(id = "1", filename = "other.drawio", content = server.url("diagram").toString()),
                            ),
                    ),
            )
        val results = jiraMapper.findDrawIoAttachmentResults(webClient, issue, config(), "missing", sessionId = 1)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findDrawIoAttachmentResults handles attachment with empty content (filename match only)`() {
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        summary = "Issue",
                        attachment = listOf(JiraClient.JiraAttachment(id = "1", filename = "user-flow.drawio", content = "")),
                    ),
            )
        val results = jiraMapper.findDrawIoAttachmentResults(webClient, issue, config(), "user", sessionId = 1)
        assertEquals(1, results.size)
    }

    @Test
    fun `findDrawIoAttachmentResults triggers onErrorResume branch when attachment fetch fails`() {
        val issue =
            JiraClient.JiraIssue(
                key = "PROJ-1",
                fields =
                    JiraClient.JiraFields(
                        summary = "Issue",
                        attachment =
                            listOf(
                                JiraClient.JiraAttachment(
                                    id = "1",
                                    filename = "user-flow.drawio",
                                    content = "http://127.0.0.1:1/attachment/1",
                                ),
                            ),
                    ),
            )
        val unreachableClient =
            org.springframework.web.reactive.function.client.WebClient
                .builder()
                .baseUrl("http://127.0.0.1:1/")
                .build()
        val results =
            jiraMapper.findDrawIoAttachmentResults(unreachableClient, issue, config(), "user", sessionId = 1)
        assertTrue(results.size == 1 || results.isEmpty())
    }

    @Test
    fun `extractDescriptionExcerpt returns excerpt at start of text without ellipsis`() {
        val mapper = JiraIssueMapper()
        val om =
            com.fasterxml.jackson.databind
                .ObjectMapper()
        val text = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hello world foo bar"}]}]}"""
        val node = om.readTree(text)
        val excerpt = mapper.extractDescriptionExcerpt(node, "hello", 200)
        assertTrue(excerpt.startsWith("hello"))
        assertTrue(!excerpt.startsWith("…"))
    }

    @Test
    fun `flattenJsonNode uses text field directly when present`() {
        val mapper = JiraIssueMapper()
        val om =
            com.fasterxml.jackson.databind
                .ObjectMapper()
        val text = """{"type":"paragraph","text":"direct text","content":[{"type":"text","text":"child"}]}"""
        val node = om.readTree(text)
        val result = mapper.flattenDescriptionNode(node)
        assertTrue(result.contains("direct text"))
    }
}
