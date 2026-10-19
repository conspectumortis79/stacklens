package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.shared.NativeLibraryEnvironment
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
@Lazy(false)
class TesseractNativeLibraryBootstrap(
    private val environment: NativeLibraryEnvironment,
) {
    private val log = LoggerFactory.getLogger(TesseractNativeLibraryBootstrap::class.java)

    @PostConstruct
    fun configureNativeLibraryPath() {
        val libDir = detectTesseractLibraryDirectory()
        if (libDir == null) {
            log.warn(
                "Tesseract native library not found in any known location. " +
                    "Install via 'brew install tesseract' (macOS) or 'apt install tesseract-ocr' (Linux).",
            )
            return
        }
        try {
            val currentPath = environment.property(JNA_LIBRARY_PATH_PROPERTY) ?: ""
            if (currentPath.contains(libDir)) {
                log.info("Tesseract native library directory already in jna.library.path: {}", libDir)
            } else {
                val newPath = if (currentPath.isBlank()) libDir else "$currentPath:$libDir"
                environment.setProperty(JNA_LIBRARY_PATH_PROPERTY, newPath)
                log.info("Added Tesseract native library directory to jna.library.path: {}", libDir)
            }
            preLoadLibrary(libDir)
        } catch (e: Exception) {
            log.warn("Could not configure Tesseract native library path: {}", e.message)
        }
    }

    private fun preLoadLibrary(libDir: String) {
        for (name in TESSERACT_LIBRARY_NAMES) {
            if (!environment.fileExists(libDir, name)) continue
            val absolutePath = environment.absolutePathOf(libDir, name)
            try {
                environment.loadLibrary(absolutePath)
                log.info("Pre-loaded Tesseract native library: {}", absolutePath)
                return
            } catch (e: UnsatisfiedLinkError) {
                log.debug("Could not pre-load {}: {}", absolutePath, e.message)
            } catch (e: Exception) {
                log.debug("Could not pre-load {}: {}", absolutePath, e.message)
            }
        }
        log.info("Native library files in {} will be resolved by JNA at first use", libDir)
    }

    private fun detectTesseractLibraryDirectory(): String? {
        val candidates =
            buildList {
                environment.environment(ENV_LIBRARY_DIR)?.let { add(it) }
                environment.environment(ENV_DYLD_PATH)?.split(environment.pathSeparator())?.forEach { add(it) }
                environment.environment(ENV_LD_PATH)?.split(environment.pathSeparator())?.forEach { add(it) }
                if (environment.isMacOs()) {
                    addAll(listOf("/opt/homebrew/lib", "/usr/local/lib", "/opt/local/lib"))
                } else {
                    addAll(
                        listOf(
                            "/usr/lib",
                            "/usr/lib/x86_64-linux-gnu",
                            "/usr/lib/aarch64-linux-gnu",
                            "/usr/local/lib",
                        ),
                    )
                }
                val userHome = environment.userHome()
                addAll(
                    listOf(
                        "$userHome/.local/lib",
                        "$userHome/Library/Frameworks/tesseract.framework",
                        "/Library/Frameworks/tesseract.framework",
                    ),
                )
            }
        for (dir in candidates) {
            if (dir.isBlank()) continue
            if (TESSERACT_LIBRARY_NAMES.any { environment.fileExists(dir, it) }) return dir
        }
        return null
    }

    companion object {
        const val JNA_LIBRARY_PATH_PROPERTY = "jna.library.path"

        const val ENV_LIBRARY_DIR = "TESSERACT_LIBRARY_DIR"

        const val ENV_DYLD_PATH = "DYLD_LIBRARY_PATH"

        const val ENV_LD_PATH = "LD_LIBRARY_PATH"

        val TESSERACT_LIBRARY_NAMES: List<String> =
            listOf(
                "libtesseract.dylib",
                "libtesseract.5.dylib",
                "libtesseract.so.5",
                "libtesseract.so.5.0.0",
                "libtesseract.so.4",
                "libtesseract.so.4.0.0",
                "libtesseract.so",
                "tesseract.dll",
                "libtesseract-5.dll",
                "tesseract",
            )
    }
}
