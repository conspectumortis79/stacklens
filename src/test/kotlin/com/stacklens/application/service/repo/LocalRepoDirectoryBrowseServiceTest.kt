package com.stacklens.application.service.repo

import com.stacklens.application.port.out.platform.DirectoryPickerPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LocalRepoDirectoryBrowseServiceTest {
    private val directoryPicker: DirectoryPickerPort = mock()
    private val service = LocalRepoDirectoryBrowseService(directoryPicker)

    @Test
    fun `pickDirectory delegates the selected path`() {
        whenever(directoryPicker.pickDirectory()).thenReturn("/selected")

        assertEquals("/selected", service.pickDirectory())
    }

    @Test
    fun `pickDirectory delegates a missing selection as null`() {
        whenever(directoryPicker.pickDirectory()).thenReturn(null)

        assertNull(service.pickDirectory())
    }
}
