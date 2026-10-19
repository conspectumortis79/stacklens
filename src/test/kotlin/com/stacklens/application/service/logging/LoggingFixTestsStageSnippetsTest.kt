package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.io.File
import java.nio.file.Files

class LoggingFixTestsStageSnippetsTest {
    private val copilot: CopilotStreamingPort = mock()
    private val promptLoader: PromptLoaderPort = mock()
    private val stage = LoggingFixTestsStage(copilot, promptLoader, catalogBackedLoggingFixMarkers(), JavaFileSystemAdapter())

    private fun listSystemTestSnippets(root: File): String = ReflectionTestUtils.invokeMethod<String>(stage, "listSystemTestSnippets", root)!!

    private fun splitDescription(block: String): Pair<String?, String> = ProposedFileParser.splitDescription(block)

    private fun repoWithSystemTests(files: Map<String, String>): File {
        val root = Files.createTempDirectory("logging-fix-tests-repo").toFile()
        File(root, "src/test/systemTest/kotlin").mkdirs()
        files.forEach { (relative, content) ->
            val target = File(root, "src/test/systemTest/kotlin/$relative")
            target.parentFile.mkdirs()
            target.writeText(content)
        }
        return root
    }

    private fun context(root: File?): LoggingFixStageContext =
        LoggingFixStageContext(
            logText = "log",
            selectedCategories = listOf("errors"),
            repoPath = root?.absolutePath,
            repoRoot = root,
            language = "de",
            onPartial = { _, _ -> },
        )

    @Test fun `a repository without system tests explains the missing folder`() {
        val root = Files.createTempDirectory("logging-fix-tests-empty").toFile()

        val snippet = listSystemTestSnippets(root)

        assertTrue(snippet.contains("Kein src/test/systemTest/kotlin im Repo gefunden"))
    }

    @Test fun `an empty system test folder lists nothing and skips the excerpt`() {
        val root = repoWithSystemTests(emptyMap())

        val snippet = listSystemTestSnippets(root)

        assertEquals("Bekannte Systemtest-Dateien (Auszug):\n", snippet)
    }

    @Test fun `folders without kotlin files are reported as such`() {
        val root = repoWithSystemTests(mapOf("notekotlin.md" to "text"))

        assertEquals("Bekannte Systemtest-Dateien (Auszug):\n", listSystemTestSnippets(root))
    }

    @Test fun `system tests are listed up to the cap and the first one is excerpted`() {
        val small = (1..20).associate { index -> "Snippet$index.kt" to "package x\n// snippet $index" }
        val root = repoWithSystemTests(small + mapOf("README.md" to "kein Kotlin"))

        val snippet = listSystemTestSnippets(root)

        assertEquals(15, Regex("^- ", RegexOption.MULTILINE).findAll(snippet).count())
        assertTrue(snippet.contains("Erster Systemtest (excerpt, für Tag/Konvention-Erkennung):"))
        assertTrue(snippet.contains("// snippet"))
    }

    @Test fun `a very large first system test is not excerpted`() {
        val root = repoWithSystemTests(mapOf("Riesig.kt" to "// ${"x".repeat(5000)}"))

        val snippet = listSystemTestSnippets(root)

        assertTrue(snippet.contains("Riesig.kt"))
        assertTrue(!snippet.contains("Erster Systemtest"))
    }

    @Test fun `descriptions are taken from the inline line or the following block`() {
        assertEquals("Kurzfassung" to "code", splitDescription("### DESCRIPTION: Kurzfassung\ncode"))
        assertEquals(null to "", splitDescription("### DESCRIPTION: \n"))
        assertEquals("Vor dem Marker" to "", splitDescription("Vor dem Marker\n### DESCRIPTION:\n"))
        assertEquals(null to "", splitDescription("### DESCRIPTION:\n"))
        assertEquals(null to "nur code", splitDescription("nur code"))
    }

    @Test fun `the stage streams ai output and records the proposed files`() {
        val root = repoWithSystemTests(mapOf("AuthTest.kt" to "package s\n// auth"))
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("PROMPT")
        whenever(copilot.resolveModel()).thenReturn("model-x")
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                anyOrNull(),
            ),
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onPartial = invocation.arguments[5] as (String) -> Unit
            onPartial("chunk-1")
            "### FILE: src/main/Fix.kt\n### DESCRIPTION: behebt den Fehler\nfun main() {}"
        }
        val ctx = context(root)

        stage.execute(ctx)

        assertEquals(1, ctx.proposedFiles.size)
        assertEquals("src/main/Fix.kt", ctx.proposedFiles.single().path)
        assertEquals("behebt den Fehler", ctx.proposedFiles.single().description)
        assertTrue(ctx.testsOutput.toString().contains("fun main() {}"))
    }

    @Test fun `the stage skips itself when the diagnosis is incomplete`() {
        val ctx = context(null).also { it.diagnosisIncomplete = true }

        stage.execute(ctx)

        assertTrue(ctx.testsOutput.toString().contains("Diagnose nicht möglich"))
        assertNull(ctx.repoRoot)
    }
}
