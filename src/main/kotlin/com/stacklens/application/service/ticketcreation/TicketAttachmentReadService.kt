package com.stacklens.application.service.ticketcreation

import com.stacklens.application.port.inbound.ticket.TicketAttachmentOutcome
import com.stacklens.application.port.inbound.ticket.TicketAttachmentReadUseCase
import com.stacklens.application.port.inbound.ticket.TicketAttachmentTypes
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.FileContentStore
import com.stacklens.application.port.out.file.PdfTextExtractionPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory
import java.io.File

@ApplicationService
class TicketAttachmentReadService(
    private val fileAttributes: FileAttributes,
    private val fileContentStore: FileContentStore,
    private val pdfTextExtraction: PdfTextExtractionPort,
) : TicketAttachmentReadUseCase {
    private val log = LoggerFactory.getLogger(TicketAttachmentReadService::class.java)

    override fun readAttachment(path: String): TicketAttachmentOutcome {
        val homeDir = fileAttributes.canonicalFile(fileAttributes.homeDirectory())
        val canonical = canonicalPath(path) ?: return TicketAttachmentOutcome.AccessDenied
        if (!isInsideHome(homeDir, canonical)) {
            return TicketAttachmentOutcome.AccessDenied
        }
        if (!fileAttributes.exists(canonical) || !fileAttributes.isFile(canonical)) {
            return TicketAttachmentOutcome.NotFound
        }
        val extension = canonical.name.substringAfterLast('.', "").lowercase()
        if (extension in TicketAttachmentTypes.imageExtensions) {
            return readImage(canonical, extension)
        }
        if (extension == "pdf") {
            return readPdf(canonical)
        }
        return TicketAttachmentOutcome.Unsupported(extension)
    }

    private fun canonicalPath(path: String): File? =
        try {
            fileAttributes.canonicalFile(File(path))
        } catch (e: Exception) {
            null
        }

    private fun isInsideHome(
        homeDir: File,
        canonical: File,
    ): Boolean {
        val homePath = homeDir.absolutePath + File.separator
        return canonical.absolutePath == homeDir.absolutePath || canonical.absolutePath.startsWith(homePath)
    }

    private fun readImage(
        canonical: File,
        extension: String,
    ): TicketAttachmentOutcome {
        if (fileAttributes.size(canonical) > TicketAttachmentTypes.MAX_IMAGE_BYTES) {
            return TicketAttachmentOutcome.ImageTooLarge
        }
        val mimeType = TicketAttachmentTypes.mimeFor(extension)!!
        return TicketAttachmentOutcome.Image(fileContentStore.readBytes(canonical), canonical.name, mimeType)
    }

    private fun readPdf(canonical: File): TicketAttachmentOutcome {
        if (fileAttributes.size(canonical) > TicketAttachmentTypes.MAX_PDF_BYTES) {
            return TicketAttachmentOutcome.PdfTooLarge
        }
        return try {
            val text = pdfTextExtraction.extractText(fileContentStore.readBytes(canonical))
            TicketAttachmentOutcome.Pdf(text.take(15_000), canonical.name, false)
        } catch (e: Exception) {
            log.warn("[TicketCreation] PDF text extraction failed for {}: {}", canonical.name, e.message)
            TicketAttachmentOutcome.Pdf("", canonical.name, true)
        }
    }
}
