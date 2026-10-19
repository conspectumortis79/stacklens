package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.config.UiPreferencesUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
open class UiPreferencesController(
    private val preferences: UiPreferencesUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @GetMapping("/ui-prefs")
    fun getUiPrefs(): Map<String, String> = preferences.allPreferences()

    @GetMapping("/ui-prefs/{key}")
    fun getUiPref(
        @PathVariable key: String,
    ): ResponseEntity<Map<String, String>> = ResponseEntity.ok(mapOf("value" to preferences.preference(key)))

    @PostMapping("/ui-prefs/{key}")
    fun setUiPref(
        @PathVariable key: String,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Map<String, Any>> {
        val value = body["value"] ?: return ResponseEntity.badRequest().body(mapOf("error" to tr("app.be_missing_value")))
        preferences.setPreference(key, value)
        return ResponseEntity.ok(mapOf("ok" to true))
    }
}
