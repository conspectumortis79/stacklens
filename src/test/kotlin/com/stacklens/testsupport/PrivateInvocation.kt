package com.stacklens.testsupport

import java.lang.reflect.InvocationTargetException

object PrivateInvocation {
    fun invoke(
        target: Any,
        methodName: String,
        vararg args: Any?,
    ): Any? {
        val method =
            generateSequence(target.javaClass as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .firstOrNull { it.name == methodName && it.parameterCount == args.size }
                ?: throw NoSuchElementException("no method $methodName/${args.size} on ${target.javaClass.name}")
        method.isAccessible = true
        return try {
            method.invoke(target, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
