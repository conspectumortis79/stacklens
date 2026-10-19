package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import java.io.File

class RepoFileTreeLister(
    private val directoryScan: LocalDirectoryScanPort,
) {
    private val skippedDirectoryNames: Set<String> = setOf("node_modules", "vendor", "__pycache__")

    fun buildFileList(
        dir: File,
        maxFiles: Int = 80,
    ): String {
        val entries =
            directoryScan.entries(dir, { file -> !file.name.startsWith(".") && file.name !in skippedDirectoryNames }, maxFiles)
        return buildString {
            entries.forEach { appendLine("${"  ".repeat(it.depth)}${it.name}") }
        }
    }
}
