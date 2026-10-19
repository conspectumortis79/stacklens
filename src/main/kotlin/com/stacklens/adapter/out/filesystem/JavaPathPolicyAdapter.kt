package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.PathPolicyPort
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Path

@Component
class JavaPathPolicyAdapter(
    private val fileAttributes: FileAttributes,
) : PathPolicyPort {
    override fun canonicalFile(file: File): File = fileAttributes.canonicalFile(file)

    override fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith("/") &&
            runCatching {
                Path.of(path).normalize().none { it.toString() == ".." }
            }.getOrDefault(false)

    override fun isInsideRoot(
        root: File,
        target: File,
    ): Boolean = target.path.startsWith(root.path + File.separator) || target.path == root.path
}
