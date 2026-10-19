package com.stacklens.adapter.out.process

import com.stacklens.application.port.out.process.Privilege
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ProcessRunPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ShellCommandAdapterTest {
    private val processPort: ProcessRunPort = mock()

    private val adapter = ShellCommandAdapter(processPort)

    private fun result(
        exitCode: Int,
        stdout: String,
    ) = ProcessResult(exitCode, stdout, "")

    @Test
    fun `commandPath resolves the stdout of a shell lookup`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, "/usr/local/bin/gh\n"))
        assertEquals("/usr/local/bin/gh", adapter.commandPath("gh"))
    }

    @Test
    fun `commandPath runs the lookup through a login shell with a short timeout`() {
        whenever(processPort.run(listOf("/bin/sh", "-lc", "command -v true"), 5L, null)).thenReturn(result(0, "/bin/true"))
        assertEquals("/bin/true", adapter.commandPath("true"))
    }

    @Test
    fun `commandPath returns null when the lookup fails`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(1, ""))
        assertNull(adapter.commandPath("gh"))
    }

    @Test
    fun `commandPath returns null when the lookup succeeds with blank output`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, "   \n"))
        assertNull(adapter.commandPath("gh"))
    }

    @Test
    fun `commandPath skips shell start-up noise before the resolved path`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, "# brew shellenv\n# another\n/usr/bin/env\n"))
        assertEquals("/usr/bin/env", adapter.commandPath("env"))
    }

    @Test
    fun `commandPath returns null when shell start-up noise is all the lookup produces`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, "# brew shellenv\n\n"))
        assertNull(adapter.commandPath("env"))
    }

    @Test
    fun `firstNonBlankLine returns the first populated line trimmed`() {
        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(result(0, "\ngh version 2.45.0 (2024-01-01)\nhttps://github.com/cli/cli\n"))
        assertEquals("gh version 2.45.0 (2024-01-01)", adapter.firstNonBlankLine("gh --version"))
    }

    @Test
    fun `firstNonBlankLine returns null when every line is blank`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, "  \n  \n"))
        assertNull(adapter.firstNonBlankLine("gh --version"))
    }

    @Test
    fun `firstNonBlankLine returns null when the command fails`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(1, "value"))
        assertNull(adapter.firstNonBlankLine("gh --version"))
    }

    @Test
    fun `commandSucceeds reflects the exit code`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, ""), result(1, ""))
        assertTrue(adapter.commandSucceeds("gh copilot --help"))
        assertFalse(adapter.commandSucceeds("gh copilot --help"))
    }

    @Test
    fun `plainScript wraps the script without privilege`() {
        val command = adapter.shellCommand("echo hi", Privilege.NONE)
        assertEquals(listOf("/bin/sh", "-c", "echo hi"), command.command)
        assertFalse(command.requiresPrivilege)
    }

    @Test
    fun `elevatedScript prefers pkexec when it is installed`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, "/usr/bin/pkexec"))
        val command = adapter.shellCommand("apt-get update", Privilege.ELEVATED)
        assertEquals(listOf("pkexec", "/bin/sh", "-c", "apt-get update"), command.command)
        assertTrue(command.requiresPrivilege)
    }

    @Test
    fun `elevatedScript falls back to non-interactive sudo`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(1, ""))
        val command = adapter.shellCommand("apt-get update", Privilege.ELEVATED)
        assertEquals(listOf("/bin/sh", "-c", "sudo -n /bin/sh -c 'apt-get update'"), command.command)
        assertTrue(command.requiresPrivilege)
    }

    @Test
    fun `elevatedScript single quotes are escaped for the sudo fallback`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(1, ""))
        val command = adapter.shellCommand("echo it's", Privilege.ELEVATED)
        assertEquals(
            listOf("/bin/sh", "-c", "sudo -n /bin/sh -c 'echo it'\\''s'"),
            command.command,
        )
    }

    @Test
    fun `run forwards command lists to the process port`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, "done"))
        val res = adapter.run(listOf("echo", "done"), 600L)
        assertEquals(0, res.exitCode)
        assertEquals("done", res.stdout)
    }

    @Test
    fun `commandSucceeds uses a longer timeout than a plain lookup`() {
        whenever(processPort.run(listOf("/bin/sh", "-lc", "gh copilot --help"), 10L, null)).thenReturn(result(0, ""))
        assertTrue(adapter.commandSucceeds("gh copilot --help"))
    }
}
