package com.stacklens.application.service.codeqa

import com.stacklens.application.stereotype.ApplicationService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationService
class CodeQaStreamingRegistry {
    private val streamBuffers = ConcurrentHashMap<Long, String>()
    private val streamingChangeListeners =
        ConcurrentHashMap<Long, CopyOnWriteArrayList<() -> Unit>>()

    fun getBuffer(id: Long): String? = streamBuffers[id]

    fun setBuffer(
        id: Long,
        content: String,
    ) {
        streamBuffers[id] = content
        notifyChange(id)
    }

    fun clear(id: Long) {
        streamBuffers.remove(id)
        streamingChangeListeners.remove(id)
    }

    fun registerChangeListener(
        id: Long,
        listener: () -> Unit,
    ): () -> Unit {
        val list = streamingChangeListeners.computeIfAbsent(id) { CopyOnWriteArrayList() }
        list.add(listener)
        return { unregisterChangeListener(id, listener) }
    }

    fun unregisterChangeListener(
        id: Long,
        listener: () -> Unit,
    ) {
        streamingChangeListeners[id]?.remove(listener)
    }

    private fun notifyChange(id: Long) {
        val list = streamingChangeListeners[id] ?: return
        for (listener in list) {
            runCatching { listener() }
        }
    }
}
