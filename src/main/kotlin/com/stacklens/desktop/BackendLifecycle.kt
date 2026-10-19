package com.stacklens.desktop

interface BackendLifecycle {
    fun start(): Int

    fun stop()

    fun checkpoint()
}
