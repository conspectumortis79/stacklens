package com.stacklens.infrastructure.shared

import com.stacklens.adapter.out.platform.PlatformDetector
import com.stacklens.adapter.out.platform.PlatformDetectorAdapter
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformDetectorAdapterTest {
    @Test
    fun `detect returns a Platform for the current host`() {
        val platform = PlatformDetectorAdapter().detect()
        assertNotNull(platform)
        assertTrue(platform.pretty.isNotEmpty())
        assertTrue(platform.os in setOf("macos", "linux", "windows", "unsupported"))
    }

    @Test
    fun `detect uses the injected detector function`() {
        val raw =
            Platform(
                osType = OsType.MACOS,
                distroId = "macos",
                distroVersion = "14.0",
                pretty = "macOS 14",
                supported = true,
            )
        val mapped = PlatformDetectorAdapter(detector = { raw }).detect()
        assertEquals(OsType.MACOS, mapped.osType)
        assertEquals("macOS 14", mapped.pretty)
    }

    @Test
    fun `detect with mac osName resolves to MACOS`() {
        val adapter = PlatformDetectorAdapter(detector = { PlatformDetector.detect(osName = "mac os x") })
        assertEquals(OsType.MACOS, adapter.detect().osType)
    }

    @Test
    fun `detect with windows osName resolves to WINDOWS`() {
        val adapter = PlatformDetectorAdapter(detector = { PlatformDetector.detect(osName = "windows 10") })
        assertEquals(OsType.WINDOWS, adapter.detect().osType)
    }

    @Test
    fun `detect with unsupported osName resolves to UNSUPPORTED`() {
        val adapter = PlatformDetectorAdapter(detector = { PlatformDetector.detect(osName = "plan9") })
        assertEquals(OsType.UNSUPPORTED, adapter.detect().osType)
    }

    @Test
    fun `detect with linux osName resolves to LINUX`() {
        val adapter = PlatformDetectorAdapter(detector = { PlatformDetector.detect(osName = "linux", osReleaseFile = java.io.File("/nonexistent")) })
        assertEquals(OsType.LINUX, adapter.detect().osType)
    }
}
