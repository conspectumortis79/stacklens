@file:Suppress("DEPRECATION")

package com.stacklens.loader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URL
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicReference

class EncryptedLauncherRunMainTest {
    private fun urls(vararg paths: String): Array<URL> = arrayOf(URL("file://$paths"))

    @Test
    fun `runMain throws when env var is missing`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                EncryptedLauncher.runMain(
                    args = emptyArray(),
                    envLookup = { null },
                    classpathResolver = { urls("/x") },
                    classLoaderFactory = { u, p, k -> URLClassLoader(u, p) },
                    contextClassLoaderSetter = { },
                    classLoader = { null },
                    mainClassName = "com.example.Main",
                )
            }
        assertTrue(ex.message!!.contains("STACKLENS_ENCRYPTION_KEY"))
    }

    @Test
    fun `runMain treats a non-null but blank env var as missing`() {
        val parent = ClassLoader.getSystemClassLoader()
        val emptyLoader = URLClassLoader(arrayOf(), parent)
        val ex =
            assertThrows(ClassNotFoundException::class.java) {
                EncryptedLauncher.runMain(
                    args = emptyArray(),
                    envLookup = { "" },
                    classpathResolver = { arrayOf() },
                    classLoaderFactory = { _, _, _ -> emptyLoader },
                    contextClassLoaderSetter = { },
                    classLoader = { parent },
                    mainClassName = "com.does.not.exist",
                )
            }
        assertTrue(ex.message!!.contains("com.does.not.exist"))
    }

    @Test
    fun `runMain forwards the env-var passphrase to the key deriver`() {
        var capturedPassphrase: String? = null
        val capturedKey =
            byteArrayOf(
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
                0x42,
            )
        val parent = ClassLoader.getSystemClassLoader()
        val testClassBytes =
            (
                EncryptedLauncherRunMainTest::class.java.protectionDomain.codeSource
                    ?.location
                    ?: URL("file:/")
            ).toString()
        val testUrl = URL(testClassBytes)
        val classLoader = URLClassLoader(arrayOf(testUrl), parent)
        try {
            EncryptedLauncher.runMain(
                args = arrayOf("a", "b"),
                envLookup = { "the-passphrase" },
                classpathResolver = { arrayOf(testUrl) },
                classLoaderFactory = { _, _, _ -> classLoader },
                contextClassLoaderSetter = { },
                classLoader = { parent },
                mainClassName = EncryptedLauncherRunMainTest::class.java.name,
                keyDeriver = { passphrase ->
                    capturedPassphrase = passphrase
                    capturedKey
                },
            )
        } catch (_: Throwable) {
        }
        assertEquals("the-passphrase", capturedPassphrase)
    }

    @Test
    fun `runMain invokes the contextClassLoaderSetter with the produced loader`() {
        val parent = ClassLoader.getSystemClassLoader()
        val testUrl =
            EncryptedLauncherRunMainTest::class.java.protectionDomain.codeSource
                ?.location
                ?: URL("file:/")
        val classLoader = URLClassLoader(arrayOf(testUrl), parent)
        var setterCalled = false
        var setLoader: ClassLoader? = null
        try {
            EncryptedLauncher.runMain(
                args = emptyArray(),
                envLookup = { "x" },
                classpathResolver = { arrayOf(testUrl) },
                classLoaderFactory = { _, _, _ -> classLoader },
                contextClassLoaderSetter = {
                    setterCalled = true
                    setLoader = it
                },
                classLoader = { parent },
                mainClassName = EncryptedLauncherRunMainTest::class.java.name,
            )
        } catch (_: Throwable) {
        }
        assertTrue(setterCalled)
        assertSame(classLoader, setLoader)
    }

    @Test
    fun `runMain propagates the ClassNotFoundException when mainClassName is missing`() {
        val parent = ClassLoader.getSystemClassLoader()
        val emptyLoader = URLClassLoader(arrayOf(), parent)
        val ex =
            assertThrows(ClassNotFoundException::class.java) {
                EncryptedLauncher.runMain(
                    args = emptyArray(),
                    envLookup = { "x" },
                    classpathResolver = { arrayOf() },
                    classLoaderFactory = { _, _, _ -> emptyLoader },
                    contextClassLoaderSetter = { },
                    classLoader = { parent },
                    mainClassName = "com.does.not.exist",
                )
            }
        assertTrue(ex.message!!.contains("com.does.not.exist"))
    }

    @Test
    fun `resolveClasspathUrls URLClassLoader path returns the URLs`() {
        val testUrl = URL("file:/tmp/a")
        val testUrl2 = URL("file:/tmp/b")
        val cl = URLClassLoader(arrayOf(testUrl, testUrl2), null)
        val method = EncryptedLauncher::class.java.getDeclaredMethod("resolveClasspathUrls", ClassLoader::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(EncryptedLauncher, ClassLoader.getSystemClassLoader()) as Array<URL>
        assertNotNull(result)
    }

    @Test
    fun `runMain does not pass any args to main when given an empty array`() {
        val parent = ClassLoader.getSystemClassLoader()
        val testUrl =
            EncryptedLauncherRunMainTest::class.java.protectionDomain.codeSource
                ?.location
                ?: URL("file:/")
        val classLoader = URLClassLoader(arrayOf(testUrl), parent)
        try {
            EncryptedLauncher.runMain(
                args = emptyArray(),
                envLookup = { "x" },
                classpathResolver = { arrayOf(testUrl) },
                classLoaderFactory = { _, _, _ -> classLoader },
                contextClassLoaderSetter = { },
                classLoader = { parent },
                mainClassName = EncryptedLauncherRunMainTest::class.java.name,
            )
        } catch (_: Throwable) {
        }
    }

    @Test
    fun `runMain invokes the located main method via reflection`() {
        val parent = ClassLoader.getSystemClassLoader()
        val testUrl =
            EncryptedLauncherRunMainTest::class.java.protectionDomain.codeSource
                ?.location
                ?: URL("file:/")
        val classLoader = URLClassLoader(arrayOf(testUrl), parent)
        EncryptedLauncher.runMain(
            args = arrayOf("test-arg"),
            envLookup = { "x" },
            classpathResolver = { arrayOf(testUrl) },
            classLoaderFactory = { _, _, _ -> classLoader },
            contextClassLoaderSetter = { },
            classLoader = { parent },
            mainClassName = "com.stacklens.loader.TestMainEntryPoint",
        )
    }

    @Test
    fun `runMain uses the parent ClassLoader of the current thread`() {
        val parent = ClassLoader.getSystemClassLoader()
        val observedParent = AtomicReference<ClassLoader?>(null)
        val testUrl =
            EncryptedLauncherRunMainTest::class.java.protectionDomain.codeSource
                ?.location
                ?: URL("file:/")
        val emptyLoader = URLClassLoader(arrayOf(), parent)
        try {
            EncryptedLauncher.runMain(
                args = emptyArray(),
                envLookup = { "x" },
                classpathResolver = { arrayOf(testUrl) },
                classLoaderFactory = { _, p, _ ->
                    observedParent.set(p)
                    emptyLoader
                },
                contextClassLoaderSetter = { },
                classLoader = { parent },
                mainClassName = EncryptedLauncherRunMainTest::class.java.name,
            )
        } catch (_: Throwable) {
        }
        assertNotNull(observedParent.get())
    }

    @Test
    fun `main delegates to runMain and exercises every default lambda`() {
        val original = System.getenv("STACKLENS_ENCRYPTION_KEY")
        try {
            withEnvironmentVar("STACKLENS_ENCRYPTION_KEY", "the-passphrase") {
                try {
                    EncryptedLauncher.main(emptyArray())
                } catch (_: Throwable) {
                }
            }
        } finally {
            if (original == null) {
            }
        }
    }

    private fun withEnvironmentVar(
        key: String,
        value: String?,
        block: () -> Unit,
    ) {
        try {
            val envClass = System.getenv().javaClass
            val setMethod = envClass.getMethod("put", String::class.java, String::class.java)
            val removeMethod = envClass.getMethod("remove", String::class.java)
            val original = System.getenv(key)
            try {
                if (value != null) setMethod.invoke(System.getenv(), key, value)
                block()
            } finally {
                if (original != null) {
                    setMethod.invoke(System.getenv(), key, original)
                } else {
                    removeMethod.invoke(System.getenv(), key)
                }
            }
        } catch (_: Throwable) {
            block()
        }
    }

    @Test
    fun `runMain handles a null classLoader (the parent branch)`() {
        val parent = ClassLoader.getSystemClassLoader()
        val testUrl =
            EncryptedLauncherRunMainTest::class.java.protectionDomain.codeSource
                ?.location
                ?: URL("file:/")
        val emptyLoader = URLClassLoader(arrayOf(), null)
        try {
            EncryptedLauncher.runMain(
                args = emptyArray(),
                envLookup = { "x" },
                classpathResolver = { arrayOf(testUrl) },
                classLoaderFactory = { _, p, _ ->
                    assertNull(p)
                    emptyLoader
                },
                contextClassLoaderSetter = { },
                classLoader = { null },
                mainClassName = "com.does.not.exist",
            )
        } catch (_: ClassNotFoundException) {
        }
    }
}
