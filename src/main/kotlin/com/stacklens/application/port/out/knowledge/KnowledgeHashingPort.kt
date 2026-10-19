package com.stacklens.application.port.out.knowledge

interface KnowledgeHashingPort {
    fun sha256Hex(text: String): String

    fun floatArrayToBytes(vector: FloatArray): ByteArray

    fun bytesToFloatArray(bytes: ByteArray): FloatArray

    fun norm(vector: FloatArray): Double
}
