package com.stacklens.adapter.out.textfilter

internal object TextFilterProperties {
    const val ENABLED_KEY: String = "stacklens.ai.filter.enabled"

    const val ENABLED_DEFAULT: Boolean = true

    const val ENABLED_VALUE: String = "\${" + ENABLED_KEY + ":true}"
}
