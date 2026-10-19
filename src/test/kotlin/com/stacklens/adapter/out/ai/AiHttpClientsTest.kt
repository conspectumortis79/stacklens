package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.http.HttpClient

class AiHttpClientsTest {
    @Test
    fun `http2 client uses default HTTP version`() {
        val clients = AiHttpClients()
        val client = clients.http2
        assertEquals(HttpClient.Version.HTTP_2, client.version())
    }

    @Test
    fun `http11 client uses HTTP 1_1`() {
        val clients = AiHttpClients()
        val client = clients.http11
        assertEquals(HttpClient.Version.HTTP_1_1, client.version())
    }
}
