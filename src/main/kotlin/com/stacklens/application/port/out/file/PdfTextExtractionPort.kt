package com.stacklens.application.port.out.file

interface PdfTextExtractionPort {
    fun extractText(bytes: ByteArray): String
}
