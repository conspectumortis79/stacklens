package com.stacklens.testsupport

import org.mockito.Mockito
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

object DataCarrierProbe {
    class Unsupported(
        override val message: String,
    ) : Exception(message)

    fun isDataCarrier(type: KClass<*>): Boolean = runCatching { type.isData }.getOrDefault(false)

    fun exercise(type: KClass<*>): Int {
        if (!isDataCarrier(type)) throw Unsupported("${type.qualifiedName} is no data class")
        val klass = type.java
        singletonOf(type)?.let { instance ->
            invokeAll(instance, klass.declaredMethods.toList().filter { it.parameterCount == 0 && !it.isSynthetic })
            exerciseEquality(instance, type)
            return 1
        }
        val constructor = type.primaryConstructor ?: throw Unsupported("${type.qualifiedName} has no primary constructor")
        val valueParameters = constructor.parameters.filter { it.kind == KParameter.Kind.VALUE }
        if (valueParameters.isEmpty()) throw Unsupported("${type.qualifiedName} carries no values")
        val arguments = valueParameters.mapIndexed { index, parameter -> valueFor(parameter.type, index + 1) ?: throw Unsupported("no sample value for ${parameter.name}") }
        val javaConstructor =
            klass.declaredConstructors.firstOrNull { candidate -> candidate.parameterCount == valueParameters.size }
                ?: throw Unsupported("${type.qualifiedName} has no matching java constructor")
        javaConstructor.isAccessible = true
        val instance =
            try {
                javaConstructor.newInstance(*arguments.toTypedArray())
            } catch (failure: Throwable) {
                throw Unsupported("${type.qualifiedName} cannot be built: ${failure.cause ?: failure}")
            }
        invokeAll(instance, klass.declaredMethods.toList().filter { it.parameterCount == 0 && !it.isSynthetic && it.returnType != Void.TYPE })
        exerciseEquality(instance, type)

        val copy =
            klass.declaredMethods.firstOrNull { it.name == "copy" && !it.isSynthetic }
                ?: throw Unsupported("${type.qualifiedName} has no copy method")
        copy.isAccessible = true
        val copied = copy.invoke(instance, *arguments.toTypedArray())
        if (copied != instance) throw Unsupported("${type.qualifiedName} copy is not equal to the original")
        if (copied.hashCode() != instance.hashCode()) throw Unsupported("${type.qualifiedName} copy has another hash code")

        val components = klass.declaredMethods.toList().filter { it.name.matches(Regex("component[0-9]+")) }
        if (components.size != valueParameters.size) {
            throw Unsupported("${type.qualifiedName} exposes ${components.size} components for ${valueParameters.size} constructor values")
        }
        invokeAll(instance, components)
        return type.memberProperties.size
    }

    private fun singletonOf(type: KClass<*>): Any? =
        runCatching { type.objectInstance }.getOrNull()
            ?: runCatching {
                val field = type.java.getDeclaredField("INSTANCE")
                field.isAccessible = true
                field.get(null)
            }.getOrNull()

    private fun invokeAll(
        instance: Any,
        methods: List<Method>,
    ) {
        methods.forEach { method ->
            method.isAccessible = true
            method.invoke(instance)
        }
    }

    private fun exerciseEquality(
        instance: Any,
        type: KClass<*>,
    ) {
        if (!instance.equals(instance)) throw Unsupported("${type.qualifiedName} is not equal to itself")
        if (instance.equals(Any())) throw Unsupported("${type.qualifiedName} equals an unrelated instance")
        if (instance.toString().isBlank()) throw Unsupported("${type.qualifiedName} renders no toString")
    }

    private fun valueFor(
        type: KType?,
        seed: Int,
    ): Any? {
        val classifier = type?.classifier as? KClass<*> ?: return null
        val javaType = runCatching { classifier.java }.getOrNull() ?: return null
        val element = type.arguments.firstOrNull()?.type
        return when {
            javaType == List::class.java || javaType == Collection::class.java || javaType == Iterable::class.java ->
                arrayListOf<Any?>(valueFor(element, seed + 1) ?: return null)

            javaType == Set::class.java -> linkedSetOf<Any?>(valueFor(element, seed + 1) ?: return null)

            javaType == Map::class.java ->
                linkedMapOf<Any?, Any?>(
                    (valueFor(type.arguments.getOrNull(0)?.type, seed + 1) ?: return null) to
                        (valueFor(type.arguments.getOrNull(1)?.type, seed + 2) ?: return null),
                )

            javaType.isArray -> valueForArray(javaType.componentType, seed)

            classifier.isSealed -> sealedSample(classifier, seed)

            else -> BeanContractProbe.sampleFor(javaType, seed) ?: mock(javaType)
        }
    }

    private fun sealedSample(
        classifier: KClass<*>,
        seed: Int,
    ): Any? =
        runCatching {
            classifier
                .sealedSubclasses
                .firstNotNullOfOrNull { subtype ->
                    runCatching { singletonOf(subtype) ?: exerciseValueOf(subtype, seed) }.getOrNull()
                }
        }.getOrNull()

    private fun exerciseValueOf(
        type: KClass<*>,
        seed: Int,
    ): Any {
        val constructor = type.primaryConstructor ?: throw Unsupported("${type.qualifiedName} has no primary constructor")
        val arguments = constructor.parameters.filter { it.kind == KParameter.Kind.VALUE }.map { valueFor(it.type, seed) ?: throw Unsupported("no sample value for ${it.name}") }
        return runCatching { constructor.call(*arguments.toTypedArray()) }.getOrElse { throw Unsupported("${type.qualifiedName} cannot be sampled: $it") }
    }

    private fun mock(javaType: Class<*>): Any? = runCatching { if (javaType.isEnum) javaType.enumConstants.first() else Mockito.mock(javaType) }.getOrNull()

    private fun valueForArray(
        component: Class<*>,
        seed: Int,
    ): Any {
        val empty =
            java.lang.reflect.Array
                .newInstance(component, 0)
        val sample = BeanContractProbe.sampleFor(component, seed) ?: mock(component) ?: return empty
        val filled =
            java.lang.reflect.Array
                .newInstance(component, 1)
        java.lang.reflect.Array
            .set(filled, 0, sample)
        return filled
    }
}
