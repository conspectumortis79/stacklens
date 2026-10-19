package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode

internal fun flattenAdfNode(node: JsonNode): String =
    buildString {
        val textNode = node.get("text")
        if (textNode != null && textNode.isTextual) {
            append(textNode.asText())
        } else {
            node.get("content")?.forEach { child ->
                val part = flattenAdfNode(child)
                if (part.isNotBlank()) {
                    if (isNotEmpty()) append(' ')
                    append(part)
                }
            }
        }
    }
