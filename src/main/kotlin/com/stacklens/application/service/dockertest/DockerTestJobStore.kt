package com.stacklens.application.service.dockertest

import com.stacklens.application.stereotype.ApplicationService
import java.util.concurrent.atomic.AtomicReference

@ApplicationService
class DockerTestJobStore {
    private val currentJob: AtomicReference<DockerTestJob?> = AtomicReference(null)

    fun replace(next: DockerTestJob): DockerTestJob? = currentJob.getAndSet(next)

    fun current(): DockerTestJob? = currentJob.get()

    fun clear(): DockerTestJob? = currentJob.getAndSet(null)
}
