package com.stacklens.application.service

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.out.i18n.I18nPort

class FakeI18n :
    I18nUseCase,
    I18nPort {
    val requests: MutableList<Pair<String, List<Any>>> = mutableListOf()

    override fun t(
        key: String,
        vararg args: Any,
    ): String {
        requests += key to args.toList()
        return if (args.isEmpty()) key else "$key(${args.joinToString(",")})"
    }

    override fun tFor(
        lang: String,
        key: String,
        vararg args: Any,
    ): String = t(key, *args)

    override fun translateError(e: Exception): String = e.message ?: e.javaClass.simpleName

    override fun resolveLanguage(): String = "de"
}
