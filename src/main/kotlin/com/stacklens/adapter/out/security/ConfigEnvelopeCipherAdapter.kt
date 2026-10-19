package com.stacklens.adapter.out.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.config.ConfigEnvelopePort
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Component
class ConfigEnvelopeCipherAdapter(
    private val objectMapper: ObjectMapper,
) : ConfigEnvelopePort {
    override fun encrypt(
        plain: ByteArray,
        password: String,
    ): ByteArray {
        val rng = SecureRandom()
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val iterations = 310_000
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plain)
        val enc = Base64.getEncoder()
        val envelope =
            ConfigEnvelope(
                iterations = iterations,
                salt = enc.encodeToString(salt),
                iv = enc.encodeToString(iv),
                data = enc.encodeToString(ciphertext),
            )
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(envelope)
    }

    override fun decrypt(
        envelope: ByteArray,
        password: String,
    ): ByteArray? {
        val parsed = objectMapper.readValue(envelope, ConfigEnvelope::class.java)
        val iterations = parsed.iterations.coerceIn(1, MAX_KEY_DERIVATION_ITERATIONS)
        val dec = Base64.getDecoder()
        val salt = dec.decode(parsed.salt)
        val iv = dec.decode(parsed.iv)
        val ciphertext = dec.decode(parsed.data)
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            null
        }
    }

    private companion object {
        const val MAX_KEY_DERIVATION_ITERATIONS = 2_000_000
    }
}

internal fun deriveKey(
    password: String,
    salt: ByteArray,
    iterations: Int,
): SecretKeySpec {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
    return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
}
