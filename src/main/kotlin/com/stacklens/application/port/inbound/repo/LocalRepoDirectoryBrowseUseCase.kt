package com.stacklens.application.port.inbound.repo

interface LocalRepoDirectoryBrowseUseCase {
    fun pickDirectory(): String?
}
