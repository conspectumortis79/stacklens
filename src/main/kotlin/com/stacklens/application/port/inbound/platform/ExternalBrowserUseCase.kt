package com.stacklens.application.port.inbound.platform

data class BrowserOpenResult(
    val opened: Boolean,
    val errorKey: String? = null,
    val errorMessage: String = "",
)

interface ExternalBrowserUseCase {
    fun openUrl(url: String?): BrowserOpenResult
}
