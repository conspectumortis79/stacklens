package com.stacklens.desktop

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Path
import kotlin.io.path.Path

@EnabledIfEnvironmentVariable(named = "STACKLENS_RUN_INTEGRATION", matches = "1")
class DesktopBackendBootstrapStopIntegrationTest {
    @Test
    fun `stop closes the embedded Spring context and lets the JVM shut down`() {
        val dataDir =
            Path
                .of(System.getProperty("java.io.tmpdir"), "stacklens-stop-int-${System.nanoTime()}")
                .toFile()
                .also { it.mkdirs() }
        val bootstrap = DesktopBackendBootstrap()
        val port = bootstrap.start()
        check(port > 0) { "embedded server should be listening" }

        val pid = ProcessHandle.current().pid()
        val before =
            java.lang.management.ManagementFactory
                .getThreadMXBean()
                .getThreadInfo(pid)
                .let { java.lang.Thread.activeCount() }

        bootstrap.stop()

        check(bootstrap.toString().contains("DesktopBackendBootstrap")) {
            "sanity: instance survived stop()"
        }

        val after =
            java.lang.management.ManagementFactory
                .getThreadMXBean()
                .getThreadInfo(pid)
                .let { java.lang.Thread.activeCount() }

        check(after < before) {
            "stop() did not reduce the live thread count (before=$before, after=$after); " +
                "embedded Tomcat threads are still alive and will block System.exit(0)"
        }
    }
}
