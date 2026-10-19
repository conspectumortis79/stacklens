package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.ImageExtractionPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.service.imageextraction.ImageExtractionAi
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path

class ImageExtractionServiceExistingTargetCoverageTest {
    private val service =
        ImageExtractionService(
            mock<ImageExtractionAi>(),
            mock<AiUsageContextPort>(),
            mock<ImageExtractionPort>(),
            mock<StartupRecovery>(),
            fileSystem = JavaFileSystemAdapter(),
        )

    private fun persist(
        filename: String,
        target: File,
    ): String? =
        PrivateInvocation.invoke(
            service,
            "persistExtractedText",
            filename,
            "extracted text",
            target.absolutePath,
        ) as? String

    @Test
    fun `an already existing target directory is written into without creating it`(
        @TempDir dir: Path,
    ) {
        val persisted = persist("shot.png", dir.toFile())

        assertEquals(File(dir.toFile(), "shot.txt").absolutePath, persisted)
        assertTrue(persisted != null && File(persisted).readText() == "extracted text")
    }

    @Test
    fun `a missing target directory is created before the text is written`(
        @TempDir dir: Path,
    ) {
        val created = File(dir.toFile(), "nested/deeper")

        val persisted = persist("diagram.png", created)

        assertTrue(created.isDirectory)
        assertEquals(File(created, "diagram.txt").absolutePath, persisted)
    }
}
