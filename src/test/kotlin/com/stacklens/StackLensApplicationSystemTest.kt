package com.stacklens

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StackLensApplicationSystemTest {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun shouldServeApplicationPagesForAkCreateUnitTests() {
        val config = restTemplate.getForEntity("/config", String::class.java)
        assertTrue(config.statusCode == HttpStatus.OK)
        assertTrue(config.body?.contains("StackLens") == true)

        val security = restTemplate.getForEntity("/security-analysis", String::class.java)
        assertTrue(security.statusCode == HttpStatus.OK)
        assertTrue(security.body?.contains("StackLens") == true)
    }
}
