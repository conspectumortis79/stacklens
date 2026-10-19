package com.stacklens.testsupport

import com.stacklens.application.port.out.LmStudioModelLookupPort

class RecordingLmStudioLookup(
    private val models: (String) -> List<String>,
) : LmStudioModelLookupPort {
    val requested: MutableList<String> = mutableListOf()

    override fun fetchAvailableModels(baseUrl: String): List<String> {
        requested += baseUrl
        return models(baseUrl)
    }
}
