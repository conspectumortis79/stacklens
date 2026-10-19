package com.stacklens.domain.model

enum class IssueType(
    val displayName: String,
    val promptKey: String,
) {
    STORY("Story", "ticketcreation.create_story"),
    TECHNICAL_TASK("Technical Task", "ticketcreation.create_technical_task"),
    BUG("Bug", "ticketcreation.create_bug"),
    ;

    companion object {
        val DEFAULT: IssueType = STORY

        fun fromString(value: String?): IssueType? {
            val normalized = value?.trim() ?: return null
            if (normalized.isEmpty()) return null
            return entries.firstOrNull {
                it.displayName.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            }
        }
    }
}
