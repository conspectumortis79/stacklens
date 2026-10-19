package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.FakeKotlincProcessRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KotlincResolverTest {
    @Test
    fun `injectJavaHome sets JAVA_HOME in process env`() {
        val resolver = KotlincResolver()
        val pb = ProcessBuilder("echo", "noop")

        pb.environment().remove("JAVA_HOME")
        assertNull(pb.environment()["JAVA_HOME"])
        resolver.injectJavaHome(pb)
        val javaHome = pb.environment()["JAVA_HOME"]
        assertNotNull(javaHome)
        assertEquals(System.getProperty("java.home"), javaHome)
    }

    @Test
    fun `findKotlinc consults the system environment when no environment port is injected`() {
        val resolver = KotlincResolver(FakeKotlincProcessRunner(versionResult = KotlincRunResult(exitCode = 1, finished = true, output = "")))

        assertNull(resolver.findKotlinc())
    }
}
