package com.stacklens.domain.text

object HealthMessageText {
    const val MAX_ERROR_MESSAGE_LEN: Int = 500

    fun truncate(
        raw: String,
        maxLen: Int = MAX_ERROR_MESSAGE_LEN,
    ): String {
        if (raw.isEmpty()) return ""
        val compact =
            raw
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()
        return if (compact.length <= maxLen) compact else compact.substring(0, maxLen - 1) + "…"
    }
}
