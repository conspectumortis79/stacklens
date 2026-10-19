package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.DirectRepoReader
import com.stacklens.adapter.out.filesystem.ShellRepoReader
import com.stacklens.application.port.out.repo.RepoDockerfile
import com.stacklens.application.port.out.repo.RepoFileRead
import com.stacklens.application.port.out.repo.RepoReader
import com.stacklens.application.port.out.repo.ShellCommandRunner
import com.stacklens.application.port.out.repo.UNBOUNDED_REPO_DEPTH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class RepoSectionBuilderTest {
    @Test
    fun `build produces prompt with header and directory structure`(
        @TempDir tmp: Path,
    ) {
        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.startsWith("Repository: my-repo"))
        assertTrue(out.contains("## Directory structure (top-level)"))
    }

    @Test
    fun `build includes shell fallback banner when provided`(
        @TempDir tmp: Path,
    ) {
        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo", shellFallbackBanner = "[TCC] fallback")

        assertTrue(out.contains("[TCC] fallback"))
    }

    @Test
    fun `build includes composer json when present`(
        @TempDir tmp: Path,
    ) {
        Files.writeString(tmp.resolve("composer.json"), """{"name":"foo"}""")

        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.contains("## composer.json"))
        assertTrue(out.contains("\"name\":\"foo\""))
    }

    @Test
    fun `build includes docker compose content when present`(
        @TempDir tmp: Path,
    ) {
        Files.writeString(tmp.resolve("docker-compose.yml"), "version: '3'")

        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.contains("## docker-compose.yml"))
        assertTrue(out.contains("version: '3'"))
    }

    @Test
    fun `build tries alternative docker compose paths`(
        @TempDir tmp: Path,
    ) {
        Files.createDirectories(tmp.resolve("docker"))
        Files.writeString(tmp.resolve("docker/docker-compose.yml"), "nested-compose")

        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.contains("nested-compose"))
    }

    @Test
    fun `build includes Dockerfile content when present`(
        @TempDir tmp: Path,
    ) {
        Files.writeString(tmp.resolve("Dockerfile"), "FROM ubuntu")

        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.contains("## Dockerfile"))
        assertTrue(out.contains("FROM ubuntu"))
    }

    @Test
    fun `build includes source tree when src directory exists`(
        @TempDir tmp: Path,
    ) {
        val src = Files.createDirectories(tmp.resolve("src"))
        Files.createDirectory(src.resolve("main"))

        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.contains("## Source tree (src/)"))
    }

    @Test
    fun `build uses app directory when src is missing`(
        @TempDir tmp: Path,
    ) {
        val app = Files.createDirectories(tmp.resolve("app"))

        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.contains("## Source tree (app/)"))
        assertTrue(out.contains(app.toString() + "/"))
    }

    @Test
    fun `build includes sample PHP files when found`(
        @TempDir tmp: Path,
    ) {
        val src = Files.createDirectories(tmp.resolve("src"))
        Files.writeString(src.resolve("index.php"), "<?php echo 'hi';")

        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.contains("## Sample PHP files"))
        assertTrue(out.contains("<?php echo 'hi';"))
    }

    @Test
    fun `build skips PHP file body when file is unreadable`(
        @TempDir tmp: Path,
    ) {
        val src = Files.createDirectories(tmp.resolve("src"))
        val phpFile = src.resolve("unreadable.php")
        Files.writeString(phpFile, "<?php")
        phpFile.toFile().setReadable(false)

        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.contains("## Sample PHP files"))
    }

    @Test
    fun `build includes Makefile when present`(
        @TempDir tmp: Path,
    ) {
        Files.writeString(tmp.resolve("Makefile"), "test:\n\tjest")

        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.contains("## Makefile targets"))
        assertTrue(out.contains("jest"))
    }

    @Test
    fun `build truncates output to 20000 characters`(
        @TempDir tmp: Path,
    ) {
        Files.writeString(tmp.resolve("Makefile"), "x".repeat(30_000))

        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.length <= 20_000)
    }

    @Test
    fun `build is a no-op for empty repo with no banner`(
        @TempDir tmp: Path,
    ) {
        val out = RepoSectionBuilder(DirectRepoReader()).build(tmp.toFile(), "my-repo")

        assertTrue(out.contains("Repository: my-repo"))
        assertTrue(out.contains("## Directory structure"))
        assertTrue(out.contains("## composer.json") || out.contains("## Source tree") || out.contains("## Makefile") || out.length < 500)
    }

    @Test
    fun `a dockerfile that cannot be read is reported instead of skipped`(
        @TempDir tmp: Path,
    ) {
        val reader = StubRepoReader(dockerfile = RepoDockerfile("nested/Dockerfile", RepoFileRead.Unreadable))

        val out = RepoSectionBuilder(reader).build(tmp.toFile(), "repo")

        assertTrue(out.contains("## nested/Dockerfile"))
        assertTrue(out.contains("(unreadable)"))
    }

    @Test
    fun `a dockerfile that turns out to be absent is left out`(
        @TempDir tmp: Path,
    ) {
        val reader = StubRepoReader(dockerfile = RepoDockerfile("Dockerfile", RepoFileRead.Missing))

        val out = RepoSectionBuilder(reader).build(tmp.toFile(), "repo")

        assertTrue(!out.contains("## Dockerfile"))
    }

    private class StubRepoReader(
        private val dockerfile: RepoDockerfile?,
        private val phpFiles: List<String> = listOf("src/A.php"),
        private val reads: Map<String, RepoFileRead> = emptyMap(),
        private val directories: List<String> = emptyList(),
        private val existingDirectories: Set<String> = emptySet(),
    ) : RepoReader {
        override fun readText(
            root: File,
            path: String,
            maxLen: Int,
        ): RepoFileRead = reads[path] ?: RepoFileRead.Missing

        override fun listTopLevel(
            root: File,
            limit: Int,
        ) = emptyList<String>()

        override fun listDirectories(
            root: File,
            maxDepth: Int,
            limit: Int,
        ) = directories

        override fun hasDirectory(
            root: File,
            relativePath: String,
        ): Boolean = relativePath in existingDirectories

        override fun findFiles(
            root: File,
            extension: String,
            skipDirectoryNamed: String,
            limit: Int,
        ) = phpFiles

        override fun readFirstDockerfile(
            root: File,
            maxDepth: Int,
            maxLen: Int,
        ) = dockerfile
    }

    @Test
    fun `a php file that vanished between listing and reading is left out`(
        @TempDir tmp: Path,
    ) {
        val reader = StubRepoReader(dockerfile = null, phpFiles = listOf("/repo/src/A.php"))

        val out = RepoSectionBuilder(reader).build(tmp.toFile(), "repo")

        assertTrue(out.contains("## Sample PHP files"))
        assertTrue(out.contains("### /repo/src/A.php"))
        assertTrue(!out.contains("(unreadable)"))
    }

    @Test
    fun `DirectRepoReader readText reports a non-existing file as missing`(
        @TempDir tmp: Path,
    ) {
        val reader = DirectRepoReader()
        val result = reader.readText(tmp.toFile(), "missing.txt", 100)
        assertEquals(RepoFileRead.Missing, result)
    }

    @Test
    fun `DirectRepoReader readText returns truncated content`(
        @TempDir tmp: Path,
    ) {
        Files.writeString(tmp.resolve("foo.txt"), "x".repeat(100))

        val reader = DirectRepoReader()
        val result = reader.readText(tmp.toFile(), "foo.txt", 10)
        assertEquals(10, (result as RepoFileRead.Available).text.length)
    }

    @Test
    fun `DirectRepoReader listTopLevel returns sorted entries with limit`(
        @TempDir tmp: Path,
    ) {
        Files.writeString(tmp.resolve("a.txt"), "")
        Files.writeString(tmp.resolve("b.txt"), "")
        Files.createDirectory(tmp.resolve("c"))

        val reader = DirectRepoReader()
        val out = reader.listTopLevel(tmp.toFile(), 2)
        assertEquals(2, out.size)
    }

    @Test
    fun `DirectRepoReader findFiles returns php files excluding vendor`(
        @TempDir tmp: Path,
    ) {
        val src = Files.createDirectories(tmp.resolve("src"))
        Files.writeString(src.resolve("a.php"), "")
        val vendor = Files.createDirectories(tmp.resolve("vendor"))
        Files.writeString(vendor.resolve("b.php"), "")

        val reader = DirectRepoReader()
        val out = reader.findFiles(tmp.toFile(), "php", "vendor", 10)
        assertEquals(1, out.size)
        assertTrue(out[0].endsWith("a.php"))
    }

    @Test
    fun `DirectRepoReader readFirstDockerfile returns null when no Dockerfile`() {
        val reader = DirectRepoReader()
        val result = reader.readFirstDockerFile(java.io.File("/nonexistent-${System.nanoTime()}"), 100)
        assertEquals(null, result)
    }

    @Test
    fun `build handles PHP file with null readText content via missing file`(
        @TempDir tmp: Path,
    ) {
        val reader = DirectRepoReader()
        val phpFile = File(tmp.toFile(), "missing.php")
        phpFile.delete()
        val out = RepoSectionBuilder(reader).build(tmp.toFile(), "my-repo")
        assertTrue(out.contains("Repository: my-repo"))
    }

    @Test
    fun `build handles PHP file with null readText content when file deleted after listing`(
        @TempDir tmp: Path,
    ) {
        val phpFile = File(tmp.toFile(), "ephemeral.php")
        phpFile.writeText("<?php echo 1;")
        val reader =
            DirectRepoReader()
        val foundFiles = reader.findFiles(tmp.toFile(), "php", "vendor", 6)
        assertTrue(foundFiles.isNotEmpty())
        phpFile.delete()
        val out = RepoSectionBuilder(reader).build(tmp.toFile(), "my-repo")
        assertTrue(out.contains("Repository: my-repo"))
    }

    @Test
    fun `build handles PHP file with null readText content via ShellRepoReader returning null`(
        @TempDir tmp: Path,
    ) {
        val reader =
            ShellRepoReader(
                ShellCommandRunner { args ->
                    val joined = args.joinToString(" ")
                    when {
                        joined.startsWith("cat ") -> null
                        joined.contains("*.php") -> "/some/absolute/path/a.php"
                        joined.contains("-maxdepth") -> ""
                        joined.startsWith("ls ") -> ""
                        else -> ""
                    }
                },
            )
        val out = RepoSectionBuilder(reader).build(tmp.toFile(), "my-repo")
        assertTrue(out.contains("Repository: my-repo"))
        assertTrue(out.contains("## Sample PHP files"))
    }
}

private fun DirectRepoReader.readFirstDockerFile(
    root: java.io.File,
    maxLen: Int,
): RepoDockerfile? = this.readFirstDockerfile(root, UNBOUNDED_REPO_DEPTH, maxLen)
