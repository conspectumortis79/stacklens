package com.stacklens.adapter.out.async

import com.stacklens.application.port.out.async.DockerTestDispatchPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService

@Component
class DockerTestAsyncJobDispatcher(
    @Qualifier("dockerTestStreamExecutor") private val streamExecutor: ExecutorService,
) : DockerTestDispatchPort {
    override fun submitStream(task: () -> Unit) {
        streamExecutor.submit(task)
    }
}
