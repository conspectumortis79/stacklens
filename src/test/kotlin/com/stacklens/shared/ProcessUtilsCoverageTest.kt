package com.stacklens.shared

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class ProcessUtilsCoverageTest {
    @Test
    fun `the toolchain lookup finds an installed binary or falls back to the bare name`() {
        val git = ProcessUtils.resolveGitBinary()
        val make = ProcessUtils.resolveMakeBinary()

        assertNotNull(git)
        assertNotNull(make)
        assertTrue(git == "git" || File(git).canExecute(), git)
        assertTrue(make == "make" || File(make).canExecute(), make)
    }

    @Test
    fun `killing a process that cannot be inspected is swallowed`() {
        val proc = mock<Process>()
        whenever(proc.descendants()).thenThrow(IllegalStateException("process already reaped"))

        ProcessUtils.killProcessTree(proc)

        verify(proc).descendants()
    }

    @Test
    fun `killing a finished process needs no force kill`() {
        val proc = mock<Process>()
        whenever(proc.descendants()).thenReturn(
            java.util.stream.Stream
                .empty(),
        )
        whenever(proc.waitFor(any(), any())).thenReturn(true)

        ProcessUtils.killProcessTree(proc)

        verify(proc, org.mockito.kotlin.never()).destroyForcibly()
    }
}
