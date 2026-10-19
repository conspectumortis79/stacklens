package com.stacklens.testsupport

import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class RunnableJarBuilder(
    private val workDir: File,
) {
    private val javaBinary: String = File(System.getProperty("java.home"), "bin/java").absolutePath

    fun javaExecutable(): String = javaBinary

    fun build(
        className: String,
        body: String,
    ): File {
        val sourceDir = File(workDir, "$className-src").apply { mkdirs() }
        val classesDir = File(workDir, "$className-classes").apply { mkdirs() }
        val source = File(sourceDir, "$className.java")
        source.writeText(
            """
            public class $className {
                public static void main(String[] args) throws Exception {
                    $body
                }
            }
            """.trimIndent(),
        )
        compile(source, classesDir)
        return jar(classesDir, className)
    }

    private fun compile(
        source: File,
        classesDir: File,
    ) {
        val javac = javax.tools.ToolProvider.getSystemJavaCompiler() ?: throw IllegalStateException("no JDK compiler available")
        val fileManager = javac.getStandardFileManager(null, null, null)
        val task = javac.getTask(null, fileManager, null, listOf("-d", classesDir.absolutePath), null, fileManager.getJavaFileObjects(source))
        if (!task.call()) throw IllegalStateException("javac failed for ${source.name}")
        fileManager.close()
    }

    private fun jar(
        classesDir: File,
        className: String,
    ): File {
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.MAIN_CLASS] = className
            }
        val jarFile = File(workDir, "$className.jar")
        JarOutputStream(jarFile.outputStream(), manifest).use { out ->
            classesDir
                .walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val entryName =
                        classesDir
                            .toPath()
                            .relativize(file.toPath())
                            .toString()
                            .replace('\\', '/')
                    out.putNextEntry(JarEntry(entryName))
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
        }
        return jarFile
    }
}
