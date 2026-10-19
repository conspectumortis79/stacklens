package com.stacklens.adapter.out.knowledge.archive

import com.stacklens.domain.knowledge.KnowledgeArchiveHeader
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object KnowledgeArchiveCipherFactory {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val KEY_DERIVATION = "PBKDF2WithHmacSHA256"
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun encryptor(
        password: String,
        header: KnowledgeArchiveHeader,
    ): Cipher = cipher(Cipher.ENCRYPT_MODE, password, header)

    fun decryptor(
        password: String,
        header: KnowledgeArchiveHeader,
    ): Cipher = cipher(Cipher.DECRYPT_MODE, password, header)

    private fun cipher(
        mode: Int,
        password: String,
        header: KnowledgeArchiveHeader,
    ): Cipher {
        val derived =
            SecretKeyFactory
                .getInstance(KEY_DERIVATION)
                .generateSecret(PBEKeySpec(password.toCharArray(), header.salt, header.iterations, KEY_BITS))
        return Cipher
            .getInstance(TRANSFORMATION)
            .apply { init(mode, SecretKeySpec(derived.encoded, KEY_ALGORITHM), GCMParameterSpec(TAG_BITS, header.iv)) }
    }
}
