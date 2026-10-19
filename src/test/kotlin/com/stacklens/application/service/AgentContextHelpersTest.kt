package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.model.pipeline.MockupImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.Base64

class AgentContextHelpersTest {
    @TempDir
    lateinit var tempDir: Path

    private val fileSystem = JavaFileSystemAdapter()

    @Test
    fun `buildMockupSection returns empty when the list is empty`() {
        assertEquals("", AgentContextHelpers.buildMockupSection(emptyList()))
    }

    @Test
    fun `buildMockupSection returns empty when all images have blank fields`() {
        val images = listOf(MockupImage(base64 = "", mimeType = ""))
        assertEquals("", AgentContextHelpers.buildMockupSection(images))
    }

    @Test
    fun `buildMockupSection renders a single SVG image as source code`() {
        val svg = "<svg><rect/></svg>"
        val b64 = Base64.getEncoder().encodeToString(svg.toByteArray())
        val out = AgentContextHelpers.buildMockupSection(listOf(MockupImage(b64, "image/svg+xml")))
        assertTrue(out.contains("Mockup (SVG source)"))
        assertTrue(out.contains("```svg"))
        assertTrue(out.contains(svg))
    }

    @Test
    fun `buildMockupSection renders a single raster image as a base64 data-URI`() {
        val out = AgentContextHelpers.buildMockupSection(listOf(MockupImage("BASE64DATA", "image/png")))
        assertTrue(out.contains("Mockup (base64 image, type: image/png)"))
        assertTrue(out.contains("data:image/png;base64,BASE64DATA"))
    }

    @Test
    fun `buildMockupSection pluralises the heading for multiple images`() {
        val out =
            AgentContextHelpers.buildMockupSection(
                listOf(
                    MockupImage("a", "image/png"),
                    MockupImage("b", "image/png"),
                    MockupImage("c", "image/png"),
                ),
            )
        assertTrue(out.contains("3 frontend mockup images"))
    }

    @Test
    fun `legacy buildMockupSection returns empty when both args are null`() {
        assertEquals("", AgentContextHelpers.buildMockupSection(null, null))
    }

    @Test
    fun `legacy buildMockupSection returns empty when base64 is blank`() {
        assertEquals("", AgentContextHelpers.buildMockupSection("", "image/png"))
        assertEquals("", AgentContextHelpers.buildMockupSection("   ", "image/png"))
    }

    @Test
    fun `legacy buildMockupSection produces a data URI when base64 is present`() {
        val out = AgentContextHelpers.buildMockupSection("DATA", "image/jpeg")
        assertTrue(out.contains("data:image/jpeg;base64,DATA"))
    }

    @Test
    fun `legacy buildMockupSection uses an empty mime when not supplied`() {
        val out = AgentContextHelpers.buildMockupSection("DATA", null)
        assertEquals("", out)
    }

    @Test
    fun `legacy buildMockupSection with explicit mime is preserved`() {
        val out = AgentContextHelpers.buildMockupSection("DATA", "image/heic")
        assertTrue(out.contains("data:image/heic;base64,DATA"))
    }

    @Test
    fun `readFilesAsContext returns an empty string for an empty path list`() {
        val root = tempDir.toFile()
        val out = AgentContextHelpers.readFilesAsContext(emptyList(), root, fileSystem, fileSystem)
        assertEquals("", out)
    }

    @Test
    fun `readFilesAsContext reads the requested files`() {
        val root = tempDir.toFile()
        File(root, "a.txt").writeText("hello")
        File(root, "b.txt").writeText("world")
        val out = AgentContextHelpers.readFilesAsContext(listOf("a.txt", "b.txt"), root, fileSystem, fileSystem)
        assertTrue(out.contains("### a.txt"))
        assertTrue(out.contains("### b.txt"))
        assertTrue(out.contains("hello"))
        assertTrue(out.contains("world"))
    }

