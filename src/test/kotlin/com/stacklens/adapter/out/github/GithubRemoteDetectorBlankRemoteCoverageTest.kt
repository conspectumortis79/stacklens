package com.stacklens.adapter.out.github

import com.stacklens.application.port.out.GithubRepoConfigPort
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.nio.file.Path

class GithubRemoteDetectorBlankRemoteCoverageTest {
    private val githubRepoConfigPort: GithubRepoConfigPort = mock()
    private val detector = GithubRemoteDetector(githubRepoConfigPort)

    private fun runGit(
        dir: Path,
        vararg args: String,
    ) {
        val process =
            ProcessBuilder("git", *args)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start()
        process.waitFor()
    }

    @Test
    fun `a repository whose origin url is blank yields no configuration`(
        @TempDir dir: Path,
    ) {
        runGit(dir, "init", "-q")
        runGit(dir, "config", "remote.origin.url", " ")

        assertNull(detector.detectGithubRemote(dir))
    }
}
