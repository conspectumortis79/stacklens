package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.application.port.inbound.repo.DetectedRepoStack
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Random
import javax.imageio.ImageIO

class ImageInlinePreparerBranchTest {
    private val preparer =
        ImageInlinePreparer(
            com.stacklens.adapter.out.image
                .DefaultInlineImageEncoder(),
        )

    private fun noisyPng(
        width: Int = 2000,
        height: Int = 2000,
    ): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val rnd = Random(42)
        for (x in 0 until width) {
            for (y in 0 until height) {
                img.setRGB(x, y, rnd.nextInt())
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    @Test
    fun `small images pass through unchanged`() {
        val bytes = byteArrayOf(1, 2, 3)
        val result = preparer.prepareImageForInlineUse(bytes, "image/png")
        assertFalse(result.scaledDown)
        assertEquals(-1, result.width)
        assertEquals(-1, result.height)
        assertEquals("image/png", result.mimeType)
    }

    @Test
    fun `large images are resized and re-encoded as jpeg`() {
        val bytes = noisyPng()
        assertTrue(bytes.size > 200_000)
        val result = preparer.prepareImageForInlineUse(bytes, "image/png")
        assertEquals("image/jpeg", result.mimeType)
        assertTrue(result.width in 1..1568)
        assertEquals(true, result.scaledDown)
    }

    @Test
    fun `large undecodable images pass through unchanged`() {
        val bytes = ByteArray(2_000_000) { 7 }
        val result = preparer.prepareImageForInlineUse(bytes, "image/png")
        assertFalse(result.scaledDown)
        assertEquals("image/png", result.mimeType)
    }

    @Test
    fun `mime type variants map to formats`() {
        val small = byteArrayOf(1)
        assertEquals("Image/PNG", preparer.prepareImageForInlineUse(small, "Image/PNG").mimeType)
        assertEquals("image/jpeg", preparer.prepareImageForInlineUse(small, "image/jpeg").mimeType)
        assertEquals("image/jpg", preparer.prepareImageForInlineUse(small, "image/jpg").mimeType)
        assertEquals("image/tiff", preparer.prepareImageForInlineUse(small, "image/tiff").mimeType)
        assertEquals("image/tif", preparer.prepareImageForInlineUse(small, "image/tif").mimeType)
        assertEquals("application/octet-stream", preparer.prepareImageForInlineUse(small, "application/octet-stream").mimeType)
    }

    @Test
    fun `large image with unknown format still decodes via fallback formats`() {
        val bytes = noisyPng()
        val result = preparer.prepareImageForInlineUse(bytes, "application/octet-stream")
        assertEquals("image/jpeg", result.mimeType)
    }

    @Test
    fun `scale keeps small images untouched and handles argb sources`() {
        val small = noisyPng(100, 50)
        val result = preparer.prepareImageForInlineUse(small, "image/png")
        assertFalse(result.scaledDown)
    }
}

class LocalRepoServiceDetectStackTest {
    private val gitlabConfigRepo: com.stacklens.application.port.out.GitlabConfigPort = mock()
    private val gitlabPort: com.stacklens.application.port.out.repo.GitlabPort = mock()
    private val repo: LocalRepoConfigPort = mock()
    private val codeAnalysisRepo: com.stacklens.application.port.out.CodeAnalysisPort = mock()
    private val gitClonePort: com.stacklens.application.port.out.repo.GitClonePort = mock()
    private val i18n: com.stacklens.application.port.out.i18n.I18nPort = mock()
    private val service = LocalRepoService(gitlabConfigRepo, gitlabPort, repo, codeAnalysisRepo, gitClonePort, i18n, PassthroughDemoConfigFilter.instance, JavaFileSystemAdapter())

    @Test
    fun `detectStack returns empty for missing path`() {
        assertEquals(DetectedRepoStack(emptyList(), emptyList()), service.detectStack("/does/not/exist"))
    }

    @Test
    fun `detectStack returns empty when path is a file`() {
        val file = java.io.File.createTempFile("stacklens-", ".txt")
        file.deleteOnExit()
        assertEquals(DetectedRepoStack(emptyList(), emptyList()), service.detectStack(file.absolutePath))
    }

    @Test
    fun `detectStack collects known indicators`() {
        val dir =
            java.nio.file.Files
                .createTempDirectory("stacklens-repo")
        java.io.File(dir.toFile(), "build.gradle.kts").writeText("x")
        java.io.File(dir.toFile(), "package.json").writeText("{}")
        java.io.File(dir.toFile(), "tsconfig.json").mkdirs()
        val result = service.detectStack(dir.toString())
        assertTrue(result.stacks.contains("kotlin"))
        assertTrue(result.stacks.contains("node"))
        assertTrue(result.indicators.contains("build.gradle.kts"))
        assertFalse(result.indicators.contains("tsconfig.json"))
    }

    @Test
    fun `detectStack maps gradle indicators to stacks`(
        @TempDir tmp: Path,
    ) {
        java.io.File(tmp.toFile(), "pom.xml").writeText("x")
        java.io.File(tmp.toFile(), "requirements.txt").writeText("x")
        val result = service.detectStack(tmp.toString())
        assertEquals(setOf("java", "python"), result.stacks.toSet())
    }
}
