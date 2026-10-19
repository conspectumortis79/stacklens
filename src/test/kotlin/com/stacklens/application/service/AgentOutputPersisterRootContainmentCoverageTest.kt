package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.JavaPathPolicyAdapter
import com.stacklens.application.port.out.file.FileSystemPort
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path

class AgentOutputPersisterRootContainmentCoverageTest {
    private val persister = AgentOutputPersister(mock<FileSystemPort>(), JavaPathPolicyAdapter(mock<FileSystemPort>()))

    @Test
    fun `a file below the repository root is inside the root`(
        @TempDir root: Path,
    ) {
        assertTrue(inside(root.resolve("src/Main.kt").toFile(), root.toFile()))
    }

    @Test
    fun `the repository root itself counts as inside`(
        @TempDir root: Path,
    ) {
        assertTrue(inside(root.toFile(), root.toFile()))
    }

    @Test
    fun `a file outside the repository root is not inside`(
        @TempDir root: Path,
    ) {
        assertFalse(inside(File(root.toFile(), "../outside").canonicalFile, root.toFile().canonicalFile))
    }

    private fun inside(
        target: File,
        repoRoot: File,
    ): Boolean = PrivateInvocation.invoke(persister, "isInsideRoot", target, repoRoot) as Boolean
}
