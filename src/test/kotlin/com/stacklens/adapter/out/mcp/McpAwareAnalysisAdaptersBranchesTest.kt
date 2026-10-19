package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareAnalysisAdaptersBranchesTest {
    private val mapper = ObjectMapper()
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()

    private val jiraConfig =
        JiraConfig(name = "j", baseUrl = "https://jira.example.com", username = "u", apiToken = "t")

    private val gitlabCfg = GitlabConfig(name = "g", baseUrl = "https://gl", accessToken = "T")
    private val githubCfg = GithubRepoConfig(name = "n", owner = "octo", repo = "cat", accessToken = "T")

    private val jiraMcpCfg =
        McpServerConfig(active = true, routeJira = true, jiraBaseUrl = "https://jira.example.com", jiraApiToken = "T")
    private val gitlabMcpCfg = McpServerConfig(active = true, routeGitlab = true, gitlabAccessToken = "T")
    private val githubMcpCfg = McpServerConfig(active = true, routeGithub = true, githubToken = "T")

    @Test
    fun `Jira description rendered as object when MCP returns structured description`() {
        val jiraAdapter =
            McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, mapper, mock())
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(jiraMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{
                    "key":"PROJ-1",
                    "fields":{
                        "summary":"S",
                        "description":{"type":"doc","version":1,"content":[]},
                        "issuetype":{"name":"Story"},
                        "status":{"name":"Open"},
                        "labels":["a","", "  "],
                        "components":[{"name":"comp1"},{"name":""}]
                    }
                }""",
            ),
        )
        val data = jiraAdapter.fetchTicketByKey(jiraConfig, "PROJ-1")
        assertTrue(
            data.description.startsWith("{\"type"),
            "description should be stringified ADF object; was: ${data.description}",
        )
        assertEquals(listOf("a"), data.labels)
        assertEquals(listOf("comp1"), data.components)
    }

    @Test
    fun `Jira description is empty when missing entirely`() {
        val jiraAdapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, mapper, mock())
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(jiraMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"key":"PROJ-2","fields":{"summary":"S"}}""",
            ),
        )
        val data = jiraAdapter.fetchTicketByKey(jiraConfig, "PROJ-2")
        assertEquals("", data.description)
    }

    @Test
    fun `Jira comment with blank author renders body only`() {
        val jiraAdapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, mapper, mock())
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(jiraMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{
                    "key":"PROJ-3",
                    "fields":{
                        "summary":"S",
                        "comment":{"comments":[
                            {"author":{"displayName":""},"body":"hi"}
                        ]}
                    }
                }""",
            ),
        )
        val data = jiraAdapter.fetchTicketByKey(jiraConfig, "PROJ-3")
        assertEquals("hi", data.comments)
    }

    @Test
    fun `Jira comment body as object is stringified`() {
        val jiraAdapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, mapper, mock())
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(jiraMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{
                    "key":"PROJ-4",
                    "fields":{
                        "summary":"S",
                        "comment":{"comments":[
                            {"author":{"displayName":"alice"},"body":{"type":"doc","content":[1]}}
                        ]}
                    }
                }""",
            ),
        )
        val data = jiraAdapter.fetchTicketByKey(jiraConfig, "PROJ-4")
        assertTrue(data.comments.startsWith("alice: {\"type"))
    }

    @Test
    fun `Jira priority omitted when blank`() {
        val jiraAdapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, mapper, mock())
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(jiraMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{
                    "key":"PROJ-5",
                    "fields":{"summary":"S","priority":{"name":"  "}}
                }""",
            ),
        )
        val data = jiraAdapter.fetchTicketByKey(jiraConfig, "PROJ-5")
        assertEquals(null, data.priority)
    }

    @Test
    fun `Jira priority set when non-blank`() {
        val jiraAdapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, mapper, mock())
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(jiraMcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{
                    "key":"PROJ-6",
                    "fields":{"summary":"S","priority":{"name":"High"}}
                }""",
            ),
        )
        val data = jiraAdapter.fetchTicketByKey(jiraConfig, "PROJ-6")
        assertEquals("High", data.priority)
    }

    @Test
    fun `GitLab MR mapping handles changes wrapped in an object with changes array`() {
        val delegate = mock<MrFetchPort>()
        val adapter = McpAwareMrFetchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"iid":"1","title":"T","description":"","source_branch":"feat","target_branch":"main","author":{"name":"alice"},"state":"opened"}""",
                ),
            ).thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"changes":[
                        {"new_path":"a.kt","old_path":"a.kt","diff":"+ new line"}
                    ],"diff_refs":{"base_sha":"b","start_sha":"s","head_sha":"h"}}""",
                ),
            )
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals(1, data.changedFiles.size)
        assertTrue(data.changedFiles.contains("a.kt"))
        assertTrue(data.diffText.contains("+ new line"))
        assertEquals("b", data.diffBaseSha)
    }

    @Test
    fun `GitLab MR mapping falls back to diff_refs on MR node when changes is not an object`() {
        val delegate = mock<MrFetchPort>()
        val adapter = McpAwareMrFetchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text =
                        """{"iid":"1","title":"T","description":"","source_branch":"feat","target_branch":"main","author":{"name":"a"},"state":"opened","diff_refs":{"base_sha":"B","start_sha":"S","head_sha":"H"}}""",
                ),
            ).thenReturn(
                McpToolResult(
                    isError = false,
                    text =
                        "\"not-an-object-at-all\"",
                ),
            )
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.changedFiles.isEmpty())
        assertEquals("", data.diffBaseSha)
    }

    @Test
    fun `GitLab MR mapping handles changes with oldPath-only and blank diff`() {
        val delegate = mock<MrFetchPort>()
        val adapter = McpAwareMrFetchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text =
                        """{"iid":"1","title":"T","description":"","source_branch":"f","target_branch":"m","author":{"name":"a"},"state":"o","diff_refs":{"base_sha":"b","start_sha":"s","head_sha":"h"}}""",
                ),
            ).thenReturn(
                McpToolResult(
                    isError = false,
                    text = """[
                        {"new_path":"","old_path":"old.kt","diff":""}
                    ]""",
                ),
            )
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals(listOf("old.kt"), data.changedFiles)
        assertEquals("", data.diffText)
    }

    @Test
    fun `GitHub PR mapping handles files with non-blank patch`() {
        val delegate = mock<MrFetchPort>()
        val adapter = McpAwareMrFetchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(githubMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"number":9,"title":"T","body":"B","head":{"ref":"f","sha":"H"},"base":{"ref":"m","sha":"B"},"user":{"login":"u"},"state":"open"}""",
                ),
            ).thenReturn(
                McpToolResult(
                    isError = false,
                    text = """[{"filename":"foo.kt","patch":"@@ -1 +1 @@\\n-old\\n+new","status":"modified"}]""",
                ),
            )
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals(listOf("foo.kt"), data.changedFiles)
        assertTrue(data.diffText.contains("--- a/foo.kt"))
        assertTrue(data.diffText.contains("+++ b/foo.kt"))
        assertEquals("B", data.diffBaseSha)
        assertEquals("H", data.diffHeadSha)
    }

    @Test
    fun `GitHub PR mapping handles files object (not array) as null`() {
        val delegate = mock<MrFetchPort>()
        val adapter = McpAwareMrFetchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(githubMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"number":9,"title":"T","body":"B","head":{"ref":"f","sha":"H"},"base":{"ref":"m","sha":"B"},"user":{"login":"u"},"state":"open"}""",
                ),
            ).thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"not":"array"}""",
                ),
            )
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.changedFiles.isEmpty())
        assertEquals("", data.diffText)
    }

    @Test
    fun `GitHub PR mapping only emits filename when patch is non-blank`() {
        val delegate = mock<MrFetchPort>()
        val adapter = McpAwareMrFetchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(githubMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    isError = false,
                    text = """{"number":9,"title":"T","body":"B","head":{"ref":"f","sha":"H"},"base":{"ref":"m","sha":"B"},"user":{"login":"u"},"state":"open"}""",
                ),
            ).thenReturn(
                McpToolResult(
                    isError = false,
                    text = """[{"filename":"","patch":""}, {"filename":"foo.kt","patch":""}, {"filename":"bar.kt","patch":"@@"}]""",
                ),
            )
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals(listOf("foo.kt", "bar.kt"), data.changedFiles)
        assertTrue(data.diffText.contains("bar.kt"))
        assertFalse(data.diffText.contains("foo.kt"))
        assertEquals("B", data.diffBaseSha)
        assertEquals("H", data.diffHeadSha)
    }

    private fun assertFalse(
        condition: Boolean,
        message: String = "",
    ) = org.junit.jupiter.api.Assertions
        .assertFalse(condition, message)

    @Test
    fun `GitLab URL parser handles HTTPS variant`() {
        val delegate = mock<MrFetchPort>()
        val adapter = McpAwareMrFetchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(gitlabMcpCfg)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"iid":"1","title":"T"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"changes":[]}"""))
        val result = adapter.fetchMrData("https://gl/g/r/-/merge_requests/1", listOf(gitlabCfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
    }

    @Test
    fun `GitHub URL parser rejects non-github dot-com host`() {
        val delegate = mock<MrFetchPort>()
        val expected = MrFetchPort.MrFetchResult.Failure("delegate-fallback")
        whenever(delegate.fetchMrData(any(), any(), any())).thenReturn(expected)
        val adapter = McpAwareMrFetchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(githubMcpCfg)
        val result = adapter.fetchMrData("https://github.example/octo/cat/pull/9", emptyList(), listOf(githubCfg))
        assertEquals(expected, result)
    }
}
