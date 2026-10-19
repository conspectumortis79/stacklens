package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.EssentialToolsUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tools")
class EssentialToolsApiController(
    private val essentialTools: EssentialToolsUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    data class InstallReq(
        val tool: String? = null,
    )

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(essentialTools.getStatus())

    @PostMapping("/install")
    fun install(
        @RequestBody(required = false) req: InstallReq?,
    ): ResponseEntity<Map<String, Any?>> {
        val tool = req?.tool ?: "gh"
        val result = essentialTools.install(tool)
        if (!result.success && result.manualCommand == null && result.stdout.isBlank()) {
            return ResponseEntity.ok(
                mapOf(
                    "success" to false,
                    "error" to tr("essential_tools.be_unknown_tool", tool),
                ),
            )
        }
        if (!result.success && result.exitCode == null) {
            return ResponseEntity.ok(
                mapOf(
                    "success" to false,
                    "error" to tr("essential_tools.be_auto_install_unsupported", result.stderr.ifBlank { tool }),
                    "manualCommand" to result.manualCommand,
                ),
            )
        }
        return ResponseEntity.ok(
            mapOf(
                "success" to result.success,
                "exitCode" to result.exitCode,
                "stdout" to result.stdout,
                "stderr" to result.stderr,
                "needsPrivilege" to result.needsPrivilege,
                "manualCommand" to result.manualCommand,
                "tools" to result.tools,
            ),
        )
    }
}
