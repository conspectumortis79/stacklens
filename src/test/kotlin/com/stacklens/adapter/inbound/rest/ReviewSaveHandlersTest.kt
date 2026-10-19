package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.domain.model.ReviewAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.io.File
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
class ReviewSaveHandlersTest {
    @Mock
    lateinit var analysisService: ReviewAnalysisUseCase

    @Mock
    lateinit var i18n: I18nUseCase

    @Mock
    lateinit var workspaceFiles: WorkspaceFileUseCase

    private fun makeAnalysis(markdown: String? = null): ReviewAnalysis =
        ReviewAnalysis(
            id = 1L,
            jiraSprintUrl = "http://jira/sprint/1",
            resultMarkdown = markdown,
        )

    private fun captureRequest(): TextFileSaveRequest {
        val captor = argumentCaptor<TextFileSaveRequest>()
        verify(workspaceFiles).saveText(captor.capture())
        return captor.firstValue
    }

    private fun stubSaved(
        dir: File,
        filename: String,
    ): String {
        val path = File(dir, filename).absolutePath
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved(path))
        return path
    }

    @Test
    fun `MarkdownReviewSaveHandler returns 400 when resultMarkdown is null`(
        @TempDir tempDir: Path,
    ) {
        whenever(i18n.t(any<String>())).thenReturn("no result")
        val handler = MarkdownReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis(null), tempDir.toString(), null, null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `MarkdownReviewSaveHandler returns 400 when resultMarkdown is blank`(
        @TempDir tempDir: Path,
    ) {
        whenever(i18n.t(any<String>())).thenReturn("no result")
        val handler = MarkdownReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis("   "), tempDir.toString(), null, null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `MarkdownReviewSaveHandler saves markdown with default filename`(
        @TempDir tempDir: Path,
    ) {
        val expectedPath = stubSaved(tempDir.toFile(), "Sprint-Review-Analyse.md")
        val handler = MarkdownReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis("# Sprint Review\n\nContent"), tempDir.toString(), null, null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(expectedPath, resp.body?.get("savedPath"))
        val request = captureRequest()
        assertEquals(tempDir.toFile().path, request.directory)
        assertEquals("Sprint-Review-Analyse.md", request.filename)
        assertEquals("# Sprint Review\n\nContent", request.content)
    }

    @Test
    fun `MarkdownReviewSaveHandler saves markdown with safe suffix filename`(
        @TempDir tempDir: Path,
    ) {
        val expectedPath = stubSaved(tempDir.toFile(), "Sprint-Review-Q2-2024.md")
        val handler = MarkdownReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis("# Content"), tempDir.toString(), null, "Q2-2024")
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(expectedPath, resp.body?.get("savedPath"))
        assertEquals("Sprint-Review-Q2-2024.md", captureRequest().filename)
    }

    @Test
    fun `HtmlReviewSaveHandler returns 400 when resultMarkdown is null`(
        @TempDir tempDir: Path,
    ) {
        whenever(i18n.t(any<String>())).thenReturn("no result")
        val handler = HtmlReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis(null), tempDir.toString(), null, null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `HtmlReviewSaveHandler returns 400 when resultMarkdown is blank`(
        @TempDir tempDir: Path,
    ) {
        whenever(i18n.t(any<String>())).thenReturn("no result")
        val handler = HtmlReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis("  "), tempDir.toString(), null, null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `HtmlReviewSaveHandler saves html with default filename and sprintName`(
        @TempDir tempDir: Path,
    ) {
        val expectedPath = stubSaved(tempDir.toFile(), "Sprint-Review-Analyse.html")
        val handler = HtmlReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis("# Review"), tempDir.toString(), "Sprint <1>", null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(expectedPath, resp.body?.get("savedPath"))
        val request = captureRequest()
        assertEquals("Sprint-Review-Analyse.html", request.filename)
        assertTrue(request.content.contains("<!DOCTYPE html>"))
        assertTrue(request.content.contains("Sprint &lt;1&gt;"))
    }

    @Test
    fun `HtmlReviewSaveHandler saves html with safe suffix filename`(
        @TempDir tempDir: Path,
    ) {
        val expectedPath = stubSaved(tempDir.toFile(), "Sprint-Review-Sprint-42.html")
        val handler = HtmlReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis("Content"), tempDir.toString(), null, "Sprint-42")
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(expectedPath, resp.body?.get("savedPath"))
        assertEquals("Sprint-Review-Sprint-42.html", captureRequest().filename)
    }

    @Test
    fun `HtmlReviewSaveHandler strips html block from markdown before saving`(
        @TempDir tempDir: Path,
    ) {
        stubSaved(tempDir.toFile(), "Sprint-Review-Analyse.html")
        val handler = HtmlReviewSaveHandler(workspaceFiles, i18n)
        val md = "# Review\n\n```html\n<html><body>dashboard</body></html>\n```\n\nSome analysis."
        val ctx = ReviewSaveContext(1L, makeAnalysis(md), tempDir.toString(), "Sprint 1", null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.OK, resp.statusCode)
        val content = captureRequest().content
        assertTrue(content.contains("Some analysis."))
        assertTrue(!content.contains("```html"))
    }

    @Test
    fun `HtmlReviewSaveHandler uses html block directly when only html block present`(
        @TempDir tempDir: Path,
    ) {
        stubSaved(tempDir.toFile(), "Sprint-Review-Analyse.html")
        val handler = HtmlReviewSaveHandler(workspaceFiles, i18n)
        val dashboard = "<!DOCTYPE html><html><body><h1>Dashboard</h1></body></html>"
        val md = "```html\n$dashboard\n```"
        val ctx = ReviewSaveContext(1L, makeAnalysis(md), tempDir.toString(), "Sprint 1", null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.OK, resp.statusCode)
        val content = captureRequest().content
        assertTrue(content.contains("Dashboard"))
        assertTrue(!content.contains("```html"))
    }

    @Test
    fun `MarkdownReviewSaveHandler throws when the workspace rejects the write`(
        @TempDir tempDir: Path,
    ) {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))
        val handler = MarkdownReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis("# Content"), tempDir.toString(), null, null)
        assertThrows(RuntimeException::class.java) { handler.save(ctx) }
    }

    @Test
    fun `HtmlReviewSaveHandler throws when the workspace rejects the write`(
        @TempDir tempDir: Path,
    ) {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.EXTENSION_NOT_EDITABLE))
        val handler = HtmlReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis("# Content"), tempDir.toString(), null, null)
        assertThrows(RuntimeException::class.java) { handler.save(ctx) }
    }

    @Test
    fun `DashboardReviewSaveHandler throws when the workspace rejects the write`(
        @TempDir tempDir: Path,
    ) {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
        val handler = DashboardReviewSaveHandler(workspaceFiles, i18n)
        val md = "```html\n<html><body>dashboard</body></html>\n```"
        val ctx = ReviewSaveContext(1L, makeAnalysis(md), tempDir.toString(), null, null)
        assertThrows(RuntimeException::class.java) { handler.save(ctx) }
    }

    @Test
    fun `escapeHtml escapes special characters`() {
        val result = escapeHtml("Hello <World> & \"test\"")
        assertEquals("Hello &lt;World&gt; &amp; &quot;test&quot;", result)
    }

    @Test
    fun `escapeForJs escapes backslash and double quote`() {
        assertEquals("a\\\\b\\\"c", escapeForJs("a\\b\"c"))
    }

    @Test
    fun `escapeForJs escapes newlines`() {
        assertEquals("line1\\nline2", escapeForJs("line1\nline2"))
    }

    @Test
    fun `escapeForJs escapes script close tag`() {
        val result = escapeForJs("</script>")
        assertTrue(result.contains("\\u003c"))
        assertTrue(!result.contains("<"))
    }

    @Test
    fun `extractHtmlBlock returns content of fenced html block`() {
        val md = "```html\n<h1>Hello</h1>\n```"
        val result = extractHtmlBlock(md)
        assertNotNull(result)
        assertTrue(result!!.contains("<h1>Hello</h1>"))
    }

    @Test
    fun `extractHtmlBlock returns null when no html block present`() {
        assertNull(extractHtmlBlock("# Heading\n\nPlain text."))
    }

    @Test
    fun `extractHtmlBlock matches bare doctype html`() {
        val md = "<!DOCTYPE html><html><body></body></html>"
        val result = extractHtmlBlock(md)
        assertNotNull(result)
        assertTrue(result!!.startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun `extractHtmlBlock matches bare html tag`() {
        val md = "<html><body>content</body></html>"
        val result = extractHtmlBlock(md)
        assertNotNull(result)
        assertTrue(result!!.startsWith("<html>"))
    }

    @Test
    fun `HtmlReviewSaveHandler falls back to buildHtmlPage when only non-html fenced block`(
        @TempDir tempDir: Path,
    ) {
        stubSaved(tempDir.toFile(), "Sprint-Review-Analyse.html")
        val md = "```python\nprint('hi')\n```"
        val handler = HtmlReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis(md), tempDir.toString(), null, null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertTrue(captureRequest().content.contains("<!DOCTYPE html>"))
    }

    @Test
    fun `buildHtmlPage embeds markdown as js string`() {
        val html = buildHtmlPage("Title", "# Hello\n\nWorld")
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("var md ="))
        assertTrue(!html.contains("noscript"))
    }

    @Test
    fun `buildHtmlPage escapes title as html`() {
        val html = buildHtmlPage("Sprint <1>", "content")
        assertTrue(html.contains("Sprint &lt;1&gt;"))
    }

    @Test
    fun `stripHtmlBlock removes fenced html block from markdown`() {
        val markdown = "# Review\n\n```html\n<html><body>dashboard</body></html>\n```\n\nMore content."
        val result = stripHtmlBlock(markdown)
        assertTrue(result.contains("# Review"))
        assertTrue(result.contains("More content."))
        assertTrue(!result.contains("```html"))
        assertTrue(!result.contains("dashboard"))
    }

    @Test
    fun `stripHtmlBlock returns unchanged markdown when no html block present`() {
        val markdown = "# Review\n\nJust plain markdown."
        assertEquals(markdown, stripHtmlBlock(markdown))
    }

    @Test
    fun `MarkdownReviewSaveHandler has format md`() {
        val handler = MarkdownReviewSaveHandler(workspaceFiles, i18n)
        assertEquals("md", handler.format)
    }

    @Test
    fun `HtmlReviewSaveHandler has format html`() {
        val handler = HtmlReviewSaveHandler(workspaceFiles, i18n)
        assertEquals("html", handler.format)
    }

    @Test
    fun `ReviewSaveContext exposes id property`() {
        val analysis = makeAnalysis("# Review")
        val ctx = ReviewSaveContext(id = 99L, analysis = analysis, dirPath = "/tmp", sprintName = null, safeSuffix = null)
        assertEquals(99L, ctx.id)
    }

    @Test
    fun `DashboardReviewSaveHandler has format dashboard`() {
        val handler = DashboardReviewSaveHandler(workspaceFiles, i18n)
        assertEquals("dashboard", handler.format)
    }

    @Test
    fun `DashboardReviewSaveHandler returns 400 when resultMarkdown is null`(
        @TempDir tempDir: Path,
    ) {
        whenever(i18n.t(any<String>())).thenReturn("no result")
        val handler = DashboardReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis(null), tempDir.toString(), null, null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `DashboardReviewSaveHandler returns 400 when resultMarkdown is blank`(
        @TempDir tempDir: Path,
    ) {
        whenever(i18n.t(any<String>())).thenReturn("no result")
        val handler = DashboardReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis("   "), tempDir.toString(), null, null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `DashboardReviewSaveHandler returns 400 when no html block present`(
        @TempDir tempDir: Path,
    ) {
        whenever(i18n.t(any<String>())).thenReturn("dashboard not found")
        val handler = DashboardReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis("# Just markdown, no html block"), tempDir.toString(), null, null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `DashboardReviewSaveHandler saves fenced html block with default filename`(
        @TempDir tempDir: Path,
    ) {
        val expectedPath = stubSaved(tempDir.toFile(), "Sprint-Dashboard.html")
        val htmlContent = "<html><body><h1>Sprint Dashboard</h1></body></html>"
        val md = "```html\n$htmlContent\n```"
        val handler = DashboardReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis(md), tempDir.toString(), null, null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(expectedPath, resp.body?.get("savedPath"))
        val request = captureRequest()
        assertEquals("Sprint-Dashboard.html", request.filename)
        assertTrue(request.content.contains("Sprint Dashboard"))
    }

    @Test
    fun `DashboardReviewSaveHandler saves with safe suffix in filename`(
        @TempDir tempDir: Path,
    ) {
        val expectedPath = stubSaved(tempDir.toFile(), "Sprint-Dashboard-Sprint-42.html")
        val htmlContent = "<html><body>dashboard</body></html>"
        val md = "```html\n$htmlContent\n```"
        val handler = DashboardReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis(md), tempDir.toString(), null, "Sprint-42")
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(expectedPath, resp.body?.get("savedPath"))
        assertEquals("Sprint-Dashboard-Sprint-42.html", captureRequest().filename)
    }

    @Test
    fun `DashboardReviewSaveHandler saves bare html document`(
        @TempDir tempDir: Path,
    ) {
        stubSaved(tempDir.toFile(), "Sprint-Dashboard.html")
        val html = "<!DOCTYPE html><html><body><h1>Dashboard</h1></body></html>"
        val handler = DashboardReviewSaveHandler(workspaceFiles, i18n)
        val ctx = ReviewSaveContext(1L, makeAnalysis(html), tempDir.toString(), "Sprint 1", null)
        val resp = handler.save(ctx)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertTrue(captureRequest().content.contains("Dashboard"))
    }
}
