package com.stacklens.application.service

internal fun KiChatAttachmentPreparer.isSupportedMimeType(mimeType: String): Boolean = KiChatAttachmentPreparer.SUPPORTED_MIME_TYPES.contains(mimeType.lowercase())
