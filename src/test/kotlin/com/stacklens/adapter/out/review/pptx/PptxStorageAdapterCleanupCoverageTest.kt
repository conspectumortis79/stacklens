package com.stacklens.adapter.out.review.pptx

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PptxStorageAdapterCleanupCoverageTest {
    private val previousDataDir = System.getProperty("stacklens.data-dir")

    @AfterEach
    fun restoreDataDir() {
        if (previousDataDir == null) {
            System.clearProperty("stacklens.data-dir")
        } else {
            System.setProperty("stacklens.data-dir", previousDataDir)
        }
    }

    @Test
    fun `cleanup deletes the upload and the result folder`(
        @TempDir dir: File,
    ) {
        System.setProperty("stacklens.data-dir", dir.absolutePath)
        val storage = PptxStorageAdapter()
        storage.saveUpload(7L, "deck.pptx", byteArrayOf(1, 2))
        storage.saveHtml(7L, "<html/>")

        storage.cleanup(7L)

        assertFalse(File(dir, "review-analyse/uploads/7").exists())
        assertFalse(File(dir, "review-analyse/results/7").exists())
    }

    @Test
    fun `cleanup of an analysis without any folder is harmless`(
        @TempDir dir: File,
    ) {
        System.setProperty("stacklens.data-dir", dir.absolutePath)

        PptxStorageAdapter().cleanup(99L)

        assertTrue(File(dir, "review-analyse").exists())
    }

    @Test
    fun `a folder that looks like an upload is not offered as the deck`(
        @TempDir dir: File,
    ) {
        System.setProperty("stacklens.data-dir", dir.absolutePath)
        val storage = PptxStorageAdapter()
        val lookalike = File(dir, "review-analyse/uploads/6/original-deck.pptx")
        lookalike.parentFile.mkdirs()
        assertTrue(lookalike.mkdir())

        assertNull(storage.findUploadedPptx(6L, null))
    }

    @Test
    fun `an upload folder that cannot be listed yields no candidate`(
        @TempDir dir: File,
    ) {
        System.setProperty("stacklens.data-dir", dir.absolutePath)
        val storage = PptxStorageAdapter()
        val uploads = File(dir, "review-analyse/uploads/5")
        uploads.parentFile.mkdirs()
        assertTrue(uploads.createNewFile())

        assertNull(storage.findUploadedPptx(5L, null))
    }
}
