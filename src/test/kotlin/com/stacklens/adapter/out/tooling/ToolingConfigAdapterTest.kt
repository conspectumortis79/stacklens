package com.stacklens.adapter.out.tooling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolingConfigAdapterTest {
    @Test
    fun `the default icon timeout is five seconds`() {
        assertEquals(5L, ToolingConfigAdapter().iconPngTimeoutSeconds)
    }

    @Test
    fun `a configured icon timeout is exposed`() {
        assertEquals(42L, ToolingConfigAdapter(42L).iconPngTimeoutSeconds)
    }
}
