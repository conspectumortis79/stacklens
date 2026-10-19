package com.stacklens.shared

internal fun sprintIdFromRegex(
    regex: Regex,
    url: String,
): Long? {
    val match = regex.find(url) ?: return null
    val captured = match.groupValues.getOrNull(1) ?: return null
    return captured.toLongOrNull()
}
