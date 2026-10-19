package com.stacklens.application.service

import com.stacklens.application.port.inbound.chat.ChatAttachment
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.image.InlineImagePreparationPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory
import java.util.Base64

@ApplicationService
class KiChatAttachmentPreparer(
    private val imageInlinePreparer: InlineImagePreparationPort,
) {
    private val log = LoggerFactory.getLogger(KiChatAttachmentPreparer::class.java)

    fun prepare(attachments: List<ChatAttachment>): List<AiAttachment> {
        if (attachments.isEmpty()) return emptyList()
        val prepared = mutableListOf<AiAttachment>()
        for (att in attachments) {
            val single = prepareOne(att) ?: continue
            prepared += single
        }
        return prepared
    }

    internal fun prepareOne(attachment: ChatAttachment): AiAttachment? {
        val mime = attachment.mimeType.lowercase()
        if (!SUPPORTED_MIME_TYPES.contains(mime)) {
            log.warn("Dropping KI-chat attachment '{}' ({}): unsupported mime type", attachment.name, mime)
            return null
        }
        val rawBytes =
            try {
                Base64.getDecoder().decode(attachment.base64Data)
            } catch (e: IllegalArgumentException) {
                log.warn("Dropping KI-chat attachment '{}': invalid base64 payload", attachment.name)
                return null
            }
        if (rawBytes.isEmpty()) {
            log.warn("Dropping KI-chat attachment '{}': empty payload", attachment.name)
            return null
        }
        val prepared = imageInlinePreparer.prepareImageForInlineUse(rawBytes, mime)
        if (prepared.scaledDown) {
            log.info(
                "Downscaled KI-chat attachment '{}' from {} bytes to {} bytes ({}x{})",
                attachment.name,
                rawBytes.size,
                prepared.bytes.size,
                prepared.width,
                prepared.height,
            )
        }
        return AiAttachment(
            mimeType = prepared.mimeType,
            base64Data = Base64.getEncoder().encodeToString(prepared.bytes),
        )
    }

    companion object {
        val SUPPORTED_MIME_TYPES: Set<String> =
            setOf(
                "image/png",
                "image/jpeg",
                "image/jpg",
                "image/tiff",
                "image/tif",
            )
    }
}
