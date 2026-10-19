package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.testsupport.McpRoutingFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareConfluenceSearchAdapterMappingTest {
    private val gateway: McpGatewayPort = mock()
    private val delegate: ConfluenceSearchPort = mock()
    private val adapter =
        McpAwareConfluenceSearchAdapter(
            mcpRepo = McpRoutingFixtures.repoWith(McpRoutingFixtures.routed()),
            gateway = gateway,
            objectMapper = ObjectMapper(),
            delegate = delegate,
        )

    private fun config(
        baseUrl: String = McpRoutingFixtures.CONFLUENCE_BASE_URL,
        cloudMode: Boolean = true,
    ) = ConfluenceConfig(
        name = "wiki",
        baseUrl = baseUrl,
        username = "u",
        apiToken = "t",
        cloudMode = cloudMode,
        active = true,
    )

    private fun results(items: String) = """{"results":$items}"""

    @Test
    fun `a cloud hit without a webui link is built from the space and page id`() {
        val mapped =
            adapter.mapToSearchResults(
                results("""[{"title":"Runbook","excerpt":"text","space":{"key":"OPS"},"id":"4711"}]"""),
                config(baseUrl = "https://host.example.net"),
                "runbook",
                3L,
            )

        assertEquals(1, mapped.size)
        assertEquals("https://host.example.net/wiki/spaces/OPS/pages/4711", mapped.single().repoUrl)
    }

    @Test
    fun `a server hit keeps the webui path below the configured base`() {
        val mapped =
            adapter.mapToSearchResults(
                results("""[{"title":"Runbook","_links":{"webui":"/display/OPS/Runbook"},"space":{"key":""},"id":""}]"""),
                config(cloudMode = false),
                "runbook",
                3L,
            )

        assertEquals("https://wiki.example.com/display/OPS/Runbook", mapped.single().repoUrl)
    }

    @Test
    fun `an absolute webui link wins and a wiki base is not doubled`() {
        val mapped =
            adapter.mapToSearchResults(
                results(
                    """[
                      {"title":"A","_links":{"webui":"https://host/pages/viewpage.action?pageId=1"},"space":{"key":"S"},"id":"1"},
                      {"title":"B","space":{"key":"S"},"id":"2"}
                    ]""",
                ),
                config(baseUrl = "https://host/wiki"),
                "a",
                4L,
            )

        assertEquals("https://host/pages/viewpage.action?pageId=1", mapped[0].repoUrl)
        assertEquals("https://host/wiki/spaces/S/pages/2", mapped[1].repoUrl)
    }

    @Test
    fun `hits without a title and unparsable payloads are ignored`() {
        assertTrue(adapter.mapToSearchResults(results("""[{"title":"  "}]"""), config(), "k", 1L).isEmpty())
        assertTrue(adapter.mapToSearchResults("no json at all", config(), "k", 1L).isEmpty())
        assertTrue(adapter.mapToSearchResults("""{"results":{"title":"A"}}""", config(), "k", 1L).isEmpty())
    }

    @Test
    fun `an offset search is delegated to the direct adapter`() {
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any())).thenReturn(emptyList())

        assertEquals(0, adapter.searchKeyword(config(), "runbook", emptyList(), 5L, 10).size)
    }

    @Test
    fun `a cql with space filters is sent through the gateway`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(text = results("""[{"title":"Runbook","space":{"key":"OPS"},"id":"9"}]""")))

        val mapped = adapter.searchKeyword(config(), "runbook", listOf("OPS", " ", "DOCS"), 6L, 0)

        assertEquals(1, mapped.size)
        assertTrue(adapter.buildCql("quote\"x", listOf("A", "B")).contains("space in (\"A\",\"B\")"))
    }

    @Test
    fun `a mismatching base url uses the delegate`() {
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any())).thenReturn(emptyList())

        assertEquals(0, adapter.searchKeyword(config(baseUrl = "https://other.example.com"), "k", emptyList(), 1L, 0).size)
    }

    @Test
    fun `a gateway error falls back to the delegate`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = "nope", isError = true))
        whenever(delegate.searchKeyword(any(), any(), any(), any(), any())).thenReturn(emptyList())

        assertEquals(0, adapter.searchKeyword(config(), "k", emptyList(), 1L, 0).size)
    }
}
