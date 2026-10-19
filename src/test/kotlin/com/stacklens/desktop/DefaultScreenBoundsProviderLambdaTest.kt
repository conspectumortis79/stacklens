package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class DefaultScreenBoundsProviderLambdaTest {
    @Test
    fun `no-arg constructor is invocable`() {
        assertDoesNotThrow {
            DefaultScreenBoundsProvider()
        }
    }

    @Test
    fun `provider with custom supplier is invocable`() {
        val provider = DefaultScreenBoundsProvider(boundsSupplier = { javafx.geometry.Rectangle2D(0.0, 0.0, 100.0, 100.0) })
        assertNotNull(provider)
    }
}
