package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.net.http.HttpClient
import java.nio.file.Path

class OpenAiCompatibleAdapterBranchCoverageTest {
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
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private val baseUrl: String
        get() = server.url("/").toString().trimEnd('/')

    private fun endpoint(): OpenAiCompatibleEndpoint =
        OpenAiCompatibleEndpoint(
            label = "Compatible",
            fallbackBaseUrl = baseUrl,
            defaultModel = "provider/model-one",
            apiKeyOf = { config -> config?.compatibleApiKey },
            baseUrlOf = { config -> config?.compatibleBaseUrl },
        )

    private fun adapter(): OpenAiCompatibleAdapter =
        OpenAiCompatibleAdapter(
            endpoint = endpoint(),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
        )

    private fun explicitAdapter(): OpenAiCompatibleAdapter =
        OpenAiCompatibleAdapter(
            endpoint = endpoint(),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            httpVersion = HttpClient.Version.HTTP_1_1,
            httpSendRetry = HttpSendRetry(),
            aiHttpClientExecutor = AiHttpClientExecutor(),
        )

    private fun config(key: String?): AiConfig = AiConfig(compatibleApiKey = key, compatibleBaseUrl = baseUrl, model = "provider/model-one")

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body).setResponseCode(code))
    }

    @Test
    fun `defaults and explicit wiring build equivalent clients`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "ck"))
        enqueue("""{"choices":[{"message":{"content":"plain answer"}}]}""")
        assertEquals("plain answer", adapter().execute("provider/model-one", "prompt", null, emptyList()))

        enqueue("""{"choices":[{"message":{"content":"wired answer"}}]}""")
        assertEquals("wired answer", explicitAdapter().execute("provider/model-one", "prompt", 7L, emptyList()))
    }

    @Test
    fun `chat requests report failing status codes`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "ck"))
        enqueue("""{"error":"gateway"}""", code = 502)
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("provider/model-one", "prompt", null, emptyList())
            }
        assertTrue(failure.message!!.contains("Compatible error (HTTP 502)"))
    }

    @Test
    fun `request bodies embed attachments as data uris`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "ck"))
        val body =
            adapter().buildChatRequestBody(
                "provider/model-one",
                "describe the image",
                listOf(AiAttachment("image/png", "aGk="), AiAttachment("image/jpeg", "aGk=")),
            )
        assertTrue(body.contains("\"image_url\""))
        assertTrue(body.contains("data:image/png;base64,aGk="))
        assertTrue(body.contains("data:image/jpeg;base64,aGk="))

        val plain = adapter().buildChatRequestBody("provider/model-one", "text only", emptyList())
        assertTrue(plain.contains("\"content\":\"text only\""))
    }

    @Test
    fun `streaming per line falls back for unusable repository directories`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "ck"))
        val aFile = File(workDir.toFile(), "regular.txt").apply { writeText("x") }
        enqueue("""{"choices":[{"message":{"content":"file dir"}}]}""")
        assertEquals("file dir", adapter().executeWithStreamingPerLine("provider/model-one", "prompt", 6L, aFile, null, {}))

        enqueue("""{"choices":[{"message":{"content":"missing dir"}}]}""")
        assertEquals(
            "missing dir",
            adapter().executeWithStreamingPerLine("provider/model-one", "prompt", 6L, File(workDir.toFile(), "ghost").absoluteFile, null, {}),
        )

        enqueue("""{"choices":[{"message":{"content":"no dir"}}]}""")
        assertEquals("no dir", adapter().executeWithStreamingPerLine("provider/model-one", "prompt", null, null, null, {}))
    }

    @Test
    fun `repo tool runs extract markdown file blocks from the answer`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "ck"))
        enqueue(
            """{"choices":[{"message":{"content":"## notes.md\n```markdown\n# written\n```"}}]}""",
        )
        val output = adapter().executeWithStreamingPerLine("provider/model-one", "prompt", 6L, workDir.toFile(), null, {})
        assertTrue(output.contains("# written"))
        assertEquals("# written\n", File(workDir.toFile(), "notes.md").readText())
    }

    @Test
    fun `repo tool runs keep answers without file blocks unchanged`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "ck"))
        enqueue("""{"choices":[{"message":{"content":"just prose"}}]}""")
        assertEquals("just prose", adapter().executeWithStreamingPerLine("provider/model-one", "prompt", 6L, workDir.toFile(), null, {}))
    }

    @Test
    fun `missing api keys abort before any request`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = null))
        assertThrows(IllegalStateException::class.java) {
            adapter().execute("provider/model-one", "prompt", null, emptyList())
        }
        assertThrows(IllegalStateException::class.java) {
            adapter().executeWithStreamingPerLine("provider/model-one", "prompt", null, workDir.toFile(), null, {})
        }
    }
}
