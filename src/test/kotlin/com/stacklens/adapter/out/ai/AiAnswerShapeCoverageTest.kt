package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.HttpStatusCodeExceptionStubs
import com.stacklens.testsupport.PrivateInvocation
import net.sourceforge.tess4j.Tesseract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate
import java.io.File
import java.nio.file.Path

class AiAnswerShapeCoverageTest {
    private val i18n = FakeI18n()

    @Test
    fun `a responses payload whose content entries are not objects is skipped`() {
        val adapter = OpenAiAdapter(aiConfigRepo = mock<AiConfigPort>(), objectMapper = ObjectMapper(), promptLoader = mock<PromptReaderPort>())

        val payload = """{"output":[{"content":["plain string",{"text":"kept"},{"other":1}]}]}"""

        val answer = adapter.parseResponsesApiResponse(payload)

        assertEquals("kept", answer)
    }

    @Test
    fun `a model listing with holes ignores the entries that are not nodes`() {
        val raw = mock<JsonNode>()

        @Suppress("UNCHECKED_CAST")
        val holes = listOf<JsonNode?>(null).iterator() as MutableIterator<JsonNode>
        whenever(raw.iterator()).thenReturn(holes)
        val body = mock<JsonNode>()
        whenever(body.get("data")).thenReturn(raw)

        val models =
            PrivateInvocation.invoke(
                CopilotModelsConnectionTester(mock<RestTemplate>(), i18n),
                "parseModels",
                body,
            ) as List<*>

        assertTrue(models.isEmpty())
    }

    @Test
    fun `an ocr adapter without any recorded reason names the native library`() {
        val adapter = TesseractOcrAdapter()
        val delegate = TesseractOcrAdapter::class.java.getDeclaredField("tesseract\$delegate")
        delegate.isAccessible = true
        delegate.set(adapter, lazy { null as Tesseract? })

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.extractText(ByteArray(0), "image/png", "deu")
            }

        assertTrue(failure.message!!.contains("Native-Bibliothek konnte nicht geladen werden"), failure.message)
    }

    @Test
    fun `an ocr adapter with a recorded reason quotes it`() {
        val adapter = TesseractOcrAdapter()
        TesseractOcrAdapter::class.java.getDeclaredField("tesseract\$delegate").apply {
            isAccessible = true
            set(adapter, lazy { null as Tesseract? })
        }
        TesseractOcrAdapter::class.java.getDeclaredField("lastError").apply {
            isAccessible = true
            set(adapter, "libtesseract missing")
        }

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.extractText(ByteArray(0), "image/png", "deu")
            }

        assertTrue(failure.message!!.contains("libtesseract missing"), failure.message)
    }

    @Test
    fun `a rejected request without any body keeps the status diagnosis`() {
        val rejected = HttpStatusCodeExceptionStubs.withoutResponseBody(HttpStatus.SERVICE_UNAVAILABLE)
        val restTemplate = mock<RestTemplate>()
        whenever(restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<*>>(), any<Class<JsonNode>>()))
            .thenThrow(rejected)

        val result =
            OpenAiCompatibleConnectionTester(restTemplate, i18n)
                .test(ProviderSpec.Compatible(apiKey = "key", baseUrl = "http://localhost:9999", model = null))

        val failure = assertInstanceOf(TestResult.Failure::class.java, result)
        assertEquals(false, failure.payload["success"])
        assertFalse(failure.payload["error"].toString().contains("null"), failure.payload["error"].toString())
    }

    @Test
    fun `a heading that mixes a space with a path is still written`(
        @TempDir dir: Path,
    ) {
        val repo = dir.toFile()
        val response =
            "### docs/my file.md\n```md\n# content\n```\n\n" +
                "### windows\\path my file.md\n```\nsecond\n```\n\n" +
                "### plain file.txt\n```\nignored\n```\n"

        val written =
            RepoFileMarkdownExtractor.extract(response, repo, org.slf4j.LoggerFactory.getLogger(javaClass))

        assertTrue(File(repo, "docs/my file.md").exists())
        assertTrue(File(repo, "windows\\path my file.md").exists())
        assertEquals(2, written)
    }
}
