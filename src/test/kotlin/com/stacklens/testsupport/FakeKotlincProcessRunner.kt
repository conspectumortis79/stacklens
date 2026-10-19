package com.stacklens.testsupport

import com.stacklens.adapter.out.compiler.KotlincProcessRunner
import com.stacklens.adapter.out.compiler.KotlincRunResult

class FakeKotlincProcessRunner(
    var pathResult: String? = null,
    var versionResult: KotlincRunResult = KotlincRunResult(exitCode = 0, finished = true, output = ""),
    var throwOnVersion: Throwable? = null,
) : KotlincProcessRunner {
    val versionCalls = mutableListOf<String>()

    override fun lookupInPath(): String? = pathResult

    override fun runVersionCheck(kotlincPath: String): KotlincRunResult {
        versionCalls.add(kotlincPath)
        throwOnVersion?.let { throw it }
        return versionResult
    }
}
