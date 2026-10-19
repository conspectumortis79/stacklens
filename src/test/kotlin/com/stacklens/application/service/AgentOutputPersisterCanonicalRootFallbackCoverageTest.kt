package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.JavaPathPolicyAdapter
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.file.FileSystemPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class AgentOutputPersisterCanonicalRootFallbackCoverageTest {
    private val filePersistence: FileSystemPort = mock()
    private val persister = AgentOutputPersister(filePersistence, JavaPathPolicyAdapter(filePersistence))

    @Test
    fun `a repository root without a resolvable canonical path is used as given`(
        @TempDir tmp: Path,
    ) {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = "/tmp/repo",
                featureSlug = "feat",
                createdAt = System.currentTimeMillis(),
            )
        run.finn.output = "### src/main.kt\n```kotlin\nfun main() {}\n```"
        var canonicalCalls = 0
        whenever(filePersistence.canonicalFile(any())).thenAnswer { invocation ->
            canonicalCalls++
            if (canonicalCalls == 1) throw RuntimeException("canonical path unavailable")
            (invocation.arguments[0] as File).canonicalFile
        }
        whenever(filePersistence.writeFile(any(), any())).thenAnswer { invocation ->
            val target = invocation.arguments[0] as File
            target.parentFile?.mkdirs()
            target.writeText(invocation.arguments[1] as String)
        }

        val result = persister.persist(run, tmp.toFile().canonicalFile)

        assertEquals(listOf("src/main.kt"), result.written)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(emptyList<String>(), result.deleted)
    }
}
