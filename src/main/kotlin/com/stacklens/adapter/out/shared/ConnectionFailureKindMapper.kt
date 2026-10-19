package com.stacklens.adapter.out.shared

import com.stacklens.application.port.model.ConnectionFailureKind
import org.springframework.web.client.HttpStatusCodeException

internal fun connectionFailureKind(status: Int?): ConnectionFailureKind =
    when (status) {
        401 -> ConnectionFailureKind.AUTHENTICATION
        403 -> ConnectionFailureKind.AUTHORIZATION
        404 -> ConnectionFailureKind.NOT_FOUND
        null -> ConnectionFailureKind.CONNECTION
        else -> ConnectionFailureKind.OTHER
    }

internal fun connectionFailureKindFor(cause: Throwable): ConnectionFailureKind = connectionFailureKind((cause as? HttpStatusCodeException)?.statusCode?.value())

internal fun connectionFailureKindFromMessage(message: String): ConnectionFailureKind {
    val lower = message.lowercase()
    return when {
        "401" in lower -> ConnectionFailureKind.AUTHENTICATION
        "unauthorized" in lower -> ConnectionFailureKind.AUTHENTICATION
        "403" in lower -> ConnectionFailureKind.AUTHORIZATION
        "forbidden" in lower -> ConnectionFailureKind.AUTHORIZATION
        "404" in lower -> ConnectionFailureKind.NOT_FOUND
        "timeout" in lower -> ConnectionFailureKind.CONNECTION
        "connection refused" in lower -> ConnectionFailureKind.CONNECTION
        else -> ConnectionFailureKind.OTHER
    }
}
