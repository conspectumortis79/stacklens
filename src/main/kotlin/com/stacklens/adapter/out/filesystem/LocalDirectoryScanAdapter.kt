package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.out.repo.DirectoryEntry
import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import com.stacklens.application.port.out.repo.ScannedPath
import com.stacklens.application.port.out.repo.ScannedTextFile
import org.springframework.stereotype.Component
import java.io.File

@Component
class LocalDirectoryScanAdapter : LocalDirectoryScanPort {
    override fun textFiles(
        root: File,
        accept: (ScannedPath) -> Boolean,
        maxContentChars: Int,
        limit: Int,
    ): List<ScannedTextFile> = paths(root, accept, limit).map { ScannedTextFile(path = it, content = it.file.readText().take(maxContentChars)) }

    override fun paths(
        root: File,
        accept: (ScannedPath) -> Boolean,
        limit: Int,
    ): List<ScannedPath> =
        root
            .walkTopDown()
            .filter { it.isFile }
            .map { ScannedPath(file = it, relativePath = it.relativeTo(root).path) }
            .filter(accept)
            .take(limit)
            .toList()

    override fun filesNamed(
        root: File,
        names: Set<String>,
        descendInto: (directory: File) -> Boolean,
    ): List<File> {
        val found = mutableListOf<File>()

        fun walk(dir: File) {
            dir
                .listFiles()
                ?.forEach { entry ->
                    when {
                        entry.isDirectory -> if (descendInto(entry)) walk(entry)
                        entry.name in names -> found += entry
                    }
                }
        }

        walk(root)
        return found
    }

    override fun entries(
        root: File,
        include: (file: File) -> Boolean,
        maxEntries: Int,
    ): List<DirectoryEntry> {
        val collected = mutableListOf<DirectoryEntry>()

        fun walk(
            dir: File,
            depth: Int,
        ) {
            if (collected.size >= maxEntries) return
            val children = dir.listFiles() ?: return
            children
                .sortedBy { it.path }
                .forEach { entry ->
                    if (collected.size >= maxEntries) return
                    if (!include(entry)) return@forEach
                    collected += DirectoryEntry(name = entry.name, depth = depth, isDirectory = entry.isDirectory)
                    if (entry.isDirectory) walk(entry, depth + 1)
                }
        }

        walk(root, 0)
        return collected
    }
}
