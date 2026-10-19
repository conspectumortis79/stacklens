package com.stacklens.application.port.out

interface MarkdownConvertPort {
    fun sanitize(raw: String?): String

    fun render(markdown: String?): String
}
