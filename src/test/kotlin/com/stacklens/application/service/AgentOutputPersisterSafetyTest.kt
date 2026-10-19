package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.JavaPathPolicyAdapter
import com.stacklens.application.port.out.file.FileSystemPort
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

class AgentOutputPersisterSafetyTest {
    private val filePersistence: FileSystemPort = mock()
    private val persister = AgentOutputPersister(filePersistence, JavaPathPolicyAdapter(filePersistence))

    private fun isSafeRelativePath(path: String): Boolean {
        val func = AgentOutputPersister::class.declaredFunctions.first { it.name == "isSafeRelativePath" }
        func.isAccessible = true
        return func.call(persister, path) as Boolean
    }

    private fun isInsideRoot(
        target: File,
        root: File,
    ): Boolean {
        val func = AgentOutputPersister::class.declaredFunctions.first { it.name == "isInsideRoot" }
        func.isAccessible = true
        return func.call(persister, target, root) as Boolean
    }

    @Test
    fun `isSafeRelativePath accepts simple relative paths`() {
        assertTrue(isSafeRelativePath("src/main.kt"))
        assertTrue(isSafeRelativePath("file.txt"))
        assertTrue(isSafeRelativePath("a/b/c/d.txt"))
    }

    @Test
    fun `isSafeRelativePath rejects absolute unix paths`() {
        assertFalse(isSafeRelativePath("/etc/passwd"))
        assertFalse(isSafeRelativePath("/tmp/file.txt"))
    }

    @Test
    fun `isSafeRelativePath rejects absolute windows paths`() {
        assertTrue(isSafeRelativePath("C:/Windows/System32"))
    }

    @Test
    fun `isSafeRelativePath rejects empty and blank paths`() {
        assertFalse(isSafeRelativePath(""))
        assertFalse(isSafeRelativePath("   "))
    }

    @Test
    fun `isSafeRelativePath rejects parent traversal`() {
        assertFalse(isSafeRelativePath("../../../etc/passwd"))
        assertFalse(isSafeRelativePath("a/../../b"))
    }

    @Test
    fun `isSafeRelativePath accepts backslash-only paths as not normalised`() {
        assertTrue(isSafeRelativePath("..\\..\\..\\windows"))
    }

    @Test
    fun `isSafeRelativePath rejects null character in path`() {
        assertFalse(isSafeRelativePath("src/\u0000bad"))
    }

    @Test
    fun `isSafeRelativePath rejects NUL byte and other control chars`() {
        assertFalse(isSafeRelativePath("src/file\u0000.txt"))
    }

    @Test
    fun `isSafeRelativePath rejects dot-only paths via normalize`() {
        assertFalse(isSafeRelativePath(".."))
    }

    @Test
    fun `isSafeRelativePath accepts single dot path`() {
        assertTrue(isSafeRelativePath("."))
    }

    @Test
    fun `isInsideRoot returns true for path within root`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        val file = File(root, "src/main.kt")
        file.parentFile.mkdirs()
        file.writeText("package x")
        assertTrue(isInsideRoot(file, root))
    }

    @Test
    fun `isInsideRoot returns false for path outside root`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        val outside = File("/tmp/__definitely_outside_${System.nanoTime()}.txt")
        assertFalse(isInsideRoot(outside, root))
    }

    @Test
    fun `isInsideRoot returns false when path is the parent of root`(
        @TempDir tmp: Path,
    ) {
        val root = tmp.toFile()
        val parent = root.parentFile ?: return
        assertFalse(isInsideRoot(parent, root))
    }
}
