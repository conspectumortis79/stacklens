package com.stacklens.application.service.codeanalysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MonorepoDetectorTest {
    private val detector = MonorepoDetector()

    @Test
    fun `returns empty for single-application repo (no monorepo)`() {
        val result = detector.detectSubApps(listOf("src/Main.kt", "build.gradle.kts"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty for empty path list`() {
        assertTrue(detector.detectSubApps(emptyList()).isEmpty())
    }

    @Test
    fun `detects monorepo at depth-1 (flat structure with two apps)`() {
        val paths =
            listOf(
                "backend/build.gradle.kts",
                "frontend/package.json",
                "backend/src/Main.kt",
                "frontend/src/App.tsx",
            )
        val result = detector.detectSubApps(paths)
        assertEquals(2, result.size)
        assertTrue(result.contains(Pair("backend", "backend")))
        assertTrue(result.contains(Pair("frontend", "frontend")))
    }

    @Test
    fun `detects monorepo at depth-2 (nested structure with packages directory)`() {
        val paths =
            listOf(
                "packages/app1/package.json",
                "packages/app2/package.json",
                "packages/app1/src/index.ts",
                "packages/app2/src/main.ts",
            )
        val result = detector.detectSubApps(paths)
        assertEquals(2, result.size)
        assertTrue(result.contains(Pair("packages/app1", "app1")))
        assertTrue(result.contains(Pair("packages/app2", "app2")))
    }

    @Test
    fun `depth-1 takes priority over depth-2 (flat wins over nested)`() {
        val paths =
            listOf(
                "a/build.gradle.kts",
                "b/package.json",
                "packages/c/build.gradle.kts",
                "packages/d/package.json",
            )
        val result = detector.detectSubApps(paths)
        assertEquals(2, result.size)
        assertTrue(result.contains(Pair("a", "a")))
        assertTrue(result.contains(Pair("b", "b")))
    }

    @Test
    fun `returns empty for depth-1 with only one app`() {
        val paths = listOf("only/build.gradle.kts", "only/src/Main.kt")
        val result = detector.detectSubApps(paths)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty for depth-2 with only one app`() {
        val paths = listOf("packages/only/package.json", "packages/only/src/index.ts")
        val result = detector.detectSubApps(paths)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `depth-2 deduplicates by subPath when multiple build files exist for the same group`() {
        val paths =
            listOf(
                "packages/app1/package.json",
                "packages/app1/build.gradle.kts",
                "packages/app2/package.json",
            )
        val result = detector.detectSubApps(paths)
        assertEquals(2, result.size)
        assertTrue(result.contains(Pair("packages/app1", "app1")))
        assertTrue(result.contains(Pair("packages/app2", "app2")))
    }

    @Test
    fun `ignores non-marker files at depth-1`() {
        val paths = listOf("a/some-file.txt", "b/some-file.txt")
        val result = detector.detectSubApps(paths)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `recognises all supported build marker file names`() {
        for (
        marker in
        listOf(
            "build.gradle.kts",
            "build.gradle",
            "pom.xml",
            "package.json",
            "composer.json",
            "pyproject.toml",
            "go.mod",
            "Cargo.toml",
            "settings.gradle.kts",
            "settings.gradle",
        )
        ) {
            val result = detector.detectSubApps(listOf("a/$marker", "b/$marker"))
            assertEquals(2, result.size, "marker=$marker should be recognised")
        }
    }
}
