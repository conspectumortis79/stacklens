package com.stacklens.adapter.out.ai

import com.stacklens.testsupport.FakeNativeLibraryEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TesseractNativeLibraryBootstrapTest {
    @Test
    fun `configure swallows an environment that cannot be read`() {
        val environment =
            FakeNativeLibraryEnvironment(
                variables = mapOf("TESSERACT_LIBRARY_DIR" to "/lib"),
                existingFiles = setOf("/lib" to "libtesseract.dylib"),
                propertyFailure = IllegalStateException("security manager denied"),
            )

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertEquals(emptyList<Pair<String, String>>(), environment.writtenProperties)
    }

    @Test
    fun `configure does nothing when no tesseract library can be found`() {
        val environment = FakeNativeLibraryEnvironment(existingFiles = emptySet(), variables = mapOf("TESSERACT_LIBRARY_DIR" to "/empty/dir"))

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertEquals(emptyList<Pair<String, String>>(), environment.writtenProperties)
        assertEquals(emptyList<String>(), environment.loadedLibraries)
    }

    @Test
    fun `configure adds the detected library directory to an empty jna path`() {
        val environment =
            FakeNativeLibraryEnvironment(
                variables = mapOf("TESSERACT_LIBRARY_DIR" to "/opt/tesseract/lib"),
                existingFiles = setOf("/opt/tesseract/lib" to "libtesseract.dylib"),
            )

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertEquals(
            listOf("jna.library.path" to "/opt/tesseract/lib"),
            environment.writtenProperties,
        )
        assertEquals(listOf("/opt/tesseract/lib/libtesseract.dylib"), environment.loadedLibraries)
    }

    @Test
    fun `configure appends the detected directory to an existing jna path`() {
        val environment =
            FakeNativeLibraryEnvironment(
                variables = mapOf("TESSERACT_LIBRARY_DIR" to "/opt/tesseract/lib"),
                properties = mutableMapOf("jna.library.path" to "/already/here"),
                existingFiles = setOf("/opt/tesseract/lib" to "libtesseract.so.5"),
            )

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertEquals(
            listOf("jna.library.path" to "/already/here:/opt/tesseract/lib"),
            environment.writtenProperties,
        )
    }

    @Test
    fun `configure leaves a jna path that already contains the directory`() {
        val environment =
            FakeNativeLibraryEnvironment(
                variables = mapOf("TESSERACT_LIBRARY_DIR" to "/opt/tesseract/lib"),
                properties = mutableMapOf("jna.library.path" to "/opt:/opt/tesseract/lib"),
                existingFiles = setOf("/opt/tesseract/lib" to "libtesseract.so"),
            )

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertEquals(emptyList<Pair<String, String>>(), environment.writtenProperties)
        assertEquals(listOf("/opt/tesseract/lib/libtesseract.so"), environment.loadedLibraries)
    }

    @Test
    fun `configure prefers the dynamic loader path over the operating system defaults`() {
        val environment =
            FakeNativeLibraryEnvironment(
                variables =
                    mapOf(
                        "TESSERACT_LIBRARY_DIR" to "  ",
                        "DYLD_LIBRARY_PATH" to "/blank:/from/dyld",
                    ),
                existingFiles = setOf("/from/dyld" to "tesseract"),
            )

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertEquals(listOf("jna.library.path" to "/from/dyld"), environment.writtenProperties)
    }

    @Test
    fun `configure scans the shared library path on linux`() {
        val environment =
            FakeNativeLibraryEnvironment(
                macOs = false,
                variables = mapOf("LD_LIBRARY_PATH" to "/first/empty:/second/empty"),
                existingFiles = setOf("/usr/local/lib" to "libtesseract-5.dll"),
            )

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertTrue(
            environment.writtenProperties
                .single()
                .second
                .contains("/usr/local/lib"),
            "${environment.writtenProperties}",
        )
    }

    @Test
    fun `configure falls back to the user home locations`() {
        val environment =
            FakeNativeLibraryEnvironment(
                userHome = "/home/tester",
                existingFiles = setOf("/home/tester/.local/lib" to "libtesseract.dylib"),
            )

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertEquals(listOf("jna.library.path" to "/home/tester/.local/lib"), environment.writtenProperties)
    }

    @Test
    fun `preloading keeps scanning after a link error and stops after a success`() {
        val environment =
            FakeNativeLibraryEnvironment(
                variables = mapOf("TESSERACT_LIBRARY_DIR" to "/lib"),
                existingFiles = setOf("/lib" to "libtesseract.dylib", "/lib" to "libtesseract.5.dylib"),
                loadFailure = { path -> if (path.endsWith("libtesseract.dylib")) UnsatisfiedLinkError("broken") else null },
            )

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertEquals(
            listOf("/lib/libtesseract.dylib", "/lib/libtesseract.5.dylib"),
            environment.loadedLibraries,
        )
    }

    @Test
    fun `a runtime failure while preloading does not abort the configuration`() {
        val environment =
            FakeNativeLibraryEnvironment(
                variables = mapOf("TESSERACT_LIBRARY_DIR" to "/lib"),
                existingFiles = setOf("/lib" to "libtesseract.dylib"),
                loadFailure = { IllegalStateException("jna unavailable") },
            )

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertEquals(listOf("/lib/libtesseract.dylib"), environment.loadedLibraries)
        assertEquals(
            listOf("jna.library.path" to "/lib"),
            environment.writtenProperties,
        )
    }

    @Test
    fun `preloading keeps scanning when every candidate fails to load`() {
        val environment =
            FakeNativeLibraryEnvironment(
                variables = mapOf("TESSERACT_LIBRARY_DIR" to "/lib"),
                existingFiles = setOf("/lib" to "libtesseract.dylib", "/lib" to "tesseract.dll"),
                loadFailure = { UnsatisfiedLinkError("cannot load") },
            )

        TesseractNativeLibraryBootstrap(environment).configureNativeLibraryPath()

        assertEquals(listOf("/lib/libtesseract.dylib", "/lib/tesseract.dll"), environment.loadedLibraries)
    }
}
