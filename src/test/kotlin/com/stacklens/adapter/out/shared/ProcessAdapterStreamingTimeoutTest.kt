package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ProcessAdapterStreamingTimeoutTest {
    private val adapter = ProcessAdapter()

    private fun script(
        dir: Path,
        name: String,
        body: String,
    ): File =
        File(dir.toFile(), name).apply {
            writeText("#!/bin/sh\n$body\n")
            setExecutable(true)
        }

    @Test
    fun `a process that stays silent past its deadline is killed`(
        @TempDir dir: Path,
    ) {
        val silent = script(dir, "silent.sh", "sleep 30")

        val failure =
            assertThrows<ProcessExecutionException> {
                adapter.runStreaming(listOf(silent.absolutePath), null, 1L) {}
            }
        assertTrue(failure.message!!.contains("Process timed out after 1s"), failure.message)
    }

    @Test
    fun `a stream that closes while the process keeps running is treated as a timeout`(
        @TempDir dir: Path,
    ) {
        val detached = script(dir, "detached.sh", "exec 1>&-\nexec 2>&-\nsleep 30")

        val failure =
            assertThrows<ProcessExecutionException> {
                adapter.runStreaming(listOf(detached.absolutePath), null, 1L) {}
            }
        assertTrue(failure.message!!.contains("Process timed out after 1s"), failure.message)
    }
}
