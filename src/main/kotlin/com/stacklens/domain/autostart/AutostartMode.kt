package com.stacklens.domain.autostart

enum class AutostartMode(
    val wire: String,
) {
    DESKTOP("desktop"),
    SERVER("server"),
    ;

    companion object {
        fun fromWire(value: String?): AutostartMode = entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: DESKTOP
    }
}
