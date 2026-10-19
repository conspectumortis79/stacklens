package com.stacklens.application.service.prompt

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.prompt.PromptFileReadFailure
import com.stacklens.application.port.inbound.prompt.PromptFileReadResult
import com.stacklens.testsupport.FakeHostDirectoryPort
import com.stacklens.testsupport.FakePromptFileSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class PromptFileReadServiceTest {
    @TempDir
    private lateinit var tempDir: Path

    private val fileSystem = JavaFileSystemAdapter()

    private fun service(home: File = tempDir.toFile()) = PromptFileReadService(fileSystem, FakeHostDirectoryPort(home = home.absolutePath))

    private fun fileInHome(
        name: String,
        content: String,
    ): File = File(tempDir.toFile(), name).apply { writeText(content) }

    @Test
    fun `a file inside the home directory is returned with its content`() {
        val target = fileInHome("prompt.txt", "  hello  ")

        val result = service().readPromptFile(target.absolutePath)

        assertTrue(result is PromptFileReadResult.Success)
        assertEquals("  hello  ", (result as PromptFileReadResult.Success).content)
    }

    @Test
    fun `a path outside the home directory is denied`() {
        val outside = File(System.getProperty("java.io.tmpdir"), "stacklens-outside-${System.nanoTime()}.txt")
        outside.writeText("secret")
        try {
            val result = service().readPromptFile(outside.absolutePath)

            assertEquals(PromptFileReadFailure.ACCESS_DENIED, (result as PromptFileReadResult.Failed).failure)
        } finally {
            outside.delete()
        }
    }

    @Test
    fun `the home directory itself is not a readable file`() {
        val result = service().readPromptFile(tempDir.toString())

        assertEquals(PromptFileReadFailure.NOT_FOUND, (result as PromptFileReadResult.Failed).failure)
    }

    @Test
    fun `a missing file inside the home directory is reported as not found`() {
        val result = service().readPromptFile(File(tempDir.toFile(), "missing-${System.nanoTime()}.md").absolutePath)

        assertEquals(PromptFileReadFailure.NOT_FOUND, (result as PromptFileReadResult.Failed).failure)
    }

    @Test
    fun `a directory inside the home is not returned as a file`() {
        File(tempDir.toFile(), "sub").mkdirs()

        assertEquals(PromptFileReadFailure.NOT_FOUND, service().readPromptFile(File(tempDir.toFile(), "sub").absolutePath).failed().failure)
    }

    @Test
    fun `a file above the size budget is refused`() {
        val big = fileInHome("big.txt", "x")
        val fake = FakePromptFileSystem(base = fileSystem, reportedSize = TEN_MIB + 1)

        assertEquals(PromptFileReadFailure.TOO_LARGE, PromptFileReadService(fake, FakeHostDirectoryPort(home = tempDir.toString())).readPromptFile(big.absolutePath).failed().failure)
        assertEquals(big.canonicalFile.absolutePath, fake.lastSizeQuery?.canonicalFile?.absolutePath)
    }

    @Test
    fun `a file at exactly the size budget is still readable`() {
        val edge = fileInHome("edge.txt", "content")

        val result = PromptFileReadService(FakePromptFileSystem(base = fileSystem, reportedSize = TEN_MIB), FakeHostDirectoryPort(home = tempDir.toString())).readPromptFile(edge.absolutePath)

        assertEquals("content", result.succeeded())
    }

    @Test
    fun `a read failure is reported with the exception message`() {
        val target = fileInHome("broken.txt", "content")
        val service = PromptFileReadService(FakePromptFileSystem(base = fileSystem, readFailure = IllegalStateException("disk error")), FakeHostDirectoryPort(home = tempDir.toString()))

        val result = service.readPromptFile(target.absolutePath)

        val failed = result as PromptFileReadResult.Failed
        assertEquals(PromptFileReadFailure.READ_FAILED, failed.failure)
        assertEquals("disk error", failed.message)
    }

    @Test
    fun `a read failure without a message yields an empty detail`() {
        val target = fileInHome("silent.txt", "content")
        val service = PromptFileReadService(FakePromptFileSystem(base = fileSystem, readFailure = RuntimeException()), FakeHostDirectoryPort(home = tempDir.toString()))

        assertEquals("", service.readPromptFile(target.absolutePath).failed().message)
    }

    private fun PromptFileReadResult.failed(): PromptFileReadResult.Failed = this as PromptFileReadResult.Failed

    private fun PromptFileReadResult.succeeded(): String = (this as PromptFileReadResult.Success).content

    private companion object {
        private const val TEN_MIB: Long = 10L * 1024 * 1024
    }
}
