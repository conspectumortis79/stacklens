package com.stacklens.application.port.inbound.ticket

sealed interface TicketAttachmentOutcome {
    data object AccessDenied : TicketAttachmentOutcome

    data object NotFound : TicketAttachmentOutcome

    data object ImageTooLarge : TicketAttachmentOutcome

    data object PdfTooLarge : TicketAttachmentOutcome

    data class Unsupported(
        val extension: String,
    ) : TicketAttachmentOutcome

    data class Image(
        val bytes: ByteArray,
        val name: String,
        val mimeType: String,
    ) : TicketAttachmentOutcome

    data class Pdf(
        val text: String,
        val name: String,
        val extractionError: Boolean,
    ) : TicketAttachmentOutcome
}

interface TicketAttachmentReadUseCase {
    fun readAttachment(path: String): TicketAttachmentOutcome
}
