package com.stacklens.adapter.out.knowledge.jira

import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class JiraHierarchyAdapterWalkTest {
    private lateinit var server: MockWebServer

    private val resolver = mock<JiraApiVersionResolver>()

    private lateinit var config: JiraConfig

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        whenever(resolver.apiVersion(any())).thenReturn("2")
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
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter() =
        JiraHierarchyAdapter(
            ReactiveHttpClientFactory(connectTimeoutMs = 5000, readTimeoutMs = 10000),
            resolver,
        )

    private fun issueJson(
        key: String,
        summary: String = "Ticket $key",
        subtasks: List<String> = emptyList(),
        links: List<String> = emptyList(),
        parent: String? = null,
        description: String? = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Text"}]}]}""",
    ): String {
        val sub = subtasks.joinToString(",") { """{"key":"$it","fields":{"summary":"sub"}}""" }
        val linked =
            links.joinToString(",") {
                """{"type":{"name":"Blocks"},"outwardIssue":{"key":"$it","fields":{"summary":"linked"}}}"""
            }
        val parentField = parent?.let { """"parent":{"key":"${jsonEscape(it)}"},""" } ?: ""
        val descriptionField = description ?: "null"
        return listOf(
            """{"key":"$key","fields":{""",
            """  "summary":"${jsonEscape(summary)}",""",
            """  "issuetype":{"name":"Story"},""",
            """  "status":{"name":"In Progress"},""",
            """  "priority":{"name":"High"},""",
            """  "labels":["a","b"],""",
            parentField,
            """  "description":$descriptionField,""",
            """  "comment":{"comments":[{"author":{"displayName":"Anna"},"body":{"type":"doc","content":[{"type":"text","text":"Kommentar"}]}}]},""",
            """  "subtasks":[$sub],""",
            """  "issuelinks":[$linked]""",
            "}}",
        ).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private fun jsonEscape(value: String): String = value.replace("\"", "\\\"")

    private fun dispatcher(pages: Map<String, String>) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val key =
                        request.path
                            .orEmpty()
                            .substringAfter("/issue/")
                            .substringBefore("?")
                    val body = pages[key] ?: return MockResponse().setResponseCode(404).setBody("no such issue")
                    return MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
                }
            }
    }

    private fun walk(
        root: String = "PROJ-1",
        includeChildren: Boolean = true,
        includeComments: Boolean = true,
        maxDepth: Int = 3,
        maxIssues: Int = 20,
        includeLinkedTickets: Boolean = true,
        includeParent: Boolean = true,
    ) = adapter()
        .walkHierarchy(
            config,
            root,
            includeChildren,
            includeComments,
            maxDepth,
            maxIssues,
            includeLinkedTickets,
            includeParent,
        ).toList()

    @Test
    fun `the walk follows subtasks issue links and the parent ticket`() {
        dispatcher(
            mapOf(
                "PROJ-1" to issueJson("PROJ-1", subtasks = listOf("PROJ-2", "  ", "PROJ-1"), links = listOf("PROJ-3", "PROJ-1"), parent = "PROJ-4"),
                "PROJ-2" to issueJson("PROJ-2"),
                "PROJ-3" to issueJson("PROJ-3", parent = "PROJ-1"),
                "PROJ-4" to issueJson("PROJ-4"),
            ),
        )

        val keys = walk().map { it.key }

        assertEquals(listOf("PROJ-1", "PROJ-2", "PROJ-3", "PROJ-4"), keys)
    }

    @Test
    fun `a parent reference to the same ticket is not enqueued twice`() {
        dispatcher(mapOf("PROJ-1" to issueJson("PROJ-1", parent = "PROJ-1")))

        assertEquals(listOf("PROJ-1"), walk().map { it.key })
    }

    @Test
    fun `a parent without a key is ignored`() {
        dispatcher(mapOf("PROJ-1" to issueJson("PROJ-1", parent = "")))

        assertEquals(listOf("PROJ-1"), walk().map { it.key })
    }

    @Test
    fun `neighbour flags switch the traversal off independently`() {
        dispatcher(
            mapOf(
                "PROJ-1" to issueJson("PROJ-1", subtasks = listOf("PROJ-2"), links = listOf("PROJ-3"), parent = "PROJ-4"),
                "PROJ-2" to issueJson("PROJ-2"),
                "PROJ-3" to issueJson("PROJ-3"),
                "PROJ-4" to issueJson("PROJ-4"),
            ),
        )

        assertEquals(listOf("PROJ-1"), walk(includeChildren = false, includeLinkedTickets = false, includeParent = false).map { it.key })
        assertEquals(listOf("PROJ-1", "PROJ-2"), walk(includeLinkedTickets = false, includeParent = false).map { it.key })
        assertEquals(listOf("PROJ-1", "PROJ-3"), walk(includeChildren = false, includeParent = false).map { it.key })
        assertEquals(listOf("PROJ-1", "PROJ-4"), walk(includeChildren = false, includeLinkedTickets = false).map { it.key })
    }

    @Test
    fun `the depth cap stops the fan-out but still walks the root`() {
        dispatcher(
            mapOf(
                "PROJ-1" to issueJson("PROJ-1", subtasks = listOf("PROJ-2")),
                "PROJ-2" to issueJson("PROJ-2", subtasks = listOf("PROJ-3")),
                "PROJ-3" to issueJson("PROJ-3"),
            ),
        )

        assertEquals(listOf("PROJ-1"), walk(maxDepth = 0).map { it.key })
        assertEquals(listOf("PROJ-1", "PROJ-2"), walk(maxDepth = 1).map { it.key })
    }

    @Test
    fun `the issue cap stops the walk early`() {
        dispatcher(
            mapOf(
                "PROJ-1" to issueJson("PROJ-1", subtasks = listOf("PROJ-2", "PROJ-3")),
                "PROJ-2" to issueJson("PROJ-2"),
                "PROJ-3" to issueJson("PROJ-3"),
            ),
        )

        assertEquals(listOf("PROJ-1"), walk(maxIssues = 1).map { it.key })
        assertEquals(0, walk(maxIssues = 0).size)
    }

    @Test
    fun `a ticket that jira does not return is skipped`() {
        dispatcher(emptyMap())

        assertEquals(emptyList<String>(), walk().map { it.key })
    }

    @Test
    fun `a broken json payload is skipped instead of failing the walk`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"key":"PROJ-1","fields": this is broken }""")
            }

        assertEquals(emptyList<String>(), walk().map { it.key })
    }

    @Test
    fun `the snapshot renders metadata description and comments`() {
        dispatcher(mapOf("PROJ-1" to issueJson("PROJ-1")))

        val snapshot = walk(includeComments = true).single()

        assertTrue(snapshot.title.startsWith("PROJ-1"), snapshot.title)
        assertTrue(snapshot.sourceUri.endsWith("/browse/PROJ-1"), snapshot.sourceUri)
        assertTrue(snapshot.plainText.contains("Type: Story"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("Status: In Progress"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("Priority: High"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("Labels: a, b"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("## Description"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("Text"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("## Comments"), snapshot.plainText)
        assertEquals(0, snapshot.depth)
    }

    @Test
    fun `comments can be suppressed and a missing description prints a placeholder`() {
        dispatcher(mapOf("PROJ-1" to issueJson("PROJ-1", description = null)))

        val snapshot = walk(includeComments = false).single()

        assertTrue(!snapshot.plainText.contains("## Comments"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("(no description)"), snapshot.plainText)
        assertTrue(!snapshot.plainText.contains("Linked:"), snapshot.plainText)
    }

    @Test
    fun `a rich adf description is flattened across every supported block type`() {
        val adf =
            """
            {"type":"doc","content":[
              {"type":"heading","content":[{"type":"text","text":"Überschrift"}]},
              {"type":"paragraph","content":[{"type":"text","text":"absatz "},{"type":"text","text":"zwei"}]},
              {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"text","text":"punkt"}]}]},
              {"type":"blockquote","content":[{"type":"text","text":"zitat"}]},
              {"type":"codeBlock","content":[{"type":"text","text":"code"}]}
            ]}
            """.trimIndent().replace("\n", "")
        dispatcher(mapOf("PROJ-1" to issueJson("PROJ-1", description = adf)))

        val snapshot = walk().single()

        assertTrue(snapshot.plainText.contains("Überschrift"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("absatz zwei"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("punkt"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("zitat"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("code"), snapshot.plainText)
    }

    @Test
    fun `acceptance criteria are extracted into their own section`() {
        val adf = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"AK:\n- [ ] Lint laeuft durch"}]}]}"""
        dispatcher(mapOf("PROJ-1" to issueJson("PROJ-1", description = adf)))

        val snapshot = walk().single()

        assertTrue(snapshot.plainText.contains("## Acceptance Criteria"), snapshot.plainText)
        assertTrue(snapshot.plainText.contains("Lint laeuft durch"), snapshot.plainText)
    }
}
