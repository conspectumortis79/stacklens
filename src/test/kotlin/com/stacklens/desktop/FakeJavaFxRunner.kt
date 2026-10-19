package com.stacklens.desktop

class FakeJavaFxRunner : JavaFxRunner {
    val submitted: MutableList<() -> Unit> = mutableListOf()

    override fun runOnFxThread(block: () -> Unit) {
        submitted += block
    }

    fun reset() {
        submitted.clear()
    }

    fun runSubmitted() {
        submitted.lastOrNull()?.invoke()
    }
}
