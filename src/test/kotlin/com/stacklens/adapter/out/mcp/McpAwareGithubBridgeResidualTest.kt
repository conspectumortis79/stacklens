package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraCodePatch
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareGithubBridgeResidualTest {
    private val mcpRepo = mock<McpServerConfigPort>()

    private val gateway = mock<McpGatewayPort>()

    private val delegate = mock<GithubTicketBridgePort>()

    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeGithub = true,
            githubToken = "TOK",
        )

    private val queues = mutableMapOf<String, Iterator<McpToolResult>>()

    private fun adapter() =
        McpAwareGithubTicketBridgeAdapter(
            mcpRepo = mcpRepo,
            gateway = gateway,
            objectMapper = ObjectMapper(),
            delegate = delegate,
        )

    private fun route(answers: List<Pair<String, List<McpToolResult>>>) {
        queues.clear()
        answers.forEach { (tool, results) -> queues[tool] = results.iterator() }
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenAnswer { invocation ->
            val iterator = queues[invocation.getArgument<String>(1)]
            when {
                iterator?.hasNext() == true -> iterator.next()
                else -> McpToolResult("{}")
            }
        }
    }

    private fun config(branch: String?) =
        GithubRepoConfig(
            id = 1L,
            name = "n",
            owner = "octo",
            repo = "cat",
            accessToken = "TOK",
            branch = branch,
            active = true,
        )

    private val ticket =
        JiraTicketData(
            key = "P-1",
            summary = "S",
            description = "",
            comments = "",
            issueType = "Story",
            status = "Open",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "https://jira/P-1",
        )

    private fun patchImplementation(code: String) =
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
                        code = code,
                        patches = listOf(JiraCodePatch(search = "alt", replace = "neu")),
                    ),
                ),
            testFiles = emptyList(),
            databaseChanges = emptyList(),
            implementationNotes = "",
        )

    private val refAnswers =
        listOf(
            "github_get_ref" to listOf(McpToolResult("""{"object":{"sha":"base"}}""")),
            "github_create_ref" to listOf(McpToolResult("""{"ref":"refs/heads/x"}""")),
            "github_put_content" to listOf(McpToolResult("""{"commit":{"sha":"c"}}""")),
            "github_create_pull_request" to listOf(McpToolResult("""{"html_url":"https://gh/pr"}""")),
        )

    @Test
    fun `a blank config branch still asks the repository for its default branch`() {
        route(
            listOf("github_get_repo" to listOf(McpToolResult("""{"default_branch":"trunk"}"""))) + refAnswers,
        )

        val url = adapter().createPullRequest(config("   "), ticket, patchImplementation("code"))

        assertEquals("https://gh/pr", url)
    }

    @Test
    fun `an empty file sha and an undecodable body are both tolerated`() {
        route(
            listOf("github_get_content" to listOf(McpToolResult("""{"sha":"  "}"""), McpToolResult("""{"content":"AAAAA"}"""))) + refAnswers,
        )

        val url = adapter().createPullRequest(config(null), ticket, patchImplementation("code"))

        assertEquals("https://gh/pr", url)
    }

    @Test
    fun `a file sha that is no json is ignored`() {
        route(
            listOf(
                "github_get_content" to
                    listOf(
                        McpToolResult("kein json{"),
                        McpToolResult("""{"content":"YWx0"}"""),
                    ),
            ) + refAnswers,
        )

        val url = adapter().createPullRequest(config("main"), ticket, patchImplementation("code"))

        assertEquals("https://gh/pr", url)
    }

    @Test
    fun `a known file sha is sent along with the new content`() {
        route(
            listOf(
                "github_get_content" to
                    listOf(
                        McpToolResult("""{"sha":"vorerst"}"""),
                        McpToolResult("""{"content":"YWx0"}"""),
                    ),
            ) + refAnswers,
        )

        val url = adapter().createPullRequest(config("main"), ticket, patchImplementation("code"))

        assertEquals("https://gh/pr", url)
    }

    @Test
    fun `a repository listing that fails yields no files`() {
        route(listOf("github_get_content" to listOf(McpToolResult("abgelehnt", isError = true))))

        assertTrue(adapter().fetchRepoContext(config("main"))!!.files.isEmpty())
    }

    @Test
    fun `a repository tree mixes readable and empty files`() {
        route(
            listOf(
                "github_get_content" to
                    listOf(
                        McpToolResult("""[{"path":"src/A.kt","type":"file"},{"path":"","type":"file"},{"path":"leer","type":"dir"}]"""),
                        McpToolResult("""{"content":"aGVsbG8="}"""),
                        McpToolResult("""{"content":"   "}"""),
                    ),
            ),
        )

        val context = adapter().fetchRepoContext(config("main"))

        assertNotNull(context)
        assertEquals(listOf("src/A.kt"), context!!.files.map { it.path })
        assertEquals("hello", context.files.first().content)
    }
}
