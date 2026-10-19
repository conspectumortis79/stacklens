package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import kotlin.reflect.KClass

interface AiProviderTesterBase {
    val specType: KClass<out ProviderSpec>

    fun testSpec(spec: ProviderSpec): AiConnectionTestPort.TestResult
}

interface AiProviderTester<S : ProviderSpec> : AiProviderTesterBase {
    override val specType: KClass<S>

    fun test(spec: S): AiConnectionTestPort.TestResult

    override fun testSpec(spec: ProviderSpec): AiConnectionTestPort.TestResult {
        if (!specType.isInstance(spec)) {
            throw IllegalArgumentException(
                "AiProviderTester for ${specType.qualifiedName} cannot handle ${spec::class.qualifiedName}",
            )
        }
        return test(specType.java.cast(spec))
    }
}
