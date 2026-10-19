package com.stacklens.adapter.out.knowledge.jira

import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.application.port.out.knowledge.JiraIssueSnapshot
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class JiraHierarchyAdapterLinkAndKeyCoverageTest {
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

    private val plainFields =
        """"summary":"S","issuetype":{"name":"Story"},"status":{"name":"Done"},"priority":{"name":"High"},"labels":["x"]"""

    private fun issue(
        key: String,
        fieldsJson: String,
    ): String = """{"key":"$key","fields":$fieldsJson}"""

    private fun serve(pages: Map<String, String>) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    val body =
                        when {
                            "/search" in path -> """{"issues":[]}"""
                            else ->
                                pages[path.substringAfter("/issue/").substringBefore("?")]
                                    ?: return MockResponse().setResponseCode(404).setBody("{}")
                        }
                    return MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
                }
            }
    }

    private fun walk(root: String): List<JiraIssueSnapshot> =
        adapter()
            .walkHierarchy(
                config = config,
                rootTicketKeyOrUrl = root,
                includeChildren = true,
                includeComments = true,
                maxDepth = 3,
                maxIssues = 10,
                includeLinkedTickets = true,
                includeParent = true,
            ).toList()

    @Test
    fun `inward only links, blank link keys and a self reference are handled`() {
        serve(
            mapOf(
                "PROJ-1" to
                    issue(
                        "PROJ-1",
                        """
                        {$plainFields,
                        "description":{"type":"doc","content":[]},
                        "subtasks":[{"key":"PROJ-2"}],
                        "issuelinks":[
                          {"type":{"inward":"is blocked by","outward":"blocks"},"inwardIssue":{"key":"PROJ-3"}},
                          {"type":{"inward":"is blocked by","outward":"blocks"},"inwardIssue":{"key":"  "}},
                          {"type":{"inward":"is blocked by","outward":"blocks"},"outwardIssue":{"key":"PROJ-1"}},
                          {"type":{"inward":"is blocked by","outward":"blocks"}}
                        ]}
                        """.trimIndent(),
                    ),
                "PROJ-2" to issue("PROJ-2", """{$plainFields,"description":null}"""),
                "PROJ-3" to issue("PROJ-3", """{$plainFields}"""),
            ),
        )

        val snapshots = walk("PROJ-1")

        assertEquals(listOf("PROJ-1", "PROJ-2", "PROJ-3"), snapshots.map { it.key })
        assertTrue(snapshots.first().plainText.contains("Linked: PROJ-3"), snapshots.first().plainText)
        assertTrue(snapshots.first { it.key == "PROJ-2" }.plainText.contains("(no description)"))
    }

    @Test
    fun `comments without a body are dropped and an author without a display name falls back`() {
        serve(
            mapOf(
                "PROJ-1" to
                    issue(
                        "PROJ-1",
                        """
                        {$plainFields,
                        "description":null,
                        "comment":{"comments":[
                          {"author":{"displayName":"Anna"},"body":null},
                          {"author":{"name":"Joe"},"body":{"type":"doc"}},
                          {"author":{},"body":{"type":"doc"}},
                          {"author":{"displayName":"Nope"}}
                        ]},
                        "subtasks":null}
                        """.trimIndent(),
                    ),
            ),
        )

        val plainText = walk("PROJ-1").single().plainText

        assertTrue(plainText.contains("Joe:"), plainText)
        assertTrue(plainText.contains("Unbekannt:"), plainText)
        org.junit.jupiter.api.Assertions
            .assertFalse(plainText.contains("Nope"), plainText)
    }

    @Test
    fun `a comment node that is not an array yields no comments`() {
        serve(mapOf("PROJ-1" to issue("PROJ-1", """{$plainFields,"comment":{"comments":{"not":"an array"}}}""")))

        assertTrue(walk("PROJ-1").single().plainText.contains("(no description)"))
    }

    @Test
    fun `a reference without a ticket key never leaves the adapter`() {
        assertTrue(walk("plain words only").isEmpty())
        assertNull(adapter().extractTicketKey("plain words only"))
        assertThrows<IllegalStateException> { adapter().ensureHostMatches(config, "plain words only") }
    }

    @Test
    fun `a JsonNode description is rendered as text when the adf walk finds nothing`() {
        serve(mapOf("PROJ-1" to issue("PROJ-1", """{$plainFields,"description":"legacy wiki markup"}""")))

        assertTrue(walk("PROJ-1").single().plainText.contains("legacy wiki markup"))
    }
}
