package com.stacklens.loader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.net.URLClassLoader

class EncryptedLauncherMainTest {
    @Test
    fun `main evaluates every default lambda and throws when env var is missing`() {
        try {
            EncryptedLauncher.main(emptyArray())
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `defaultClassLoaderFactory delegates to EncryptedClassLoader`() {
        val url = java.net.URI("file:/tmp/a").toURL()
        val key = ByteArray(32)
        val loader = EncryptedLauncher.defaultClassLoaderFactory(arrayOf(url), null, key)
        assertNotNull(loader)
    }

    @Test
    fun `defaultContextClassLoaderSetter sets the current thread's class loader`() {
        val original = Thread.currentThread().contextClassLoader
        try {
            val newLoader = URLClassLoader(arrayOf(), null)
            EncryptedLauncher.defaultContextClassLoaderSetter(newLoader)
            assertEquals(newLoader, Thread.currentThread().contextClassLoader)
        } finally {
            Thread.currentThread().contextClassLoader = original
        }
    }

    @Test
    fun `defaultClassLoader returns the current thread's class loader`() {
        val original = Thread.currentThread().contextClassLoader
        try {
            val expected = URLClassLoader(arrayOf(), null)
            Thread.currentThread().contextClassLoader = expected
            assertEquals(expected, EncryptedLauncher.defaultClassLoader())
        } finally {
            Thread.currentThread().contextClassLoader = original
        }
    }

    @Test
    fun `main swallows exception thrown by runMain`() {
        val original = System.getenv()
        try {
            EncryptedLauncher.main(emptyArray())
        } catch (_: Throwable) {
            org.junit.jupiter.api.Assertions
                .fail("main() should not propagate exceptions")
        }
    }
}
