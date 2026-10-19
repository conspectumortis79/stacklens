package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
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

class ClaudeCodeAdapterBranchCoverageTest {
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

    private fun adapter(storedKey: String? = null): ClaudeCodeAdapter {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(if (storedKey == null) null else AiConfig(claudeCodeApiKey = storedKey))
        return ClaudeCodeAdapter(
            defaultBaseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            tokenLookup = ClaudeCodeTestSupport.tokenLookup(storedKey),
            settings = ClaudeCodeTestSupport.settings(),
        )
    }

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body).setResponseCode(code))
    }

    private fun answer(text: String): String = """{"content":[{"type":"text","text":"$text"}]}"""

    @Test
    fun `the adapter delegates token resolution and trims the result`() {
        assertEquals("sk-stored", adapter(storedKey = "  sk-stored  ").resolveToken())
        assertNull(adapter(storedKey = "   ").resolveToken())
        assertTrue(adapter(storedKey = "sk-stored").isReady())
        assertNull(adapter().resolveToken())
        assertTrue(!adapter().isReady())
    }

    @Test
    fun `execute aborts without a token`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("claude-sonnet", "prompt", null, emptyList())
            }
        assertTrue(failure.message!!.contains("No Claude Code API key set."))
    }

    @Test
    fun `execute accepts api key responses and bearer tokens`() {
        enqueue(answer("plain answer"))
        assertEquals("plain answer", adapter(storedKey = "sk-ant-api03-abcdefgh").execute("claude-sonnet", "prompt", null, emptyList()))

        enqueue(answer("bearer answer"))
        assertEquals("bearer answer", adapter(storedKey = "sk-ant-oat01-abc").execute("claude-sonnet", "prompt", 6L, emptyList()))
    }

    @Test
    fun `bearer only tokens report failing responses`() {
        enqueue("""{"error":"denied"}""", code = 403)
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter(storedKey = "sk-ant-oat01-abc").execute("claude-sonnet", "prompt", null, emptyList())
            }
        assertTrue(failure.message!!.contains("ClaudeCode error (HTTP 403)"))
    }

    @Test
    fun `api key failures retry with bearer and mask the token`() {
        enqueue("""{"error":"invalid x-api-key"}""", code = 401)
        enqueue(answer("after retry"))
        assertEquals("after retry", adapter(storedKey = "sk-ant-api03-abcdefgh").execute("claude-sonnet", "prompt", null, emptyList()))

        enqueue("""{"error":"invalid x-api-key"}""", code = 401)
        enqueue("""{"error":"still denied"}""", code = 403)
        assertThrows(IllegalStateException::class.java) {
            adapter(storedKey = "sk-short-key-value").execute("claude-sonnet", "prompt", null, emptyList())
        }

        enqueue("""{"error":"invalid x-api-key"}""", code = 401)
        enqueue("""{"error":"still denied"}""", code = 500)
        assertThrows(IllegalStateException::class.java) {
            adapter(storedKey = "tiny").execute("claude-sonnet", "prompt", null, emptyList())
        }

        enqueue("""{"error":"server error"}""", code = 503)
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter(storedKey = "abcdefghijklmno").execute("claude-sonnet", "prompt", null, emptyList())
            }
        assertTrue(failure.message!!.contains("HTTP 503"))
    }

    @Test
    fun `streaming per line falls back when the repository directory is unusable`() {
        enqueue(answer("no tools"))
        val aFile = File(workDir.toFile(), "regular.txt").apply { writeText("x") }
        assertEquals(
            "no tools",
            adapter(storedKey = "sk-ant-api03-abcdefgh")
                .executeWithStreamingPerLine("claude-sonnet", "prompt", 6L, aFile, null, {}),
        )
    }

    @Test
    fun `repo tool requests abort without a token`() {
        assertThrows(IllegalStateException::class.java) {
            adapter().executeWithStreamingPerLine("claude-sonnet", "prompt", 6L, workDir.toFile(), null, {})
        }
    }

    @Test
    fun `repo tool runs extract markdown file blocks from the answer`() {
        enqueue("""{"content":[{"type":"text","text":"## notes.md\n```markdown\n# written\n```"}]}""")
        val output =
            adapter(storedKey = "sk-ant-api03-abcdefgh")
                .executeWithStreamingPerLine("claude-sonnet", "prompt", 6L, workDir.toFile(), null, {})
        assertTrue(output.contains("# written"))
        assertEquals("# written\n", File(workDir.toFile(), "notes.md").readText())
    }

    @Test
    fun `streaming responses are forwarded to the partial consumer`() {
        enqueue(answer("streamed"))
        val parts = mutableListOf<String>()
        val result =
            adapter(storedKey = "sk-ant-api03-abcdefgh")
                .executeWithStreaming("claude-sonnet", "prompt", null, { false }, { parts += it }, emptyList())
        assertEquals("streamed", result)
        assertEquals(listOf("streamed"), parts)
    }

    @Test
    fun `message parsing tolerates unexpected payloads`() {
        val adapter = adapter(storedKey = "sk-ant-api03-abcdefgh")
        assertEquals("", adapter.parseMessagesResponse(""))
        assertEquals("", adapter.parseMessagesResponse("not json"))
        assertEquals("", adapter.parseMessagesResponse("""{"content":"missing list"}"""))
        assertEquals("", adapter.parseMessagesResponse("""{"content":["scalar",{"type":"tool_use","text":"ignored"}]}"""))
        assertEquals("kept", adapter.parseMessagesResponse("""{"content":[{"type":"text","text":"kept"}]}"""))
    }
}