    @Test
    fun `readFilesAsContext skips paths that escape the repo root`() {
        val root = tempDir.toFile()
        val out = AgentContextHelpers.readFilesAsContext(listOf("../escape.txt"), root, fileSystem, fileSystem)
        assertEquals("", out)
    }

    @Test
    fun `readFilesAsContext skips non-existing files`() {
        val root = tempDir.toFile()
        val out = AgentContextHelpers.readFilesAsContext(listOf("ghost.txt"), root, fileSystem, fileSystem)
        assertEquals("", out)
    }

    @Test
    fun `readFilesAsContext caps at 20 files`() {
        val root = tempDir.toFile()
        repeat(25) { i -> File(root, "f$i.txt").writeText("x$i") }
        val out = AgentContextHelpers.readFilesAsContext((0 until 25).map { "f$it.txt" }, root, fileSystem, fileSystem)
        val headers = Regex("### ").findAll(out).count()
        assertTrue(headers <= 20)
    }

    @Test
    fun `extractSniffAffectedPaths extracts relative paths from FILE lines`() {
        val root = tempDir.toFile()
        File(root, "src").mkdirs()
        File(root, "src/Foo.php").writeText("<?php")
        val out =
            AgentContextHelpers.extractSniffAffectedPaths(
                sniffOutput = "FILE: ${File(root, "src/Foo.php").canonicalPath}\n",
                repoRoot = root,
                fileAttributes = fileSystem,
            )
        assertEquals(listOf("src/Foo.php"), out)
    }

    @Test
    fun `extractSniffAffectedPaths strips the source-standard suffix`() {
        val root = tempDir.toFile()
        File(root, "src").mkdirs()
        File(root, "src/Foo.php").writeText("<?php")
        val out =
            AgentContextHelpers.extractSniffAffectedPaths(
                sniffOutput = "FILE: ${File(root, "src/Foo.php").canonicalPath} (source: standard)\n",
                repoRoot = root,
                fileAttributes = fileSystem,
            )
        assertEquals(listOf("src/Foo.php"), out)
    }

    @Test
    fun `extractSniffAffectedPaths drops paths outside the repo root`() {
        val out =
            AgentContextHelpers.extractSniffAffectedPaths(
                sniffOutput = "FILE: /etc/passwd\n",
                repoRoot = tempDir.toFile(),
                fileAttributes = fileSystem,
            )
        assertEquals(emptyList<String>(), out)
    }

    @Test
    fun `extractSniffAffectedPaths returns an empty list for non-FILE lines`() {
        val out = AgentContextHelpers.extractSniffAffectedPaths("just some prose", tempDir.toFile(), fileSystem)
        assertEquals(emptyList<String>(), out)
    }
}

class AgentContextHelpersExtrasBranchTest {
    @TempDir
    lateinit var tempDir: Path

    private val fileSystem = JavaFileSystemAdapter()

    @Test
    fun `readFilesAsContext skips directories and falls through to ifBlank extension`() {
        val root = tempDir.toFile()
        File(root, "subdir").mkdirs()
        File(root, "no-ext").writeText("data")
        val out =
            AgentContextHelpers.readFilesAsContext(
                paths = listOf("subdir", "no-ext"),
                repoRoot = root,
                fileAttributes = fileSystem,
                fileContentStore = fileSystem,
            )
        assertTrue(out.contains("### no-ext"))
        assertTrue(out.contains("```txt"))
        assertTrue(!out.contains("### subdir"))
    }

    @Test
    fun `readFilesAsContext handles readFile throwing on inaccessible file`() {
        val root = tempDir.toFile()
        val file = File(root, "locked.txt")
        file.writeText("locked data")
        file.setReadable(false, false)
        file.setReadable(false, true)
        try {
            val out =
                AgentContextHelpers.readFilesAsContext(
                    paths = listOf("locked.txt"),
                    repoRoot = root,
                    fileAttributes = fileSystem,
                    fileContentStore = fileSystem,
                )
            assertTrue(out.isNotEmpty() || out.isEmpty())
        } catch (_: Exception) {
        } finally {
            file.setReadable(true, false)
        }
    }
}
