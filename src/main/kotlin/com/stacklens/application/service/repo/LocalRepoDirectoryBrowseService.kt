package com.stacklens.application.service.repo

import com.stacklens.application.port.inbound.repo.LocalRepoDirectoryBrowseUseCase
import com.stacklens.application.port.out.platform.DirectoryPickerPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class LocalRepoDirectoryBrowseService(
    private val directoryPicker: DirectoryPickerPort,
) : LocalRepoDirectoryBrowseUseCase {
    override fun pickDirectory(): String? = directoryPicker.pickDirectory()
}
