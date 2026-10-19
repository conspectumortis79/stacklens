package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestTemplate

class CopilotModelsConnectionTesterTest {
    private lateinit var restTemplate: RestTemplate
    private lateinit var server: MockRestServiceServer
    private val i18n: I18nPort = mock()
    private lateinit var tester: CopilotModelsConnectionTester

    @BeforeEach
    fun setUp() {
        restTemplate = RestTemplate()
        server = MockRestServiceServer.createServer(restTemplate)
        tester = CopilotModelsConnectionTester(restTemplate, i18n)
    }

    @Test
    fun `test returns Failure when the token is null`() {
        whenever(i18n.t(any(), any())).thenReturn("no token")
        val result = tester.test(ProviderSpec.CopilotModels(token = null))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns Failure when the token is blank`() {
        whenever(i18n.t(any(), any())).thenReturn("no token")
        val result = tester.test(ProviderSpec.CopilotModels(token = "   "))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns Success on the first Copilot host with model picker enabled`() {
        server
            .expect { request ->
                assertEquals(HttpMethod.GET, request.method)
                assertTrue(request.uri.path.endsWith("/user"))
            }.andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"login":"alice","id":1}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server
            .expect { request ->
                assertTrue(request.uri.host == "api.business.githubcopilot.com")
                assertTrue(request.uri.path.endsWith("/models"))
            }.andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"data":[
                      {"id":"gpt-4o","name":"GPT-4o","model_picker_enabled":true}
                    ]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = tester.test(ProviderSpec.CopilotModels("ghp_abcdef"))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
        val map = (result as AiConnectionTestPort.TestResult.Success).payload
        assertEquals(1, map["modelCount"])
        @Suppress("UNCHECKED_CAST")
        val models = map["models"] as List<Map<String, String>>
        assertEquals(1, models.size)
        assertEquals("gpt-4o", models[0]["id"])
        assertEquals("alice", map["githubUser"])
        server.verify()
    }

    @Test
    fun `test falls back to the individual host when the business host returns no models`() {
        server
            .expect { request -> assertTrue(request.uri.path.endsWith("/user")) }
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"login":"bob","id":2}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server
            .expect { request ->
                assertTrue(request.uri.host == "api.business.githubcopilot.com")
            }.andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"data":[]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server
            .expect { request ->
                assertTrue(request.uri.host == "api.individual.githubcopilot.com")
            }.andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"data":[
                      {"id":"claude","name":"Claude","model_picker_enabled":true}
                    ]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = tester.test(ProviderSpec.CopilotModels("ghp_abcdef"))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
        val map = (result as AiConnectionTestPort.TestResult.Success).payload
        @Suppress("UNCHECKED_CAST")
        assertEquals("claude", ((map["models"] as List<Map<String, String>>)[0])["id"])
    }

    @Test
    fun `test filters out models where model_picker_enabled is not true`() {
        server
            .expect { request -> assertTrue(request.uri.path.endsWith("/user")) }
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"id":3}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server
            .expect { request -> assertTrue(request.uri.host == "api.business.githubcopilot.com") }
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"data":[
                      {"id":"hidden","name":"Hidden","model_picker_enabled":false},
                      {"id":"gpt-4o","name":"GPT-4o","model_picker_enabled":true}
                    ]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        val result = tester.test(ProviderSpec.CopilotModels("ghp_abcdef"))
        val map = (result as AiConnectionTestPort.TestResult.Success).payload

        @Suppress("UNCHECKED_CAST")
        val models = map["models"] as List<Map<String, String>>
        assertEquals(1, models.size)
        assertEquals("gpt-4o", models[0]["id"])
    }

    @Test
    fun `test returns Failure when both Copilot hosts return no usable models`() {
        whenever(i18n.t(any(), any())).thenReturn("copilot down")
        server
            .expect { request -> assertTrue(request.uri.path.endsWith("/user")) }
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"id":4}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        for (host in listOf("api.business.githubcopilot.com", "api.individual.githubcopilot.com")) {
            server
                .expect { request -> assertTrue(request.uri.host == host) }
                .andRespond(
                    org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        """{"data":[]}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )
        }
        val result = tester.test(ProviderSpec.CopilotModels("ghp_abcdef"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test omits the githubUser field when the first probe body has no login`() {
        server
            .expect { request -> assertTrue(request.uri.path.endsWith("/user")) }
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"id":5}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server
            .expect { request -> assertTrue(request.uri.host == "api.business.githubcopilot.com") }
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"data":[
                      {"id":"m","name":"M","model_picker_enabled":true}
                    ]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        val result = tester.test(ProviderSpec.CopilotModels("ghp_abcdef"))
        val map = (result as AiConnectionTestPort.TestResult.Success).payload
        assertTrue(!map.containsKey("githubUser"))
    }
}
