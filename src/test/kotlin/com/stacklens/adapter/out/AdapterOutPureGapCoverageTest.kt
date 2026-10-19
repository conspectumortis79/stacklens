package com.stacklens.adapter.out

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ShellCommandAdapterLineCoverageTest {
    private val processPort = mock<com.stacklens.application.port.out.process.ProcessRunPort>()
    private val adapter =
        com.stacklens.adapter.out.process
            .ShellCommandAdapter(processPort)

    private fun result(
        exitCode: Int,
        stdout: String,
    ) = com.stacklens.application.port.out.process
        .ProcessResult(exitCode, stdout, "")

    @Test
    fun `the first non blank line of a successful command is trimmed`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, "   \n  second line  \nthird"))

        assertEquals("second line", adapter.firstNonBlankLine("print-matrix"))
    }

    @Test
    fun `a failed command has no first line`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(1, "ignored"))

        assertNull(adapter.firstNonBlankLine("print-matrix"))
    }

    @Test
    fun `a command without any content has no first line`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, "   \n\t"))

        assertNull(adapter.firstNonBlankLine("print-matrix"))
    }

    @Test
    fun `the probe result of a command path skips blank and commented lines`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(0, "\n# comment\n/usr/bin/git\n"))

        assertEquals("/usr/bin/git", adapter.commandPath("git"))
    }

    @Test
    fun `a failing command path probe yields no path`() {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(result(127, ""))

        assertNull(adapter.commandPath("git"))
    }
}

class MarkdownToHtmlSanitizeCoverageTest {
    @Test
    fun `a fenced markdown block is unwrapped before conversion`() {
        val html =
            com.stacklens.adapter.out.shared.MarkdownToHtmlConverter
                .sanitize("```md\n# Titel\nText\n```")

        assertEquals(false, html.contains("```"))
    }

    @Test
    fun `plain markdown keeps its content and a null input yields nothing`() {
        assertEquals(
            "",
            com.stacklens.adapter.out.shared.MarkdownToHtmlConverter
                .sanitize(null),
        )
        assertEquals(
            true,
            com.stacklens.adapter.out.shared.MarkdownToHtmlConverter
                .sanitize("ohne Zaun")
                .contains("ohne Zaun"),
        )
    }
}

class ClaudeCodeTokenLookupCoverageTest {
    private val aiConfigRepo = mock<AiConfigPort>()

    private fun adapter(
        credential: String? = null,
        apiKey: String? = null,
        oauthToken: String? = null,
        credentialFailure: Throwable? = null,
    ) = com.stacklens.adapter.out.ai
        .ClaudeCodeTokenLookupPortAdapter(
            aiConfigRepo,
            com.stacklens.adapter.out.ai.ClaudeCodeTestSupport
                .settings(apiKey = apiKey, oauthToken = oauthToken),
            com.stacklens.application.port.out.ai.ClaudeCodeCredentialAccessPort {
                credentialFailure?.let { throw it }
                credential
            },
        )

    @Test
    fun `the configured api key wins and is trimmed`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(claudeCodeApiKey = "  stored key  "))

        assertEquals("stored key", adapter(credential = "store key", apiKey = "env key").resolveToken())
    }

    @Test
    fun `a blank configured key falls back to the environment`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(claudeCodeApiKey = "   "))

        assertEquals("env key", adapter(credential = "store key", apiKey = "env key").resolveToken())
        assertEquals("oauth key", adapter(credential = "store key", apiKey = "env key", oauthToken = "oauth key").resolveToken())
    }

    @Test
    fun `a missing ai config and a blank environment value resolve to nothing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)

        assertNull(adapter(credential = "   ").resolveToken())
    }

    @Test
    fun `a failing credential store is tolerated`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)

        assertEquals("fallback", adapter(apiKey = "fallback", credentialFailure = IllegalStateException("keychain locked")).resolveToken())
        assertNull(adapter(credentialFailure = IllegalStateException("keychain locked")).resolveToken())
    }

    @Test
    fun `a probed executable path is returned when the probe accepts it`() {
        val shell = mock<com.stacklens.application.port.out.process.ShellPort>()
        whenever(shell.commandPath("git")).thenReturn("  /usr/bin/git  ")
        val probe = mock<com.stacklens.adapter.out.process.FileSystemExecutableProbe>()
        whenever(probe.isExecutable("/usr/bin/git")).thenReturn(true)
        val lookup =
            com.stacklens.adapter.out.process
                .HostExecutableLookup(shell, mock(), mock(), emptyList(), probe)

        assertEquals("/usr/bin/git", lookup.resolve("git", emptyList()))
    }
}
