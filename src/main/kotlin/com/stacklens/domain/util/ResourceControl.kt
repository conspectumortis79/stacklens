package com.stacklens.domain.util

fun quietly(action: () -> Unit) {
    try {
        action()
    } catch (_: Throwable) {
    }
}

fun <T : AutoCloseable, R> T.closingAuto(block: (T) -> R): R {
    val thrown = ArrayList<Throwable>(1)
    try {
        return block(this)
    } catch (t: Throwable) {
        thrown += t
        throw t
    } finally {
        try {
            close()
        } catch (t: Throwable) {
            if (thrown.isEmpty()) throw t else thrown[0].addSuppressed(t)
        }
    }
}
