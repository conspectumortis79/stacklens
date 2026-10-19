package com.stacklens.domain.text.filter

data class FilterTestCase(
    val name: String,
    val input: String,
    val expected: String,
)

data class FilterBundle(
    val specs: List<FilterSpec>,
    val tests: Map<String, List<FilterTestCase>>,
) {
    init {
        val specNames = specs.map { it.name }.toSet()
        val unknownTestKeys = tests.keys - specNames
        require(unknownTestKeys.isEmpty()) {
            "FilterBundle: tests reference unknown specs: $unknownTestKeys"
        }
    }

    fun spec(name: String): FilterSpec? = specs.firstOrNull { it.name == name }
}
