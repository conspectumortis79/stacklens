package com.stacklens.adapter.out.ai

internal object AiCancellation {
    const val MESSAGE_PREFIX = "AI completion cancelled"

    fun checkCancelled(
        isCancelled: (() -> Boolean)?,
        subject: String,
    ) {
        if (isCancelled?.invoke() == true) throw InterruptedException("$MESSAGE_PREFIX: $subject")
    }
}
