package com.stacklens.adapter.inbound.rest.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.inbound.rest.ConfigExportDto
import com.stacklens.application.port.inbound.config.ConfigEnvelopeUseCase
import org.springframework.stereotype.Component

enum class ConfigEnvelopeRejection {
    NEEDS_PASSWORD,
    WRONG_PASSWORD,
    DECRYPT_FAILED,
    INVALID_JSON,
}

sealed interface ConfigEnvelopeOutcome {
    data class Loaded(
        val dto: ConfigExportDto,
    ) : ConfigEnvelopeOutcome

    data class Rejected(
        val reason: ConfigEnvelopeRejection,
        val detail: String = "",
    ) : ConfigEnvelopeOutcome
}

@Component
class ConfigEnvelopeCodec(
    private val objectMapper: ObjectMapper,
    private val envelopeCipher: ConfigEnvelopeUseCase,
) {
    fun encrypt(
        dto: ConfigExportDto,
        password: String,
    ): ByteArray {
        val plain = prettyWriter().writeValueAsBytes(dto)
        return envelopeCipher.encrypt(plain, password)
    }

    fun decode(
        bytes: ByteArray,
        password: String?,
    ): ConfigEnvelopeOutcome =
        try {
            val root = objectMapper.readTree(bytes)
            if (!root.path("encrypted").asBoolean(false)) {
                ConfigEnvelopeOutcome.Loaded(objectMapper.readValue(bytes, ConfigExportDto::class.java))
            } else {
                decodeEncrypted(bytes, password)
            }
        } catch (e: Exception) {
            ConfigEnvelopeOutcome.Rejected(ConfigEnvelopeRejection.INVALID_JSON, e.toString().take(MAX_DETAIL_LENGTH))
        }

    private fun decodeEncrypted(
        bytes: ByteArray,
        password: String?,
    ): ConfigEnvelopeOutcome {
        if (password.isNullOrBlank()) {
            return ConfigEnvelopeOutcome.Rejected(ConfigEnvelopeRejection.NEEDS_PASSWORD)
        }
        val plain =
            try {
                envelopeCipher.decrypt(bytes, password) ?: return ConfigEnvelopeOutcome.Rejected(ConfigEnvelopeRejection.WRONG_PASSWORD)
            } catch (e: Exception) {
                return ConfigEnvelopeOutcome.Rejected(ConfigEnvelopeRejection.DECRYPT_FAILED, e.toString().take(MAX_DETAIL_LENGTH))
            }
        return ConfigEnvelopeOutcome.Loaded(objectMapper.readValue(plain, ConfigExportDto::class.java))
    }

    private fun prettyWriter() = objectMapper.writerWithDefaultPrettyPrinter()

    private companion object {
        const val MAX_DETAIL_LENGTH = 200
    }
}
