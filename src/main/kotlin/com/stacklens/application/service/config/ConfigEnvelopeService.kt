package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.ConfigEnvelopeUseCase
import com.stacklens.application.port.out.config.ConfigEnvelopePort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class ConfigEnvelopeService(
    private val configEnvelopePort: ConfigEnvelopePort,
) : ConfigEnvelopeUseCase {
    override fun encrypt(
        plain: ByteArray,
        password: String,
    ): ByteArray = configEnvelopePort.encrypt(plain, password)

    override fun decrypt(
        envelope: ByteArray,
        password: String,
    ): ByteArray? = configEnvelopePort.decrypt(envelope, password)
}
