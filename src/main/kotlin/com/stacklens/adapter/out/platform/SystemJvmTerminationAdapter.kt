package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.JvmTerminationPort
import org.springframework.stereotype.Component

@Component
class SystemJvmTerminationAdapter(
    private val runtime: Runtime = Runtime.getRuntime(),
) : JvmTerminationPort {
    override fun halt(status: Int) {
        runtime.halt(status)
    }
}
