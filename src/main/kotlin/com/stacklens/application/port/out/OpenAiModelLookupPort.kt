package com.stacklens.application.port.out

interface OpenAiModelLookupPort {
    fun fetchAvailableModels(apiKey: String): List<String>
}
