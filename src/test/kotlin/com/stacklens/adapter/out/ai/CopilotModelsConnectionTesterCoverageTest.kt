package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate

class CopilotModelsConnectionTesterCoverageTest {
    private val restTemplate = mock<RestTemplate>()
    private val i18n = mock<I18nPort>()
    private val tester = CopilotModelsConnectionTester(restTemplate, i18n)
    private val mapper = ObjectMapper()

    private fun stubExchange(handler: (String) -> JsonNode?) {
        whenever(
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<Void>>(),
                eq(JsonNode::class.java),
            ),
        ).thenAnswer { invocation -> ResponseEntity.ok(handler(invocation.arguments[0] as String)) }
    }

    private fun parseModels(payload: JsonNode?): Any? {
        val parse = CopilotModelsConnectionTester::class.java.getDeclaredMethod("parseModels", JsonNode::class.java)
        parse.isAccessible = true
        return parse.invoke(tester, payload)
    }

    @Test
    fun `a missing token is rejected before any http call`() {
        whenever(i18n.t("config.be_no_token")).thenReturn("no token")

        val result = tester.test(ProviderSpec.CopilotModels("  "))

        assertEquals("no token", (result as TestResult.Failure).payload["error"])
    }

    @Test
    fun `models of the first reachable host are reported together with the github login`() {
        stubExchange { url ->
            when {
                url.endsWith("/user") -> mapper.readTree("""{"login":"octocat"}""")
                url.contains("api.business") ->
                    mapper.readTree(
                        """{"data":[{"model_picker_enabled":true,"id":"gpt-5","name":"GPT 5"}]}""",
                    )

                else -> null
            }
        }

        val success = tester.test(ProviderSpec.CopilotModels("pat")) as TestResult.Success

        assertEquals(1, success.payload["modelCount"])
        assertEquals("octocat", success.payload["githubUser"])
        assertEquals(listOf(mapOf("id" to "gpt-5", "name" to "GPT 5")), success.payload["models"])
    }

    @Test
    fun `a failing user lookup still succeeds with models only`() {
        stubExchange { url ->
            when {
                url.endsWith("/user") -> throw ResourceAccessException("github unreachable")
                url.contains("api.individual") ->
                    mapper.readTree("""{"data":[{"model_picker_enabled":true,"id":"claude"}]}""")

                else -> mapper.readTree("""{"data":[]}""")
            }
        }

        val success = tester.test(ProviderSpec.CopilotModels("pat")) as TestResult.Success

        assertEquals(null, success.payload["githubUser"])
        assertEquals(listOf(mapOf("id" to "claude", "name" to "claude")), success.payload["models"])
    }

    @Test
    fun `a user response without a login keeps the models path`() {
        stubExchange { url ->
            if (url.endsWith("/user")) {
                mapper.readTree("""{"id":7}""")
            } else {
                mapper.readTree("""{"data":[{"model_picker_enabled":true,"id":"m1","name":"Model"}]}""")
            }
        }

        val success = tester.test(ProviderSpec.CopilotModels("pat")) as TestResult.Success

        assertEquals(null, success.payload["githubUser"])
    }

    @Test
    fun `no usable models on either host is reported as unavailable`() {
        whenever(i18n.t("config.be_copilot_models_unavailable")).thenReturn("unavailable")
        stubExchange { mapper.readTree("""{"data":[]}""") }

        val failure = tester.test(ProviderSpec.CopilotModels("pat")) as TestResult.Failure

        assertEquals("unavailable", failure.payload["error"])
    }

    @Test
    fun `parseModels ignores payloads without a data array`() {
        assertEquals(emptyList<Any>(), parseModels(null))
        assertEquals(emptyList<Any>(), parseModels(mapper.readTree("""{"other":1}""")))
    }

    @Test
    fun `parseModels keeps only entries the model picker enables`() {
        val payload =
            mapper.readTree(
                """
                {"data":[
                  {"model_picker_enabled":true,"id":"a","name":"A"},
                  {"model_picker_enabled":false,"id":"b","name":"B"},
                  {"id":"c"},
                  {"model_picker_enabled":true,"id":"d"}
                ]}
                """.trimIndent(),
            )

        @Suppress("UNCHECKED_CAST")
        val models = parseModels(payload) as List<Map<String, String>>

        assertEquals(listOf(mapOf("id" to "a", "name" to "A"), mapOf("id" to "d", "name" to "d")), models)
    }

    @Test
    fun `the tester declares the spec it serves`() {
        assertEquals(ProviderSpec.CopilotModels::class, tester.specType)
        assertTrue(tester.specType.simpleName == "CopilotModels")
    }
}
