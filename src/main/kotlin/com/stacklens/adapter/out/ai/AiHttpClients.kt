package com.stacklens.adapter.out.ai

import org.springframework.stereotype.Component
import java.net.http.HttpClient
import java.time.Duration

@Component
class AiHttpClients {
    val http2: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()

    val http11: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_1_1)
            .build()
}
