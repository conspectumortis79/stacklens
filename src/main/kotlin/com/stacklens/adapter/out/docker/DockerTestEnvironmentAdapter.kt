package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.ComposeFileLocationPort
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.application.port.out.docker.DockerTestComposePlan
import com.stacklens.application.port.out.docker.DockerTestEnvironmentPort
import org.springframework.stereotype.Component

@Component
open class DockerTestEnvironmentAdapter(
    private val composeFiles: ComposeFileLocationPort,
    private val dockerExecutable: DockerExecutablePort,
) : DockerTestEnvironmentPort {
    override fun composePlan(): DockerTestComposePlan? {
        val composeFile = composeFiles.resolveComposeFile() ?: return null
        val executable = dockerExecutable.resolveDockerExecutable()
        return DockerTestComposePlan(
            build = testdockerComposeCommand(executable, composeFile, "build"),
            up =
                testdockerComposeCommand(
                    executable,
                    composeFile,
                    "up",
                    "-d",
                    "--force-recreate",
                    "--remove-orphans",
                ),
            down = testdockerComposeCommand(executable, composeFile, "down", "--volumes", "--rmi", "all"),
            prune = listOf(executable, "container", "prune", "-f"),
            workDir = composeFiles.containingDirectory(composeFile),
        )
    }
}
