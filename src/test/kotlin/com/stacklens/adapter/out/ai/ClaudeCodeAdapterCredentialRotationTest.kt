package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class ClaudeCodeAdapterCredentialRotationTest {
    private lateinit var server: MockWebServer

    private val aiConfigRepo: AiConfigPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(claudeCodeApiKey = "sk-ant-api03-broke", model = "claude-sonnet-5"))
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system prompt")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun lookupWith(vararg sources: () -> String?): ClaudeCodeTokenLookupPort =
        object : ClaudeCodeTokenLookupPort {
            override fun resolveToken(): String? = sources.firstNotNullOfOrNull { runCatching { it() }.getOrNull() }

            override fun tokenCandidateSources(): List<() -> String?> = sources.toList()
        }

    private fun adapter(lookup: ClaudeCodeTokenLookupPort = ClaudeCodeTestSupport.lookupOfStoredKey(aiConfigRepo)): ClaudeCodeAdapter =
        ClaudeCodeAdapter(
            defaultBaseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            tokenLookup = lookup,
            settings = ClaudeCodeTestSupport.settings(),
        )

    private fun enqueue(
        status: Int,
        body: String,
    ) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
    }

    private val creditError = """{"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API."}}"""

    private fun answer(text: String) = """{"stop_reason":"end_turn","content":[{"type":"text","text":"$text"}]}"""

    private fun requests(): List<RecordedRequest> = generateSequence { server.takeRequest(5, TimeUnit.SECONDS) }.toList()

    @Test
    fun `a rejected credential rotates to the next stored credential`() {
        val lookup = lookupWith({ "sk-ant-api03-broke" }, { "sk-ant-oat01-login" })
        enqueue(403, """{"type":"error","error":{"type":"permission_error","message":"this key has no access"}}""")
        enqueue(200, answer("recovered"))

        assertEquals("recovered", adapter(lookup).execute("claude-sonnet-5", "prompt", 10L, emptyList()))

        val seen = requests()
        assertEquals(2, seen.size)
        assertEquals("sk-ant-api03-broke", seen[0].getHeader("x-api-key"))
        assertEquals("Bearer sk-ant-oat01-login", seen[1].getHeader("Authorization"))
    }

    @Test
    fun `the credential that worked is reused first on the next call`() {
        val lookup = lookupWith({ "sk-ant-api03-broke" }, { "sk-ant-oat01-login" })
        val subject = adapter(lookup)
        enqueue(403, """{"type":"error","error":{"type":"permission_error","message":"this key has no access"}}""")
        enqueue(200, answer("first"))
        subject.execute("claude-sonnet-5", "prompt", 10L, emptyList())
        requests()

        enqueue(200, answer("second"))
        assertEquals("second", subject.execute("claude-sonnet-5", "prompt", 10L, emptyList()))

        val next = requests()
        assertEquals(1, next.size)
        assertEquals("Bearer sk-ant-oat01-login", next[0].getHeader("Authorization"))
    }

    @Test
    fun `an exhausted credit balance becomes a translated failure`() {
        val lookup = lookupWith({ "sk-ant-api03-one" }, { "sk-ant-oat01-two" })
        enqueue(400, creditError)
        enqueue(400, creditError)

        val failure =
            assertThrows(StackLensFailureException::class.java) {
                adapter(lookup).execute("claude-sonnet-5", "prompt", 10L, emptyList())
            }

        val exhausted = failure.failure as StackLensFailure.AiCreditsExhausted
        assertEquals(2, exhausted.attemptedCredentials)
        assertTrue(exhausted.detail.contains("credit balance is too low"), exhausted.detail)
        assertEquals(2, requests().size)
    }

    @Test
    fun `a request error that is not credential related is raised unchanged without rotating`() {
        val lookup = lookupWith({ "sk-ant-api03-one" }, { "sk-ant-oat01-two" })
        enqueue(400, """{"type":"error","error":{"type":"invalid_request_error","message":"max_tokens: 200000 > 128000"}}""")

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter(lookup).execute("claude-sonnet-5", "prompt", 10L, emptyList())
            }

        assertTrue(failure.message!!.contains("max_tokens: 200000 > 128000"), failure.message)
        assertEquals(1, requests().size)
    }

    @Test
    fun `a failing credential source is skipped and the next one answers`() {
        val lookup =
            lookupWith({
                error("keychain unavailable")
                null
            }, { "sk-ant-oat01-login" })
        enqueue(200, answer("from login"))

        assertEquals("from login", adapter(lookup).execute("claude-sonnet-5", "prompt", 10L, emptyList()))
        assertEquals("Bearer sk-ant-oat01-login", requests().single().getHeader("Authorization"))
    }

    @Test
    fun `without any credential the call still reports the missing key`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter(lookupWith()).execute("claude-sonnet-5", "prompt", 10L, emptyList())
            }

        assertEquals("No Claude Code API key set.", failure.message)
    }

    @Test
    fun `the tool loop shares the credential rotation`() {
        val lookup = lookupWith({ "sk-ant-api03-broke" }, { "sk-ant-oat01-login" })
        enqueue(403, """{"type":"error","error":{"type":"permission_error","message":"forbidden"}}""")
        enqueue(200, answer("tool loop ok"))

        val subject = adapter(lookup)
        val body = subject.buildToolRequestBody("claude-sonnet-5", listOf(mapOf("role" to "user", "content" to "hi")))

        assertEquals("""{"stop_reason":"end_turn","content":[{"type":"text","text":"tool loop ok"}]}""", subject.toolLoop.transport.send(body, 10L))
        assertEquals("Bearer sk-ant-oat01-login", requests().last().getHeader("Authorization"))
    }

    @Test
    fun `rejections classify only auth and billing statuses as credential faults`() {
        assertTrue(ClaudeCodeRejections.isCredentialFault(401, "{}"))
        assertTrue(ClaudeCodeRejections.isCredentialFault(402, "{}"))
        assertTrue(ClaudeCodeRejections.isCredentialFault(403, "{}"))
        assertTrue(ClaudeCodeRejections.isCredentialFault(400, creditError))
        assertTrue(ClaudeCodeRejections.isBilling(402, "{}"))
        assertTrue(ClaudeCodeRejections.isBilling(400, creditError))
        assertTrue(!ClaudeCodeRejections.isCredentialFault(400, """{"message":"adaptive thinking is not supported"}"""))
        assertTrue(!ClaudeCodeRejections.isCredentialFault(529, """{"message":"overloaded"}"""))
    }
}
