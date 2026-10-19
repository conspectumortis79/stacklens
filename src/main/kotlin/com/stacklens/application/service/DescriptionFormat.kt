package com.stacklens.application.service

enum class DescriptionFormat(
    val wire: String,
    val promptKey: String?,
) {
    ADF("adf", "ticketrefinement.improve_ticket_adf"),
    WIKI("wiki", "ticketrefinement.improve_ticket_wiki"),
    MARKDOWN("markdown", null),
    ;

    companion object {
        fun fromWire(value: String?): DescriptionFormat = entries.firstOrNull { it.wire == value } ?: MARKDOWN
    }
}
