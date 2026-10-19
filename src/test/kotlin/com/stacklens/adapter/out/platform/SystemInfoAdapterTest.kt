package com.stacklens.adapter.out.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemInfoAdapterTest {
    private val adapter = SystemInfoAdapter()

    @Test
    fun `hostSystem mirrors the jvm system properties named by the port contract`() {
        val info = adapter.hostSystem()

        assertEquals(System.getProperty("os.name", "unknown"), info.osName)
        assertEquals(System.getProperty("os.arch", "unknown"), info.osArch)
        assertEquals(System.getProperty("os.version", ""), info.osVersion)
        assertEquals(System.getProperty("java.version", "unknown"), info.javaVersion)
    }

    @Test
    fun `hostSystem fills the fields the environment banner renders`() {
        val info = adapter.hostSystem()

        assertTrue(info.osName.isNotBlank())
        assertTrue(info.osArch.isNotBlank())
        assertTrue(info.javaVersion.isNotBlank())
    }
}
