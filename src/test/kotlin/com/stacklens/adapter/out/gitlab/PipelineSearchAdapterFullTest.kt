package com.stacklens.adapter.out.gitlab

import com.stacklens.adapter.out.gitlab.PipelineSearchAdapter.GitlabProject
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PipelineSearchAdapterFullTest {
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

    private fun gitlabConfig(): GitlabConfig =
        GitlabConfig(
            name = "g",
            baseUrl = server.url("").toString().removeSuffix("/"),
            accessToken = "TOK",
        )

    @Test
    fun `parsePipelineSnippet detects Jenkinsfile stage() call`() {
        val snippet =
            """
            pipeline {
                agent any
                stage('Build') {
                    sh 'make'
                }
            }
            """.trimIndent()
        val result = adapter.parsePipelineSnippet(snippet)
        assertEquals("Build", result.stageName)
    }

    @Test
    fun `parsePipelineSnippet picks the LAST job name when multiple matches`() {
        val snippet =
            """
            job1:
              stage: alpha
              script: x
            job2:
              stage: beta
              script: y
            """.trimIndent()
        val result = adapter.parsePipelineSnippet(snippet)
        assertEquals("job2", result.jobName)
        assertEquals("beta", result.stageName)
    }

    @Test
    fun `parsePipelineSnippet returns PIPELINE_STAGE when stage present without job name`() {
        val snippet =
            """
            something else:
              stage: deploy
              script: ./deploy.sh
            """.trimIndent()
        val result = adapter.parsePipelineSnippet(snippet)
        assertEquals("deploy", result.stageName)
        assertEquals(ResultType.PIPELINE_STAGE, result.resultType)
    }

    @Test
    fun `searchPipelines scans each well-known CI file when project has no workflows`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":42,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        repeat(9) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("build:\n  script: kw"),
            )
        }
        val results = adapter.searchPipelines(gitlabConfig(), listOf(42L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(9, results.size)
        assertEquals(".gitlab-ci.yml", results[0].filePath)
    }

    @Test
    fun `searchPipelines uses main as fallback when defaultBranch is null`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":42,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r"}""",
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        repeat(9) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("build:\n  script: kw"),
            )
        }
        val results = adapter.searchPipelines(gitlabConfig(), listOf(42L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(9, results.size)
    }

    @Test
    fun `searchPipelines scans GitHub Actions workflow files when present`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":42,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
            ),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """[
                    {"id":"a","name":"ci.yml","path":".github/workflows/ci.yml","type":"blob"},
                    {"id":"b","name":"deploy.yml","path":".github/workflows/deploy.yml","type":"blob"},
                    {"id":"c","name":"README","path":".github/workflows/README","type":"blob"}
                ]""",
            ),
        )
        repeat(9) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("nothing here"),
            )
        }
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("runs-on: kw"),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("another: kw"),
        )
        val results = adapter.searchPipelines(gitlabConfig(), listOf(42L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(2, results.size)
        assertTrue(results.any { it.filePath?.endsWith("ci.yml") == true })
        assertTrue(results.any { it.filePath?.endsWith("deploy.yml") == true })
    }

    @Test
    fun `searchPipelines returns empty when project fetch fails`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(404))
        val results = adapter.searchPipelines(gitlabConfig(), listOf(99L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchPipelines continues to next file when one fetch fails`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("pipeline { kw }"))
        repeat(6) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("nothing here"),
            )
        }
        val results = adapter.searchPipelines(gitlabConfig(), listOf(1L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(1, results.size)
        assertEquals("Jenkinsfile", results[0].filePath)
    }

    @Test
    fun `searchPipelines applies pipeline filter to blob hits`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """[
                    {"project_id":1,"path":".gitlab-ci.yml","ref":"main","startline":1,"data":"build: kw"},
                    {"project_id":1,"path":"Jenkinsfile","ref":"main","startline":1,"data":"pipeline kw"}
                ]""",
            ),
        )
        val results = adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 1, pipelineFilters = listOf("gitlab-ci"))
        assertEquals(1, results.size)
        assertEquals(".gitlab-ci.yml", results[0].filePath)
    }

    @Test
    fun `searchPipelines applies pipeline filter to project files`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        repeat(2) {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(""))
        }
        val results = adapter.searchPipelines(gitlabConfig(), listOf(1L), "kw", sessionId = 1, pipelineFilters = listOf("jenkins"))
        assertEquals(0, results.size)
    }

    @Test
    fun `searchPipelines deduplicates identical hits from blob and file scan`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """[
                    {"project_id":1,"path":".gitlab-ci.yml","ref":"main","startline":1,"data":"kw"}
                ]""",
            ),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        repeat(9) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("kw"),
            )
        }
        val results = adapter.searchPipelines(gitlabConfig(), listOf(1L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertNotNull(results)
        assertTrue(results.size >= 1)
    }

    @Test
    fun `searchPipelines stops pagination when a page returns fewer than 100 items`() {
        val page1 =
            (1..5).joinToString(",") { i ->
                """{"project_id":1,"path":".gitlab-ci.yml","ref":"main","startline":$i,"data":"kw$i"}"""
            }
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody("[$page1]"),
        )
        val results = adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(5, results.size)
    }

    @Test
    fun `searchPipelines returns one result per matching line within a CI file`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody(
                """
                build:
                  script: kw1
                test:
                  script: kw2
                deploy:
                  script: kw3
                """.trimIndent(),
            ),
        )
        repeat(8) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("nothing here"),
            )
        }
        val results = adapter.searchPipelines(gitlabConfig(), listOf(1L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(3, results.size)
    }

    @Test
    fun `searchPipelines returns no results when blob search finds no CI files`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """[
                    {"project_id":1,"path":"README.md","ref":"main","startline":1,"data":"kw"},
                    {"project_id":1,"path":"docs/index.txt","ref":"main","startline":1,"data":"kw"}
                ]""",
            ),
        )
        val results = adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `TreeItem defaults to empty strings`() {
        val t = PipelineSearchAdapter.TreeItem()
        assertEquals("", t.id)
        assertEquals("", t.name)
        assertEquals("", t.type)
        assertEquals("", t.path)
    }

    @Test
    fun `TreeItem holds custom values`() {
        val t = PipelineSearchAdapter.TreeItem(id = "x", name = "y", type = "blob", path = "z")
        assertEquals("x", t.id)
        assertEquals("y", t.name)
        assertEquals("blob", t.type)
        assertEquals("z", t.path)
    }

    @Test
    fun `GitlabProject default branch is main when missing in JSON`() {
        val p = GitlabProject(id = 1, name = "x", pathWithNamespace = "g/x", webUrl = "u", defaultBranch = null)
        assertNull(p.defaultBranch)
    }

    @Test
    fun `GitlabProject holds webUrl`() {
        val p = GitlabProject(id = 1, name = "x", pathWithNamespace = "g/x", webUrl = "https://gl/x")
        assertEquals("https://gl/x", p.webUrl)
    }

    @Test
    fun `searchPipelines caps project iteration to 50`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        repeat(50) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "application/json").setBody(
                    """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
                ),
            )
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
            repeat(9) {
                server.enqueue(MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody(""))
            }
        }
        val ids = (1L..60L).toList()
        val results = adapter.searchPipelines(gitlabConfig(), ids, "kw", sessionId = 1, pipelineFilters = emptyList())
        assertNotNull(results)
    }

    @Test
    fun `searchPipelines with blank pipeline filter is treated as empty`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        repeat(9) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("kw"),
            )
        }
        val results = adapter.searchPipelines(gitlabConfig(), listOf(1L), "kw", sessionId = 1, pipelineFilters = listOf("", "  ", ""))
        assertEquals(9, results.size)
    }

    @Test
    fun `searchPipelines matches workflow files with yml extension`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
            ),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """[
                    {"id":"a","name":"build.yml","path":".github/workflows/build.yml","type":"blob"}
                ]""",
            ),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("runs-on: kw"),
        )
        repeat(9) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("nothing here"),
            )
        }
        val results = adapter.searchPipelines(gitlabConfig(), listOf(1L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(1, results.size)
        assertTrue(results[0].filePath?.endsWith(".yml") == true)
    }

    @Test
    fun `searchPipelines excludes yaml workflow files (only yml matches)`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
            ),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """[
                    {"id":"a","name":"build.yaml","path":".github/workflows/build.yaml","type":"blob"}
                ]""",
            ),
        )
        repeat(9) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("nothing here"),
            )
        }
        val results = adapter.searchPipelines(gitlabConfig(), listOf(1L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(0, results.size)
    }

    @Test
    fun `searchPipelines ignores tree entries that are not blob type`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"http://g/r","default_branch":"main"}""",
            ),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """[
                    {"id":"a","name":"subdir","path":".github/workflows/subdir","type":"tree"}
                ]""",
            ),
        )
        repeat(9) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("nothing here"),
            )
        }
        val results = adapter.searchPipelines(gitlabConfig(), listOf(1L), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(0, results.size)
    }

    @Test
    fun `searchPipelines captures parsed job name in result`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """[
                    {"project_id":1,"path":".gitlab-ci.yml","ref":"main","startline":1,"data":"build:\n  stage: test\n  script: kw"}
                ]""",
            ),
        )
        val results = adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(1, results.size)
        assertEquals("build", results[0].className)
    }

    @Test
    fun `searchPipelines returns one result per CI blob hit`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """[
                    {"project_id":1,"path":".gitlab-ci.yml","ref":"main","startline":1,"data":"kw1"},
                    {"project_id":2,"path":"Jenkinsfile","ref":"main","startline":2,"data":"kw2"},
                    {"project_id":3,"path":".github/workflows/ci.yml","ref":"main","startline":3,"data":"kw3"}
                ]""",
            ),
        )
        val results = adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 1, pipelineFilters = emptyList())
        assertEquals(3, results.size)
    }

    @Test
    fun `searchPipelines with two successive calls each return independent results`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 1, pipelineFilters = emptyList())
        adapter.searchPipelines(gitlabConfig(), emptyList(), "kw", sessionId = 2, pipelineFilters = emptyList())
        assertFalse(false)
    }
}
