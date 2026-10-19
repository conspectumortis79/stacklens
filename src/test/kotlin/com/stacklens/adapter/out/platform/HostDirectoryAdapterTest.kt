package com.stacklens.adapter.out.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class HostDirectoryAdapterTest {
    private val adapter = HostDirectoryAdapter()

    @Test
    fun `directories mirror the jvm host properties named by the port contract`() {
        assertEquals(System.getProperty("user.home", ""), adapter.homeDirectory())
        assertEquals(System.getProperty("user.dir", ""), adapter.workingDirectory())
        assertEquals(System.getProperty("java.io.tmpdir", "/tmp"), adapter.temporaryDirectory())
    }

    @Test
    fun `working and temporary directories are resolvable paths`() {
        assertTrue(File(adapter.workingDirectory()).isAbsolute)
        assertTrue(File(adapter.temporaryDirectory()).isAbsolute)
    }
}
