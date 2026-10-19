package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.compiler.JavaRuntimePort
import com.stacklens.application.port.out.tooling.ToolJarRuntime
import com.stacklens.shared.ToolJarCommand
import com.stacklens.shared.utf8Reader
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class ProcessToolJarRuntime(
    private val javaRuntime: JavaRuntimePort,
    private val processStarter: (List<String>) -> Process = ::startJarProcess,
    private val runTimeoutSeconds: Long = DEFAULT_RUN_TIMEOUT_SECONDS,
) : ToolJarRuntime {
    private val log = LoggerFactory.getLogger(ProcessToolJarRuntime::class.java)

    override fun runJar(
        jarPath: String,
        onLine: (String) -> Unit,
    ): String {
        val process = processStarter(ToolJarCommand.of(javaRuntime.findJavaExecutable(), jarPath))
        return try {
            val output = StringBuilder()
            process.inputStream.utf8Reader().use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    output.appendLine(line)
                    onLine(line)
                    line = reader.readLine()
                }
            }
            if (!process.waitFor(runTimeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                log.warn("Tool JAR {} did not finish within {}s – killed", jarPath, runTimeoutSeconds)
            }
            output.toString()
        } catch (e: Exception) {
            if (process.isAlive) process.destroyForcibly()
            throw e
        }
    }

    companion object {
        const val DEFAULT_RUN_TIMEOUT_SECONDS: Long = 300L

        private fun startJarProcess(command: List<String>): Process = ProcessBuilder(command).redirectErrorStream(true).start()
    }
}
