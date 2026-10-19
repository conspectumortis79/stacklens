package com.stacklens.adapter.out.review.pptx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class PptxStorageAdapterCoverageTest {
    private val propertyKey = "stacklens.data-dir"

    private fun withDataDir(
        value: String?,
        block: (PptxStorageAdapter) -> Unit,
    ) {
        val previous = System.getProperty(propertyKey)
        if (value == null) {
            System.clearProperty(propertyKey)
        } else {
            System.setProperty(propertyKey, value)
        }
        try {
            block(PptxStorageAdapter())
        } finally {
            if (previous == null) {
                System.clearProperty(propertyKey)
            } else {
                System.setProperty(propertyKey, previous)
            }
        }
    }

    private fun baseDirOf(adapter: PptxStorageAdapter): File {
        val field = PptxStorageAdapter::class.java.getDeclaredField("baseDir")
        field.isAccessible = true
        return field.get(adapter) as File
    }

    @Test
    fun `a blank configured data dir falls back to the working directory layout`() {
        withDataDir("   ") { adapter ->
            assertEquals(File("data/review-analyse"), baseDirOf(adapter))
        }
    }

    @Test
    fun `an absent configuration resolves the base directory from the environment`() {
        withDataDir(null) { adapter ->
            val env = System.getenv("STACKLENS_DATA_DIR")
            val expected = if (env == null) File("data/review-analyse") else File(env, "review-analyse")

            assertEquals(expected, baseDirOf(adapter))
        }
    }

    @Test
    fun `a configured data dir is used as the review root`(
        @TempDir dir: Path,
    ) {
        withDataDir(dir.toString()) { adapter ->
            assertEquals(File(dir.toFile(), "review-analyse"), baseDirOf(adapter))
        }
    }

    @Test
    fun `cleanup removes the upload and result folders of an analysis`(
        @TempDir dir: Path,
    ) {
        withDataDir(dir.toString()) { adapter ->
            val upload = adapter.saveUpload(7L, "deck!.pptx", byteArrayOf(1, 2, 3))
            val htmlPath = adapter.saveHtml(7L, "<html/>")

            assertTrue(upload.exists())
            assertTrue(File(htmlPath).exists())

            adapter.cleanup(7L)

            assertFalse(upload.exists())
            assertFalse(File(htmlPath).exists())
        }
    }

    @Test
    fun `findUploadedPptx matches the expected suffix case insensitively`(
        @TempDir dir: Path,
    ) {
        withDataDir(dir.toString()) { adapter ->
            val saved = adapter.saveUpload(3L, "Quarterly.PPTX", byteArrayOf(1, 2, 3))
            File(adapter.uploadsDir(3L), "empty.pptx").apply { writeText("") }
            File(adapter.uploadsDir(3L), "subdir.pptx").mkdir()
            File(adapter.uploadsDir(3L), "notes.txt").writeText("text")

            assertEquals(saved, adapter.findUploadedPptx(3L, expectedName = "quarterly.pptx"))
            assertEquals(saved, adapter.findUploadedPptx(3L, expectedName = null))
            assertNull(adapter.findUploadedPptx(3L, expectedName = "missing.pptx"))
        }
    }

    @Test
    fun `findUploadedPptx returns null when the upload folder is empty`(
        @TempDir dir: Path,
    ) {
        withDataDir(dir.toString()) { adapter ->
            assertNull(adapter.findUploadedPptx(42L, expectedName = null))
        }
    }

    @Test
    fun `findUploadedPptx tolerates an upload path that is not a directory`(
        @TempDir dir: Path,
    ) {
        withDataDir(dir.toString()) { adapter ->
            File(File(dir.toFile(), "uploads"), "9").mkdirs()
            File(File(dir.toFile(), "uploads"), "9").delete()
            File(File(dir.toFile(), "uploads"), "9").writeText("i am a file")

            assertNull(adapter.findUploadedPptx(9L, expectedName = null))
        }
    }
}
