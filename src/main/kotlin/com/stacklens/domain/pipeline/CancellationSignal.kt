package com.stacklens.domain.pipeline

fun interface CancellationSignal {
    fun cancelRequest()
}
