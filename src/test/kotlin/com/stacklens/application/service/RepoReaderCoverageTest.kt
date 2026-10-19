package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.DirectRepoReader
import com.stacklens.adapter.out.filesystem.ShellRepoReader
import com.stacklens.application.port.out.repo.RepoDockerfile
import com.stacklens.application.port.out.repo.RepoFileRead
import com.stacklens.application.port.out.repo.UNBOUNDED_REPO_DEPTH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

class RepoReaderCoverageTest {
    private val direct = DirectRepoReader()

    private fun permissionsOf(file: File): PosixFileAttributeView = Files.getFileAttributeView(file.toPath(), PosixFileAttributeView::class.java)

    private fun scripted(replies: MutableList<String?>): ShellRepoReader =
        ShellRepoReader {
            if (replies.isEmpty()) null else replies.removeAt(0)
        }

    @Test
    fun `the shell reader reports nothing when every command fails`() {
        val replies = mutableListOf<String?>(null, null, null, null, null, null)
        val reader = scripted(replies)

        assertEquals(RepoFileRead.Missing, reader.readText(File("/repo"), "README.md", 10))
        assertEquals(emptyList<String>(), reader.listTopLevel(File("/repo"), 5))
        assertEquals(emptyList<String>(), reader.listDirectories(File("/repo"), UNBOUNDED_REPO_DEPTH, 5))
        assertEquals(emptyList<String>(), reader.findFiles(File("/repo"), "php", "vendor", 5))
        assertEquals(null, reader.readFirstDockerfile(File("/repo"), UNBOUNDED_REPO_DEPTH, 20))
        assertEquals(0, replies.size)
    }

    @Test
    fun `the shell reader keeps only non blank command output`() {
        val reader = scripted(mutableListOf("src/a\n\nsrc/b\n", "app/index.php\n", null))

        assertEquals(listOf("src/a", "src/b"), reader.listTopLevel(File("/repo"), 5))
        assertEquals(listOf("app/index.php"), reader.findFiles(File("/repo"), "php", "vendor", 5))
        assertEquals(null, reader.readFirstDockerfile(File("/repo"), UNBOUNDED_REPO_DEPTH, 10))
    }

    @Test
    fun `the shell reader pairs the located dockerfile with its content`() {
        val reader = scripted(mutableListOf("/repo/docker/Dockerfile", "FROM alpine\nRUN echo 1"))

        assertEquals(
            RepoDockerfile("docker/Dockerfile", RepoFileRead.Available("FROM alpine")),
            reader.readFirstDockerfile(File("/repo"), UNBOUNDED_REPO_DEPTH, 11),
        )
    }

    @Test
    fun `the shell reader marks a located but unreadable dockerfile as unreadable`() {
        val reader = scripted(mutableListOf("/repo/Dockerfile", null, "present"))

        assertEquals(
            RepoDockerfile("Dockerfile", RepoFileRead.Unreadable),
            reader.readFirstDockerfile(File("/repo"), UNBOUNDED_REPO_DEPTH, 10),
        )
    }

    @Test
    fun `the direct reader yields no entries for a plain file root`(
        @TempDir dir: Path,
    ) {
        val file = File(dir.toFile(), "plain.txt").apply { writeText("x") }

        assertEquals(emptyList<String>(), direct.listTopLevel(file, 5))
        assertEquals(listOf("plain.txt"), direct.listTopLevel(dir.toFile(), 5))
    }

    @Test
    fun `the direct reader reports a dockerfile it cannot open as unreadable`(
        @TempDir dir: Path,
    ) {
        val dockerfile = File(dir.toFile(), "Dockerfile").apply { writeText("FROM alpine") }
        permissionsOf(dockerfile).setPermissions(PosixFilePermissions.fromString("---------"))
        try {
            assertEquals(
                RepoDockerfile("Dockerfile", RepoFileRead.Unreadable),
                direct.readFirstDockerfile(dir.toFile(), UNBOUNDED_REPO_DEPTH, 20),
            )
        } finally {
            permissionsOf(dockerfile).setPermissions(PosixFilePermissions.fromString("rw-r--r--"))
        }
    }

    @Test
    fun `the direct reader answers missing for absent files and content for readable ones`(
        @TempDir dir: Path,
    ) {
        File(dir.toFile(), "README.md").writeText("hello world")

        assertEquals(RepoFileRead.Available("hello"), direct.readText(dir.toFile(), "README.md", 5))
        assertEquals(RepoFileRead.Missing, direct.readText(dir.toFile(), "absent.md", 5))
        assertEquals(null, direct.readFirstDockerfile(File(dir.toFile(), "nowhere"), UNBOUNDED_REPO_DEPTH, 10))
        assertEquals(listOf("README.md"), direct.listTopLevel(dir.toFile(), 5))
    }

    @Test
    fun `both readers answer the same way for a missing file and a readable file`(
        @TempDir dir: Path,
    ) {
        File(dir.toFile(), "README.md").writeText("hello world")
        val shell = scripted(mutableListOf("hello world", null, null))

        assertEquals(direct.readText(dir.toFile(), "README.md", 11), shell.readText(dir.toFile(), "README.md", 11))
        assertEquals(direct.readText(dir.toFile(), "absent.md", 11), shell.readText(dir.toFile(), "absent.md", 11))
    }

    @Test
    fun `both readers answer the same way for an unreadable file`(
        @TempDir dir: Path,
    ) {
        val locked = File(dir.toFile(), "Makefile").apply { writeText("all:") }
        permissionsOf(locked).setPermissions(PosixFilePermissions.fromString("---------"))
        val shell = scripted(mutableListOf(null, "present"))
        try {
            assertEquals(RepoFileRead.Unreadable, direct.readText(dir.toFile(), "Makefile", 100))
            assertEquals(RepoFileRead.Unreadable, shell.readText(dir.toFile(), "Makefile", 100))
        } finally {
            permissionsOf(locked).setPermissions(PosixFilePermissions.fromString("rw-r--r--"))
        }
    }
}
