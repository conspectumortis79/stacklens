package com.stacklens.application.service.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerTestStreamHandle
import com.stacklens.application.port.inbound.dockertest.DockerTestStreamRegistry
import com.stacklens.application.stereotype.ApplicationService
import java.util.concurrent.atomic.AtomicInteger

@ApplicationService
class DockerTestStreamRegistryService : DockerTestStreamRegistry {
    private val activeStreams: AtomicInteger = AtomicInteger(0)

    override fun open(): DockerTestStreamHandle {
        activeStreams.incrementAndGet()
        return object : DockerTestStreamHandle {
            private val released = AtomicInteger(0)

            override fun release() {
                if (released.compareAndSet(0, 1)) {
                    activeStreams.decrementAndGet()
                }
            }
        }
    }

    override fun activeStreamCount(): Int = activeStreams.get()
}
