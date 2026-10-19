package com.stacklens.adapter.inbound.rest.config

internal object LanguageConfigSupport {
    val SUPPORTED_LANGUAGES: Set<String> = setOf("en", "de")
    const val FALLBACK_LANGUAGE: String = "en"

    fun resolve(language: String?): String = language?.takeIf { it in SUPPORTED_LANGUAGES } ?: FALLBACK_LANGUAGE
}
