package com.stacklens.application.service.grafana

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrafanaUrlValidatorTest {
    private val validator = GrafanaUrlValidator

    @Test
    fun `parsePrometheusQueryUrl accepts http on localhost`() {
        val r = validator.parsePrometheusQueryUrl("http://localhost:9090")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Ok)
        assertEquals("localhost", (r as GrafanaUrlValidator.PrometheusUrlResult.Ok).uri.host)
        assertEquals(9090, r.uri.port)
    }

    @Test
    fun `parsePrometheusQueryUrl accepts http on 127 dot 0 dot 0 dot 1`() {
        val r = validator.parsePrometheusQueryUrl("http://127.0.0.1:9090")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Ok)
    }

    @Test
    fun `parsePrometheusQueryUrl accepts http on IPv6 loopback`() {
        val r = validator.parsePrometheusQueryUrl("http://[::1]:9090")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Ok)
    }

    @Test
    fun `parsePrometheusQueryUrl accepts https on localhost`() {
        val r = validator.parsePrometheusQueryUrl("https://localhost:9090")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Ok)
    }

    @Test
    fun `parsePrometheusQueryUrl rejects malformed URL`() {
        val r = validator.parsePrometheusQueryUrl("not a url with spaces")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Err)
        assertEquals(
            GrafanaUrlValidator.PrometheusUrlError.MALFORMED,
            (r as GrafanaUrlValidator.PrometheusUrlResult.Err).reason,
        )
    }

    @Test
    fun `parsePrometheusQueryUrl rejects ftp scheme`() {
        val r = validator.parsePrometheusQueryUrl("ftp://localhost")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Err)
        assertEquals(
            GrafanaUrlValidator.PrometheusUrlError.NON_HTTP_SCHEME,
            (r as GrafanaUrlValidator.PrometheusUrlResult.Err).reason,
        )
    }

    @Test
    fun `parsePrometheusQueryUrl rejects non-loopback host`() {
        val r = validator.parsePrometheusQueryUrl("http://example.com:9090")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Err)
        assertEquals(
            GrafanaUrlValidator.PrometheusUrlError.NON_LOOPBACK_HOST,
            (r as GrafanaUrlValidator.PrometheusUrlResult.Err).reason,
        )
    }

    @Test
    fun `parsePrometheusQueryUrl rejects URI with null host`() {
        val r = validator.parsePrometheusQueryUrl("http:///path")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Err)
        assertEquals(
            GrafanaUrlValidator.PrometheusUrlError.NON_LOOPBACK_HOST,
            (r as GrafanaUrlValidator.PrometheusUrlResult.Err).reason,
        )
    }

    @Test
    fun `parsePrometheusQueryUrl is case-insensitive on scheme`() {
        val r = validator.parsePrometheusQueryUrl("HTTP://localhost")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Ok)
    }

    @Test
    fun `parsePrometheusQueryUrl is case-insensitive on host`() {
        val r = validator.parsePrometheusQueryUrl("http://LOCALHOST")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Ok)
    }

    @Test
    fun `parsePrometheusQueryUrl rejects scheme-less URL covering null scheme branch`() {
        val r = validator.parsePrometheusQueryUrl("//localhost:9090")
        assertTrue(r is GrafanaUrlValidator.PrometheusUrlResult.Err)
        assertEquals(
            GrafanaUrlValidator.PrometheusUrlError.NON_HTTP_SCHEME,
            (r as GrafanaUrlValidator.PrometheusUrlResult.Err).reason,
        )
    }

    @Test
    fun `parseHttpUrl accepts http and https`() {
        assertTrue(validator.parseHttpUrl("http://example.com") is GrafanaUrlValidator.HttpUrlResult.Ok)
        assertTrue(validator.parseHttpUrl("https://example.com") is GrafanaUrlValidator.HttpUrlResult.Ok)
    }

    @Test
    fun `parseHttpUrl rejects malformed URL`() {
        val r = validator.parseHttpUrl("not a url with spaces")
        assertTrue(r is GrafanaUrlValidator.HttpUrlResult.Err)
        assertEquals(
            GrafanaUrlValidator.HttpUrlError.MALFORMED,
            (r as GrafanaUrlValidator.HttpUrlResult.Err).reason,
        )
    }

    @Test
    fun `parseHttpUrl rejects non-http scheme`() {
        val r = validator.parseHttpUrl("ftp://example.com")
        assertTrue(r is GrafanaUrlValidator.HttpUrlResult.Err)
        assertEquals(
            GrafanaUrlValidator.HttpUrlError.NON_HTTP_SCHEME,
            (r as GrafanaUrlValidator.HttpUrlResult.Err).reason,
        )
    }

    @Test
    fun `parseHttpUrl accepts URL without port`() {
        val r = validator.parseHttpUrl("https://example.com")
        assertTrue(r is GrafanaUrlValidator.HttpUrlResult.Ok)
        assertEquals(-1, (r as GrafanaUrlValidator.HttpUrlResult.Ok).uri.port)
    }

    @Test
    fun `parseHttpUrl trims whitespace around the URL`() {
        val r = validator.parseHttpUrl("  https://example.com  ")
        assertTrue(r is GrafanaUrlValidator.HttpUrlResult.Ok)
    }

    @Test
    fun `baseUrl omits port when URI has none`() {
        val r = validator.parseHttpUrl("https://example.com/foo")
        val uri = (r as GrafanaUrlValidator.HttpUrlResult.Ok).uri
        assertEquals("https://example.com", validator.baseUrl(uri))
    }

    @Test
    fun `baseUrl includes port when URI has one`() {
        val r = validator.parseHttpUrl("http://example.com:3000/foo")
        val uri = (r as GrafanaUrlValidator.HttpUrlResult.Ok).uri
        assertEquals("http://example.com:3000", validator.baseUrl(uri))
    }

    @Test
    fun `baseUrl returns null scheme and host when URI lacks them`() {
        val uri = java.net.URI("")
        assertEquals("null://null", validator.baseUrl(uri))
    }
}
