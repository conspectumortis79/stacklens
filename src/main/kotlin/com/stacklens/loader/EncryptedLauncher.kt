
package com.stacklens.loader

import java.net.URLClassLoader
import java.nio.file.Paths

object EncryptedLauncher {
    private const val MAIN_CLASS = "com.stacklens.StackLensApplicationKt"

    private const val KEY_ENV_VAR = "STACKLENS_ENCRYPTION_KEY"

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            runMain(
                args = args,
                envLookup = { key -> System.getenv(key) },
                classpathResolver = ::resolveClasspathUrlsImpl,
                classLoaderFactory = ::defaultClassLoaderFactory,
                contextClassLoaderSetter = ::defaultContextClassLoaderSetter,
                classLoader = ::defaultClassLoader,
                mainClassName = MAIN_CLASS,
            )
        } catch (_: Exception) {
        }
    }

    internal fun defaultClassLoaderFactory(
        urls: Array<java.net.URL>,
        parent: ClassLoader?,
        key: ByteArray,
    ): ClassLoader = EncryptedClassLoader(urls, parent, key)

    internal fun defaultContextClassLoaderSetter(loader: ClassLoader) {
        Thread.currentThread().contextClassLoader = loader
    }

    internal fun defaultClassLoader(): ClassLoader? = Thread.currentThread().contextClassLoader

    internal fun runMain(
        args: Array<String>,
        envLookup: (String) -> String?,
        classpathResolver: () -> Array<java.net.URL>,
        classLoaderFactory: (Array<java.net.URL>, ClassLoader?, ByteArray) -> ClassLoader,
        contextClassLoaderSetter: (ClassLoader) -> Unit,
        classLoader: () -> ClassLoader?,
        mainClassName: String,
        keyDeriver: (String) -> ByteArray = ::deriveKey,
    ) {
        val passphrase =
            envLookup(KEY_ENV_VAR)
                ?: error("$KEY_ENV_VAR environment variable is not set")

        val key = keyDeriver(passphrase)
        val urls = classpathResolver()

        val parent = classLoader()?.parent
        val loader = classLoaderFactory(urls, parent, key)
        contextClassLoaderSetter(loader)

        val appClass = loader.loadClass(mainClassName)
        val mainMethod = appClass.getMethod("main", Array<String>::class.java)
        @Suppress("UNCHECKED_CAST")
        mainMethod.invoke(null, args as Any)
    }

    @JvmName("resolveClasspathUrls")
    internal fun resolveClasspathUrlsImpl(systemClassLoader: ClassLoader = ClassLoader.getSystemClassLoader()): Array<java.net.URL> {
        if (systemClassLoader is URLClassLoader) {
            return systemClassLoader.urLs
        }
        val classPath = System.getProperty("java.class.path", "")
        val sep = System.getProperty("path.separator", ":")
        return classPath
            .split(sep)
            .filter { it.isNotBlank() }
            .map { Paths.get(it).toUri().toURL() }
            .toTypedArray()
    }
}
