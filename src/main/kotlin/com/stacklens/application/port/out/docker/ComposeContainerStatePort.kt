package com.stacklens.application.port.out.docker

import com.stacklens.domain.docker.ComposeContainerState

interface ComposeContainerStatePort {
    fun queryComposeContainers(
        composeFile: String,
        timeoutSeconds: Long,
    ): ComposeContainerState
}
