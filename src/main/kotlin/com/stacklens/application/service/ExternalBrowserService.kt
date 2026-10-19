package com.stacklens.application.service

import com.stacklens.application.port.inbound.platform.BrowserOpenResult
import com.stacklens.application.port.inbound.platform.ExternalBrowserUseCase
import com.stacklens.application.port.out.platform.HostBrowserPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class ExternalBrowserService(
    private val hostBrowser: HostBrowserPort,
) : ExternalBrowserUseCase {
    override fun openUrl(url: String?): BrowserOpenResult {
        val trimmed = url?.trim()
        if (trimmed.isNullOrEmpty()) {
            return BrowserOpenResult(opened = false, errorKey = MISSING_URL_KEY)
        }
        if (!trimmed.startsWith(HTTP_SCHEME) && !trimmed.startsWith(HTTPS_SCHEME)) {
            return BrowserOpenResult(opened = false, errorKey = INVALID_SCHEME_KEY)
        }
        return try {
            hostBrowser.browse(trimmed)
            BrowserOpenResult(opened = true)
        } catch (e: Exception) {
            BrowserOpenResult(opened = false, errorMessage = e.message ?: FALLBACK_MESSAGE)
        }
    }

    companion object {
        const val MISSING_URL_KEY: String = "app.be_missing_url"
        const val INVALID_SCHEME_KEY: String = "app.be_invalid_url_scheme"
        private const val HTTP_SCHEME: String = "http://"
        private const val HTTPS_SCHEME: String = "https://"
        private const val FALLBACK_MESSAGE: String = "failed"
    }
}
