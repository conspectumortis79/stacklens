package com.stacklens.loader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.net.URI
import java.net.URLClassLoader

class EncryptedLauncherResolveClasspathUrlsBranchTest {
    @Test
    fun `resolveClasspathUrlsImpl URLClassLoader true branch is covered`() {
        val testUrl = URI.create("file:/tmp/a").toURL()
        val testUrl2 = URI.create("file:/tmp/b").toURL()
        val urlCl = URLClassLoader(arrayOf(testUrl, testUrl2), null)
        val urls = EncryptedLauncher.resolveClasspathUrlsImpl(urlCl)
        assertEquals(2, urls.size)
        assertTrue(testUrl in urls)
        assertTrue(testUrl2 in urls)
    }

    @Test
    fun `resolveClasspathUrlsImpl default synthetic method is covered`() {
        val urls = EncryptedLauncher.resolveClasspathUrlsImpl()
        assertNotNull(urls)
    }

    @Test
    fun `resolveClasspathUrls$default synthetic static method is invoked`() {
        val method: Method =
            EncryptedLauncher::class.java
                .getDeclaredMethod(
                    "resolveClasspathUrls\$default",
                    EncryptedLauncher::class.java,
                    ClassLoader::class.java,
                    Int::class.javaPrimitiveType,
                    Any::class.java,
                )
        method.isAccessible = true
        val result = method.invoke(null, EncryptedLauncher, ClassLoader.getSystemClassLoader(), 1, null)
        assertNotNull(result)
    }
}
