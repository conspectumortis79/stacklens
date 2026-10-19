package com.stacklens.adapter.out.review.pptx

import com.stacklens.shared.sniffMagic

internal object PptxFormats {
    val EXTENSIONS: Set<String> = setOf("pptx", "ppt")

    fun sniff(content: ByteArray): String? = sniffMagic(content)

    fun supports(
        filename: String,
        content: ByteArray,
    ): Boolean {
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".pptx") -> sniff(content) == "zip"
            lower.endsWith(".ppt") -> sniff(content) == "ole2"
            else -> false
        }
    }
}
