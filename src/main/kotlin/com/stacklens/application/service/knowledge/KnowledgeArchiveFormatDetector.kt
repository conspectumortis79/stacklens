package com.stacklens.application.service.knowledge

import com.stacklens.domain.knowledge.KnowledgeArchiveMagic
import java.util.zip.ZipInputStream

object KnowledgeArchiveFormatDetector {
    enum class Kind {
        SINGLE,
        BUNDLE,
        UNKNOWN,
    }

    private val SLKN_MAGIC = KnowledgeArchiveMagic.SLKN.toByteArray(Charsets.US_ASCII)

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun detect(archiveBytes: ByteArray): Kind {
        if (archiveBytes.size < 4) return Kind.UNKNOWN
        if (archiveBytes.copyOfRange(0, 4).contentEquals(SLKN_MAGIC)) {
            return Kind.SINGLE
        }
        if (archiveBytes.copyOfRange(0, 4).contentEquals(ZIP_MAGIC)) {
            return if (zipContainsEntry(archiveBytes, "MANIFEST.json")) Kind.BUNDLE else Kind.UNKNOWN
        }
        return Kind.UNKNOWN
    }

    private fun zipContainsEntry(
        bytes: ByteArray,
        entryName: String,
    ): Boolean {
        ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryName) return true
                entry = zip.nextEntry
            }
        }
        return false
    }
}
