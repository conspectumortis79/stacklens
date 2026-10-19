package com.stacklens.testsupport

import java.io.File
import kotlin.reflect.KClass

object PersistentTypes {
    fun annotatedIn(
        rootPackage: String,
        vararg annotations: KClass<out Annotation>,
    ): List<Class<*>> {
        val wanted = annotations.map { it.java }
        return classesIn(rootPackage)
            .filter { type -> wanted.any { type.isAnnotationPresent(it) } }
            .sortedBy { it.name }
    }

    fun classesIn(rootPackage: String): List<Class<*>> =
        packageDirectories(rootPackage)
            .flatMap { directory -> directory.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.map { it.toBinaryName(directory, rootPackage) } }
            .mapNotNull { name -> runCatching { Class.forName(name, false, classLoader()) }.getOrNull() }
            .filter { !it.isInterface && !it.isEnum && !it.isAnnotation && !it.isSynthetic }
            .distinctBy { it.name }
            .sortedBy { it.name }

    private fun File.toBinaryName(
        root: File,
        rootPackage: String,
    ): String =
        rootPackage +
            "." +
            absolutePath
                .removePrefix(root.absolutePath + File.separator)
                .removeSuffix(".class")
                .replace(File.separatorChar, '.')

    private fun packageDirectories(rootPackage: String): List<File> =
        classLoader()
            .getResources(rootPackage.replace('.', '/'))
            .toList()
            .mapNotNull { url -> runCatching { File(url.toURI()) }.getOrNull() }
            .filter { it.isDirectory && "/kotlin/test/" !in it.absolutePath.replace(File.separatorChar, '/') }

    private fun classLoader(): ClassLoader = PersistentTypes::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
}
