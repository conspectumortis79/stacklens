package com.stacklens.domain.text.filter

data class FilterResult(
    val text: String,
    val originalChars: Int,
    val filteredChars: Int,
    val specName: String,
    val matchedShortCircuit: Boolean = false,
) {
    val savingsPercent: Int
        get() =
            if (originalChars == 0) {
                0
            } else {
                (((originalChars - filteredChars).toDouble() / originalChars) * 100).toInt()
            }
}
