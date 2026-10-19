package com.stacklens.testsupport

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.out.i18n.I18nPort

class FakeI18n(
    private val language: String = "en",
    private val marker: String = "i18n",
) : I18nUseCase,
    I18nPort {
    val translatedKeys: MutableList<String> = mutableListOf()

    val translatedErrors: MutableList<Exception> = mutableListOf()

    override fun resolveLanguage(): String = language

    override fun t(
        key: String,
        vararg args: Any,
    ): String = rendered(key, args)

    override fun tFor(
        lang: String,
        key: String,
        vararg args: Any,
    ): String = rendered("$lang/$key", args)

    override fun translateError(e: Exception): String {
        translatedErrors += e
        return "$marker:${e.message}"
    }

    private fun rendered(
        key: String,
        args: Array<out Any>,
    ): String {
        translatedKeys += key
        return if (args.isEmpty()) "$marker:$key" else "$marker:$key|${args.joinToString(",")}"
    }
}
