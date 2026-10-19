package com.stacklens.testsupport

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

open class RecordingSseEmitter : SseEmitter(0L) {
    val dataPayloads: MutableList<String> = mutableListOf()

    val controlPayloads: MutableList<String> = mutableListOf()

    var completeCalls = 0

    var errorSent: Throwable? = null

    var sendFailure: Throwable? = null

    var completionCallbacks = 0

    var timeoutCallbacks = 0

    private var completionCallback: Runnable? = null

    private var timeoutCallback: Runnable? = null

    override fun send(items: MutableSet<ResponseBodyEmitter.DataWithMediaType>) {
        sendFailure?.let { throw it }
        items.forEach { entry ->
            if (entry.mediaType == null) {
                dataPayloads += entry.data.toString()
            } else {
                controlPayloads += entry.data.toString()
            }
        }
    }

    override fun complete() {
        completeCalls++
    }

    override fun completeWithError(e: Throwable) {
        errorSent = e
    }

    override fun onCompletion(callback: Runnable) {
        completionCallbacks++
        completionCallback = callback
    }

    override fun onTimeout(callback: Runnable) {
        timeoutCallbacks++
        timeoutCallback = callback
    }

    fun payloads(): List<String> = dataPayloads + controlPayloads

    fun triggerCompletion() {
        completionCallback?.run()
    }

    fun triggerTimeout() {
        timeoutCallback?.run()
    }
}
