
package com.stacklens.domain

fun String?.splitCsvList(): List<String> = this?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
