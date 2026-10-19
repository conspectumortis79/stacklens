package com.stacklens.adapter.inbound.rest.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.inbound.rest.ConfigExportDto
import com.stacklens.adapter.out.security.ConfigEnvelopeCipherAdapter
import com.stacklens.application.service.config.ConfigEnvelopeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigEnvelopeCodecTest {
    private val mapper = ObjectMapper()

    private val codec = ConfigEnvelopeCodec(mapper, ConfigEnvelopeService(ConfigEnvelopeCipherAdapter(mapper)))

    private val dto =
        ConfigExportDto(
            exportedAt = "2026-01-01T00:00:00",
            jira =
                listOf(
                    com.stacklens.adapter.inbound.rest.JiraConfigDto(
                        name = "JR",
                        baseUrl = "https://jira",
                        username = "u",
                        apiToken = "t",
                    ),
                ),
            theme = "dark",
            uiPreferences = mapOf("theme" to "dark"),
        )

    @Test
    fun `encrypt produces an envelope that decrypts back to the same payload`() {
        val encrypted = codec.encrypt(dto, "secret")

        val outcome = codec.decode(encrypted, "secret")

        val loaded = (outcome as ConfigEnvelopeOutcome.Loaded).dto
        assertEquals(dto.exportedAt, loaded.exportedAt)
        assertEquals("JR", loaded.jira.single().name)
    }

    @Test
    fun `decode reads a plain envelope without a password`() {
        val plain = mapper.writeValueAsBytes(dto)

        val outcome = codec.decode(plain, null)

        assertEquals("dark", (outcome as ConfigEnvelopeOutcome.Loaded).dto.theme)
    }

    @Test
    fun `decode asks for a password when the envelope is encrypted`() {
        val encrypted = codec.encrypt(dto, "secret")

        val outcome = codec.decode(encrypted, null)

        assertEquals(ConfigEnvelopeRejection.NEEDS_PASSWORD, (outcome as ConfigEnvelopeOutcome.Rejected).reason)
    }

    @Test
    fun `decode reports a wrong password without leaking the payload`() {
        val encrypted = codec.encrypt(dto, "secret")

        val outcome = codec.decode(encrypted, "wrong-password")

        assertEquals(ConfigEnvelopeRejection.WRONG_PASSWORD, (outcome as ConfigEnvelopeOutcome.Rejected).reason)
    }

    @Test
    fun `decode reports malformed json`() {
        val outcome = codec.decode("not json".toByteArray(), "secret")

        val rejected = outcome as ConfigEnvelopeOutcome.Rejected
        assertEquals(ConfigEnvelopeRejection.INVALID_JSON, rejected.reason)
        assertTrue(rejected.detail.isNotBlank())
    }
}
