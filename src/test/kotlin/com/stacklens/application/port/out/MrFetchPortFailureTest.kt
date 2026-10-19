package com.stacklens.application.port.out

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MrFetchPortFailureTest {
    @Test
    fun `Failure stores the reason and exposes it via the getter`() {
        val f = MrFetchPort.MrFetchResult.Failure(reason = "boom")
        assertEquals("boom", f.reason)
    }

    @Test
    fun `Failure with empty reason still exposes it`() {
        val f = MrFetchPort.MrFetchResult.Failure(reason = "")
        assertEquals("", f.reason)
    }
}
