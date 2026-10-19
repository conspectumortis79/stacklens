package com.stacklens.application.port.out.repo

import java.io.File

data class ScannedPath(
    val file: File,
    val relativePath: String,
)

data class ScannedTextFile(
    val path: ScannedPath,
    val content: String,
)

data class DirectoryEntry(
    val name: String,
    val depth: Int,
    val isDirectory: Boolean,
)

interface LocalDirectoryScanPort {
    fun textFiles(
        root: File,
        accept: (ScannedPath) -> Boolean,
        maxContentChars: Int,
        limit: Int,
    ): List<ScannedTextFile>

    fun paths(
        root: File,
        accept: (ScannedPath) -> Boolean,
        limit: Int,
    ): List<ScannedPath>

    fun filesNamed(
        root: File,
        names: Set<String>,
        descendInto: (directory: File) -> Boolean,
    ): List<File>

    fun entries(
        root: File,
        include: (file: File) -> Boolean,
        maxEntries: Int,
    ): List<DirectoryEntry>
}
