package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.ticket.TicketAttachmentTypes

internal object ImageMimeTypes {
    private val extraMimeByExtension: Map<String, String> =
        mapOf(
            "svg" to "image/svg+xml",
            "heic" to "image/heic",
            "heif" to "image/heif",
        )

    val mimeByExtension: Map<String, String> = TicketAttachmentTypes.mimeTypes + extraMimeByExtension

    val extractionExtensions: Set<String> = setOf("tif", "tiff", "png", "jpg", "jpeg")
    val extractionMimeTypes: Set<String> = setOf("image/tiff", "image/png", "image/jpeg", "image/jpg")
    val fileBrowserExtensions: Set<String> = setOf("tif", "tiff", "png", "jpg", "jpeg", "svg")
    val ticketImageExtensions: Set<String> = TicketAttachmentTypes.imageExtensions
    val mockupExtensions: Set<String> = setOf("png", "jpg", "jpeg", "svg", "heic", "heif", "tif", "tiff")
    val mockupMimeTypes: Set<String> = setOf("image/png", "image/jpeg", "image/svg+xml", "image/heic", "image/heif", "image/tiff")

    fun mimeFor(extension: String): String? = mimeByExtension[extension]
}
