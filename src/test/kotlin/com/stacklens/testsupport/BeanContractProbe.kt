package com.stacklens.testsupport

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import java.lang.reflect.Method
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.UUID

internal data class BeanProperty(
    val name: String,
    val getter: Method,
    val setter: Method?,
)

internal class BeanProbeReport(
    val properties: List<BeanProperty>,
    val exercised: List<String>,
    val readOnly: List<String>,
    val unsupported: List<String>,
)

internal object BeanContractProbe {
    fun propertiesOf(target: Any): List<BeanProperty> {
        target.javaClass.methods.forEach { it.isAccessible = true }
        val byName = LinkedHashMap<String, BeanProperty>()
        val setters =
            target.javaClass.methods
                .filter { !it.isSynthetic && it.parameterCount == 1 && it.name.startsWith("set") && it.name.length > 3 }
                .associateBy { it.name.removePrefix("set") }
        target.javaClass.methods
            .filter { !it.isSynthetic && it.parameterCount == 0 && it.returnType != Void.TYPE }
            .filter {
                it.name.startsWith("get") &&
                    it.name.length > 3 ||
                    it.name.startsWith("is") &&
                    it.name.length > 2 &&
                    it.returnType != java.lang.Boolean.TYPE
            }.sortedBy { it.name }
            .forEach { getter ->
                val suffix =
                    when {
                        getter.name.startsWith("get") -> getter.name.removePrefix("get")
                        else -> getter.name.removePrefix("is")
                    }
                val name = suffix.replaceFirstChar { it.lowercaseChar() }
                if (name == "class" || byName.containsKey(name)) return@forEach
                byName[name] = BeanProperty(name = name, getter = getter, setter = setters[suffix])
            }
        return byName.values.toList()
    }

    fun initialValues(target: Any): Map<String, Any?> = propertiesOf(target).associate { it.name to it.getter.invoke(target) }

    fun propertyValue(
        target: Any,
        name: String,
    ): Any? = propertiesOf(target).first { it.name == name }.getter.invoke(target)

    fun roundTrip(target: Any): BeanProbeReport {
        val props = propertiesOf(target)
        val exercised = mutableListOf<String>()
        val readOnly = mutableListOf<String>()
        val unsupported = mutableListOf<String>()
        props.forEachIndexed { index, property ->
            val current = property.getter.invoke(target)
            val setter = property.setter
            if (setter == null) {
                readOnly += property.name
                assertEquals(current, property.getter.invoke(target), property.name)
                return@forEachIndexed
            }
            val sample = sampleFor(setter.parameterTypes[0], current, index + 1)
            if (sample == null) {
                unsupported += property.name
                return@forEachIndexed
            }
            setter.invoke(target, sample)
            val readBack = property.getter.invoke(target)
            if (sample is ByteArray) {
                assertArrayEquals(sample, readBack as ByteArray?, property.name)
            } else {
                assertEquals(sample, readBack, property.name)
            }
            exercised += property.name
        }
        return BeanProbeReport(
            properties = props,
            exercised = exercised,
            readOnly = readOnly,
            unsupported = unsupported,
        )
    }

    internal fun sampleFor(
        type: Class<*>,
        seed: Int,
    ): Any? = sampleFor(type, null, seed)

    private fun sampleFor(
        type: Class<*>,
        current: Any?,
        seed: Int,
    ): Any? {
        val first = sampleValue(type, seed) ?: return null
        if (first != current) return first
        var shift = seed
        repeat(6) {
            shift += 31
            val candidate = sampleValue(type, shift) ?: return@repeat
            if (candidate != current) return candidate
        }
        return first
    }

    private fun sampleValue(
        type: Class<*>,
        seq: Int,
    ): Any? =
        when {
            type == String::class.java -> "bean-$seq"
            type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> seq % 2 == 0
            type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> seq * 7 + 3
            type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType -> seq * 7L + 3L
            type == Short::class.javaPrimitiveType || type == Short::class.javaObjectType -> (seq * 7 + 3).toShort()
            type == Byte::class.javaPrimitiveType || type == Byte::class.javaObjectType -> (seq * 7 + 3).toByte()
            type == Double::class.javaPrimitiveType || type == Double::class.javaObjectType -> seq + 0.5
            type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType -> seq + 0.5f
            type == Char::class.javaPrimitiveType || type == Char::class.javaObjectType -> 'a' + seq % 26
            type == ByteArray::class.java -> byteArrayOf(1, 2, 3, seq.toByte())
            type == Array<ByteArray>::class.java -> arrayOf(byteArrayOf(seq.toByte()))
            type == BigDecimal::class.java -> BigDecimal(seq + 1)
            type == UUID::class.java -> UUID.nameUUIDFromBytes("bean-$seq".toByteArray())
            type == LocalDateTime::class.java -> LocalDateTime.of(2020 + seq % 5, seq % 12 + 1, seq % 28 + 1, 3, seq % 60, seq % 60)
            type == LocalDate::class.java -> LocalDate.of(2020 + seq % 5, seq % 12 + 1, seq % 28 + 1)
            type == LocalTime::class.java -> LocalTime.of(seq % 24, seq % 60)
            type == Instant::class.java -> Instant.ofEpochMilli(1_600_000_000_000L + seq)
            type == Duration::class.java -> Duration.ofSeconds(seq * 3L)
            type == OffsetDateTime::class.java -> LocalDateTime.of(2021, seq % 12 + 1, seq % 28 + 1, 4, 5).atOffset(OffsetDateTime.now().offset)
            type == ZonedDateTime::class.java -> LocalDateTime.of(2022, seq % 12 + 1, seq % 28 + 1, 6, 7).atZone(ZonedDateTime.now().zone)
            type == java.util.Date::class.java -> java.util.Date(1_600_000_000_000L + seq)
            type == java.sql.Timestamp::class.java -> java.sql.Timestamp(1_600_000_000_000L + seq)
            Map::class.java.isAssignableFrom(type) -> mapOf("key-$seq" to seq)
            Set::class.java.isAssignableFrom(type) -> setOf("item-$seq")
            List::class.java.isAssignableFrom(type) -> listOf("item-$seq")
            Collection::class.java.isAssignableFrom(type) -> listOf("item-$seq")
            type == java.io.File::class.java -> java.io.File("bean-$seq")
            type == java.net.URI::class.java -> java.net.URI.create("https://bean-$seq.test")
            type == java.awt.Color::class.java -> java.awt.Color.RED
            type == com.fasterxml.jackson.databind.JsonNode::class.java ->
                com.fasterxml.jackson.databind.node
                    .IntNode(seq)
            type.isArray -> emptyArray<Any>()
            type.isEnum -> type.enumConstants.let { it[seq % it.size] }
            type.isInterface -> null
            else -> runCatching { type.getDeclaredConstructor().newInstance() }.getOrNull()
        }
}
