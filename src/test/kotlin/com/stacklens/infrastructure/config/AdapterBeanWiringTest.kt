package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.docker.DockerOsPaths
import com.stacklens.adapter.out.docker.DockerProcessEnvironment
import com.stacklens.adapter.out.docker.DockerSocketDiscoveryAdapter
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.compiler.JavaRuntimePort
import com.stacklens.application.port.out.tooling.ToolJarRejectedException
import com.stacklens.application.port.out.tooling.ToolJarRejection
import com.stacklens.testsupport.DockerPortsDouble
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class AdapterBeanWiringTest {
    @Test
    fun `toolJarStore writes below the configured data dir`() {
        val stackLensConfig = mock<StackLensConfigPort>()
        val store = ToolingAdapterConfig().toolJarStore(stackLensConfig)
        assertNotNull(store)
        whenever(stackLensConfig.dataDir).thenReturn("/tmp")
        assertThrows(ToolJarRejectedException::class.java) {
            store.storeImported("tool.jar", "not a jar".toByteArray())
        }
    }

    @Test
    fun `toolJarStore rejects a jar without the StackLens marker`() {
        val stackLensConfig = mock<StackLensConfigPort>()
        whenever(stackLensConfig.dataDir).thenReturn(System.getProperty("java.io.tmpdir"))
        val error =
            assertThrows(ToolJarRejectedException::class.java) {
                ToolingAdapterConfig().toolJarStore(stackLensConfig).storeImported("tool.jar", ByteArray(0))
            }
        assertTrue(error.rejection == ToolJarRejection.NOT_A_JAR || error.rejection == ToolJarRejection.MISSING_STACKLENS_MARKER)
    }

    @Test
    fun `toolJarRuntime is created from the java runtime port`(
        @TempDir dir: Path,
    ) {
        val javaRuntime = JavaRuntimePort { "/nonexistent/java" }
        val runtime = ToolingAdapterConfig().toolJarRuntime(javaRuntime)
        assertThrows(java.io.IOException::class.java) {
            runtime.runJar(dir.resolve("tool.jar").toString()) { }
        }
    }

    @Test
    fun `dockerCliProber is created from the compose port`() {
        val composePort = mock<DockerPortsDouble>()
        whenever(composePort.resolveDockerExecutable()).thenReturn("/nonexistent/docker")
        val status = DockerTestAdapterConfig().dockerCliProber(composePort).probe()
        assertFalse(status.available)
    }

    @Test
    fun `dockerCliPort surfaces start failures of the resolved docker executable`() {
        val composePort = mock<DockerPortsDouble>()
        whenever(composePort.resolveDockerExecutable()).thenReturn("/nonexistent/docker")
        assertThrows(java.io.IOException::class.java) {
            DockerTestAdapterConfig().dockerCliPort(composePort, DockerProcessEnvironment(DockerOsPaths(), DockerSocketDiscoveryAdapter())).run(listOf("version"), null, 5L)
        }
    }

    @Test
    fun `dockerTestEnvExecutor is a shutdownable single thread executor`() {
        val executor = DockerTestAdapterConfig().dockerTestEnvExecutor()
        val done = java.util.concurrent.CountDownLatch(1)
        executor.execute { done.countDown() }
        assertTrue(done.await(2, java.util.concurrent.TimeUnit.SECONDS))
        executor.shutdown()
    }
}
