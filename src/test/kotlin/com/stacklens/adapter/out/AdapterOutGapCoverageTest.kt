package com.stacklens.adapter.out

import com.stacklens.adapter.out.git.LineDiffCalculator
import com.stacklens.adapter.out.platform.FileSystemContainerRuntimeProbeAdapter
import com.stacklens.adapter.out.platform.SystemNativeLibraryEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SystemNativeLibraryEnvironmentCoverageTest {
    private val environment = SystemNativeLibraryEnvironment()

    @Test
    fun `the path separator is taken from the running platform`() {
        assertEquals(File.pathSeparator, environment.pathSeparator())
    }

    @Test
    fun `the user home and the macos flag mirror the jvm properties`() {
        assertEquals(System.getProperty("user.home"), environment.userHome())
        assertEquals(System.getProperty("os.name", "").lowercase().contains("mac"), environment.isMacOs())
    }

    @Test
    fun `the macos flag distinguishes a mac host from a linux host`() {
        val original = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "Mac OS X")
            assertTrue(environment.isMacOs())
            System.setProperty("os.name", "Linux")
            assertFalse(environment.isMacOs())
        } finally {
            original?.let { System.setProperty("os.name", it) } ?: System.clearProperty("os.name")
        }
    }

    @Test
    fun `a missing environment variable is reported as absent`() {
        assertEquals(null, environment.environment("STACKLENS_DEFINITELY_NOT_SET"))
        assertTrue(environment.environment("PATH")!!.isNotBlank())
    }
}

class LineDiffCalculatorCoverageTest {
    @Test
    fun `the diff calculator is a stateless holder of the diff helpers`() {
        val calculator = LineDiffCalculator()

        assertSame(LineDiffCalculator::class.java, calculator.javaClass)
        assertTrue(LineDiffCalculator.lcsDiff(emptyList(), emptyList()).isEmpty())
    }
}

class FileSystemContainerRuntimeProbeAdapterEnvCoverageTest {
    @Test
    fun `a present environment variable is read verbatim and a missing one as empty string`() {
        assertEquals(System.getenv("PATH"), FileSystemContainerRuntimeProbeAdapter.readEnvVarOrEmpty("PATH"))
        assertEquals("", FileSystemContainerRuntimeProbeAdapter.readEnvVarOrEmpty("STACKLENS_DEFINITELY_NOT_SET"))
    }
}
