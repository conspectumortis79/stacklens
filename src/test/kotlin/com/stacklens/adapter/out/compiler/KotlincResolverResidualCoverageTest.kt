package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.FakeKotlincProcessRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class KotlincResolverResidualCoverageTest {
    @Test
    fun `a version check that times out marks kotlinc as incompatible`() {
        val resolver =
            KotlincResolver(
                FakeKotlincProcessRunner(
                    versionResult = KotlincRunResult(exitCode = -1, finished = false, output = ""),
                ),
            )

        assertFalse(resolver.isKotlincCompatible("/opt/homebrew/bin/kotlinc"))
    }
}
