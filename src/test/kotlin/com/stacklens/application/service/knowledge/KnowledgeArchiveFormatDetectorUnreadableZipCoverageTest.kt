package com.stacklens.application.service.knowledge

import com.stacklens.testsupport.CorruptZips
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.zip.ZipException

class KnowledgeArchiveFormatDetectorUnreadableZipCoverageTest {
    @Test
    fun `an unreadable zip payload throws while the entries are walked`() {
        val bytes = CorruptZips.unreadableEntry()

        assertThrows(ZipException::class.java) { KnowledgeArchiveFormatDetector.detect(bytes) }
    }

    @Test
    fun `a zip with a destroyed central directory is walked without finding the manifest`() {
        val bytes = CorruptZips.brokenCentralDirectory()

        org.junit.jupiter.api.Assertions.assertEquals(
            KnowledgeArchiveFormatDetector.Kind.UNKNOWN,
            KnowledgeArchiveFormatDetector.detect(bytes),
        )
    }
}
