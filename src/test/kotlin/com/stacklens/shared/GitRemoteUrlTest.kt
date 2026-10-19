package com.stacklens.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import java.io.File

class GitRemoteUrlTest {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    private fun shellScript(
        name: String,
        body: String,
    ): File {
        val file = File.createTempFile(name, ".sh")
        file.deleteOnExit()
        file.writeText("#!/bin/sh\n$body\n")
        file.setExecutable(true)
        return file
    }

    @Test
    fun `gitRemoteUrl returns the trimmed output of a successful command`() {
        assumeFalse(isWindows)
        val fakeGit = shellScript("fake-git-ok", "echo '  https://gitlab.example.com/team/repo.git  '")

        val remote = gitRemoteUrl(File("."), gitBinary = fakeGit.absolutePath, timeoutSeconds = 20)

        assertEquals("https://gitlab.example.com/team/repo.git", remote)
    }

    @Test
    fun `gitRemoteUrl returns null when the command writes nothing`() {
        assumeFalse(isWindows)
        val fakeGit = shellScript("fake-git-blank", "true")

        assertNull(gitRemoteUrl(File("."), gitBinary = fakeGit.absolutePath, timeoutSeconds = 20))
    }

    @Test
    fun `gitRemoteUrl returns null when the command fails`() {
        assumeFalse(isWindows)
        val fakeGit = shellScript("fake-git-fail", "echo nope; exit 128")

        assertNull(gitRemoteUrl(File("."), gitBinary = fakeGit.absolutePath, timeoutSeconds = 20))
    }

    @Test
    fun `gitRemoteUrl gives up and kills a command that never finishes`() {
        assumeFalse(isWindows)
        val hangingGit = shellScript("fake-git-hang", "sleep 120")
        val startedAt = System.nanoTime()

        val remote = gitRemoteUrl(File("."), gitBinary = hangingGit.absolutePath, timeoutSeconds = 1)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertNull(remote)
        assertTrue(elapsedMillis < 30_000, "hanging git binary was not killed in time (${elapsedMillis}ms)")
    }

    @Test
    fun `gitRemoteUrl yields null when the working directory cannot be used`() {
        assertNull(gitRemoteUrl(File("src/test/resources/definitely-not-a-directory/xyz")))
    }

    @Test
    fun `gitRemotePathWithNamespace prefers the https path`() {
        assertEquals("team/nested/repo", gitRemotePathWithNamespace("https://gitlab.example.com/team/nested/repo.git"))
    }

    @Test
    fun `gitRemotePathWithNamespace falls back to the scp style colon`() {
        assertEquals("team/repo", gitRemotePathWithNamespace("git@gitlab.example.com:team/repo.git"))
    }

    @Test
    fun `gitRemotePathWithNamespace returns null for a path without any separator`() {
        assertNull(gitRemotePathWithNamespace("plain-text-remote"))
    }

    @Test
    fun `gitRemotePathWithNamespace returns null when the parsed path is blank`() {
        assertNull(gitRemotePathWithNamespace("git@gitlab.example.com:"))
    }
}
