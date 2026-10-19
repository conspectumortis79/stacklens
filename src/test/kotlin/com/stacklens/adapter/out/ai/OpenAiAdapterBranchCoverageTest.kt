package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class OpenAiAdapterBranchCoverageTest {
    private lateinit var server: MockWebServer

    private val aiConfigRepo: AiConfigPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    private val objectMapper = ObjectMapper()

    @TempDir
    lateinit var workDir: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system prompt")
        whenever(promptLoader.context(any())).thenReturn(PromptContext(mapOf("commontools.systemPrompt" to "system prompt")))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(envReader: (String) -> String? = { null }): OpenAiAdapter =
        OpenAiAdapter(
            defaultOpenAiBaseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            envReader = envReader,
        )

    private fun config(key: String?): AiConfig = AiConfig(openAiApiKey = key, model = "gpt-5.2", language = "de")

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body).setResponseCode(code))
    }

    @Test
    fun `resolve token falls back to the environment when the stored key is unusable`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "   "))
        assertEquals("sk-env", adapter { "sk-env" }.resolveToken())
        assertNull(adapter { "   " }.resolveToken())
        assertNull(adapter { null }.resolveToken())

        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("sk-env-two", adapter { "sk-env-two" }.resolveToken())
        assertNull(adapter { null }.resolveToken())

        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "sk-db"))
        assertEquals("sk-db", adapter { "sk-env" }.resolveToken())
        assertTrue(adapter().isReady())
    }

    @Test
    fun `execute fails without any api key`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-5.2", "prompt", null, emptyList())
            }
        assertTrue(failure.message!!.contains("No OpenAI API key set."))
    }

    @Test
    fun `execute reports failing responses and chat completions endpoints`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "sk-db"))
        enqueue("""{"error":"nope"}""", code = 500)
        assertThrows(IllegalStateException::class.java) {
            adapter().execute("gpt-5.2", "prompt", null, emptyList())
        }

        enqueue("""{"error":"nope"}""", code = 400)
        assertThrows(IllegalStateException::class.java) {
            adapter().execute("gpt-5.2", "prompt", 5L, listOf(AiAttachment("image/png", "aGk=")))
        }
    }

    @Test
    fun `execute succeeds for plain and vision payloads`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "sk-db"))
        enqueue("""{"output_text":"answer"}""")
        assertEquals("answer", adapter().execute("gpt-5.2", "prompt", null, emptyList()))

        enqueue(
            """{"choices":[{"message":{"content":"vision answer"}}]}""",
        )
        assertEquals(
            "vision answer",
            adapter().execute("gpt-5.2", "prompt", null, listOf(AiAttachment("image/png", "aGk="))),
        )
    }

    @Test
    fun `repo tool requests fail without a key and report http errors`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            adapter().executeWithStreamingPerLine("gpt-5.2", "prompt", 5L, workDir.toFile(), null, {})
        }

        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "sk-db"))
        enqueue("""{"error":"boom"}""", code = 503)
        assertThrows(IllegalStateException::class.java) {
            adapter().executeWithStreamingPerLine("gpt-5.2", "prompt", 5L, workDir.toFile(), null, {})
        }
    }

    @Test
    fun `streaming per line falls back when the repository directory is unusable`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "sk-db"))
        enqueue("""{"output_text":"plain"}""")
        assertEquals("plain", adapter().executeWithStreamingPerLine("gpt-5.2", "prompt", null, File(workDir.toFile(), "ghost").absoluteFile, null, {}))

        val aFile = File(workDir.toFile(), "regular.txt").apply { writeText("x") }
        enqueue("""{"output_text":"fallback"}""")
        assertEquals("fallback", adapter().executeWithStreamingPerLine("gpt-5.2", "prompt", null, aFile, null, {}))

        enqueue("""{"output_text":"none"}""")
        assertEquals("none", adapter().executeWithStreamingPerLine("gpt-5.2", "prompt", null, null, null, {}))
    }

    @Test
    fun `model listing reports http errors`() {
        enqueue("""{"error":"models unavailable"}""", code = 500)
        assertThrows(IllegalStateException::class.java) {
            adapter().fetchAvailableModels("sk-db")
        }
    }

    @Test
    fun `responses parsing ignores entries that are not objects`() {
        assertEquals(
            "abc",
            adapter().parseResponsesApiResponse(
                """{"output":["skip",{"content":[{"text":"a"},{"content":"bc"},{"text":42}]},"also skipped"]}""",
            ),
        )
        assertEquals("", adapter().parseResponsesApiResponse("""{"output":"not a list"}"""))
        assertEquals("", adapter().parseResponsesApiResponse("""{"output":[{"noContent":1}]}"""))
    }
}
