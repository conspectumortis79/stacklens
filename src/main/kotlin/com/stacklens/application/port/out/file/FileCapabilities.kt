package com.stacklens.application.port.out.file

import java.io.File

interface FileContentStore {
    fun writeFile(
        file: File,
        content: String,
    )

    fun writeBytes(
        file: File,
        bytes: ByteArray,
    )

    fun readText(file: File): String

    fun readBytes(file: File): ByteArray
}

interface FileAttributes {
    fun exists(file: File): Boolean

    fun isFile(file: File): Boolean

    fun isDirectory(file: File): Boolean

    fun isEmptyDirectory(directory: File): Boolean

    fun size(file: File): Long

    fun canWrite(file: File): Boolean

    fun canRead(file: File): Boolean

    fun canonicalFile(file: File): File

    fun homeDirectory(): File
}

interface FileListing {
    fun listDirectories(
        root: File,
        relativeTo: File,
        maxDepth: Int,
        limit: Int,
    ): List<String>

    fun listFiles(
        root: File,
        relativeTo: File,
        extension: String,
        limit: Int,
    ): List<String>

    fun firstFile(
        root: File,
        extension: String,
    ): File?

    fun listFilesIn(
        directory: File,
        extension: String,
    ): List<File>

    fun listEntries(directory: File): List<File>
}

interface FileDeletion {
    fun deleteFile(file: File): Boolean

    fun deleteDirectoryRecursively(directory: File): Boolean
}

interface TempFileCreation {
    fun createTempFile(
        prefix: String,
        suffix: String,
    ): File

    fun createTempDirectory(prefix: String): File
}
