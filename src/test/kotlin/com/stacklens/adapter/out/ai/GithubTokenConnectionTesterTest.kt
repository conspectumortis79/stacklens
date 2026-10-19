package com.stacklens.adapter.out.ai

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.service.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class GithubTokenConnectionTesterTest {
    private lateinit var restTemplate: RestTemplate

    private lateinit var server: MockRestServiceServer

    private val i18n = FakeI18n()

    private lateinit var tester: GithubTokenConnectionTester

    @BeforeEach
    fun setUp() {
        restTemplate = RestTemplate()
        server = MockRestServiceServer.createServer(restTemplate)
        tester = GithubTokenConnectionTester(restTemplate, i18n)
    }

    @Test
    fun `a missing or blank token is rejected before any request`() {
        listOf(null, "", "   ").forEach { token ->
            val result = tester.test(ProviderSpec.GithubToken(token))

            assertEquals(
                TestResult.Failure(ConnectionFailureKind.AUTHENTICATION, mapOf("success" to false, "error" to "config.be_no_token")),
                result,
            )
        }
    }

    @Test
    fun `a valid token reports the github user`() {
        val headers = mutableListOf<String?>()
        server
            .expect(requestTo("https://api.github.com/user"))
            .andExpect {
                assertEquals(HttpMethod.GET, it.method)
                headers += it.headers.getFirst("Authorization")
            }.andRespond(withSuccess("""{"login":"octocat"}""", MediaType.APPLICATION_JSON))

        val result = tester.test(ProviderSpec.GithubToken("ghp_secret"))

        assertEquals(
            TestResult.Success(
                mapOf(
                    "success" to true,
                    "githubUser" to "octocat",
                    "modelCount" to 0,
                    "models" to emptyList<String>(),
                ),
            ),
            result,
        )
        assertEquals(listOf("Bearer ghp_secret"), headers)
    }

    @Test
    fun `a response without a login falls back to a question mark`() {
        server.expect(requestTo("https://api.github.com/user")).andRespond(withSuccess("""{"id":7}""", MediaType.APPLICATION_JSON))

        val payload = (tester.test(ProviderSpec.GithubToken("ghp")) as TestResult.Success).payload

        assertEquals("?", payload["githubUser"])
    }

    @Test
    fun `a rejected token is reported with the status code and a body snippet`() {
        server
            .expect(requestTo("https://api.github.com/user"))
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators
                    .withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body("Bad credentials"),
            )

        val payload = (tester.test(ProviderSpec.GithubToken("ghp")) as TestResult.Failure).payload

        assertEquals(false, payload["success"])
        assertTrue(payload["error"].toString().startsWith("config.be_pat_invalid(401,"), "unexpected: ${payload["error"]}")
        assertTrue(payload["error"].toString().contains("Bad credentials"), "unexpected: ${payload["error"]}")
    }

    @Test
    fun `a server error is reported as unreachable`() {
        server.expect(requestTo("https://api.github.com/user")).andRespond(withServerError())

        val payload = (tester.test(ProviderSpec.GithubToken("ghp")) as TestResult.Failure).payload

        assertTrue(payload["error"].toString().startsWith("config.be_github_unreachable"), "unexpected: ${payload["error"]}")
    }
}
