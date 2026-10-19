package com.stacklens.application.port.out.config

interface ConfigEnvelopePort {
    fun encrypt(
        plain: ByteArray,
        password: String,
    ): ByteArray

    fun decrypt(
        envelope: ByteArray,
        password: String,
    ): ByteArray?
}
