package com.stacklens.adapter.out.knowledge.extraction

import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import org.springframework.stereotype.Component

@Component
class MarkdownKnowledgeExtractor : DocumentTextExtractor {
    override fun supports(
        filename: String,
        content: ByteArray,
    ): Boolean {
        val lower = filename.lowercase()
        return lower.endsWith(".md") || lower.endsWith(".markdown")
    }

    override fun extract(
        filename: String,
        content: ByteArray,
    ): String {
        val raw = String(content, Charsets.UTF_8)
        return raw
            .replace(Regex("^```[a-zA-Z0-9]*\\s*$", RegexOption.MULTILINE), "")
            .replace(Regex("<!--[\\s\\S]*?-->"), "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
