package com.stacklens.application.port.inbound.config

interface ConfigEnvelopeUseCase {
    fun encrypt(
        plain: ByteArray,
        password: String,
    ): ByteArray

    fun decrypt(
        envelope: ByteArray,
        password: String,
    ): ByteArray?
}
