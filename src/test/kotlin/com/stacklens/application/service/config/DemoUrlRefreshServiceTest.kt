package com.stacklens.application.service.config

import com.stacklens.application.port.out.async.StartupTaskExecutor
import com.stacklens.application.port.out.config.DemoUrlAlignmentPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DemoUrlRefreshServiceTest {
    private val alignment: DemoUrlAlignmentPort = mock()

    private class RecordingStartupTaskExecutor : StartupTaskExecutor {
        val tasks = mutableListOf<() -> Unit>()

        override fun submit(task: () -> Unit) {
            tasks += task
        }
    }

    private fun service(executor: StartupTaskExecutor) = DemoUrlRefreshService(alignment, executor)

    @Test
    fun `defers the alignment to the startup executor`() {
        val executor = RecordingStartupTaskExecutor()

        service(executor).refreshDemoUrlsInBackground()

        assertEquals(1, executor.tasks.size)
        verify(alignment, never()).alignAll()

        executor.tasks.single().invoke()
        verify(alignment, times(1)).alignAll()
    }

    @Test
    fun `aligns demo urls immediately with the inline executor`() {
        whenever(alignment.alignAll()).thenReturn(listOf("GitLab:Demo GitLab→http://host.docker.internal:3080"))

        service(
            object : StartupTaskExecutor {
                override fun submit(task: () -> Unit) {
                    task()
                }
            },
        ).refreshDemoUrlsInBackground()

        verify(alignment, times(1)).alignAll()
    }

    @Test
    fun `runs the alignment when no demo url is stale`() {
        whenever(alignment.alignAll()).thenReturn(emptyList())
        val executor = RecordingStartupTaskExecutor()

        service(executor).refreshDemoUrlsInBackground()
        executor.tasks.single().invoke()

        verify(alignment, times(1)).alignAll()
    }
}
