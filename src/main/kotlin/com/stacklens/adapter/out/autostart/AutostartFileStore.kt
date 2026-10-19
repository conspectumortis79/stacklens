package com.stacklens.adapter.out.autostart

import com.stacklens.application.port.out.platform.AutostartStorageException
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

class AutostartFileStore(
    val homeDir: String,
    private val canonicalizer: (File) -> File = { file -> file.canonicalFile },
    private val regularFileMarker: (File) -> Boolean = { file ->
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).isRegularFile
    },
    private val processCommand: () -> String? = {
        ProcessHandle
            .current()
            .info()
            .command()
            .orElse(null)
    },
) {
    fun launchAgentsDir(): File = File(homeDir, "Library/LaunchAgents")

    fun macosPlist(label: String): File = File(launchAgentsDir(), "$label.plist")

    fun linuxAutostartDir(): File = File(homeDir, ".config/autostart")

    fun linuxDesktopFile(fileName: String): File = File(linuxAutostartDir(), fileName)

    fun exists(file: File): Boolean = file.exists()

    fun isDirectory(file: File): Boolean = file.isDirectory

    fun read(file: File): String = file.readText()

    fun write(
        file: File,
        content: String,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun isExecutable(path: String): Boolean = File(path).canExecute()

    fun homeApplicationsMacosExec(): String = File(homeDir, "Applications/StackLens.app/Contents/MacOS/StackLens").absolutePath

    fun currentProcessCommand(): String? = processCommand()

    fun deleteWithinHome(file: File) {
        if (!file.exists()) return
        val canonical = canonical(file)
        if (!canonical.absolutePath.startsWith(homePrefix())) {
            throw AutostartStorageException(KEY_DELETE_FAILED, canonical.absolutePath)
        }
        if (!isRegularFile(canonical)) {
            throw AutostartStorageException(KEY_DELETE_FAILED, canonical.absolutePath)
        }
        if (!canonical.delete()) {
            throw AutostartStorageException(KEY_DELETE_FAILED, canonical.absolutePath)
        }
    }

    private fun canonical(file: File): File = runCatching { canonicalizer(file) }.getOrElse { file.absoluteFile }

    private fun homePrefix(): String =
        runCatching { canonicalizer(File(homeDir)).path + File.separator }
            .getOrElse { homeDir + File.separator }

    private fun isRegularFile(file: File): Boolean = runCatching { regularFileMarker(file) }.getOrDefault(false)

    companion object {
        const val KEY_DELETE_FAILED: String = "config.autostart.be_delete_file_failed"
    }
}
