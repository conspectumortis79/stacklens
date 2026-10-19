package com.stacklens.application.port.out.process

import java.io.InputStream
import java.util.concurrent.TimeUnit

interface ManagedProcess {
    val isAlive: Boolean

    fun stdout(): InputStream

    fun stderr(): InputStream

    fun waitFor(): Int

    fun waitFor(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean

    fun exitValue(): Int

    fun destroyForcibly()

    fun killTree()
}
