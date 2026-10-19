package com.stacklens.application.port.out.file

import java.io.File

interface PathPolicyPort {
    fun canonicalFile(file: File): File

    fun isSafeRelativePath(path: String): Boolean

    fun isInsideRoot(
        root: File,
        target: File,
    ): Boolean
}
