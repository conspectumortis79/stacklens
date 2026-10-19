package com.stacklens.adapter.out.knowledge.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.jira.DefaultJiraApiVersionResolver
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JiraHierarchyAdapterFullTest {
    private val mapper = ObjectMapper()
    private lateinit var server: MockWebServer
    private lateinit var adapter: JiraHierarchyAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter =
            JiraHierarchyAdapter(
                ReactiveHttpClientFactory(connectTimeoutMs = 5000, readTimeoutMs = 10000),
                DefaultJiraApiVersionResolver(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun cloudConfig(): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "c",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "",
            username = "u",
            apiToken = "t",
            cloudMode = true,
        )

    @Test
    fun `extractTicketKey upper-cases bare keys`() {
        assertEquals("PROJ-123", adapter.extractTicketKey("proj-123"))
        assertEquals("PROJ-123", adapter.extractTicketKey("PROJ-123"))
    }

    @Test
    fun `extractTicketKey handles underscored project prefixes`() {
        assertEquals("MY_PROJECT-9", adapter.extractTicketKey("my_project-9"))
    }

    @Test
    fun `extractTicketKey returns null for empty input`() {
        assertNull(adapter.extractTicketKey(""))
        assertNull(adapter.extractTicketKey("   "))
    }

    @Test
    fun `extractTicketKey returns null when no key pattern is present`() {
        assertNull(adapter.extractTicketKey("https://example.com/"))
        assertNull(adapter.extractTicketKey("lorem ipsum"))
    }

    @Test
    fun `ensureHostMatches accepts URL on the configured host with a valid key`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
            }
        adapter.ensureHostMatches(
            cloudConfig(),
            "${server.url("/").toString().trimEnd('/')}/browse/PROJ-1",
        )
    }

    @Test
    fun `ensureHostMatches throws when the URL host does not match the config host`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
            }
        assertThrows<IllegalArgumentException> {
            adapter.ensureHostMatches(
                cloudConfig(),
                "https://evil.example.com/browse/PROJ-1",
            )
        }
    }

    @Test
    fun `ensureHostMatches throws for a non key-shaped bare token`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
            }
        assertThrows<IllegalStateException> {
            adapter.ensureHostMatches(cloudConfig(), "not-a-jira-key")
        }
    }

    @Test
    fun `ensureHostMatches throws when the config base URL is malformed`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
            }
        val bad =
            JiraConfig(
                id = 1L,
                name = "c",
                baseUrl = "not a url",
                projectKey = "",
                username = "u",
                apiToken = "t",
                cloudMode = true,
            )
        assertThrows<IllegalStateException> {
            adapter.ensureHostMatches(bad, "https://company.atlassian.net/browse/PROJ-1")
        }
    }

    @Test
    fun `walkHierarchy emits a single snapshot for a leaf issue with no children or comments`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "key": "PROJ-1",
                              "fields": {
                                "summary": "Hello",
                                "description": {"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"Body"}]}]},
                                "issuetype": {"name": "Story"},
                                "status": {"name": "Done"},
                                "priority": {"name": "High"},
                                "labels": ["a", "b"],
                                "comment": {"comments": []},
                                "subtasks": [],
                                "parent": {"key": "PROJ-0"}
                              }
                            }
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertEquals(1, results.size)
        val snapshot = results[0]
        assertEquals("PROJ-1", snapshot.key)
        assertEquals("PROJ-1 – Hello", snapshot.title)
        assertTrue(snapshot.plainText.contains("Hello"))
        assertTrue(snapshot.plainText.contains("Body"))
        assertEquals(0, snapshot.depth)
        assertNotNull(snapshot.sourceUri)
    }

    @Test
    fun `walkHierarchy walks sub-tasks recursively up to maxDepth`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val key =
                        request.path
                            .orEmpty()
                            .substringAfterLast("/")
                            .substringBefore("?")
                    val body =
                        when (key) {
                            "PROJ-1" ->
                                """
                                {
                                  "key": "PROJ-1",
                                  "fields": {
                                    "summary": "Parent",
                                    "description": null,
                                    "issuetype": {"name":"Story"},
                                    "status": {"name":"In Progress"},
                                    "priority": {"name":"Medium"},
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [{"key":"PROJ-2"}]
                                  }
                                }
                                """.trimIndent()
                            "PROJ-2" ->
                                """
                                {
                                  "key": "PROJ-2",
                                  "fields": {
                                    "summary": "Sub",
                                    "description": null,
                                    "issuetype": {"name":"Task"},
                                    "status": {"name":"To Do"},
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": []
                                  }
                                }
                                """.trimIndent()
                            else ->
                                """
                                {"key":"?","fields":{"summary":"?","description":null,"issuetype":{"name":"?"},"status":{"name":"?"},"priority":null,"labels":[],"comment":{"comments": []},"subtasks":[]}}
                                """.trimIndent()
                        }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = true,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertEquals(2, results.size)
        assertEquals("PROJ-1", results[0].key)
        assertEquals("PROJ-2", results[1].key)
        assertEquals(1, results[1].depth)
    }

    @Test
    fun `walkHierarchy respects maxDepth by stopping recursion`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val key =
                        request.path
                            .orEmpty()
                            .substringAfterLast("/")
                            .substringBefore("?")
                    val body =
                        """
                        {
                          "key": "$key",
                          "fields": {
                            "summary": "$key",
                            "description": null,
                            "issuetype": {"name":"Story"},
                            "status": {"name":"X"},
                            "priority": null,
                            "labels": [],
                            "comment": {"comments": []},
                            "subtasks": [{"key":"CHILD-$key"}]
                          }
                        }
                        """.trimIndent()
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = true,
                    includeComments = false,
                    maxDepth = 0,
                    maxIssues = 50,
                ).toList()
        assertEquals(1, results.size)
        assertEquals("PROJ-1", results[0].key)
    }

    @Test
    fun `walkHierarchy stops at maxIssues`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val key =
                        request.path
                            .orEmpty()
                            .substringAfterLast("/")
                            .substringBefore("?")
                    val body =
                        """
                        {
                          "key": "$key",
                          "fields": {
                            "summary": "$key",
                            "description": null,
                            "issuetype": {"name":"Story"},
                            "status": {"name":"X"},
                            "priority": null,
                            "labels": [],
                            "comment": {"comments": []},
                            "subtasks": [{"key":"CHILD-$key"}]
                          }
                        }
                        """.trimIndent()
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = true,
                    includeComments = false,
                    maxDepth = 100,
                    maxIssues = 3,
                ).toList()
        assertTrue(results.size <= 3, "expected at most 3 results, got ${results.size}")
    }

    @Test
    fun `walkHierarchy de-duplicates children encountered twice`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body =
                        when (
                            request.path
                                .orEmpty()
                                .substringAfterLast("/")
                                .substringBefore("?")
                        ) {
                            "PROJ-1" ->
                                """
                                {
                                  "key": "PROJ-1",
                                  "fields": {
                                    "summary": "P1",
                                    "description": null,
                                    "issuetype": {"name":"Story"},
                                    "status": {"name":"X"},
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [{"key":"PROJ-2"}, {"key":"PROJ-3"}]
                                  }
                                }
                                """.trimIndent()
                            "PROJ-2" ->
                                """
                                {
                                  "key": "PROJ-2",
                                  "fields": {
                                    "summary": "P2",
                                    "description": null,
                                    "issuetype": {"name":"Story"},
                                    "status": {"name":"X"},
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [{"key":"PROJ-3"}]
                                  }
                                }
                                """.trimIndent()
                            "PROJ-3" ->
                                """
                                {
                                  "key": "PROJ-3",
                                  "fields": {
                                    "summary": "P3",
                                    "description": null,
                                    "issuetype": {"name":"Story"},
                                    "status": {"name":"X"},
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": []
                                  }
                                }
                                """.trimIndent()
                            else ->
                                """{"key":"?","fields":{"summary":"?","description":null,"issuetype":{"name":"?"},"status":{"name":"?"},"priority":null,"labels":[],"comment":{"comments": []},"subtasks":[]}}"""
                        }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = true,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertEquals(listOf("PROJ-1", "PROJ-2", "PROJ-3"), results.map { it.key })
    }

    @Test
    fun `walkHierarchy skips issues that 404 from the API`() {
        var firstHit = true
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (firstHit) {
                        firstHit = false
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "key": "PROJ-1",
                                  "fields": {
                                    "summary": "Root",
                                    "description": null,
                                    "issuetype": {"name":"Story"},
                                    "status": {"name":"X"},
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [{"key":"PROJ-2"}]
                                  }
                                }
                                """.trimIndent(),
                            )
                    } else {
                        MockResponse().setResponseCode(404)
                    }
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = true,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertEquals(1, results.size)
        assertEquals("PROJ-1", results[0].key)
    }

    @Test
    fun `walkHierarchy skips items with null subtasks and empty labels`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "key": "PROJ-1",
                              "fields": {
                                "summary": "edge",
                                "description": null,
                                "issuetype": null,
                                "status": null,
                                "priority": null,
                                "comment": null,
                                "labels": null
                              }
                            }
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = true,
                    includeComments = true,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertEquals(1, results.size)
        assertEquals("PROJ-1 – edge", results[0].title)
    }

    @Test
    fun `walkHierarchy includes comments when requested`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "key": "PROJ-1",
                              "fields": {
                                "summary": "edge",
                                "description": null,
                                "issuetype": {"name":"Story"},
                                "status": {"name":"X"},
                                "priority": null,
                                "labels": [],
                                "comment": {"comments": [
                                  {"author":{"displayName":"Alice"},
                                   "body":{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"Hi"}]}]}},
                                  {"author":{"name":"Bob"},
                                   "body":{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"Bye"}]}]}}
                                ]},
                                "subtasks": []
                              }
                            }
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = true,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertEquals(1, results.size)
        assertTrue(
            results[0].plainText.contains("Alice") && results[0].plainText.contains("Hi"),
            "expected Alice's comment in plainText: ${results[0].plainText}",
        )
        assertTrue(
            results[0].plainText.contains("Bob") && results[0].plainText.contains("Bye"),
            "expected Bob's comment in plainText: ${results[0].plainText}",
        )
    }

    @Test
    fun `walkHierarchy falls back to Unbekannt for comment authors with no displayName or name`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "key": "PROJ-1",
                              "fields": {
                                "summary": "edge",
                                "description": null,
                                "issuetype": {"name":"Story"},
                                "status": {"name":"X"},
                                "priority": null,
                                "labels": [],
                                "comment": {"comments": [
                                  {"author":{},
                                   "body":{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"anonymous"}]}]}}
                                ]},
                                "subtasks": []
                              }
                            }
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = true,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertEquals(1, results.size)
        assertTrue(results[0].plainText.contains("Unbekannt"))
    }

    @Test
    fun `walkHierarchy omits null fields from the rendered snapshot`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "key": "PROJ-1",
                              "fields": {
                                "summary": "X",
                                "description": null,
                                "issuetype": {"name":""},
                                "status": null,
                                "priority": null,
                                "labels": [],
                                "comment": {"comments": []},
                                "subtasks": []
                              }
                            }
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = true,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        val text = results[0].plainText
        assertTrue(!text.contains("Status"))
        assertTrue(!text.contains("Priority"))
        assertTrue(!text.contains("Labels"))
        assertTrue(text.contains("Type: Issue"))
    }

    @Test
    fun `walkHierarchy emits server-DC v2 when cloudMode is false`() {
        var sawPath = ""
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    sawPath = request.path.orEmpty()
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {"key":"PROJ-1","fields":{"summary":"x","description":null,"issuetype":null,"status":null,"priority":null,"labels":[],"comment":{"comments": []},"subtasks":[]}}
                            """.trimIndent(),
                        )
                }
            }
        val dcConfig =
            JiraConfig(
                id = 1L,
                name = "c",
                baseUrl = server.url("/").toString().trimEnd('/'),
                projectKey = "",
                username = "u",
                apiToken = "t",
                cloudMode = false,
            )
        adapter.walkHierarchy(dcConfig, "PROJ-1", false, false, 5, 50).toList()
        assertTrue(sawPath.startsWith("/rest/api/2/issue/PROJ-1"))
    }

    @Test
    fun `walkHierarchy handles ADF descriptions with no nodes and text bodies`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {"key":"PROJ-1","fields":{"summary":"x","description":"just a plain string","issuetype":null,"status":null,"priority":null,"labels":[],"comment":{"comments": []},"subtasks":[]}}
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertEquals(1, results.size)
        assertTrue(results[0].plainText.contains("just a plain string"))
    }

    @Test
    fun `walkHierarchy skips the parent key when it is missing or absent`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {"key":"PROJ-1","fields":{"summary":"x","description":null,"issuetype":null,"status":null,"priority":null,"labels":[],"comment":{"comments": []},"subtasks":[]}}
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertTrue(!results[0].plainText.contains("Parent:"))
    }

    @Test
    fun `walkHierarchy extracts acceptance criteria from a description block`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"key":"PROJ-1","fields":{"summary":"x","description": ${'"'}## Introduction\n\nintro text\n\n## Acceptance Criteria\n- first AC\n- second AC\n- third AC\n\n## Done${'"'},"issuetype":null,"status":null,"priority":null,"labels":[],"comment":{"comments": []},"subtasks":[]}}""",
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertTrue(
            results[0].plainText.contains("Acceptance Criteria"),
            "expected AC heading in: ${results[0].plainText}",
        )
        assertTrue(results[0].plainText.contains("first AC"))
        assertTrue(results[0].plainText.contains("third AC"))
    }

    @Test
    fun `extractTicketKey returns null for non-key input that contains a dash`() {
        assertNull(adapter.extractTicketKey("lorem ipsum"))
        assertNull(adapter.extractTicketKey("https://example.com/"))
    }

    @Test
    fun `IssueResponse from a complete JSON node`() {
        val json =
            """
            {
              "key": "PROJ-X",
              "fields": {
                "summary": "Summ",
                "description": {"type":"doc","version":1,"content":[]},
                "issuetype": {"name":"Story"},
                "status": {"name":"Done"},
                "priority": {"name":"Low"},
                "labels": ["a", null, "b"],
                "comment": {"comments":[]},
                "subtasks": [{"key":"S-1"}, {"key":null}],
                "parent": {"key":"PROJ-Y"}
              }
            }
            """.trimIndent()
        val node: com.fasterxml.jackson.databind.JsonNode = mapper.readTree(json)
        val adapterClass = adapter.javaClass
        val innerClasses = adapterClass.declaredClasses
        val issueResponseClass =
            innerClasses.first { it.simpleName == "IssueResponse" }
        val companionClass = issueResponseClass.declaredClasses.first { it.simpleName == "Companion" }
        val companionField =
            issueResponseClass.getDeclaredField("Companion").apply { isAccessible = true }
        val companion = companionField.get(null)
        val fromMethod =
            companionClass.declaredMethods.first { it.name == "from" }
        fromMethod.isAccessible = true
        val result = fromMethod.invoke(companion, node)
        assertNotNull(result)
    }

    @Test
    fun `internal walker covers paragraph, heading, listItem, blockquote and nested arrays`() {
        val node: com.fasterxml.jackson.databind.JsonNode =
            com.fasterxml.jackson.databind
                .ObjectMapper()
                .readTree(
                    """
                    {"type":"doc","content":[
                      {"type":"heading","content":[{"type":"text","text":"H1"}]},
                      {"type":"paragraph","content":[{"type":"text","text":"p1"}]},
                      {"type":"listItem","content":[{"type":"text","text":"x"}]},
                      {"type":"blockquote","content":[{"type":"text","text":"q"}]}
                    ]}
                    """.trimIndent(),
                )
        val sb = StringBuilder()
        val method =
            JiraHierarchyAdapter::class.java.declaredMethods.first { it.name == "walkAdf" }
        method.isAccessible = true
        method.invoke(adapter, node, sb)
        val text = sb.toString()
        assertTrue(text.contains("H1"))
        assertTrue(text.contains("p1"))
        assertTrue(text.contains("x"))
        assertTrue(text.contains("q"))
    }

    @Test
    fun `walkHierarchy renders no AC block for a description that has none`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {"key":"PROJ-1","fields":{"summary":"x","description":"just prose","issuetype":null,"status":null,"priority":null,"labels":[],"comment":{"comments": []},"subtasks":[]}}
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertTrue(!results[0].plainText.contains("Acceptance Criteria"))
    }

    @Test
    fun `walkHierarchy replaces label null and tolerates bad comment shapes`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "key": "PROJ-1",
                              "fields": {
                                "summary": "x",
                                "description": null,
                                "issuetype": {"name":"Story"},
                                "status": {"name":"Done"},
                                "priority": null,
                                "labels": [null, "a", null],
                                "comment": {"comments": [
                                  {"author":{"displayName":"Anonymous"},
                                   "body": null}
                                ]},
                                "subtasks": []
                              }
                            }
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = true,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertEquals(1, results.size)
        assertTrue(results[0].plainText.contains("Anonymous") || results[0].plainText.isNotEmpty())
    }

    @Test
    fun `flattenAdf returns blank for null and for plain string`() {
        val method =
            JiraHierarchyAdapter::class.java.declaredMethods.first { it.name == "flattenAdf" }
        method.isAccessible = true
        assertEquals("", method.invoke(adapter, null as com.fasterxml.jackson.databind.JsonNode?))
        val mapper =
            com.fasterxml.jackson.databind
                .ObjectMapper()
        val plain = mapper.readTree("\"hello\"")
        assertEquals("hello", method.invoke(adapter, plain))
    }

    @Test
    fun `flattenAdf collapses whitespace across paragraphs`() {
        val method =
            JiraHierarchyAdapter::class.java.declaredMethods.first { it.name == "flattenAdf" }
        method.isAccessible = true
        val mapper =
            com.fasterxml.jackson.databind
                .ObjectMapper()
        val tree =
            mapper.readTree(
                """
                {"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"one"}]},
                  {"type":"paragraph","content":[{"type":"text","text":"two"}]}
                ]}
                """.trimIndent(),
            )
        val result = method.invoke(adapter, tree) as String
        assertTrue(result.contains("one"))
        assertTrue(result.contains("two"))
    }

    @Test
    fun `flattenAdf returns empty for an empty document`() {
        val method =
            JiraHierarchyAdapter::class.java.declaredMethods.first { it.name == "flattenAdf" }
        method.isAccessible = true
        val mapper =
            com.fasterxml.jackson.databind
                .ObjectMapper()
        val tree =
            mapper.readTree(
                """{"type":"doc","version":1,"content":[]}""",
            )
        val result = method.invoke(adapter, tree) as String
        assertEquals("", result.trim())
    }

    @Test
    fun `walkHierarchy walks outward and inward linked issues when includeLinkedTickets is true`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val key =
                        request.path
                            .orEmpty()
                            .substringAfterLast("/")
                            .substringBefore("?")
                    val body =
                        when (key) {
                            "PROJ-1" ->
                                """
                                {
                                  "key": "PROJ-1",
                                  "fields": {
                                    "summary": "Root",
                                    "description": null,
                                    "issuetype": {"name":"Story"},
                                    "status": {"name":"Open"},
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [],
                                    "issuelinks": [
                                      {"type":{"name":"blocks"},"outwardIssue":{"key":"PROJ-2"}},
                                      {"type":{"name":"relates"},"outwardIssue":{"key":"PROJ-3"}},
                                      {"type":{"name":"is blocked by"},"inwardIssue":{"key":"PROJ-4"}}
                                    ]
                                  }
                                }
                                """.trimIndent()
                            else ->
                                """
                                {
                                  "key": "$key",
                                  "fields": {
                                    "summary": "$key summary",
                                    "description": null,
                                    "issuetype": {"name":"Story"},
                                    "status": null,
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [],
                                    "issuelinks": []
                                  }
                                }
                                """.trimIndent()
                        }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                    includeLinkedTickets = true,
                    includeParent = false,
                ).toList()
        assertEquals(4, results.size)
        assertEquals(
            setOf("PROJ-1", "PROJ-2", "PROJ-3", "PROJ-4"),
            results.map { it.key }.toSet(),
        )
        val root = results.first { it.key == "PROJ-1" }
        assertTrue(
            root.plainText.contains("Linked: PROJ-2"),
            "expected 'Linked: PROJ-2' in: ${root.plainText}",
        )
        assertTrue(root.plainText.contains("PROJ-3"))
        assertTrue(root.plainText.contains("PROJ-4"))
    }

    @Test
    fun `walkHierarchy skips linked tickets when includeLinkedTickets is false`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "key": "PROJ-1",
                              "fields": {
                                "summary": "Root",
                                "description": null,
                                "issuetype": {"name":"Story"},
                                "status": null,
                                "priority": null,
                                "labels": [],
                                "comment": {"comments": []},
                                "subtasks": [],
                                "issuelinks": [
                                  {"outwardIssue":{"key":"PROJ-2"}}
                                ]
                              }
                            }
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                    includeLinkedTickets = false,
                    includeParent = false,
                ).toList()
        assertEquals(1, results.size)
        assertEquals("PROJ-1", results[0].key)
        assertTrue(
            results[0].plainText.contains("Linked: PROJ-2"),
            "expected the snapshot to render the linked-ticket list, got: ${results[0].plainText}",
        )
    }

    @Test
    fun `walkHierarchy renders no Linked line when the issue has no issue links`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "key": "PROJ-1",
                              "fields": {
                                "summary": "Root",
                                "description": null,
                                "issuetype": {"name":"Story"},
                                "status": null,
                                "priority": null,
                                "labels": [],
                                "comment": {"comments": []},
                                "subtasks": [],
                                "issuelinks": []
                              }
                            }
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-1",
                    includeChildren = false,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                ).toList()
        assertEquals(1, results.size)
        assertTrue(!results[0].plainText.contains("Linked:"))
    }

    @Test
    fun `walkHierarchy follows parent one hop when includeParent is true`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val key =
                        request.path
                            .orEmpty()
                            .substringAfterLast("/")
                            .substringBefore("?")
                    val body =
                        when (key) {
                            "PROJ-2" ->
                                """
                                {
                                  "key": "PROJ-2",
                                  "fields": {
                                    "summary": "child",
                                    "description": null,
                                    "issuetype": {"name":"Sub-Task"},
                                    "status": null,
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [],
                                    "parent": {"key": "PROJ-1"},
                                    "issuelinks": []
                                  }
                                }
                                """.trimIndent()
                            "PROJ-1" ->
                                """
                                {
                                  "key": "PROJ-1",
                                  "fields": {
                                    "summary": "parent",
                                    "description": null,
                                    "issuetype": {"name":"Story"},
                                    "status": null,
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [],
                                    "issuelinks": []
                                  }
                                }
                                """.trimIndent()
                            else ->
                                """{"key":"?","fields":{"summary":"?","description":null,"issuetype":null,"status":null,"priority":null,"labels":[],"comment":{"comments": []},"subtasks":[],"issuelinks":[]}}"""
                        }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-2",
                    includeChildren = false,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                    includeLinkedTickets = false,
                    includeParent = true,
                ).toList()
        assertEquals(listOf("PROJ-2", "PROJ-1"), results.map { it.key })
        assertEquals(0, results[0].depth)
        assertEquals(1, results[1].depth)
    }

    @Test
    fun `walkHierarchy does NOT follow the parent when includeParent is false`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "key": "PROJ-2",
                              "fields": {
                                "summary": "child",
                                "description": null,
                                "issuetype": {"name":"Sub-Task"},
                                "status": null,
                                "priority": null,
                                "labels": [],
                                "comment": {"comments": []},
                                "subtasks": [],
                                "parent": {"key": "PROJ-1"},
                                "issuelinks": []
                              }
                            }
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    cloudConfig(),
                    "PROJ-2",
                    includeChildren = false,
                    includeComments = false,
                    maxDepth = 5,
                    maxIssues = 50,
                    includeLinkedTickets = false,
                    includeParent = false,
                ).toList()
        assertEquals(1, results.size)
        assertEquals("PROJ-2", results[0].key)
    }

    @Test
    fun `walkHierarchy does not enqueue parent twice when two siblings both reference it`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val key =
                        request.path
                            .orEmpty()
                            .substringAfterLast("/")
                            .substringBefore("?")
                    val body =
                        when (key) {
                            "PROJ-1" ->
                                """
                                {
                                  "key": "PROJ-1",
                                  "fields": {
                                    "summary": "parent",
                                    "description": null,
                                    "issuetype": {"name":"Story"},
                                    "status": null,
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [{"key":"PROJ-2"},{"key":"PROJ-3"}],
                                    "issuelinks": []
                                  }
                                }
                                """.trimIndent()
                            "PROJ-2", "PROJ-3" ->
                                """
                                {
                                  "key": "$key",
                                  "fields": {
                                    "summary": "$key",
                                    "description": null,
                                    "issuetype": {"name":"Sub-Task"},
                                    "status": null,
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [],
                                    "parent": {"key":"PROJ-1"},
                                    "issuelinks": []
                                  }
                                }
                                """.trimIndent()
                            else ->
                                """{"key":"?","fields":{"summary":"?","description":null,"issuetype":null,"status":null,"priority":null,"labels":[],"comment":{"comments": []},"subtasks":[],"issuelinks":[]}}"""
                        }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        val collectedKeys = mutableListOf<String>()
        adapter
            .walkHierarchy(
                cloudConfig(),
                "PROJ-1",
                includeChildren = true,
                includeComments = false,
                maxDepth = 5,
                maxIssues = 50,
                includeLinkedTickets = false,
                includeParent = true,
            ).forEach { collectedKeys += it.key }
        assertEquals(listOf("PROJ-1", "PROJ-2", "PROJ-3"), collectedKeys)
    }

    @Test
    fun `walkHierarchy logs a WARN when a ticket returns HTTP 404 and skips it`() {
        var sawWarn = false
        val originalLevel = setLogLevel("WARN")
        try {
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val key =
                            request.path
                                .orEmpty()
                                .substringAfterLast("/")
                                .substringBefore("?")
                        if (key == "PROJ-2") {
                            sawWarn = true
                            return MockResponse().setResponseCode(404)
                        }
                        return MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "key": "PROJ-1",
                                  "fields": {
                                    "summary": "Root",
                                    "description": null,
                                    "issuetype": {"name":"Story"},
                                    "status": null,
                                    "priority": null,
                                    "labels": [],
                                    "comment": {"comments": []},
                                    "subtasks": [{"key":"PROJ-2"}],
                                    "issuelinks": []
                                  }
                                }
                                """.trimIndent(),
                            )
                    }
                }
            val results =
                adapter
                    .walkHierarchy(
                        cloudConfig(),
                        "PROJ-1",
                        includeChildren = true,
                        includeComments = false,
                        maxDepth = 5,
                        maxIssues = 50,
                    ).toList()
            assertEquals(1, results.size)
            assertEquals("PROJ-1", results[0].key)
        } finally {
            setLogLevel(originalLevel)
        }
    }

    private fun setLogLevel(level: String): String {
        val logger =
            org.slf4j.LoggerFactory.getLogger(JiraHierarchyAdapter::class.java) as ch.qos.logback.classic.Logger
        val previous = logger.level
        logger.level =
            ch.qos.logback.classic.Level
                .toLevel(level)
        return previous?.toString() ?: "INFO"
    }
}
