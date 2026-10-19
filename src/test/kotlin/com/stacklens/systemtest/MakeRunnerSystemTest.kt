package com.stacklens.systemtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MakeRunnerSystemTest {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @BeforeEach
    fun useSimpleHttpClient() {
        restTemplate.restTemplate.requestFactory = SimpleClientHttpRequestFactory()
    }

    private fun jsonEntity(body: String): HttpEntity<String> = HttpEntity(body, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON })

    @Test
    fun `POST make-runner with non-existent repo returns 200`() {
        val response =
            restTemplate.postForEntity(
                "/api/make-runner",
                jsonEntity("""{"repoPath": "/this/path/does/not/exist/stacklens", "target": "test"}"""),
                String::class.java,
            )
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `POST make-runner with disallowed target returns 200`() {
        val response =
            restTemplate.postForEntity(
                "/api/make-runner",
                jsonEntity("""{"repoPath": "/tmp", "target": "rm-rf"}"""),
                String::class.java,
            )
        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
