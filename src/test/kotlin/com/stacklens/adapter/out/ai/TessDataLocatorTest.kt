package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class TessDataLocatorTest {
    private class InstallRecorder {
        val requests = mutableListOf<Triple<String, String, String>>()

        fun install(
            sourceDirectory: String,
            language: String,
            targetDirectory: String,
        ): Boolean {
            requests += Triple(sourceDirectory, language, targetDirectory)
            File(targetDirectory, "$language.traineddata").writeBytes(File(sourceDirectory, "$language.traineddata").readBytes())
            return true
        }
    }

    private fun directoryWith(
        root: Path,
        name: String,
        vararg languages: String,
    ): String {
        val dir = root.resolve(name).toFile()
        dir.mkdirs()
        languages.forEach { File(dir, "$it.traineddata").writeText("model-$it") }
        return dir.absolutePath
    }

    private fun scopedHasLanguageModel(root: Path): (String, String) -> Boolean =
        { directory, language ->
            File(directory).canonicalPath.startsWith(root.toFile().canonicalPath) && File(directory, "$language.traineddata").isFile
        }

    private fun scopedEnsureDirectory(root: Path): (String) -> Boolean =
        { directory ->
            File(directory).canonicalPath.startsWith(root.toFile().canonicalPath) && (File(directory).isDirectory || File(directory).mkdirs())
        }

    @Test
    fun `the environment prefix is used when it holds every requested language`(
        @TempDir root: Path,
    ) {
        val prefix = directoryWith(root, "prefix", "deu", "eng")
        val home = directoryWith(root, "home")

        val path =
            TessDataLocator(
                envReader = { prefix },
                userHome = { home },
                installLanguageModel = { _, _, _ -> error("nothing may be installed") },
            ).dataPathFor("deu+eng")

        assertEquals("$prefix/", path)
    }

    @Test
    fun `an incomplete prefix loses to a directory that holds every requested language`(
        @TempDir root: Path,
    ) {
        val prefix = directoryWith(root, "prefix", "deu")
        val home = directoryWith(root, "home")
        val complete = directoryWith(root, "home/.local/share/tessdata", "deu", "eng")

        val path =
            TessDataLocator(
                envReader = { prefix },
                userHome = { home },
                hasLanguageModel = scopedHasLanguageModel(root),
                installLanguageModel = { _, _, _ -> error("nothing may be installed") },
            ).dataPathFor("deu+eng")

        assertEquals("$complete/", path)
    }

    @Test
    fun `languages spread over several directories are collected into the stacklens directory`(
        @TempDir root: Path,
    ) {
        val prefix = directoryWith(root, "prefix", "eng")
        val home = directoryWith(root, "home")
        val userDirectory = directoryWith(root, "home/.local/share/tessdata", "deu")
        val installs = InstallRecorder()
        val assembly = File(home, ".stacklens/tessdata")

        val path =
            TessDataLocator(
                envReader = { prefix },
                userHome = { home },
                hasLanguageModel = scopedHasLanguageModel(root),
                ensureDirectory = scopedEnsureDirectory(root),
                installLanguageModel = { source, language, target -> installs.install(source, language, target) },
            ).dataPathFor("deu+eng")

        assertEquals("${assembly.absolutePath}/", path)
        assertEquals(
            listOf(
                Triple(userDirectory, "deu", assembly.absolutePath),
                Triple(prefix, "eng", assembly.absolutePath),
            ),
            installs.requests,
        )
    }

    @Test
    fun `an already complete stacklens directory is reused without installing anything`(
        @TempDir root: Path,
    ) {
        val home = directoryWith(root, "home")
        val assembly = File(home, ".stacklens/tessdata").also { it.mkdirs() }
        File(assembly, "deu.traineddata").writeText("model-deu")
        File(assembly, "eng.traineddata").writeText("model-eng")

        val path =
            TessDataLocator(
                envReader = { null },
                userHome = { home },
                installLanguageModel = { _, _, _ -> error("nothing may be installed") },
            ).dataPathFor("eng+deu")

        assertEquals("${assembly.absolutePath}/", path)
    }

    @Test
    fun `a language that exists nowhere leaves the first existing directory in place`(
        @TempDir root: Path,
    ) {
        val home = directoryWith(root, "home")
        val prefix = directoryWith(root, "prefix", "eng")
        val installs = InstallRecorder()

        val path =
            TessDataLocator(
                envReader = { prefix },
                userHome = { home },
                hasLanguageModel = scopedHasLanguageModel(root),
                ensureDirectory = scopedEnsureDirectory(root),
                installLanguageModel = { source, language, target -> installs.install(source, language, target) },
            ).dataPathFor("deu+eng")

        assertEquals("$prefix/", path)
        assertTrue(installs.requests.isEmpty())
        assertFalse(File(home, ".stacklens/tessdata/deu.traineddata").exists())
        assertFalse(File(home, ".stacklens/tessdata/eng.traineddata").exists())
    }

    @Test
    fun `an empty assembly directory is never handed to tesseract`(
        @TempDir root: Path,
    ) {
        val home = directoryWith(root, "home")
        val prefix = directoryWith(root, "prefix", "eng")

        val path =
            TessDataLocator(
                envReader = { prefix },
                userHome = { home },
                hasLanguageModel = scopedHasLanguageModel(root),
                ensureDirectory = { error("an assembly directory without every model is worse than none") },
                installLanguageModel = { _, _, _ -> error("no model may be installed") },
            ).dataPathFor("deu+eng")

        assertEquals("$prefix/", path)
        assertFalse(File(home, ".stacklens").exists())
    }

    @Test
    fun `the built in path is the last resort when no candidate exists`() {
        val path =
            TessDataLocator(
                envReader = { null },
                userHome = { "/nonexistent-home-directory" },
                isDirectory = { false },
                hasLanguageModel = { _, _ -> false },
                ensureDirectory = { false },
            ).dataPathFor("deu+eng")

        assertEquals(TessDataLocator.FALLBACK_TESS_DATA_PATH, path)
    }

    @Test
    fun `a blank prefix is dropped and the user directories are searched before the system ones`() {
        val candidates =
            TessDataLocator(
                envReader = { "   " },
                userHome = { "/home/tester" },
            ).candidateDirectories()

        assertEquals("/home/tester/.stacklens/tessdata", candidates[0])
        assertEquals("/home/tester/.local/share/tessdata", candidates[1])
        assertEquals(TessDataLocator.KNOWN_SYSTEM_TESS_DATA_DIRS, candidates.drop(2))
    }

    @Test
    fun `the prefix is the first candidate when it is set`() {
        val candidates =
            TessDataLocator(
                envReader = { "/from/env" },
                userHome = { "/home/tester" },
            ).candidateDirectories()

        assertEquals("/from/env", candidates.first())
        assertEquals(3 + TessDataLocator.KNOWN_SYSTEM_TESS_DATA_DIRS.size, candidates.size)
    }

    @Test
    fun `stray separators in the language spec are ignored`(
        @TempDir root: Path,
    ) {
        val home = directoryWith(root, "home")
        val prefix = directoryWith(root, "prefix", "deu", "eng")

        val path =
            TessDataLocator(
                envReader = { prefix },
                userHome = { home },
                hasLanguageModel = scopedHasLanguageModel(root),
                installLanguageModel = { _, _, _ -> error("nothing may be installed") },
            ).dataPathFor("deu + , eng")

        assertEquals("$prefix/", path)
    }

    @Test
    fun `an assembly directory that cannot be created keeps the candidate search going`(
        @TempDir root: Path,
    ) {
        val home = directoryWith(root, "home")
        val prefix = directoryWith(root, "prefix", "eng")
        directoryWith(root, "home/.local/share/tessdata", "deu")

        val path =
            TessDataLocator(
                envReader = { prefix },
                userHome = { home },
                hasLanguageModel = scopedHasLanguageModel(root),
                trainedDataNames = { emptyList() },
                ensureDirectory = { false },
                installLanguageModel = { _, _, _ -> error("nothing may be installed") },
            ).dataPathFor("deu+eng")

        assertEquals(TessDataLocator.FALLBACK_TESS_DATA_PATH, path)
        assertFalse(File(home, ".stacklens").exists())
    }

    @Test
    fun `a model that cannot be installed discards the assembly directory`(
        @TempDir root: Path,
    ) {
        val home = directoryWith(root, "home")
        val prefix = directoryWith(root, "prefix", "eng")
        directoryWith(root, "home/.local/share/tessdata", "deu")

        val path =
            TessDataLocator(
                envReader = { prefix },
                userHome = { home },
                hasLanguageModel = scopedHasLanguageModel(root),
                trainedDataNames = { emptyList() },
                ensureDirectory = scopedEnsureDirectory(root),
                installLanguageModel = { _, _, _ -> false },
            ).dataPathFor("deu+eng")

        assertEquals(TessDataLocator.FALLBACK_TESS_DATA_PATH, path)
        assertFalse(File(home, ".stacklens/tessdata/deu.traineddata").exists())
    }

    @Test
    fun `an assembly that stays incomplete after installing is discarded`(
        @TempDir root: Path,
    ) {
        val home = directoryWith(root, "home")
        val prefix = directoryWith(root, "prefix", "eng")
        directoryWith(root, "home/.local/share/tessdata", "deu")

        val path =
            TessDataLocator(
                envReader = { prefix },
                userHome = { home },
                hasLanguageModel = scopedHasLanguageModel(root),
                trainedDataNames = { emptyList() },
                ensureDirectory = scopedEnsureDirectory(root),
                installLanguageModel = { _, _, _ -> true },
            ).dataPathFor("deu+eng")

        assertEquals(TessDataLocator.FALLBACK_TESS_DATA_PATH, path)
    }

    @Test
    fun `a candidate path that is no directory yields no model names`(
        @TempDir root: Path,
    ) {
        val home = directoryWith(root, "home")
        val strayFile = File(home, "not-a-directory").apply { writeText("x") }
        val readable = directoryWith(root, "readable", "deu")
        File(readable, "notes.txt").writeText("x")

        assertEquals(emptyList<String>(), listTrainedDataNames(strayFile.absolutePath))
        assertEquals(emptyList<String>(), listTrainedDataNames(File(home, "missing").absolutePath))
        assertEquals(
            listOf("deu.traineddata", "notes.txt").sorted(),
            listTrainedDataNames(readable).sorted(),
        )
    }

    @Test
    fun `an existing model file is kept as is`(
        @TempDir root: Path,
    ) {
        val source = directoryWith(root, "source", "deu")
        val target = directoryWith(root, "target", "deu")

        assertTrue(linkOrCopyLanguageModel(source, "deu", target))
        assertEquals("model-deu", File(target, "deu.traineddata").readText())
    }

    @Test
    fun `a refused symlink falls back to copying the model`(
        @TempDir root: Path,
    ) {
        val source = directoryWith(root, "source", "deu")
        val target = directoryWith(root, "target")

        val installed =
            linkOrCopyLanguageModel(
                sourceDirectory = source,
                language = "deu",
                targetDirectory = target,
                link = { _, _ -> throw UnsupportedOperationException("no symlinks here") },
            )

        assertTrue(installed)
        assertEquals("model-deu", File(target, "deu.traineddata").readText())
    }

    @Test
    fun `a refused symlink and a refused copy install nothing`(
        @TempDir root: Path,
    ) {
        val source = directoryWith(root, "source", "deu")
        val target = directoryWith(root, "target")

        val installed =
            linkOrCopyLanguageModel(
                sourceDirectory = source,
                language = "deu",
                targetDirectory = target,
                link = { _, _ -> throw UnsupportedOperationException("no symlinks here") },
                copy = { _, _ -> throw UnsupportedOperationException("no writes here") },
            )

        assertFalse(installed)
        assertFalse(File(target, "deu.traineddata").exists())
    }

    @Test
    fun `the assembly directory is created once and a file in its place is refused`(
        @TempDir root: Path,
    ) {
        val fresh = root.resolve("fresh").toFile()
        assertTrue(ensureDirectoryExists(fresh.absolutePath))
        assertTrue(ensureDirectoryExists(fresh.absolutePath))

        val blockedByFile = root.resolve("occupied").toFile().apply { writeText("x") }
        assertFalse(ensureDirectoryExists(blockedByFile.absolutePath))
    }

    @Test
    fun `the real installation links the missing model out of the prefix directory`(
        @TempDir root: Path,
    ) {
        val prefix = directoryWith(root, "prefix", "eng", "osd")
        val home = directoryWith(root, "home")
        val userDirectory = File(home, ".local/share/tessdata").also { it.mkdirs() }
        File(userDirectory, "deu.traineddata").writeText("model-deu")

        val path =
            TessDataLocator(
                envReader = { prefix },
                userHome = { home },
                systemDirectories = emptyList(),
            ).dataPathFor("deu+eng")

        val assembly = File(home, ".stacklens/tessdata")
        assertEquals("${assembly.canonicalPath}/", File(path).canonicalPath + "/")
        assertEquals(File(userDirectory, "deu.traineddata").toPath(), Files.readSymbolicLink(File(path, "deu.traineddata").toPath()))
        assertEquals(File(prefix, "eng.traineddata").toPath(), Files.readSymbolicLink(File(path, "eng.traineddata").toPath()))
        assertTrue(File(path, "deu.traineddata").isFile)
        assertTrue(File(path, "eng.traineddata").isFile)
    }
}
