package com.stacklens.shared

internal fun sniffMagic(content: ByteArray): String? {
    if (content.size < 4) return null
    return when {
        content[0] == '%'.code.toByte() &&
            content[1] == 'P'.code.toByte() &&
            content[2] == 'D'.code.toByte() &&
            content[3] == 'F'.code.toByte() -> "pdf"
        content[0] == 'P'.code.toByte() &&
            content[1] == 'K'.code.toByte() &&
            content[2] == 0x03.toByte() &&
            content[3] == 0x04.toByte() -> "zip"
        content[0] == 0xD0.toByte() &&
            content[1] == 0xCF.toByte() &&
            content[2] == 0x11.toByte() &&
            content[3] == 0xE0.toByte() -> "ole2"
        content[0] == 0xD0.toByte() &&
            content[1] == 0xCF.toByte() &&
            content[2] == 0x11.toByte() &&
            content[3] == 0xE1.toByte() -> "ppt"
        else -> null
    }
}
