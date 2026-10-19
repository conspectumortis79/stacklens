package com.stacklens.testsupport

import com.stacklens.application.port.out.docker.ComposeContainerStatePort
import com.stacklens.application.port.out.docker.ComposeFileLocationPort
import com.stacklens.application.port.out.docker.DockerCommandPort
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort

interface DockerPortsDouble :
    ComposeFileLocationPort,
    DockerExecutablePort,
    DockerCommandPort,
    ComposeContainerStatePort,
    DockerSocketDiscoveryPort
