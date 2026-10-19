package com.stacklens.adapter.out.pdf

import com.stacklens.application.port.out.file.PdfTextExtractionPort
import com.stacklens.domain.util.closingAuto
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component

@Component
class PdfTextExtractionAdapter : PdfTextExtractionPort {
    override fun extractText(bytes: ByteArray): String =
        Loader.loadPDF(bytes).closingAuto { document ->
            PDFTextStripper().getText(document)
        }
}
