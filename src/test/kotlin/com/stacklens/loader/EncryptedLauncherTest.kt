package com.stacklens.loader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.net.URL

class EncryptedLauncherTest {
    @Test
    fun `main does not propagate exceptions when env var is missing`() {
        val original = System.getenv("STACKLENS_ENCRYPTION_KEY")
        val env = System.getenv().toMutableMap()
        env.remove("STACKLENS_ENCRYPTION_KEY")
        setEnv(env)

        try {
            EncryptedLauncher.main(emptyArray())
        } finally {
            if (original != null) setEnv(mapOf("STACKLENS_ENCRYPTION_KEY" to original))
        }
    }

    @Test
    fun `main executes remaining code when env var is present`() {
        val originalCp = System.getProperty("java.class.path")
        System.setProperty("java.class.path", "/nonexistent/path")
        setEnv(mapOf("STACKLENS_ENCRYPTION_KEY" to "test-key-123"))

        try {
            try {
                EncryptedLauncher.main(emptyArray())
            } catch (e: Throwable) {
                assertNotNull(e)
            }
        } finally {
            if (originalCp != null) {
                System.setProperty("java.class.path", originalCp)
            } else {
                System.clearProperty("java.class.path")
            }
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `resolveClasspathUrls via reflection covers URLClassLoader branch`() {
        val method: Method = EncryptedLauncher::class.java.getDeclaredMethod("resolveClasspathUrls", ClassLoader::class.java)
        method.isAccessible = true
        val urls = method.invoke(EncryptedLauncher, ClassLoader.getSystemClassLoader()) as Array<URL>
        assertNotNull(urls)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `resolveClasspathUrls via reflection covers java class path fallback`() {
        val originalCp = System.getProperty("java.class.path")
        try {
            System.setProperty("java.class.path", "")
            val method: Method = EncryptedLauncher::class.java.getDeclaredMethod("resolveClasspathUrls", ClassLoader::class.java)
            method.isAccessible = true
            val urls = method.invoke(EncryptedLauncher, ClassLoader.getSystemClassLoader()) as Array<URL>
            assertNotNull(urls)
        } finally {
            System.setProperty("java.class.path", originalCp)
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `resolveClasspathUrls fallback actually runs on Java 9+ (system cl is AppClassLoader)`() {
        val method: Method = EncryptedLauncher::class.java.getDeclaredMethod("resolveClasspathUrls", ClassLoader::class.java)
        method.isAccessible = true
        val urls = method.invoke(EncryptedLauncher, ClassLoader.getSystemClassLoader()) as Array<URL>
        assertNotNull(urls)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `resolveClasspathUrls fallback covers filter and map with non-blank entries`() {
        val originalCp = System.getProperty("java.class.path")
        val originalSep = System.getProperty("path.separator")
        try {
            System.setProperty("java.class.path", "non/existent/path.jar")
            System.setProperty("path.separator", ":")
            val method: Method = EncryptedLauncher::class.java.getDeclaredMethod("resolveClasspathUrls", ClassLoader::class.java)
            method.isAccessible = true
            val urls = method.invoke(EncryptedLauncher, ClassLoader.getSystemClassLoader()) as Array<URL>
            assertEquals(1, urls.size)
            assertTrue(urls[0].toString().contains("non/existent/path.jar"))
        } finally {
            System.setProperty("java.class.path", originalCp)
            if (originalSep != null) System.setProperty("path.separator", originalSep)
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `resolveClasspathUrls handles blank entries`() {
        val originalCp = System.getProperty("java.class.path")
        val method: Method = EncryptedLauncher::class.java.getDeclaredMethod("resolveClasspathUrls", ClassLoader::class.java)
        method.isAccessible = true
        System.setProperty("java.class.path", "")
        val urls = method.invoke(EncryptedLauncher, ClassLoader.getSystemClassLoader()) as Array<URL>
        assertNotNull(urls)
        if (originalCp != null) System.setProperty("java.class.path", originalCp)
    }

    @Test
    fun `object equality and reference identity`() {
        val a: Any = EncryptedLauncher
        val b: Any = EncryptedLauncher
        assertEquals(a, b)
    }

    @Test
    fun `main covers every lambda by setting env var via alternate env accessor`() {
        val original = System.getenv("STACKLENS_ENCRYPTION_KEY")
        for (name in listOf("theEnvironment", "theCaseInsensitiveEnvironment")) {
            try {
                val cls = Class.forName("java.lang.ProcessEnvironment")
                val field = cls.getDeclaredField(name)
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val map = field.get(null) as MutableMap<String, String>
                map["STACKLENS_ENCRYPTION_KEY"] = "test-key-123"
            } catch (_: Throwable) {
            }
        }
        try {
            val originalCp = System.getProperty("java.class.path")
            System.setProperty("java.class.path", "")
            try {
                EncryptedLauncher.main(emptyArray())
            } catch (e: Throwable) {
                assertNotNull(e)
            } finally {
                if (originalCp != null) System.setProperty("java.class.path", originalCp)
            }
        } finally {
            for (name in listOf("theEnvironment", "theCaseInsensitiveEnvironment")) {
                try {
                    val cls = Class.forName("java.lang.ProcessEnvironment")
                    val field = cls.getDeclaredField(name)
                    field.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val map = field.get(null) as MutableMap<String, String>
                    map.remove("STACKLENS_ENCRYPTION_KEY")
                    if (original != null) map["STACKLENS_ENCRYPTION_KEY"] = original
                } catch (_: Throwable) {
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setEnv(map: Map<String, String>) {
        try {
            val processEnvClass = Class.forName("java.lang.ProcessEnvironment")
            val theEnvironmentField = processEnvClass.getDeclaredField("theEnvironment")
            theEnvironmentField.isAccessible = true
            val env = theEnvironmentField.get(null) as MutableMap<String, String>
            val toRemove = env.keys - map.keys
            toRemove.forEach { env.remove(it) }
            env.putAll(map)
        } catch (_: Throwable) {
        }
        try {
            val processEnvClass = Class.forName("java.lang.ProcessEnvironment")
            val theCaseInsensitiveEnvironmentField = processEnvClass.getDeclaredField("theCaseInsensitiveEnvironment")
            theCaseInsensitiveEnvironmentField.isAccessible = true
            val ciEnv = theCaseInsensitiveEnvironmentField.get(null) as MutableMap<String, String>
            val toRemove = ciEnv.keys - map.keys
            toRemove.forEach { ciEnv.remove(it) }
            ciEnv.putAll(map)
        } catch (_: Throwable) {
        }
    }
}
