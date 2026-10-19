package com.stacklens.testsupport

import java.io.File
import java.io.FileOutputStream
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

object JarFileFixtures {
    fun minimalJar(): ByteArray =
        createJar(
            extraMainAttrs = emptyMap(),
            compiledClassName = null,
        )

    fun markerJar(
        name: String = "",
        icon: String = "",
    ): ByteArray {
        val attrs =
            mutableMapOf<String, String>(
                "X-StackLens-Tool" to "true",
            )
        if (name.isNotBlank()) attrs["X-StackLens-Name"] = name
        if (icon.isNotBlank()) attrs["X-StackLens-Icon"] = icon
        return createJar(extraMainAttrs = attrs, compiledClassName = null)
    }

    fun echoHelloJar(): ByteArray =
        createJar(
            extraMainAttrs = mapOf("Main-Class" to "EchoHello"),
            compiledClassName = "EchoHello",
        )

    fun echoLongJar(): ByteArray =
        createJar(
            extraMainAttrs = mapOf("Main-Class" to "EchoLong"),
            compiledClassName = "EchoLong",
        )

    private fun createJar(
        extraMainAttrs: Map<String, String>,
        compiledClassName: String?,
    ): ByteArray {
        val tempFile = File.createTempFile("jar-fixture-", ".jar")
        tempFile.deleteOnExit()

        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        extraMainAttrs.forEach { (k, v) -> manifest.mainAttributes[Attributes.Name(k)] = v }

        JarOutputStream(FileOutputStream(tempFile), manifest).use { jos ->
            compiledClassName?.let { className ->
                val classFile = compileJavaClass(className)
                jos.putNextEntry(JarEntry("$className.class"))
                classFile.inputStream().use { it.copyTo(jos) }
                jos.closeEntry()
                classFile.delete()
            }
        }
        return tempFile.readBytes()
    }

    private fun compileJavaClass(className: String): File {
        val source =
            when (className) {
                "EchoHello" ->
                    """
                    public class EchoHello {
                        public static void main(String[] args) {
                            System.out.println("hello");
                        }
                    }
                    """.trimIndent()
                "EchoLong" ->
                    """
                    public class EchoLong {
                        public static void main(String[] args) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < 3000; i++) {
                                sb.append("abcdefghij");
                            }
                            System.out.print(sb.toString());
                        }
                    }
                    """.trimIndent()
                else -> error("Unknown fixture class $className")
            }
        val tmpDir = File.createTempFile("fixture-build-", "")
        tmpDir.delete()
        tmpDir.mkdirs()
        tmpDir.deleteOnExit()
        val sourceFile =
            File(tmpDir, "$className.java").apply {
                writeText(source)
                deleteOnExit()
            }
        val compiler = javax.tools.ToolProvider.getSystemJavaCompiler()
        val fileManager = compiler.getStandardFileManager(null, null, null)
        val units = fileManager.getJavaFileObjects(sourceFile)
        val success = compiler.getTask(null, fileManager, null, null, null, units).call()
        check(success) { "Failed to compile $className.java for the test JAR" }
        return File(tmpDir, "$className.class")
    }
}
