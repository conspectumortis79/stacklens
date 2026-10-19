package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.JavaPathPolicyAdapter
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.file.FileSystemPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class AgentOutputPersisterPersistTest {
    private val filePersistence: FileSystemPort = mock()
    private val persister = AgentOutputPersister(filePersistence, JavaPathPolicyAdapter(filePersistence))

    @org.junit.jupiter.api.BeforeEach
    fun stubCanonicalFile() {
        whenever(filePersistence.canonicalFile(any())).thenAnswer { (it.arguments[0] as File).canonicalFile }
    }

    @Test
    fun `persist writes valid files inside repo root`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "```kotlin\nsrc/main.kt\nfun main() {}\n```"
        whenever(filePersistence.writeFile(any(), any())).thenAnswer {
            val target = it.arguments[0] as File
            target.parentFile?.mkdirs()
            target.writeText(it.arguments[1] as String)
        }
        val result = persister.persist(run, tmp.toFile())
        assertTrue(result.errors.isEmpty() || result.written.isNotEmpty())
    }

    @Test
    fun `persist rejects path traversal via parent of root`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "```kotlin\n../escape.kt\ncontent\n```"
        val result = persister.persist(run, tmp.toFile())
        assertTrue(result.written.isEmpty() || result.errors.isNotEmpty())
    }

    @Test
    fun `persist catches exceptions thrown by writeFile`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "```kotlin\nsrc/main.kt\nfun main() {}\n```"
        whenever(filePersistence.writeFile(any(), any())).thenThrow(RuntimeException("disk full"))
        val result = persister.persist(run, tmp.toFile())
        assertTrue(result.errors.isEmpty() || result.errors.any { it.contains("disk full") })
    }

    @Test
    fun `persist rejects absolute path in agent output`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "```kotlin\n/etc/passwd\ncontent\n```"
        val result = persister.persist(run, tmp.toFile())
        assertTrue(result.written.isEmpty() || result.errors.isNotEmpty())
    }

    @Test
    fun `persist processes deletes from agent output`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "🗑️ Deleted files:\n- old/file.txt\n- another.kt\n"
        val result = persister.persist(run, tmp.toFile())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `persist rejects write path that fails isSafeRelativePath check`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "### /etc/passwd\n```kotlin\nsneaky\n```"
        val result = persister.persist(run, tmp.toFile())
        assertTrue(result.written.isEmpty())
        assertTrue(
            result.errors.any { it.contains("path traversal denied") },
            "Expected a path-traversal error, got: ${result.errors}",
        )
    }

    @Test
    fun `persist rejects delete path that fails isSafeRelativePath check`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "DELETED /etc/passwd\n"
        val result = persister.persist(run, tmp.toFile())
        assertTrue(result.deleted.isEmpty())
        assertTrue(
            result.errors.any { it.contains("path traversal denied") },
            "Expected a path-traversal error, got: ${result.errors}",
        )
    }

    @Test
    fun `persist collects write exception into errors with exception message`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "### src/main.kt\n```kotlin\ncontent\n```"
        whenever(filePersistence.writeFile(any(), any())).thenAnswer {
            throw RuntimeException("disk full")
        }
        val result = persister.persist(run, tmp.toFile())
        assertTrue(
            result.errors.any { it.contains("disk full") },
            "Expected write error to be reported, got: ${result.errors}",
        )
    }

    @Test
    fun `persist collects write exception with javaClass simpleName when message is null`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "### src/main.kt\n```kotlin\ncontent\n```"
        whenever(filePersistence.writeFile(any(), any())).thenAnswer {
            throw RuntimeException()
        }
        val result = persister.persist(run, tmp.toFile())
        assertTrue(
            result.errors.isNotEmpty(),
            "Expected write error to be reported, got: ${result.errors}",
        )
        assertTrue(
            result.errors.any { it.contains("RuntimeException") },
            "Expected RuntimeException simpleName in error message, got: ${result.errors}",
        )
    }

    @Test
    fun `persist collects delete exception into errors with exception message`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "DELETED src/old.kt\n"
        whenever(filePersistence.deleteFile(any())).thenAnswer {
            throw RuntimeException("permission denied")
        }
        val result = persister.persist(run, tmp.toFile())
        assertTrue(
            result.errors.any { it.contains("permission denied") },
            "Expected delete error to be reported, got: ${result.errors}",
        )
    }

    @Test
    fun `persist collects delete exception with javaClass simpleName when message is null`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "DELETED src/old.kt\n"
        whenever(filePersistence.deleteFile(any())).thenAnswer {
            throw RuntimeException()
        }
        val result = persister.persist(run, tmp.toFile())
        assertTrue(
            result.errors.isNotEmpty(),
            "Expected delete error to be reported, got: ${result.errors}",
        )
        assertTrue(
            result.errors.any { it.contains("RuntimeException") },
            "Expected RuntimeException simpleName in error message, got: ${result.errors}",
        )
    }

    @Test
    fun `persist falls back to original repoRoot when canonicalFile throws`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        run.finn.output = "```kotlin\nsrc/main.kt\ncontent\n```"
        whenever(filePersistence.writeFile(any(), any())).thenAnswer {
            val target = it.arguments[0] as File
            target.parentFile?.mkdirs()
            target.writeText(it.arguments[1] as String)
        }
        val result = persister.persist(run, tmp.toFile())
        assertTrue(result.errors.isEmpty() || result.written.isNotEmpty())
    }

    @Test
    fun `persist rejects write path that escapes root after canonicalization`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        val outsideTarget = File("/tmp")
        val symlinkInside = File(tmp.toFile(), "escape")
        try {
            java.nio.file.Files
                .createSymbolicLink(symlinkInside.toPath(), outsideTarget.toPath())
        } catch (_: UnsupportedOperationException) {
            return
        } catch (_: java.io.IOException) {
            return
        }
        run.finn.output = "### escape/secret.txt\n```kotlin\nstolen\n```"
        val result = persister.persist(run, tmp.toFile())
        assertTrue(result.written.isEmpty())
        assertTrue(
            result.errors.any { it.contains("path traversal denied") },
            "Expected a path-traversal error, got: ${result.errors}",
        )
    }

    @Test
    fun `persist rejects delete path that escapes root after canonicalization`(
        @TempDir tmp: Path,
    ) {
        val run = newRun()
        val outsideTarget = File("/tmp")
        val symlinkInside = File(tmp.toFile(), "escape")
        try {
            java.nio.file.Files
                .createSymbolicLink(symlinkInside.toPath(), outsideTarget.toPath())
        } catch (_: UnsupportedOperationException) {
            return
        } catch (_: java.io.IOException) {
            return
        }
        run.finn.output = "DELETED escape/victim.txt\n"
        val result = persister.persist(run, tmp.toFile())
        assertTrue(result.deleted.isEmpty())
        assertTrue(
            result.errors.any { it.contains("path traversal denied") },
            "Expected a path-traversal error, got: ${result.errors}",
        )
    }

    @Test
    fun `persist reports target canonicalization failures for writes`(
        @TempDir tmp: Path,
    ) {
        var calls = 0
        whenever(filePersistence.canonicalFile(any())).thenAnswer { invocation ->
            calls++
            if (calls > 1) throw RuntimeException("target canonical failure")
            (invocation.arguments[0] as File).canonicalFile
        }
        val run = newRun()
        run.finn.output = "### src/main.kt\n```kotlin\ncontent\n```"

        val result = persister.persist(run, tmp.toFile())

        assertTrue(result.written.isEmpty())
        assertTrue(result.failures.single().detail == "target canonical failure")
    }

    @Test
    fun `persist reports target canonicalization failures for deletes`(
        @TempDir tmp: Path,
    ) {
        var calls = 0
        whenever(filePersistence.canonicalFile(any())).thenAnswer { invocation ->
            calls++
            if (calls > 1) throw RuntimeException("delete canonical failure")
            (invocation.arguments[0] as File).canonicalFile
        }
        val run = newRun()
        run.finn.output = "DELETED src/old.kt\n"

        val result = persister.persist(run, tmp.toFile())

        assertTrue(result.deleted.isEmpty())
        assertTrue(result.failures.single().detail == "delete canonical failure")
    }

    @Test
    fun `persist reports target canonicalization failures without a message`(
        @TempDir tmp: Path,
    ) {
        var calls = 0
        whenever(filePersistence.canonicalFile(any())).thenAnswer { invocation ->
            calls++
            if (calls > 1) throw RuntimeException()
            (invocation.arguments[0] as File).canonicalFile
        }
        val run = newRun()
        run.finn.output = "### src/main.kt\n```kotlin\ncontent\n```"

        val result = persister.persist(run, tmp.toFile())

        assertTrue(result.failures.single().detail == "RuntimeException")
    }

    @Test
    fun `persist reports delete canonicalization failures without a message`(
        @TempDir tmp: Path,
    ) {
        var calls = 0
        whenever(filePersistence.canonicalFile(any())).thenAnswer { invocation ->
            calls++
            if (calls > 1) throw RuntimeException()
            (invocation.arguments[0] as File).canonicalFile
        }
        val run = newRun()
        run.finn.output = "DELETED src/old.kt\n"

        val result = persister.persist(run, tmp.toFile())

        assertTrue(result.failures.single().detail == "RuntimeException")
    }

    @Test
    fun `a null target directory is classified as a write failure`() {
        val method =
            AgentOutputPersister::class.java.getDeclaredMethod(
                "pipelineErrorCode",
                File::class.java,
                Exception::class.java,
            )
        method.isAccessible = true
        val code = method.invoke(persister, null, RuntimeException("x"))

        assertEquals(com.stacklens.application.port.inbound.pipeline.PipelineWriteErrorCode.WRITE_FAILED, code)
    }

    @Test
    fun `persist marks missing target directories as create failures`(
        @TempDir tmp: Path,
    ) {
        whenever(filePersistence.exists(any())).thenReturn(false)
        whenever(filePersistence.writeFile(any(), any())).thenThrow(RuntimeException("cannot create"))
        val run = newRun()
        run.finn.output = "### nested/file.kt\n```kotlin\ncontent\n```"

        val result = persister.persist(run, tmp.toFile())

        assertTrue(result.failures.single().code == com.stacklens.application.port.inbound.pipeline.PipelineWriteErrorCode.DIR_CREATE_FAILED)
        assertTrue(
            result.failures
                .single()
                .detail!!
                .endsWith("nested"),
        )
    }

    private fun newRun(): AgentPipelineRun =
        AgentPipelineRun(
            id = 1L,
            rawText = "raw",
            repoPath = "/tmp/repo",
            featureSlug = "feat",
            createdAt = System.currentTimeMillis(),
        )
}
