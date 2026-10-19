package com.stacklens.domain.text

object SourceLanguages {
    const val TEXT: String = "text"

    private val BY_EXTENSION: Map<String, String> =
        listOf(
            "kotlin" to listOf("kt", "kts"),
            "java" to listOf("java", "groovy", "scala"),
            "python" to listOf("py"),
            "typescript" to listOf("ts", "tsx"),
            "javascript" to listOf("js", "jsx", "mjs", "cjs"),
            "go" to listOf("go"),
            "ruby" to listOf("rb"),
            "csharp" to listOf("cs"),
            "php" to listOf("php"),
            "swift" to listOf("swift"),
            "rust" to listOf("rs"),
            "cpp" to listOf("cpp", "cc", "cxx", "hpp"),
            "c" to listOf("c", "h"),
            "html" to listOf("html"),
            "xml" to listOf("xml"),
            "sql" to listOf("sql"),
        ).flatMap { (language, extensions) -> extensions.map { it to language } }.toMap()

    fun of(path: String): String {
        val extension = path.substringAfterLast('.', "").lowercase()
        return BY_EXTENSION[extension] ?: TEXT
    }
}
