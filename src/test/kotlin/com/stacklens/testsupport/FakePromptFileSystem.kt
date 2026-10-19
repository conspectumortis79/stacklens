package com.stacklens.testsupport

import com.stacklens.application.port.out.file.FileSystemPort
import java.io.File

class FakePromptFileSystem(
    private val base: FileSystemPort,
    private val reportedSize: Long? = null,
    private val readFailure: RuntimeException? = null,
) : FileSystemPort by base {
    var lastSizeQuery: File? = null

    override fun size(file: File): Long {
        lastSizeQuery = file
        return reportedSize ?: base.size(file)
    }

    override fun readText(file: File): String {
        readFailure?.let { throw it }
        return base.readText(file)
    }
}
