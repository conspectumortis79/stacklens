package com.stacklens.adapter.out.compiler

import com.stacklens.shared.ToolJarCommand
import com.stacklens.testsupport.RunnableJarBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class JarRuntimeCheckerTest {
    @Test
    fun `check returns null when the jar runs cleanly and exits with zero`(
        @TempDir tmp: Path,
    ) {
        val jars = RunnableJarBuilder(tmp.toFile())
        val jar = jars.build("CleanExit", """System.out.println("hello"); System.exit(0);""")

        assertNull(JarRuntimeChecker { jars.javaExecutable() }.check(jar.absolutePath, timeoutSeconds = 10))
    }

    @Test
    fun `check returns null when the default timeout is used for a jar that exits silently`(
        @TempDir tmp: Path,
    ) {
        val jars = RunnableJarBuilder(tmp.toFile())
        val jar = jars.build("SilentExit", "System.exit(0);")

        assertNull(JarRuntimeChecker { jars.javaExecutable() }.check(jar.absolutePath))
    }

    @Test
    fun `check launches the jar with the same command a saved tool is run with later`() {
        val command = JarRuntimeChecker { "/usr/bin/java" }.launchCommand("/tmp/tool.jar")

        assertEquals(ToolJarCommand.of("/usr/bin/java", "/tmp/tool.jar"), command)
    }

    @Test
    fun `check does not restrict the awt capability of the launched jar`() {
        val command = JarRuntimeChecker { "/usr/bin/java" }.launchCommand("/tmp/tool.jar")

        assertTrue(command.none { it.contains("headless") }, "unexpected capability restriction in $command")
    }

    @Test
    fun `check accepts a jar that only fails when the launcher forces headless mode`(
        @TempDir tmp: Path,
    ) {
        val jars = RunnableJarBuilder(tmp.toFile())
        val jar =
            jars.build(
                "HeadlessOnlyFailure",
                """
                if ("true".equals(System.getProperty("java.awt.headless"))) {
                    System.out.println("Exception in thread \"AWT-EventQueue-0\" java.awt.HeadlessException: forced by launcher");
                    System.out.println("\tat java.desktop/java.awt.GraphicsEnvironment.checkHeadless(GraphicsEnvironment.java:158)");
                }
                System.exit(0);
                """.trimIndent(),
            )

        val result = JarRuntimeChecker { jars.javaExecutable() }.check(jar.absolutePath, timeoutSeconds = 10)

        assertNull(result, "the checker crippled the jar it was meant to smoke-test: $result")
    }

    @Test
    fun `check returns the printed throwable when the jar exits zero after a jvm crash`(
        @TempDir tmp: Path,
    ) {
        val jars = RunnableJarBuilder(tmp.toFile())
        val jar =
            jars.build(
                "CrashedEdt",
                """
                Thread thread = new Thread(() -> { throw new RuntimeException("simulated EDT crash"); }, "AWT-EventQueue-0");
                thread.start();
                thread.join();
                System.exit(0);
                """.trimIndent(),
            )

        val result = JarRuntimeChecker { jars.javaExecutable() }.check(jar.absolutePath, timeoutSeconds = 10)

        assertNotNull(result)
        assertTrue(result!!.contains("Exception in thread \"AWT-EventQueue-0\""), "unexpected output: $result")
        assertTrue(result.contains("RuntimeException: simulated EDT crash"), "unexpected output: $result")
    }

    @Test
    fun `check reports the throwable signature when the jar prints a stack frame and exits zero`(
        @TempDir tmp: Path,
    ) {
        val jars = RunnableJarBuilder(tmp.toFile())
        val jar =
            jars.build(
                "PrintedThrowable",
                """
                System.out.println("kotlin.KotlinNullPointerException: boom");
                System.out.println("\tat PrintedThrowable.main(PrintedThrowable.java:1)");
                System.exit(0);
                """.trimIndent(),
            )

        val result = JarRuntimeChecker { jars.javaExecutable() }.check(jar.absolutePath, timeoutSeconds = 10)

        assertNotNull(result)
        assertTrue(result!!.startsWith("kotlin.KotlinNullPointerException"), "unexpected output: $result")
    }

    @Test
    fun `check returns the error output when the jar file cannot be accessed`(
        @TempDir tmp: Path,
    ) {
        val jars = RunnableJarBuilder(tmp.toFile())
        val missing = File(tmp.toFile(), "does-not-exist.jar")

        val result = JarRuntimeChecker { jars.javaExecutable() }.check(missing.absolutePath, timeoutSeconds = 10)

        assertNotNull(result)
        assertTrue(result!!.contains(missing.name), "unexpected output: $result")
    }

    @Test
    fun `check returns the fallback message when the jar exits non zero without output`(
        @TempDir tmp: Path,
    ) {
        val jars = RunnableJarBuilder(tmp.toFile())
        val jar = jars.build("SilentFailure", "System.exit(7);")

        val result = JarRuntimeChecker { jars.javaExecutable() }.check(jar.absolutePath, timeoutSeconds = 10)

        assertEquals("Process exited with code 7", result)
    }

    @Test
    fun `check returns the output when the jar exits non zero with a message`(
        @TempDir tmp: Path,
    ) {
        val jars = RunnableJarBuilder(tmp.toFile())
        val jar =
            jars.build(
                "NoisyFailure",
                """
                System.out.println("java.lang.IllegalStateException: boom");
                System.exit(3);
                """.trimIndent(),
            )

        val result = JarRuntimeChecker { jars.javaExecutable() }.check(jar.absolutePath, timeoutSeconds = 10)

        assertTrue(result!!.contains("java.lang.IllegalStateException: boom"), "unexpected output: $result")
    }

    @Test
    fun `check returns null when the jar keeps running beyond the timeout`(
        @TempDir tmp: Path,
    ) {
        val jars = RunnableJarBuilder(tmp.toFile())
        val jar = jars.build("LongRunner", "Thread.sleep(30000);")

        assertNull(JarRuntimeChecker { jars.javaExecutable() }.check(jar.absolutePath, timeoutSeconds = 1))
    }

    @Test
    fun `check returns null when the configured java executable cannot be launched`() {
        assertNull(JarRuntimeChecker { "/no/such/java" }.check("whatever.jar", timeoutSeconds = 1))
    }
}
