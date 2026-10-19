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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class JiraHierarchyAdapterIssuePayloadCoverageTest {
    private lateinit var server: MockWebServer
    private val resolver = mock<JiraApiVersionResolver>()

    private lateinit var config: JiraConfig

    @BeforeEach
    fun startJira() {
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
    fun stopJira() {
        server.shutdown()
    }

    private fun adapter() =
        JiraHierarchyAdapter(
            ReactiveHttpClientFactory(connectTimeoutMs = 5000, readTimeoutMs = 10000),
            resolver,
        )

    private fun serve(pages: Map<String, String?>) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val key =
                        request.path
                            .orEmpty()
                            .substringAfter("/issue/")
                            .substringBefore("?")
                    val body =
                        if (pages.containsKey(key)) pages[key] ?: "" else null
                    body ?: return MockResponse().setResponseCode(404).setBody("no such issue")
                    return MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
                }
            }
    }

    private fun issue(
        key: String,
        fields: String,
    ): String = """{"key":"$key","fields":{$fields}}"""

    private companion object {
        const val KEY = "PROJ-1"
    }

    private fun fieldsWith(description: String?): String =
        """"summary":"Root summary","issuetype":{"name":"Story"},"status":{"name":"Done"},""" +
            """"priority":{"name":"High"},"labels":["a"],"subtasks":[],"issuelinks":[],""" +
            """"description":${description ?: "null"}"""

    private fun walkRoot(description: String?): List<com.stacklens.application.port.out.knowledge.JiraIssueSnapshot> {
        serve(mapOf(KEY to issue(KEY, fieldsWith(description))))
        return adapter()
            .walkHierarchy(
                config,
                KEY,
                includeChildren = false,
                includeComments = true,
                maxDepth = 1,
                maxIssues = 5,
                includeLinkedTickets = true,
                includeParent = false,
            ).toList()
    }

    @Test
    fun `a nested adf tree linearises text leaves and ignores unusable branches`() {
        val adf =
            "{\"type\":\"doc\",\"content\":[\"bare text leaf\"," +
                "{\"type\":\"paragraph\",\"text\":123}," +
                "{\"type\":\"list\",\"content\":{\"type\":\"not-an-array\"}}," +
                "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"real leaf marker\"}]}]}"

        val snapshot = walkRoot(adf).single()

        assertTrue(snapshot.plainText.contains("real leaf marker"))
        assertTrue(snapshot.plainText.contains("bare text leaf"))
        assertFalse(snapshot.plainText.contains("123"))
        assertFalse(snapshot.plainText.contains("not-an-array"))
    }

    @Test
    fun `a null description yields no description section`() {
        val snapshot = walkRoot("null").single()

        assertTrue(snapshot.plainText.contains("Root summary"))
    }

    @Test
    fun `acceptance criteria are cut at the next section header and normalised`() {
        val text =
            """"AK:\n- [ ] a long first criterion\n- [x] 2. a long second criterion\n) a long third criterion\n- [ ] ab\nBeschreibung: cut here\nignored trailing criterion""""
        val criteria = walkRoot(text).single().plainText.substringAfter("## Acceptance Criteria")

        assertTrue(criteria.contains("a long first criterion"))
        assertTrue(criteria.contains("a long second criterion"))
        assertTrue(criteria.contains("a long third criterion"))
        assertFalse(criteria.contains("ignored trailing criterion"))
        assertFalse(criteria.contains("ab"))
    }

    @Test
    fun `acceptance criteria without a following section are taken wholesale`() {
        val text = """"Akzeptanzkriterien:\\n* a long bullet criterion\\n-4. another long criterion""""

        val criteria = walkRoot(text).single().plainText.substringAfter("## Acceptance Criteria")

        assertTrue(criteria.contains("a long bullet criterion"))
        assertTrue(criteria.contains("another long criterion"))
    }

    @Test
    fun `labels comments and links tolerate malformed payloads`() {
        val fields =
            """"summary":"Root summary","issuetype":{"name":"Story"},"status":{"name":"Done"},""" +
                """"priority":{"name":"High"},"labels":"not-an-array","description":null,""" +
                """"comment":{"comments":[{"author":{"name":"bob"},"body":null},""" +
                """{"author":{"displayName":"alice"},"body":{"type":"text","text":"comment body marker"}},""" +
                """{"body":{"type":"text","text":"anonymous comment marker"}}]},""" +
                """"subtasks":[],"issuelinks":[{"outwardIssue":{"key":"  "},"inwardIssue":{"key":"LINK-1"}},""" +
                """{"outwardIssue":{},"inwardIssue":{"key":"LINK-2"}}]"""
        serve(mapOf(KEY to issue(KEY, fields)))

        val snapshot =
            adapter()
                .walkHierarchy(
                    config,
                    KEY,
                    includeChildren = false,
                    includeComments = true,
                    maxDepth = 1,
                    maxIssues = 5,
                    includeLinkedTickets = true,
                    includeParent = true,
                ).toList()
                .single()

        assertTrue(snapshot.plainText.contains("comment body marker"))
        assertTrue(snapshot.plainText.contains("anonymous comment marker"))
        assertFalse(snapshot.plainText.contains("body null"))
    }

    @Test
    fun `an issue answered with an empty body is skipped`() {
        serve(mapOf(KEY to null))

        val snapshots =
            adapter()
                .walkHierarchy(config, KEY, false, true, 1, 5, true, false)
                .toList()

        assertEquals(emptyList<com.stacklens.application.port.out.knowledge.JiraIssueSnapshot>(), snapshots)
    }

    @Test
    fun `a ticket reference without a jira key resolves to nothing`() {
        val adapter = adapter()

        assertEquals("PROJ-12", adapter.extractTicketKey("https://jira.example/browse/PROJ-12"))
        assertEquals(null, adapter.extractTicketKey("no key inside this text"))
        assertThrows(IllegalStateException::class.java) {
            adapter.ensureHostMatches(config, "not-a-ticket")
        }
    }

    @Test
    fun `a ticket url on another host is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            adapter().ensureHostMatches(config, "https://other.example/browse/PROJ-1")
        }
        adapter().ensureHostMatches(config, "${config.baseUrl}/browse/PROJ-1")
    }
}
