package com.stacklens.application.port.out.loadtest

interface K6ScriptPort {
    fun patchScriptForLocalDefault(
        script: String,
        forcedReplacement: String? = null,
    ): String

    fun extractBaseUrlLiteral(script: String): String?

    fun isSuspiciousTemplateDefault(url: String): Boolean

    fun moveTopLevelStagesIntoDefaultScenario(script: String): String

    fun sanitizeScriptForK6V2(script: String): String
}
