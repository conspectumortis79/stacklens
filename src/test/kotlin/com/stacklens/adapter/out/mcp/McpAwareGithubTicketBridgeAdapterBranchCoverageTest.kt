package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareGithubTicketBridgeAdapterBranchCoverageTest {
    private val mcpRepo = mock<McpServerConfigPort>()

    private val gateway = mock<McpGatewayPort>()

    private val delegate = mock<GithubTicketBridgePort>()

    private val mapper = ObjectMapper()

    private val adapter by lazy { McpAwareGithubTicketBridgeAdapter(mcpRepo, gateway, mapper, delegate) }

    private fun repoConfig(branch: String?): GithubRepoConfig =
        GithubRepoConfig(
            id = 3L,
            name = "widgets",
            owner = "acme",
            repo = "widgets",
            branch = branch,
            accessToken = "pat",
        )

    private val trunkConfig = repoConfig("trunk")

    private val blankBranchConfig = repoConfig("   ")

    private val noBranchConfig = repoConfig(null)

    @BeforeEach
    fun routeGithub() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig())
            .thenReturn(
                McpServerConfig(
                    active = true,
                    routeGithub = true,
                    githubToken = "gh-token",
                ),
            )
    }

    private fun dispatcher(responses: Map<String, McpToolResult>) {
        whenever(gateway.callTool(any(), any(), any())).thenAnswer { call ->
            val toolName = call.arguments[1] as String
            responses[toolName] ?: throw IllegalStateException("no stub for $toolName")
        }
    }

    private val ticket =
        JiraTicketData(
            key = "PRJ-9",
            summary = "Add widget export",
            description = "d",
            comments = "c",
            issueType = "Story",
            status = "In Progress",
            priority = "High",
            labels = emptyList(),
            components = emptyList(),
            url = "https://jira/browse/PRJ-9",
        )

    private val implementation =
        JiraCodeImplementation(
            identifiedRepositories = listOf("acme/widgets"),
            identifiedDatabases = emptyList(),
            files = listOf(codeFile("/src/main/kotlin/A.kt")),
            testFiles = listOf(codeFile("src/test/kotlin/ATest.kt", FileChangeType.PATCH)),
            systemTestFiles = emptyList(),
            databaseChanges = emptyList(),
            implementationNotes = "notes",
        )

    private fun codeFile(
        path: String,
        changeType: FileChangeType = FileChangeType.CREATE,
    ): JiraCodeFile =
        JiraCodeFile(
            repository = "acme/widgets",
            path = path,
            language = "kotlin",
            description = "d",
            changeType = changeType,
            code = "val a = 1",
        )

    @Test
    fun `repository context resolves the default branch when none is configured`() {
        dispatcher(
            mapOf(
                "github_get_repo" to McpToolResult("""{"default_branch":"trunk"}"""),
                "github_list_files" to McpToolResult("unavailable", isError = true),
            ),
        )

        adapter.fetchRepoContext(noBranchConfig)

        verify(gateway).callTool(any(), eq("github_get_repo"), any())
        verify(delegate).fetchRepoContext(noBranchConfig)
    }

    @Test
    fun `repository context keeps a configured branch without querying the repository`() {
        dispatcher(mapOf("github_list_files" to McpToolResult("unavailable", isError = true)))

        adapter.fetchRepoContext(trunkConfig)

        verify(gateway, never()).callTool(any(), eq("github_get_repo"), any())
        verify(delegate).fetchRepoContext(trunkConfig)
    }

    @Test
    fun `repository context treats a blank branch as missing and survives broken answers`() {
        dispatcher(
            mapOf(
                "github_get_repo" to McpToolResult("not json"),
                "github_list_files" to McpToolResult("unavailable", isError = true),
            ),
        )

        adapter.fetchRepoContext(blankBranchConfig)

        verify(gateway).callTool(any(), eq("github_get_repo"), any())
        verify(delegate).fetchRepoContext(blankBranchConfig)
    }

    @Test
    fun `repository context falls back when the repository lookup errors`() {
        dispatcher(mapOf("github_get_repo" to McpToolResult("not found", isError = true)))

        adapter.fetchRepoContext(noBranchConfig)

        verify(delegate).fetchRepoContext(noBranchConfig)
    }

    @Test
    fun `pull requests are opened through the gateway and answer the html url`() {
        dispatcher(
            mapOf(
                "github_get_ref" to McpToolResult("""{"object":{"sha":"base-sha"}}"""),
                "github_create_ref" to McpToolResult("""{"ok":true}"""),
                "github_get_content" to McpToolResult("""{"sha":"existing"}"""),
                "github_put_content" to McpToolResult("""{"ok":true}"""),
                "github_create_pull_request" to McpToolResult("""{"html_url":"https://github.com/acme/widgets/pull/4"}"""),
            ),
        )
        assertEquals("https://github.com/acme/widgets/pull/4", adapter.createPullRequest(trunkConfig, ticket, implementation))
    }

    @Test
    fun `pull request steps that report errors fall back to the direct adapter`() {
        whenever(delegate.createPullRequest(trunkConfig, ticket, implementation)).thenReturn("native")

        dispatcher(
            mapOf(
                "github_get_ref" to McpToolResult("""{"object":{"sha":"base-sha"}}"""),
                "github_create_ref" to McpToolResult("""{"ok":true}"""),
                "github_get_content" to McpToolResult("gone", isError = true),
                "github_put_content" to McpToolResult("quota", isError = true),
            ),
        )
        assertEquals("native", adapter.createPullRequest(trunkConfig, ticket, implementation))

        dispatcher(
            mapOf(
                "github_get_ref" to McpToolResult("broken body"),
                "github_create_ref" to McpToolResult("""{"ok":true}"""),
                "github_get_content" to McpToolResult("not json"),
                "github_put_content" to McpToolResult("""{"ok":true}"""),
            ),
        )
        assertEquals("native", adapter.createPullRequest(trunkConfig, ticket, implementation))

        dispatcher(
            mapOf(
                "github_get_ref" to McpToolResult("""{"object":{"sha":"base-sha"}}"""),
                "github_create_ref" to McpToolResult("""{"ok":true}"""),
                "github_get_content" to McpToolResult("""{"sha":"  "}"""),
                "github_put_content" to McpToolResult("""{"ok":true}"""),
                "github_create_pull_request" to McpToolResult("no json"),
            ),
        )
        assertEquals("native", adapter.createPullRequest(trunkConfig, ticket, implementation))

        dispatcher(
            mapOf(
                "github_get_ref" to McpToolResult("""{"object":{"sha":"base-sha"}}"""),
                "github_create_ref" to McpToolResult("""{"ok":true}"""),
                "github_get_content" to McpToolResult("""{"content":"not-base64!!!"}"""),
                "github_put_content" to McpToolResult("""{"ok":true}"""),
                "github_create_pull_request" to McpToolResult("""{"html_url":"  "}"""),
            ),
        )
        assertEquals("native", adapter.createPullRequest(trunkConfig, ticket, implementation))
    }

    @Test
    fun `without github routing every call goes to the direct adapter`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.createPullRequest(trunkConfig, ticket, implementation)).thenReturn("native")

        adapter.fetchRepoContext(trunkConfig)
        assertEquals("native", adapter.createPullRequest(trunkConfig, ticket, implementation))
        adapter.detectGithubRemote(
            java.nio.file.Path
                .of("/tmp"),
        )

        verify(delegate).fetchRepoContext(trunkConfig)
        verify(gateway, never()).callTool(any(), any(), any())
    }
}
