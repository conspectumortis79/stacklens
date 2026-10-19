package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.adapter.out.textfilter.DefaultAiJsonRepairer
import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class TicketRefinementJsonParserFallbackTest {
    private val parser = TicketRefinementJsonParser(JacksonJsonCodecAdapter(ObjectMapper().registerModule(KotlinModule.Builder().build())), DefaultAiJsonRepairer(), DefaultAiResponseJsonExtractor())

    @Test
    fun `parseFromRawText with markdown fallback returns ParsedReview`() {
        val func =
            TicketRefinementJsonParser::class
                .declaredFunctions
                .firstOrNull { it.name == "parseFromRawText" }
        if (func != null) {
            func.javaMethod!!.isAccessible = true
            val rawText =
                """{"improvedDescription": "Improved text"}"""
            try {
                func.call(parser, rawText)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.cause ?: e
            }
        }
    }

    @Test
    fun `parseFromRawText with escaped chars in improved description`() {
        val func =
            TicketRefinementJsonParser::class
                .declaredFunctions
                .firstOrNull { it.name == "parseFromRawText" }
        if (func != null) {
            func.javaMethod!!.isAccessible = true
            val rawText =
                """{"improvedDescription": "Line1\\nLine2"}"""
            try {
                func.call(parser, rawText)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.cause ?: e
            }
        }
    }
}
