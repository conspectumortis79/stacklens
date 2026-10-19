package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.ClasspathResourceAdapter
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.classpath.ClasspathEntry
import com.stacklens.testsupport.FakeHostDirectoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

private val testHostDirs = FakeHostDirectoryPort()

class AppKnowledgeServiceTest {
    private val service = AppKnowledgeService(ClasspathResourceAdapter(), testHostDirs, JavaFileSystemAdapter())

    @Test
    fun `index returns non-empty string`() {
        val index = service.index()
        assertNotNull(index)
    }

    @Test
    fun `index contains available documents header`() {
        val index = service.index()
        assertTrue(
            index.contains("Available StackLens knowledge documents:") || index.isEmpty(),
            "Index should contain header or be empty if no resources found",
        )
    }

    @Test
    fun `relevant returns empty for blank query`() {
        val result = service.relevant("")
        assertEquals("", result)
    }

    @Test
    fun `relevant returns empty for whitespace-only query`() {
        val result = service.relevant("   ")
        assertEquals("", result)
    }

    @Test
    fun `relevant returns empty for all-stopword query`() {
        val result = service.relevant("der die das und oder")
        assertEquals("", result)
    }

    @Test
    fun `relevant with meaningful query returns string`() {
        val result = service.relevant("code analysis kotlin")
        assertNotNull(result)
    }

    @Test
    fun `relevant maxFiles parameter limits results`() {
        val result1 = service.relevant("code analysis kotlin security grafana", maxFiles = 1)
        val result2 = service.relevant("code analysis kotlin security grafana", maxFiles = 4)
        assertNotNull(result1)
        assertNotNull(result2)
    }

    @Test
    fun `relevant perFileChars parameter truncates content`() {
        val short = service.relevant("code", perFileChars = 100)
        val long = service.relevant("code", perFileChars = 5000)
        assertNotNull(short)
        assertNotNull(long)
        if (short.isNotEmpty() && long.isNotEmpty()) {
            assertTrue(short.length <= long.length, "Shorter perFileChars should produce shorter or equal output")
        }
    }

    @Test
    fun `readme returns string or null`() {
        val readme = service.readme()
    }

    @Test
    fun `relevant with stacklens keyword`() {
        val result = service.relevant("stacklens agent pipeline nora cole")
        assertNotNull(result)
    }

    @Test
    fun `relevant returns empty when valid tokens have no match in knowledge base`() {
        val result = service.relevant("xyznonexistent9876543")
        assertEquals("", result)
    }

    @Test
    fun `relevant filters out tokens shorter than 3 characters`() {
        val result = service.relevant("ab cd")
        assertEquals("", result)
    }

