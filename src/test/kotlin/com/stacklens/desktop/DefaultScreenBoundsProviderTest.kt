package com.stacklens.desktop

import javafx.geometry.Rectangle2D
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultScreenBoundsProviderTest {
    @Test
    fun `primaryBounds returns the rectangle produced by the injected supplier`() {
        val expected = Rectangle2D(10.0, 20.0, 1920.0, 1080.0)
        val provider = DefaultScreenBoundsProvider(boundsSupplier = { expected })
        assertEquals(expected, provider.primaryBounds())
    }

    @Test
    fun `primaryBounds is called every time to pick up dynamic screen changes`() {
        var counter = 0
        val provider =
            DefaultScreenBoundsProvider(boundsSupplier = {
                counter++
                Rectangle2D(0.0, 0.0, counter.toDouble(), counter.toDouble())
            })
        assertEquals(1.0, provider.primaryBounds().width)
        assertEquals(2.0, provider.primaryBounds().height)
        assertEquals(2, counter)
    }
}
