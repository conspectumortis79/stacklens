package com.stacklens.application.service.pipeline.loopback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoopbackMarkersTest {
    @Test
    fun `HANDOFF_TO_FINN uses the documented emoji`() {
        assertEquals("🔄 HANDOFF_TO_FINN", HANDOFF_TO_FINN)
    }

    @Test
    fun `HANDOFF_TO_TESS uses the documented emoji`() {
        assertEquals("🔄 HANDOFF_TO_TESS", HANDOFF_TO_TESS)
    }

    @Test
    fun `HANDOFF_TO_SYD uses the documented emoji`() {
        assertEquals("🔄 HANDOFF_TO_SYD", HANDOFF_TO_SYD)
    }

    @Test
    fun `HANDOFF_TO_FINN is distinct from HANDOFF_TO_TESS`() {
        assert(HANDOFF_TO_FINN != HANDOFF_TO_TESS)
    }
}
