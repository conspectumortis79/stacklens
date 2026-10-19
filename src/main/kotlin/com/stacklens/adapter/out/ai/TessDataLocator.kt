package com.stacklens.adapter.out.ai

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class TessDataLocator(
    private val envReader: (String) -> String? = System::getenv,
    private val userHome: () -> String = { System.getProperty("user.home") },
    private val isDirectory: (String) -> Boolean = { File(it).isDirectory },
    private val hasLanguageModel: (String, String) -> Boolean = { directory, language ->
        File(directory, "$language.traineddata").isFile
    },
    private val trainedDataNames: (String) -> List<String> = { listTrainedDataNames(it) },
    private val ensureDirectory: (String) -> Boolean = { ensureDirectoryExists(it) },
    private val installLanguageModel: (String, String, String) -> Boolean = { sourceDirectory, language, targetDirectory ->
        linkOrCopyLanguageModel(sourceDirectory, language, targetDirectory)
    },
    private val systemDirectories: List<String> = KNOWN_SYSTEM_TESS_DATA_DIRS,
) {
    fun dataPathFor(languageSpec: String): String {
        val languages = languagesOf(languageSpec)
        val candidates = candidateDirectories()
        val completeDirectory = candidates.firstOrNull { directory -> languages.all { hasLanguageModel(directory, it) } }
        if (completeDirectory != null) return asDataPath(completeDirectory)
        val assembledDirectory = assembleLanguageModels(languages, candidates)
        if (assembledDirectory != null) return asDataPath(assembledDirectory)
        val usableDirectory = candidates.firstOrNull { hasAnyLanguageModel(it) } ?: return FALLBACK_TESS_DATA_PATH
        return asDataPath(usableDirectory)
    }

    private fun hasAnyLanguageModel(directory: String): Boolean = isDirectory(directory) && trainedDataNames(directory).any { it.endsWith(".traineddata") }

    internal fun candidateDirectories(): List<String> {
        val home = userHome()
        return listOfNotNull(envReader(TESS_DATA_ENV_VAR)?.trim()?.takeIf { it.isNotEmpty() }) +
            listOf(
                "$home/$STACKLENS_TESS_DATA_RELATIVE_PATH",
                "$home/.local/share/tessdata",
            ) +
            systemDirectories
    }

    internal fun assemblyDirectory(): String = "${userHome()}/$STACKLENS_TESS_DATA_RELATIVE_PATH"

    private fun assembleLanguageModels(
        languages: List<String>,
        candidates: List<String>,
    ): String? {
        val target = assemblyDirectory()
        val missing = languages.filterNot { hasLanguageModel(target, it) }
        val sources = missing.map { language -> candidates.firstOrNull { hasLanguageModel(it, language) }?.let { language to it } ?: return null }
        if (!ensureDirectory(target)) return null
        for ((language, source) in sources) {
            if (!installLanguageModel(source, language, target)) return null
        }
        val assembled = languages.all { hasLanguageModel(target, it) }
        return if (assembled) target else null
    }

    private fun languagesOf(languageSpec: String): List<String> =
        languageSpec
            .split('+', ',', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun asDataPath(directory: String): String = directory.trimEnd('/') + "/"

    companion object {
        const val TESS_DATA_ENV_VAR = "TESSDATA_PREFIX"

        const val STACKLENS_TESS_DATA_RELATIVE_PATH = ".stacklens/tessdata"

        const val FALLBACK_TESS_DATA_PATH = "/usr/share/tesseract-ocr/5/tessdata/"

        val KNOWN_SYSTEM_TESS_DATA_DIRS: List<String> =
            listOf(
                "/opt/homebrew/share/tessdata",
                "/usr/local/share/tessdata",
                "/usr/share/tesseract-ocr/5/tessdata",
                "/usr/share/tesseract-ocr/4.00/tessdata",
                "/usr/share/tesseract-ocr/tessdata",
                "/usr/share/tessdata",
            )
    }
}

internal fun listTrainedDataNames(directory: String): List<String> = File(directory).list()?.toList().orEmpty()

internal fun ensureDirectoryExists(directory: String): Boolean = File(directory).isDirectory || File(directory).mkdirs()

internal fun linkOrCopyLanguageModel(
    sourceDirectory: String,
    language: String,
    targetDirectory: String,
    link: (Path, Path) -> Unit = { target, source -> Files.createSymbolicLink(target, source) },
    copy: (Path, Path) -> Unit = { source, target -> Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES) },
): Boolean {
    val source = File(sourceDirectory, "$language.traineddata").toPath()
    val target = File(targetDirectory, "$language.traineddata").toPath()
    if (Files.isRegularFile(target)) return true
    if (runCatching { link(target, source) }.isSuccess) return true
    runCatching { copy(source, target) }
    return Files.isReadable(target)
}
