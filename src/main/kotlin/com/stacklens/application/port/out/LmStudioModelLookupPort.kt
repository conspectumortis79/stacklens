package com.stacklens.application.port.out

interface LmStudioModelLookupPort {
    fun fetchAvailableModels(baseUrl: String): List<String>
}
