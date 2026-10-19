package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.ImageExtractionPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.service.imageextraction.ImageExtractionAi
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path

class ImageExtractionServiceTargetDirectoryCoverageTest {
    private val service =
        ImageExtractionService(
            mock<ImageExtractionAi>(),
            mock<AiUsageContextPort>(),
            mock<ImageExtractionPort>(),
            mock<StartupRecovery>(),
            fileSystem = JavaFileSystemAdapter(),
        )

    @Test
    fun `a target path below a plain file cannot be created and nothing is persisted`(
        @TempDir dir: Path,
    ) {
        val blocker = File(dir.toFile(), "blocker")
        blocker.writeText("not a directory")

        val persisted =
            PrivateInvocation.invoke(service, "persistExtractedText", "shot.png", "extracted", File(blocker, "sub").absolutePath)

        assertNull(persisted)
    }
}
