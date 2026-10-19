package com.stacklens.application.service

import com.stacklens.application.port.inbound.platform.AppKnowledgeUseCase
import com.stacklens.application.port.out.classpath.ClasspathEntry
import com.stacklens.application.port.out.classpath.ClasspathResourcePort
import com.stacklens.application.port.out.file.FileReadWritePort
import com.stacklens.application.port.out.platform.AppKnowledgePort
import com.stacklens.application.port.out.platform.HostDirectoryPort
import com.stacklens.application.stereotype.ApplicationService
import java.io.File

@ApplicationService
open class AppKnowledgeService(
    private val classpathResource: ClasspathResourcePort,
    private val hostDirectories: HostDirectoryPort,
    private val fileSystem: FileReadWritePort,
) : AppKnowledgeUseCase,
    AppKnowledgePort {
    internal open fun getClasspathEntries(pattern: String): List<ClasspathEntry> = classpathResource.listEntries(pattern)

    private data class Entry(
        val path: String,
        val title: String,
        val content: String,
    )

    private val cache: List<Entry> by lazy { loadAll() }

    override fun index(): String =
        buildString {
            appendLine("Available StackLens knowledge documents:")
            cache.forEach { e ->
                val firstLine =
                    e.content
                        .lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                        ?.take(140) ?: e.title
                appendLine("- ${e.path}: $firstLine")
            }
        }.trim()

    override fun relevant(
        query: String,
        maxFiles: Int,
        perFileChars: Int,
    ): String {
        if (query.isBlank()) return ""
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return ""

        val scored =
            cache
                .map { e ->
                    val haystack = (e.path + "\n" + e.title + "\n" + e.content).lowercase()
                    val score = tokens.sumOf { tok -> countOccurrences(haystack, tok) }
                    e to score
                }.filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(maxFiles)

        if (scored.isEmpty()) return ""
        return buildString {
            scored.forEach { (e, _) ->
                appendLine("===== ${e.path} =====")
                appendLine(e.content.take(perFileChars))
                if (e.content.length > perFileChars) appendLine("[…truncated…]")
                appendLine()
            }
        }.trim()
    }

    override fun readme(): String? = cache.firstOrNull { it.path == "README.md" }?.content

    internal open fun readmeCandidates(): List<File> =
        listOf(
            File("README.md"),
            File(hostDirectories.workingDirectory(), "README.md"),
        )

    private fun loadAll(): List<Entry> {
        val list = mutableListOf<Entry>()
        loadPattern("classpath:prompts/**/*.md", "prompts").forEach { list += it }
        loadPattern("classpath:agents/**/*.md", "agents").forEach { list += it }
        runCatching {
            readmeCandidates().firstOrNull { fileSystem.exists(it) && fileSystem.isFile(it) }?.let { f ->
                val text = fileSystem.readText(f)
                list += Entry("README.md", "StackLens README", text)
            }
        }
        return list
    }

    private fun loadPattern(
        pattern: String,
        prefix: String,
    ): List<Entry> =
        runCatching {
            getClasspathEntries(pattern).map { entry ->
                Entry("$prefix/${entry.filename}", entry.filename.removeSuffix(".md"), entry.content)
            }
        }.getOrElse { emptyList() }

    private fun tokenize(s: String): List<String> {
        val stop =
            setOf(
                "der",
                "die",
                "das",
                "und",
                "oder",
                "mit",
                "ohne",
                "ein",
                "eine",
                "einen",
                "ist",
                "sind",
                "von",
                "zu",
                "in",
                "im",
                "für",
                "fur",
                "auf",
                "den",
                "dem",
                "als",
                "wie",
                "was",
                "wer",
                "ich",
                "du",
                "er",
                "sie",
                "es",
                "wir",
                "ihr",
                "sie",
                "the",
                "and",
                "or",
                "with",
                "without",
                "is",
                "are",
                "of",
                "to",
                "for",
                "on",
                "as",
                "what",
                "who",
                "i",
                "you",
                "he",
                "she",
                "it",
                "we",
                "they",
                "this",
                "that",
                "bitte",
                "please",
                "kannst",
                "kann",
                "mir",
                "me",
                "my",
                "mein",
                "meine",
            )
        return s
            .lowercase()
            .split(Regex("[^\\p{L}0-9]+"))
            .filter { it.length >= 3 && it !in stop }
            .distinct()
    }

    private fun countOccurrences(
        haystack: String,
        needle: String,
    ): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            val f = haystack.indexOf(needle, idx)
            if (f < 0) break
            count++
            idx = f + needle.length
        }
        return count
    }
}
