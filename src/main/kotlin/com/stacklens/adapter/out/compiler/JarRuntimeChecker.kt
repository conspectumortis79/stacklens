package com.stacklens.adapter.out.compiler

import com.stacklens.shared.ToolJarCommand
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class JarRuntimeChecker(
    private val javaExecutableProvider: () -> String,
) {
    private val log = LoggerFactory.getLogger(JarRuntimeChecker::class.java)

    internal fun launchCommand(jarPath: String): List<String> = ToolJarCommand.of(javaExecutableProvider(), jarPath)

    fun check(
        jarPath: String,
        timeoutSeconds: Long = 8,
    ): String? =
        try {
            val pb =
                ProcessBuilder(launchCommand(jarPath))
                    .redirectErrorStream(true)
            val process = pb.start()
            val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                log.debug("[RuntimeCheck] JAR {} started successfully (still running after {}s)", jarPath, timeoutSeconds)
                return null
            }
            val exitCode = process.exitValue()
            val output = readProcessOutput(process)
            if (exitCode == 0) {
                if (JvmThrowableSignature.looksLikeThrowable(output)) {
                    log.warn(
                        "[RuntimeCheck] JAR {} exited 0 but stdout contains a JVM Throwable signature: {}",
                        jarPath,
                        output.take(200),
                    )
                    return output
                }
                return null
            }
            log.warn("[RuntimeCheck] JAR {} exited immediately with code {}: {}", jarPath, exitCode, output.take(200))
            output.ifBlank { "Process exited with code $exitCode" }
        } catch (e: Exception) {
            log.debug("[RuntimeCheck] Could not run runtime check for {}: {}", jarPath, e.message)
            null
        }

    private fun readProcessOutput(process: Process): String = String(process.inputStream.readBytes(), Charsets.UTF_8).trim()
}
