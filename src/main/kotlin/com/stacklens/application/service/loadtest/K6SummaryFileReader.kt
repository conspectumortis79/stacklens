package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.file.FileReadWriteDeletePort
import java.io.File

class K6SummaryFileReader(
    private val fileSystem: FileReadWriteDeletePort,
) {
    fun readAndDelete(summaryFile: File): String? =
        runCatching {
            if (fileSystem.exists(summaryFile) && fileSystem.size(summaryFile) > 2) fileSystem.readText(summaryFile) else null
        }.getOrNull().also { runCatching { fileSystem.deleteFile(summaryFile) } }
}
