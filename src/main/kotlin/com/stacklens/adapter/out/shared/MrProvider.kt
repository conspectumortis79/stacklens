package com.stacklens.adapter.out.shared

internal enum class MrProvider {
    GITHUB {
        override fun matches(
            host: String,
            path: String,
        ): Boolean = host == "github.com" || host.endsWith(".github.com")
    },
    GITLAB {
        override fun matches(
            host: String,
            path: String,
        ): Boolean = path.contains("/-/merge_requests")
    },
    ;

    abstract fun matches(
        host: String,
        path: String,
    ): Boolean

    companion object {
        fun resolve(
            host: String,
            path: String,
        ): MrProvider? = entries.firstOrNull { it.matches(host, path) }
    }
}
