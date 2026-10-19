
package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.out.storage.DiagramStoragePort
import java.io.File

class DiagramStorageAdapter : DiagramStoragePort {
    override fun saveDiagram(
        directory: String,
        filename: String,
        content: String,
    ): String {
        val targetDir = File(directory)
        require(targetDir.exists() && targetDir.isDirectory) {
            "Directory not found: $directory"
        }
        val safeName = filename.replace(Regex("[^a-zA-Z0-9_\\-.]"), "_")
        val targetFile = File(targetDir, safeName)
        targetFile.writeText(content, Charsets.UTF_8)
        return targetFile.absolutePath
    }
}
