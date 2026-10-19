package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.jwt.JwtAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class JwtAnalysisController(
    private val jwtAnalysisService: JwtAnalysisUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    data class AnalyzeRequest(
        val token: String = "",
    )

    @GetMapping("/jwt-analysis")
    fun jwtAnalyse(): String = "jwt-analysis"

    @PostMapping("/api/jwt-analyse")
    @ResponseBody
    fun analyze(
        @RequestBody req: AnalyzeRequest,
    ): ResponseEntity<Any> {
        if (req.token.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to tr("jwt_analysis.be_token_required")))
        }
        return try {
            ResponseEntity.ok(jwtAnalysisService.analyze(req.token))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: tr("common.be_unknown_error"))))
        }
    }
}
