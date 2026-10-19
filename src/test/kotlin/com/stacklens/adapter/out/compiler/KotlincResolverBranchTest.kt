package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.FakeKotlincProcessRunner
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KotlincResolverBranchTest {
    @Test
    fun `findKotlinc returns null when the version check rejects every candidate`() {
        val resolver = KotlincResolver(FakeKotlincProcessRunner(versionResult = KotlincRunResult(exitCode = 1, finished = true, output = "")))
        val result = resolver.findKotlinc()

        assertNull(result)
    }

    @Test
    fun `injectJavaHome adds JAVA_HOME to ProcessBuilder`() {
        val resolver = KotlincResolver()
        val pb = ProcessBuilder("/bin/echo", "test")
        resolver.injectJavaHome(pb)
        assertNotNull(pb.environment()["JAVA_HOME"])
    }

    @Test
    fun `KotlincResolver is constructible`() {
        val resolver = KotlincResolver()
        assertNotNull(resolver)
    }
}
