package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartupHealerTest {
    private val healer = StartupHealer()

    @Test
    fun `heal uses provided failedStatus label`() {
        val saved = mutableListOf<String>()
        healer.heal(
            logTag = "test",
            failedStatus = "FAILED",
            findStale = { listOf("item1") },
            markFailed = { },
            save = { saved.add(it) },
        )
        assertEquals(1, saved.size)
    }

    @Test
    fun `heal does nothing when no stale items`() {
        var saveCalled = false
        healer.heal(
            logTag = "test",
            findStale = { emptyList<String>() },
            markFailed = { saveCalled = true },
            save = { saveCalled = true },
        )
        assertEquals(false, saveCalled)
    }
}
