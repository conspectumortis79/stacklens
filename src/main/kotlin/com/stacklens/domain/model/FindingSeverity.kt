package com.stacklens.domain.model

enum class FindingSeverity(
    val wire: String,
    val badge: String,
) {
    HIGH("HIGH", "🔴"),
    MEDIUM("MEDIUM", "🟡"),
    LOW("LOW", "🟢"),
    ;

    companion object {
        fun fromWire(value: String?): FindingSeverity = entries.firstOrNull { it.wire == value?.trim()?.uppercase() } ?: LOW

        fun badgeFor(value: String?): String = fromWire(value).badge
    }
}
