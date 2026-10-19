package com.stacklens.application.port.out.classpath

data class ClasspathEntry(
    val filename: String,
    val content: String,
)

interface ClasspathResourcePort {
    fun listEntries(pattern: String): List<ClasspathEntry>

    fun readText(path: String): String

    fun exists(path: String): Boolean
}
