package com.stacklens.systemtest

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared Ollama embedding access for the RAG retrieval system tests.
 *
 * `src/test/resources/application.yml` caps `webclient.read-timeout` at 5 s. That is right for
 * tests against mocked HTTP endpoints, but far too tight for a local embedding model: a cold
 * `nomic-embed-text` run regularly needs 10–30 s per request (model load + embedding), so
 * `KnowledgePublicationExecutor` calls fail with a Netty `ReadTimeoutException` and the RAG tests
 * report bogus assertion errors. The RAG tests therefore raise the timeout for their Spring context
 * via [READ_TIMEOUT_PROPERTY] and use the same budget for their own warm-up/search calls.
 */
object RagOllamaTestSupport {
    const val EMBED_MODEL: String = "nomic-embed-text"
    const val EMBED_DIMENSIONS: Int = 768

    private const val READ_TIMEOUT_MS: Int = 120_000

    /** Spring property that overrides the 5 s test default for the RAG system test contexts. */
    const val READ_TIMEOUT_PROPERTY: String = "webclient.read-timeout=$READ_TIMEOUT_MS"

    private val mapper = ObjectMapper()

    /**
     * Loads the embedding model into Ollama before the first assertion so that a slow cold start
     * does not surface as a timeout inside `publish(...)` or the first query embedding.
     */
    fun warmup() {
        runCatching { embedQuery("warmup") }
            .onFailure { failure ->
                throw IllegalStateException(
                    "Ollama embedding model '$EMBED_MODEL' is not usable at ${ollamaUrl()}: ${failure.message}. " +
                        "Start Ollama and run 'ollama pull $EMBED_MODEL' " +
                        "(override the URL with STACKLENS_RAG_TEST_OLLAMA_URL).",
                    failure,
                )
            }
    }

    fun embedQuery(text: String): FloatArray {
        val body = """{"model":"$EMBED_MODEL","input":${mapper.writeValueAsString(listOf(text))}}"""

        @Suppress("DEPRECATION")
        val conn = URL("${ollamaUrl()}/api/embed").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val status = conn.responseCode
            val response =
                (if (status in 200..299) conn.inputStream else conn.errorStream)
                    .bufferedReader()
                    .readText()
            check(status in 200..299) { "Ollama /api/embed returned HTTP $status: ${response.take(300)}" }
            val vectors = mapper.readTree(response).path("embeddings")
            require(vectors.isArray && vectors.size() > 0) { "Ollama returned no embeddings. Body: $response" }
            val emb = vectors.path(0)
            val arr = FloatArray(emb.size()) { emb.get(it).asDouble().toFloat() }
            assertEquals(
                EMBED_DIMENSIONS,
                arr.size,
                "expected $EMBED_DIMENSIONS-dim vector from $EMBED_MODEL, got ${arr.size}",
            )
            return arr
        } finally {
            conn.disconnect()
        }
    }

    private fun ollamaUrl(): String = System.getenv("STACKLENS_RAG_TEST_OLLAMA_URL") ?: "http://127.0.0.1:11434"
}
