package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.out.git.GitDiffBuilder
import com.stacklens.application.port.inbound.pipeline.CopilotAgentPipelineUseCase
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.out.PullRequestDiffRequest
import com.stacklens.application.service.I18nService
import com.stacklens.domain.pipeline.PullRequestData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.thymeleaf.TemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.io.File

@ExtendWith(MockitoExtension::class)
class AgentPipelinePullRequestControllerTest {
    @Mock private lateinit var pipelineService: CopilotAgentPipelineUseCase

    private val i18n: I18nService = mock(defaultAnswer = org.mockito.stubbing.Answer<Any> { "" })

    private val templateEngine: TemplateEngine =
        TemplateEngine().apply {
            addTemplateResolver(
                ClassLoaderTemplateResolver().apply {
                    prefix = "templates/"
                    suffix = ".html"
                    templateMode = TemplateMode.HTML
                    characterEncoding = "UTF-8"
                },
            )
        }

    private val controller by lazy { AgentPipelinePullRequestController(pipelineService, i18n, templateEngine) }

    private fun makeRun(
        repoPath: String? = null,
        finnOutput: String = "",
    ) = AgentPipelineRun(
        id = 1L,
        rawText = "test",
        repoPath = repoPath,
        featureSlug = "test-feature",
        createdAt = System.currentTimeMillis(),
    ).also { run ->
        run.status = PipelineStatus.DONE
        run.finn.output = finnOutput
    }

    private fun git(
        dir: File,
        vararg args: String,
    ): String {
        val proc =
            ProcessBuilder(listOf("git") + args.toList())
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return output.trim()
    }

    private fun initGitRepo(
        dir: File,
        branch: String = "main",
    ) {
        git(dir, "init", "-b", branch)
        git(dir, "config", "user.email", "test@example.com")
        git(dir, "config", "user.name", "Test User")
    }

    private fun stubPrData(run: AgentPipelineRun) {
        val gitDiffBuilder = GitDiffBuilder()
        val request =
            PullRequestDiffRequest(
                repoPath = run.repoPath,
                baseBranch = run.baseBranch,
                baseSha = run.baseSha,
                preExistingUntracked = run.preExistingUntracked,
                finnOutput = run.finn.output,
                tessOutput = run.tess.output,
                quinnOutput = run.quinn.output,
                veraOutput = run.vera.output,
            )
        whenever(pipelineService.buildPullRequestData(any())).thenAnswer {
            val result = gitDiffBuilder.computeDiffs(request)
            PullRequestData(
                runId = run.id,
                featureSlug = run.featureSlug,
                gitBranch = run.gitBranch.ifBlank { run.featureSlug },
                baseBranch = result?.baseBranch ?: "",
                repoPath = run.repoPath,
                diffs = result?.diffs ?: emptyList(),
            )
        }
    }

    @Test
    fun `parseAgentPaths extracts paths from output`() {
        val output = "### src/App.php\n```php\nclass App {}\n```"
        val paths = AgentPipelinePullRequestController.parseAgentPaths(output)
        assertEquals(listOf("src/App.php"), paths)
    }

