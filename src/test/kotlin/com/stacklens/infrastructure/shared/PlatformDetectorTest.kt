package com.stacklens.infrastructure.shared
import com.stacklens.adapter.out.platform.PlatformDetector
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class PlatformDetectorTest {
    @Test
    fun `detect returns a platform with non-null os`() {
        val platform = PlatformDetector.detect()
        assertNotNull(platform.os)
        assertTrue(platform.os.isNotBlank())
    }

    @Test
    fun `detect returns valid os type`() {
        val platform = PlatformDetector.detect()
        val validTypes = setOf("macos", "linux", "windows", "unsupported")
        assertTrue(platform.os in validTypes, "OS type should be one of $validTypes, got: ${platform.os}")
    }

    @Test
    fun `detect OsType wire values`() {
        assertEquals("macos", OsType.MACOS.wire)
        assertEquals("linux", OsType.LINUX.wire)
        assertEquals("windows", OsType.WINDOWS.wire)
        assertEquals("unsupported", OsType.UNSUPPORTED.wire)
    }

    @Test
    fun `detect Platform os getter returns wire value`() {
        val platform = PlatformDetector.detect()
        assertEquals(platform.osType.wire, platform.os)
    }

    @Test
    fun `detect Platform pretty is non-blank`() {
        val platform = PlatformDetector.detect()
        assertTrue(platform.pretty.isNotBlank())
    }

    @Test
    fun `detect returns macos on mac`() {
        val platform = PlatformDetector.detect(osName = "mac os x")
        assertEquals(OsType.MACOS, platform.osType)
        assertEquals("macos", platform.distroId)
        assertTrue(platform.supported)
    }

    @Test
    fun `detect returns macos with empty os version when null`() {
        val saved = System.getProperty("os.version")
        try {
            System.clearProperty("os.version")
            val platform = PlatformDetector.detect(osName = "mac os x")
            assertEquals(OsType.MACOS, platform.osType)
            assertEquals("", platform.distroVersion)
        } finally {
            if (saved != null) System.setProperty("os.version", saved) else System.clearProperty("os.version")
        }
    }

    @Test
    fun `detect returns linux platform`() {
        val platform = PlatformDetector.detect(osName = "linux")
        assertEquals(OsType.LINUX, platform.osType)
    }

    @Test
    fun `detect returns linux supported for ubuntu`() {
        val tmpFile = Files.createTempFile("os-release", ".txt").toFile()
        try {
            tmpFile.writeText("ID=ubuntu\nVERSION_ID=22.04\nPRETTY_NAME=\"Ubuntu 22.04\"\n")
            val parsed = PlatformDetector.readOsRelease(tmpFile)
            val id = parsed["ID"]?.lowercase()
            val platform =
                Platform(
                    OsType.LINUX,
                    distroId = id,
                    distroVersion = parsed["VERSION_ID"],
                    pretty = parsed["PRETTY_NAME"] ?: "Linux",
                    supported = id in setOf("ubuntu", "pop", "debian", "linuxmint", "elementary"),
                )
            assertTrue(platform.supported)
            assertEquals("ubuntu", platform.distroId)
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun `detect returns windows platform`() {
        val platform = PlatformDetector.detect(osName = "windows 10")
        assertEquals(OsType.WINDOWS, platform.osType)
        assertFalse(platform.supported)
    }

    @Test
    fun `detect returns unsupported platform for unknown os`() {
        val platform = PlatformDetector.detect(osName = "freebsd")
        assertEquals(OsType.UNSUPPORTED, platform.osType)
        assertFalse(platform.supported)
    }

    @Test
    fun `detect Platform data class equals and copy`() {
        val p1 = Platform(OsType.MACOS, "macos", "14.0", "macOS 14.0", true)
        val p2 = p1.copy()
        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
        val p3 = p1.copy(supported = false)
        assertNotEquals(p1, p3)
    }

    @Test
    fun `OsType enum values`() {
        val values = OsType.values()
        assertEquals(4, values.size)
    }

    @Test
    fun `readOsRelease returns empty map when file does not exist`() {
        val result = PlatformDetector.readOsRelease(File("/nonexistent/os-release-test-xyz"))
        assertEquals(emptyMap<String, String>(), result)
    }

    @Test
    fun `readOsRelease no-arg overload uses default etc os-release path`() {
        val result = PlatformDetector.readOsRelease()

        assertNotNull(result)
    }

    @Test
    fun `readOsRelease parses key-value pairs correctly`() {
        val tmpFile = Files.createTempFile("os-release", ".txt").toFile()
        try {
            tmpFile.writeText("ID=ubuntu\nVERSION_ID=\"22.04\"\nPRETTY_NAME=\"Ubuntu 22.04 LTS\"\n=ignored\n")
            val result = PlatformDetector.readOsRelease(tmpFile)
            assertEquals("ubuntu", result["ID"])
            assertEquals("22.04", result["VERSION_ID"])
            assertEquals("Ubuntu 22.04 LTS", result["PRETTY_NAME"])
            assertFalse(result.containsKey(""))
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun `readOsRelease handles directory input gracefully`() {
        val dir = Files.createTempDirectory("os-release-test").toFile()
        try {
            val result = PlatformDetector.readOsRelease(dir)
            assertEquals(emptyMap<String, String>(), result)
        } finally {
            dir.delete()
        }
    }

    @Test
    fun `detect linux with temp os-release file covers non-null ID and PRETTY_NAME`() {
        val tmpFile = Files.createTempFile("os-release", ".txt").toFile()
        try {
            tmpFile.writeText("ID=ubuntu\nVERSION_ID=22.04\nPRETTY_NAME=\"Ubuntu 22.04 LTS\"\n")
            val platform = PlatformDetector.detect(osName = "linux", osReleaseFile = tmpFile)
            assertEquals(OsType.LINUX, platform.osType)
            assertEquals("ubuntu", platform.distroId)
            assertEquals("22.04", platform.distroVersion)
            assertEquals("Ubuntu 22.04 LTS", platform.pretty)
            assertTrue(platform.supported)
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun `detect linux with missing PRETTY_NAME falls back to Linux`() {
        val tmpFile = Files.createTempFile("os-release", ".txt").toFile()
        try {
            tmpFile.writeText("ID=ubuntu\n")
            val platform = PlatformDetector.detect(osName = "linux", osReleaseFile = tmpFile)
            assertEquals("Linux", platform.pretty)
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun `detect linux with unknown distro is not supported`() {
        val tmpFile = Files.createTempFile("os-release", ".txt").toFile()
        try {
            tmpFile.writeText("ID=arch\nPRETTY_NAME=\"Arch Linux\"\n")
            val platform = PlatformDetector.detect(osName = "linux", osReleaseFile = tmpFile)
            assertFalse(platform.supported)
            assertEquals("arch", platform.distroId)
            assertNull(platform.distroVersion)
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun `detect linux with missing ID in os-release sets distroId to null`() {
        val tmpFile = Files.createTempFile("os-release", ".txt").toFile()
        try {
            tmpFile.writeText("PRETTY_NAME=\"Some Linux\"\nVERSION_ID=1.0\n")
            val platform = PlatformDetector.detect(osName = "linux", osReleaseFile = tmpFile)
            assertNull(platform.distroId)
            assertFalse(platform.supported)
        } finally {
            tmpFile.delete()
        }
    }
}
