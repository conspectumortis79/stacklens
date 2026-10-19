package com.stacklens.adapter.out.review.pptx

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class PptxStorageAdapterTest {
    private lateinit var root: File

    private var previousProperty: String? = null

    @BeforeEach
    fun setUp(
        @TempDir tmp: Path,
    ) {
        root = tmp.toFile()
        previousProperty = System.getProperty(DATA_DIR_PROPERTY)
        System.setProperty(DATA_DIR_PROPERTY, root.absolutePath)
    }

    @AfterEach
    fun tearDown() {
        previousProperty?.let { System.setProperty(DATA_DIR_PROPERTY, it) } ?: System.clearProperty(DATA_DIR_PROPERTY)
    }

    @Test
    fun `uploads and result directories live below the configured data dir`() {
        val storage = PptxStorageAdapter()

        assertEquals(File(root, "review-analyse/uploads/7"), storage.uploadsDir(7L))
        assertEquals(File(root, "review-analyse/results/7"), storage.resultsDir(7L))
        assertTrue(storage.uploadsDir(7L).isDirectory)
    }

    @Test
    fun `saveUpload sanitizes the original file name`() {
        val storage = PptxStorageAdapter()

        val saved = storage.saveUpload(3L, "../sprint folie.pptx", byteArrayOf(1, 2, 3))

        assertEquals("original-.._sprint_folie.pptx", saved.name)
        assertByteArraysEqual(byteArrayOf(1, 2, 3), saved.readBytes())
    }

    @Test
    fun `importFromServer copies an existing pptx into the upload folder`() {
        val storage = PptxStorageAdapter()
        val source =
            File(root, "srv/folien.pptx").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(9, 8))
            }

        val imported = storage.importFromServer(4L, source.absolutePath)

        assertEquals("server-folien.pptx", imported.name)
        assertByteArraysEqual(byteArrayOf(9, 8), imported.readBytes())
    }

    @Test
    fun `importFromServer rejects a source that is not a readable file`() {
        val storage = PptxStorageAdapter()
        val directory = File(root, "order").apply { mkdirs() }

        assertThrows(IllegalArgumentException::class.java) { storage.importFromServer(5L, directory.absolutePath) }
        assertThrows(IllegalArgumentException::class.java) { storage.importFromServer(5L, File(root, "missing.pptx").absolutePath) }
    }

    @Test
    fun `saveHtml writes the result page and returns its path`() {
        val storage = PptxStorageAdapter()

        val path = storage.saveHtml(6L, "<html>ok</html>")

        assertEquals("<html>ok</html>", File(path).readText())
    }

    @Test
    fun `findUploadedPptx prefers the expected name and ignores empty files`() {
        val storage = PptxStorageAdapter()
        val uploads = storage.uploadsDir(8L)
        File(uploads, "leer.pptx").writeBytes(ByteArray(0))
        File(uploads, "notes.txt").writeText("kein pptx")
        File(uploads, "Sprint-Folie.pptx").writeBytes(byteArrayOf(1))

        assertEquals("Sprint-Folie.pptx", storage.findUploadedPptx(8L, "sprint-folie.pptx")?.name)
        assertEquals("Sprint-Folie.pptx", storage.findUploadedPptx(8L, null)?.name)
        assertNull(storage.findUploadedPptx(8L, "fehlend.pptx"))
    }

    @Test
    fun `findUploadedPptx returns null when the upload folder is empty`() {
        val storage = PptxStorageAdapter()

        assertNull(storage.findUploadedPptx(9L, null))
    }

    @Test
    fun `findUploadedPptx ignores subdirectories and empty files when looking for any presentation`() {
        val storage = PptxStorageAdapter()
        val uploads = storage.uploadsDir(13L)

        File(uploads, "Unterordner.pptx").mkdirs()
        File(uploads, "original-Folie.pptx").writeBytes(byteArrayOf(1))

        assertEquals("original-Folie.pptx", storage.findUploadedPptx(13L, null)?.name)

        File(uploads, "original-Folie.pptx").delete()
        File(uploads, "leer.pptx").writeBytes(ByteArray(0))
        assertNull(storage.findUploadedPptx(13L, null))
    }

    @Test
    fun `findUploadedPptx accepts the legacy ppt extension when no name is expected`() {
        val storage = PptxStorageAdapter()
        val uploads = storage.uploadsDir(14L)
        File(uploads, "legacy.PPT").writeBytes(byteArrayOf(1))

        assertEquals("legacy.PPT", storage.findUploadedPptx(14L, null)?.name)
    }

    @Test
    fun `cleanup removes the uploads and results of an analysis`() {
        val storage = PptxStorageAdapter()
        val uploads = storage.uploadsDir(11L)
        val results = storage.resultsDir(11L)
        File(uploads, "deck.pptx").writeBytes(byteArrayOf(1))
        File(results, "filled.html").writeText("<html></html>")

        storage.cleanup(11L)

        assertTrue(!uploads.exists() || uploads.listFiles().orEmpty().isEmpty(), "uploads still contain files")
        assertTrue(!results.exists() || results.listFiles().orEmpty().isEmpty(), "results still contain files")
        assertNotNull(storage.uploadsDir(11L))
    }

    private fun assertByteArraysEqual(
        expected: ByteArray,
        actual: ByteArray,
    ) {
        assertEquals(expected.toList(), actual.toList())
    }

    private companion object {
        const val DATA_DIR_PROPERTY = "stacklens.data-dir"
    }
}
