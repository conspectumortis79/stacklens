package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

class AdfCommentPreparer(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(AdfCommentPreparer::class.java)

    fun prependGreeting(
        adfJson: String,
        greeting: String,
        openPointsLabel: String,
        openPoints: List<String>,
    ): String {
        if (adfJson.isBlank()) return adfJson
        return try {
            val doc: MutableMap<String, Any?> =
                objectMapper.readValue(adfJson, object : TypeReference<MutableMap<String, Any?>>() {})
            val greetingNode =
                mapOf(
                    "type" to "paragraph",
                    "content" to listOf(mapOf("type" to "text", "text" to greeting)),
                )
            val original = (doc["content"] as? List<Any?>) ?: emptyList()
            doc["content"] = listOf(greetingNode) + original + buildOpenPointsAdf(openPointsLabel, openPoints)
            objectMapper.writeValueAsString(doc)
        } catch (e: Exception) {
            log.warn("Failed to prepend greeting to ADF, posting body as-is: {}", e.message)
            adfJson
        }
    }

    private fun buildOpenPointsAdf(
        openPointsLabel: String,
        openPoints: List<String>,
    ): List<Map<String, Any?>> {
        if (openPoints.isEmpty()) return emptyList()
        val rule = mapOf("type" to "rule")
        val heading =
            mapOf(
                "type" to "heading",
                "attrs" to mapOf("level" to 3),
                "content" to
                    listOf(
                        mapOf("type" to "text", "text" to "⚠ $openPointsLabel"),
                    ),
            )
        val items =
            openPoints.map { point ->
                mapOf(
                    "type" to "listItem",
                    "content" to
                        listOf(
                            mapOf(
                                "type" to "paragraph",
                                "content" to listOf(mapOf("type" to "text", "text" to point)),
                            ),
                        ),
                )
            }
        val bulletList = mapOf("type" to "bulletList", "content" to items)
        return listOf(rule, heading, bulletList)
    }
}
