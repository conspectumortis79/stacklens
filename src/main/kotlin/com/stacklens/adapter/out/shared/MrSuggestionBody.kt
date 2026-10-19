package com.stacklens.adapter.out.shared

internal fun suggestionBody(suggestedCode: String): String {
    val body = if (suggestedCode.isBlank()) "" else suggestedCode.trimEnd('\n')
    return "```suggestion\n$body\n```"
}
