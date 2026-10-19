package com.stacklens.domain.knowledge

class KnowledgeArchiveHeader(
    val salt: ByteArray,
    val iv: ByteArray,
    val iterations: Int,
) {
    val ciphertextOffset: Int
        get() = HEADER_BYTES

    fun encode(): ByteArray {
        val out = ByteArray(HEADER_BYTES)
        KnowledgeArchiveMagic.SLKN.toByteArray(Charsets.US_ASCII).copyInto(out, MAGIC_OFFSET)
        out[VERSION_OFFSET] = FORMAT_VERSION.toByte()
        salt.copyInto(out, SALT_OFFSET)
        iv.copyInto(out, IV_OFFSET)
        writeInt(out, ITERATIONS_OFFSET, iterations)
        return out
    }

    companion object {
        const val FORMAT_VERSION: Int = 1
        const val DEFAULT_PBKDF2_ITERATIONS: Int = 310_000
        const val SALT_BYTES: Int = 16
        const val IV_BYTES: Int = 12
        private const val MAGIC_OFFSET: Int = 0
        private const val VERSION_OFFSET: Int = MAGIC_OFFSET + 4
        private const val RESERVED_BYTES: Int = 3
        private const val SALT_OFFSET: Int = VERSION_OFFSET + 1 + RESERVED_BYTES
        private const val IV_OFFSET: Int = SALT_OFFSET + SALT_BYTES
        private const val ITERATIONS_OFFSET: Int = IV_OFFSET + IV_BYTES
        val HEADER_BYTES: Int = ITERATIONS_OFFSET + 4

        fun isEncryptedArchive(bytes: ByteArray): Boolean =
            bytes.size >= VERSION_OFFSET &&
                String(bytes, MAGIC_OFFSET, VERSION_OFFSET, Charsets.US_ASCII) == KnowledgeArchiveMagic.SLKN

        fun decode(bytes: ByteArray): KnowledgeArchiveHeader {
            require(isEncryptedArchive(bytes)) { "not a StackLens knowledge archive" }
            require(bytes.size >= HEADER_BYTES) {
                "Truncated knowledge archive header: expected $HEADER_BYTES bytes, got ${bytes.size}"
            }
            val version = bytes[VERSION_OFFSET].toInt() and 0xFF
            require(version == FORMAT_VERSION) { "Unsupported archive version: $version" }
            return KnowledgeArchiveHeader(
                salt = bytes.copyOfRange(SALT_OFFSET, IV_OFFSET),
                iv = bytes.copyOfRange(IV_OFFSET, ITERATIONS_OFFSET),
                iterations = readInt(bytes, ITERATIONS_OFFSET),
            )
        }

        private fun writeInt(
            target: ByteArray,
            offset: Int,
            value: Int,
        ) {
            target[offset] = ((value ushr 24) and 0xFF).toByte()
            target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
            target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
            target[offset + 3] = (value and 0xFF).toByte()
        }

        private fun readInt(
            source: ByteArray,
            offset: Int,
        ): Int =
            ((source[offset].toInt() and 0xFF) shl 24) or
                ((source[offset + 1].toInt() and 0xFF) shl 16) or
                ((source[offset + 2].toInt() and 0xFF) shl 8) or
                (source[offset + 3].toInt() and 0xFF)
    }
}
