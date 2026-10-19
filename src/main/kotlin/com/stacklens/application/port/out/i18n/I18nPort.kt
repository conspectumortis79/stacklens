package com.stacklens.application.port.out.i18n

interface I18nPort {
    fun t(
        key: String,
        vararg args: Any,
    ): String

    fun tFor(
        lang: String,
        key: String,
        vararg args: Any,
    ): String

    fun translateError(e: Exception): String

    fun resolveLanguage(): String
}
