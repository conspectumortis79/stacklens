package com.stacklens.infrastructure.config

import com.stacklens.adapter.inbound.web.HtmlNoCacheFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class HtmlNoCacheFilterTest {
    private val filter = HtmlNoCacheFilter()

    private fun headersFor(path: String): MockHttpServletResponse {
        val response = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("GET", path), response, MockFilterChain())
        return response
    }

    @Test
    fun `page routes are marked as not cacheable`() {
        val response = headersFor("/search")

        assertEquals("no-cache, no-store, must-revalidate", response.getHeader("Cache-Control"))
        assertEquals("no-cache", response.getHeader("Pragma"))
    }

    @Test
    fun `thymeleaf html routes are marked as not cacheable`() {
        val response = headersFor("/simulated-mr.html")

        assertEquals("no-cache, no-store, must-revalidate", response.getHeader("Cache-Control"))
    }

    @Test
    fun `api routes stay uncached`() {
        val response = headersFor("/api/config/language")

        assertEquals("no-cache, no-store, must-revalidate", response.getHeader("Cache-Control"))
        assertEquals("0", response.getHeader("Expires"))
    }

    @Test
    fun `javascript assets keep their own caching`() {
        val response = headersFor("/js/app.js")

        assertFalse(response.containsHeader("Cache-Control"))
        assertFalse(response.containsHeader("Pragma"))
    }

    @Test
    fun `stylesheet and font assets keep their own caching`() {
        listOf("/css/custom.css", "/css/themes/theme-dark.css", "/i18n/de.json", "/favicon.ico", "/vendor/x.woff2")
            .forEach { path ->
                val response = headersFor(path)

                assertFalse(response.containsHeader("Pragma"), path)
                assertFalse(response.containsHeader("Expires"), path)
            }
    }
}
