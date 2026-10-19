package com.stacklens.application.service.loadtest

data class LoadTestSourceFile(
    val matchPath: String,
    val displayPath: String,
    val content: String,
) {
    val name: String
        get() = matchPath.substringAfterLast('/')

    companion object {
        fun of(
            path: String,
            content: String,
        ): LoadTestSourceFile = LoadTestSourceFile(matchPath = path, displayPath = path, content = content)
    }
}
