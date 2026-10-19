package com.stacklens.adapter.inbound.rest.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerDemoDataFileUseCase
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataLocationUseCase
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataReadResult
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataWriteResult
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/docker-test")
class DockerTestDemoDataController(
    private val demoDataFiles: DockerDemoDataFileUseCase,
    private val i18n: I18nUseCase,
    private val demoDataLocation: DockerDemoDataLocationUseCase,
) {
    private val log = LoggerFactory.getLogger(DockerTestDemoDataController::class.java)

    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    private fun composeNotFoundMsg(): String = tr("docker_test.be_compose_not_found")

    private fun invalidPathMsg(): String = tr("docker_test.be_invalid_path")

    private fun notAFileMsg(): String = tr("docker_test.be_not_a_file")

    private fun notEditableMsg(): String = tr("docker_test.be_extension_not_editable")

    private fun writeFailedMsg(): String = tr("docker_test.editor_save_error")

    data class DataFileWriteRequest(
        val path: String = "",
        val content: String = "",
    )

    @GetMapping("/data-files")
    fun listDataFiles(): ResponseEntity<List<Map<String, String>>> {
        val files = demoDataFiles.listDataFiles(composeFileLocation())
        return ResponseEntity.ok(files.map { mapOf("path" to it.path, "label" to it.label) })
    }

    @GetMapping("/data-file")
    fun readDataFile(
        @RequestParam path: String,
    ): ResponseEntity<Map<String, Any>> {
        val result = demoDataFiles.readDataFile(composeFileLocation(), path)
        return when (result) {
            is DockerDemoDataReadResult.Read ->
                ResponseEntity.ok(mapOf("content" to result.content))
            DockerDemoDataReadResult.ComposeNotFound ->
                ResponseEntity.badRequest().body(mapOf("error" to composeNotFoundMsg()))
            DockerDemoDataReadResult.PathOutsideBase -> {
                log.warn("Rejected data-file read outside docker dir: {}", path)
                ResponseEntity.badRequest().body(mapOf("error" to invalidPathMsg()))
            }
            DockerDemoDataReadResult.NotAFile ->
                ResponseEntity.badRequest().body(mapOf("error" to notAFileMsg()))
            DockerDemoDataReadResult.ExtensionNotEditable ->
                ResponseEntity.badRequest().body(mapOf("error" to notEditableMsg()))
        }
    }

    @PutMapping("/data-file")
    fun writeDataFile(
        @RequestBody body: DataFileWriteRequest,
    ): ResponseEntity<Map<String, String>> {
        val result = demoDataFiles.writeDataFile(composeFileLocation(), body.path, body.content)
        return when (result) {
            is DockerDemoDataWriteResult.Saved -> {
                log.info("Data file written: {}", result.path)
                ResponseEntity.ok(mapOf("status" to "ok"))
            }
            DockerDemoDataWriteResult.ComposeNotFound ->
                ResponseEntity.badRequest().body(mapOf("error" to composeNotFoundMsg()))
            is DockerDemoDataWriteResult.Rejected -> rejectionResponse(body, result.reason)
        }
    }

    private fun rejectionResponse(
        body: DataFileWriteRequest,
        reason: WorkspaceWriteRejection,
    ): ResponseEntity<Map<String, String>> =
        when (reason) {
            WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT -> {
                log.warn("Rejected data-file write outside docker dir: {}", body.path)
                ResponseEntity.badRequest().body(mapOf("error" to invalidPathMsg()))
            }
            WorkspaceWriteRejection.EXTENSION_NOT_EDITABLE ->
                ResponseEntity.badRequest().body(mapOf("error" to notEditableMsg()))
            WorkspaceWriteRejection.WRITE_FAILED -> {
                log.warn("Data file write failed: {}", body.path)
                ResponseEntity.badRequest().body(mapOf("error" to writeFailedMsg()))
            }
            else ->
                ResponseEntity.badRequest().body(mapOf("error" to notAFileMsg()))
        }

    private fun composeFileLocation(): String? = runCatching { demoDataLocation.composeFileLocation() }.getOrNull()
}
