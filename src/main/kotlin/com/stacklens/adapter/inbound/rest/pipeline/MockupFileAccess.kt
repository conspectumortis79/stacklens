package com.stacklens.adapter.inbound.rest.pipeline

import org.springframework.http.ResponseEntity

internal interface MockupFileAccess {
    fun readMockupFile(path: String): ResponseEntity<Map<String, Any?>>

    fun validateMockup(
        base64: String?,
        mimeType: String?,
    ): String?
}
