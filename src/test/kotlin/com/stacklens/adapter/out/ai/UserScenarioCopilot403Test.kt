package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
import com.stacklens.testsupport.FakeLmStudioModelLookupPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

class UserScenarioCopilot403Test {
    private val restTemplate: RestTemplate = mock()
    private val i18n: I18nPort =
        mock(
            defaultAnswer =
                org.mockito.stubbing.Answer { inv ->
                    when {
                        inv.method.returnType == String::class.java -> inv.arguments.firstOrNull() as? String ?: ""
                        else -> null
                    }
                },
        )

    private fun buildAdapter(): AiConnectionTestAdapter {
        val githubTester = GithubTokenConnectionTester(restTemplate, i18n)
        val copilotTester = CopilotModelsConnectionTester(restTemplate, i18n)
        return AiConnectionTestAdapter(
            testers = listOf(githubTester, copilotTester),
            lmStudioService = FakeLmStudioModelLookupPort(),
            containerRuntimeProbe = FakeContainerRuntimeProbePort(),
        )
    }

    @Test
    fun `reproduces user scenario - test-token succeeds but copilot-models returns 403`() {
        whenever(restTemplate.exchange(any<String>(), any<HttpMethod>(), any(), any<Class<*>>()))
            .thenAnswer { inv ->
                val url = inv.getArgument<String>(0)
                when {
                    url.contains("github.com/user") -> {
                        val node = JsonNodeFactory.instance.objectNode().put("login", "pamo97")
                        ResponseEntity.ok<JsonNode>(node)
                    }
                    url.contains("githubcopilot.com") -> {
                        throw HttpClientErrorException(
                            HttpStatus.FORBIDDEN,
                            "Forbidden",
                            "token lacks copilot scope".toByteArray(),
                            null,
                        )
                    }
                    else -> throw IllegalStateException("Unexpected URL: $url")
                }
            }
        val adapter = buildAdapter()

        val tokenResult = adapter.testConnection(ProviderSpec.GithubToken(token = "ghp_valid"))
        assertTrue(tokenResult is TestResult.Success)
        assertEquals("pamo97", tokenResult.payload["githubUser"])

        val modelsResult = adapter.testConnection(ProviderSpec.CopilotModels(token = "ghp_valid"))
        assertTrue(modelsResult is TestResult.Failure)
        assertEquals(ConnectionFailureKind.AUTHORIZATION, (modelsResult as TestResult.Failure).kind)
        assertEquals(false, modelsResult.payload["success"])
        assertEquals("config.be_copilot_models_unavailable", modelsResult.payload["error"])
    }

    @Test
    fun `testConnection dispatch throws when no matching tester for CopilotModels`() {
        val onlyGithubTester = GithubTokenConnectionTester(restTemplate, i18n)
        val adapter = AiConnectionTestAdapter(testers = listOf(onlyGithubTester), lmStudioService = FakeLmStudioModelLookupPort(), containerRuntimeProbe = FakeContainerRuntimeProbePort())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            adapter.testConnection(ProviderSpec.CopilotModels(token = "ghp_valid"))
        }
    }
}
