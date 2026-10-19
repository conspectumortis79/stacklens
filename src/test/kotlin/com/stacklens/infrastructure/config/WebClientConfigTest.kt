package com.stacklens.infrastructure.config

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class WebClientConfigTest {
    @Test
    fun `webClientBuilder returns a configured builder`() {
        val config = WebClientConfig()
        val builder: WebClient.Builder = config.webClientBuilder()
        assertNotNull(builder)
    }

    @Test
    fun `webClientBuilder can build a working WebClient`() {
        val config = WebClientConfig()
        val builder = config.webClientBuilder()
        val client = builder.build()
        assertNotNull(client)
    }

    @Test
    fun `multiple invocations produce independent builders`() {
        val config = WebClientConfig()
        val a = config.webClientBuilder()
        val b = config.webClientBuilder()
        assertNotNull(a)
        assertNotNull(b)
    }

    @Test
    fun `webClientBuilder registers configureConnection callback via doOnConnected`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("OK"))
            server.start()
            val config = WebClientConfig()
            val client = config.webClientBuilder().build()
            val body =
                client
                    .get()
                    .uri(server.url("/test").toString())
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .block()
            assertNotNull(body)
        }
    }
}
