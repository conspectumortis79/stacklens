package com.stacklens.adapter.out.gitlab

import com.stacklens.adapter.out.gitlab.PipelineSearchAdapter.GitlabBlobResult
import com.stacklens.adapter.out.gitlab.PipelineSearchAdapter.GitlabProject
import com.stacklens.adapter.out.gitlab.PipelineSearchAdapter.ParsedPipeline
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PipelineSearchAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: PipelineSearchAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter =
            PipelineSearchAdapter(
                connectTimeoutMs = 5000,
                readTimeoutMs = 5000,
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig(): GitlabConfig = GitlabConfig(name = "g", baseUrl = server.url("").toString().removeSuffix("/"), accessToken = "TOK")

    @Test
    fun `parsePipelineSnippet extracts job and stage names from sample`() {
        val snippet =
            """
            build:
              stage: test
              script: ./gradlew test
            """.trimIndent()
        val result = adapter.parsePipelineSnippet(snippet)
        assertEquals("build", result.jobName)
        assertEquals("test", result.stageName)
    }

    @Test
    fun `parsePipelineSnippet returns nulls for unparseable text`() {
        val result = adapter.parsePipelineSnippet("just plain text without keywords")
        assertEquals(null, result.jobName)
        assertEquals(null, result.stageName)
    }

    @Test
    fun `parsePipelineSnippet handles empty string`() {
        val result = adapter.parsePipelineSnippet("")
        assertEquals(null, result.jobName)
        assertEquals(null, result.stageName)
    }

    @Test
    fun `parsePipelineSnippet returns PIPELINE_JOB for build script`() {
        val result = adapter.parsePipelineSnippet("script: ./build.sh")
        assertEquals(ResultType.PIPELINE_JOB, result.resultType)
    }

    @Test
    fun `parsePipelineSnippet returns PIPELINE_SCRIPT for shell-like snippets`() {
        val result = adapter.parsePipelineSnippet("npm install")
        assertEquals(ResultType.PIPELINE_SCRIPT, result.resultType)
    }

    @Test
    fun `parsePipelineSnippet handles GitHub Actions workflow`() {
        val snippet =
            """
            name: CI
            on: [push]
            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@v3
            """.trimIndent()
        val result = adapter.parsePipelineSnippet(snippet)
        assertEquals(ResultType.PIPELINE_JOB, result.resultType)
    }

    @Test
    fun `ParsedPipeline data class holds values`() {
        val p = ParsedPipeline(jobName = "j", stageName = "s", resultType = ResultType.PIPELINE_JOB)
        assertEquals("j", p.jobName)
        assertEquals("s", p.stageName)
        assertEquals(ResultType.PIPELINE_JOB, p.resultType)
    }

    @Test
    fun `GitlabProject data class holds values`() {
        val p = GitlabProject(id = 7L, name = "r", pathWithNamespace = "g/r", defaultBranch = "main")
        assertEquals(7L, p.id)
        assertEquals("r", p.name)
        assertEquals("g/r", p.pathWithNamespace)
        assertEquals("main", p.defaultBranch)
    }

    @Test
    fun `GitlabBlobResult data class holds values`() {
        val b = GitlabBlobResult(projectId = 1, path = "p", ref = "main", startline = 10, data = "d")
        assertEquals(1, b.projectId)
        assertEquals("p", b.path)
        assertEquals("main", b.ref)
        assertEquals(10, b.startline)
        assertEquals("d", b.data)
    }

    @Test
    fun `searchPipelines returns empty when no projects and no blob hits`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val results = adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchPipelines returns blob hits from CI files`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"project_id":1,"path":".gitlab-ci.yml","ref":"main","startline":3,"data":"build:\\n  stage: test\\n  script: ./gradlew test"},
                        {"project_id":1,"path":"README.md","ref":"main","startline":1,"data":"plain text"}
                    ]""",
                ),
        )
        val results = adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(1, results.size)
        assertEquals(".gitlab-ci.yml", results[0].filePath)
        assertEquals(ResultType.PIPELINE_JOB, results[0].resultType)
    }

    @Test
    fun `searchPipelines returns blob hits for github workflows`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"project_id":1,"path":".github/workflows/ci.yml","ref":"main","startline":5,"data":"runs-on: ubuntu-latest"}
                    ]""",
                ),
        )
        val results = adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(1, results.size)
        assertTrue(results[0].filePath.orEmpty().contains(".github/workflows"))
    }

    @Test
    fun `searchPipelines handles HTTP error gracefully`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val results = adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchPipelines fetches project CI files for each project`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"id":1,"name":"repo","path_with_namespace":"g/repo","default_branch":"main"}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":"x","name":".gitlab-ci.yml","path":".gitlab-ci.yml","type":"blob"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"content":"$(echo 'aGVsbG8gd29ybGQ=' | base64)"}"""),
        )
        val results = adapter.searchPipelines(gitlabConfig(), listOf(1L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertNotNull(results)
    }
}
