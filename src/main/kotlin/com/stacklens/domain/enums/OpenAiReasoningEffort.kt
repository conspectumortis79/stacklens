package com.stacklens.domain.enums

object OpenAiReasoningEffort {
    val SUPPORTED: Set<String> = setOf("low", "medium", "high")

    const val DEFAULT: String = "medium"

    fun normalize(value: String?): String = value?.trim()?.lowercase()?.takeIf { it in SUPPORTED } ?: DEFAULT
}
