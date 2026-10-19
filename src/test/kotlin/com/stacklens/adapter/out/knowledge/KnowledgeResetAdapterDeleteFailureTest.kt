package com.stacklens.adapter.out.knowledge

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet

class KnowledgeResetAdapterDeleteFailureTest {
    private fun adapterAt(indexDir: String): KnowledgeResetAdapter =
        KnowledgeResetAdapter(
            basePort = mock(),
            sourcePort = mock(),
            documentPort = mock(),
            chunkPort = mock(),
            jobPort = mock(),
            indexGenerationPort = mock(),
            vectorIndexPort = mock(),
            indexDir = indexDir,
        )

    @Test
    fun `an index directory that cannot be emptied is reported and left behind`(
        @TempDir tmp: Path,
    ) {
        val root = Files.createDirectory(tmp.resolve("index"))
        Files.createFile(root.resolve("segment.gen"))
        val readableOnly = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE)
        Files.setPosixFilePermissions(root, readableOnly)

        try {
            adapterAt(root.toString()).resetAllKnowledgeData()
        } finally {
            Files.setPosixFilePermissions(root, EnumSet.of(PosixFilePermission.OWNER_WRITE, *readableOnly.toTypedArray()))
        }
    }
}
