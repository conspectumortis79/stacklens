package com.stacklens.application.service.logging

import com.stacklens.application.port.inbound.logging.WriteFileEntry
import com.stacklens.application.port.inbound.logging.WriteFilesPreview
import com.stacklens.application.port.inbound.logging.WriteFilesResult
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.file.FileReadWritePort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.domain.model.LoggingFixAnalysis
import org.slf4j.LoggerFactory
import java.io.File

data class LoggingFixFileState(
    val analysis: LoggingFixAnalysis?,
    val proposedFiles: List<ProposedFile>,
)

class LoggingFixFileWriter(
    private val repository: LoggingFixPort,
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val fileWriter: FileReadWritePort,
    private val clock: ClockPort,
    private val stateOf: (Long) -> LoggingFixFileState,
) {
    private val log = LoggerFactory.getLogger(LoggingFixFileWriter::class.java)

    fun getWriteFilesPreview(id: Long): WriteFilesPreview? {
        val state = stateOf(id)
        val analysis = state.analysis ?: return null
        val repoPath = analysis.localRepoConfigId?.let { localRepoConfigRepo.findLocalRepoConfigById(it)?.path }
        return WriteFilesPreview(
            repoPath = repoPath,
            files = state.proposedFiles.map { WriteFileEntry(it.path, it.content, it.description) },
        )
    }

    fun writeFiles(id: Long): WriteFilesResult {
        val state = stateOf(id)
        val analysis = state.analysis
        if (analysis == null) {
            return WriteFilesResult(success = false, error = "Analyse nicht gefunden")
        }
        val repoPath =
            analysis.localRepoConfigId?.let { localRepoConfigRepo.findLocalRepoConfigById(it)?.path }
                ?: return WriteFilesResult(success = false, error = "Repository nicht verfügbar")
        val root = File(repoPath)
        if (!fileWriter.isDirectory(root)) {
            return WriteFilesResult(success = false, error = "Repository-Pfad existiert nicht: $repoPath")
        }
        val files = state.proposedFiles
        if (files.isEmpty()) {
            return WriteFilesResult(success = false, error = "Keine Dateien zum Schreiben erkannt")
        }
        val written = mutableListOf<String>()
        for (f in files) {
            try {
                val target = File(root, f.path)
                fileWriter.writeFile(target, f.content)
                written += f.path
            } catch (e: Exception) {
                log.warn("[LoggingFix {}] write '{}' failed: {}", id, f.path, e.message)
                return WriteFilesResult(success = false, error = "Fehler beim Schreiben von ${f.path}: ${e.message}")
            }
        }
        log.info("[LoggingFix {}] wrote {} files to {}", id, written.size, repoPath)
        return WriteFilesResult(success = true, writtenFiles = written)
    }

    fun generateSlug(text: String): String =
        text
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "-")
            ?.trim('-')
            ?.take(60)
            ?: "log-fix-${clock.epochMillis()}"
}
