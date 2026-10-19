package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.out.file.FileSystemPort
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files

@Component
class JavaFileSystemAdapter : FileSystemPort {
    override fun writeFile(
        file: File,
        content: String,
    ) {
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }

    override fun deleteFile(file: File): Boolean = file.exists() && file.isFile && file.delete()

    override fun isDirectory(file: File): Boolean = file.isDirectory

    override fun isEmptyDirectory(directory: File): Boolean = directory.list()?.isEmpty() ?: false

    override fun isFile(file: File): Boolean = file.isFile

    override fun exists(file: File): Boolean = file.exists()

    override fun size(file: File): Long = file.length()

    override fun readText(file: File): String = file.readText(Charsets.UTF_8)

    override fun readBytes(file: File): ByteArray = file.readBytes()

    override fun writeBytes(
        file: File,
        bytes: ByteArray,
    ) {
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()
        file.writeBytes(bytes)
    }

    override fun canWrite(file: File): Boolean = file.canWrite()

    override fun canRead(file: File): Boolean = file.canRead()

    override fun canonicalFile(file: File): File = file.canonicalFile

    override fun homeDirectory(): File = File(System.getProperty("user.home", ""))

    override fun listDirectories(
        root: File,
        relativeTo: File,
        maxDepth: Int,
        limit: Int,
    ): List<String> =
        root
            .walkTopDown()
            .maxDepth(maxDepth)
            .filter { it.isDirectory }
            .take(limit)
            .map { it.toRelativeString(relativeTo) }
            .toList()

    override fun listFiles(
        root: File,
        relativeTo: File,
        extension: String,
        limit: Int,
    ): List<String> =
        root
            .walkTopDown()
            .filter { it.isFile && it.extension == extension }
            .take(limit)
            .map { it.toRelativeString(relativeTo) }
            .toList()

    override fun listFilesIn(
        directory: File,
        extension: String,
    ): List<File> =
        directory
            .listFiles { f -> f.isFile && f.name.endsWith(".$extension") }
            ?.toList() ?: emptyList()

    override fun listEntries(directory: File): List<File> = directory.listFiles()?.toList() ?: emptyList()

    override fun firstFile(
        root: File,
        extension: String,
    ): File? =
        root
            .walkTopDown()
            .filter { it.isFile && it.extension == extension }
            .firstOrNull()

    override fun deleteDirectoryRecursively(directory: File): Boolean = directory.walkBottomUp().all { it.delete() }

    override fun createTempDirectory(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    override fun createTempFile(
        prefix: String,
        suffix: String,
    ): File = Files.createTempFile(prefix, suffix).toFile()
}
