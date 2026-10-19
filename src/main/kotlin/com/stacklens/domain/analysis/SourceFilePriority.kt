package com.stacklens.domain.analysis

fun sourceFilePriority(path: String): Int {
    val p = path.lowercase()
    return when {
        p.startsWith("src/main/") && !p.contains("/resources/") -> 0
        p.startsWith("src/main/resources/") && !p.contains("/static") && !p.contains("/templates") -> 1
        p.contains("/test/") -> 2
        p.contains("/static/") || p.contains("/templates/") -> 4
        else -> 3
    }
}
