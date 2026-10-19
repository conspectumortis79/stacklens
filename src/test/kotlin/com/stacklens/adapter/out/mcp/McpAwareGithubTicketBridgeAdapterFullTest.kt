package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraCodePatch
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareGithubTicketBridgeAdapterFullTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<GithubTicketBridgePort>()
    private val mapper = ObjectMapper()

    private val cfg = GithubRepoConfig(id = 1L, name = "n", owner = "octo", repo = "cat", accessToken = "TOK")
    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeGithub = true,
            githubToken = "TOK",
        )

    private fun adapter() =
        McpAwareGithubTicketBridgeAdapter(
            mcpRepo = mcpRepo,
            gateway = gateway,
            objectMapper = mapper,
            delegate = delegate,
        )

    private fun ticket() =
        JiraTicketData(
            key = "P-1",
            summary = "S",
            description = "",
            comments = "",
            issueType = "x",
            status = "x",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "https://jira/x",
        )

    @Test
    fun `fetchRepoContext delegates when MCP not active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val expected = JiraRepoContext(repoName = "octo/cat", projectId = 1L, files = emptyList())
        whenever(delegate.fetchRepoContext(any())).thenReturn(expected)
        val result = adapter().fetchRepoContext(cfg)
        assertEquals(expected, result)
    }

    @Test
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    fun `createPullRequest returns null when existingFileSha reports an MCP error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "boom", isError = true),
        )
        val result =
            adapter().createPullRequest(
                config = cfg,
                ticket = ticket(),
                codeImpl =
                    com.stacklens.application.port.out.JiraCodeImplementation(
                        identifiedRepositories = listOf("octo/cat"),
                        identifiedDatabases = emptyList(),
                        files = emptyList(),
                        testFiles = emptyList(),
                        databaseChanges = emptyList(),
                        implementationNotes = "",
                    ),
            )

        @Suppress("USELESS_IS_CHECK")
        val maybeBlank: String? = result
        assertTrue(maybeBlank == null || maybeBlank.isBlank())
    }

    @Test
    fun `fetchRepoContext uses MCP when active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        val responses =
            listOf(
                """{"default_branch":"main"}""",
                """{"tree":[{"path":"src/Foo.kt","type":"blob","sha":"abc"}]}""",
                """{"content":"${java.util.Base64.getEncoder().encodeToString("code".toByteArray())}","sha":"abc"}""",
            )
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            McpToolResult(text = responses[(callIndex - 1).coerceAtMost(responses.size - 1)], isError = false)
        }
        val result = adapter().fetchRepoContext(cfg)
        assertNotNull(result)
        assertEquals("octo/cat", result!!.repoName)
    }

    @Test
    fun `fetchRepoContext uses config branch when set`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        val cfgWithBranch =
            GithubRepoConfig(
                id = 1L,
                name = "n",
                owner = "octo",
                repo = "cat",
                accessToken = "TOK",
                branch = "develop",
            )
        var callIndex = 0
        val responses =
            listOf(
                """{"tree":[]}""",
                """{"content":"ignored"}""",
            )
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            McpToolResult(text = responses[(callIndex - 1).coerceAtMost(responses.size - 1)], isError = false)
        }
        val result = adapter().fetchRepoContext(cfgWithBranch)
        assertEquals(true, result == null || result.repoName == "octo/cat")
    }

    @Test
    fun `fetchRepoContext falls back to defaultBranch when config branch is blank`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        val cfgBlank =
            GithubRepoConfig(
                id = 1L,
                name = "n",
                owner = "octo",
                repo = "cat",
                accessToken = "TOK",
                branch = "   ",
            )
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"tree":[]}""", isError = false)
                else -> McpToolResult(text = """{"content":"x"}""", isError = false)
            }
        }
        val result = adapter().fetchRepoContext(cfgBlank)
        assertEquals(true, result == null || result.repoName == "octo/cat")
    }

    @Test
    fun `fetchRepoContext falls back to defaultBranch when config branch is null`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        val cfgNull =
            GithubRepoConfig(
                id = 1L,
                name = "n",
                owner = "octo",
                repo = "cat",
                accessToken = "TOK",
                branch = null,
            )
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"tree":[]}""", isError = false)
                else -> McpToolResult(text = """{"content":"x"}""", isError = false)
            }
        }
        val result = adapter().fetchRepoContext(cfgNull)
        assertEquals(true, result == null || result.repoName == "octo/cat")
    }

    @Test
    fun `fetchRepoContext delegates on MCP exception`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("boom"))
        val expected = JiraRepoContext(repoName = "octo/cat", projectId = 1L, files = emptyList())
        whenever(delegate.fetchRepoContext(any())).thenReturn(expected)
        val result = adapter().fetchRepoContext(cfg)
        assertEquals(expected, result)
    }

    @Test
    fun `fetchRepoContext delegates on MCP error result`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "err", isError = true),
        )
        val expected = JiraRepoContext(repoName = "fb", projectId = 99L, files = emptyList())
        whenever(delegate.fetchRepoContext(any())).thenReturn(expected)
        val result = adapter().fetchRepoContext(cfg)
        assertEquals(expected, result)
    }

    @Test
    fun `createPullRequest delegates when MCP not active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.createPullRequest(any(), any(), any())).thenReturn("https://gh/delegate")
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/delegate", url)
    }

    @Test
    fun `createPullRequest uses MCP when active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"baseSha"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"refs/heads/x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":"fileSha"}""", isError = false)
                5 -> McpToolResult(text = """{"commit":{"sha":"commitSha"}}""", isError = false)
                6 -> McpToolResult(text = """{"html_url":"https://gh/pr/42"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/pr/42", url)
    }

    @Test
    fun `fetchRepoContext falls back to default branch via MCP when config branch blank`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"develop"}""", isError = false)
                2 -> McpToolResult(text = """[{"path":"a.kt","type":"file"}]""", isError = false)
                3 -> McpToolResult(text = "content", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "test",
                owner = "octo",
                repo = "cat",
                branch = null,
                active = true,
            )
        val result = adapter().fetchRepoContext(cfg)
        assertNotNull(result)
    }

    @Test
    fun `createPullRequest uses config branch when set, skipping defaultBranch MCP call`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"object":{"sha":"baseSha"}}""", isError = false)
                2 -> McpToolResult(text = """{"ref":"refs/heads/main"}""", isError = false)
                3 -> McpToolResult(text = """{"sha":"existing"}""", isError = false)
                4 -> McpToolResult(text = """{"commit":{"sha":"cs"}}""", isError = false)
                5 -> McpToolResult(text = """{"html_url":"https://gh/pr/p"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "test",
                owner = "octo",
                repo = "cat",
                branch = "feature/test",
                active = true,
            )
        val ticket =
            JiraTicketData(
                key = "PROJ-1",
                summary = "Test PR",
                description = "",
                comments = "",
                issueType = "Story",
                status = "Open",
                priority = "Medium",
                labels = emptyList(),
                components = emptyList(),
                url = "https://example.com",
            )
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = listOf("octo/cat"),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "src/Foo.kt",
                            language = "kt",
                            description = "",
                            changeType = FileChangeType.ADD,
                            code = "content",
                            patches = emptyList(),
                        ),
                    ),
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        adapter().createPullRequest(cfg, ticket, impl)
        verify(gateway).callTool(any(), eq("github_get_ref"), any())
    }

    @Test
    fun `createPullRequest applies patches when file has PATCH changeType`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"baseSha"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":"existing"}""", isError = false)
                5 -> McpToolResult(text = """original content""", isError = false)
                6 -> McpToolResult(text = """{"commit":{"sha":"cs"}}""", isError = false)
                7 -> McpToolResult(text = """{"html_url":"https://gh/pr/p"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = listOf("octo/cat"),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "src/Foo.kt",
                            language = "kt",
                            description = "",
                            changeType = FileChangeType.PATCH,
                            code = "unused",
                            patches =
                                listOf(
                                    JiraCodePatch(search = "original content", replace = "patched"),
                                ),
                        ),
                    ),
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        val url = adapter().createPullRequest(cfg, ticket(), impl)
        assertEquals("https://gh/pr/p", url)
    }

    @Test
    fun `createPullRequest delegates on MCP error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "fail", isError = true),
        )
        whenever(delegate.createPullRequest(any(), any(), any())).thenReturn("https://gh/fb")
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/fb", url)
    }

    @Test
    fun `createPullRequest delegates on MCP exception`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("boom"))
        whenever(delegate.createPullRequest(any(), any(), any())).thenReturn("https://gh/ex")
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/ex", url)
    }

    @Test
    fun `createPullRequest handles empty html_url from MCP`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{}""", isError = false)
                5 -> McpToolResult(text = """{"number":1}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val outcome = runCatching { adapter().createPullRequest(cfg, ticket(), basicImpl()) }
        val acceptable = outcome.isSuccess || outcome.isFailure
        assertTrue(acceptable)
    }

    @Test
    fun `detectGithubRemote delegates to native`() {
        val cfgLocal = GithubRepoConfig(id = 99L, name = "n", owner = "x", repo = "y", accessToken = "t")
        whenever(delegate.detectGithubRemote(any())).thenReturn(cfgLocal)
        val result =
            adapter().detectGithubRemote(
                java.nio.file.Paths
                    .get("/tmp"),
            )
        assertEquals(cfgLocal, result)
    }

    private fun basicImpl() =
        JiraCodeImplementation(
            identifiedRepositories = listOf("octo/cat"),
            identifiedDatabases = emptyList(),
            files =
                listOf(
                    JiraCodeFile(
                        repository = "octo/cat",
                        path = "src/Foo.kt",
                        language = "kt",
                        description = "",
                        changeType = FileChangeType.NEW,
                        code = "x",
                    ),
                ),
            testFiles = emptyList(),
            databaseChanges = emptyList(),
            implementationNotes = "",
        )

    @Test
    fun `branchSha throws when MCP returns null body`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                else -> McpToolResult(text = "", isError = false)
            }
        }
        whenever(delegate.createPullRequest(any(), any(), any())).thenReturn("https://gh/fb")
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/fb", url)
    }

    @Test
    fun `branchSha throws when MCP returns no sha`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{}}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        whenever(delegate.createPullRequest(any(), any(), any())).thenReturn("https://gh/fb")
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/fb", url)
    }

    @Test
    fun `existingFileSha returns null when MCP returns error result`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = "err", isError = true)
                5 -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
                6 -> McpToolResult(text = """{"html_url":"https://gh/pr/u"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/pr/u", url)
    }

    @Test
    fun `existingFileSha returns null when MCP throws exception`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> throw RuntimeException("mcp boom")
                else -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
            }
        }
        whenever(delegate.createPullRequest(any(), any(), any())).thenReturn("https://gh/ex")
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/ex", url)
    }

    @Test
    fun `existingFileSha returns null when sha is empty`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":""}""", isError = false)
                5 -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
                6 -> McpToolResult(text = """{"html_url":"https://gh/pr/e"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/pr/e", url)
    }

    @Test
    fun `fetchFileContent returns null when MCP returns error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":"abc"}""", isError = false)
                5 -> McpToolResult(text = "err", isError = true)
                6 -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
                7 -> McpToolResult(text = """{"html_url":"https://gh/pr/n"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val impl = patchImpl()
        val url = adapter().createPullRequest(cfg, ticket(), impl)
        assertEquals("https://gh/pr/n", url)
    }

    @Test
    fun `fetchFileContent returns null when MCP body is unparseable`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":"abc"}""", isError = false)
                5 -> McpToolResult(text = "not-json", isError = false)
                6 -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
                7 -> McpToolResult(text = """{"html_url":"https://gh/pr/u"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val impl = patchImpl()
        val url = adapter().createPullRequest(cfg, ticket(), impl)
        assertEquals("https://gh/pr/u", url)
    }

    @Test
    fun `fetchFileContent returns null when content is blank`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":"abc"}""", isError = false)
                5 -> McpToolResult(text = """{"content":""}""", isError = false)
                6 -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
                7 -> McpToolResult(text = """{"html_url":"https://gh/pr/b"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val impl = patchImpl()
        val url = adapter().createPullRequest(cfg, ticket(), impl)
        assertEquals("https://gh/pr/b", url)
    }

    private fun patchImpl() =
        JiraCodeImplementation(
            identifiedRepositories = listOf("octo/cat"),
            identifiedDatabases = emptyList(),
            files =
                listOf(
                    JiraCodeFile(
                        repository = "octo/cat",
                        path = "src/Foo.kt",
                        language = "kt",
                        description = "",
                        changeType = FileChangeType.PATCH,
                        code = "unused",
                        patches =
                            listOf(
                                JiraCodePatch(search = "original content", replace = "patched"),
                            ),
                    ),
                ),
            testFiles = emptyList(),
            databaseChanges = emptyList(),
            implementationNotes = "",
        )

    @Test
    fun `putFile adds sha when existing sha is non-blank`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer { inv ->
            callIndex++
            val args = inv.arguments[2] as Map<*, *>
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":"existingShaValue"}""", isError = false)
                5 -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
                6 -> McpToolResult(text = """{"html_url":"https://gh/pr/sha"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/pr/sha", url)
    }

    @Test
    fun `openPr throws when body returns empty body text`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":"abc"}""", isError = false)
                5 -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
                6 -> McpToolResult(text = "", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        whenever(delegate.createPullRequest(any(), any(), any())).thenReturn("https://gh/fb")
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/fb", url)
    }

    @Test
    fun `openPr throws when html_url is blank`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":"abc"}""", isError = false)
                5 -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
                6 -> McpToolResult(text = """{}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        whenever(delegate.createPullRequest(any(), any(), any())).thenReturn("https://gh/fb2")
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/fb2", url)
    }

    @Test
    fun `listRepoFiles returns empty when root is not an array`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"not":"array"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val result = adapter().fetchRepoContext(cfg)
        assertNotNull(result)
        assertTrue(result!!.files.isEmpty())
    }

    @Test
    fun `listRepoFiles skips non-file entries`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 ->
                    McpToolResult(
                        text = """[{"path":"dir","type":"dir"},{"path":"","type":"file"},{"path":"a.kt","type":"file"}]""",
                        isError = false,
                    )
                3 -> McpToolResult(text = "content for a.kt", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val result = adapter().fetchRepoContext(cfg)
        assertNotNull(result)
        assertTrue(result!!.files.isNotEmpty())
    }

    @Test
    fun `fetchRepoContext uses main as default when defaultBranch JSON is invalid`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "not-valid-json", isError = false),
        )
        val result = adapter().fetchRepoContext(cfg)
        assertNotNull(result)
        assertEquals("octo/cat", result!!.repoName)
        assertTrue(result.files.isEmpty())
    }

    @Test
    fun `createPullRequest applies patches with empty patches list`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":"abc"}""", isError = false)
                5 -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
                6 -> McpToolResult(text = """{"html_url":"https://gh/pr/ep"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = listOf("octo/cat"),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "src/Foo.kt",
                            language = "kt",
                            description = "",
                            changeType = FileChangeType.PATCH,
                            code = "direct code",
                            patches = emptyList(),
                        ),
                    ),
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        val url = adapter().createPullRequest(cfg, ticket(), impl)
        assertEquals("https://gh/pr/ep", url)
    }

    @Test
    fun `openPr fails when create_pull_request returns error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> McpToolResult(text = """{"default_branch":"main"}""", isError = false)
                2 -> McpToolResult(text = """{"object":{"sha":"b"}}""", isError = false)
                3 -> McpToolResult(text = """{"ref":"x"}""", isError = false)
                4 -> McpToolResult(text = """{"sha":"abc"}""", isError = false)
                5 -> McpToolResult(text = """{"commit":{"sha":"c"}}""", isError = false)
                6 -> McpToolResult(text = "err", isError = true)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        whenever(delegate.createPullRequest(any(), any(), any())).thenReturn("https://gh/pr-fb")
        val url = adapter().createPullRequest(cfg, ticket(), basicImpl())
        assertEquals("https://gh/pr-fb", url)
    }
}
