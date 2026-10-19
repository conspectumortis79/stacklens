package com.stacklens.coverage

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.ai.OpenAiCompatibleAdapter
import com.stacklens.adapter.out.ai.OpenAiCompatibleEndpoint
import com.stacklens.adapter.out.demo.DemoHostResolver
import com.stacklens.adapter.out.shared.MrSuggestionAdapter
import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.RexStageRunner
import com.stacklens.application.service.logging.ProposedFile
import com.stacklens.shared.SocketChannelSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

class DeferredAccessorCoverageTest {
    @Test
    fun `the ollama adapter publishes its provider label`() {
        val adapter =
            com.stacklens.adapter.out.ai.OllamaAdapter(
                aiConfigRepo = mock<AiConfigPort>(),
                objectMapper = ObjectMapper(),
                promptLoader = mock<PromptReaderPort>(),
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )

        assertEquals("Ollama", adapter.providerLabel)
    }

    @Test
    fun `the compatible adapter exposes its http executor`() {
        val adapter =
            OpenAiCompatibleAdapter(
                endpoint = mock<OpenAiCompatibleEndpoint>(),
                aiConfigRepo = mock<AiConfigPort>(),
                objectMapper = ObjectMapper(),
                promptLoader = mock<PromptReaderPort>(),
            )

        assertNotNull(adapter.aiHttpClientExecutor)
    }

    @Test
    fun `rex publishes the empty default test stages`() {
        assertEquals(0, RexStageRunner.DEFAULT_TEST_STAGES.size)
    }

    @Test
    fun `ivan exposes the make binary of its run context`() {
        val contextType =
            Class.forName("com.stacklens.application.service.IvanStageRunner\$IvanStageContext")
        val constructor = contextType.getDeclaredConstructor(AgentPipelineRun::class.java, StringBuilder::class.java)
        constructor.isAccessible = true
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "",
                repoPath = null,
                featureSlug = "",
                createdAt = 1L,
            )
        val context = constructor.newInstance(run, StringBuilder())
        val getter = contextType.getDeclaredMethod("getMakeBin")
        getter.isAccessible = true

        val failure = assertThrows(java.lang.reflect.InvocationTargetException::class.java) { getter.invoke(context) }
        assertEquals(UninitializedPropertyAccessException::class.java, failure.targetException::class.java)
    }

    @Test
    fun `the mr suggestion adapter keeps a overridable github api base url`() {
        val adapter =
            MrSuggestionAdapter()

        assertEquals("https://api.github.com", adapter.githubApiBaseUrl)
        adapter.githubApiBaseUrl = "https://github.example.com/api/v3"
        assertEquals("https://github.example.com/api/v3", adapter.githubApiBaseUrl)
    }

    @Test
    fun `a socket channel session publishes its three fields`() {
        val channel = mock<SocketChannel>()
        val selector = mock<Selector>()
        val session = SocketChannelSession(channel, selector, 42L)

        assertEquals(channel, session.channel)
        assertEquals(selector, session.selector)
        assertEquals(42L, session.deadline)
    }

    @Test
    fun `an import request keeps its defaults when the caller omits them`() {
        val request = KnowledgeImportRequest(archiveBytes = byteArrayOf(1, 2), password = null)

        assertEquals(KnowledgeImportMode.NEW_COPY, request.mode)
        assertEquals(null, request.targetKnowledgeBaseId)
        assertEquals(null, request.targetKnowledgeBaseName)
        assertEquals(true, request.includeVectors)
    }

    @Test
    fun `a proposed file is new by default`() {
        val file = ProposedFile(path = "a/B.kt", content = "body")

        assertEquals(null, file.description)
        assertEquals(true, file.isNew)
    }

    @Test
    fun `the demo seeder derives the host from the ambient docker flags`() {
        assertNotNull(DemoHostResolver().serviceHost())
    }

    @Test
    fun `the database scanner support owns a lazily named logger`() {
        val owner = Class.forName("com.stacklens.adapter.out.database.scanner.DatabaseScannerSupportKt")
        val getter = owner.declaredMethods.first { it.name == "getScannerLog" }
        getter.isAccessible = true

        assertNotNull(getter.invoke(null))
    }

    @Test
    fun `the embedded kotlinc invoker locates its runtime jars on demand`() {
        val invoker =
            com.stacklens.adapter.out.compiler.EmbeddedKotlincInvoker(
                i18n = mock<com.stacklens.application.port.out.i18n.I18nPort>(),
                kotlincResolver = mock<com.stacklens.adapter.out.compiler.KotlincResolver>(),
                fatJarBuilder =
                    com.stacklens.adapter.out.compiler
                        .FatJarBuilder(),
                javaExecutableProvider = { "java" },
            )

        assertNotNull(invoker.runtimeJarLocator)
        assertNotNull(invoker.jarLocator)
    }
}
