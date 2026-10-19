package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.stacklens.adapter.out.mcp.McpAwareGithubTicketBridgeAdapter
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class McpAwareGithubTicketBridgeAdapterToolTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<GithubTicketBridgePort>()
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private lateinit var adapter: McpAwareGithubTicketBridgeAdapter

    private val cfg = GithubRepoConfig(id = 1L, name = "n", owner = "octo", repo = "cat", accessToken = "TOK")
    private val mcpCfg = McpServerConfig(active = true, routeGithub = true, githubToken = "TOK")

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        adapter = McpAwareGithubTicketBridgeAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `branchSha returns sha from github_get_ref response`() {
        val response =
            mapper.writeValueAsString(
                mapOf("object" to mapOf("sha" to "abc123")),
            )
        whenever(gateway.callTool(any(), eq("github_get_ref"), any())).thenReturn(
            McpToolResult(isError = false, text = response),
        )
        val sha = callBranchSha(adapter, mcpCfg, "octo", "cat", "main")
        assertEquals("abc123", sha)
    }

    @Test
    fun `branchSha throws when MCP gateway returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = true, text = "github_get_ref failed"),
        )
        assertThrows(IllegalStateException::class.java) {
            try {
                callBranchSha(adapter, mcpCfg, "octo", "cat", "main")
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.cause!!
            }
        }
    }

    @Test
    fun `branchSha throws when response cannot be parsed`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not valid json"),
        )
        assertThrows(IllegalStateException::class.java) {
            try {
                callBranchSha(adapter, mcpCfg, "octo", "cat", "main")
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.cause!!
            }
        }
    }

    @Test
    fun `branchSha throws when response is missing the sha field`() {
        val response = mapper.writeValueAsString(mapOf("object" to emptyMap<String, Any>()))
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = response),
        )
        assertThrows(IllegalStateException::class.java) {
            try {
                callBranchSha(adapter, mcpCfg, "octo", "cat", "main")
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.cause!!
            }
        }
    }

    @Test
    fun `existingFileSha returns null when github_get_file returns 404 error`() {
        whenever(gateway.callTool(any(), eq("github_get_file"), any())).thenReturn(
            McpToolResult(isError = true, text = "404 not found"),
        )
        val sha = callExistingFileSha(adapter, mcpCfg, "octo", "cat", "main", "README.md")
        assertEquals(null, sha)
    }

    @Test
    fun `existingFileSha returns null when response has no sha field`() {
        val response = mapper.writeValueAsString(emptyMap<String, Any>())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = response),
        )
        val sha = callExistingFileSha(adapter, mcpCfg, "octo", "cat", "main", "README.md")
        assertEquals(null, sha)
    }

    @Test
    fun `existingFileSha returns sha from github_get_file response`() {
        val response = mapper.writeValueAsString(mapOf("sha" to "deadbeef"))
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = response),
        )
        val sha = callExistingFileSha(adapter, mcpCfg, "octo", "cat", "main", "README.md")
        assertEquals("deadbeef", sha)
    }

    @Test
    fun `fetchFileContent returns null when MCP returns error`() {
        whenever(gateway.callTool(any(), eq("github_get_file"), any())).thenReturn(
            McpToolResult(isError = true, text = "404 not found"),
        )
        var result: String? = null
        try {
            result = callFetchFileContent(adapter, mcpCfg, "octo", "cat", "main", "README.md")
        } catch (_: Throwable) {
        }
        assertEquals(null, result)
    }

    @Test
    fun `fetchFileContent returns content from MCP response`() {
        val original = "hello world"
        val encoded =
            java.util.Base64
                .getEncoder()
                .encodeToString(original.toByteArray())
        val response = mapper.writeValueAsString(mapOf("content" to encoded))
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = response),
        )
        val content = callFetchFileContent(adapter, mcpCfg, "octo", "cat", "main", "README.md")
        assertEquals(original, content)
    }

    @Test
    fun `fetchFileContent returns null when response has no content field`() {
        val response = mapper.writeValueAsString(emptyMap<String, Any>())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = response),
        )
        val content = callFetchFileContent(adapter, mcpCfg, "octo", "cat", "main", "README.md")
        assertEquals(null, content)
    }

    private fun callBranchSha(
        a: McpAwareGithubTicketBridgeAdapter,
        m: McpServerConfig,
        owner: String,
        repo: String,
        branch: String,
    ): String {
        val func = McpAwareGithubTicketBridgeAdapter::class.declaredFunctions.first { it.name == "branchSha" }
        func.javaMethod!!.isAccessible = true
        return func.call(a, m, owner, repo, branch) as String
    }

    private fun callExistingFileSha(
        a: McpAwareGithubTicketBridgeAdapter,
        m: McpServerConfig,
        owner: String,
        repo: String,
        branch: String,
        path: String,
    ): String? {
        val func = McpAwareGithubTicketBridgeAdapter::class.declaredFunctions.first { it.name == "existingFileSha" }
        func.javaMethod!!.isAccessible = true
        return func.call(a, m, owner, repo, branch, path) as String?
    }

    private fun callFetchFileContent(
        a: McpAwareGithubTicketBridgeAdapter,
        m: McpServerConfig,
        owner: String,
        repo: String,
        branch: String,
        path: String,
    ): String? {
        val func = McpAwareGithubTicketBridgeAdapter::class.declaredFunctions.first { it.name == "fetchFileContent" }
        func.javaMethod!!.isAccessible = true
        return try {
            func.call(a, m, owner, repo, branch, path) as String?
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause!!
        }
    }
}
