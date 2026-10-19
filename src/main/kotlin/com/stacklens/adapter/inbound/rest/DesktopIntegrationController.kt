package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.ExternalBrowserUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
open class DesktopIntegrationController(
    private val externalBrowser: ExternalBrowserUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @PostMapping("/open-url")
    fun openUrl(
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Map<String, Any>> {
        val result = externalBrowser.openUrl(body["url"])
        if (result.opened) return ResponseEntity.ok(mapOf("status" to "opened"))
        val errorKey = result.errorKey
        return if (errorKey != null) {
            ResponseEntity.badRequest().body(mapOf("error" to tr(errorKey)))
        } else {
            ResponseEntity.internalServerError().body(mapOf("error" to result.errorMessage))
        }
    }
}
