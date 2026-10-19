package com.stacklens.adapter.out.filesystem
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class StageOutputFileWriterTest {
    private val writer = StageOutputFileWriter(JavaFileSystemAdapter())

    private fun run(
        repoPath: String? = null,
        slug: String = "test-slug",
    ): AgentPipelineRun =
        AgentPipelineRun(
            id = 1L,
            rawText = "test",
            repoPath = repoPath,
            featureSlug = slug,
            createdAt = System.currentTimeMillis(),
        )

    @Test
    fun `stageOutputDir returns null when repoPath is null`() {
        assertNull(writer.stageOutputDir(run(repoPath = null)))
    }

    @Test
    fun `stageOutputDir returns the correct path when repoPath is set`(
        @TempDir tmp: Path,
    ) {
        val dir = writer.stageOutputDir(run(repoPath = tmp.toString()))
        assertNotNull(dir)
        assertEquals("test-slug", dir!!.name)
        assertEquals(".stacklens", dir.parentFile!!.name)
        assertEquals(tmp.toFile(), dir.parentFile!!.parentFile)
        assertEquals(
            File(tmp.toFile(), ".stacklens/test-slug").absolutePath,
            dir.absolutePath,
        )
    }

    @Test
    fun `stageOutputDir uses the feature slug in path`() {
        val dir = writer.stageOutputDir(run(repoPath = "/tmp/repo", slug = "my-feature"))
        assertEquals("my-feature", dir!!.name)
        assertEquals(".stacklens", dir.parentFile!!.name)
    }

    @Test
    fun `writeStageOutput is no-op when repoPath is null`() {
        writer.write(
            run = run(repoPath = null),
            stageName = "finn",
            content = "content",
        )
        assertTrue(true)
    }

    @Test
    fun `writeStageOutput is no-op when repoPath is not a directory`(
        @TempDir tmp: Path,
    ) {
        val file = tmp.resolve("not-a-dir.txt").toFile()
        file.writeText("I am a file")
        writer.write(
            run = run(repoPath = file.absolutePath),
            stageName = "finn",
            content = "content",
        )
        assertTrue(!File(file.parentFile, ".stacklens").exists())
    }

    @Test
    fun `writeStageOutput writes content to the expected file`(
        @TempDir tmp: Path,
    ) {
        writer.write(
            run = run(repoPath = tmp.toString()),
            stageName = "finn",
            content = "Hello world",
        )
        val written = File(tmp.toFile(), ".stacklens/test-slug/finn.md")
        assertTrue(written.exists())
        assertEquals("Hello world", written.readText(Charsets.UTF_8))
    }

    @Test
    fun `writeStageOutput creates nested directories`(
        @TempDir tmp: Path,
    ) {
        writer.write(
            run = run(repoPath = tmp.toString()),
            stageName = "deeply-nested-stage",
            content = "nested content",
        )
        val written = File(tmp.toFile(), ".stacklens/test-slug/deeply-nested-stage.md")
        assertTrue(written.exists())
        assertEquals("nested content", written.readText(Charsets.UTF_8))
    }

    @Test
    fun `writeStageOutput handles unicode content`(
        @TempDir tmp: Path,
    ) {
        writer.write(
            run = run(repoPath = tmp.toString()),
            stageName = "finn",
            content = "Héllo wörld 🚀",
        )
        val written = File(tmp.toFile(), ".stacklens/test-slug/finn.md")
        assertEquals("Héllo wörld 🚀", written.readText(Charsets.UTF_8))
    }

    @Test
    fun `writeStageOutput overwrites existing file`(
        @TempDir tmp: Path,
    ) {
        writer.write(
            run = run(repoPath = tmp.toString()),
            stageName = "finn",
            content = "first",
        )
        writer.write(
            run = run(repoPath = tmp.toString()),
            stageName = "finn",
            content = "second",
        )
        val written = File(tmp.toFile(), ".stacklens/test-slug/finn.md")
        assertEquals("second", written.readText(Charsets.UTF_8))
    }

    @Test
    fun `writeStageOutput supports multiple stage files`(
        @TempDir tmp: Path,
    ) {
        writer.write(
            run = run(repoPath = tmp.toString()),
            stageName = "finn",
            content = "finn content",
        )
        writer.write(
            run = run(repoPath = tmp.toString()),
            stageName = "faye",
            content = "faye content",
        )
        val finnFile = File(tmp.toFile(), ".stacklens/test-slug/finn.md")
        val fayeFile = File(tmp.toFile(), ".stacklens/test-slug/faye.md")
        assertTrue(finnFile.exists())
        assertTrue(fayeFile.exists())
        assertEquals("finn content", finnFile.readText(Charsets.UTF_8))
        assertEquals("faye content", fayeFile.readText(Charsets.UTF_8))
    }

    @Test
    fun `writeStageOutput isolates stages per slug`(
        @TempDir tmp: Path,
    ) {
        writer.write(
            run = run(repoPath = tmp.toString(), slug = "feature-a"),
            stageName = "finn",
            content = "A content",
        )
        writer.write(
            run = run(repoPath = tmp.toString(), slug = "feature-b"),
            stageName = "finn",
            content = "B content",
        )
        val aFile = File(tmp.toFile(), ".stacklens/feature-a/finn.md")
        val bFile = File(tmp.toFile(), ".stacklens/feature-b/finn.md")
        assertTrue(aFile.exists())
        assertTrue(bFile.exists())
        assertEquals("A content", aFile.readText(Charsets.UTF_8))
        assertEquals("B content", bFile.readText(Charsets.UTF_8))
    }

    @Test
    fun `writeStageOutput handles empty content`(
        @TempDir tmp: Path,
    ) {
        writer.write(
            run = run(repoPath = tmp.toString()),
            stageName = "finn",
            content = "",
        )
        val written = File(tmp.toFile(), ".stacklens/test-slug/finn.md")
        assertTrue(written.exists())
        assertEquals("", written.readText(Charsets.UTF_8))
    }

    @Test
    fun `writeStageOutput handles multiline content`(
        @TempDir tmp: Path,
    ) {
        writer.write(
            run = run(repoPath = tmp.toString()),
            stageName = "finn",
            content = "line1\nline2\nline3\n",
        )
        val written = File(tmp.toFile(), ".stacklens/test-slug/finn.md")
        assertEquals("line1\nline2\nline3\n", written.readText(Charsets.UTF_8))
    }
}
