package com.stacklens.application.port.out

interface AiResponseJsonExtractorPort {
    fun extractSection(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String

    fun extractTrimmedSection(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String

    fun extractLooseSection(
        text: String,
        startWords: List<String>,
        endWords: List<String>,
    ): String

    fun extractJsonBlock(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String?

    fun extractJsonBlock(text: String): String

    fun extractJsonArray(text: String): String?

    fun extractCodeBlock(
        text: String,
        vararg langs: String,
    ): String?

    fun extractJsonBlockAfter(
        text: String,
        startMarker: String,
    ): String?

    fun extractBalancedJsonObject(
        text: String,
        startPos: Int,
    ): String?
}
