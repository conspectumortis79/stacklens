package com.stacklens.adapter.inbound.rest

internal fun parseConfigId(raw: Any?): Long? =
    when (raw) {
        is Number -> raw.toLong()
        is String -> raw.trim().toLongOrNull()
        else -> null
    }

internal fun resolveConfigId(
    raw: Any?,
    firstActive: () -> Long?,
): Long? = parseConfigId(raw) ?: firstActive()
