package com.stacklens.testsupport

import com.stacklens.application.port.out.LmStudioModelLookupPort

class FakeLmStudioModelLookupPort(
    private val models: (baseUrl: String) -> List<String> = { emptyList() },
) : LmStudioModelLookupPort {
    override fun fetchAvailableModels(baseUrl: String): List<String> = models(baseUrl)
}
