package com.stacklens.testsupport

import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort

class FakeContainerRuntimeProbePort(
    private val runtime: ContainerRuntime = ContainerRuntime.NONE,
) : ContainerRuntimeProbePort {
    override fun detectActiveRuntime(): ContainerRuntime = runtime
}
