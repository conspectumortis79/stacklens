package com.stacklens.testsupport

object CompanionAccessor {
    fun read(
        owner: Class<*>,
        accessor: String,
    ): Any? {
        val companionField =
            owner.declaredFields.firstOrNull { it.name == "Companion" }
                ?: throw IllegalArgumentException("${owner.name} has no companion object")
        companionField.isAccessible = true
        val companion = companionField.get(null)
        val method =
            companion.javaClass.declaredMethods.firstOrNull { it.name == accessor && it.parameterCount == 0 }
                ?: throw IllegalArgumentException("${owner.name}\$Companion has no no-arg accessor '$accessor'")
        method.isAccessible = true
        return method.invoke(companion)
    }
}
