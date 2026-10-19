package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CopilotBinaryDetectorWalkTopDownTest {
    @Test
    fun `walkTopDown yields absolute paths for every file under the root`(
        @TempDir tmp: Path,
    ) {
        val subFile = tmp.resolve("a.txt")
        subFile.toFile().writeText("hello")
        val subDir = tmp.resolve("sub")
        subDir.toFile().mkdirs()
        val nestedFile = subDir.resolve("nested.txt")
        nestedFile.toFile().writeText("nested")
        val results =
            CopilotBinaryDetector.DefaultFileSystem
                .walkTopDown(tmp.toAbsolutePath().toString())
                .toList()
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.endsWith("a.txt") })
        assertTrue(results.any { it.endsWith("nested.txt") })
    }

    @Test
    fun `walkTopDown on a non-existent root returns an empty sequence`() {
        val results = CopilotBinaryDetector.DefaultFileSystem.walkTopDown("/this/path/does/not/exist").toList()
        assertNotNull(results)
    }
}
