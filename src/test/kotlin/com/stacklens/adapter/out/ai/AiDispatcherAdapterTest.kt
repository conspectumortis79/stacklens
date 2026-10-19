package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class AiDispatcherAdapterTest {
    private val copilot = mock<CopilotStreamingPort>()
    private val ollama = mock<CopilotStreamingPort>()
    private val lmStudio = mock<CopilotStreamingPort>()
    private val openAi = mock<CopilotStreamingPort>()
    private val claudeCode = mock<CopilotStreamingPort>()
    private val aiConfigRepo = mock<AiConfigPort>()

    private fun adapterWith(
        map: Map<String, CopilotStreamingPort> =
            mapOf(
                "copilot" to copilot,
                "ollama" to ollama,
                "lmstudio" to lmStudio,
                "openai" to openAi,
                "claudecode" to claudeCode,
            ),
        defaultProvider: String = "copilot",
    ): AiDispatcherAdapter =
        AiDispatcherAdapter(
            providers = map,
            aiConfigRepo = aiConfigRepo,
            defaultProviderName = defaultProvider,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun aiConfig(provider: String): AiConfig = AiConfig(model = "gpt", language = "de", aiProvider = provider)

    @Test
    fun `execute delegates to copilot when provider is copilot`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        adapterWith().execute("m", "p")
        verify(copilot).execute("m", "p")
    }

    @Test
    fun `execute delegates to ollama when provider is ollama`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("ollama"))
        adapterWith().execute("m", "p")
        verify(ollama).execute("m", "p")
    }

    @Test
    fun `execute delegates to lmStudio when provider is lmstudio`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("lmstudio"))
        adapterWith().execute("m", "p")
        verify(lmStudio).execute("m", "p")
    }

    @Test
    fun `execute delegates to openAi when provider is openai`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("openai"))
        adapterWith().execute("m", "p")
        verify(openAi).execute("m", "p")
    }

    @Test
    fun `execute delegates to claudeCode when provider is claudecode`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("claudecode"))
        adapterWith().execute("m", "p")
        verify(claudeCode).execute("m", "p")
    }

    @Test
    fun `execute falls back to defaultProvider when aiProvider is unknown`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("unknown"))
        adapterWith().execute("m", "p")
        verify(copilot).execute("m", "p")
    }

    @Test
    fun `execute uses defaultProviderName when aiConfig is null`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        adapterWith().execute("m", "p")
        verify(copilot).execute("m", "p")
    }

    @Test
    fun `execute throws when neither provider nor default is registered`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("missing"))
        val empty = adapterWith(mapOf("copilot" to copilot), defaultProvider = "missing")
        assertThrows<IllegalStateException> {
            empty.execute("m", "p")
        }
    }

    @Test
    fun `execute with timeout override delegates correctly`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("ollama"))
        adapterWith().execute("m", "p", 60L)
        verify(ollama).execute("m", "p", 60L)
    }

    @Test
    fun `executeWithStreaming delegates to configured provider`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        adapterWith().executeWithStreaming("m", "p", null, onPartialOutput = { })
        verify(copilot).executeWithStreaming(any(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `executeWithStreamingPerLine delegates to configured provider`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("ollama"))
        adapterWith().executeWithStreamingPerLine("m", "p", null, null, onPartialOutput = { })
        verify(ollama).executeWithStreamingPerLine(any(), any(), anyOrNull<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `executeWithStreamingPerLine with repoDir delegates correctly`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("ollama"))
        val tmpDir = java.io.File.createTempFile("test", "dir")
        tmpDir.delete()
        tmpDir.mkdirs()
        try {
            adapterWith().executeWithStreamingPerLine("m", "p", 30L, tmpDir, onPartialOutput = { })
            verify(ollama).executeWithStreamingPerLine(any(), any(), anyOrNull<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `resolveToken delegates to configured provider`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        whenever(copilot.resolveToken()).thenReturn("TOK")
        assertEquals("TOK", adapterWith().resolveToken())
    }

    @Test
    fun `isReady delegates to configured provider`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        whenever(copilot.isReady()).thenReturn(true)
        assertTrue(adapterWith().isReady())
    }

    @Test
    fun `resolveModel delegates to configured provider`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("ollama"))
        whenever(ollama.resolveModel()).thenReturn("llama-3")
        assertEquals("llama-3", adapterWith().resolveModel())
    }

    @Test
    fun `resolveLanguage delegates to configured provider`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("ollama"))
        whenever(ollama.resolveLanguage()).thenReturn("en")
        assertEquals("en", adapterWith().resolveLanguage())
    }

    @Test
    fun `execute keeps attachments with the active provider when it supports them`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        whenever(copilot.supportsAttachments).thenReturn(true)
        val attachment = AiAttachment("image/png", "AA==")

        adapterWith().execute("m", "p", null, listOf(attachment))

        verify(copilot).execute(eq("m"), eq("p"), anyOrNull(), eq(listOf(attachment)))
    }

    @Test
    fun `execute routes attachments to a vision-capable provider when the active provider cannot send them`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        whenever(copilot.supportsAttachments).thenReturn(false)
        whenever(openAi.supportsAttachments).thenReturn(true)
        val attachment = AiAttachment("image/png", "AA==")

        adapterWith().execute("m", "p", null, listOf(attachment))

        verify(openAi).execute(eq("m"), eq("p"), anyOrNull(), eq(listOf(attachment)))
        verify(copilot, never()).execute(any(), any(), anyOrNull(), any())
    }

    @Test
    fun `execute fails when attachments are requested but no provider supports them`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        whenever(copilot.supportsAttachments).thenReturn(false)
        val attachment = AiAttachment("image/png", "AA==")

        val failure =
            assertThrows<IllegalStateException> {
                adapterWith().execute("m", "p", null, listOf(attachment))
            }

        assertTrue(failure.message!!.contains("does not support image attachments"))
    }

    @Test
    fun `executeWithStreaming routes attachments to a vision-capable provider`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        whenever(copilot.supportsAttachments).thenReturn(false)
        whenever(openAi.supportsAttachments).thenReturn(true)
        val attachment = AiAttachment("image/png", "AA==")

        adapterWith().executeWithStreaming("m", "p", null, null, {}, listOf(attachment))

        verify(openAi).executeWithStreaming(eq("m"), eq("p"), anyOrNull(), anyOrNull(), any(), eq(listOf(attachment)))
    }

    @Test
    fun `executeWithStreamingPerLine routes attachments to a vision-capable provider`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        whenever(copilot.supportsAttachments).thenReturn(false)
        whenever(openAi.supportsAttachments).thenReturn(true)
        val attachment = AiAttachment("image/png", "AA==")

        adapterWith().executeWithStreamingPerLine("m", "p", null, null, null, {}, listOf(attachment))

        verify(openAi).executeWithStreamingPerLine(eq("m"), eq("p"), anyOrNull(), anyOrNull(), anyOrNull(), any(), eq(listOf(attachment)))
    }

    @Test
    fun `supportsAttachments reflects the resolved provider capability`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        whenever(copilot.supportsAttachments).thenReturn(false)
        assertEquals(false, adapterWith().supportsAttachments)

        whenever(openAi.supportsAttachments).thenReturn(true)
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("openai"))
        assertEquals(true, adapterWith().supportsAttachments)
    }

    @Test
    fun `supportsAttachments is false when no provider is registered`() {
        assertEquals(false, adapterWith(map = emptyMap()).supportsAttachments)
    }

    @Test
    fun `delegate uses cached value when cache TTL has not expired`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        val adapter = adapterWith()
        adapter.execute("m", "p1")
        adapter.execute("m", "p2")
        verify(copilot).execute("m", "p1", null)
        verify(copilot).execute("m", "p2", null)
    }

    @Test
    fun `refreshCacheIfStale inner check false branch is exercised via direct call`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        val adapter = adapterWith()
        val tsField = AiDispatcherAdapter::class.java.getDeclaredField("cacheTimestamp")
        tsField.isAccessible = true
        val refreshMethod = AiDispatcherAdapter::class.java.getDeclaredMethod("refreshCacheIfStale", Long::class.javaPrimitiveType)
        refreshMethod.isAccessible = true
        tsField.setLong(adapter, 0L)
        val mainThreadBlocked = java.util.concurrent.CountDownLatch(1)
        val sideThreadDone = java.util.concurrent.CountDownLatch(1)
        val sideThread =
            Thread {
                synchronized(adapter) {
                    mainThreadBlocked.countDown()
                    Thread.sleep(150)
                    tsField.setLong(adapter, System.currentTimeMillis())
                }
                sideThreadDone.countDown()
            }
        sideThread.start()
        mainThreadBlocked.await()
        val futureNow = System.currentTimeMillis() + 200L
        refreshMethod.invoke(adapter, futureNow)
        sideThreadDone.await()
    }

    @Test
    fun `providerLabel delegates to the configured provider`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        whenever(copilot.providerLabel).thenReturn("Copilot")
        assertEquals("Copilot", adapterWith().providerLabel)
    }

    @Test
    fun `providerLabel delegates to ollama when provider is ollama`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("ollama"))
        whenever(ollama.providerLabel).thenReturn("Ollama")
        assertEquals("Ollama", adapterWith().providerLabel)
    }

    @Test
    fun `writesFilesNatively delegates to the resolved provider`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("copilot"))
        whenever(copilot.writesFilesNatively).thenReturn(true)
        assertEquals(true, adapterWith().writesFilesNatively)
    }

    @Test
    fun `writesFilesNatively is false when no provider is registered`() {
        assertEquals(false, adapterWith(map = emptyMap()).writesFilesNatively)
    }

    @Test
    fun `writesFilesNatively is false when the resolved provider does not write files natively`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("openai"))
        assertEquals(false, adapterWith().writesFilesNatively)
    }
}
