package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.GitlabTicketBridgePort
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraMonorepoIdentification
import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraRelatedMr
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.GitlabConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import java.nio.file.Path
import java.util.function.Function

class McpAwareGitlabTicketBridgeAdapterTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val delegate: GitlabTicketBridgePort = mock()
    private val routed: GitlabTicketBridgePort = mock()
    private val portFactory: Function<WebClient.Builder, GitlabTicketBridgePort> = mock()
    private val mapper = ObjectMapper()

    private lateinit var adapter: McpAwareGitlabTicketBridgeAdapter

    @BeforeEach
    fun setUp() {
        whenever(portFactory.apply(any())).thenReturn(routed)
        adapter =
            McpAwareGitlabTicketBridgeAdapter(
                mcpRepo,
                gateway,
                mapper,
                delegate,
                portFactory,
            )
    }

    private fun cfg() =
        GitlabConfig(
            name = "test",
            baseUrl = "https://gitlab.example.com",
            accessToken = "TOK",
        )

    @Test
    fun `constructor exercises mcpRoutedWebClientBuilder call`() {
        val newAdapter =
            McpAwareGitlabTicketBridgeAdapter(
                mcpRepo,
                gateway,
                mapper,
                delegate,
                portFactory,
            )
        assertNotNull(newAdapter)
    }

    @Test
    fun `constructor with explicit timeouts is invokable`() {
        val newAdapter =
            McpAwareGitlabTicketBridgeAdapter(
                mcpRepo,
                gateway,
                mapper,
                delegate,
                portFactory,
                connectTimeoutMs = 12_000,
                readTimeoutMs = 24_000,
            )
        assertNotNull(newAdapter)
    }

    @Test
    fun `fetchRelatedMrs delegates to routed`() {
        val expected = listOf<JiraRelatedMr>()
        whenever(
            routed.fetchRelatedMrs(
                any(),
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(expected)
        val problem =
            JiraProblemAnalysis(
                problemDescription = "x",
                rootCause = "r",
                affectedComponents = emptyList(),
                searchKeywords = emptyList(),
                errorMessages = emptyList(),
            )
        val ticket =
            JiraTicketData(
                key = "T-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "https://jira/T-1",
            )
        val result =
            adapter.fetchRelatedMrs(
                gitlabConfigs = listOf(cfg()),
                similarTickets = emptyList(),
                problem = problem,
                originalTicket = ticket,
                selectedProjects = emptyMap(),
            )
        assertSame(expected, result)
        verify(routed).fetchRelatedMrs(
            any(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `findProjectById delegates to routed and returns result`() {
        val info =
            GitlabProjectInfo(
                id = 1,
                name = "n",
                pathWithNamespace = "grp/n",
                defaultBranch = "main",
                httpUrlToRepo = "https://gitlab/grp/n.git",
            )
        whenever(routed.findProjectById(any(), any())).thenReturn(info)
        val result = adapter.findProjectById(cfg(), 1L)
        assertSame(info, result)
        verify(routed).findProjectById(any(), eq(1L))
    }

    @Test
    fun `findProjectById returns null when routed returns null`() {
        whenever(routed.findProjectById(any(), any())).thenReturn(null)
        val result = adapter.findProjectById(cfg(), 99L)
        assertNull(result)
    }

    @Test
    fun `fetchRepoContextForCodeGen delegates to routed`() {
        val expected = listOf<JiraRepoContext>()
        whenever(
            routed.fetchRepoContextForCodeGen(
                any(),
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(expected)
        val problem =
            JiraProblemAnalysis(
                problemDescription = "x",
                rootCause = "r",
                affectedComponents = emptyList(),
                searchKeywords = emptyList(),
                errorMessages = emptyList(),
            )
        val ticket =
            JiraTicketData(
                key = "T-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "https://jira/T-1",
            )
        val result =
            adapter.fetchRepoContextForCodeGen(
                gitlabConfigs = listOf(cfg()),
                ticket = ticket,
                problem = problem,
                relatedMrs = emptyList(),
                selectedProjects = emptyMap(),
            )
        assertSame(expected, result)
        verify(routed).fetchRepoContextForCodeGen(any(), any(), any(), any(), any())
    }

    @Test
    fun `createMergeRequest delegates to routed and returns url`() {
        val ticket =
            JiraTicketData(
                key = "T-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "https://jira/T-1",
            )
        val codeImpl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files = emptyList(),
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "n",
            )
        whenever(
            routed.createMergeRequest(
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("https://gl/mr/1")
        val result =
            adapter.createMergeRequest(
                config = cfg(),
                repoName = "repo",
                ticket = ticket,
                codeImpl = codeImpl,
                gitlabProjectId = 1L,
                monorepoInfo = null,
            )
        assertEquals("https://gl/mr/1", result)
        verify(routed).createMergeRequest(any(), any(), any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `createMergeRequest with monorepoInfo delegates to routed`() {
        val ticket =
            JiraTicketData(
                key = "T-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "https://jira/T-1",
            )
        val codeImpl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files = emptyList(),
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "n",
            )
        val mono =
            JiraMonorepoIdentification(
                identifiedApplication = "a",
                relativeRootPath = "p",
                reasoning = "r",
                confidence = "HIGH",
                isMonorepo = true,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "",
                testCommand = "",
            )
        whenever(
            routed.createMergeRequest(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn("https://gl/mr/1")
        val result =
            adapter.createMergeRequest(
                config = cfg(),
                repoName = "repo",
                ticket = ticket,
                codeImpl = codeImpl,
                gitlabProjectId = 1L,
                monorepoInfo = mono,
            )
        assertEquals("https://gl/mr/1", result)
        verify(routed).createMergeRequest(any(), eq("repo"), any(), any(), eq(1L), eq(mono))
    }

    @Test
    fun `commitDirectly delegates to routed and returns result`() {
        val ticket =
            JiraTicketData(
                key = "T-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "https://jira/T-1",
            )
        val codeImpl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files = emptyList(),
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "n",
            )
        whenever(
            routed.commitDirectly(
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("https://gl/commit/1")
        val result =
            adapter.commitDirectly(
                config = cfg(),
                repoName = "repo",
                ticket = ticket,
                codeImpl = codeImpl,
                gitlabProjectId = null,
                monorepoInfo = null,
            )
        assertEquals("https://gl/commit/1", result)
        verify(routed).commitDirectly(any(), any(), any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `detectGitlabRemote always uses delegate not routed`() {
        val config = cfg()
        whenever(delegate.detectGitlabRemote(any(), any())).thenReturn(config to 1L)
        val result = adapter.detectGitlabRemote(Path.of("/tmp/repo"), listOf(config))
        assertEquals(config to 1L, result)
        verify(delegate).detectGitlabRemote(any(), any())
        verify(routed, org.mockito.kotlin.never()).detectGitlabRemote(any(), any())
    }

    @Test
    fun `detectGitlabRemote returns null when delegate returns null`() {
        whenever(delegate.detectGitlabRemote(any(), any())).thenReturn(null)
        val result = adapter.detectGitlabRemote(Path.of("/tmp/repo"), listOf(cfg()))
        assertNull(result)
    }

    @Test
    fun `getGitRemotePathWithNamespace always uses delegate not routed`() {
        whenever(delegate.getGitRemotePathWithNamespace(any())).thenReturn("grp/repo")
        val result = adapter.getGitRemotePathWithNamespace(Path.of("/tmp/repo"))
        assertEquals("grp/repo", result)
        verify(delegate).getGitRemotePathWithNamespace(any())
        verify(routed, org.mockito.kotlin.never()).getGitRemotePathWithNamespace(any())
    }

    @Test
    fun `getGitRemotePathWithNamespace returns null when delegate returns null`() {
        whenever(delegate.getGitRemotePathWithNamespace(any())).thenReturn(null)
        val result = adapter.getGitRemotePathWithNamespace(Path.of("/tmp/repo"))
        assertNull(result)
    }
}
