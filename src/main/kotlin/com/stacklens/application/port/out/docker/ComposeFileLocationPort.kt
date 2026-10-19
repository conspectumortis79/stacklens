package com.stacklens.application.port.out.docker

interface ComposeFileLocationPort {
    fun resolveComposeFile(): String?

    fun containingDirectory(path: String): String?

    fun resolveComposeSiblingFile(
        composeFile: String,
        relativePath: String,
    ): String?

    fun findComposeContainerNames(composeFile: String): List<String>
}
