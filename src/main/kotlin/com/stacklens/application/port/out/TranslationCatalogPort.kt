package com.stacklens.application.port.out

interface TranslationCatalogPort {
    fun load(language: String): Map<String, String>
}
