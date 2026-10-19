package com.stacklens.testsupport

import com.stacklens.application.port.out.process.ExecutableLookupPort

class FakeExecutableLookupPort(
    private val resolved: Map<String, String> = emptyMap(),
    private val directories: Map<String, List<String>> = emptyMap(),
) : ExecutableLookupPort {
    val resolveCalls: MutableList<Pair<String, List<String>>> = mutableListOf()

    override fun resolve(
        executable: String,
        preferredPaths: List<String>,
    ): String? {
        resolveCalls += executable to preferredPaths
        return resolved[executable]
    }

    override fun directoriesFor(executable: String): List<String> = directories[executable].orEmpty()
}
