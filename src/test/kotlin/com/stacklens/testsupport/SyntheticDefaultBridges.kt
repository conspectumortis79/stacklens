package com.stacklens.testsupport

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

object SyntheticDefaultBridges {
    fun invoke(
        owner: Any,
        methodName: String,
        args: List<Any?>,
        defaultMask: Int,
    ): Any? {
        val bridge =
            generateSequence(owner.javaClass as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .firstOrNull { it.name == "$methodName\$default" }
                ?: throw NoSuchElementException("no default bridge for $methodName on ${owner.javaClass.name}")
        bridge.isAccessible = true
        val arity = bridge.parameterTypes.size
        val head: List<Any?> = if (Modifier.isStatic(bridge.modifiers)) listOf(owner) + args else args
        val call = head.toMutableList()
        while (call.size < arity - 2) call += null
        call += defaultMask
        call += null
        return try {
            bridge.invoke(if (Modifier.isStatic(bridge.modifiers)) null else owner, *call.toTypedArray())
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
