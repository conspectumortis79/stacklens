package com.stacklens.adapter.out.health

import com.stacklens.domain.text.HealthMessageText

internal const val MAX_ERROR_MESSAGE_LEN: Int = HealthMessageText.MAX_ERROR_MESSAGE_LEN

internal fun truncateMessage(
    raw: String,
    maxLen: Int = MAX_ERROR_MESSAGE_LEN,
): String = HealthMessageText.truncate(raw, maxLen)
