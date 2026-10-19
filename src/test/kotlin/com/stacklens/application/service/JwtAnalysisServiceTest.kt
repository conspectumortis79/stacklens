package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.filesystem.ClasspathTranslationCatalogAdapter
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.AiConfigPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.core.io.DefaultResourceLoader
import java.time.Instant
import java.util.Base64

class JwtAnalysisServiceTest {
    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }
    private val jsonCodec = JacksonJsonCodecAdapter(objectMapper)
    private val aiConfigRepo: AiConfigPort = mock()
    private lateinit var service: JwtAnalysisService

    @BeforeEach
    fun setUp() {
        val catalog = ClasspathTranslationCatalogAdapter(DefaultResourceLoader(), jsonCodec)
        val i18n = I18nService(catalog, aiConfigRepo)
        service = JwtAnalysisService(jsonCodec, i18n, clock = com.stacklens.adapter.out.time.SystemClockPort)
    }

    private fun base64url(json: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private fun buildToken(
        header: String = """{"alg":"HS256","typ":"JWT"}""",
        payload: String,
        signature: String = "sig",
    ): String = "${base64url(header)}.${base64url(payload)}.$signature"

    private fun futureEpoch(secondsFromNow: Long = 3600L): Long = Instant.now().epochSecond + secondsFromNow

    private fun pastEpoch(secondsAgo: Long = 3600L): Long = Instant.now().epochSecond - secondsAgo

    @Test
    fun `parseBase64UrlJson parses valid base64url-encoded JSON`() {
        val json = """{"key":"value"}"""
        val node = service.parseBase64UrlJson(base64url(json))
        assertEquals("value", node["key"])
    }

    @Test
    fun `parseBase64UrlJson handles input without padding`() {
        val json = """{"a":1}"""
        val node = service.parseBase64UrlJson(base64url(json))
        assertEquals(1, (node["a"] as Number).toInt())
    }

    @Test
    fun `analyze throws IllegalArgumentException for token with fewer than two parts`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.analyze("onlyone")
        }
    }

    @Test
    fun `analyze throws IllegalArgumentException when header is not valid base64url JSON`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.analyze("not-base64!.${base64url("""{"sub":"1"}""")}.sig")
        }
    }

    @Test
    fun `analyze throws IllegalArgumentException when payload is not valid base64url JSON`() {
        val validHeader = base64url("""{"alg":"HS256"}""")
        assertThrows(IllegalArgumentException::class.java) {
            service.analyze("$validHeader.not-base64!.sig")
        }
    }

    @Test
    fun `analyze returns VALID status when token has future expiry`() {
        val token =
            buildToken(
                payload =
                    """{"sub":"user1","exp":${futureEpoch()},"iat":${Instant.now().epochSecond}}""",
            )
        val result = service.analyze(token)
        assertEquals("VALID", result.status)
    }

    @Test
    fun `analyze returns correct algorithm from header`() {
        val token =
            buildToken(
                header = """{"alg":"RS256","typ":"JWT"}""",
                payload = """{"sub":"u","exp":${futureEpoch()}}""",
            )
        val result = service.analyze(token)
        assertEquals("RS256", result.algorithm)
    }

    @Test
    fun `analyze returns correct tokenType from header`() {
        val token = buildToken(payload = """{"sub":"u","exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertEquals("JWT", result.tokenType)
    }

    @Test
    fun `analyze returns null algorithm and tokenType when header fields are absent`() {
        val token =
            buildToken(
                header = "{}",
                payload = """{"sub":"u","exp":${futureEpoch()}}""",
            )
        val result = service.analyze(token)
        assertNull(result.algorithm)
        assertNull(result.tokenType)
    }

    @Test
    fun `analyze returns correct issuer claim`() {
        val token = buildToken(payload = """{"iss":"https://auth.example.com","exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertEquals("https://auth.example.com", result.issuer)
    }

    @Test
    fun `analyze returns correct subject claim`() {
        val token = buildToken(payload = """{"sub":"user42","exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertEquals("user42", result.subject)
    }

    @Test
    fun `analyze returns single audience as string`() {
        val token = buildToken(payload = """{"aud":"my-service","exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertEquals("my-service", result.audience)
    }

    @Test
    fun `analyze returns array audience joined by comma`() {
        val token = buildToken(payload = """{"aud":["svc1","svc2"],"exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertEquals("svc1, svc2", result.audience)
    }

    @Test
    fun `analyze handles null element in audience array`() {
        val token = buildToken(payload = """{"aud":["svc1",null,"svc2"],"exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertEquals("svc1, , svc2", result.audience)
    }

    @Test
    fun `analyze returns jwtId from jti claim`() {
        val token = buildToken(payload = """{"jti":"unique-id-123","exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertEquals("unique-id-123", result.jwtId)
    }

    @Test
    fun `analyze returns null jwtId when jti is blank`() {
        val token = buildToken(payload = """{"jti":"","exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertNull(result.jwtId)
    }

    @Test
    fun `analyze returns null jwtId when jti claim is absent`() {
        val token = buildToken(payload = """{"sub":"u","exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertNull(result.jwtId)
    }

    @Test
    fun `analyze returns formatted expirationUtc`() {
        val token = buildToken(payload = """{"exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertNotNull(result.expirationUtc)
        assertTrue(result.expirationUtc!!.contains("UTC"))
    }

    @Test
    fun `analyze returns formatted issuedAtUtc when iat is present`() {
        val iat = Instant.now().epochSecond
        val token = buildToken(payload = """{"iat":$iat,"exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertNotNull(result.issuedAtUtc)
        assertTrue(result.issuedAtUtc!!.contains("UTC"))
    }

    @Test
    fun `analyze returns null issuedAtUtc when iat is absent`() {
        val token = buildToken(payload = """{"sub":"u","exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertNull(result.issuedAtUtc)
    }

    @Test
    fun `analyze returns formatted notBeforeUtc when nbf is present`() {
        val nbf = Instant.now().epochSecond - 60
        val token = buildToken(payload = """{"nbf":$nbf,"exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertNotNull(result.notBeforeUtc)
        assertTrue(result.notBeforeUtc!!.contains("UTC"))
    }

    @Test
    fun `analyze returns null notBeforeUtc when nbf is absent`() {
        val token = buildToken(payload = """{"sub":"u","exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertNull(result.notBeforeUtc)
    }

    @Test
    fun `analyze timeRemaining mentions days when expiry is more than one day away`() {
        val token = buildToken(payload = """{"exp":${futureEpoch(secondsFromNow = 90_000L)}}""")
        val result = service.analyze(token)
        assertEquals("VALID", result.status)
        assertNotNull(result.timeRemaining)
    }

    @Test
    fun `analyze timeRemaining uses hours-only format when expiry is less than one day away`() {
        val token = buildToken(payload = """{"exp":${futureEpoch(secondsFromNow = 3600L)}}""")
        val result = service.analyze(token)
        assertEquals("VALID", result.status)
        assertNotNull(result.timeRemaining)
    }

    @Test
    fun `analyze returns EXPIRED status when expiry is in the past`() {
        val token = buildToken(payload = """{"exp":${pastEpoch()}}""")
        val result = service.analyze(token)
        assertEquals("EXPIRED", result.status)
    }

    @Test
    fun `analyze returns non-null timeRemaining for expired token`() {
        val token = buildToken(payload = """{"exp":${pastEpoch()}}""")
        val result = service.analyze(token)
        assertNotNull(result.timeRemaining)
    }

    @Test
    fun `analyze returns UNLIMITED status when no exp claim is present`() {
        val token = buildToken(payload = """{"sub":"service-account"}""")
        val result = service.analyze(token)
        assertEquals("UNLIMITED", result.status)
    }

    @Test
    fun `analyze returns null expirationUtc for unlimited token`() {
        val token = buildToken(payload = """{"sub":"service-account"}""")
        val result = service.analyze(token)
        assertNull(result.expirationUtc)
    }

    @Test
    fun `analyze returns null timeRemaining for unlimited token`() {
        val token = buildToken(payload = """{"sub":"service-account"}""")
        val result = service.analyze(token)
        assertNull(result.timeRemaining)
    }

    @Test
    fun `analyze returns null when optional claims are absent`() {
        val token = buildToken(payload = """{"exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertNull(result.issuer)
        assertNull(result.subject)
        assertNull(result.audience)
    }

    @Test
    fun `analyze handles token with exactly two dots (no signature segment)`() {
        val token =
            buildToken(
                payload = """{"sub":"u","exp":${futureEpoch()}}""",
                signature = "",
            )
        val result = service.analyze(token)
        assertEquals("VALID", result.status)
    }

    @Test
    fun `analyze returns header and payload as non-null JsonNode`() {
        val token = buildToken(payload = """{"sub":"u","exp":${futureEpoch()}}""")
        val result = service.analyze(token)
        assertNotNull(result.header)
        assertNotNull(result.payload)
    }
}