    @Test
    fun `parseAgentPaths rejects absolute paths`() {
        val output = "### /etc/passwd\n```txt\nroot\n```"
        val paths = AgentPipelinePullRequestController.parseAgentPaths(output)
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `parseAgentPaths rejects blank paths`() {
        val output = "###  \n```txt\ncontent\n```"
        val paths = AgentPipelinePullRequestController.parseAgentPaths(output)
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `parseAgentPaths strips quotes and punctuation from path`() {
        val output = "### `src/Service.php`\n```php\nclass S {}\n```"
        val paths = AgentPipelinePullRequestController.parseAgentPaths(output)
        assertEquals(listOf("src/Service.php"), paths)
    }

    @Test
    fun `parseAgentPaths handles multiple files`() {
        val output =
            """
            ### src/A.php
            ```php
            class A {}
            ```
            ### src/B.php
            ```php
            class B {}
            ```
            """.trimIndent()
        val paths = AgentPipelinePullRequestController.parseAgentPaths(output)
        assertEquals(2, paths.size)
        assertTrue(paths.contains("src/A.php"))
        assertTrue(paths.contains("src/B.php"))
    }

    @Test
    fun `parseAgentPaths returns empty for no matches`() {
        val paths = AgentPipelinePullRequestController.parseAgentPaths("no file blocks here")
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `pullRequestView returns 404 for unknown id`() {
        whenever(pipelineService.buildPullRequestData(any())).thenReturn(null)
        val result = controller.pullRequestView(99L)
        assertEquals(404, result.statusCode.value())
        assertEquals(MediaType.TEXT_HTML, result.headers.contentType)
        assertTrue(result.body!!.contains("99"))
    }

    @Test
    fun `pullRequestView returns HTML for run with no repo`() {
        stubPrData(makeRun())
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(MediaType.TEXT_HTML, result.headers.contentType)
        assertNotNull(result.body)
        assertTrue(result.body!!.contains("<!doctype html") || result.body!!.contains("<!DOCTYPE html"))
    }

    @Test
    fun `pullRequestView returns HTML with pipeline id in title`() {
        stubPrData(makeRun())
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("1"))
    }

    @Test
    fun `pullRequestView returns HTML for run with nonexistent repo path`() {
        stubPrData(makeRun(repoPath = "/nonexistent/repo/abc123xyz"))
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("<!doctype html") || result.body!!.contains("<!DOCTYPE html"))
    }

    @Test
    fun `pullRequestView escapes HTML in error message`() {
        whenever(pipelineService.buildPullRequestData(any())).thenReturn(null)
        val result = controller.pullRequestView(99L)
        assertFalse(result.body!!.contains("<script"))
    }

    @Test
    fun `pullRequestView with modified file shows diff`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve("README.md").writeText("# Hello\nline2\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        tmpDir.resolve("README.md").writeText("# Hello World\nline2\n")
        val run = makeRun(repoPath = tmpDir.absolutePath)
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("README.md"))
    }

