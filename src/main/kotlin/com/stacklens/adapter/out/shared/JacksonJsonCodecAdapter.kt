package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.JsonTree
import java.io.InputStream
import kotlin.reflect.KClass

class JacksonJsonCodecAdapter(
    baseMapper: ObjectMapper,
) : JsonCodecPort {
    private val mapper: ObjectMapper =
        baseMapper
            .copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val lenientMapper: ObjectMapper =
        baseMapper
            .copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)

    private val escapeTolerantMapper: ObjectMapper =
        baseMapper
            .copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)

    override fun <T : Any> decode(
        json: String,
        type: KClass<T>,
    ): T = mapper.readValue(json, type.java)

    override fun <T : Any> decode(
        stream: InputStream,
        type: KClass<T>,
    ): T = mapper.readValue(stream, type.java)

    override fun <T : Any> decodeList(
        json: String,
        elementType: KClass<T>,
    ): List<T> {
        val listType =
            escapeTolerantMapper.typeFactory.constructCollectionType(List::class.java, elementType.java)
        return escapeTolerantMapper.readValue(json, listType)
    }

    override fun decodeLenientMap(json: String): Map<String, Any?> = lenientMapper.readValue(json)

    override fun decodeMap(json: String): Map<String, Any?> = mapper.readValue(json)

    override fun decodeMap(stream: InputStream): Map<String, Any?> = mapper.readValue(stream)

    override fun decodeTree(json: String?): JsonTree? {
        if (json.isNullOrBlank()) return null
        val node: JsonNode = runCatching { mapper.readTree(json) }.getOrNull() ?: return null
        return node.toJsonTree()
    }

    internal fun JsonNode.toJsonTree(): JsonTree =
        when {
            isObject ->
                JsonTree.ObjectNode(
                    entries = properties().associate { (k, v) -> k to v.toJsonTree() },
                )
            isArray ->
                JsonTree.ArrayNode(
                    elements = map { it.toJsonTree() },
                )
            isTextual -> JsonTree.TextNode(asText())
            isBoolean -> JsonTree.BooleanNode(asBoolean())
            isNumber -> JsonTree.NumberNode(asDouble())
            isNull -> JsonTree.NullNode
            else -> JsonTree.TextNode(asText())
        }

    override fun encode(value: Any?): String = mapper.writeValueAsString(value)

    override fun extractStringField(
        json: String?,
        fieldName: String,
    ): String? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val node = mapper.readTree(json).takeUnless { it.isNull } ?: return@runCatching null
            val field = node.get(fieldName) ?: return@runCatching null
            if (field.isNull) return@runCatching null
            field.asText()
        }.getOrNull()
    }

    override fun parseArrayOrEmpty(json: String?): List<Any?> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val list = mapper.readValue(json, List::class.java)
            list ?: emptyList<Any?>()
        }.getOrDefault(emptyList())
    }
}