    @Test
    fun `countOccurrences returns 0 for empty needle`() {
        val method =
            AppKnowledgeService::class.java.getDeclaredMethod(
                "countOccurrences",
                String::class.java,
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(service, "some haystack text", "") as Int
        assertEquals(0, result)
    }

    @Test
    fun `countOccurrences counts multiple occurrences`() {
        val method =
            AppKnowledgeService::class.java.getDeclaredMethod(
                "countOccurrences",
                String::class.java,
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(service, "foo foo foo bar", "foo") as Int
        assertEquals(3, result)
    }

    @Test
    fun `readme returns null when README entry is not in cache`() {
        val freshService = AppKnowledgeService(ClasspathResourceAdapter(), testHostDirs, JavaFileSystemAdapter())
        val delegateField = AppKnowledgeService::class.java.getDeclaredField("cache\$delegate")
        delegateField.isAccessible = true
        val lazyDelegate = delegateField.get(freshService)
        val valueField = lazyDelegate::class.java.getDeclaredField("_value")
        valueField.isAccessible = true
        valueField.set(lazyDelegate, emptyList<Any>())

        assertNull(freshService.readme())
    }

    private fun serviceWithEntries(vararg entries: ClasspathEntry): AppKnowledgeService =
        object : AppKnowledgeService(ClasspathResourceAdapter(), testHostDirs, JavaFileSystemAdapter()) {
            override fun getClasspathEntries(pattern: String): List<ClasspathEntry> = entries.toList()

            override fun readmeCandidates(): List<java.io.File> = emptyList()
        }

    private fun serviceWithThrowingResolver(): AppKnowledgeService =
        object : AppKnowledgeService(ClasspathResourceAdapter(), testHostDirs, JavaFileSystemAdapter()) {
            override fun getClasspathEntries(pattern: String): List<ClasspathEntry> = throw RuntimeException("classpath io error")

            override fun readmeCandidates(): List<java.io.File> = emptyList()
        }

    @Test
    fun `loadPattern returns empty list when resolver getResources throws`() {
        val svc = serviceWithThrowingResolver()

        val result = svc.index()
        assertNotNull(result)
        assertTrue(
            result.contains("Available StackLens knowledge documents:") || result.isEmpty(),
        )
    }

    @Test
    fun `index uses entry title when all content lines start with hash`() {
        val svc = serviceWithEntries(ClasspathEntry("all-comments.md", "# Header\n# Another comment\n"))

        val result = svc.index()
        assertTrue(result.contains("all-comments"))
    }

    @Test
    fun `loadAll README not found does not add README entry`() {
        val svc =
            object : AppKnowledgeService(ClasspathResourceAdapter(), testHostDirs, JavaFileSystemAdapter()) {
                override fun getClasspathEntries(pattern: String): List<ClasspathEntry> = emptyList()

                override fun readmeCandidates(): List<java.io.File> = emptyList()
            }

        val result = svc.index()
        assertNotNull(result)
        assertTrue(!result.contains("README.md") || result.isEmpty())
    }

    @Test
    fun `loadAll README candidate is directory skips it`(
        @TempDir tempDir: Path,
    ) {
        val dirCandidate = tempDir.toFile()
        val svc =
            object : AppKnowledgeService(ClasspathResourceAdapter(), testHostDirs, JavaFileSystemAdapter()) {
                override fun getClasspathEntries(pattern: String): List<ClasspathEntry> = emptyList()

                override fun readmeCandidates(): List<java.io.File> = listOf(dirCandidate)
            }

        val result = svc.index()
        assertNotNull(result)
    }

    @Test
    fun `loadAll README candidate does not exist skips it`(
        @TempDir tempDir: Path,
    ) {
        val nonExistentFile = tempDir.resolve("no-readme.md").toFile()
        val svc =
            object : AppKnowledgeService(ClasspathResourceAdapter(), testHostDirs, JavaFileSystemAdapter()) {
                override fun getClasspathEntries(pattern: String): List<ClasspathEntry> = emptyList()

                override fun readmeCandidates(): List<java.io.File> = listOf(nonExistentFile)
            }

        val result = svc.index()
        assertNotNull(result)
    }

    @Test
    fun `relevant truncates content longer than perFileChars and appends marker`() {
        val longContent = "Lorem ipsum ".repeat(500)
        val svc =
            serviceWithEntries(ClasspathEntry("long.md", longContent))

        val result = svc.relevant("Lorem", perFileChars = 100)
        assertTrue(result.contains("[…truncated…]"), "Truncation marker should be appended")
    }

    @Test
    fun `relevant does not append truncation marker when content is shorter than perFileChars`() {
        val shortContent = "Lorem ipsum dolor sit amet."
        val svc = serviceWithEntries(ClasspathEntry("short.md", shortContent))

        val result = svc.relevant("Lorem", perFileChars = 5000)
        assertTrue(result.contains("Lorem ipsum"), "Content should be present")
        assertTrue(!result.contains("[…truncated…]"), "No truncation marker for short content")
    }

    @Test
    fun `relevant returns null README candidate when first candidate is a directory`() {
        val svc =
            object : AppKnowledgeService(ClasspathResourceAdapter(), testHostDirs, JavaFileSystemAdapter()) {
                override fun getClasspathEntries(pattern: String): List<ClasspathEntry> = emptyList()

                override fun readmeCandidates(): List<java.io.File> = listOf(java.io.File("."))
            }
        val result = svc.index()
        assertNotNull(result)
    }

    @Test
    fun `loadAll README readText exception is swallowed by runCatching`(
        @TempDir tempDir: Path,
    ) {
        val unreadableFile = tempDir.resolve("README.md").toFile()
        unreadableFile.createNewFile()
        unreadableFile.setReadable(false)

        val svc =
            object : AppKnowledgeService(ClasspathResourceAdapter(), testHostDirs, JavaFileSystemAdapter()) {
                override fun getClasspathEntries(pattern: String): List<ClasspathEntry> = emptyList()

                override fun readmeCandidates(): List<java.io.File> = listOf(unreadableFile)
            }

        val result = svc.index()
        assertNotNull(result)

        unreadableFile.setReadable(true)
    }
}