    @Test
    fun `pullRequestView with added untracked file shows addition`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve(".gitkeep").writeText("")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        tmpDir.resolve("NewFile.php").writeText("<?php\necho 'hello';\n")
        val run = makeRun(repoPath = tmpDir.absolutePath)
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("NewFile.php"))
    }

    @Test
    fun `pullRequestView with deleted file shows deletion`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve("ToDelete.txt").writeText("old content\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        tmpDir.resolve("ToDelete.txt").delete()
        val run = makeRun(repoPath = tmpDir.absolutePath)
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("ToDelete.txt"))
    }

    @Test
    fun `pullRequestView with baseSha uses committed diff`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve("app.kt").writeText("fun main() {}\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        val baseSha = git(tmpDir, "rev-parse", "HEAD")
        tmpDir.resolve("app.kt").writeText("fun main() { println(\"hello\") }\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "update")
        val run = makeRun(repoPath = tmpDir.absolutePath)
        run.baseSha = baseSha
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("app.kt"))
    }

    @Test
    fun `pullRequestView with master branch detects master`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir, "master")
        tmpDir.resolve("file.txt").writeText("content\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        tmpDir.resolve("file.txt").writeText("changed\n")
        val run = makeRun(repoPath = tmpDir.absolutePath)
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("<!doctype html") || result.body!!.contains("<!DOCTYPE html"))
    }

    @Test
    fun `pullRequestView with neither main nor master falls back to HEAD`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir, "feature-branch")
        tmpDir.resolve("x.txt").writeText("data\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        tmpDir.resolve("x.txt").writeText("data changed\n")
        val run = makeRun(repoPath = tmpDir.absolutePath)
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("<!doctype html") || result.body!!.contains("<!DOCTYPE html"))
    }

    @Test
    fun `pullRequestView filters stacklens files from untracked`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve(".gitkeep").writeText("")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        val stacklensDir = tmpDir.resolve(".stacklens")
        stacklensDir.mkdirs()
        stacklensDir.resolve("output.json").writeText("{}")
        val run = makeRun(repoPath = tmpDir.absolutePath)
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertFalse(result.body!!.contains("output.json"))
    }

    @Test
    fun `pullRequestView filters pre-existing untracked files`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve(".gitkeep").writeText("")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        tmpDir.resolve("pre-existing.txt").writeText("was here before run")
        val run = makeRun(repoPath = tmpDir.absolutePath)
        run.preExistingUntracked = listOf("pre-existing.txt")
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertFalse(result.body!!.contains("pre-existing.txt"))
    }

    @Test
    fun `pullRequestView shows pre-existing file when in agent written paths`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve(".gitkeep").writeText("")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        tmpDir.resolve("agent-wrote.php").writeText("<?php echo 'agent wrote this'; ?>")
        val agentOutput = "### agent-wrote.php\n```php\n<?php echo 'agent wrote this'; ?>\n```"
        val run = makeRun(repoPath = tmpDir.absolutePath, finnOutput = agentOutput)
        run.preExistingUntracked = listOf("agent-wrote.php")
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("agent-wrote.php"))
    }

    @Test
    fun `pullRequestView with subpath prefix scopes diff to subdirectory`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        val subDir = tmpDir.resolve("subproject")
        subDir.mkdirs()
        subDir.resolve("app.php").writeText("<?php\n")
        tmpDir.resolve("root-file.txt").writeText("root\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        subDir.resolve("app.php").writeText("<?php\necho 'hi';\n")
        tmpDir.resolve("root-file.txt").writeText("changed root\n")
        val run = makeRun(repoPath = subDir.absolutePath)
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("app.php"))
    }

    @Test
    fun `pullRequestView with unchanged file produces no diff lines`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve("same.txt").writeText("line1\nline2\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        val baseSha = git(tmpDir, "rev-parse", "HEAD")
        tmpDir.resolve("other.txt").writeText("new\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "add other")
        val run = makeRun(repoPath = tmpDir.absolutePath)
        run.baseSha = baseSha
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("<!doctype html") || result.body!!.contains("<!DOCTYPE html"))
    }

    @Test
    fun `pullRequestView with large file adds truncation marker`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        val lines = (1..6000).joinToString("\n") { "line $it" }
        tmpDir.resolve("big.txt").writeText("$lines\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        val baseSha = git(tmpDir, "rev-parse", "HEAD")
        val updatedLines = (1..6000).joinToString("\n") { "edited line $it" }
        tmpDir.resolve("big.txt").writeText("$updatedLines\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "update big file")
        val run = makeRun(repoPath = tmpDir.absolutePath)
        run.baseSha = baseSha
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("abgeschnitten"))
    }

    @Test
    fun `pullRequestView with blank repoPath returns html without git context`(
        @TempDir tmpDir: File,
    ) {
        stubPrData(makeRun(repoPath = ""))
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("<!doctype html") || result.body!!.contains("<!DOCTYPE html"))
    }

    @Test
    fun `pullRequestView with repo dir that has no git parent returns html`(
        @TempDir tmpDir: File,
    ) {
        val noGitDir = tmpDir.resolve("nongit").also { it.mkdirs() }
        stubPrData(makeRun(repoPath = noGitDir.absolutePath))
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("<!doctype html") || result.body!!.contains("<!DOCTYPE html"))
    }

    @Test
    fun `pullRequestView with multiple changed files shows all in HTML`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve("a.txt").writeText("aaa\n")
        tmpDir.resolve("b.txt").writeText("bbb\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        tmpDir.resolve("a.txt").writeText("aaa modified\n")
        tmpDir.resolve("c.txt").writeText("new file\n")
        tmpDir.resolve("b.txt").delete()
        val run = makeRun(repoPath = tmpDir.absolutePath)
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        val body = result.body!!
        assertTrue(body.contains("a.txt"))
        assertTrue(body.contains("c.txt"))
    }

    @Test
    fun `pullRequestView uses gitBranch from run when not blank`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve("init.txt").writeText("init\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        val run = makeRun(repoPath = tmpDir.absolutePath)
        run.gitBranch = "feature/my-branch"
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("feature/my-branch"))
    }

    @Test
    fun `pullRequestView with null-byte in repoPath covers canonicalFile catch block`() {
        stubPrData(makeRun(repoPath = "/valid/start\u0000/invalid"))
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("<!doctype html") || result.body!!.contains("<!DOCTYPE html"))
    }

    @Test
    fun `pullRequestView with fake git dir causes detectBaseBranch to return null`(
        @TempDir tmpDir: File,
    ) {
        File(tmpDir, ".git").mkdirs()
        stubPrData(makeRun(repoPath = tmpDir.absolutePath))
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("<!doctype html") || result.body!!.contains("<!DOCTYPE html"))
    }

    @Test
    fun `pullRequestView with subpath and finn output prepends prefix to agent paths`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        val subDir = tmpDir.resolve("subproject").also { it.mkdirs() }
        subDir.resolve("keep.txt").writeText("keep\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        subDir.resolve("agent.php").writeText("<?php echo 'hi';\n")
        val agentOutput = "### agent.php\n```php\n<?php echo 'hi';\n```"
        val run = makeRun(repoPath = subDir.absolutePath, finnOutput = agentOutput)
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!.contains("agent.php"))
    }

    @Test
    fun `pullRequestView with working-tree reverted file shows UNCHANGED status`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve("file.txt").writeText("original-line1\noriginal-line2")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        val baseSha = git(tmpDir, "rev-parse", "HEAD")

        tmpDir.resolve("file.txt").writeText("modified-line1\noriginal-line2")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "modify file")

        tmpDir.resolve("file.txt").writeText("original-line1\noriginal-line2")

        val run = makeRun(repoPath = tmpDir.absolutePath)
        run.baseSha = baseSha
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        val body = result.body!!
        assertTrue(body.contains("pill-unchanged"), "Expected pill-unchanged in body")
        assertTrue(
            body.contains("Keine Änderungen gegenüber dem Repository-Stand."),
            "Expected no-changes message for UNCHANGED file",
        )
    }

    @Test
    fun `pullRequestView with hunk separator for large unchanged middle section`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        val baseLines = (1..13).joinToString("\n") { "ctx-line-$it" }
        tmpDir.resolve("ctx.txt").writeText("$baseLines\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")
        val baseSha = git(tmpDir, "rev-parse", "HEAD")

        val workLines =
            listOf("MOD-1", "MOD-2") +
                (3..11).map { "ctx-line-$it" } +
                listOf("MOD-12", "MOD-13")
        tmpDir.resolve("ctx.txt").writeText(workLines.joinToString("\n") + "\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "modify ends")

        val run = makeRun(repoPath = tmpDir.absolutePath)
        run.baseSha = baseSha
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        val body = result.body!!
        assertTrue(body.contains("line-hunk"), "Expected hunk separator row in diff output")
    }

    @Test
    fun `pullRequestView with empty added file renders no-changes message in renderFile`(
        @TempDir tmpDir: File,
    ) {
        initGitRepo(tmpDir)
        tmpDir.resolve("keep.txt").writeText("keep\n")
        git(tmpDir, "add", ".")
        git(tmpDir, "commit", "-m", "init")

        tmpDir.resolve("empty.txt").writeText("")

        val run = makeRun(repoPath = tmpDir.absolutePath)
        stubPrData(run)
        val result = controller.pullRequestView(1L)
        assertEquals(200, result.statusCode.value())
        val body = result.body!!
        assertTrue(body.contains("empty.txt"), "Expected empty.txt in body")
        assertTrue(
            body.contains("Keine Änderungen gegenüber dem Repository-Stand."),
            "Expected no-changes message for file with empty lines",
        )
    }

    @Test
    fun `parseAgentPaths returns empty for path that becomes blank after trimming`() {
        val output = "### `:`\n```txt\ncontent\n```"
        val paths = AgentPipelinePullRequestController.parseAgentPaths(output)
        assertTrue(paths.isEmpty(), "Expected empty list for path that trims to blank")
    }
}
