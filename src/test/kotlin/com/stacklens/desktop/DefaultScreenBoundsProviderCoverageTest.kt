package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultScreenBoundsProviderCoverageTest {
    @Test
    fun `the default bounds supplier reports the primary screen`() {
        JavafxTestSupport.init()
        val setup = DefaultScreenBoundsProvider()

        val bounds = setup.primaryBounds()

        assertTrue(bounds.width > 0.0, "width was ${bounds.width}")
        assertTrue(bounds.height > 0.0, "height was ${bounds.height}")
    }
}
