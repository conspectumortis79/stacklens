package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.process.ManagedProcess
import com.stacklens.shared.ProcessUtils
import java.io.InputStream
import java.util.concurrent.TimeUnit

class JvmManagedProcess(
    private val process: Process,
) : ManagedProcess {
    override val isAlive: Boolean get() = process.isAlive

    override fun stdout(): InputStream = process.inputStream

    override fun stderr(): InputStream = process.errorStream

    override fun waitFor(): Int = process.waitFor()

    override fun waitFor(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = process.waitFor(timeout, unit)

    override fun exitValue(): Int = process.exitValue()

    override fun destroyForcibly() {
        process.destroyForcibly()
    }

    override fun killTree() {
        ProcessUtils.killProcessTree(process)
    }

    override fun equals(other: Any?): Boolean = this === other || (other is JvmManagedProcess && other.process === process)

    override fun hashCode(): Int = System.identityHashCode(process)

    override fun toString(): String = "JvmManagedProcess($process)"
}
