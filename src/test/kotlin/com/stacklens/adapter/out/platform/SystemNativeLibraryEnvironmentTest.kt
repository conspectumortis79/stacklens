package com.stacklens.adapter.out.platform

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SystemNativeLibraryEnvironmentTest {
    private val environment = SystemNativeLibraryEnvironment()

    @Test
    fun `setProperty stores a value that property reads back`() {
        environment.setProperty("stacklens.test.nativeLibrary.value", "stored")

        assertEquals("stored", environment.property("stacklens.test.nativeLibrary.value"))
        assertNull(environment.property("stacklens.test.nativeLibrary.absent"))
    }

    @Test
    fun `absolutePathOf joins directory and name the same way the file api does`() {
        assertEquals(File("opt", "lib.so").absolutePath, environment.absolutePathOf("opt", "lib.so"))
    }

    @Test
    fun `loadLibrary hands an existing shared library over to jna`() {
        val library = File(System.getProperty("java.home"), "lib/" + System.mapLibraryName("java"))
        assertTrue(library.isFile, "expected a jdk shared library at $library")

        assertDoesNotThrow { environment.loadLibrary(library.absolutePath) }
    }
}
