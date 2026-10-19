package com.stacklens.application.service.prompt

import com.stacklens.application.port.inbound.prompt.PromptFileReadFailure
import com.stacklens.application.port.inbound.prompt.PromptFileReadResult
import com.stacklens.application.port.inbound.prompt.PromptFileReadUseCase
import com.stacklens.application.port.out.file.FileReadWritePort
import com.stacklens.application.port.out.platform.HostDirectoryPort
import com.stacklens.application.stereotype.ApplicationService
import java.io.File

@ApplicationService
class PromptFileReadService(
    private val fileSystem: FileReadWritePort,
    private val hostDirectories: HostDirectoryPort,
) : PromptFileReadUseCase {
    override fun readPromptFile(path: String): PromptFileReadResult {
        val homeDir = fileSystem.canonicalFile(File(hostDirectories.homeDirectory()))
        val canonical = fileSystem.canonicalFile(File(path))
        val homePath = homeDir.absolutePath + File.separator
        val isHomeItself = canonical.absolutePath == homeDir.absolutePath
        if (!isHomeItself && !canonical.absolutePath.startsWith(homePath)) {
            return PromptFileReadResult.Failed(PromptFileReadFailure.ACCESS_DENIED)
        }
        if (!fileSystem.exists(canonical) || !fileSystem.isFile(canonical)) {
            return PromptFileReadResult.Failed(PromptFileReadFailure.NOT_FOUND)
        }
        if (fileSystem.size(canonical) > MAX_FILE_BYTES) {
            return PromptFileReadResult.Failed(PromptFileReadFailure.TOO_LARGE)
        }
        return try {
            PromptFileReadResult.Success(fileSystem.readText(canonical))
        } catch (e: Exception) {
            PromptFileReadResult.Failed(PromptFileReadFailure.READ_FAILED, e.message ?: "")
        }
    }

    private companion object {
        const val MAX_FILE_BYTES: Long = 10L * 1024 * 1024
    }
}
