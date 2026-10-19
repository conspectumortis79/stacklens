package com.stacklens.application.port.out

import java.io.InputStream
import kotlin.reflect.KClass

sealed class JsonTree {
    data class ObjectNode(
        val entries: Map<String, JsonTree>,
    ) : JsonTree()

    data class ArrayNode(
        val elements: List<JsonTree>,
    ) : JsonTree()

    data class TextNode(
        val value: String,
    ) : JsonTree()

    data class NumberNode(
        val value: Double,
    ) : JsonTree()

    data class BooleanNode(
        val value: Boolean,
    ) : JsonTree()

    data object NullNode : JsonTree()
}

interface JsonReaderPort {
    fun <T : Any> decode(
        json: String,
        type: KClass<T>,
    ): T

    fun <T : Any> decode(
        stream: InputStream,
        type: KClass<T>,
    ): T

    fun <T : Any> decodeList(
        json: String,
        elementType: KClass<T>,
    ): List<T>

    fun decodeMap(json: String): Map<String, Any?>

    fun decodeMap(stream: InputStream): Map<String, Any?>
}

interface LenientJsonReaderPort {
    fun decodeLenientMap(json: String): Map<String, Any?>
}

interface JsonWriterPort {
    fun encode(value: Any?): String
}

interface JsonTreeReaderPort {
    fun decodeTree(json: String?): JsonTree?

    fun extractStringField(
        json: String?,
        fieldName: String,
    ): String?

    fun parseArrayOrEmpty(json: String?): List<Any?>
}

interface JsonCodecPort :
    JsonReaderPort,
    LenientJsonReaderPort,
    JsonWriterPort,
    JsonTreeReaderPort
