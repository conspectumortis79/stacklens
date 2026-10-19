package com.stacklens.application.port.out

import java.io.File

interface PptxStoragePort {
    fun saveUpload(
        analysisId: Long,
        originalFilename: String,
        bytes: ByteArray,
    ): File

    fun importFromServer(
        analysisId: Long,
        sourcePath: String,
    ): File

    fun saveHtml(
        analysisId: Long,
        html: String,
    ): String

    fun findUploadedPptx(
        analysisId: Long,
        expectedName: String?,
    ): File?

    fun cleanup(analysisId: Long)

    fun readUploadBytes(file: File): ByteArray
}
