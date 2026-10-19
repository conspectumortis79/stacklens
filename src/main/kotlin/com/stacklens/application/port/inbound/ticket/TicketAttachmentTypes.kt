package com.stacklens.application.port.inbound.ticket

object TicketAttachmentTypes {
    const val MAX_IMAGE_BYTES: Long = 10_485_760L
    const val MAX_PDF_BYTES: Long = 25_165_824L

    val imageExtensions: Set<String> = setOf("png", "jpg", "jpeg", "gif", "webp", "tiff", "tif")

    val mimeTypes: Map<String, String> =
        mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "tif" to "image/tiff",
            "tiff" to "image/tiff",
        )

    fun mimeFor(extension: String): String? = mimeTypes[extension]
}
