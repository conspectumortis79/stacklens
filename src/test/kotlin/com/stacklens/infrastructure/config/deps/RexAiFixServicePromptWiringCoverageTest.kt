package com.stacklens.infrastructure.config.deps

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.classpath.ClasspathResourcePort
import com.stacklens.application.port.out.file.FileSystemPort
import com.stacklens.application.port.out.process.AgentProcessPort
import com.stacklens.application.service.RexAiFixService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class RexAiFixServicePromptWiringCoverageTest {
    @Test
    fun `the bean hands a prompt loader that reads the classpath template`(
        @TempDir dir: Path,
    ) {
        val stackLensConfig = mock<StackLensConfigPort>()
        whenever(stackLensConfig.dataDir).thenReturn(dir.toFile().absolutePath)
        val classpathResource = mock<ClasspathResourcePort>()
        whenever(classpathResource.readText("agents/pipeline/rex.md")).thenReturn("FIX-VORLAGE")
        val service =
            CopilotAgentPipelineDepsConfig()
                .rexAiFixService(
                    mock<CopilotStreamingPort>(),
                    mock<CopilotConfigPort>(),
                    mock<AgentProcessPort>(),
                    mock<FileSystemPort>(),
                    stackLensConfig,
                    classpathResource,
                )

        val loaded = promptLoaderOf(service)("rex", emptyMap())

        assertEquals("FIX-VORLAGE", loaded)
    }

    @Suppress("UNCHECKED_CAST")
    private fun promptLoaderOf(service: RexAiFixService): (String, Map<String, String>) -> String {
        val field = RexAiFixService::class.java.declaredFields.first { it.name == "loadPrompt" }
        field.isAccessible = true
        return field.get(service) as (String, Map<String, String>) -> String
    }
}
