package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets

class OpenAiCompatibleConnectionTesterProbeTest {
    private lateinit var restTemplate: RestTemplate

    private lateinit var server: MockRestServiceServer

    private val i18n = mock<I18nPort>()

    private lateinit var tester: OpenAiCompatibleConnectionTester

    @BeforeEach
    fun wireTester() {
        restTemplate = RestTemplate()
        server = MockRestServiceServer.createServer(restTemplate)
        tester = OpenAiCompatibleConnectionTester(restTemplate, i18n)
        whenever(i18n.t(eq("config.be_compatible_unreachable"), any<String>())).thenReturn("unreachable")
        whenever(i18n.t(eq("config.be_compatible_model_not_found"), any<String>())).thenReturn("model-not-found")
    }

    private fun spec(model: String? = null) = ProviderSpec.Compatible(apiKey = "sk-routed", baseUrl = "http://compatible.test/v1", model = model)

    private fun body(
        status: org.springframework.http.HttpStatus,
        payload: String,
    ): ResponseCreator = ResponseCreator { MockClientHttpResponse(payload.toByteArray(StandardCharsets.UTF_8), status) }

    @Test
    fun `the tester announces the compatible specification type`() {
        assertEquals(ProviderSpec.Compatible::class, tester.specType)
    }

    @Test
    fun `an empty model listing falls back to a chat probe that answers with a server error`() {
        server
            .expect { request ->
                assertEquals("http://compatible.test/v1/models", request.uri.toString())
                assertEquals(HttpMethod.GET, request.method)
            }.andRespond(withSuccess("""{"data":[]}""", MediaType.APPLICATION_JSON))
        server
            .expect { request ->
                assertEquals("http://compatible.test/v1/chat/completions", request.uri.toString())
                assertEquals(HttpMethod.POST, request.method)
            }.andRespond(withServerError())

        val result = tester.test(spec())

        assertTrue(result is TestResult.Failure)
        assertEquals("unreachable", result.payload["error"])
        server.verify()
    }

    @Test
    fun `a transport failure on the chat probe reports the endpoint as unreachable`() {
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond(withSuccess("""{"data":[]}""", MediaType.APPLICATION_JSON))
        server
            .expect { request -> assertEquals(HttpMethod.POST, request.method) }
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withException(
                    java.net.UnknownHostException("compatible.test nicht auflösbar"),
                ),
            )

        val result = tester.test(spec("gpt-mini"))

        assertTrue(result is TestResult.Failure)
        server.verify()
    }

    @Test
    fun `a model listing that answers with a not found keeps the requested model empty in the message`() {
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond(body(org.springframework.http.HttpStatus.NOT_FOUND, """{"error":{"code":"no_route"}}"""))
        server
            .expect { request -> assertEquals(HttpMethod.POST, request.method) }
            .andRespond(body(org.springframework.http.HttpStatus.NOT_FOUND, """{"error":{"code":"no_model"}}"""))

        val result = tester.test(spec())

        assertEquals("model-not-found", result.payload["error"])
        assertEquals("warning", result.payload["severity"])
    }

    @Test
    fun `a successful listing connects with the sorted model identifiers`() {
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond(withSuccess("""{"data":[{"id":"b"},{"id":"a"}]}""", MediaType.APPLICATION_JSON))

        val result = tester.test(spec())

        assertTrue(result is TestResult.Success)
        assertEquals(2, result.payload["modelCount"])
        assertEquals(listOf("a", "b"), result.payload["models"])
        assertEquals(false, result.payload["modelsUnavailable"])
    }
}
