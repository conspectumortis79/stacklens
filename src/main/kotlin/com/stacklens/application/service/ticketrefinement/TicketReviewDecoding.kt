package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.domain.model.TicketReview

internal fun JsonCodecPort.decodeTicketReviews(json: String?): List<TicketReview> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        decodeList(json, TicketReview::class)
    } catch (_: Exception) {
        emptyList()
    }
}
