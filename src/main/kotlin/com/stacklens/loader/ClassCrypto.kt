
@file:JvmName("ClassCrypto")

package com.stacklens.loader

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal val MAGIC = byteArrayOf(0x53, 0x4C, 0x45, 0x4E, 0x43, 0x01)

private const val IV_LENGTH = 12

private const val TAG_LENGTH_BITS = 128

fun deriveKey(passphrase: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(passphrase.toByteArray(Charsets.UTF_8))

fun encrypt(
    data: ByteArray,
    key: ByteArray,
): ByteArray {
    val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, iv))
    val ciphertext = cipher.doFinal(data)
    return MAGIC + iv + ciphertext
}

fun decrypt(
    data: ByteArray,
    key: ByteArray,
): ByteArray {
    require(data.size > MAGIC.size + IV_LENGTH) { "Data too short to be a valid SLENC blob" }
    for (i in MAGIC.indices) {
        require(data[i] == MAGIC[i]) { "Invalid SLENC magic at byte $i" }
    }
    val iv = data.copyOfRange(MAGIC.size, MAGIC.size + IV_LENGTH)
    val ciphertext = data.copyOfRange(MAGIC.size + IV_LENGTH, data.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, iv))
    return cipher.doFinal(ciphertext)
}
