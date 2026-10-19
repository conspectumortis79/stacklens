package com.stacklens.adapter.out.gitlab

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class GitlabMergeRequestApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabMergeRequestApiClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GitlabMergeRequestApiClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun directClient(): WebClient {
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(2)),
            )
        return WebClient
            .builder()
            .baseUrl(server.url("").toString().removeSuffix("/"))
            .clientConnector(connector)
            .build()
    }

    @Test
    fun `searchMrs returns matching MRs`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"iid":5,"title":"PROJ-1 Fix bug","description":"x","state":"opened","web_url":"u"}]"""),
        )
        val result = client.searchMrs(directClient(), projectId = 42L, searchTerm = "PROJ-1")
        assertEquals(1, result.size)
        assertEquals(5L, result[0].iid)
        assertEquals("PROJ-1 Fix bug", result[0].title)
    }

    @Test
    fun `searchMrs returns empty list on error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.searchMrs(directClient(), projectId = 42L, searchTerm = "x")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchMrs uses default state of all`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        client.searchMrs(directClient(), projectId = 42L, searchTerm = "x")
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("state=all"))
    }

    @Test
    fun `searchMrs respects custom state`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        client.searchMrs(directClient(), projectId = 42L, searchTerm = "x", state = "merged")
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("state=merged"))
    }

    @Test
    fun `fetchMrChanges returns file changes from the API`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                        "changes": [
                            {"new_path": "src/Foo.kt", "old_path": "src/Foo.kt", "diff": "@@ -1 +1 @@\n-old\n+new"},
                            {"new_path": "src/Bar.kt", "old_path": "src/Bar.kt", "diff": "@@ -1 +1 @@\n-old\n+new"}
                        ]
                    }""",
                ),
        )
        val result = client.fetchMrChanges(directClient(), projectId = 42L, mrIid = 5L)
        assertEquals(2, result.size)
        assertEquals("src/Foo.kt", result[0].filePath)
        assertTrue(result[0].diffExcerpt.contains("new"))
    }

    @Test
    fun `fetchMrChanges returns empty on error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.fetchMrChanges(directClient(), projectId = 42L, mrIid = 5L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchMrChanges returns empty when response body is null`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(""),
        )
        val result = client.fetchMrChanges(directClient(), projectId = 42L, mrIid = 5L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchMrChanges falls back to oldPath when newPath is blank`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes": [{"new_path": "", "old_path": "src/Deleted.kt", "diff": "@@"}]}"""),
        )
        val result = client.fetchMrChanges(directClient(), projectId = 42L, mrIid = 5L)
        assertEquals(1, result.size)
        assertEquals("src/Deleted.kt", result[0].filePath)
    }

    @Test
    fun `fetchMrChanges truncates diff excerpt to 600 chars`() {
        val longDiff = "x".repeat(1000)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes": [{"new_path": "src/Foo.kt", "old_path": "src/Foo.kt", "diff": "$longDiff"}]}"""),
        )
        val result = client.fetchMrChanges(directClient(), projectId = 42L, mrIid = 5L)
        assertEquals(1, result.size)
        assertTrue(result[0].diffExcerpt.length <= 600)
    }

    @Test
    fun `toRelatedMr builds a JiraRelatedMr with basic URL`() {
        val mr = JiraBridgeGitlabMrDto(id = 1, iid = 5, title = "Fix", description = "x", state = "opened", webUrl = "u")
        val project = JiraBridgeGitlabProjectDto(id = 42, name = "r", pathWithNamespace = "g/r")
        val result = client.toRelatedMr(mr, project, apiBase = "https://gl", changes = emptyList())
        assertEquals("https://gl/g/r/-/merge_requests/5", result.url)
        assertEquals("Fix", result.title)
        assertEquals("g/r", result.projectPath)
        assertEquals(5L, result.mrIid)
        assertEquals("opened", result.state)
    }

    @Test
    fun `toRelatedMr appends ticket-key hint when foundViaTicketKey is set`() {
        val mr = JiraBridgeGitlabMrDto(iid = 5, title = "Fix")
        val project = JiraBridgeGitlabProjectDto(pathWithNamespace = "g/r")
        val result = client.toRelatedMr(mr, project, apiBase = "https://gl", changes = emptyList(), foundViaTicketKey = "PROJ-123")
        assertTrue(result.description.contains("via ticket PROJ-123"))
    }

    @Test
    fun `toRelatedMr appends keyword hint when foundViaKeyword is set`() {
        val mr = JiraBridgeGitlabMrDto(iid = 5, title = "Fix")
        val project = JiraBridgeGitlabProjectDto(pathWithNamespace = "g/r")
        val result = client.toRelatedMr(mr, project, apiBase = "https://gl", changes = emptyList(), foundViaKeyword = "refactor")
        assertTrue(result.description.contains("via keyword \"refactor\""))
    }

    @Test
    fun `toRelatedMr passes file changes through`() {
        val mr = JiraBridgeGitlabMrDto(iid = 5)
        val project = JiraBridgeGitlabProjectDto(pathWithNamespace = "g/r")
        val changes =
            listOf(
                com.stacklens.application.port.out
                    .JiraMrFileChange("src/Foo.kt", "diff1"),
            )
        val result = client.toRelatedMr(mr, project, apiBase = "x", changes = changes)
        assertEquals(1, result.fileChanges.size)
        assertEquals("src/Foo.kt", result.fileChanges[0].filePath)
    }

    @Test
    fun `isRelevantSourceFile accepts source extensions`() {
        assertTrue(client.isRelevantSourceFile("src/Foo.kt"))
        assertTrue(client.isRelevantSourceFile("src/Foo.java"))
        assertTrue(client.isRelevantSourceFile("src/Foo.ts"))
        assertTrue(client.isRelevantSourceFile("src/Foo.py"))
        assertTrue(client.isRelevantSourceFile("src/Foo.go"))
        assertTrue(client.isRelevantSourceFile("src/Foo.php"))
    }

    @Test
    fun `isRelevantSourceFile rejects binary extensions`() {
        assertFalse(client.isRelevantSourceFile("src/image.png"))
        assertFalse(client.isRelevantSourceFile("src/data.bin"))
        assertFalse(client.isRelevantSourceFile("src/archive.zip"))
    }

    @Test
    fun `isRelevantSourceFile rejects files in ignored directories`() {
        assertFalse(client.isRelevantSourceFile("src/node_modules/foo/index.js"))
        assertFalse(client.isRelevantSourceFile("src/.git/HEAD"))
        assertFalse(client.isRelevantSourceFile("src/build/output.jar"))
        assertFalse(client.isRelevantSourceFile("src/dist/bundle.js"))
        assertFalse(client.isRelevantSourceFile("src/vendor/lib.php"))
    }

    @Test
    fun `isRelevantSourceFile rejects minified files`() {
        assertFalse(client.isRelevantSourceFile("dist/bundle.min.js"))
        assertFalse(client.isRelevantSourceFile("dist/style.min.css"))
    }

    @Test
    fun `isRelevantSourceFile is case-insensitive on extension`() {
        assertTrue(client.isRelevantSourceFile("src/Foo.KT"))
        assertTrue(client.isRelevantSourceFile("src/Foo.Java"))
    }

    @Test
    fun `isRelevantSourceFile returns false for files with no extension`() {
        assertFalse(client.isRelevantSourceFile("README"))
        assertFalse(client.isRelevantSourceFile("Makefile"))
    }

    @Test
    fun `isRelevantSourceFile accepts gradle, yaml, json, sql, html, css, sh, tf`() {
        assertTrue(client.isRelevantSourceFile("build.gradle"))
        assertTrue(client.isRelevantSourceFile("config.yaml"))
        assertTrue(client.isRelevantSourceFile("data.json"))
        assertTrue(client.isRelevantSourceFile("schema.sql"))
        assertTrue(client.isRelevantSourceFile("index.html"))
        assertTrue(client.isRelevantSourceFile("style.css"))
        assertTrue(client.isRelevantSourceFile("deploy.sh"))
        assertTrue(client.isRelevantSourceFile("infra.tf"))
    }
}
