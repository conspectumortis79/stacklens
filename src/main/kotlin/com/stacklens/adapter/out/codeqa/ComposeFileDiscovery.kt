package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import java.io.File

class ComposeFileDiscovery(
    private val directoryScan: LocalDirectoryScanPort,
) {
    private val composeFileNames: Set<String> =
        setOf("docker-compose.yml", "docker-compose.yaml", "docker-compose.override.yml", "docker-compose.override.yaml")

    private val skippedDirectoryNames: Set<String> =
        setOf(".git", "node_modules", "vendor", "__pycache__", "target", "build", ".gradle")

    fun findComposeFiles(root: File): List<File> =
        directoryScan
            .filesNamed(root, composeFileNames) { directory -> !directory.name.startsWith(".") && directory.name !in skippedDirectoryNames }
            .sortedBy { it.relativeTo(root).path.count { c -> c == File.separatorChar } }
}
