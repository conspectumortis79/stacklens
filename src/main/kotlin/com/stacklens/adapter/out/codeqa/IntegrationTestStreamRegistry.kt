package com.stacklens.adapter.out.codeqa

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class IntegrationTestStreamRegistry {
    private val buffers = ConcurrentHashMap<Long, StringBuilder>()

    fun open(id: Long): StringBuilder = buffers.computeIfAbsent(id) { StringBuilder() }

    fun read(id: Long): String? = buffers[id]?.let { buffer -> synchronized(buffer) { buffer.toString() } }

    fun close(id: Long) {
        buffers.remove(id)
    }
}
