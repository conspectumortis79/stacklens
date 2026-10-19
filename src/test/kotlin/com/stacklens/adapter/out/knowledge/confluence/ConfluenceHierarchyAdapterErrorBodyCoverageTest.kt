package com.stacklens.adapter.out.knowledge.confluence

import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfluenceHierarchyAdapterErrorBodyCoverageTest {
    private lateinit var server: MockWebServer

    private val factory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 10_000)

    private lateinit var config: ConfluenceConfig

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
        config =
            ConfluenceConfig(
                name = "wiki",
                baseUrl = server.url("/").toString().trimEnd('/'),
                username = "u",
                apiToken = "t",
            )
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun adapter() = ConfluenceHierarchyAdapter(factory)

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `a failing root page aborts the walk with the http cause`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(503)
                        .setHeader("Content-Type", "text/plain")
                        .setBody("confluence is maintenance")
            }

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter().walkHierarchy(config, "${server.url("/spaces/X/display")}?pageId=1234", true, 2, 5).toList()
            }

        assertTrue(failure.message!!.contains("root page fetch failed"), failure.message)
        assertTrue(failure.cause!!.message!!.contains("503") || failure.message!!.contains("503"), failure.message)
    }

    @Test
    fun `a failing child page lookup only ends that branch`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        "/content/100?" in path || path.endsWith("/content/100") ->
                            json("""{"id":"100","title":"Root","body":{"view":{"value":"<p>root</p>"}}}""")

                        "/child/page" in path -> MockResponse().setResponseCode(500).setBody("""{"message":"broken"}""")
                        else -> json("{}")
                    }
                }
            }

        val titles =
            adapter()
                .walkHierarchy(config, "${server.url("/spaces/X/display")}?pageId=100", includeChildren = true, maxDepth = 2, maxPages = 5)
                .map { it.title }
                .toList()

        assertEquals(listOf("Root"), titles)
    }
}
