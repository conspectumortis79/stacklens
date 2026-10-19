package com.stacklens.domain.docker

data class ComposeContainerState(
    val entries: List<ComposeContainerEntry>,
) {
    val total: Int get() = entries.size
    val running: Int get() = entries.count { it.state == "running" }
    val any: Boolean get() = entries.isNotEmpty()
    val anyExitedNonZero: Boolean get() =
        entries.any {
            it.state == "dead" || (it.state == "exited" && it.exitCode != 0)
        }

    companion object {
        val EMPTY = ComposeContainerState(emptyList())
    }
}

data class ComposeContainerEntry(
    val name: String,
    val state: String,
    val exitCode: Int,
)
