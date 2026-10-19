package com.stacklens.application.service

import com.stacklens.application.port.inbound.pipeline.PipelineWriteErrorCode
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.file.FileListingReadWriteDeletePort
import com.stacklens.application.port.out.file.PathPolicyPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.text.AgentTextUtils
import com.stacklens.domain.text.AgentTextUtils.ParsedFile
import org.slf4j.LoggerFactory
import java.io.File

@ApplicationService
class AgentOutputPersister(
    private val filePersistence: FileListingReadWriteDeletePort,
    private val pathPolicy: PathPolicyPort,
) {
    private val log = LoggerFactory.getLogger(AgentOutputPersister::class.java)

    data class PersistFailure(
        val path: String?,
        val code: PipelineWriteErrorCode,
        val detail: String?,
    )

    data class PersistResult(
        val written: List<String>,
        val deleted: List<String>,
        val errors: List<String>,
        val failures: List<PersistFailure> = emptyList(),
    )

    private val fileBlockRe get() = AgentTextUtils.agentOutputBlockRe

    private val deletedLineRe = Regex("""(?m)^\s*DELETED\s+(\S.*?)\s*$""")

    private fun stageOutputs(run: AgentPipelineRun): List<String> =
        listOf(
            run.lena.output,
            run.nora.output,
            run.ivan.output,
            run.cole.output,
            run.max.output,
            run.finn.output,
            run.faye.output,
            run.tess.output,
            run.syd.output,
            run.quinn.output,
            run.sage.output,
            run.vera.output,
            run.rex.output,
        )

    fun parseAllFiles(run: AgentPipelineRun): List<ParsedFile> = stageOutputs(run).flatMap { parseFiles(it) }

    fun parseFiles(output: String): List<ParsedFile> {
        if (output.isBlank()) return emptyList()
        return fileBlockRe
            .findAll(output)
            .map {
                val cleaned =
                    it.groupValues[1]
                        .trim()
                        .trim('`', '\'', '"')
                        .trimEnd(':', ',', ';')
                        .trim()
                ParsedFile(cleaned, it.groupValues[2])
            }.toList()
    }

    fun parseDeletions(output: String): List<String> {
        if (output.isBlank()) return emptyList()
        return deletedLineRe
            .findAll(output)
            .map { it.groupValues[1].trim().trim('`', '\'', '"') }
            .toList()
    }

    fun parseAllDeletions(run: AgentPipelineRun): List<String> = stageOutputs(run).flatMap { parseDeletions(it) }

    fun persist(
        run: AgentPipelineRun,
        repoRoot: File,
    ): PersistResult {
        val written = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val failures = mutableListOf<PersistFailure>()

        val canonicalRoot = canonical(repoRoot)

        parseAllFiles(run).forEach { pf ->
            if (!isSafeRelativePath(pf.path)) {
                recordFailure(pf.path, PipelineWriteErrorCode.PATH_TRAVERSAL, "path traversal denied", errors, failures)
                return@forEach
            }
            val target =
                try {
                    pathPolicy.canonicalFile(canonicalRoot.resolve(pf.path))
                } catch (e: Exception) {
                    recordFailure(pf.path, PipelineWriteErrorCode.WRITE_FAILED, e.message ?: e.javaClass.simpleName, errors, failures)
                    return@forEach
                }
            if (!isInsideRoot(target, canonicalRoot)) {
                recordFailure(pf.path, PipelineWriteErrorCode.PATH_TRAVERSAL, "path traversal denied", errors, failures)
                return@forEach
            }
            val targetDirectory = target.parentFile
            try {
                filePersistence.writeFile(target, pf.content)
                written.add(pf.path)
            } catch (e: Exception) {
                val code = pipelineErrorCode(targetDirectory, e)
                val message = e.message ?: e.javaClass.simpleName
                val detail = if (code == PipelineWriteErrorCode.DIR_CREATE_FAILED) canonical(targetDirectory!!).path else message
                recordFailure(pf.path, code, detail, errors, failures, message)
            }
        }

        parseAllDeletions(run).forEach { path ->
            if (!isSafeRelativePath(path)) {
                recordFailure(path, PipelineWriteErrorCode.PATH_TRAVERSAL, "path traversal denied", errors, failures)
                return@forEach
            }
            val target =
                try {
                    pathPolicy.canonicalFile(canonicalRoot.resolve(path))
                } catch (e: Exception) {
                    recordFailure(path, PipelineWriteErrorCode.WRITE_FAILED, e.message ?: e.javaClass.simpleName, errors, failures)
                    return@forEach
                }
            if (!isInsideRoot(target, canonicalRoot)) {
                recordFailure(path, PipelineWriteErrorCode.PATH_TRAVERSAL, "path traversal denied", errors, failures)
                return@forEach
            }
            try {
                if (filePersistence.deleteFile(target)) {
                    deleted.add(path)
                }
            } catch (e: Exception) {
                recordFailure(path, PipelineWriteErrorCode.WRITE_FAILED, e.message ?: e.javaClass.simpleName, errors, failures)
            }
        }

        log.info(
            "[Pipeline {}] Auto-persist applied to {}: {} written, {} deleted, {} error(s)",
            run.id,
            canonicalRoot.path,
            written.size,
            deleted.size,
            errors.size,
        )
        return PersistResult(written = written, deleted = deleted, errors = errors, failures = failures)
    }

    private fun recordFailure(
        path: String,
        code: PipelineWriteErrorCode,
        detail: String?,
        errors: MutableList<String>,
        failures: MutableList<PersistFailure>,
        message: String? = detail,
    ) {
        errors.add("$path: $message")
        failures.add(PersistFailure(path = path, code = code, detail = detail))
    }

    private fun pipelineErrorCode(
        targetDirectory: File?,
        cause: Exception,
    ): PipelineWriteErrorCode =
        when {
            cause is SecurityException -> PipelineWriteErrorCode.SECURITY_DENIED
            targetDirectory != null && !filePersistence.exists(targetDirectory) -> PipelineWriteErrorCode.DIR_CREATE_FAILED
            else -> PipelineWriteErrorCode.WRITE_FAILED
        }

    private fun canonical(file: File): File = runCatching { pathPolicy.canonicalFile(file) }.getOrNull() ?: file

    internal fun isSafeRelativePath(path: String): Boolean = pathPolicy.isSafeRelativePath(path)

    private fun isInsideRoot(
        target: File,
        repoRoot: File,
    ): Boolean = pathPolicy.isInsideRoot(repoRoot, target)
}
