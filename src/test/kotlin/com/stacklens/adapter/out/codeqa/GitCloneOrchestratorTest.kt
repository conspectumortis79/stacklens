package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ProcessRunPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class GitCloneOrchestratorTest {
    private val processPort = mock<ProcessRunPort>()
    private val orchestrator = GitCloneOrchestrator(processPort)

    private fun mrData(
        mrUrl: String = "https://example.com/mr/1",
        platform: String = "gitlab",
        title: String = "MR",
        description: String = "",
        sourceBranch: String = "feature/x",
        targetBranch: String = "main",
        authorName: String = "tester",
        state: String = "open",
        diffText: String = "",
        changedFiles: List<String> = emptyList(),
        diffBaseSha: String = "",
        diffStartSha: String = "",
        diffHeadSha: String = "",
    ) = MrFetchPort.MrData(
        mrUrl = mrUrl,
        platform = platform,
        title = title,
        description = description,
        sourceBranch = sourceBranch,
        targetBranch = targetBranch,
        authorName = authorName,
        state = state,
        diffText = diffText,
        changedFiles = changedFiles,
        diffBaseSha = diffBaseSha,
        diffStartSha = diffStartSha,
        diffHeadSha = diffHeadSha,
    )

    @Test
    fun `cloneBranch succeeds when shallow clone works`() {
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(0, "", ""))
        val tmpDir = File("/tmp/clone")
        val res = orchestrator.cloneBranch("git@github.com:foo/bar.git", tmpDir, mrData())
        assertTrue(res.success)
        assertNull(res.errorMessage)

        val captor = argumentCaptor<List<String>>()
        verify(processPort).run(captor.capture(), any(), any())
        assertEquals(
            listOf("git", "clone", "--branch", "feature/x", "--depth", "1", "git@github.com:foo/bar.git", "/tmp/clone"),
            captor.firstValue,
        )
    }

    @Test
    fun `cloneBranch uses 300 second timeout for primary clone`() {
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(0, "", ""))
        orchestrator.cloneBranch("git@github.com:foo/bar.git", File("/tmp/c"), mrData())
        verify(processPort).run(any(), org.mockito.kotlin.eq(300L), any())
    }

    @Test
    fun `cloneBranch falls back to SHA fetch when clone fails with exit 128 and SHA is provided`() {
        whenever(processPort.run(any(), any(), any()))
            .thenReturn(ProcessResult(128, "", "fatal: not found"))
            .thenReturn(ProcessResult(0, "", ""))
            .thenReturn(ProcessResult(0, "", ""))
            .thenReturn(ProcessResult(0, "", ""))
            .thenReturn(ProcessResult(0, "", ""))

        val tmpDir = File("/tmp/clone")
        val res = orchestrator.cloneBranch("git@github.com:foo/bar.git", tmpDir, mrData(diffHeadSha = "abc123"))
        assertTrue(res.success)

        val captor = argumentCaptor<List<String>>()
        verify(processPort, org.mockito.kotlin.times(5)).run(captor.capture(), any(), any())
        val calls = captor.allValues
        assertEquals(listOf("git", "init", "/tmp/clone"), calls[1])
        assertEquals(listOf("git", "-C", "/tmp/clone", "remote", "add", "origin", "git@github.com:foo/bar.git"), calls[2])
        assertEquals(listOf("git", "-C", "/tmp/clone", "fetch", "--depth", "1", "origin", "abc123"), calls[3])
        assertEquals(listOf("git", "-C", "/tmp/clone", "checkout", "FETCH_HEAD"), calls[4])
    }

    @Test
    fun `cloneBranch returns failure when fallback init fails`() {
        whenever(processPort.run(any(), any(), any()))
            .thenReturn(ProcessResult(128, "", "fatal"))
            .thenReturn(ProcessResult(1, "", "init failed"))
        val res = orchestrator.cloneBranch("git@github.com:foo/bar.git", File("/tmp/c"), mrData(diffHeadSha = "abc"))
        assertFalse(res.success)
        assertEquals("git init failed", res.errorMessage)
    }

    @Test
    fun `cloneBranch returns failure when fallback remote add fails`() {
        whenever(processPort.run(any(), any(), any()))
            .thenReturn(ProcessResult(128, "", "fatal"))
            .thenReturn(ProcessResult(0, "", ""))
            .thenReturn(ProcessResult(1, "", "remote failed"))
        val res = orchestrator.cloneBranch("git@github.com:foo/bar.git", File("/tmp/c"), mrData(diffHeadSha = "abc"))
        assertFalse(res.success)
        assertEquals("git remote add failed", res.errorMessage)
    }

    @Test
    fun `cloneBranch returns failure when fallback fetch fails`() {
        whenever(processPort.run(any(), any(), any()))
            .thenReturn(ProcessResult(128, "", "fatal"))
            .thenReturn(ProcessResult(0, "", ""))
            .thenReturn(ProcessResult(0, "", ""))
            .thenReturn(ProcessResult(1, "", "fetch failed"))
        val res = orchestrator.cloneBranch("git@github.com:foo/bar.git", File("/tmp/c"), mrData(diffHeadSha = "abc"))
        assertFalse(res.success)
        assertEquals("git fetch failed for abc", res.errorMessage)
    }

    @Test
    fun `cloneBranch returns failure when fallback checkout fails`() {
        whenever(processPort.run(any(), any(), any()))
            .thenReturn(ProcessResult(128, "", "fatal"))
            .thenReturn(ProcessResult(0, "", ""))
            .thenReturn(ProcessResult(0, "", ""))
            .thenReturn(ProcessResult(0, "", ""))
            .thenReturn(ProcessResult(1, "", "checkout failed"))
        val res = orchestrator.cloneBranch("git@github.com:foo/bar.git", File("/tmp/c"), mrData(diffHeadSha = "abc"))
        assertFalse(res.success)
        assertEquals("git checkout failed", res.errorMessage)
    }

    @Test
    fun `cloneBranch skips SHA fallback when exit code is not 128`() {
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(1, "", "auth fail"))
        val res = orchestrator.cloneBranch("git@github.com:foo/bar.git", File("/tmp/c"), mrData(diffHeadSha = "abc"))
        assertFalse(res.success)
        verify(processPort, org.mockito.kotlin.times(1)).run(any(), any(), any())
        assertTrue(res.errorMessage!!.contains("auth fail"))
    }

    @Test
    fun `cloneBranch skips SHA fallback when SHA is blank`() {
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(128, "", "fatal"))
        val res = orchestrator.cloneBranch("git@github.com:foo/bar.git", File("/tmp/c"), mrData(diffHeadSha = ""))
        assertFalse(res.success)
        verify(processPort, org.mockito.kotlin.times(1)).run(any(), any(), any())
    }

    @Test
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    fun `cloneBranch redacts credentials from URL in error message`() {
        val stdout = "Cloning into 'foo'\nfatal: could not read Username for 'https://user:secret@example.com': terminal prompts disabled"
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(1, stdout, ""))
        val res = orchestrator.cloneBranch("https://user:secret@example.com/repo.git", File("/tmp/c"), mrData())
        assertNotNull(res.errorMessage)
        assertFalse(res.errorMessage!!.contains("user:secret"))
        assertTrue(res.errorMessage!!.contains("https://" + "***" + "@"))
    }

    @Test
    fun `cloneBranch truncates long output to 600 chars`() {
        val longOut = "x".repeat(2000)
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(1, longOut, ""))
        val res = orchestrator.cloneBranch("u", File("/c"), mrData())
        val chunkInMessage = res.errorMessage!!.substringAfter(":\n")
        assertTrue(chunkInMessage.length <= 600)
    }

    @Test
    fun `cloneBranch passes tmpDir parentFile as workdir for primary clone`() {
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(0, "", ""))
        val tmpDir = File("/parent/child")
        orchestrator.cloneBranch("u", tmpDir, mrData())
        verify(processPort).run(any(), any(), org.mockito.kotlin.eq(File("/parent")))
    }
}
