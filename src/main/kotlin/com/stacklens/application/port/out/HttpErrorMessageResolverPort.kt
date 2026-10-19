package com.stacklens.application.port.out

enum class HttpErrorMessageVariant {
    GENERIC,
    GRAFANA,
}

interface HttpErrorMessageResolverPort {
    fun resolveMessage(
        statusCode: Int,
        endpoint: String,
        responseBody: String,
        variant: HttpErrorMessageVariant = HttpErrorMessageVariant.GENERIC,
    ): String
}
