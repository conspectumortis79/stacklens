package com.stacklens.adapter.out.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeHashingPort
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class DefaultKnowledgeHashing : KnowledgeHashingPort {
    override fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    override fun floatArrayToBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach(buffer::putFloat)
        return buffer.array()
    }

    override fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) out[i] = buffer.getFloat()
        return out
    }

    override fun norm(vector: FloatArray): Double {
        var sum = 0.0
        for (v in vector) sum += (v * v).toDouble()
        return Math.sqrt(sum)
    }
}
