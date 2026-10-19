
package com.stacklens.adapter.inbound.rest.pipeline

import com.stacklens.adapter.inbound.rest.ImageMimeTypes
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.io.File
import java.util.Base64

@Component
class
MockupReader(
    private val i18n: I18nUseCase,
) : MockupFileAccess {
    override fun readMockupFile(path: String): ResponseEntity<Map<String, Any?>> {
        val file =
            try {
                File(path).canonicalFile
            } catch (e: Exception) {
                return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_invalid_path")))
            }
        if (!file.exists() || !file.isFile) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_file_not_found")))
        }
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext !in ImageMimeTypes.mockupExtensions) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_file_type_not_allowed")))
        }
        if (file.length() > MAX_MOCKUP_BYTES) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_mockup_too_large")))
        }
        val mimeType = ImageMimeTypes.mimeFor(ext)!!
        val b64 = Base64.getEncoder().encodeToString(file.readBytes())
        return ResponseEntity.ok(
            mapOf(
                "base64" to b64,
                "mimeType" to mimeType,
                "dataUrl" to "data:$mimeType;base64,$b64",
                "name" to file.name,
            ),
        )
    }

    override fun validateMockup(
        base64: String?,
        mimeType: String?,
    ): String? {
        if (base64.isNullOrBlank()) return null
        if (mimeType.isNullOrBlank()) {
            return i18n.t("config.be_mockup_mime_missing")
        }
        if (mimeType !in ImageMimeTypes.mockupMimeTypes) {
            return i18n.t("config.be_mockup_type_not_supported", mimeType, ImageMimeTypes.mockupMimeTypes.joinToString())
        }
        if (base64.length > MAX_MOCKUP_B64_CHARS) {
            return i18n.t("config.be_mockup_too_large")
        }
        return null
    }

    companion object {
        const val MAX_MOCKUP_BYTES: Long = 10_485_760L
        const val MAX_MOCKUP_B64_CHARS: Int = 10_485_760
    }
}
