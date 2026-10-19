package com.stacklens.adapter.out.compiler

object JvmThrowableSignature {
    private val JVM_THROWABLE_PREFIXES: List<String> =
        listOf(
            "java.",
            "javax.",
            "kotlin.",
            "org.jetbrains.kotlin.",
            "kotlinx.",
        )

    fun looksLikeThrowable(output: String): Boolean {
        if (output.isEmpty()) return false
        if (!output.contains(STACK_FRAME)) return false
        if (output.contains(EXCEPTION_IN_THREAD)) return true
        val firstLine = output.substringBefore('\n')
        return JVM_THROWABLE_PREFIXES.any { prefix -> firstLine.startsWith(prefix) && firstLine.contains(": ") }
    }

    private const val EXCEPTION_IN_THREAD = "Exception in thread"

    private const val STACK_FRAME = "\tat "
}
